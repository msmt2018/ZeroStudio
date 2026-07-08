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

#include <atomic>
#include <chrono>
#include <mutex>
#include <iostream>
#include <unordered_map>
#include <vector>

#include "utf16str/UTF16String.h"
#include "utils/ts_obj_utils.h"
#include "utils/ts_exceptions.h"
#include "utils/ts_preconditions.h"
#include "ts__log.h"

#include "ts_parser.h"

// ============================================================================
// Logger 反向回调实现
//
// tree-sitter 0.27 的 TSLogger 是一个 C 结构体，包含 payload 指针和函数指针：
//   typedef struct TSLogger {
//     void *payload;
//     void (*log)(void *payload, TSLogType log_type, const char *buffer);
//   } TSLogger;
//
// 为了让 Java 端能注册 TSLogger 回调，我们在 native 层：
//   1. 维护一个 parser pointer -> Java TSLogger GlobalRef 的映射
//   2. 提供 java_logger_callback 作为 C 函数指针，从映射中取出 Java 对象并回调
//   3. 缓存 JavaVM 用于在解析线程上 attach（同步 parseString 场景下其实当前线程已有 JNIEnv）
//   4. 缓存 TSLogger 接口类 + log 方法 ID，以及 TSLogType 枚举常量
// ============================================================================

static JavaVM *g_jvm = nullptr;
static std::mutex g_logger_mutex;

// parser pointer -> Java TSLogger GlobalRef
static std::unordered_map<jlong, jobject> g_logger_refs;

// 缓存的 Java 类/方法/字段
static jclass g_logger_class = nullptr;        // com/itsaky/androidide/treesitter/TSLogger
static jmethodID g_logger_log_method = nullptr; // TSLogger.log(TSLogType, String)
static jclass g_logtype_class = nullptr;       // com/itsaky/androidide/treesitter/TSLogType
static jobject g_logtype_parse = nullptr;      // TSLogType.PARSE (GlobalRef)
static jobject g_logtype_lex = nullptr;        // TSLogType.LEX   (GlobalRef)
static bool g_logger_cache_inited = false;

// 初始化 logger 缓存（必须在 Java 线程上调用，带 env）
static void ensure_logger_cache(JNIEnv *env) {
  if (g_logger_cache_inited) {
    return;
  }

  // TSLogger 接口类
  jclass logger_local = env->FindClass("com/itsaky/androidide/treesitter/TSLogger");
  if (logger_local == nullptr) {
    LOGE("TSParser", "Failed to find TSLogger class");
    env->ExceptionClear();
    return;
  }
  g_logger_class = (jclass) env->NewGlobalRef(logger_local);
  env->DeleteLocalRef(logger_local);
  g_logger_log_method = env->GetMethodID(g_logger_class, "log",
                                         "(Lcom/itsaky/androidide/treesitter/TSLogType;Ljava/lang/String;)V");
  if (g_logger_log_method == nullptr) {
    LOGE("TSParser", "Failed to find TSLogger.log method");
    env->ExceptionClear();
    return;
  }

  // TSLogType 枚举类 + PARSE/LEX 静态字段
  jclass logtype_local = env->FindClass("com/itsaky/androidide/treesitter/TSLogType");
  if (logtype_local == nullptr) {
    LOGE("TSParser", "Failed to find TSLogType class");
    env->ExceptionClear();
    return;
  }
  g_logtype_class = (jclass) env->NewGlobalRef(logtype_local);
  env->DeleteLocalRef(logtype_local);

  jfieldID parse_field = env->GetStaticFieldID(g_logtype_class, "PARSE",
                                               "Lcom/itsaky/androidide/treesitter/TSLogType;");
  jfieldID lex_field = env->GetStaticFieldID(g_logtype_class, "LEX",
                                             "Lcom/itsaky/androidide/treesitter/TSLogType;");
  if (parse_field == nullptr || lex_field == nullptr) {
    LOGE("TSParser", "Failed to find TSLogType enum constants");
    env->ExceptionClear();
    return;
  }
  jobject parse_local = env->GetStaticObjectField(g_logtype_class, parse_field);
  jobject lex_local = env->GetStaticObjectField(g_logtype_class, lex_field);
  g_logtype_parse = env->NewGlobalRef(parse_local);
  g_logtype_lex = env->NewGlobalRef(lex_local);
  env->DeleteLocalRef(parse_local);
  env->DeleteLocalRef(lex_local);

  g_logger_cache_inited = true;
}

// 获取 JNIEnv，必要时 attach 当前线程
// 通过 *out_attached 返回是否需要后续 DetachCurrentThread
static JNIEnv *get_jni_env_for_logger(bool *out_attached) {
  if (g_jvm == nullptr) {
    *out_attached = false;
    return nullptr;
  }
  JNIEnv *env = nullptr;
  jint rc = g_jvm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6);
  if (rc == JNI_EDETACHED) {
    if (g_jvm->AttachCurrentThread(&env, nullptr) != JNI_OK) {
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

// tree-sitter 调用的 log 回调
// payload 存储的是 parser pointer (jlong)，用于从 map 查找 Java TSLogger
static void java_logger_callback(void *payload, TSLogType log_type, const char *buffer) {
  if (payload == nullptr) {
    return;
  }
  jlong parser_key = (jlong) reinterpret_cast<intptr_t>(payload);

  jobject logger_ref = nullptr;
  {
    std::lock_guard<std::mutex> lock(g_logger_mutex);
    auto it = g_logger_refs.find(parser_key);
    if (it == g_logger_refs.end()) {
      return;
    }
    logger_ref = it->second;
  }
  if (logger_ref == nullptr) {
    return;
  }

  bool attached = false;
  JNIEnv *env = get_jni_env_for_logger(&attached);
  if (env == nullptr) {
    LOGE("TSParser", "logger callback: failed to obtain JNIEnv");
    return;
  }

  if (!g_logger_cache_inited) {
    ensure_logger_cache(env);
  }
  if (g_logger_class == nullptr || g_logger_log_method == nullptr) {
    if (attached) {
      g_jvm->DetachCurrentThread();
    }
    return;
  }

  jobject log_type_obj = (log_type == TSLogTypeLex) ? g_logtype_lex : g_logtype_parse;
  jstring buffer_obj = env->NewStringUTF(buffer == nullptr ? "" : buffer);

  env->CallVoidMethod(logger_ref, g_logger_log_method, log_type_obj, buffer_obj);

  if (env->ExceptionCheck()) {
    env->ExceptionDescribe();
    env->ExceptionClear();
  }

  env->DeleteLocalRef(buffer_obj);

  if (attached) {
    g_jvm->DetachCurrentThread();
  }
}

// 清理指定 parser 的 logger GlobalRef（在 delete 或 setLogger 时调用）
static void release_logger_ref(jlong parser_key) {
  std::lock_guard<std::mutex> lock(g_logger_mutex);
  auto it = g_logger_refs.find(parser_key);
  if (it == g_logger_refs.end()) {
    return;
  }
  if (g_jvm != nullptr && it->second != nullptr) {
    JNIEnv *env = nullptr;
    if (g_jvm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) == JNI_OK) {
      env->DeleteGlobalRef(it->second);
    }
  }
  g_logger_refs.erase(it);
}

// tree-sitter 0.27 移除了 ts_parser_set_cancellation_flag 和 ts_parser_set_timeout_micros
// 取消和超时现在通过 ts_parser_parse_with_options + TSParseOptions.progress_callback 实现
// progress_callback 返回 true 表示取消解析

// progress_callback 的 payload，包含取消标志和超时信息
struct ParseCallbackPayload {
  std::atomic<bool> *cancelled;        // 取消标志（与 TSParserInternal 共享）
  std::atomic<uint64_t> *timeout_micros; // 超时（微秒，0 = 无超时）
  std::chrono::steady_clock::time_point start_time; // 解析开始时间
};

// progress_callback 实现：检查取消标志和超时
static bool parse_progress_callback(TSParseState *state) {
  auto *payload = static_cast<ParseCallbackPayload *>(state->payload);
  if (payload == nullptr) {
    return false;
  }

  // 检查取消标志
  if (payload->cancelled != nullptr && payload->cancelled->load()) {
    return true; // 取消解析
  }

  // 检查超时
  if (payload->timeout_micros != nullptr) {
    uint64_t timeout = payload->timeout_micros->load();
    if (timeout > 0) {
      auto elapsed = std::chrono::duration_cast<std::chrono::microseconds>(
          std::chrono::steady_clock::now() - payload->start_time).count();
      if (static_cast<uint64_t>(elapsed) >= timeout) {
        return true; // 超时，取消解析
      }
    }
  }

  return false; // 继续解析
}

/**
 * `TSParserInternal` stores the actual tree sitter parser instance along
 * with the cancellation flag and timeout for 0.27 progress_callback 机制。
 */
class TSParserInternal {
 public:

  TSParserInternal() {
    cancellation_flag_mutex = new std::mutex();
    cancelled = new std::atomic<bool>(false);
    timeout_micros = new std::atomic<uint64_t>(0);
    parsing = new std::atomic<bool>(false);
    parser = ts_parser_new();
  }

  ~TSParserInternal() {
    delete cancellation_flag_mutex;
    delete cancelled;
    delete timeout_micros;
    delete parsing;
    ts_parser_delete(parser);

    cancellation_flag_mutex = nullptr;
    cancelled = nullptr;
    timeout_micros = nullptr;
    parsing = nullptr;
    parser = nullptr;
  }

  TSParser *getParser(JNIEnv *env) {
    if (check_destroyed(env)) {
      return nullptr;
    }

    return this->parser;
  }

  // 开始一次解析，设置取消标志为 false，parsing 为 true
  bool begin_round(JNIEnv *env) {
    if (is_parsing(env)) {
      throw_illegal_state(env,
                          "Parser is already parsing another syntax tree! You must cancel the current parse first!");
      return false;
    }

    std::lock_guard<std::mutex> lock(*cancellation_flag_mutex);
    cancelled->store(false);
    parsing->store(true);
    return true;
  }

  // 结束一次解析
  void end_round(JNIEnv *env) {
    if (check_destroyed(env)) {
      return;
    }

    std::lock_guard<std::mutex> lock(*cancellation_flag_mutex);
    parsing->store(false);
    cancelled->store(false);
  }

  // 请求取消当前解析
  bool request_cancellation(JNIEnv *env) {
    if (check_destroyed(env)) {
      return false;
    }

    std::lock_guard<std::mutex> lock(*cancellation_flag_mutex);
    if (!parsing->load()) {
      LOGD("TSParser",
           "Cannot cancel parsing, no parse is in progress.");
      return false;
    }

    cancelled->store(true);
    LOGD("TSParser", "Cancellation flag has been set");
    return true;
  }

  // 设置超时（微秒）
  void set_timeout(uint64_t timeout) {
    timeout_micros->store(timeout);
  }

  // 获取超时（微秒）
  uint64_t get_timeout() {
    return timeout_micros->load();
  }

  // 获取取消标志指针（供 progress_callback 使用）
  std::atomic<bool> *get_cancelled() {
    return cancelled;
  }

  // 获取超时指针（供 progress_callback 使用）
  std::atomic<uint64_t> *get_timeout_micros() {
    return timeout_micros;
  }

 private:
  std::mutex *cancellation_flag_mutex;
  std::atomic<bool> *cancelled;
  std::atomic<uint64_t> *timeout_micros;
  std::atomic<bool> *parsing;

  TSParser *parser;

  bool is_parsing(JNIEnv *env) {
    if (check_destroyed(env)) {
      return false;
    }
    return parsing->load();
  }

  bool check_destroyed(JNIEnv *env) {
    if (cancellation_flag_mutex == nullptr || cancelled == nullptr
        || timeout_micros == nullptr || parsing == nullptr
        || parser == nullptr) {
      throw_illegal_state(env, "TSParserInternal has already been destroyed");
      return true;
    }

    return false;
  }
};

static jlong
TSParser_newParser(JNIEnv *env,
                   jclass self) {
  // 初始化 JavaVM 缓存，供 logger 回调使用（在解析线程上获取 JNIEnv）
  if (g_jvm == nullptr) {
    env->GetJavaVM(&g_jvm);
  }
  auto parser = new TSParserInternal;
  return (jlong) parser;
}

static void
TSParser_delete(JNIEnv *env,
                jclass self,
                jlong parser_ptr) {
  req_nnp(env, parser_ptr);

  // 先清除 logger，避免 parser 析构后回调访问悬垂指针
  TSParser *ts_parser = ((TSParserInternal *) parser_ptr)->getParser(env);
  if (ts_parser != nullptr) {
    TSLogger empty_logger;
    empty_logger.payload = nullptr;
    empty_logger.log = nullptr;
    ts_parser_set_logger(ts_parser, empty_logger);
  }
  release_logger_ref(parser_ptr);

  auto parser = (TSParserInternal *) parser_ptr;
  delete parser;
}

static jboolean
TSParser_setLanguage(JNIEnv *env,
                     jclass self,
                     jlong parser,
                     jlong language) {
  req_nnp(env, parser, "parser");
  req_nnp(env, language, "language");
  TSParser *pParser = ((TSParserInternal *) parser)->getParser(env);
  if (pParser == nullptr) return (jboolean) false;
  bool ok = ts_parser_set_language(pParser, (TSLanguage *) language);
  return (jboolean) ok;
}

static jlong
TSParser_getLanguage(JNIEnv *env,
                     jclass self,
                     jlong parser) {
  req_nnp(env, parser);
  TSParser *pParser = ((TSParserInternal *) parser)->getParser(env);
  if (pParser == nullptr) return 0;
  return (jlong) ts_parser_language(pParser);
}

static void
TSParser_reset(JNIEnv *env,
               jclass self,
               jlong parser) {
  req_nnp(env, parser);
  TSParser *pParser = ((TSParserInternal *) (parser))->getParser(env);
  // M5 修复：getParser 在 parser 已销毁时返回 nullptr 并设置 pending exception，
  // 此处必须 early return，避免将 nullptr 传给 C API 导致 SIGSEGV。
  if (pParser == nullptr) return;
  LOGD("ts_parser.cc", "Reset parser: %p, language: %p", pParser,
       ts_parser_language(pParser));
  ts_parser_reset(pParser);
}

// 0.27 移除了 ts_parser_set_timeout_micros，超时现在通过 progress_callback 实现
// 这里仅存储超时值，在解析时通过 progress_callback 检查
static void
TSParser_setTimeout(JNIEnv *env,
                    jclass self,
                    jlong parser,
                    jlong macros) {
  req_nnp(env, parser);
  ((TSParserInternal *) parser)->set_timeout((uint64_t) macros);
}

static jlong
TSParser_getTimeout(JNIEnv *env,
                    jclass self,
                    jlong parser) {
  req_nnp(env, parser);
  return (jlong) ((TSParserInternal *) parser)->get_timeout();
}

static jboolean
TSParser_setIncludedRanges(
    JNIEnv *env,
    jclass self,
    jlong parser,
    jobjectArray ranges) {
  req_nnp(env, parser);
  // M2 修复：添加 null 检查，避免 GetArrayLength(nullptr) 导致 SIGSEGV
  req_nnp(env, ranges, "ranges");
  TSParser *pParser = ((TSParserInternal *) parser)->getParser(env);
  if (pParser == nullptr) return (jboolean) false;
  int count = env->GetArrayLength(ranges);
  // M2 修复：使用堆分配替代 VLA（变长数组非标准 C++，且大数组可能栈溢出）
  std::vector<TSRange> tsRanges(count > 0 ? count : 1);
  for (int i = 0; i < count; i++) {
    jobject range = env->GetObjectArrayElement(ranges, i);
    std::string msg = std::string("ranges[") + std::to_string(i) + "]";
    req_nnp(env, range, msg);
    tsRanges[i] = _unmarshalRange(env, range);
    env->DeleteLocalRef(range);
  }

  const TSRange *r = tsRanges.data();
  return (jboolean) ts_parser_set_included_ranges(pParser, r, count);
}

static jobjectArray
TSParser_getIncludedRanges(
    JNIEnv *env,
    jclass self,
    jlong parser) {
  req_nnp(env, parser);
  TSParser *pParser = ((TSParserInternal *) parser)->getParser(env);
  if (pParser == nullptr) return nullptr;
  jint count;
  const TSRange *ranges =
      ts_parser_included_ranges(pParser,
                                reinterpret_cast<uint32_t *>(&count));
  jobjectArray result = createRangeArr(env, count);
  req_nnp(env, result, "TSRange[] from factory");

  for (uint32_t i = 0; i < count; i++) {
    const TSRange *r = (ranges + i);
    jobject obj = _marshalRange(env, *r);
    env->SetObjectArrayElement(result, (jint) i, obj);
    // M6 修复：释放循环中创建的 LocalRef
    env->DeleteLocalRef(obj);
  }
  return result;
}

static jlong TSParser_parse(JNIEnv *env,
                            jclass clazz,
                            jlong parser,
                            jlong tree_pointer,
                            jlong str_pointer) {
  req_nnp(env, parser);
  req_nnp(env, str_pointer, "string");
  auto *ts_parser_internal = (TSParserInternal *) parser;
  TSParser *ts_parser = ts_parser_internal->getParser(env);
  TSTree *old_tree = tree_pointer == 0 ? nullptr : (TSTree *) tree_pointer;
  auto *source = as_str(env, str_pointer);

  if (!ts_parser_internal->begin_round(env)) {
    return 0;
  }

  auto src_cstring = source->to_cstring();
  uint32_t src_len = source->byte_length();

  // 0.27 使用 ts_parser_parse_with_options + progress_callback 实现取消和超时
  // 构造 progress_callback payload
  ParseCallbackPayload payload;
  payload.cancelled = ts_parser_internal->get_cancelled();
  payload.timeout_micros = ts_parser_internal->get_timeout_micros();
  payload.start_time = std::chrono::steady_clock::now();

  TSParseOptions options;
  options.payload = &payload;
  options.progress_callback = parse_progress_callback;

  // 使用 ts_parser_parse_with_options（需要构造 TSInput）
  // TSInput 的 read 回调从内存字符串读取
  struct StringInput {
    const char *data;
    uint32_t length;
  };
  StringInput input_payload;
  input_payload.data = src_cstring;
  input_payload.length = src_len;

  auto read_callback = [](void *p, uint32_t byte_index,
                          TSPoint position, uint32_t *bytes_read) -> const char * {
    auto *input = static_cast<StringInput *>(p);
    if (byte_index >= input->length) {
      *bytes_read = 0;
      return "";
    }
    *bytes_read = input->length - byte_index;
    return input->data + byte_index;
  };

  // 0.27 将 TSInputEncodingUTF16 拆分为 TSInputEncodingUTF16LE/BE
  // Android 为小端架构，使用 LE
  TSInput input;
  input.payload = &input_payload;
  input.read = read_callback;
  input.encoding = TSInputEncodingUTF16LE;
  input.decode = nullptr;

  // 使用带选项的解析（支持取消和超时）
  TSTree *tree = ts_parser_parse_with_options(ts_parser, old_tree, input, options);

  ts_parser_internal->end_round(env);
  delete[] src_cstring;

  return (jlong) tree;
}

static jboolean
TSParser_requestCancellation(
    JNIEnv *env,
    jclass clazz,
    jlong parser) {
  req_nnp(env, parser);
  auto *parserInternal = (TSParserInternal *) parser;
  return (jboolean) parserInternal->request_cancellation(env);
}

// 为 parser 分配 wasm store
// ts_parser_set_wasm_store 在 parser.c 中总有实现（不依赖 TREE_SITTER_FEATURE_WASM）
static void
TSParser_setWasmStore(JNIEnv *env, jclass clazz, jlong parser, jlong store) {
  req_nnp(env, parser);
  auto *parserInternal = (TSParserInternal *) parser;
  TSParser *pParser = parserInternal->getParser(env);
  if (pParser == nullptr) return;
  ts_parser_set_wasm_store(pParser,
                           store == 0 ? nullptr : (TSWasmStore *) store);
}

// 移除并返回 parser 当前的 wasm store
// ts_parser_take_wasm_store 在 parser.c 中总有实现（不依赖 TREE_SITTER_FEATURE_WASM）
static jlong
TSParser_takeWasmStore(JNIEnv *env, jclass clazz, jlong parser) {
  req_nnp(env, parser);
  auto *parserInternal = (TSParserInternal *) parser;
  TSParser *pParser = parserInternal->getParser(env);
  if (pParser == nullptr) return 0;
  TSWasmStore *store = ts_parser_take_wasm_store(pParser);
  return (jlong) store;
}

// ts_parser_print_dot_graphs：将解析过程的 DOT 调试图写入指定的文件描述符。
// 这是 tree-sitter 0.27 的调试 API，传入负数 fd 会关闭输出。
static void
TSParser_printDotGraphs(JNIEnv *env, jclass clazz, jlong parser,
                        jint file_descriptor) {
  req_nnp(env, parser);
  auto *parserInternal = (TSParserInternal *) parser;
  TSParser *pParser = parserInternal->getParser(env);
  if (pParser == nullptr) return;
  ts_parser_print_dot_graphs(pParser, (int) file_descriptor);
}

// ts_parser_set_logger：设置 parser 的日志回调
// logger 为 null 时清除当前 logger
static void
TSParser_setLogger(JNIEnv *env, jclass clazz, jlong parser, jobject logger) {
  req_nnp(env, parser);
  auto *parserInternal = (TSParserInternal *) parser;
  TSParser *ts_parser = parserInternal->getParser(env);
  if (ts_parser == nullptr) {
    return;
  }

  // 初始化缓存（即使 logger 为 null 也初始化，便于后续 getLogger 不需要懒加载）
  ensure_logger_cache(env);

  // 释放之前可能存在的 logger GlobalRef
  release_logger_ref(parser);

  TSLogger c_logger;
  if (logger == nullptr) {
    c_logger.payload = nullptr;
    c_logger.log = nullptr;
  } else {
    // 持有 Java TSLogger 的 GlobalRef，key 用 parser pointer
    jobject global_ref = env->NewGlobalRef(logger);
    {
      std::lock_guard<std::mutex> lock(g_logger_mutex);
      g_logger_refs[parser] = global_ref;
    }
    // payload 存储 parser pointer，回调时用它查找 Java logger
    c_logger.payload = reinterpret_cast<void *>(static_cast<intptr_t>(parser));
    c_logger.log = java_logger_callback;
  }
  ts_parser_set_logger(ts_parser, c_logger);
}

// ts_parser_logger：获取 parser 当前的日志回调
// 返回之前 setLogger 时传入的 Java TSLogger 对象（或 null）
static jobject
TSParser_getLogger(JNIEnv *env, jclass clazz, jlong parser) {
  req_nnp(env, parser);

  std::lock_guard<std::mutex> lock(g_logger_mutex);
  auto it = g_logger_refs.find(parser);
  if (it == g_logger_refs.end() || it->second == nullptr) {
    return nullptr;
  }
  // 返回局部引用副本
  return env->NewLocalRef(it->second);
}

void TSParser_Native__SetJniMethods(JNINativeMethod *methods, int count) {
  SET_JNI_METHOD(methods, TSParser_Native_newParser, TSParser_newParser);
  SET_JNI_METHOD(methods, TSParser_Native_delete, TSParser_delete);
  SET_JNI_METHOD(methods, TSParser_Native_setLanguage, TSParser_setLanguage);
  SET_JNI_METHOD(methods, TSParser_Native_getLanguage, TSParser_getLanguage);
  SET_JNI_METHOD(methods, TSParser_Native_reset, TSParser_reset);
  SET_JNI_METHOD(methods, TSParser_Native_setTimeout, TSParser_setTimeout);
  SET_JNI_METHOD(methods, TSParser_Native_getTimeout, TSParser_getTimeout);
  SET_JNI_METHOD(methods, TSParser_Native_setIncludedRanges, TSParser_setIncludedRanges);
  SET_JNI_METHOD(methods, TSParser_Native_getIncludedRanges, TSParser_getIncludedRanges);
  SET_JNI_METHOD(methods, TSParser_Native_parse, TSParser_parse);
  SET_JNI_METHOD(methods, TSParser_Native_requestCancellation,
                 TSParser_requestCancellation);
  SET_JNI_METHOD(methods, TSParser_Native_setWasmStore, TSParser_setWasmStore);
  SET_JNI_METHOD(methods, TSParser_Native_takeWasmStore, TSParser_takeWasmStore);
  SET_JNI_METHOD(methods, TSParser_Native_printDotGraphs, TSParser_printDotGraphs);
  SET_JNI_METHOD(methods, TSParser_Native_setLogger, TSParser_setLogger);
  SET_JNI_METHOD(methods, TSParser_Native_getLogger, TSParser_getLogger);
}
