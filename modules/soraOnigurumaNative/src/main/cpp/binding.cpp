//
// Safe JNI bridge for Oniguruma.
//
// Native handles are registry IDs, never raw pointers. A search takes a
// shared ownership reference before leaving the registry lock, so releasing
// a handle cannot destroy a regex while an operation is using it.
//

#include "oniguruma.h"
#include "jni.h"

#include <algorithm>
#include <atomic>
#include <cstdint>
#include <limits>
#include <memory>
#include <mutex>
#include <new>
#include <unordered_map>
#include <utility>
#include <vector>

struct OnigCachedResult {
    jlong cacheKey{0};
    int lastSearchPosition{0};
    std::unique_ptr<OnigRegion, void (*)(OnigRegion*)> region{
            nullptr, [](OnigRegion* value) {
                if (value != nullptr) {
                    onig_region_free(value, 1);
                }
            }};
};

struct OnigCachedRegex {
    explicit OnigCachedRegex(OnigRegex value, bool hasAnchor)
            : hasGAnchor(hasAnchor), regex(value) {}

    ~OnigCachedRegex() {
        lastResult.region.reset();
        if (regex != nullptr) {
            onig_free(regex);
            regex = nullptr;
        }
    }

    OnigCachedRegex(const OnigCachedRegex&) = delete;
    OnigCachedRegex& operator=(const OnigCachedRegex&) = delete;

    const bool hasGAnchor;
    OnigRegex regex{nullptr};

    // Protects both OnigRegex use and the one-entry search cache. Oniguruma
    // regex objects are not assumed to be concurrently searchable here.
    std::mutex searchLock;
    OnigCachedResult lastResult;
};

namespace {

using RegexHandle = jlong;
using RegexPtr = std::shared_ptr<OnigCachedRegex>;

std::mutex gRegistryLock;
std::unordered_map<RegexHandle, RegexPtr> gRegexRegistry;
std::atomic<RegexHandle> gNextHandle{1};

using RegionPtr = std::unique_ptr<OnigRegion, void (*)(OnigRegion*)>;

void freeRegion(OnigRegion* region) {
    if (region != nullptr) {
        onig_region_free(region, 1);
    }
}

RegionPtr makeRegion() {
    return RegionPtr(onig_region_new(), &freeRegion);
}

RegexHandle registerRegex(RegexPtr regex) {
    if (!regex) {
        return 0;
    }

    std::lock_guard<std::mutex> lock(gRegistryLock);
    for (;;) {
        const RegexHandle handle = gNextHandle.fetch_add(1, std::memory_order_relaxed);
        // Zero is reserved for an invalid handle. Exhaustion is practically
        // unreachable, but never publish a non-positive identifier.
        if (handle <= 0) {
            return 0;
        }
        const auto inserted = gRegexRegistry.emplace(handle, regex);
        if (inserted.second) {
            return handle;
        }
    }
}

RegexPtr acquireRegex(RegexHandle handle) {
    if (handle <= 0) {
        return {};
    }
    std::lock_guard<std::mutex> lock(gRegistryLock);
    const auto found = gRegexRegistry.find(handle);
    return found == gRegexRegistry.end() ? RegexPtr{} : found->second;
}

void unregisterRegex(RegexHandle handle) {
    if (handle <= 0) {
        return;
    }

    RegexPtr released;
    {
        std::lock_guard<std::mutex> lock(gRegistryLock);
        const auto found = gRegexRegistry.find(handle);
        if (found == gRegexRegistry.end()) {
            return;
        }
        released = std::move(found->second);
        gRegexRegistry.erase(found);
    }
    // Destruction happens outside the registry lock. Any in-flight operation
    // retains its own shared_ptr and delays destruction until it completes.
    released.reset();
}

bool checkGAnchor(const unsigned char* data, jsize length) {
    if (data == nullptr || length < 2) {
        return false;
    }
    for (jsize i = 0; i + 1 < length; ++i) {
        if (data[i] == '\\' && data[i + 1] == 'G') {
            return true;
        }
    }
    return false;
}

void updateCacheLocked(OnigCachedRegex& regex, jlong cacheKey, int start,
                       const OnigRegion* resultRegion, bool cacheNoMatch) {
    if (cacheKey == 0 || regex.hasGAnchor) {
        return;
    }

    regex.lastResult.region.reset();
    regex.lastResult.cacheKey = cacheKey;
    regex.lastResult.lastSearchPosition = start;

    if (resultRegion != nullptr) {
        auto copy = makeRegion();
        if (!copy) {
            regex.lastResult.cacheKey = 0;
            return;
        }
        onig_region_copy(copy.get(), const_cast<OnigRegion*>(resultRegion));
        regex.lastResult.region = std::move(copy);
    } else if (!cacheNoMatch) {
        regex.lastResult.cacheKey = 0;
    }
}

bool searchCached(const RegexPtr& regex, jlong cacheKey,
                  const unsigned char* data, int start, int end,
                  OnigRegion* outRegion) {
    if (!regex || regex->regex == nullptr || outRegion == nullptr ||
        data == nullptr || start < 0 || end < start) {
        return false;
    }

    std::lock_guard<std::mutex> lock(regex->searchLock);

    if (cacheKey != 0 && !regex->hasGAnchor &&
        regex->lastResult.cacheKey == cacheKey &&
        regex->lastResult.lastSearchPosition <= start) {
        const auto* cached = regex->lastResult.region.get();

        // A cached miss remains a miss for any later start position in the
        // same immutable source string.
        if (cached == nullptr) {
            return true == false;
        }

        if (cached->num_regs > 0 &&
            cached->beg != nullptr && cached->end != nullptr &&
            cached->beg[0] >= start && cached->end[0] >= cached->beg[0] &&
            cached->end[0] <= end) {
            onig_region_copy(outRegion, const_cast<OnigRegion*>(cached));
            return true;
        }
    }

    auto resultRegion = makeRegion();
    if (!resultRegion) {
        return false;
    }

    const int status = onig_search(regex->regex, data, data + end,
                                   data + start, data + end, resultRegion.get(),
                                   ONIG_OPTION_DEFAULT);

    if (status == ONIG_MISMATCH) {
        updateCacheLocked(*regex, cacheKey, start, nullptr, true);
        return false;
    }
    if (status < 0) {
        // Do not cache operational errors as ordinary misses.
        updateCacheLocked(*regex, cacheKey, start, nullptr, false);
        return false;
    }

    updateCacheLocked(*regex, cacheKey, start, resultRegion.get(), true);
    onig_region_copy(outRegion, resultRegion.get());
    return true;
}

bool isValidRange(jsize length, jint start, jint end) {
    return start >= 0 && end >= start && end <= length;
}

jintArray makeResultArray(JNIEnv* env, const OnigRegion* region,
                          int extraValue, bool appendExtra) {
    if (region == nullptr || region->num_regs < 0 ||
        region->num_regs > (std::numeric_limits<jsize>::max() - (appendExtra ? 1 : 0)) / 2) {
        return nullptr;
    }

    const jsize count = static_cast<jsize>(region->num_regs * 2 +
                                           (appendExtra ? 1 : 0));
    jintArray result = env->NewIntArray(count);
    if (result == nullptr) {
        return nullptr; // JVM has raised OutOfMemoryError.
    }

    std::vector<jint> values(static_cast<size_t>(count));
    for (int i = 0; i < region->num_regs; ++i) {
        values[static_cast<size_t>(i) * 2] = region->beg[i];
        values[static_cast<size_t>(i) * 2 + 1] = region->end[i];
    }
    if (appendExtra) {
        values[static_cast<size_t>(count - 1)] = extraValue;
    }

    if (count > 0) {
        env->SetIntArrayRegion(result, 0, count, values.data());
        if (env->ExceptionCheck()) {
            return nullptr;
        }
    }
    return result;
}

} // namespace

extern "C"
JNIEXPORT jlong JNICALL
Java_io_github_rosemoe_oniguruma_OnigNative_nCreateRegex(
        JNIEnv* env, jclass, jbyteArray pattern, jboolean ignore_case) {
    if (pattern == nullptr) {
        return 0;
    }

    const jsize size = env->GetArrayLength(pattern);
    jbyte* content = env->GetByteArrayElements(pattern, nullptr);
    if (content == nullptr) {
        return 0;
    }

    auto* bytes = reinterpret_cast<unsigned char*>(content);
    const bool hasGAnchor = checkGAnchor(bytes, size);
    OnigOptionType options = ignore_case == JNI_TRUE ? ONIG_OPTION_IGNORECASE : ONIG_OPTION_NONE;
    options |= ONIG_OPTION_CAPTURE_GROUP;

    OnigRegex compiledRegex = nullptr;
    OnigErrorInfo errorInfo{};
    const int status = onig_new(&compiledRegex, bytes, bytes + size, options,
                                ONIG_ENCODING_UTF8, ONIG_SYNTAX_DEFAULT, &errorInfo);

    env->ReleaseByteArrayElements(pattern, content, JNI_ABORT);

    if (status != ONIG_NORMAL || compiledRegex == nullptr) {
        return 0;
    }

    try {
        auto state = std::make_shared<OnigCachedRegex>(compiledRegex, hasGAnchor);
        // Ownership transfers to OnigCachedRegex as soon as construction succeeds.
        compiledRegex = nullptr;
        return registerRegex(std::move(state));
    } catch (const std::bad_alloc&) {
        if (compiledRegex != nullptr) {
            onig_free(compiledRegex);
        }
        return 0;
    }
}

extern "C"
JNIEXPORT jintArray JNICALL
Java_io_github_rosemoe_oniguruma_OnigNative_nRegexSearch(
        JNIEnv* env, jclass, jlong native_handle, jlong cache_key,
        jbyteArray str, jint start, jint end) {
    if (str == nullptr) {
        return nullptr;
    }

    const jsize length = env->GetArrayLength(str);
    if (!isValidRange(length, start, end)) {
        return nullptr;
    }

    RegexPtr regex = acquireRegex(native_handle);
    if (!regex) {
        return nullptr;
    }

    jbyte* content = env->GetByteArrayElements(str, nullptr);
    if (content == nullptr) {
        return nullptr;
    }

    auto region = makeRegion();
    bool success = false;
    if (region) {
        auto* bytes = reinterpret_cast<unsigned char*>(content);
        success = searchCached(regex, cache_key, bytes, start, end, region.get());
    }

    env->ReleaseByteArrayElements(str, content, JNI_ABORT);

    if (!success || !region) {
        return nullptr;
    }
    return makeResultArray(env, region.get(), 0, false);
}

extern "C"
JNIEXPORT void JNICALL
Java_io_github_rosemoe_oniguruma_OnigNative_releaseRegex(
        JNIEnv*, jclass, jlong native_handle) {
    unregisterRegex(native_handle);
}

extern "C"
JNIEXPORT jintArray JNICALL
Java_io_github_rosemoe_oniguruma_OnigNative_nRegexSearchBatch(
        JNIEnv* env, jclass, jlongArray native_handles,
        jlong cache_key, jbyteArray str, jint start, jint end) {
    if (native_handles == nullptr || str == nullptr) {
        return nullptr;
    }

    const jsize regexCount = env->GetArrayLength(native_handles);
    const jsize textLength = env->GetArrayLength(str);
    if (!isValidRange(textLength, start, end) || regexCount <= 0) {
        return nullptr;
    }

    std::vector<jlong> handles(static_cast<size_t>(regexCount));
    env->GetLongArrayRegion(native_handles, 0, regexCount, handles.data());
    if (env->ExceptionCheck()) {
        return nullptr;
    }

    std::vector<RegexPtr> regexes;
    regexes.reserve(static_cast<size_t>(regexCount));
    for (const jlong handle : handles) {
        regexes.emplace_back(acquireRegex(handle));
    }

    jbyte* content = env->GetByteArrayElements(str, nullptr);
    if (content == nullptr) {
        return nullptr;
    }

    auto currentRegion = makeRegion();
    auto bestRegion = makeRegion();
    if (!currentRegion || !bestRegion) {
        env->ReleaseByteArrayElements(str, content, JNI_ABORT);
        return nullptr;
    }

    int bestIndex = -1;
    int bestLocation = std::numeric_limits<int>::max();
    auto* bytes = reinterpret_cast<unsigned char*>(content);

    for (int i = 0; i < regexCount; ++i) {
        const auto& regex = regexes[static_cast<size_t>(i)];
        if (!regex) {
            continue;
        }

        onig_region_clear(currentRegion.get());
        if (!searchCached(regex, cache_key, bytes, start, end, currentRegion.get()) ||
            currentRegion->num_regs <= 0 || currentRegion->beg == nullptr) {
            continue;
        }

        const int location = std::max(0, currentRegion->beg[0]);
        if (bestIndex == -1 || location < bestLocation) {
            bestIndex = i;
            bestLocation = location;
            onig_region_copy(bestRegion.get(), currentRegion.get());
        }

        if (location == start) {
            break;
        }
    }

    env->ReleaseByteArrayElements(str, content, JNI_ABORT);

    if (bestIndex < 0) {
        return nullptr;
    }
    return makeResultArray(env, bestRegion.get(), bestIndex, true);
}
