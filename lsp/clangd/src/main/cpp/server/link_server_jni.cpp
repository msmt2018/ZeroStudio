// 链接服务器 JNI 接口
//
// 提供 Java/Kotlin 层调用的 JNI 方法，用于启动链接守护进程。

#include "link_server_protocol.h"
#include <jni.h>
#include <unistd.h>
#include <sys/wait.h>
#include <signal.h>
#include <android/log.h>
#include <string>
#include <cerrno>
#include <cstring>

#define LOG_TAG "LinkServerJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// 声明服务器主函数
extern "C" int link_server_main(const char* nativeLibDir, const char* filesDir);

namespace {
    pid_t g_serverPid = -1;
}

extern "C" {

/**
 * 在应用启动最早期 fork 链接服务器守护进程
 *
 * 必须在其他线程启动之前调用此方法，以避免 fork 死锁问题。
 *
 * @param nativeLibDir 包含 liblld_linker.so 的目录
 * @param filesDir 应用的 files 目录
 * @return 服务器进程 PID，失败返回 -1
 */
JNIEXPORT jint JNICALL
Java_com_wuxianggujun_tinaide_core_nativebridge_NativeLoader_forkLinkServer(
    JNIEnv* env,
    jclass clazz,
    jstring nativeLibDir,
    jstring filesDir) {

    if (g_serverPid > 0) {
        // 检查服务器是否仍在运行
        int status;
        pid_t result = waitpid(g_serverPid, &status, WNOHANG);
        if (result == 0) {
            LOGI("Link server already running (pid=%d)", g_serverPid);
            return g_serverPid;
        }
        // 服务器已退出，需要重新 fork
        LOGW("Link server (pid=%d) has exited, restarting...", g_serverPid);
    }

    const char* nativeLibDirStr = env->GetStringUTFChars(nativeLibDir, nullptr);
    const char* filesDirStr = env->GetStringUTFChars(filesDir, nullptr);

    if (!nativeLibDirStr || !filesDirStr) {
        LOGE("Failed to get string arguments");
        if (nativeLibDirStr) env->ReleaseStringUTFChars(nativeLibDir, nativeLibDirStr);
        if (filesDirStr) env->ReleaseStringUTFChars(filesDir, filesDirStr);
        return -1;
    }

    LOGI("Forking link server...");
    LOGI("nativeLibDir: %s", nativeLibDirStr);
    LOGI("filesDir: %s", filesDirStr);

    // 复制字符串（fork 后原字符串可能无效）
    std::string nativeLibDirCopy = nativeLibDirStr;
    std::string filesDirCopy = filesDirStr;

    env->ReleaseStringUTFChars(nativeLibDir, nativeLibDirStr);
    env->ReleaseStringUTFChars(filesDir, filesDirStr);

    // Fork
    pid_t pid = fork();

    if (pid < 0) {
        LOGE("fork failed: %s", strerror(errno));
        return -1;
    }

    if (pid == 0) {
        // ====== 子进程（守护进程）======
        // 注意：不要关闭所有文件描述符，因为这会破坏 Android 日志系统
        // Android 的 __android_log_print 需要与 /dev/log 通信
        // 只关闭明确不需要的 fd（如继承的 socket 连接等）

        // 创建新的会话（但保留日志能力）
        setsid();

        // 运行服务器主循环
        int exitCode = link_server_main(nativeLibDirCopy.c_str(), filesDirCopy.c_str());
        _exit(exitCode);
    }

    // ====== 父进程 ======
    g_serverPid = pid;
    LOGI("Link server forked (pid=%d)", pid);

    // 等待一小段时间确保服务器启动
    usleep(100000);  // 100ms

    return pid;
}

/**
 * 检查链接服务器是否在运行
 *
 * @return true 如果服务器进程存在
 */
JNIEXPORT jboolean JNICALL
Java_com_wuxianggujun_tinaide_core_nativebridge_NativeLoader_isLinkServerRunning(
    JNIEnv* env,
    jclass clazz) {

    if (g_serverPid <= 0) {
        return JNI_FALSE;
    }

    int status;
    pid_t result = waitpid(g_serverPid, &status, WNOHANG);

    if (result == 0) {
        return JNI_TRUE;  // 进程仍在运行
    }

    // 进程已退出
    g_serverPid = -1;
    return JNI_FALSE;
}

/**
 * 终止链接服务器
 */
JNIEXPORT void JNICALL
Java_com_wuxianggujun_tinaide_core_nativebridge_NativeLoader_killLinkServer(
    JNIEnv* env,
    jclass clazz) {

    if (g_serverPid > 0) {
        LOGI("Killing link server (pid=%d)", g_serverPid);
        kill(g_serverPid, SIGTERM);

        // 等待子进程退出
        int status;
        waitpid(g_serverPid, &status, 0);

        g_serverPid = -1;
        LOGI("Link server terminated");
    }
}

/**
 * 获取链接服务器 PID
 *
 * @return 服务器 PID，如果未启动返回 -1
 */
JNIEXPORT jint JNICALL
Java_com_wuxianggujun_tinaide_core_nativebridge_NativeLoader_getLinkServerPid(
    JNIEnv* env,
    jclass clazz) {

    return g_serverPid;
}

} // extern "C"
