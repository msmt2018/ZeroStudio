//
// Manages loading/unloading of tree-sitter language libraries (.so).
//
// Design notes:
//   1. Deduplication by soPath: loading the same language multiple times only
//      performs a real dlopen once; subsequent loads are managed via reference
//      counting.
//   2. unloadLanguage only decrements the reference count and does NOT call
//      dlclose immediately — this avoids UAF races with TSParser/TSTree
//      instances that may still be using the language.
//   3. Actual memory reclamation is centralized in releaseUnusedLanguages(),
//      which is triggered by the Kotlin layer's onTrimMemory callback under
//      system memory pressure, rather than being tied to the high-frequency
//      load/unload paths.
//   4. Expensive operations like dlopen/dlclose are performed outside the lock;
//      the lock only protects read/write access to the maps themselves.

#include <dlfcn.h>
#include <stdio.h>

#include <mutex>
#include <vector>
#include <unordered_map>

#include "ts_utils.h"


namespace {

struct DLEntry {
    void *dl_handle = nullptr;
    int ref_count = 0;      // Number of active Kotlin-side holders
    std::string path_key;   // Reverse mapping to soPath for O(1) lookup on removal (avoids linear scan)
};

std::unordered_map<const TSLanguage *, DLEntry> g_lang_to_entry;
std::unordered_map<std::string, const TSLanguage *> g_path_to_lang;
std::mutex g_mutex;

} // namespace


#ifdef __cplusplus
extern "C" {
#endif

// ---------- Loading ----------
//
// Returns: A jlong cast from TSLanguage* on success; 0 on failure.
// Repeated calls with the same soPath will only perform a real dlopen once;
// subsequent calls simply increment the reference count.
jlong JNICALL language_load(JNIEnv* env, jclass clazz, jstring path, jstring name) {
    const char *lib_path = env->GetStringUTFChars(path, nullptr);
    const char *sym_name = env->GetStringUTFChars(name, nullptr);

    if (!lib_path || !sym_name) {
        if (lib_path) env->ReleaseStringUTFChars(path, lib_path);
        if (sym_name) env->ReleaseStringUTFChars(name, sym_name);
        return reinterpret_cast<jlong>(nullptr);
    }
        
    // 1. Use string_view to avoid heap allocation overhead during Phase 1
    std::string_view p_key_view(lib_path);

    // Phase 1: Short lock duration to check if the library is already loaded
    {
        std::lock_guard<std::mutex> lock(g_mutex);
        // Note: For absolute compatibility with std::map<std::string, ...> without 
        // transparent lookup (C++14 std::less<>), we temporarily construct a std::string here.
        auto it = g_path_to_lang.find(std::string(p_key_view)); 
        if (it != g_path_to_lang.end()) {
            g_lang_to_entry[it->second].ref_count++;
            env->ReleaseStringUTFChars(path, lib_path);
            env->ReleaseStringUTFChars(name, sym_name);
            return reinterpret_cast<jlong>(it->second);
        }
    }

    // Cache miss confirmed: Persist to std::string for dlopen and map storage
    std::string p_key(lib_path);
    std::string s_name(sym_name);

    // Safely copied into std::string, release JNI local references immediately
    env->ReleaseStringUTFChars(path, lib_path);
    env->ReleaseStringUTFChars(name, sym_name);
        
    // Phase 2: Perform the time-consuming library loading outside the lock
    void *handle = dlopen(p_key.c_str(), RTLD_LAZY);
    if(!handle) {        
        LOGE("dlopen failed: %s\n", dlerror());
        return reinterpret_cast<jlong>(nullptr);
    }
    
    using TSFunction = TSLanguage* (*) (void);
    auto fn_invoke = (TSFunction)dlsym(handle, s_name.c_str());
    
    if(!fn_invoke) {
        LOGE("dlsym failed: %s\n", dlerror()); // Fixed: Corrected log description from dlopen to dlsym
        dlclose(handle);
        return reinterpret_cast<jlong>(nullptr); // Prevent nullptr from contaminating the Map
    }
    
    const TSLanguage *lang = fn_invoke();
    if (!lang) {
        LOGE("Language invocation returned nullptr\n");
        dlclose(handle);
        return reinterpret_cast<jlong>(nullptr); // Intercept nullptr returns
    }
    
    // Phase 3: Re-acquire the lock to commit the results
    {
        std::lock_guard<std::mutex> lock(g_mutex);
        
        // Handle race conditions where another thread loaded the same library during Phase 2
        auto it = g_path_to_lang.find(p_key);
        if (it != g_path_to_lang.end()) {
            g_lang_to_entry[it->second].ref_count++;
            dlclose(handle); // The current thread's handle is redundant, close it safely
            return reinterpret_cast<jlong>(it->second);
        }
        
        // Thread-safe insertion into global registries
        DLEntry entry = { handle, 1, p_key};
        g_lang_to_entry[lang] = entry;
        g_path_to_lang[p_key] = lang;
        return reinterpret_cast<jlong>(lang);
    } 
}

// ---------- Unloading: only decrements ref_count, does NOT call dlclose ----------
//
// Called when: Kotlin-side LanguageHandle.close() is invoked (e.g., when closing
// a file tab).
// A ref_count dropping to 0 only means "no current users; eligible for reclamation
// under memory pressure" — it does NOT mean immediate reclamation, avoiding UAF
// with any TSParser/TSTree instances still in use.
void JNICALL language_unload(jlong lang) {
    if (lang == 0) return;
    const TSLanguage *ts_language = reinterpret_cast<const TSLanguage *>(lang);

    std::lock_guard<std::mutex> lock(g_mutex);
    auto it = g_lang_to_entry.find(ts_language);
    if (it != g_lang_to_entry.end()) {
        if (it->second.ref_count > 0) {
            it->second.ref_count--;
        }
    }
}

// ---------- Actual reclamation: should only be called on onTrimMemory ----------
//
// Iterates over all languages with ref_count == 0 and batch-closes them via dlclose.
// dlclose itself is performed outside the lock to avoid blocking other threads'
// load/unload calls for an extended period.
// Returns: The number of language libraries actually released in this call,
// allowing the Kotlin side to log the event.
jint JNICALL language_release() {
    std::vector<void *> vect;
    {
        std::lock_guard<std::mutex> lock(g_mutex);
        for (auto it = g_lang_to_entry.begin(); it != g_lang_to_entry.end(); ) {
            if (it->second.ref_count <= 0) {
                vect.push_back(it->second.dl_handle);
                g_path_to_lang.erase(it->second.path_key); // O(1), no linear scan needed
                it = g_lang_to_entry.erase(it);
            } else {
                ++it;
            }
        }
    } // Lock released
    
    for (void *handle : vect) {
        dlclose(handle);
    }
    return static_cast<jint>(vect.size());
}

jint JNICALL language_get_loaded_count() {
    std::lock_guard<std::mutex> lock(g_mutex);
    return static_cast<jint>(g_lang_to_entry.size());
}

jint JNICALL language_get_unused_count() {
    std::lock_guard<std::mutex> lock(g_mutex);
    int count = 0;
    for (const auto &kv : g_lang_to_entry) {
        if (kv.second.ref_count <= 0) count++;
    }
    return count;
}


jlong JNICALL language_copy(jlong language) {
    return reinterpret_cast<jlong>(
        ts_language_copy(reinterpret_cast<TSLanguage*>(language))
    );
}

jint JNICALL language_get_abi_version(JNIEnv* env, jobject thiz) {
    TSLanguage *self = GET_POINTER(TSLanguage, thiz);
    return ts_language_abi_version(self);
}

jint JNICALL language_get_symbol_count(JNIEnv* env, jobject thiz) {
    TSLanguage *self = GET_POINTER(TSLanguage, thiz);
    return ts_language_symbol_count(self);
}

jint JNICALL language_get_state_count(JNIEnv* env, jobject thiz) {
    TSLanguage *self = GET_POINTER(TSLanguage, thiz);
    return ts_language_state_count(self);
}

jint JNICALL language_get_field_count(JNIEnv* env, jobject thiz) {
    TSLanguage *self = GET_POINTER(TSLanguage, thiz);
    return ts_language_field_count(self);
}

jstring JNICALL language_get_name(JNIEnv *env, jobject thiz) {
    TSLanguage *self = GET_POINTER(TSLanguage, thiz);
    const char *name = ts_language_name(self);
    return name ? env->NewStringUTF(name) : nullptr;
}

jobject JNICALL language_get_metadata(JNIEnv *env, jobject thiz) {
    TSLanguage *self = GET_POINTER(TSLanguage, thiz);
    const TSLanguageMetadata *metadata = ts_language_metadata(self);
    if (metadata == nullptr)
        return nullptr;

    jobject major = env->AllocObject(global_class_cache.UShort);
    env->SetShortField(major, global_field_cache.UShort_data,
                          (jshort)metadata->major_version);
    jobject minor = env->AllocObject(global_class_cache.UShort);
    env->SetShortField(minor, global_field_cache.UShort_data,
                          (jshort)metadata->minor_version);
    jobject patch = env->AllocObject(global_class_cache.UShort);
    env->SetShortField(patch, global_field_cache.UShort_data,
                          (jshort)metadata->patch_version);
    jobject version = NEW_OBJECT(Triple, major, minor, patch);
    return NEW_OBJECT(TSLanguage$Metadata, version);
}

jstring JNICALL language_symbol_name(JNIEnv *env, jobject thiz, jshort symbol) {
    TSLanguage *self = GET_POINTER(TSLanguage, thiz);
    const char *name = ts_language_symbol_name(self, static_cast<uint16_t>(symbol));
    return env->NewStringUTF(name);
}

jshort JNICALL language_symbol_for_name(JNIEnv *env, jobject thiz, jstring name, jboolean isNamed) {
    TSLanguage *self = GET_POINTER(TSLanguage, thiz);
    const char *symbol_name = env->GetStringUTFChars(name, nullptr);
    
    TSSymbol symbol = ts_language_symbol_for_name(
        self, 
        symbol_name, 
        strlen(symbol_name), 
        isNamed
    );
    
    env->ReleaseStringUTFChars(name, symbol_name);
    return static_cast<jshort>(symbol);
}

jshortArray JNICALL language_get_supertypes(JNIEnv *env, jobject thiz) {
    TSLanguage *self = GET_POINTER(TSLanguage, thiz);
    uint32_t length;
    const TSSymbol *supertypes = ts_language_supertypes(self, &length);
    jshortArray result = env->NewShortArray(length);
    if (length > 0) {
        env->SetShortArrayRegion(result, 0, length, (const jshort *)supertypes);
    }
    return result;
}

jshortArray JNICALL language_subtypes(JNIEnv *env, jobject thiz, jshort supertype) {
    TSLanguage *self = GET_POINTER(TSLanguage, thiz);
    uint32_t length;
    const TSSymbol *subtypes = ts_language_subtypes(self, supertype, &length);
    jshortArray result = env->NewShortArray(length);
    if (length > 0) {
        env->SetShortArrayRegion(result, 0, length, (const jshort *)subtypes);
    }
    return result;
}

jboolean JNICALL language_is_named(JNIEnv *env, jobject thiz, jshort symbol) {
    TSLanguage *self = GET_POINTER(TSLanguage, thiz);
    TSSymbolType symbol_type = ts_language_symbol_type(self, symbol);
    return (symbol_type == TSSymbolTypeRegular);
}

jboolean JNICALL language_is_visible(JNIEnv *env, jobject thiz, jshort symbol) {
    TSLanguage *self = GET_POINTER(TSLanguage, thiz);
    TSSymbolType symbol_type = ts_language_symbol_type(self, symbol);
    return (symbol_type <= TSSymbolTypeAnonymous);
}

jboolean JNICALL language_is_supertype(JNIEnv *env, jobject thiz, jshort symbol) {
    TSLanguage *self = GET_POINTER(TSLanguage, thiz);
    TSSymbolType symbol_type = ts_language_symbol_type(self, symbol);
    return (jboolean)(symbol_type == TSSymbolTypeSupertype);
}

jstring JNICALL language_field_name_for_id(JNIEnv *env, jobject thiz, jshort id) {
    TSLanguage *self = GET_POINTER(TSLanguage, thiz);
    const char *name = ts_language_field_name_for_id(self, static_cast<uint16_t>(id));
    return name ? env->NewStringUTF(name) : nullptr;
}

jint JNICALL language_field_id_for_name(JNIEnv *env, jobject thiz, jstring name) {
    TSLanguage *self = GET_POINTER(TSLanguage, thiz);
    const char *field_name = env->GetStringUTFChars(name, nullptr);
    
    TSFieldId field = ts_language_field_id_for_name(self, field_name, strlen(field_name));   
    env->ReleaseStringUTFChars(name, field_name);
    return static_cast<jint>(field);
}

jshort JNICALL language_next_state(JNIEnv *env, jobject thiz, jshort state, jshort symbol) {
    TSLanguage *self = GET_POINTER(TSLanguage, thiz);
    return ts_language_next_state(self, static_cast<uint16_t>(state), static_cast<uint16_t>(symbol));
}

void JNICALL language_check_version(JNIEnv *env, jobject thiz) {
    TSLanguage *self = GET_POINTER(TSLanguage, thiz);
    uint32_t version = ts_language_abi_version(self);
    if (version < TREE_SITTER_MIN_COMPATIBLE_LANGUAGE_VERSION ||
       version > TREE_SITTER_LANGUAGE_VERSION
    ) {
        const char *fmt = "Incompatible language version %u. Must be between %u and %u.";
        char buffer[70] = {0}; // length(fmt) + digits(UINT32_MAX)
        sprintf(buffer, fmt, version, TREE_SITTER_MIN_COMPATIBLE_LANGUAGE_VERSION,
                  TREE_SITTER_LANGUAGE_VERSION);
        THROW(IllegalArgumentException, static_cast<const char *>(buffer));
    }
}

jobject JNICALL language_symbol_type(JNIEnv* env, jobject thiz, jshort symbol) {
    TSLanguage *self = GET_POINTER(TSLanguage, thiz);
    TSSymbolType symbol_type = ts_language_symbol_type(self, static_cast<uint16_t>(symbol));
    
    jobject symbol_object = nullptr;
    switch(symbol_type) {
        case TSSymbolTypeRegular:
            symbol_object = GET_STATIC_FIELD(Object, TSSymbolType, TSSymbolType_REGULAR);
            break;
        case TSSymbolTypeAnonymous:
            symbol_object = GET_STATIC_FIELD(Object, TSSymbolType, TSSymbolType_ANONYMOUS);            
            break;
        case TSSymbolTypeAuxiliary:
            symbol_object = GET_STATIC_FIELD(Object, TSSymbolType, TSSymbolType_AUXILIARY);
            break;
        default:
            UNREACHABLE();
    }
    return symbol_object;
}

extern const JNINativeMethod TSLanguage_methods[] = {
    {"load", "(Ljava/lang/String;Ljava/lang/String;)J", (void *)&language_load},
    {"unload", "(J)V", (void *)&language_unload},
    {"release", "()I", (void *)&language_release},
    {"copy", "(J)J", (void *)&language_copy},
    {"getLoadedCount", "()I", (void *)&language_get_loaded_count},
    {"getUnusedCount", "()I", (void *)&language_get_unused_count},
    {"getAbiVersion", "()I", (void *)&language_get_abi_version},
    {"getSymbolCount", "()I", (void *)&language_get_symbol_count},
    {"getStateCount", "()I", (void *)&language_get_state_count},
    {"getFieldCount", "()I", (void *)&language_get_field_count},
    {"getName", "()Ljava/lang/String;", (void *)&language_get_name},
    {"getMetadata", "()L" PACKAGE "TSLanguage$Metadata;", (void *)&language_get_metadata},
    {"getSupertypes", "()[S", (void *)&language_get_supertypes},
    {"symbolName", "(S)Ljava/lang/String;", (void *)&language_symbol_name},
    {"symbolForName", "(Ljava/lang/String;Z)S", (void *)&language_symbol_for_name},
    {"isNamed", "(S)Z", (void *)&language_is_named},
    {"isVisible", "(S)Z", (void *)&language_is_visible},
    {"isSupertype", "(S)Z", (void *)&language_is_supertype},
    {"subtypes", "(S)[S", (void *)&language_subtypes},
    {"fieldNameForId", "(S)Ljava/lang/String;", (void *)&language_field_name_for_id},
    {"fieldIdForName", "(Ljava/lang/String;)S", (void *)&language_field_id_for_name},
    {"nextState", "(SS)S", (void *)&language_next_state},
    {"checkVersion", "()V", (void *)&language_check_version},
    {"symbolType", "(S)L" PACKAGE "TSSymbolType;", (void *)&language_symbol_type},
};

extern const size_t TSLanguage_methods_size = sizeof TSLanguage_methods / sizeof(JNINativeMethod);

#ifdef __cplusplus
}
#endif // __cplusplus

