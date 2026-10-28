#ifndef __TS_UTILS_H__
#define __TS_UTILS_H__

#include <tree_sitter/api.h>

#include "jni_common.h"

#ifdef __cplusplus
extern "C" {
#endif

#define _xcat(a, b, c) a##b##c
#define _cat3(a, b, c) _xcat(a, b, c)
#define _cat2(a, b) _xcat(a, _, b)

#define GET_FIELD(jtype, object, field)                                                 \
    env->_cat3(Get, jtype, Field)((object), global_field_cache.field)

#define GET_STATIC_FIELD(jtype, clazz, field)                                            \
    env->_cat3(GetStatic, jtype, Field)(global_class_cache.clazz, global_field_cache.field)

// convert java jlong(pointer addr) to native pointer type
#define GET_POINTER(ctype, object) (ctype *)GET_FIELD(Long, object, _cat2(ctype, self))
/* #define GET_POINTER(jtype, object) (_xcat(TS, jtype, *))GET_FIELD(Long, object, _cat2(jtype, self)) */

#define CALL_METHOD(jtype, object, method, ...)                                          \
    env->_cat3(Call, jtype, Method)((object), global_method_cache.method, __VA_ARGS__)

#define CALL_STATIC_METHOD(jtype, clazz, method, ...)                                   \
    env->_cat3(CallStatic, jtype, Method)(global_class_cache.clazz,                        \
                                             global_method_cache.method, __VA_ARGS__)

#define CALL_METHOD_NO_ARGS(jtype, object, method)                                 \
    env->_cat3(Call, jtype, Method)((object), global_method_cache.method)

#define NEW_OBJECT(clazz, ...)                                                               \
    env->NewObject(global_class_cache.clazz, global_method_cache._cat2(clazz, init),       \
                      __VA_ARGS__)

#define NEW_OBJECT_NO_ARGS(clazz)                                                       \
    env->NewObject(global_class_cache.clazz, global_method_cache._cat2(clazz, init))
                      
#define THROW(clazz, message) env->ThrowNew(global_class_cache.clazz, message)

#if defined(__GNUC__) || defined(__clang__)
#define UNREACHABLE() __builtin_unreachable()
#else
#define UNREACHABLE() abort()
#endif

// java classes -> native structs
typedef struct {
    jclass TSNode;
    jclass TSPoint;
    jclass TSParser;
    jclass TSParser$LogType;
    jclass TSTree;
    jclass TSTreeCursor;
    jclass TSQuery;
    jclass TSQueryCursor;
    jclass TSRange;
    
    jclass TSInputEdit;
    jclass TSSymbolType;
    jclass TSInputEncoding;
    jclass TSLanguage;
    jclass TSLanguage$Metadata;
    jclass TSLookaheadIterator;
    jclass TSCapture;
    jclass TSQuantifier;
    
    jclass TSQueryCapture;
    jclass TSQueryMatch;
    jclass TSQueryPredicateStep;
    jclass TSQueryPredicateStepType;
    
    jclass TSQueryError$Capture;
    jclass TSQueryError$Field;
    jclass TSQueryError$NodeType;
    jclass TSQueryError$Structure;
    jclass TSQueryError$Syntax;
    
    jclass Boolean;
    jclass Triple;
    jclass UShort;
    jclass UInt;
    jclass Pair;
    jclass List;
    jclass ArrayList;
    jclass Function1;
    jclass Function2;

    jclass IllegalStateException;
    jclass IllegalArgumentException;
    jclass IndexOutOfBoundsException;
} JClassCache;

typedef struct {
    jfieldID TSInputEdit_startByte;
    jfieldID TSInputEdit_oldEndByte;
    jfieldID TSInputEdit_newEndByte;
    jfieldID TSInputEdit_startPoint;
    jfieldID TSInputEdit_oldEndPoint;
    jfieldID TSInputEdit_newEndPoint;
    
    jfieldID TSLanguage_self;
    
    jfieldID TSInputEncoding_UTF_8;
    jfieldID TSInputEncoding_UTF_16LE;
    jfieldID TSInputEncoding_UTF_16BE;
    
    jfieldID TSNode_context;
    jfieldID TSNode_id;
    jfieldID TSNode_tree;
    
    jfieldID TSParser_self;
    jfieldID TSParser_logger;
    jfieldID TSParser_language;
    jfieldID TSParser_includedRanges;
    
    jfieldID TSPoint_row;
    jfieldID TSPoint_column;   
    
    jfieldID TSRange_startByte;
    jfieldID TSRange_endByte;
    jfieldID TSRange_startPoint;
    jfieldID TSRange_endPoint;
    
    jfieldID TSParser$LogType_PARSE;
    jfieldID TSParser$LogType_LEX;
    
    jfieldID TSSymbolType_REGULAR;
    jfieldID TSSymbolType_ANONYMOUS;
    jfieldID TSSymbolType_AUXILIARY;
    
    jfieldID TSTree_self;
    jfieldID TSTree_source;
    jfieldID TSTree_language;
    
    jfieldID TSTreeCursor_self;
    jfieldID TSTreeCursor_tree;
    
    jfieldID TSQuery_self;
    jfieldID TSQuery_pattern;
    jfieldID TSQuery_language;
    
    jfieldID TSQueryCursor_self;
    jfieldID TSQueryCursor_matchLimit;
    jfieldID TSQueryCursor_maxStartDepth;
        
    jfieldID TSLookaheadIterator_self;
    
    jfieldID UInt_data;
    jfieldID UShort_data;
    jfieldID Boolean_value;
} JFieldCache;

typedef struct {
    jmethodID TSTree_init;
    jmethodID TSLanguage_init;
    jmethodID TSLanguage$Metadata_init;
    jmethodID TSNode_init;
    jmethodID TSPoint_init;
    jmethodID TSRange_init;
    jmethodID TSInputEncoding_ordinal;
    
    jmethodID TSQueryError$Capture_init;
    jmethodID TSQueryError$Field_init;
    jmethodID TSQueryError$NodeType_init;
    jmethodID TSQueryError$Structure_init;
    jmethodID TSQueryError$Syntax_init;
    
    jmethodID TSQueryCursor_init;
    jmethodID TSQueryMatch_init;
    jmethodID TSQueryCapture_init;
    
    jmethodID List_get;
    jmethodID List_size;
    jmethodID ArrayList_add;
    jmethodID ArrayList_init;
    
    jmethodID Function1_invoke;
    jmethodID Function2_invoke;
    jmethodID Triple_init;
    jmethodID Pair_init;
    jmethodID Boolean_init;
} JMethodCache;

extern JFieldCache global_field_cache;
extern JMethodCache global_method_cache;
extern JClassCache global_class_cache;


// get the java TSNode object
static inline jobject marshal_node(
   JNIEnv *env, const TSNode *node, jobject tree
) {
    jintArray int_array = env->NewIntArray(4);
    env->SetIntArrayRegion(int_array, 0, 4, (jint*)node->context);
    jlong id = reinterpret_cast<jlong>(node->id);
    return NEW_OBJECT(TSNode, int_array, id, tree);
}


// get the native TSNode
static inline TSNode unmarshal_node(JNIEnv *env, jobject node) {
    auto int_array = static_cast<jintArray>(
        GET_FIELD(Object, node, TSNode_context)
    );
    uint32_t context[4];
    env->GetIntArrayRegion(int_array, 0, 4, (jint*)context);
    jlong id = GET_FIELD(Long, node, TSNode_id);
    // java TSTree object
    jobject tree = GET_FIELD(Object, node, TSNode_tree);
    // native TSTree pointer
    jlong self = GET_FIELD(Long, tree, TSTree_self);
    
    return TSNode {
        .context = {context[0], context[1], context[2], context[3]},
        .id = reinterpret_cast<const void*>(id),
        .tree = reinterpret_cast<const TSTree*>(self)
    };
}

// get java TSPoint object
static inline jobject marshal_point(JNIEnv *env, const TSPoint *point) {
    return NEW_OBJECT(TSPoint, point->row, point->column);
}

// get the native TSPoint
static inline TSPoint unmarshal_point(JNIEnv *env, jobject point) {
    return TSPoint {
        .row = static_cast<uint32_t>(
            GET_FIELD(Int, point, TSPoint_row)
        ),
        .column = static_cast<uint32_t>(
            GET_FIELD(Int, point, TSPoint_column)
        )
    };
}

// get the java TSRange object
static inline jobject marshal_range(JNIEnv *env, const TSRange *range) {
    return NEW_OBJECT(
        TSRange, 
        marshal_point(env, &range->start_point), 
        marshal_point(env, &range->end_point), 
        range->start_byte, 
        range->end_byte
    );
}

// get the native TSRange
static inline TSRange unmarshal_range(JNIEnv *env, jobject range) {
    return TSRange {
        .start_point = unmarshal_point(
            env, GET_FIELD(Object, range, TSRange_startPoint)
        ),
        .end_point = unmarshal_point(
            env, GET_FIELD(Object, range, TSRange_endPoint)
        ),
        .start_byte = static_cast<uint32_t>(
            GET_FIELD(Int, range, TSRange_startByte)
        ),
        .end_byte = static_cast<uint32_t>(
            GET_FIELD(Int, range, TSRange_endByte)
        )
    };
}

// get the native TSInputEdit
static inline TSInputEdit unmarshal_input_edit(JNIEnv *env, jobject edit) {
    return TSInputEdit {
        .start_byte = static_cast<uint32_t>(
            GET_FIELD(Int, edit, TSInputEdit_startByte)
        ),
        .old_end_byte = static_cast<uint32_t>(
            GET_FIELD(Int, edit, TSInputEdit_oldEndByte)
        ),
        .new_end_byte = static_cast<uint32_t>(
            GET_FIELD(Int, edit, TSInputEdit_newEndByte)
        ),
        .start_point = unmarshal_point(
            env, GET_FIELD(Object, edit, TSInputEdit_startPoint)
        ),
        .old_end_point = unmarshal_point(
            env, GET_FIELD(Object, edit, TSInputEdit_oldEndPoint)
        ),
        .new_end_point = unmarshal_point(
            env, GET_FIELD(Object, edit, TSInputEdit_newEndPoint)
        )
    };
}

#ifdef __cplusplus
}
#endif // __cplusplus

#endif // __TS_UTILS_H__

