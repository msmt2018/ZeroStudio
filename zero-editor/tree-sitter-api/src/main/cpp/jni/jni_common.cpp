#include <stdlib.h>
#include <errno.h>
#include <pthread.h>

#include "ts_utils.h"

#ifdef __cplusplus
extern "C" {
#endif

// define global caches
JFieldCache global_field_cache = { };
JMethodCache global_method_cache = { };
JClassCache global_class_cache = { };

// global JVM
static JavaVM *jvm = nullptr;

static pthread_key_t key;
static pthread_once_t key_once = PTHREAD_ONCE_INIT;

// declare external native methods
extern const JNINativeMethod TSQuery_methods[];
extern const size_t TSQuery_methods_size;
extern const JNINativeMethod TSQueryCursor_methods[];
extern const size_t TSQueryCursor_methods_size;
extern const JNINativeMethod TSParser_methods[];
extern const size_t TSParser_methods_size;
extern const JNINativeMethod TSNode_methods[];
extern const size_t TSNode_methods_size;

extern const JNINativeMethod TSTree_methods[];
extern const size_t TSTree_methods_size;
extern const JNINativeMethod TSTreeCursor_methods[];
extern const size_t TSTreeCursor_methods_size;
extern const JNINativeMethod TSLanguage_methods[];
extern const size_t TSLanguage_methods_size;
extern const JNINativeMethod TSLookaheadIterator_methods[];
extern const size_t TSLookaheadIterator_methods_size;

#define REGISTER_METHOD(clazz)                  \
    do {                                            \
        jint result = env->RegisterNatives(         \
            global_class_cache.clazz,               \
            _cat2(clazz, methods),                  \
            _cat2(_cat2(clazz, methods), size)      \
        );                                          \
        if (result != JNI_OK) {                      \
            return result;                           \
        }                                           \
    } while(0)

#define CACHE_CLASS(package, name)                  \
    do {                                                  \
        jclass local = env->FindClass(package #name);   \
        if (local == nullptr) {                               \            
            return JNI_ERR;                                   \
        }                                                       \
        global_class_cache.name = (jclass)env->NewGlobalRef(local); \
        env->DeleteLocalRef(local);                            \           
    } while (0)

#define CACHE_FIELD(clazz, field, jtype)                         \
    global_field_cache._cat2(clazz, field) =                        \
        env->GetFieldID(global_class_cache.clazz, #field, jtype)

#define CACHE_STATIC_FIELD(clazz, field, jtype)                   \
    global_field_cache._cat2(clazz, field) =                          \
        env->GetStaticFieldID(global_class_cache.clazz, #field, jtype)

#define CACHE_METHOD(clazz, method, name, jtype)             \
    global_method_cache._cat2(clazz, method) =                   \
        env->GetMethodID(global_class_cache.clazz, name, jtype)

#define CACHE_STATIC_METHOD(clazz, method, name, jtype)        \
    global_method_cache._cat2(clazz, method) =                      \
        env->GetStaticMethodID(global_class_cache.clazz, name, jtype)


extern JavaVM* getJavaVM() {
    return ::jvm;
}

static void make_key() {
    pthread_key_create(&key, [](void*) {
        jvm->DetachCurrentThread();
    });
}

extern JNIEnv* getEnv() {
    // call once here
    pthread_once(&key_once, make_key);
    JNIEnv *env = static_cast<JNIEnv*>(pthread_getspecific(key));
    // cache the env pointer
    if(env == nullptr) {
        jint status = jvm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION);
        switch(status) {
        case JNI_OK:
            pthread_setspecific(key, env);
            break;
        case JNI_EDETACHED:
            if(jvm->AttachCurrentThread(&env, nullptr) < 0) {
                LOGE("%s\n", "The jvm failed to attach current thread");
            } else {
                pthread_setspecific(key, env);
            }
            break;
        case JNI_EVERSION:
        default:
            LOGE("%s\n", "The jvm failed to get the env pointer");
            break;
        }
    }
    return env;
}

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    // init the global JavaVm
    ::jvm = vm;
    // init the JNIEnv
    JNIEnv *env = ::getEnv();
    if(env == nullptr) {
        LOGE("Failed to init the jvm %s\n", strerror(errno));
        return JNI_ERR;
    }
    
    // cache the global classes, methods and fields
    CACHE_CLASS(PACKAGE, TSParser);
    CACHE_FIELD(TSParser, self, "J");
    CACHE_FIELD(TSParser, includedRanges, "Ljava/util/List;");
    CACHE_FIELD(TSParser, language, "L" PACKAGE "TSLanguage;");
    CACHE_FIELD(TSParser, logger, "Lkotlin/jvm/functions/Function2;");
    
    CACHE_CLASS(PACKAGE, TSParser$LogType);
    CACHE_STATIC_FIELD(TSParser$LogType, PARSE, "L" PACKAGE "TSParser$LogType;");
    CACHE_STATIC_FIELD(TSParser$LogType, LEX, "L" PACKAGE "TSParser$LogType;");
    
    CACHE_CLASS(PACKAGE, TSNode);    
    CACHE_FIELD(TSNode, context, "[I");
    CACHE_FIELD(TSNode, id, "J");
    CACHE_FIELD(TSNode, tree, "L" PACKAGE "TSTree;");
    CACHE_METHOD(TSNode, init, "<init>", "([IJL" PACKAGE "TSTree;)V");
    
    CACHE_CLASS(PACKAGE, TSPoint);
    CACHE_METHOD(TSPoint, init, "<init>", "(II)V");
    CACHE_FIELD(TSPoint, row, "I");
    CACHE_FIELD(TSPoint, column, "I");
    
    CACHE_CLASS(PACKAGE, TSRange);
    CACHE_FIELD(TSRange, startByte, "I");
    CACHE_FIELD(TSRange, endByte, "I");
    CACHE_FIELD(TSRange, startPoint, "L" PACKAGE "TSPoint;");
    CACHE_FIELD(TSRange, endPoint, "L" PACKAGE "TSPoint;");
    CACHE_METHOD(TSRange, init, "<init>", 
      "(L" PACKAGE "TSPoint;L" PACKAGE "TSPoint;II)V");
        
    CACHE_CLASS(PACKAGE, TSInputEncoding);
    CACHE_METHOD(TSInputEncoding, ordinal, "ordinal", "()I");
    CACHE_STATIC_FIELD(TSInputEncoding, UTF_8, "L" PACKAGE "TSInputEncoding;");
    CACHE_STATIC_FIELD(TSInputEncoding, UTF_16LE, "L" PACKAGE "TSInputEncoding;");
    CACHE_STATIC_FIELD(TSInputEncoding, UTF_16BE, "L" PACKAGE "TSInputEncoding;");
    
    CACHE_CLASS(PACKAGE, TSLanguage);
    CACHE_FIELD(TSLanguage, self, "J");
    CACHE_METHOD(TSLanguage, init, "<init>", "(J)V");
    
    CACHE_CLASS(PACKAGE, TSLanguage$Metadata);
    CACHE_METHOD(TSLanguage$Metadata, init, "<init>", "(Lkotlin/Triple;)V");
    
    CACHE_CLASS(PACKAGE, TSLookaheadIterator);
    CACHE_FIELD(TSLookaheadIterator, self, "J");
    
    CACHE_CLASS(PACKAGE, TSTree);   
    CACHE_FIELD(TSTree, self, "J");
    CACHE_FIELD(TSTree, source, "Ljava/lang/String;");
    CACHE_FIELD(TSTree, language, "L" PACKAGE "TSLanguage;");
    CACHE_METHOD(TSTree, init, "<init>", "(JLjava/lang/String;L" PACKAGE "TSLanguage;)V");
    
    CACHE_CLASS(PACKAGE, TSTreeCursor);
    CACHE_FIELD(TSTreeCursor, self, "J");
    CACHE_FIELD(TSTreeCursor, tree, "L" PACKAGE "TSTree;");    
    
    CACHE_CLASS(PACKAGE, TSSymbolType);
    CACHE_STATIC_FIELD(TSSymbolType, REGULAR, "L" PACKAGE "TSSymbolType;");
    CACHE_STATIC_FIELD(TSSymbolType, ANONYMOUS, "L" PACKAGE "TSSymbolType;");
    CACHE_STATIC_FIELD(TSSymbolType, AUXILIARY, "L" PACKAGE "TSSymbolType;");
    
    CACHE_CLASS(PACKAGE, TSInputEdit);
    CACHE_FIELD(TSInputEdit, startByte, "I");
    CACHE_FIELD(TSInputEdit, oldEndByte, "I");
    CACHE_FIELD(TSInputEdit, newEndByte, "I");
    CACHE_FIELD(TSInputEdit, startPoint, "L" PACKAGE "TSPoint;");
    CACHE_FIELD(TSInputEdit, oldEndPoint, "L" PACKAGE "TSPoint;");
    CACHE_FIELD(TSInputEdit, newEndPoint, "L" PACKAGE "TSPoint;");
    
    CACHE_CLASS(PACKAGE, TSQuery);
    CACHE_FIELD(TSQuery, self, "J");
    CACHE_FIELD(TSQuery, language, "L" PACKAGE "TSLanguage;");
    CACHE_FIELD(TSQuery, pattern, "Ljava/lang/String;");
    
    CACHE_CLASS(PACKAGE, TSQueryCursor);
    CACHE_FIELD(TSQueryCursor, self, "J");
    CACHE_FIELD(TSQueryCursor, matchLimit, "I");
    CACHE_FIELD(TSQueryCursor, maxStartDepth, "I");      
    
    CACHE_CLASS(PACKAGE, TSQueryCapture);
    CACHE_METHOD(TSQueryCapture, init, "<init>", 
      "(L" PACKAGE "TSNode;Ljava/lang/String;)V");

    CACHE_CLASS(PACKAGE, TSQueryMatch);
    CACHE_METHOD(TSQueryMatch, init, "<init>", "(ILjava/util/List;)V");

    CACHE_CLASS(PACKAGE, TSQueryError$Capture);
    CACHE_METHOD(TSQueryError$Capture, init, "<init>", "(IILjava/lang/String;)V");

    CACHE_CLASS(PACKAGE, TSQueryError$Field);
    CACHE_METHOD(TSQueryError$Field, init, "<init>", "(IILjava/lang/String;)V");

    CACHE_CLASS(PACKAGE, TSQueryError$NodeType);
    CACHE_METHOD(TSQueryError$NodeType, init, "<init>", "(IILjava/lang/String;)V");

    CACHE_CLASS(PACKAGE, TSQueryError$Syntax);
    CACHE_METHOD(TSQueryError$Syntax, init, "<init>", "(JJ)V");

    CACHE_CLASS(PACKAGE, TSQueryError$Structure);
    CACHE_METHOD(TSQueryError$Structure, init, "<init>", "(II)V");
    
    CACHE_CLASS("java/util/", List);
    CACHE_METHOD(List, size, "size", "()I");
    CACHE_METHOD(List, get, "get", "(I)Ljava/lang/Object;");
    
    CACHE_CLASS("java/util/", ArrayList);
    CACHE_METHOD(ArrayList, init, "<init>", "(I)V");
    CACHE_METHOD(ArrayList, add, "add", "(Ljava/lang/Object;)Z");
    
    CACHE_CLASS("kotlin/", Pair);
    CACHE_METHOD(Pair, init, "<init>", "(Ljava/lang/Object;Ljava/lang/Object;)V");

    CACHE_CLASS("kotlin/", UInt);
    CACHE_FIELD(UInt, data, "I");
    
    CACHE_CLASS("kotlin/", UShort);
    CACHE_FIELD(UShort, data, "S");
    
    CACHE_CLASS("java/lang/", Boolean);
    CACHE_METHOD(Boolean, init, "<init>", "(Z)V");
    CACHE_FIELD(Boolean, value, "Z");
    
    CACHE_CLASS("kotlin/", Triple);
    CACHE_METHOD(Triple, init, "<init>",
      "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V");
    
    CACHE_CLASS("kotlin/jvm/functions/", Function1);
    CACHE_METHOD(Function1, invoke, "invoke",
      "(Ljava/lang/Object;)Ljava/lang/Object;");
    
    CACHE_CLASS("kotlin/jvm/functions/", Function2);
    CACHE_METHOD(Function2, invoke, "invoke",
      "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
    
    CACHE_CLASS("java/lang/", IllegalStateException);
    CACHE_CLASS("java/lang/", IllegalArgumentException);
    CACHE_CLASS("java/lang/", IndexOutOfBoundsException);
    
    // register native methods
    REGISTER_METHOD(TSQuery);
    REGISTER_METHOD(TSQueryCursor);
    REGISTER_METHOD(TSParser);
    REGISTER_METHOD(TSNode);
    REGISTER_METHOD(TSTree);
    REGISTER_METHOD(TSTreeCursor);
    REGISTER_METHOD(TSLanguage);
    REGISTER_METHOD(TSLookaheadIterator);
    
#ifdef __ANDROID__
    // set tree-sitter allocator
    ts_set_allocator(malloc, calloc, realloc, free);
#endif
    
    return JNI_VERSION;
}

JNIEXPORT void JNI_OnUnload(JavaVM *vm, void *reserved) {
    JNIEnv *env = ::getEnv();
    // note here requires release the global references
    env->DeleteGlobalRef(global_class_cache.UInt);
    env->DeleteGlobalRef(global_class_cache.UShort);
    env->DeleteGlobalRef(global_class_cache.Boolean);
    env->DeleteGlobalRef(global_class_cache.Pair);
    env->DeleteGlobalRef(global_class_cache.List);
    env->DeleteGlobalRef(global_class_cache.Triple);
    env->DeleteGlobalRef(global_class_cache.ArrayList);
    env->DeleteGlobalRef(global_class_cache.Function1);
    env->DeleteGlobalRef(global_class_cache.Function2);
    env->DeleteGlobalRef(global_class_cache.IllegalArgumentException);
    env->DeleteGlobalRef(global_class_cache.IllegalStateException);
    env->DeleteGlobalRef(global_class_cache.IndexOutOfBoundsException);
    
    env->DeleteGlobalRef(global_class_cache.TSTree);
    env->DeleteGlobalRef(global_class_cache.TSTreeCursor);
    env->DeleteGlobalRef(global_class_cache.TSParser);
    env->DeleteGlobalRef(global_class_cache.TSParser$LogType);
    env->DeleteGlobalRef(global_class_cache.TSLanguage$Metadata);
    env->DeleteGlobalRef(global_class_cache.TSLookaheadIterator);
        
    env->DeleteGlobalRef(global_class_cache.TSNode);
    env->DeleteGlobalRef(global_class_cache.TSPoint);
    env->DeleteGlobalRef(global_class_cache.TSRange);
    env->DeleteGlobalRef(global_class_cache.TSInputEdit);
    env->DeleteGlobalRef(global_class_cache.TSSymbolType);
    
    env->DeleteGlobalRef(global_class_cache.TSQuery);
    env->DeleteGlobalRef(global_class_cache.TSQueryCursor);
    env->DeleteGlobalRef(global_class_cache.TSQueryCapture);
    env->DeleteGlobalRef(global_class_cache.TSQueryMatch);    
    env->DeleteGlobalRef(global_class_cache.TSQueryError$Capture);
    env->DeleteGlobalRef(global_class_cache.TSQueryError$Field);
    env->DeleteGlobalRef(global_class_cache.TSQueryError$NodeType);
    env->DeleteGlobalRef(global_class_cache.TSQueryError$Structure);
    env->DeleteGlobalRef(global_class_cache.TSQueryError$Syntax);
}

#ifdef __cplusplus
}
#endif // __cplusplus

