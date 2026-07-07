/*
 *  This file is part of android-tree-sitter.
 *
 *  android-tree-sitter library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  android-tree-sitter library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *  along with android-tree-sitter.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.itsaky.androidide.treesitter;

import com.itsaky.androidide.treesitter.annotations.GenerateNativeHeaders;
import com.itsaky.androidide.treesitter.util.TSObjectFactoryProvider;
import dalvik.annotation.optimization.FastNative;
import java.util.Objects;

/**
 * WebAssembly 语言存储。
 *
 * <p>这是 tree-sitter wasm 引擎的 Java 绑定。Wasm store 用于加载和管理基于 WebAssembly 的
 * tree-sitter 语言。加载的 wasm 语言可以像普通语言一样使用，但使用它的 {@link TSParser} 必须分配
 * 一个 {@link TSWasmStore}。
 *
 * <p><b>注意：</b>Wasm 功能需要在编译时通过 CMake 选项 {@code TREE_SITTER_FEATURE_WASM=ON} 启用。
 * 如果未启用，所有方法将返回默认值（创建返回 {@code null}，加载返回 {@code null}，计数返回 {@code 0}）。
 *
 * @author Akash Yadav
 */
public class TSWasmStore extends TSNativeObject {

  protected TSWasmStore(long pointer) {
    super(pointer);
  }

  /**
   * 创建一个新的 {@link TSWasmStore}。
   *
   * @return 新的 wasm store，如果 wasm 功能未启用则返回 {@code null}。
   */
  public static TSWasmStore create() {
    final long pointer = Native.newStore();
    if (pointer == 0) {
      return null;
    }
    return new TSWasmStore(pointer);
  }

  @Override
  protected void closeNativeObj() {
    Native.delete(getNativeObject());
  }

  /**
   * 从 WebAssembly 二进制数据加载 tree-sitter 语言。
   *
   * <p>加载的语言可以像普通语言一样使用，但使用它的 parser 必须分配了 wasm store。
   * 加载的语言可以通过 {@link TSLanguage#isWasm()} 检测。
   *
   * @param name      语言名称（例如 "java"、"python"）。
   * @param wasmBytes wasm 二进制数据。
   * @return 加载的 {@link TSLanguage}，如果加载失败或 wasm 功能未启用则返回 {@code null}。
   */
  public TSLanguage loadLanguage(String name, byte[] wasmBytes) {
    Objects.requireNonNull(name, "language name cannot be null");
    Objects.requireNonNull(wasmBytes, "wasm bytes cannot be null");
    checkAccess();

    final long langPtr = Native.loadLanguage(getNativeObject(), name, wasmBytes, wasmBytes.length);
    if (langPtr == 0) {
      return null;
    }

    return TSLanguage.create(name, langPtr);
  }

  /**
   * 获取此 wasm store 中已实例化的语言数量。
   *
   * @return 语言数量，如果 wasm 功能未启用则返回 {@code 0}。
   */
  public int getLanguageCount() {
    checkAccess();
    return Native.languageCount(getNativeObject());
  }

  @GenerateNativeHeaders(fileName = "wasm_store")
  private static class Native {

    /**
     * 创建新的 wasm store。
     *
     * @return wasm store 指针，如果 wasm 功能未启用则返回 {@code 0}。
     */
    @FastNative
    static native long newStore();

    /**
     * 删除 wasm store。
     *
     * @param store wasm store 指针。
     */
    @FastNative
    static native void delete(long store);

    /**
     * 从 wasm 二进制数据加载语言。
     *
     * @param store   wasm store 指针。
     * @param name    语言名称。
     * @param wasm    wasm 二进制数据。
     * @param wasmLen wasm 数据长度。
     * @return 语言指针，如果失败或 wasm 功能未启用则返回 {@code 0}。
     */
    @FastNative
    static native long loadLanguage(long store, String name, byte[] wasm, int wasmLen);

    /**
     * 获取 wasm store 中的语言数量。
     *
     * @param store wasm store 指针。
     * @return 语言数量，如果 wasm 功能未启用则返回 {@code 0}。
     */
    @FastNative
    static native int languageCount(long store);
  }
}
