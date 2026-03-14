// LLVM 目标初始化工具
// 根据当前架构初始化对应的 LLVM 后端，避免链接期未定义符号

#ifndef TINAIDE_LLVM_TARGET_INIT_H
#define TINAIDE_LLVM_TARGET_INIT_H

#if LLVM_HEADERS_AVAILABLE

extern "C" {
#if defined(__aarch64__)
void LLVMInitializeAArch64TargetInfo();
void LLVMInitializeAArch64Target();
void LLVMInitializeAArch64TargetMC();
void LLVMInitializeAArch64AsmParser();
void LLVMInitializeAArch64AsmPrinter();
#elif defined(__x86_64__) || defined(_M_X64) || defined(__i386__) || defined(_M_IX86)
void LLVMInitializeX86TargetInfo();
void LLVMInitializeX86Target();
void LLVMInitializeX86TargetMC();
void LLVMInitializeX86AsmParser();
void LLVMInitializeX86AsmPrinter();
#endif
}

namespace tinaide {
namespace compiler {

// 初始化 LLVM 目标后端（仅执行一次）
// 根据编译时的架构宏定义，初始化对应的 LLVM 目标后端
inline void initLLVMTargetsOnce() {
    static bool initialized = false;
    if (initialized) {
        return;
    }
    initialized = true;

#if defined(__aarch64__)
    LLVMInitializeAArch64TargetInfo();
    LLVMInitializeAArch64Target();
    LLVMInitializeAArch64TargetMC();
    LLVMInitializeAArch64AsmParser();
    LLVMInitializeAArch64AsmPrinter();
#elif defined(__x86_64__) || defined(_M_X64) || defined(__i386__) || defined(_M_IX86)
    LLVMInitializeX86TargetInfo();
    LLVMInitializeX86Target();
    LLVMInitializeX86TargetMC();
    LLVMInitializeX86AsmParser();
    LLVMInitializeX86AsmPrinter();
#else
    // 未知架构：不初始化，避免未定义引用
#endif
}

} // namespace compiler
} // namespace tinaide

#endif // LLVM_HEADERS_AVAILABLE

#endif // TINAIDE_LLVM_TARGET_INIT_H
