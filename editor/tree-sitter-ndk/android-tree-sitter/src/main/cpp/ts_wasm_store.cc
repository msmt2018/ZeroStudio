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

#include <jni.h>

#include "tree_sitter/api.h"
#include "utils/ts_preconditions.h"
#include "ts__log.h"

#include "ts_wasm_store.h"

// 创建新的 wasm store
// 当未启用 TREE_SITTER_FEATURE_WASM 时，ts_wasm_store_new 无实现，返回 0
static jlong TSWasmStore_newStore(JNIEnv *env, jclass self) {
#ifdef TREE_SITTER_FEATURE_WASM
  TSWasmError error;
  TSWasmStore *store = ts_wasm_store_new(nullptr, &error);
  if (store == nullptr) {
    LOGE(LOG_TAG, "Failed to create wasm store: %s",
         error.message != nullptr ? error.message : "unknown error");
    return 0;
  }
  return (jlong) store;
#else
  LOGW(LOG_TAG, "Wasm feature is not enabled, cannot create wasm store");
  return 0;
#endif
}

// 删除 wasm store
// ts_wasm_store_delete 在 wasm_store.c 的 #else 部分有 dummy 实现，总是安全调用
static void TSWasmStore_delete(JNIEnv *env, jclass self, jlong store) {
  req_nnp(env, store);
  ts_wasm_store_delete((TSWasmStore *) store);
}

// 从 wasm 二进制数据加载语言
// 当未启用 TREE_SITTER_FEATURE_WASM 时，ts_wasm_store_load_language 无实现，返回 0
static jlong TSWasmStore_loadLanguage(JNIEnv *env,
                                       jclass self,
                                       jlong store,
                                       jstring name,
                                       jbyteArray wasm,
                                       jint wasmLen) {
  req_nnp(env, store);
  req_nnp(env, name);
  req_nnp(env, wasm);

#ifdef TREE_SITTER_FEATURE_WASM
  auto lang_name = env->GetStringUTFChars(name, nullptr);
  jbyte *wasm_data = env->GetByteArrayElements(wasm, nullptr);

  TSWasmError error;
  const TSLanguage *language = ts_wasm_store_load_language(
      (TSWasmStore *) store,
      lang_name,
      reinterpret_cast<const char *>(wasm_data),
      (uint32_t) wasmLen,
      &error);

  env->ReleaseStringUTFChars(name, lang_name);
  env->ReleaseByteArrayElements(wasm, wasm_data, JNI_ABORT);

  if (language == nullptr) {
    LOGE(LOG_TAG, "Failed to load wasm language '%s': %s",
         lang_name,
         error.message != nullptr ? error.message : "unknown error");
    return 0;
  }

  LOGD(LOG_TAG, "Loaded wasm language '%s'", lang_name);
  return (jlong) language;
#else
  LOGW(LOG_TAG, "Wasm feature is not enabled, cannot load wasm language");
  return 0;
#endif
}

// 获取 wasm store 中的语言数量
// 当未启用 TREE_SITTER_FEATURE_WASM 时，ts_wasm_store_language_count 无实现，返回 0
static jint TSWasmStore_languageCount(JNIEnv *env, jclass self, jlong store) {
  req_nnp(env, store);

#ifdef TREE_SITTER_FEATURE_WASM
  return (jint) ts_wasm_store_language_count((TSWasmStore *) store);
#else
  return 0;
#endif
}

void TSWasmStore_Native__SetJniMethods(JNINativeMethod *methods, int count) {
  SET_JNI_METHOD(methods, TSWasmStore_Native_newStore, TSWasmStore_newStore);
  SET_JNI_METHOD(methods, TSWasmStore_Native_delete, TSWasmStore_delete);
  SET_JNI_METHOD(methods, TSWasmStore_Native_loadLanguage, TSWasmStore_loadLanguage);
  SET_JNI_METHOD(methods, TSWasmStore_Native_languageCount, TSWasmStore_languageCount);
}
