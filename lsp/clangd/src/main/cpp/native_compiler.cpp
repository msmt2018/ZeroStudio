// JNI 桥接层 - 原生编译器接口
// 这是一个薄包装层，将 JNI 调用转发到各个功能模块

#include <jni.h>
#include <string>

// 导入各个功能模块
#include "utils/logging.h"
#include "utils/jni_utils.h"
#include "compiler/clang_compiler.h"
#include "linker/lld_linker.h"
#include "runner/shared_runner.h"
#include "lsp/clangd_server.h"

using namespace tinaide;

// 全局 Clangd 服务器实例
static lsp::ClangdServer* g_clangdServer = nullptr;

// ============================================================================
// 版本信息
// ============================================================================

extern "C" JNIEXPORT jstring JNICALL
Java_com_wuxianggujun_tinaide_core_nativebridge_NativeCompiler_getClangVersion(
        JNIEnv* env, jclass /*clazz*/) {
    const char* version = "LLVM 17 (runtime libs bundled)";
    LOGI("getClangVersion: %s", version);
    return env->NewStringUTF(version);
}

// ============================================================================
// 编译功能
// ============================================================================

extern "C" JNIEXPORT jstring JNICALL
Java_com_wuxianggujun_tinaide_core_nativebridge_NativeCompiler_syntaxCheck(
        JNIEnv* env, jclass /*clazz*/,
        jstring jSysroot, jstring jSrc, jstring jTarget, jboolean jIsCxx) {

    // 转换参数
    std::string sysroot = utils::jstringToUtf8(env, jSysroot);
    std::string srcPath = utils::jstringToUtf8(env, jSrc);
    std::string target = utils::jstringToUtf8(env, jTarget);
    bool isCxx = (jIsCxx == JNI_TRUE);

    // 构建编译选项
    compiler::CompileOptions options;
    options.sysroot = sysroot;
    options.target = target;
    options.isCxx = isCxx;

    // 执行语法检查
    auto result = compiler::syntaxCheck(srcPath, options);

    // 返回结果
    return utils::utf8ToJstring(env, result.errorMessage);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_wuxianggujun_tinaide_core_nativebridge_NativeCompiler_emitObj(
        JNIEnv* env, jclass /*clazz*/,
        jstring jSysroot, jstring jSrc, jstring jObjOut, jstring jTarget,
        jboolean jIsCxx, jobjectArray jFlags, jobjectArray jIncludeDirs) {

    // 转换参数
    std::string sysroot = utils::jstringToUtf8(env, jSysroot);
    std::string srcPath = utils::jstringToUtf8(env, jSrc);
    std::string objOut = utils::jstringToUtf8(env, jObjOut);
    std::string target = utils::jstringToUtf8(env, jTarget);
    bool isCxx = (jIsCxx == JNI_TRUE);

    // 构建编译选项
    compiler::CompileOptions options;
    options.sysroot = sysroot;
    options.target = target;
    options.isCxx = isCxx;
    options.includeDirs = utils::jstringArrayToVector(env, jIncludeDirs);
    options.flags = utils::jstringArrayToVector(env, jFlags);

    // 执行编译
    auto result = compiler::compileToObject(srcPath, objOut, options);

    // 返回结果
    return utils::utf8ToJstring(env, result.errorMessage);
}

// ============================================================================
// 链接功能
// ============================================================================

extern "C" JNIEXPORT jstring JNICALL
Java_com_wuxianggujun_tinaide_core_nativebridge_NativeCompiler_linkExe(
        JNIEnv* env, jclass /*clazz*/,
        jstring jSysroot, jstring jObj, jstring jOut, jstring jTarget, jboolean jIsCxx) {

    // 转换参数
    std::string sysroot = utils::jstringToUtf8(env, jSysroot);
    std::string objPath = utils::jstringToUtf8(env, jObj);
    std::string outExe = utils::jstringToUtf8(env, jOut);
    std::string target = utils::jstringToUtf8(env, jTarget);
    bool isCxx = (jIsCxx == JNI_TRUE);

    // 构建链接选项
    linker::LinkOptions options;
    options.sysroot = sysroot;
    options.target = target;
    options.isCxx = isCxx;
    options.timeoutMs = 60000; // Allow up to 60s to accommodate slower devices

    // 执行链接
    auto result = linker::linkExecutable(objPath, outExe, options);

    // 返回结果
    return utils::utf8ToJstring(env, result.errorMessage);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_wuxianggujun_tinaide_core_nativebridge_NativeCompiler_linkExeMany(
        JNIEnv* env, jclass /*clazz*/,
        jstring jSysroot, jobjectArray jObjs, jstring jOut, jstring jTarget,
        jboolean jIsCxx, jobjectArray jLibDirs, jobjectArray jLibs) {

    // 转换参数
    std::string sysroot = utils::jstringToUtf8(env, jSysroot);
    std::vector<std::string> objPaths = utils::jstringArrayToVector(env, jObjs);
    std::string outExe = utils::jstringToUtf8(env, jOut);
    std::string target = utils::jstringToUtf8(env, jTarget);
    bool isCxx = (jIsCxx == JNI_TRUE);

    // 构建链接选项
    linker::LinkOptions options;
    options.sysroot = sysroot;
    options.target = target;
    options.isCxx = isCxx;
    options.libDirs = utils::jstringArrayToVector(env, jLibDirs);
    options.libs = utils::jstringArrayToVector(env, jLibs);
    options.timeoutMs = 60000;

    // 执行链接
    auto result = linker::linkExecutableMany(objPaths, outExe, options);

    // 返回结果
    return utils::utf8ToJstring(env, result.errorMessage);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_wuxianggujun_tinaide_core_nativebridge_NativeCompiler_linkSo(
        JNIEnv* env, jclass /*clazz*/,
        jstring jSysroot, jstring jObj, jstring jOutSo, jstring jTarget, jboolean jIsCxx) {

    // 转换参数
    std::string sysroot = utils::jstringToUtf8(env, jSysroot);
    std::string objPath = utils::jstringToUtf8(env, jObj);
    std::string outSo = utils::jstringToUtf8(env, jOutSo);
    std::string target = utils::jstringToUtf8(env, jTarget);
    bool isCxx = (jIsCxx == JNI_TRUE);

    // 构建链接选项
    linker::LinkOptions options;
    options.sysroot = sysroot;
    options.target = target;
    options.isCxx = isCxx;
    options.timeoutMs = 60000;

    // 执行链接
    auto result = linker::linkSharedLibrary(objPath, outSo, options);

    // 返回结果
    return utils::utf8ToJstring(env, result.errorMessage);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_wuxianggujun_tinaide_core_nativebridge_NativeCompiler_linkSoMany(
        JNIEnv* env, jclass /*clazz*/,
        jstring jSysroot, jobjectArray jObjs, jstring jOutSo, jstring jTarget,
        jboolean jIsCxx, jobjectArray jLibDirs, jobjectArray jLibs) {

    // 转换参数
    std::string sysroot = utils::jstringToUtf8(env, jSysroot);
    std::vector<std::string> objPaths = utils::jstringArrayToVector(env, jObjs);
    std::string outSo = utils::jstringToUtf8(env, jOutSo);
    std::string target = utils::jstringToUtf8(env, jTarget);
    bool isCxx = (jIsCxx == JNI_TRUE);

    // 构建链接选项
    linker::LinkOptions options;
    options.sysroot = sysroot;
    options.target = target;
    options.isCxx = isCxx;
    options.libDirs = utils::jstringArrayToVector(env, jLibDirs);
    options.libs = utils::jstringArrayToVector(env, jLibs);
    options.timeoutMs = 60000;

    // 执行链接
    auto result = linker::linkSharedLibraryMany(objPaths, outSo, options);

    // 返回结果
    return utils::utf8ToJstring(env, result.errorMessage);
}

// ============================================================================
// 执行功能
// ============================================================================

extern "C" JNIEXPORT jint JNICALL
Java_com_wuxianggujun_tinaide_core_nativebridge_NativeCompiler_runShared(
        JNIEnv* env, jclass /*clazz*/, jstring jSoPath, jstring jSym) {

    std::string soPath = utils::jstringToUtf8(env, jSoPath);
    std::string symbolName = utils::jstringToUtf8(env, jSym);

    return runner::runInProcess(soPath, symbolName);
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_wuxianggujun_tinaide_core_nativebridge_NativeCompiler_runSharedIsolated(
        JNIEnv* env, jclass /*clazz*/,
        jstring jSoPath, jstring jSym, jint jTimeoutMs) {

    std::string soPath = utils::jstringToUtf8(env, jSoPath);
    std::string symbolName = utils::jstringToUtf8(env, jSym);
    int timeoutMs = jTimeoutMs;

    auto result = runner::runIsolated(soPath, symbolName, timeoutMs);

    jclass resultClass = env->FindClass("com/wuxianggujun/tinaide/core/nativebridge/RunExecutionResult");
    if (!resultClass) {
        return nullptr;
    }
    jmethodID ctor = env->GetMethodID(resultClass, "<init>", "(ILjava/lang/String;)V");
    if (!ctor) {
        return nullptr;
    }

    jstring output = utils::utf8ToJstring(env, result.output);
    jobject runResult = env->NewObject(resultClass, ctor, static_cast<jint>(result.returnCode), output);
    env->DeleteLocalRef(output);
    return runResult;
}

// ============================================================================
// Clangd LSP 服务器功能
// ============================================================================

extern "C" JNIEXPORT jstring JNICALL
Java_com_wuxianggujun_tinaide_core_nativebridge_NativeCompiler_startClangd(
        JNIEnv* env, jclass /*clazz*/, jstring jLibPath, jobjectArray jArgs) {

    std::string libPath = utils::jstringToUtf8(env, jLibPath);
    std::vector<std::string> extraArgs = utils::jstringArrayToVector(env, jArgs);

    // 创建全局 Clangd 服务器实例（如果尚未创建）
    if (!g_clangdServer) {
        g_clangdServer = new lsp::ClangdServer();
    }

    // 启动服务器
    std::string error = g_clangdServer->start(libPath, extraArgs);

    return utils::utf8ToJstring(env, error);
}

extern "C" JNIEXPORT void JNICALL
Java_com_wuxianggujun_tinaide_core_nativebridge_NativeCompiler_stopClangd(
        JNIEnv* /*env*/, jclass /*clazz*/) {

    if (g_clangdServer) {
        g_clangdServer->stop();
        delete g_clangdServer;
        g_clangdServer = nullptr;
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_wuxianggujun_tinaide_core_nativebridge_NativeCompiler_isClangdRunning(
        JNIEnv* /*env*/, jclass /*clazz*/) {

    if (g_clangdServer && g_clangdServer->isRunning()) {
        return JNI_TRUE;
    }
    return JNI_FALSE;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_wuxianggujun_tinaide_core_nativebridge_NativeCompiler_writeToClangd(
        JNIEnv* env, jclass /*clazz*/, jbyteArray jData) {

    if (!g_clangdServer) {
        return -1;
    }

    jsize len = env->GetArrayLength(jData);
    if (len <= 0) {
        return 0;
    }

    jbyte* data = env->GetByteArrayElements(jData, nullptr);
    if (!data) {
        return -1;
    }

    std::vector<char> buffer(data, data + len);
    env->ReleaseByteArrayElements(jData, data, JNI_ABORT);

    int written = g_clangdServer->write(buffer);
    return static_cast<jint>(written);
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_wuxianggujun_tinaide_core_nativebridge_NativeCompiler_readFromClangd(
        JNIEnv* env, jclass /*clazz*/, jint maxBytes) {

    if (!g_clangdServer) {
        return nullptr;
    }

    std::vector<char> data = g_clangdServer->read(maxBytes);
    if (data.empty()) {
        return nullptr;
    }

    jbyteArray result = env->NewByteArray(static_cast<jsize>(data.size()));
    if (result) {
        env->SetByteArrayRegion(result, 0, static_cast<jsize>(data.size()),
                                reinterpret_cast<jbyte*>(data.data()));
    }
    return result;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_com_wuxianggujun_tinaide_core_nativebridge_NativeCompiler_readFromClangdWithTimeout(
        JNIEnv* env, jclass /*clazz*/, jint maxBytes, jint timeoutMs) {

    if (!g_clangdServer) {
        return nullptr;
    }

    std::vector<char> data = g_clangdServer->readWithTimeout(maxBytes, timeoutMs);
    if (data.empty()) {
        return nullptr;
    }

    jbyteArray result = env->NewByteArray(static_cast<jsize>(data.size()));
    if (result) {
        env->SetByteArrayRegion(result, 0, static_cast<jsize>(data.size()),
                                reinterpret_cast<jbyte*>(data.data()));
    }
    return result;
}
