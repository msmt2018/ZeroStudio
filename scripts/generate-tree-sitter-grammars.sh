#!/usr/bin/env bash
# ============================================================================
# Tree-sitter 语法源码生成脚本
#
# 功能: 从各语言的 grammar.js 生成 parser.c / scanner.c 等 C/C++ 源码，
#       并复制到 editor/tree-sitter-ndk/{kotlin,java}/src/main/cpp/ 目录下。
#
# 依赖:
#   - Rust 工具链 (cargo)  — 用于安装 tree-sitter CLI
#   - Node.js >= 18         — tree-sitter CLI 需要 Node.js 来执行 grammar.js
#   - git                   — 克隆语法仓库
#
# 用法:
#   ./scripts/generate-tree-sitter-grammars.sh          # 生成全部语言
#   ./scripts/generate-tree-sitter-grammars.sh kotlin   # 仅生成 kotlin
#   ./scripts/generate-tree-sitter-grammars.sh java     # 仅生成 java
# ============================================================================

set -euo pipefail

# ----------------------------------------------------------------------------
# 配置区
# ----------------------------------------------------------------------------

# 项目根目录 (脚本所在目录的上一级)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

# tree-sitter-ndk 根目录
NDK_ROOT="${PROJECT_ROOT}/editor/tree-sitter-ndk"

# 临时工作目录 (克隆语法仓库用)
WORK_DIR="${WORK_DIR:-/tmp/tree-sitter-grammar-gen}"

# tree-sitter CLI 版本 (与 tree-sitter-lib 版本对齐)
TS_CLI_VERSION="${TS_CLI_VERSION:-0.25.4}"

# 语法仓库定义
# 格式: "语言名|git仓库URL|仓库子目录(含grammar.js)|目标cpp目录"
declare -a GRAMMARS=(
  "kotlin|https://github.com/fwcd/tree-sitter-kotlin.git|src|${NDK_ROOT}/kotlin/src/main/cpp"
  "java|https://github.com/tree-sitter/tree-sitter-java.git|src|${NDK_ROOT}/java/src/main/cpp"
)

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

log_info()  { echo -e "${GREEN}[INFO]${NC}  $*"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
log_error() { echo -e "${RED}[ERROR]${NC} $*"; }
log_step()  { echo -e "${BLUE}[STEP]${NC}  $*"; }

# ----------------------------------------------------------------------------
# 前置检查
# ----------------------------------------------------------------------------

check_dependencies() {
  log_step "检查依赖工具..."

  local missing=0

  if ! command -v cargo &>/dev/null; then
    log_error "未找到 cargo (Rust 工具链)。请先安装 Rust: https://rustup.rs"
    missing=1
  fi

  if ! command -v node &>/dev/null; then
    log_error "未找到 node (Node.js)。请先安装 Node.js >= 18"
    missing=1
  fi

  if ! command -v git &>/dev/null; then
    log_error "未找到 git。请先安装 git"
    missing=1
  fi

  if [[ ${missing} -ne 0 ]]; then
    exit 1
  fi

  log_info "cargo:  $(cargo --version)"
  log_info "node:   $(node --version)"
  log_info "git:    $(git --version)"
}

# ----------------------------------------------------------------------------
# 安装 tree-sitter CLI
# ----------------------------------------------------------------------------

install_tree_sitter_cli() {
  log_step "安装 tree-sitter CLI (v${TS_CLI_VERSION})..."

  # 检查是否已安装且版本匹配
  if command -v tree-sitter &>/dev/null; then
    local installed_ver
    installed_ver=$(tree-sitter --version 2>/dev/null | grep -oE '[0-9]+\.[0-9]+\.[0-9]+' || echo "")
    if [[ "${installed_ver}" == "${TS_CLI_VERSION}" ]]; then
      log_info "tree-sitter CLI v${TS_CLI_VERSION} 已安装，跳过"
      return 0
    fi
    log_warn "已安装 tree-sitter v${installed_ver}，将重新安装 v${TS_CLI_VERSION}"
  fi

  cargo install "tree-sitter-cli@${TS_CLI_VERSION}" --locked --force
  log_info "tree-sitter CLI 安装完成: $(tree-sitter --version)"
}

# ----------------------------------------------------------------------------
# 生成单个语言的源码
# ----------------------------------------------------------------------------

generate_grammar() {
  local lang="$1"
  local repo_url="$2"
  local grammar_subdir="$3"
  local target_cpp_dir="$4"

  log_step "生成 [${lang}] 语法源码..."

  local repo_name
  repo_name=$(basename "${repo_url}" .git)
  local repo_dir="${WORK_DIR}/${repo_name}"

  # 克隆或更新仓库
  if [[ -d "${repo_dir}/.git" ]]; then
    log_info "仓库已存在，拉取最新代码: ${repo_dir}"
    git -C "${repo_dir}" fetch --depth 1 origin
    git -C "${repo_dir}" reset --hard origin/HEAD
  else
    log_info "克隆语法仓库: ${repo_url}"
    rm -rf "${repo_dir}"
    git clone --depth 1 "${repo_url}" "${repo_dir}"
  fi

  local grammar_dir="${repo_dir}/${grammar_subdir}"
  if [[ ! -f "${grammar_dir}/grammar.js" ]]; then
    log_error "未找到 grammar.js: ${grammar_dir}/grammar.js"
    exit 1
  fi

  log_info "grammar.js 路径: ${grammar_dir}/grammar.js"

  # 运行 tree-sitter generate 生成 parser.c
  log_info "执行 tree-sitter generate..."
  pushd "${repo_dir}" >/dev/null
  tree-sitter generate --libdir "${grammar_subdir}"
  popd >/dev/null

  # 确保目标目录存在
  mkdir -p "${target_cpp_dir}"

  # 复制 parser.c
  local parser_src="${grammar_dir}/parser.c"
  if [[ ! -f "${parser_src}" ]]; then
    log_error "生成失败: 未找到 ${parser_src}"
    exit 1
  fi
  cp -f "${parser_src}" "${target_cpp_dir}/parser.c"
  log_info "复制 parser.c -> ${target_cpp_dir}/parser.c ($(du -h "${target_cpp_dir}/parser.c" | cut -f1))"

  # 复制 scanner.c 或 scanner.cc (如果存在)
  local scanner_c="${grammar_dir}/scanner.c"
  local scanner_cc="${grammar_dir}/scanner.cc"
  if [[ -f "${scanner_c}" ]]; then
    cp -f "${scanner_c}" "${target_cpp_dir}/scanner.c"
    log_info "复制 scanner.c -> ${target_cpp_dir}/scanner.c ($(du -h "${target_cpp_dir}/scanner.c" | cut -f1))"
  elif [[ -f "${scanner_cc}" ]]; then
    cp -f "${scanner_cc}" "${target_cpp_dir}/scanner.cc"
    log_info "复制 scanner.cc -> ${target_cpp_dir}/scanner.cc ($(du -h "${target_cpp_dir}/scanner.cc" | cut -f1))"
  else
    log_warn "[${lang}] 无外部 scanner 文件 (某些语言不需要)"
  fi

  # 复制 node-types.json (调试/文档用，可选)
  local node_types="${grammar_dir}/node-types.json"
  if [[ -f "${node_types}" ]]; then
    cp -f "${node_types}" "${target_cpp_dir}/node-types.json"
    log_info "复制 node-types.json -> ${target_cpp_dir}/node-types.json"
  fi

  log_info "[${lang}] 语法源码生成完成"
  echo ""
}

# ----------------------------------------------------------------------------
# 主流程
# ----------------------------------------------------------------------------

main() {
  local filter="${1:-}"

  echo "============================================================"
  log_info "Tree-sitter 语法源码生成脚本"
  log_info "项目根目录: ${PROJECT_ROOT}"
  log_info "NDK 根目录: ${NDK_ROOT}"
  log_info "工作目录:   ${WORK_DIR}"
  echo "============================================================"
  echo ""

  check_dependencies
  echo ""

  install_tree_sitter_cli
  echo ""

  mkdir -p "${WORK_DIR}"

  local generated=0
  for entry in "${GRAMMARS[@]}"; do
    IFS='|' read -r lang repo_url grammar_subdir target_cpp_dir <<< "${entry}"

    # 如果指定了语言过滤器，只处理匹配的语言
    if [[ -n "${filter}" && "${lang}" != "${filter}" ]]; then
      continue
    fi

    generate_grammar "${lang}" "${repo_url}" "${grammar_subdir}" "${target_cpp_dir}"
    generated=$((generated + 1))
  done

  if [[ ${generated} -eq 0 ]]; then
    log_error "未生成任何语言源码。可用语言: kotlin, java"
    exit 1
  fi

  echo "============================================================"
  log_info "全部完成! 共生成 ${generated} 个语言的语法源码。"
  log_info "生成的文件位于各语言的 src/main/cpp/ 目录下。"
  echo "============================================================"
}

main "$@"
