#!/usr/bin/env python3
"""
v2.4 P0: Release preview APK/AAR 体积报告.

执行 `./gradlew :modules:compose-preview:assembleRelease` 后跑本脚本, 报告 R8 优化后体积,
输出到 docs/RELEASE-SIZE.md (或 stdout, --out 控制).

Usage:
  python3 scripts/report-release-size.py [--out PATH] [--baseline PATH]
"""
import argparse
import os
import re
import sys
from pathlib import Path

def find_artifacts(build_dir: Path):
    """在 build/outputs/ 找 aar / apk."""
    aars = list((build_dir / "outputs/aar").glob("*.aar")) if (build_dir / "outputs/aar").exists() else []
    apks = list((build_dir / "outputs/apk/release").glob("*.apk")) if (build_dir / "outputs/apk/release").exists() else []
    return aars, apks

def human_size(n: int) -> str:
    for unit in ["B", "KB", "MB", "GB"]:
        if n < 1024:
            return f"{n:.1f} {unit}"
        n /= 1024
    return f"{n:.1f} TB"

def parse_baseline(path: Path):
    """解析 baseline 文件 (key=value 格式)."""
    if not path.exists():
        return None
    result = {}
    for line in path.read_text().splitlines():
        if "=" in line:
            k, v = line.split("=", 1)
            try:
                result[k.strip()] = int(v.strip())
            except ValueError:
                pass
    return result

def main():
    parser = argparse.ArgumentParser(description="v2.4 P0 release preview 体积报告")
    parser.add_argument("--build-dir", type=Path,
                        default=Path("modules/compose-preview/build"),
                        help="build 目录 (默认 modules/compose-preview/build)")
    parser.add_argument("--out", type=Path, default=None,
                        help="输出 markdown 报告路径 (默认 stdout)")
    parser.add_argument("--baseline", type=Path, default=None,
                        help="baseline 文件路径 (key=value, 用于 diff)")
    args = parser.parse_args()

    aars, apks = find_artifacts(args.build_dir)
    if not aars and not apks:
        print(f"⚠️  No artifacts found in {args.build_dir}/outputs/", file=sys.stderr)
        print("   Run `./gradlew :modules:compose-preview:assembleRelease` first.", file=sys.stderr)
        sys.exit(1)

    # 报告
    lines = []
    lines.append("# Compose Preview Release 体积报告 (v2.4 P0)")
    lines.append("")
    lines.append(f"构建目录: `{args.build_dir}`")
    lines.append("")
    lines.append("## 产物")
    lines.append("")
    lines.append("| 类型 | 路径 | 大小 |")
    lines.append("| --- | --- | --- |")
    for aar in aars:
        lines.append(f"| AAR | `{aar}` | {human_size(aar.stat().st_size)} |")
    for apk in apks:
        lines.append(f"| APK | `{apk}` | {human_size(apk.stat().st_size)} |")
    lines.append("")

    # R8 mapping (如果存在)
    mapping = args.build_dir / "outputs/mapping/release/mapping.txt"
    if mapping.exists():
        lines.append("## R8 Mapping")
        lines.append("")
        lines.append(f"`{mapping}` ({human_size(mapping.stat().st_size)})")
        # 数 obfuscation 数量
        text = mapping.read_text()
        renames = sum(1 for line in text.splitlines() if "->" in line)
        lines.append(f"包含 {renames} 个 obfuscation mapping.")
        lines.append("")

    # baseline diff
    if args.baseline:
        baseline = parse_baseline(args.baseline)
        if baseline:
            lines.append("## 与 baseline 对比")
            lines.append("")
            lines.append(f"Baseline: `{args.baseline}`")
            lines.append("")
            lines.append("| 产物 | 当前 | baseline | 差异 |")
            lines.append("| --- | --- | --- | --- |")
            for aar in aars:
                key = f"aar:{aar.name}"
                cur = aar.stat().st_size
                base = baseline.get(key, 0)
                if base:
                    delta = cur - base
                    sign = "+" if delta >= 0 else ""
                    lines.append(f"| {aar.name} | {human_size(cur)} | {human_size(base)} | {sign}{human_size(delta)} |")
            for apk in apks:
                key = f"apk:{apk.name}"
                cur = apk.stat().st_size
                base = baseline.get(key, 0)
                if base:
                    delta = cur - base
                    sign = "+" if delta >= 0 else ""
                    lines.append(f"| {apk.name} | {human_size(cur)} | {human_size(base)} | {sign}{human_size(delta)} |")
            lines.append("")

    text = "\n".join(lines)

    if args.out:
        args.out.parent.mkdir(parents=True, exist_ok=True)
        args.out.write_text(text)
        print(f"✅ Wrote report to {args.out}")
    else:
        print(text)

if __name__ == "__main__":
    main()
