// Clang 编译器实现

#include "clang_compiler.h"
#include "llvm_target_init.h"
#include "../utils/file_utils.h"
#include "../utils/logging.h"

#if LLVM_HEADERS_AVAILABLE
#include "clang/Frontend/CompilerInstance.h"
#include "clang/Frontend/CompilerInvocation.h"
#include "clang/Frontend/TextDiagnosticPrinter.h"
#include "clang/FrontendTool/Utils.h"
#include "clang/Basic/Diagnostic.h"
#include "clang/Basic/DiagnosticOptions.h"
#include "llvm/ADT/IntrusiveRefCntPtr.h"
#include "llvm/Support/Host.h"
#include "llvm/Support/raw_ostream.h"
#endif

#include <sstream>

namespace tinaide {
namespace compiler {

#if !LLVM_HEADERS_AVAILABLE

// LLVM 头文件不可用时的占位实现
CompileResult syntaxCheck(const std::string& srcPath, const CompileOptions& options) {
    CompileResult result;
    result.success = false;
    result.errorMessage = "UNAVAILABLE: syntaxCheck requires LLVM headers (in-process)";
    return result;
}

CompileResult compileToObject(const std::string& srcPath, const std::string& objPath,
                               const CompileOptions& options) {
    CompileResult result;
    result.success = false;
    result.errorMessage = "UNAVAILABLE: LLVM headers not found (run tools/sync-llvm-headers.ps1)";
    return result;
}

#else

// 构建编译参数
static std::vector<std::string> buildCompilerArgs(
    const std::string& srcPath,
    const CompileOptions& options,
    bool syntaxOnly,
    const std::string& objPath = "") {

    std::vector<std::string> args;

    // 基础参数
    args.push_back("-cc1");

    // 目标三元组
    std::string target = options.target.empty()
        ? llvm::sys::getDefaultTargetTriple()
        : options.target;
    args.push_back("-triple");
    args.push_back(target);

    // 语法检查或生成目标文件
    if (syntaxOnly) {
        args.push_back("-fsyntax-only");
    } else {
        args.push_back("-emit-obj");
        args.push_back("-O2");
        // Android 要求位置无关代码
        args.push_back("-mrelocation-model");
        args.push_back("pic");
    }

    // 禁用内建头文件
    args.push_back("-nobuiltininc");

    // Sysroot 配置
    if (!options.sysroot.empty()) {
        args.push_back("-isysroot");
        args.push_back(options.sysroot);

        // Clang 资源目录
        std::string resourceDir = options.sysroot + "/lib/clang/17";
        args.push_back("-resource-dir");
        args.push_back(resourceDir);

        // 内部系统头文件路径
        args.push_back("-internal-isystem");
        args.push_back(resourceDir + "/include");
    }

    // 语言类型
    args.push_back("-x");
    args.push_back(options.isCxx ? "c++" : "c");

    // C++ 标准
    if (options.isCxx) {
        args.push_back("-std=c++17");
    }

    // Android 宏定义
    args.push_back("-DANDROID");
    args.push_back("-D__ANDROID__");

    // 系统头文件路径
    if (!options.sysroot.empty()) {
        std::string tripleBase = utils::deriveTripleBase(target);

        args.push_back("-isystem");
        args.push_back(options.sysroot + "/usr/include");

        if (!tripleBase.empty()) {
            args.push_back("-isystem");
            args.push_back(options.sysroot + "/usr/include/" + tripleBase);
        }

        // C++ 标准库头文件
        args.push_back("-I");
        args.push_back(options.sysroot + "/usr/include/c++/v1");
    }

    // 额外的头文件搜索目录
    for (const auto& includeDir : options.includeDirs) {
        if (!includeDir.empty()) {
            args.push_back("-I");
            args.push_back(includeDir);
        }
    }

    // 额外的编译标志
    for (const auto& flag : options.flags) {
        if (!flag.empty()) {
            args.push_back(flag);
        }
    }

    // 输出文件
    if (!syntaxOnly && !objPath.empty()) {
        args.push_back("-o");
        args.push_back(objPath);
    }

    // 源文件
    args.push_back(srcPath);

    return args;
}

// 执行编译
static CompileResult executeCompiler(const std::vector<std::string>& argStrings) {
    CompileResult result;

    // 初始化 LLVM 目标
    static bool targetsInitialized = false;
    if (!targetsInitialized) {
        initLLVMTargetsOnce();
        targetsInitialized = true;
    }

    // 转换为 C 风格参数数组
    std::vector<const char*> args;
    args.reserve(argStrings.size());
    for (const auto& arg : argStrings) {
        args.push_back(arg.c_str());
    }

    // 创建诊断选项
    llvm::IntrusiveRefCntPtr<clang::DiagnosticOptions> diagOpts =
        new clang::DiagnosticOptions();

    // 捕获诊断输出
    std::string diagStr;
    llvm::raw_string_ostream diagStream(diagStr);

    llvm::IntrusiveRefCntPtr<clang::DiagnosticIDs> diagIDs(new clang::DiagnosticIDs());
    auto printer = std::make_unique<clang::TextDiagnosticPrinter>(diagStream, &*diagOpts);
    clang::DiagnosticsEngine diags(diagIDs, &*diagOpts, printer.get(), false);

    // 创建编译器调用
    std::unique_ptr<clang::CompilerInvocation> invocation(new clang::CompilerInvocation());
    if (!clang::CompilerInvocation::CreateFromArgs(*invocation, args, diags)) {
        diagStream.flush();
        result.success = false;
        result.errorMessage = diagStr.empty() ? "create invocation failed" : diagStr;
        return result;
    }

    // 创建编译器实例
    clang::CompilerInstance compiler;
    compiler.setInvocation(std::move(invocation));
    compiler.createDiagnostics(printer.release(), true);

    // 执行编译
    bool success = clang::ExecuteCompilerInvocation(&compiler);
    diagStream.flush();

    result.success = success;
    if (!success) {
        result.errorMessage = diagStr.empty() ? "compilation failed" : diagStr;
    }

    return result;
}

CompileResult syntaxCheck(const std::string& srcPath, const CompileOptions& options) {
    LOGI("syntaxCheck: %s", srcPath.c_str());

    auto args = buildCompilerArgs(srcPath, options, true);
    return executeCompiler(args);
}

CompileResult compileToObject(const std::string& srcPath, const std::string& objPath,
                               const CompileOptions& options) {
    LOGI("compileToObject: %s -> %s", srcPath.c_str(), objPath.c_str());

    // 确保输出目录存在
    if (!utils::ensureParentDir(objPath)) {
        CompileResult result;
        result.success = false;
        result.errorMessage = "[TinaIDE] Failed to prepare directory for output file: " + objPath;
        return result;
    }

    auto args = buildCompilerArgs(srcPath, options, false, objPath);

#ifndef NDEBUG
    // Debug 模式下打印编译参数
    std::ostringstream oss;
    oss << "cc1 args (" << args.size() << "):";
    for (const auto& arg : args) {
        oss << " " << arg;
    }
    LOGI("%s", oss.str().c_str());
#endif

    return executeCompiler(args);
}

#endif // LLVM_HEADERS_AVAILABLE

} // namespace compiler
} // namespace tinaide
