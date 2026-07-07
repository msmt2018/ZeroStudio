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

#include "utf16str/UTF16String.h"
#include "utils/ts_obj_utils.h"
#include "utils/ts_exceptions.h"
#include "utils/ts_preconditions.h"
#include "ts__log.h"

#include "ts_parser.h"

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
  auto parser = new TSParserInternal;
  return (jlong) parser;
}

static void
TSParser_delete(JNIEnv *env,
                jclass self,
                jlong parser_ptr) {
  req_nnp(env, parser_ptr);

  auto parser = (TSParserInternal *) parser_ptr;
  delete parser;
}

static void
TSParser_setLanguage(JNIEnv *env,
                     jclass self,
                     jlong parser,
                     jlong language) {
  req_nnp(env, parser, "parser");
  req_nnp(env, language, "language");
  ts_parser_set_language(((TSParserInternal *) parser)->getParser(env),
                         (TSLanguage *) language);
}

static jlong
TSParser_getLanguage(JNIEnv *env,
                     jclass self,
                     jlong parser) {
  req_nnp(env, parser);
  return (jlong) ts_parser_language(((TSParserInternal *) parser)->getParser(env));
}

static void
TSParser_reset(JNIEnv *env,
               jclass self,
               jlong parser) {
  req_nnp(env, parser);
  TSParser *pParser = ((TSParserInternal *) (parser))->getParser(env);
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
  int count = env->GetArrayLength(ranges);
  TSRange tsRanges[count];
  for (int i = 0; i < count; i++) {
    jobject range = env->GetObjectArrayElement(ranges, i);
    std::string msg = std::string("ranges[") + std::to_string(i) + "]";
    req_nnp(env, range, msg);
    tsRanges[i] = _unmarshalRange(env, range);
  }

  const TSRange *r = tsRanges;
  return (jboolean) ts_parser_set_included_ranges(((TSParserInternal *) parser)->getParser(
      env), r, count);
}

static jobjectArray
TSParser_getIncludedRanges(
    JNIEnv *env,
    jclass self,
    jlong parser) {
  req_nnp(env, parser);
  jint count;
  const TSRange *ranges =
      ts_parser_included_ranges(((TSParserInternal *) parser)->getParser(env),
                                reinterpret_cast<uint32_t *>(&count));
  jobjectArray result = createRangeArr(env, count);
  req_nnp(env, result, "TSRange[] from factory");

  for (uint32_t i = 0; i < count; i++) {
    const TSRange *r = (ranges + i);
    env->SetObjectArrayElement(result, (jint) i, _marshalRange(env, *r));
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
  ts_parser_set_wasm_store(parserInternal->getParser(env),
                           store == 0 ? nullptr : (TSWasmStore *) store);
}

// 移除并返回 parser 当前的 wasm store
// ts_parser_take_wasm_store 在 parser.c 中总有实现（不依赖 TREE_SITTER_FEATURE_WASM）
static jlong
TSParser_takeWasmStore(JNIEnv *env, jclass clazz, jlong parser) {
  req_nnp(env, parser);
  auto *parserInternal = (TSParserInternal *) parser;
  TSWasmStore *store = ts_parser_take_wasm_store(parserInternal->getParser(env));
  return (jlong) store;
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
}
