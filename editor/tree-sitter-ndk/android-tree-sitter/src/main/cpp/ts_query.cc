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

#include "utils/ts_exceptions.h"
#include "utils/ts_obj_utils.h"
#include "utils/ts_preconditions.h"

#include "ts_query.h"

void fillQuery(JNIEnv *, jobject, uint32_t, TSQueryError);
int query_quantifier_id(JNIEnv *env, TSQuantifier quantifier);
jint getErrorType(TSQueryError);

static jlong TSQuery_newQuery(JNIEnv *env,
                              jclass self,
                              jobject queryObject,
                              jlong language,
                              jstring source) {
  req_nnp(env, language);
  const char *c_source = env->GetStringUTFChars(source, nullptr);
  // C2 修复：使用 GetStringUTFLength 获取 UTF-8 字节长度，
  // 而非 GetStringLength 返回的 UTF-16 码元数。
  // ts_query_new 的 source_length 参数期望字节数。
  uint32_t source_length = (uint32_t) env->GetStringUTFLength(source);
  // C1 修复：使用栈分配而非堆分配，避免内存泄漏。
  uint32_t error_offset = 0;
  TSQueryError error_type = TSQueryErrorNone;
  TSQuery *query = ts_query_new((TSLanguage *) language,
                                c_source,
                                source_length,
                                &error_offset,
                                &error_type);
  fillQuery(env, queryObject, error_offset, error_type);
  env->ReleaseStringUTFChars(source, c_source);
  return (jlong) query;
}

static void TSQuery_delete(JNIEnv *env, jclass self, jlong query) {
  req_nnp(env, query);
  ts_query_delete((TSQuery *) query);
}

static jint TSQuery_captureCount(JNIEnv *env, jclass self, jlong query) {
  req_nnp(env, query);
  return (jint) ts_query_capture_count((TSQuery *) query);
}

static jint TSQuery_patternCount(JNIEnv *env, jclass self, jlong query) {
  req_nnp(env, query);
  return (jint) ts_query_pattern_count((TSQuery *) query);
}

static jint TSQuery_stringCount(JNIEnv *env, jclass self, jlong query) {
  req_nnp(env, query);
  return (jint) ts_query_string_count((TSQuery *) query);
}

static jint TSQuery_startByteForPattern(JNIEnv *env,
                                        jclass self,
                                        jlong query,
                                        jint pattern) {
  req_nnp(env, query);
  return (jint) ts_query_start_byte_for_pattern((TSQuery *) query, pattern);
}

static jobjectArray TSQuery_predicatesForPattern(JNIEnv *env,
                                                 jclass self,
                                                 jlong query,
                                                 jint pattern) {
  req_nnp(env, query);

  uint32_t count;
  const TSQueryPredicateStep *predicates =
      ts_query_predicates_for_pattern((TSQuery *) query, pattern, &count);
  jobjectArray result = createQueryPredicateStepArr(env, (jint) count);
  req_nnp(env, result, "TSQueryPredicateStep[] from factory");

  for (uint32_t i = 0; i < count; i++) {
    const TSQueryPredicateStep *predicate = (predicates + i);
    jobject obj = _marshalQueryPredicateStep(env, predicate);
    env->SetObjectArrayElement(result, i, obj);
    env->DeleteLocalRef(obj);
  }

  return result;
}

static jboolean
TSQuery_patternRooted(JNIEnv *env, jclass self, jlong query, jint pattern) {
  req_nnp(env, query);
  return (jboolean) ts_query_is_pattern_rooted((TSQuery *) query, pattern);
}

static jboolean TSQuery_patternNonLocal(JNIEnv *env,
                                        jclass self,
                                        jlong query,
                                        jint pattern_index) {
  req_nnp(env, query);
  return (jboolean) ts_query_is_pattern_non_local((TSQuery *) query,
                                                  pattern_index);
}

static jboolean TSQuery_patternGuaranteedAtStep(JNIEnv *env,
                                                jclass self,
                                                jlong query,
                                                jint offset) {
  req_nnp(env, query);
  return (jboolean) ts_query_is_pattern_guaranteed_at_step((TSQuery *) query,
                                                           offset);
}

static jstring
TSQuery_captureNameForId(JNIEnv *env, jclass self, jlong query, jint id) {
  req_nnp(env, query);
  uint32_t count;
  const char
      *name = ts_query_capture_name_for_id((TSQuery *) query, id, &count);
  return (jstring) env->NewStringUTF(name);
}

static jstring
TSQuery_stringValueForId(JNIEnv *env, jclass self, jlong query, jint id) {
  req_nnp(env, query);
  uint32_t count;
  const char *str = ts_query_string_value_for_id((TSQuery *) query, id, &count);
  return env->NewStringUTF(str);
}

void fillQuery(JNIEnv *env,
               jobject query,
               uint32_t error_offset,
               TSQueryError error_type) {
  req_nnp(env, query);
  jclass klass = env->GetObjectClass(query);
  jfieldID offsetField = env->GetFieldID(klass, "errorOffset", "I");
  jfieldID errorTypeField = env->GetFieldID(klass, "errorType", "I");
  env->SetIntField(query, offsetField, (jint) error_offset);
  env->SetIntField(query, errorTypeField, getErrorType(error_type));
}

jint getErrorType(TSQueryError error) {
  switch (error) {
    default:
    case TSQueryErrorNone:
      return 0;
    case TSQueryErrorSyntax:
      return 1;
    case TSQueryErrorNodeType:
      return 2;
    case TSQueryErrorField:
      return 3;
    case TSQueryErrorCapture:
      return 4;
    case TSQueryErrorStructure:
      return 5;
    case TSQueryErrorLanguage:
      return 6;
  }
}
static jint TSQuery_captureQuantifierForId(JNIEnv *env,
                                           jclass clazz,
                                           jlong query,
                                           jint pattern,
                                           jint capture) {
  req_nnp(env, query);
  auto ts_query = (TSQuery *) query;
  auto quantifier =
      ts_query_capture_quantifier_for_id(ts_query, pattern, capture);
  return query_quantifier_id(env, quantifier);
}

// 创建 query 的副本，返回新 query 的指针（v15 新增）
static jlong TSQuery_copy(JNIEnv *env, jclass self, jlong query) {
  req_nnp(env, query);
  return (jlong) ts_query_copy((TSQuery *) query);
}

// 获取指定 pattern 在 query 源码中结束的字节偏移（v15 新增）
static jint TSQuery_endByteForPattern(JNIEnv *env,
                                      jclass self,
                                      jlong query,
                                      jint pattern) {
  req_nnp(env, query);
  return (jint) ts_query_end_byte_for_pattern((TSQuery *) query, pattern);
}

// 禁用指定 pattern，阻止其匹配（v15 新增）
static void TSQuery_disablePattern(JNIEnv *env,
                                   jclass self,
                                   jlong query,
                                   jint pattern) {
  req_nnp(env, query);
  ts_query_disable_pattern((TSQuery *) query, pattern);
}

// 禁用指定名称的 capture，阻止其在查询结果中出现
static void TSQuery_disableCapture(JNIEnv *env,
                                   jclass self,
                                   jlong query,
                                   jbyteArray name,
                                   jint length) {
  req_nnp(env, query);
  req_nnp(env, name, "capture name");
  jbyte *nm = env->GetByteArrayElements(name, nullptr);
  ts_query_disable_capture((TSQuery *) query,
                           reinterpret_cast<const char *>(nm),
                           (uint32_t) length);
  env->ReleaseByteArrayElements(name, nm, JNI_ABORT);
}

int query_quantifier_id(JNIEnv *env, TSQuantifier quantifier) {
  switch (quantifier) {
    case TSQuantifierZero:
      return 0;
    case TSQuantifierZeroOrOne:
      return 1;
    case TSQuantifierZeroOrMore:
      return 2;
    case TSQuantifierOne:
      return 3;
    case TSQuantifierOneOrMore:
      return 4;
  }

  throw_illegal_args(env, "Unknown quantifier");
  return -1;
}

void TSQuery_Native__SetJniMethods(JNINativeMethod *methods, int count) {
  SET_JNI_METHOD(methods, TSQuery_Native_newQuery, TSQuery_newQuery);
  SET_JNI_METHOD(methods, TSQuery_Native_delete, TSQuery_delete);
  SET_JNI_METHOD(methods, TSQuery_Native_captureCount, TSQuery_captureCount);
  SET_JNI_METHOD(methods, TSQuery_Native_patternCount, TSQuery_patternCount);
  SET_JNI_METHOD(methods, TSQuery_Native_stringCount, TSQuery_stringCount);
  SET_JNI_METHOD(methods, TSQuery_Native_startByteForPattern,
                 TSQuery_startByteForPattern);
  SET_JNI_METHOD(methods, TSQuery_Native_predicatesForPattern,
                 TSQuery_predicatesForPattern);
  SET_JNI_METHOD(methods, TSQuery_Native_patternRooted, TSQuery_patternRooted);
  SET_JNI_METHOD(methods, TSQuery_Native_patternNonLocal, TSQuery_patternNonLocal);
  SET_JNI_METHOD(methods, TSQuery_Native_patternGuaranteedAtStep,
                 TSQuery_patternGuaranteedAtStep);
  SET_JNI_METHOD(methods, TSQuery_Native_captureNameForId, TSQuery_captureNameForId);
  SET_JNI_METHOD(methods, TSQuery_Native_stringValueForId, TSQuery_stringValueForId);
  SET_JNI_METHOD(methods, TSQuery_Native_captureQuantifierForId,
                 TSQuery_captureQuantifierForId);
  SET_JNI_METHOD(methods, TSQuery_Native_copy, TSQuery_copy);
  SET_JNI_METHOD(methods, TSQuery_Native_endByteForPattern,
                 TSQuery_endByteForPattern);
  SET_JNI_METHOD(methods, TSQuery_Native_disablePattern,
                 TSQuery_disablePattern);
  SET_JNI_METHOD(methods, TSQuery_Native_disableCapture,
                 TSQuery_disableCapture);
}