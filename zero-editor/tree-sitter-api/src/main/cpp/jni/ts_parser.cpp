#include <errno.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <sys/types.h>

#include "ts_utils.h"

#ifdef __cplusplus
extern "C" {
#endif

// What one parse needs between the calls of its read callback: the Kotlin callback
// and the chunk it answered last, which tree-sitter reads until it asks again. One
// per parse, on the stack of parser_parse_function: parses may run on several
// threads at once (a reparse in the background, an injected part on the main
// thread), so nothing of this may be shared between them.
struct ReadState {
    JNIEnv *env;
    jobject callback;
    jbyteArray bytes;
    jbyte *chunk;
};

// lets go of the chunk answered last: its elements, and the local reference to it
// (a parse that reads thousands of chunks would otherwise fill the local reference
// table of the JNI frame)
static void release_chunk(ReadState *state) {
    if (state->bytes != nullptr) {
        if (state->chunk != nullptr) {
            state->env->ReleaseByteArrayElements(state->bytes, state->chunk, JNI_ABORT);
        }
        state->env->DeleteLocalRef(state->bytes);
    }
    state->bytes = nullptr;
    state->chunk = nullptr;
}

jlong JNICALL parser_init() {
    return reinterpret_cast<jlong>(ts_parser_new()); 
}

void JNICALL parser_delete(JNIEnv *env, jclass clazz, jlong parser) {
    TSParser *self = reinterpret_cast<TSParser*>(parser);
    TSLogger logger = ts_parser_logger(self);
    if (logger.payload != nullptr)
        env->DeleteGlobalRef((jobject)logger.payload);
    ts_parser_delete(self);
}

void JNICALL parser_reset(JNIEnv *env, jobject thiz) {
    TSParser *self = GET_POINTER(TSParser, thiz);
    ts_parser_reset(self);
}

void JNICALL parser_set_language(JNIEnv *env, jobject thiz, jobject value) {    
    TSParser *self = GET_POINTER(TSParser, thiz);
    TSLanguage *language = GET_POINTER(TSLanguage, value);
    ts_parser_set_language(self, language);
    env->SetObjectField(thiz, global_field_cache.TSParser_language, value);
}

void JNICALL parser_set_included_ranges(JNIEnv *env, jobject thiz, jobject value) {
    // get the parser
    TSParser *self = GET_POINTER(TSParser, thiz);
    // get the list size    
    uint32_t size = (uint32_t)CALL_METHOD_NO_ARGS(Int, value, List_size);
    
    // calloc native TSRange array
    TSRange *ranges = (TSRange *)calloc(size, sizeof(TSRange));
    for (uint32_t i = 0; i < size; ++i) {
        jobject range_object = CALL_METHOD(Object, value, List_get, (jint)i);
        *(ranges + i) = unmarshal_range(env, range_object);
        env->DeleteLocalRef(range_object);
    }
    
    if (ts_parser_set_included_ranges(self, ranges, size)) {
        env->SetObjectField(thiz, global_field_cache.TSParser_includedRanges, value);
    } else {
        THROW(IllegalArgumentException, "Included ranges must be in ascending order and not overlap");
    }
    // here requires free memory
    free((void*)ranges);
}

void JNICALL parser_dot_graphs(JNIEnv* env, jobject thiz, jstring pathname) {
    TSParser *self = GET_POINTER(TSParser, thiz);
    const char *path =env->GetStringUTFChars(pathname, nullptr); 
    // here may requires MANAGE_EXTERNAL_STORAGE permission
    int fp = open(path, O_CREAT|O_TRUNC|O_RDWR, 0666);
    if(fp < 0) 
        LOGE("Error: %s\n", strerror(errno));
    else 
        ts_parser_print_dot_graphs(self, fp);
    
    env->ReleaseStringUTFChars(pathname, path);
}

// the callback param is kotlin lambda
void JNICALL parser_set_logger(JNIEnv *env, jobject thiz, jobject value) {    
    // convert lambda to C-Style function pointer
    auto callback = [](void *payload, TSLogType type, const char *buffer) {
        // local JNIEnv pointer
        JNIEnv *env = ::getEnv();
        // java enum TSLogType object
        jobject log_type = nullptr;       
        switch(type) {
            case TSLogTypeParse:
                log_type = GET_STATIC_FIELD(Object, TSParser$LogType, TSParser$LogType_PARSE);
                break;
            case TSLogTypeLex:
                log_type = GET_STATIC_FIELD(Object, TSParser$LogType, TSParser$LogType_LEX);
                break;
            default:
                UNREACHABLE();
        }
        
        // the message from native TSLogType
        jstring message = env->NewStringUTF(buffer);        
        // call the kotlin TSParser logger lambda     
        CALL_METHOD(Object, (jobject)payload, Function2_invoke, log_type, message);
    };
    
    TSParser *self = GET_POINTER(TSParser, thiz);
    TSLogger logger = ts_parser_logger(self);
    if (logger.payload != nullptr) {
        // release previous global logger reference
        env->DeleteGlobalRef(reinterpret_cast<jobject>(logger.payload));
    }
    // check the kotlin lambda object
    if (value != nullptr) {        
        logger.payload = reinterpret_cast<void*>(
            // new a global reference
            env->NewGlobalRef(value)
        );
        logger.log = callback;
    } else {
        logger.payload = nullptr;
        logger.log = nullptr;
    }
    // native set logger
    ts_parser_set_logger(self, logger);
    env->SetObjectField(thiz, global_field_cache.TSParser_logger, value);
}

static inline TSInputEncoding get_encoding(JNIEnv *env, jobject encoding) {
    jobject UTF_8 = GET_STATIC_FIELD(Object, TSInputEncoding, TSInputEncoding_UTF_8);
    if (encoding == nullptr || env->IsSameObject(encoding, UTF_8)) {
        return TSInputEncodingUTF8;
    }
    
    jobject UTF_16LE = GET_STATIC_FIELD(Object, TSInputEncoding, TSInputEncoding_UTF_16LE);
    if (env->IsSameObject(encoding, UTF_16LE)) {
        return TSInputEncodingUTF16LE;
    }
    
    jobject UTF_16BE = GET_STATIC_FIELD(Object, TSInputEncoding, TSInputEncoding_UTF_16BE);
    if (env->IsSameObject(encoding, UTF_16BE)) {
        return TSInputEncodingUTF16BE;
    }
    UNREACHABLE();
}

jobject JNICALL parser_parse_string(
    JNIEnv *env, jobject thiz, jobject oldTree, jobject charset, jbyteArray byte_array
) { 
    // get the java TSLanguage object from java TSParser
    // note here language is jobect not native type
    jobject language = GET_FIELD(Object, thiz, TSParser_language);
    if (language == nullptr) {
        THROW(IllegalStateException, "The parser has no language assigned");
    }
    // native TSParser pointer
    TSParser *self = GET_POINTER(TSParser, thiz);  
    // text encoding, UTF-8 or UTF-16   
    TSInputEncoding input_encoding = get_encoding(env, charset);
    jbyte *byte_chars = env->GetByteArrayElements(byte_array, nullptr);
    size_t length = env->GetArrayLength(byte_array);
    
    // old native TSTree
    TSTree *old_tree = oldTree ? GET_POINTER(TSTree, oldTree) : nullptr;
    // new native TSTree    
    TSTree *new_tree = ts_parser_parse_string_encoding(
        self, old_tree, reinterpret_cast<const char*>(byte_chars), length, input_encoding
    );
    
    jstring source = nullptr;
    // note here requires to check the encoding
    if(input_encoding == TSInputEncodingUTF8) {
        source = env->NewStringUTF(reinterpret_cast<const char*>(byte_chars));
    } else {
        source = env->NewString(reinterpret_cast<const jchar*>(byte_chars), length / 2);
    }
    
    env->ReleaseByteArrayElements(byte_array, byte_chars, JNI_ABORT);
    // return the new java TSTree object
    return NEW_OBJECT(TSTree, reinterpret_cast<jlong>(new_tree), source, language);
}


// read callback
static const char *parse_read_callback(
    void *payload, uint32_t byte_index, TSPoint point, uint32_t *bytes_read
) {
    ReadState *state = static_cast<ReadState *>(payload);
    JNIEnv *env = state->env;
    // tree-sitter is done with the previous chunk once it asks for another one
    release_chunk(state);
    // the callback threw before: no more Java calls with that exception pending
    if (env->ExceptionCheck()) {
        *bytes_read = 0;
        return "";
    }

    // local java TSPoint object
    jobject position = NEW_OBJECT(TSPoint, point.row, point.column);
    // convert kotlin Int to UInt
    jobject uint_index = env->AllocObject(global_class_cache.UInt);
    env->SetIntField(uint_index, global_field_cache.UInt_data, static_cast<jint>(byte_index));

    // call kotlin ParseCallback lambda
    jbyteArray chunk_bytes = reinterpret_cast<jbyteArray>(
        CALL_METHOD(Object, state->callback, Function2_invoke, uint_index, position)
    );
    env->DeleteLocalRef(uint_index);
    env->DeleteLocalRef(position);

    if (env->ExceptionCheck() || chunk_bytes == nullptr) {
        // the callback failed: the end of the input, the exception goes up afterwards
        *bytes_read = 0;
        return "";
    }
    state->bytes = chunk_bytes;
    state->chunk = env->GetByteArrayElements(chunk_bytes, nullptr);
    *bytes_read = static_cast<uint32_t>(env->GetArrayLength(chunk_bytes));
    // return the native string
    return reinterpret_cast<const char *>(state->chunk);
}

// progress callback
static bool parse_progress_callback(TSParseState *state) {    
    // local JNIEnv pointer
    JNIEnv *env = ::getEnv();    
    jobject offset = env->AllocObject(global_class_cache.UInt);
    env->SetIntField(offset, global_field_cache.UInt_data, (jint)state->current_byte_offset);
    jobject error = NEW_OBJECT(Boolean, (jboolean)state->has_error);
    jobject result = CALL_METHOD(Object, (jobject)state->payload, Function2_invoke, offset, error);
    env->DeleteLocalRef(offset);
    env->DeleteLocalRef(error);
    return static_cast<bool>(
        env->GetBooleanField(result, global_field_cache.Boolean_value)
    );
}

jobject JNICALL parser_parse_function(
    JNIEnv *env, jobject thiz, jobject oldTree, 
    jobject charset, jobject progress_callback, jobject read_callback
) {   
    // get the java TSLanguage object from java TSParser
    // note here language is jobect not native type
    jobject language = GET_FIELD(Object, thiz, TSParser_language);
    if (language == nullptr) {
        THROW(IllegalStateException, "The parser has no language assigned");
        return nullptr;
    }    
    
    // native TSParser pointer
    TSParser *self = GET_POINTER(TSParser, thiz);
    // text encoding, UTF-8 or UTF-16
    TSInputEncoding input_encoding = get_encoding(env, charset);
    
    ReadState state = { env, read_callback, nullptr, nullptr };
    TSInput input = {
        .payload = (void *)&state,
        .read = parse_read_callback,
        .encoding = input_encoding,
    };
    
    // old native TSTree
    TSTree *old_tree = oldTree ? GET_POINTER(TSTree, oldTree) : nullptr;
    // new native TSTree
    TSTree *new_tree = nullptr;
    // ...
    if (progress_callback != nullptr) {
        TSParseOptions options = {
            .payload = (void *)progress_callback,
            .progress_callback = parse_progress_callback,
        };
        new_tree = ts_parser_parse_with_options(self, old_tree, input, options);
    } else {
        new_tree = ts_parser_parse(self, old_tree, input);
    }
    // the last chunk read
    release_chunk(&state);
    // the read callback threw: that goes up to Kotlin, not a tree of half the text
    if (env->ExceptionCheck()) {
        if (new_tree != nullptr) ts_tree_delete(new_tree);
        return nullptr;
    }
        
    // return the new java TSTree object
    return NEW_OBJECT(TSTree, reinterpret_cast<jlong>(new_tree), nullptr, language);
}

extern const JNINativeMethod TSParser_methods[] = {
    {"init", "()J", (void *)&parser_init},
    {"delete", "(J)V", (void *)&parser_delete},
    {"reset", "()V", (void *)&parser_reset},
    {"dotGraphs", "(Ljava/lang/String;)V", (void *)&parser_dot_graphs},
    {"setLanguage", "(L" PACKAGE "TSLanguage;)V", (void *)&parser_set_language},
    {"setIncludedRanges", "(Ljava/util/List;)V", (void *)&parser_set_included_ranges},
    {"setLogger", "(Lkotlin/jvm/functions/Function2;)V", (void *)&parser_set_logger},
    {"parse", "(L" PACKAGE "TSTree;L" PACKAGE "TSInputEncoding;[B)L" PACKAGE "TSTree;",
      (void *)&parser_parse_string},
    {"parse", "(L" PACKAGE "TSTree;L" PACKAGE "TSInputEncoding;"
        "Lkotlin/jvm/functions/Function2;Lkotlin/jvm/functions/Function2;)L" PACKAGE "TSTree;",
      (void *)&parser_parse_function}
};

extern const size_t TSParser_methods_size = sizeof TSParser_methods / sizeof(JNINativeMethod);


#ifdef __cplusplus
}
#endif // __cplusplus

