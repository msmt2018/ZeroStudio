// Clang 编译器接口
// 提供语法检查和目标文件生成功能

#ifndef TINAIDE_CLANG_COMPILER_H
#define TINAIDE_CLANG_COMPILER_H

#include <string>
#include <vector>

namespace tinaide {
namespace compiler {

// 编译选项结构
struct CompileOptions {
    std::string sysroot;                    // Sysroot 路径
    std::string target;                     // 目标三元组（如 aarch64-linux-android24）
    bool isCxx = false;                     // 是否为 C++ 代码
    std::vector<std::string> includeDirs;   // 额外的头文件搜索目录
    std::vector<std::string> flags;         // 额外的编译标志
};

// 编译结果结构
struct CompileResult {
    bool success = false;                   // 是否成功
    std::string errorMessage;               // 错误信息（如果失败）
};

// 执行语法检查（不生成目标文件）
// @param srcPath 源文件路径
// @param options 编译选项
// @return 编译结果
CompileResult syntaxCheck(const std::string& srcPath, const CompileOptions& options);

// 编译源文件生成目标文件
// @param srcPath 源文件路径
// @param objPath 输出的目标文件路径
// @param options 编译选项
// @return 编译结果
CompileResult compileToObject(const std::string& srcPath, const std::string& objPath,
                               const CompileOptions& options);

} // namespace compiler
} // namespace tinaide

#endif // TINAIDE_CLANG_COMPILER_H
