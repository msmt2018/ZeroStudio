// 链接服务器协议定义
//
// 定义主进程与链接守护进程之间的 IPC 通信协议。
// 使用 Unix Domain Socket 进行通信，消息格式为简单的长度前缀 + JSON。

#ifndef TINAIDE_LINK_SERVER_PROTOCOL_H
#define TINAIDE_LINK_SERVER_PROTOCOL_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

// ============================================================================
// 协议版本
// ============================================================================

#define LINK_SERVER_PROTOCOL_VERSION 1

// ============================================================================
// 消息类型
// ============================================================================

typedef enum {
    // 请求类型
    LINK_MSG_LINK_SHARED = 1,       // 链接共享库请求
    LINK_MSG_LINK_EXECUTABLE = 2,   // 链接可执行文件请求
    LINK_MSG_PING = 3,              // Ping（心跳检测）
    LINK_MSG_SHUTDOWN = 4,          // 关闭服务器

    // 响应类型
    LINK_MSG_RESPONSE = 100,        // 链接响应
    LINK_MSG_PONG = 101,            // Pong（心跳响应）
    LINK_MSG_ERROR = 102,           // 错误响应
} LinkMessageType;

// ============================================================================
// 消息头
// ============================================================================

// 消息头（固定 8 字节）
typedef struct {
    uint32_t length;                // 消息体长度（不含消息头）
    uint16_t type;                  // 消息类型（LinkMessageType）
    uint16_t version;               // 协议版本
} LinkMessageHeader;

// ============================================================================
// Socket 路径
// ============================================================================

// 获取 socket 路径（使用抽象命名空间）
// 返回格式: "\0tinaide_linkd_<uid>"
// 调用者需要提供足够大的 buffer（建议 108 字节）
int link_server_get_socket_path(char* buffer, size_t buffer_size);

// ============================================================================
// JSON 消息格式
// ============================================================================

/*
链接请求 JSON 格式:
{
    "obj_paths": ["/path/to/obj1.o", "/path/to/obj2.o"],
    "output_path": "/path/to/output.so",
    "sysroot": "/path/to/sysroot",
    "target": "aarch64-linux-android24",
    "is_cxx": true,
    "extra_lib_dirs": ["/path/to/libs"],
    "extra_libs": ["mylib"]
}

链接响应 JSON 格式:
{
    "success": true,
    "exit_code": 0,
    "error_message": "",
    "diagnostics": ""
}

Ping 请求: {} 或空
Pong 响应: {"alive": true}

错误响应:
{
    "error": "error message"
}
*/

#ifdef __cplusplus
}
#endif

#endif // TINAIDE_LINK_SERVER_PROTOCOL_H
