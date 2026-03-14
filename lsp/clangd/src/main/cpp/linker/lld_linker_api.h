// LLD 链接器纯 C API 定义
//
// 这个头文件定义了 liblld_linker.so 的公共接口。
// 使用纯 C 接口以便于 dlopen/dlclose 动态加载。
//
// 设计目标：
// 1. 每次调用后可以 dlclose() 清理 LLD 全局状态
// 2. 接口简单，便于 IPC 序列化
// 3. 错误信息通过回调或返回结构体传递

#ifndef TINAIDE_LLD_LINKER_API_H
#define TINAIDE_LLD_LINKER_API_H

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

// ============================================================================
// 版本信息
// ============================================================================

#define LLD_LINKER_API_VERSION 1

// ============================================================================
// 数据结构
// ============================================================================

/**
 * 链接选项
 */
typedef struct LldLinkOptions {
    const char* sysroot;           // Sysroot 路径（必需）
    const char* target;            // 目标三元组，如 "aarch64-linux-android24"
    int is_cxx;                    // 是否为 C++ 代码（需要链接 libc++）
    const char** extra_lib_dirs;   // 额外的库搜索目录数组
    size_t extra_lib_dirs_count;   // 额外库目录数量
    const char** extra_libs;       // 额外的链接库数组（不带 -l 前缀）
    size_t extra_libs_count;       // 额外库数量
} LldLinkOptions;

/**
 * 链接结果
 */
typedef struct LldLinkResult {
    int success;                   // 1=成功, 0=失败
    int exit_code;                 // 退出码
    char* error_message;           // 错误信息（调用者需要 free）
    char* diagnostics;             // 诊断输出（调用者需要 free）
} LldLinkResult;

// ============================================================================
// 公共接口
// ============================================================================

/**
 * 获取 API 版本号
 *
 * @return API 版本号
 */
int lld_api_version(void);

/**
 * 链接生成共享库 (.so)
 *
 * @param obj_paths      输入的目标文件路径数组
 * @param obj_count      目标文件数量
 * @param output_path    输出的 .so 文件路径
 * @param options        链接选项
 * @param result         [out] 链接结果，调用者需要调用 lld_free_result() 释放
 */
void lld_link_shared(
    const char** obj_paths,
    size_t obj_count,
    const char* output_path,
    const LldLinkOptions* options,
    LldLinkResult* result
);

/**
 * 链接生成可执行文件
 *
 * @param obj_paths      输入的目标文件路径数组
 * @param obj_count      目标文件数量
 * @param output_path    输出的可执行文件路径
 * @param options        链接选项
 * @param result         [out] 链接结果，调用者需要调用 lld_free_result() 释放
 */
void lld_link_executable(
    const char** obj_paths,
    size_t obj_count,
    const char* output_path,
    const LldLinkOptions* options,
    LldLinkResult* result
);

/**
 * 释放链接结果中分配的内存
 *
 * @param result 链接结果指针
 */
void lld_free_result(LldLinkResult* result);

// ============================================================================
// 函数指针类型定义（用于 dlsym）
// ============================================================================

typedef int (*lld_api_version_fn)(void);
typedef void (*lld_link_shared_fn)(const char**, size_t, const char*, const LldLinkOptions*, LldLinkResult*);
typedef void (*lld_link_executable_fn)(const char**, size_t, const char*, const LldLinkOptions*, LldLinkResult*);
typedef void (*lld_free_result_fn)(LldLinkResult*);

#ifdef __cplusplus
}
#endif

#endif // TINAIDE_LLD_LINKER_API_H
