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

#include <iostream>
#include <mutex>
#include <unordered_map>

#include "utils/ts_obj_utils.h"
#include "utils/ts_preconditions.h"

#include "ts_query_cursor.h"

// ============================================================================
// Query progress callback 反向回调实现
//
// tree-sitter 0.27 的 TSQueryCursorOptions 包含一个 progress_callback 函数指针：
//   typedef struct TSQueryCursorOptions {
//     void *payload;
//     bool (*progress_callback)(TSQueryCursorState *state);
//   } TSQueryCursorOptions;
//
// TSQueryCursorState 包含 payload 和 current_byte_offset。
// 为了让 Java 端能注册 progress callback，我们在 native 层：
//   1. 维护 cursor pointer -> Java TSQueryProgressCallback GlobalRef 的映射
//   2. 提供 java_progress_callback 作为 C 函数指针
//   3. 缓存 JavaVM + TSQueryProgressCallback 接口类 + shouldCancel 方法 ID
// ============================================================================

static JavaVM *g_qc_jvm = nullptr;
static std::mutex g_qc_progress_mutex;

// cursor pointer -> Java TSQueryProgressCallback GlobalRef
static std::unordered_map<jlong, jobject> g_qc_progress_refs;

// 缓存的 Java 类/方法
static jclass g_qc_callback_class = nullptr;        // com/itsaky/androidide/treesitter/TSQueryProgressCallback
static jmethodID g_qc_should_cancel_method = nullptr; // shouldCancel(I)Z
static bool g_qc_cache_inited = false;

static void ensure_qc_progress_cache(JNIEnv *env) {
  if (g_qc_cache_inited) {
    return;
  }
  jclass callback_local = env->FindClass("com/itsaky/androidide/treesitter/TSQueryProgressCallback");
  if (callback_local == nullptr) {
    env->ExceptionClear();
    return;
  }
  g_qc_callback_class = (jclass) env->NewGlobalRef(callback_local);
  env->DeleteLocalRef(callback_local);
  g_qc_should_cancel_method = env->GetMethodID(g_qc_callback_class, "shouldCancel", "(I)Z");
  if (g_qc_should_cancel_method == nullptr) {
    env->ExceptionClear();
  }
  g_qc_cache_inited = true;
}

static JNIEnv *get_jni_env_for_qc(bool *out_attached) {
  if (g_qc_jvm == nullptr) {
    *out_attached = false;
    return nullptr;
  }
  JNIEnv *env = nullptr;
  jint rc = g_qc_jvm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6);
  if (rc == JNI_EDETACHED) {
    if (g_qc_jvm->AttachCurrentThread(&env, nullptr) != JNI_OK) {
      *out_attached = false;
      return nullptr;
    }
    *out_attached = true;
  } else if (rc != JNI_OK) {
    *out_attached = false;
    return nullptr;
  } else {
    *out_attached = false;
  }
  return env;
}

// tree-sitter 调用的 progress 回调
// state->payload 存储的是 cursor pointer (jlong)，用于从 map 查找 Java callback
static bool java_progress_callback(TSQueryCursorState *state) {
  if (state == nullptr || state->payload == nullptr) {
    return false;
  }
  jlong cursor_key = (jlong) reinterpret_cast<intptr_t>(state->payload);

  jobject callback_ref = nullptr;
  {
    std::lock_guard<std::mutex> lock(g_qc_progress_mutex);
    auto it = g_qc_progress_refs.find(cursor_key);
    if (it == g_qc_progress_refs.end()) {
      return false;
    }
    callback_ref = it->second;
  }
  if (callback_ref == nullptr) {
    return false;
  }

  bool attached = false;
  JNIEnv *env = get_jni_env_for_qc(&attached);
  if (env == nullptr) {
    return false;
  }

  if (!g_qc_cache_inited) {
    ensure_qc_progress_cache(env);
  }
  if (g_qc_callback_class == nullptr || g_qc_should_cancel_method == nullptr) {
    if (attached) {
      g_qc_jvm->DetachCurrentThread();
    }
    return false;
  }

  jboolean cancel = env->CallBooleanMethod(callback_ref, g_qc_should_cancel_method,
                                            (jint) state->current_byte_offset);
  if (env->ExceptionCheck()) {
    env->ExceptionDescribe();
    env->ExceptionClear();
    cancel = JNI_FALSE;
  }

  if (attached) {
    g_qc_jvm->DetachCurrentThread();
  }
  return (bool) cancel;
}

// 释放指定 cursor 的 progress callback GlobalRef
static void release_qc_progress_ref(jlong cursor_key) {
  std::lock_guard<std::mutex> lock(g_qc_progress_mutex);
  auto it = g_qc_progress_refs.find(cursor_key);
  if (it == g_qc_progress_refs.end()) {
    return;
  }
  if (g_qc_jvm != nullptr && it->second != nullptr) {
    JNIEnv *env = nullptr;
    if (g_qc_jvm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) == JNI_OK) {
      env->DeleteGlobalRef(it->second);
    }
  }
  g_qc_progress_refs.erase(it);
}

static jlong TSQueryCursor_newCursor(JNIEnv *env, jclass self) {
  // 初始化 JavaVM 缓存，供 progress callback 回调使用
  if (g_qc_jvm == nullptr) {
    env->GetJavaVM(&g_qc_jvm);
  }
  return (jlong) ts_query_cursor_new();
}

static void TSQueryCursor_delete(JNIEnv *env, jclass self, jlong cursor) {
  req_nnp(env, cursor);
  // 释放可能存在的 progress callback GlobalRef
  release_qc_progress_ref(cursor);
  ts_query_cursor_delete((TSQueryCursor *) cursor);
}

static void TSQueryCursor_exec(JNIEnv *env,
                               jclass self,
                               jlong cursor,
                               jlong query,
                               jobject node) {
  req_nnp(env, cursor);
  // exec 会重置 cursor 状态，释放之前可能设置的 progress callback
  release_qc_progress_ref(cursor);
  ts_query_cursor_exec((TSQueryCursor *) cursor,
                       (TSQuery *) query,
                       _unmarshalNode(env, node));
}

// tree-sitter 0.27 API：带 progress_callback 的 exec（用于查询取消）
static void TSQueryCursor_execWithOptions(JNIEnv *env,
                                           jclass self,
                                           jlong cursor,
                                           jlong query,
                                           jobject node,
                                           jobject progressCallback) {
  req_nnp(env, cursor);

  // 释放之前可能设置的 progress callback
  release_qc_progress_ref(cursor);

  ensure_qc_progress_cache(env);

  TSNode ts_node = _unmarshalNode(env, node);

  if (progressCallback == nullptr) {
    // 无 callback，退化为普通 exec
    ts_query_cursor_exec((TSQueryCursor *) cursor, (TSQuery *) query, ts_node);
    return;
  }

  // 创建 GlobalRef 并存入 map
  jobject global_ref = env->NewGlobalRef(progressCallback);
  {
    std::lock_guard<std::mutex> lock(g_qc_progress_mutex);
    g_qc_progress_refs[cursor] = global_ref;
  }

  // 构造 options
  TSQueryCursorOptions options;
  options.payload = reinterpret_cast<void *>(static_cast<intptr_t>(cursor));
  options.progress_callback = java_progress_callback;

  ts_query_cursor_exec_with_options((TSQueryCursor *) cursor,
                                     (TSQuery *) query,
                                     ts_node,
                                     &options);
}

// 释放 execWithOptions 设置的 progress callback GlobalRef
static void TSQueryCursor_releaseProgressCallback(JNIEnv *env,
                                                   jclass self,
                                                   jlong cursor) {
  req_nnp(env, cursor);
  release_qc_progress_ref(cursor);
}

static jboolean
TSQueryCursor_exceededMatchLimit(JNIEnv *env, jclass self, jlong cursor) {
  req_nnp(env, cursor);
  return (jboolean) ts_query_cursor_did_exceed_match_limit((TSQueryCursor *) cursor);
}

static void TSQueryCursor_setMatchLimit(JNIEnv *env,
                                        jclass self,
                                        jlong cursor,
                                        jint newLimit) {
  req_nnp(env, cursor);
  ts_query_cursor_set_match_limit((TSQueryCursor *) cursor, newLimit);
}

static jint
TSQueryCursor_getMatchLimit(JNIEnv *env, jclass self, jlong cursor) {
  req_nnp(env, cursor);
  return (jint) ts_query_cursor_match_limit((TSQueryCursor *) cursor);
}

static jboolean TSQueryCursor_setByteRange(JNIEnv *env,
                                       jclass self,
                                       jlong cursor,
                                       jint start,
                                       jint end) {
  req_nnp(env, cursor);
  // 0.27 的 ts_query_cursor_set_byte_range 返回 bool，表示范围是否合法
  return (jboolean) ts_query_cursor_set_byte_range((TSQueryCursor *) cursor,
                                                    (uint32_t) start,
                                                    (uint32_t) end);
}

static jboolean TSQueryCursor_setPointRange(JNIEnv *env,
                                        jclass self,
                                        jlong cursor,
                                        jobject start,
                                        jobject end) {
  req_nnp(env, cursor);
  // 0.27 的 ts_query_cursor_set_point_range 返回 bool，表示范围是否合法
  return (jboolean) ts_query_cursor_set_point_range((TSQueryCursor *) cursor,
                                                     _unmarshalPoint(env, start),
                                                     _unmarshalPoint(env, end));
}

static jobject TSQueryCursor_nextMatch(JNIEnv *env, jclass self, jlong cursor) {
  req_nnp(env, cursor);
  TSQueryMatch m;
  bool b = ts_query_cursor_next_match((TSQueryCursor *) cursor, &m);
  if (!b) {
    return nullptr;
  }
  return _marshalMatch(env, m);
}

// v15 新增 API：获取下一个 capture（用于高亮）
static jobject TSQueryCursor_nextCapture(JNIEnv *env,
                                          jclass self,
                                          jlong cursor,
                                          jintArray captureIndexOut) {
  req_nnp(env, cursor);
  TSQueryMatch m;
  uint32_t capture_index;
  bool b = ts_query_cursor_next_capture((TSQueryCursor *) cursor, &m, &capture_index);
  if (!b) {
    return nullptr;
  }
  // 写出 capture_index 到 out 参数（检查数组长度避免越界）
  if (captureIndexOut != nullptr && env->GetArrayLength(captureIndexOut) >= 1) {
    jint ci = (jint) capture_index;
    env->SetIntArrayRegion(captureIndexOut, 0, 1, &ci);
  }
  return _marshalMatch(env, m);
}

// v15 新增 API：设置全包含式 byte 范围
static jboolean TSQueryCursor_setContainingByteRange(JNIEnv *env,
                                                     jclass self,
                                                     jlong cursor,
                                                     jint startByte,
                                                     jint endByte) {
  req_nnp(env, cursor);
  return (jboolean) ts_query_cursor_set_containing_byte_range(
      (TSQueryCursor *) cursor, startByte, endByte);
}

// v15 新增 API：设置全包含式 point 范围
static jboolean TSQueryCursor_setContainingPointRange(JNIEnv *env,
                                                      jclass self,
                                                      jlong cursor,
                                                      jobject start,
                                                      jobject end) {
  req_nnp(env, cursor);
  return (jboolean) ts_query_cursor_set_containing_point_range(
      (TSQueryCursor *) cursor,
      _unmarshalPoint(env, start),
      _unmarshalPoint(env, end));
}

// v15 新增 API：限制 pattern 根节点搜索起始深度
static void TSQueryCursor_setMaxStartDepth(JNIEnv *env,
                                           jclass self,
                                           jlong cursor,
                                           jint maxStartDepth) {
  req_nnp(env, cursor);
  ts_query_cursor_set_max_start_depth((TSQueryCursor *) cursor, maxStartDepth);
}

static void
TSQueryCursor_removeMatch(JNIEnv *env, jclass self, jlong cursor, jint id) {
  req_nnp(env, cursor);
  ts_query_cursor_remove_match((TSQueryCursor *) cursor, id);
}

void TSQueryCursor_Native__SetJniMethods(JNINativeMethod *methods, int count) {
  SET_JNI_METHOD(methods, TSQueryCursor_Native_newCursor, TSQueryCursor_newCursor);
  SET_JNI_METHOD(methods, TSQueryCursor_Native_delete, TSQueryCursor_delete);
  SET_JNI_METHOD(methods, TSQueryCursor_Native_exec, TSQueryCursor_exec);
  SET_JNI_METHOD(methods, TSQueryCursor_Native_execWithOptions, TSQueryCursor_execWithOptions);
  SET_JNI_METHOD(methods, TSQueryCursor_Native_releaseProgressCallback,
                 TSQueryCursor_releaseProgressCallback);
  SET_JNI_METHOD(methods, TSQueryCursor_Native_exceededMatchLimit,
                 TSQueryCursor_exceededMatchLimit);
  SET_JNI_METHOD(methods, TSQueryCursor_Native_setMatchLimit,
                 TSQueryCursor_setMatchLimit);
  SET_JNI_METHOD(methods, TSQueryCursor_Native_getMatchLimit,
                 TSQueryCursor_getMatchLimit);
  SET_JNI_METHOD(methods, TSQueryCursor_Native_setByteRange, TSQueryCursor_setByteRange);
  SET_JNI_METHOD(methods, TSQueryCursor_Native_setPointRange,
                 TSQueryCursor_setPointRange);
  SET_JNI_METHOD(methods, TSQueryCursor_Native_nextMatch, TSQueryCursor_nextMatch);
  SET_JNI_METHOD(methods, TSQueryCursor_Native_nextCapture, TSQueryCursor_nextCapture);
  SET_JNI_METHOD(methods, TSQueryCursor_Native_setContainingByteRange,
                 TSQueryCursor_setContainingByteRange);
  SET_JNI_METHOD(methods, TSQueryCursor_Native_setContainingPointRange,
                 TSQueryCursor_setContainingPointRange);
  SET_JNI_METHOD(methods, TSQueryCursor_Native_setMaxStartDepth,
                 TSQueryCursor_setMaxStartDepth);
  SET_JNI_METHOD(methods, TSQueryCursor_Native_removeMatch, TSQueryCursor_removeMatch);
}