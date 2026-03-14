// 链接服务器 IPC 客户端接口
//
// 提供与链接守护进程通信的客户端接口。

#ifndef TINAIDE_LINK_CLIENT_H
#define TINAIDE_LINK_CLIENT_H

#include "../linker/lld_linker.h"
#include <string>
#include <vector>

namespace tinaide {
namespace linker {
namespace client {

// 通过 IPC 链接共享库
// @param objPaths 目标文件路径列表
// @param soPath 输出的共享库路径
// @param options 链接选项
// @return 链接结果
LinkResult linkSharedViaServer(
    const std::vector<std::string>& objPaths,
    const std::string& soPath,
    const LinkOptions& options);

// 通过 IPC 链接可执行文件
// @param objPaths 目标文件路径列表
// @param exePath 输出的可执行文件路径
// @param options 链接选项
// @return 链接结果
LinkResult linkExecutableViaServer(
    const std::vector<std::string>& objPaths,
    const std::string& exePath,
    const LinkOptions& options);

// Ping 服务器检查是否存活
// @return true 如果服务器响应
bool pingServer();

// 请求服务器关闭
void shutdownServer();

// 关闭客户端连接
void closeConnection();

} // namespace client
} // namespace linker
} // namespace tinaide

#endif // TINAIDE_LINK_CLIENT_H
