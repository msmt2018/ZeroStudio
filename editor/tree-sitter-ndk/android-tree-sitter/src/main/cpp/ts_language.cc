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

#include <dlfcn.h>

#include "tree_sitter/api.h"
#include "utils/ts_obj_utils.h"
#include "ts__log.h"
#include "utils/ts_preconditions.h"

#include "ts_language.h"

typedef const TSLanguage *(*TsLangFunc)();

static jint TSLanguage_symCount(JNIEnv *env, jclass self, jlong ptr) {
  req_nnp(env, ptr);
  return (jint) ts_language_symbol_count((TSLanguage *) ptr);
}

static jint TSLanguage_fldCount(JNIEnv *env, jclass self, jlong ptr) {
  req_nnp(env, ptr);
  return (jint) ts_language_field_count((TSLanguage *) ptr);
}


static jint TSLanguage_symForName(JNIEnv *env,
                                  jclass self,
                                  jlong ptr,
                                  jbyteArray name,
                                  jint length,
                                  jboolean isNamed) {
  req_nnp(env, ptr);
  jbyte *nm = env->GetByteArrayElements(name, NULL);
  uint32_t count = ts_language_symbol_for_name((TSLanguage *) ptr,
                                               reinterpret_cast<const char *>(nm),
                                               length,
                                               isNamed);
  env->ReleaseByteArrayElements(name, nm, JNI_ABORT);
  return (jint) count;
}


static jstring
TSLanguage_symName(JNIEnv *env, jclass self, jlong lngPtr, jint sym) {
  req_nnp(env, lngPtr);
  const char *name = ts_language_symbol_name((TSLanguage *) lngPtr, sym);
  if (name == nullptr) return nullptr;
  return env->NewStringUTF(name);
}


static jstring
TSLanguage_fldNameForId(JNIEnv *env, jclass self, jlong ptr, jint id) {
  req_nnp(env, ptr);
  const char *name = ts_language_field_name_for_id((TSLanguage *) ptr, id);
  if (name == nullptr) return nullptr;
  return env->NewStringUTF(name);
}


static jint TSLanguage_fldIdForName(JNIEnv *env,
                                    jclass self,
                                    jlong ptr,
                                    jbyteArray name,
                                    jint length) {
  // M1 修复：先检查 ptr，再获取数组元素（与 symForName 保持一致），
  // 避免 ptr 为 null 时 GetByteArrayElements 的资源泄漏。
  req_nnp(env, ptr);
  jbyte *nm = env->GetByteArrayElements(name, nullptr);
  uint32_t id = ts_language_field_id_for_name((TSLanguage *) ptr,
                                              reinterpret_cast<const char *>(nm),
                                              length);
  env->ReleaseByteArrayElements(name, nm, JNI_ABORT);
  return (jint) id;
}


static jint TSLanguage_symType(JNIEnv *env, jclass self, jlong ptr, jint sym) {
  req_nnp(env, ptr);
  return (jint) ts_language_symbol_type((TSLanguage *) ptr, sym);
}


static jint TSLanguage_langVer(JNIEnv *env, jclass self, jlong ptr) {
  req_nnp(env, ptr);
  // 0.27 中 ts_language_version 被重命名为 ts_language_abi_version
  return (jint) ts_language_abi_version((TSLanguage *) ptr);
}

static jlongArray TSLanguage_loadLanguage(JNIEnv *env,
                                          jclass clazz,
                                          jstring libpath,
                                          jstring func) {
  auto lib_path = env->GetStringUTFChars(libpath, nullptr);
  auto func_name = env->GetStringUTFChars(func, nullptr);

  auto handle = dlopen(lib_path, RTLD_LAZY);
  if (handle == nullptr) {
    LOGE(LOG_TAG, "Failed to dlopen library '%s': %s", lib_path, dlerror());
    env->ReleaseStringUTFChars(libpath, lib_path);
    env->ReleaseStringUTFChars(func, func_name);
    return nullptr;
  }

  void *func_addr = dlsym(handle, func_name);
  if (func_addr == nullptr) {
    LOGE(LOG_TAG,
         "Cannot find function '%s' to create language instance: %s",
         func_name,
         dlerror());
    env->ReleaseStringUTFChars(libpath, lib_path);
    env->ReleaseStringUTFChars(func, func_name);
    return nullptr;
  }

  auto lang_func = reinterpret_cast<TsLangFunc>(func_addr);
  if (lang_func == nullptr) {
    LOGE(LOG_TAG, "Cannot reinterpreset_cast to TsLangFunc");
    env->ReleaseStringUTFChars(libpath, lib_path);
    env->ReleaseStringUTFChars(func, func_name);
    return nullptr;
  }

  auto language = lang_func();
  if (language == nullptr) {
    LOGE(LOG_TAG, "Function '%s' returned nullptr", func_name);
    // 修复：释放已获取的 JNI 字符串资源和 dlopen handle
    env->ReleaseStringUTFChars(libpath, lib_path);
    env->ReleaseStringUTFChars(func, func_name);
    dlclose(handle);
    return nullptr;
  }

  LOGD(LOG_TAG, "Loaded tree sitter language with function '%s'", func_name);

  env->ReleaseStringUTFChars(libpath, lib_path);
  env->ReleaseStringUTFChars(func, func_name);

  jlong ptrs[2] = {(jlong) language, (jlong) handle};

  auto result = env->NewLongArray(2);
  env->SetLongArrayRegion(result, 0, 2, ptrs);
  return result;
}

static void TSLanguage_dlclose(JNIEnv *env, jclass clazz, jlong libhandle) {
  if (libhandle == 0) return;
  dlclose((void *) libhandle);
}

static jint TSLanguage_stateCount(JNIEnv *env, jclass clazz, jlong pointer) {
  req_nnp(env, pointer);
  return (jint) ts_language_state_count((TSLanguage *) pointer);
}

static jshort TSLanguage_nextState(JNIEnv *env,
                                   jclass clazz,
                                   jlong pointer,
                                   jshort state_id,
                                   jshort symbol) {
  req_nnp(env, pointer);
  return (jshort) ts_language_next_state((TSLanguage *) pointer,
                                         state_id,
                                         symbol);
}

// 获取语言自身报告的名称（v15 新增，v14 及以下语言可能返回 NULL）
static jstring TSLanguage_name(JNIEnv *env, jclass self, jlong ptr) {
  req_nnp(env, ptr);
  const char *name = ts_language_name((TSLanguage *) ptr);
  if (name == nullptr) {
    return nullptr;
  }
  return env->NewStringUTF(name);
}

// 获取语言版本元数据 [major, minor, patch]（v14 及以下语言返回 NULL）
static jintArray TSLanguage_metadata(JNIEnv *env, jclass self, jlong ptr) {
  req_nnp(env, ptr);
  const TSLanguageMetadata *metadata = ts_language_metadata((TSLanguage *) ptr);
  if (metadata == nullptr) {
    return nullptr;
  }
  jint values[3] = {(jint) metadata->major_version,
                    (jint) metadata->minor_version,
                    (jint) metadata->patch_version};
  jintArray result = env->NewIntArray(3);
  req_nnp(env, result, "metadata jintArray");
  env->SetIntArrayRegion(result, 0, 3, values);
  return result;
}

// 获取所有超类型符号 id（v14 及以下语言返回空数组）
static jintArray TSLanguage_supertypes(JNIEnv *env, jclass self, jlong ptr) {
  req_nnp(env, ptr);
  uint32_t length = 0;
  const TSSymbol *supertypes =
      ts_language_supertypes((TSLanguage *) ptr, &length);
  if (supertypes == nullptr || length == 0) {
    return env->NewIntArray(0);
  }
  jintArray result = env->NewIntArray((jsize) length);
  req_nnp(env, result, "supertypes jintArray");
  // TSSymbol 为 uint16_t，需逐个转入 jint 数组
  jint *buf = new jint[length];
  for (uint32_t i = 0; i < length; i++) {
    buf[i] = (jint) supertypes[i];
  }
  env->SetIntArrayRegion(result, 0, (jsize) length, buf);
  delete[] buf;
  return result;
}

// 获取指定超类型的子类型符号 id（v14 及以下语言返回空数组）
static jintArray TSLanguage_subtypes(JNIEnv *env,
                                     jclass self,
                                     jlong ptr,
                                     jint supertype) {
  req_nnp(env, ptr);
  uint32_t length = 0;
  const TSSymbol *subtypes = ts_language_subtypes((TSLanguage *) ptr,
                                                  (TSSymbol) supertype,
                                                  &length);
  if (subtypes == nullptr || length == 0) {
    return env->NewIntArray(0);
  }
  jintArray result = env->NewIntArray((jsize) length);
  req_nnp(env, result, "subtypes jintArray");
  jint *buf = new jint[length];
  for (uint32_t i = 0; i < length; i++) {
    buf[i] = (jint) subtypes[i];
  }
  env->SetIntArrayRegion(result, 0, (jsize) length, buf);
  delete[] buf;
  return result;
}

// 检查语言是否来自 wasm 模块
// ts_language_is_wasm 在 wasm_store.c 的 #else 部分有 dummy 实现，总是安全调用
static jboolean TSLanguage_isWasm(JNIEnv *env, jclass self, jlong ptr) {
  req_nnp(env, ptr);
  return (jboolean) ts_language_is_wasm((TSLanguage *) ptr);
}

// 创建语言的另一个引用（0.27 新增，用于 wasm 语言引用计数管理）
static jlong TSLanguage_copy(JNIEnv *env, jclass self, jlong ptr) {
  req_nnp(env, ptr);
  return (jlong) ts_language_copy((TSLanguage *) ptr);
}

// 删除语言引用（0.27 新增）
// 对于内嵌语言（静态分配），ts_language_delete 是空操作
// 对于 wasm 语言，ts_language_delete 减少引用计数
static void TSLanguage_delete(JNIEnv *env, jclass self, jlong ptr) {
  req_nnp(env, ptr);
  ts_language_delete((TSLanguage *) ptr);
}

void TSLanguage_Native__SetJniMethods(JNINativeMethod *methods, int count) {
  SET_JNI_METHOD(methods, TSLanguage_Native_symCount, TSLanguage_symCount);
  SET_JNI_METHOD(methods, TSLanguage_Native_fldCount, TSLanguage_fldCount);
  SET_JNI_METHOD(methods, TSLanguage_Native_symForName, TSLanguage_symForName);
  SET_JNI_METHOD(methods, TSLanguage_Native_symName, TSLanguage_symName);
  SET_JNI_METHOD(methods, TSLanguage_Native_fldNameForId, TSLanguage_fldNameForId);
  SET_JNI_METHOD(methods, TSLanguage_Native_fldIdForName, TSLanguage_fldIdForName);
  SET_JNI_METHOD(methods, TSLanguage_Native_symType, TSLanguage_symType);
  SET_JNI_METHOD(methods, TSLanguage_Native_langVer, TSLanguage_langVer);
  SET_JNI_METHOD(methods, TSLanguage_Native_loadLanguage, TSLanguage_loadLanguage);
  SET_JNI_METHOD(methods, TSLanguage_Native_dlclose, TSLanguage_dlclose);
  SET_JNI_METHOD(methods, TSLanguage_Native_stateCount, TSLanguage_stateCount);
  SET_JNI_METHOD(methods, TSLanguage_Native_nextState, TSLanguage_nextState);
  SET_JNI_METHOD(methods, TSLanguage_Native_name, TSLanguage_name);
  SET_JNI_METHOD(methods, TSLanguage_Native_metadata, TSLanguage_metadata);
  SET_JNI_METHOD(methods, TSLanguage_Native_supertypes, TSLanguage_supertypes);
  SET_JNI_METHOD(methods, TSLanguage_Native_subtypes, TSLanguage_subtypes);
  SET_JNI_METHOD(methods, TSLanguage_Native_isWasm, TSLanguage_isWasm);
  SET_JNI_METHOD(methods, TSLanguage_Native_copy, TSLanguage_copy);
  SET_JNI_METHOD(methods, TSLanguage_Native_delete, TSLanguage_delete);
}