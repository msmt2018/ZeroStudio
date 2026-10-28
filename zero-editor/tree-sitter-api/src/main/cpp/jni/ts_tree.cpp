#include <errno.h>
#include <fcntl.h>
#include <sys/stat.h>
#include <sys/types.h>

#include "ts_utils.h"

#ifdef __cplusplus
extern "C" {
#endif

jlong JNICALL tree_copy(jlong tree) { 
    return reinterpret_cast<jlong>(
        ts_tree_copy(reinterpret_cast<TSTree*>(tree))
    );
}

void JNICALL tree_delete(jlong tree) { 
    ts_tree_delete(reinterpret_cast<TSTree*>(tree)); 
}

jobject JNICALL tree_get_root_node(JNIEnv *env, jobject thiz) {
    TSTree *self = GET_POINTER(TSTree, thiz);
    TSNode root_node = ts_tree_root_node(self);
    return marshal_node(env, &root_node, thiz);
}

jobject JNICALL tree_root_node_with_offset(JNIEnv *env, jobject thiz, jint offset, jobject point) {
    TSTree *self = GET_POINTER(TSTree, thiz);
    TSNode node = ts_tree_root_node_with_offset(
        self, 
        static_cast<uint32_t>(offset), 
        unmarshal_point(env, point)
    );
    return marshal_node(env, &node, thiz);
}

void JNICALL tree_edit(JNIEnv *env, jobject thiz, jobject edit) {
    TSTree *self = GET_POINTER(TSTree, thiz);
    // edit operation
    TSInputEdit input_edit = unmarshal_input_edit(env, edit);
    // edit the syntax tree
    ts_tree_edit(self, &input_edit);
}

jobject JNICALL tree_changed_ranges(JNIEnv *env, jobject thiz, jobject newTree) {
    uint32_t length;
    TSTree *old_tree = GET_POINTER(TSTree, thiz);
    TSTree *new_tree = GET_POINTER(TSTree, newTree);
    
    TSRange *ranges = ts_tree_get_changed_ranges(old_tree, new_tree, &length);
    if (length == 0 || ranges == nullptr)
        return NEW_OBJECT(ArrayList, 0);
    
    jobject array_list = NEW_OBJECT(ArrayList, (jint)length);
    for (uint32_t i = 0; i < length; ++i) {
        jobject range_object = marshal_range(env, &ranges[i]);
        CALL_METHOD(Boolean, array_list, ArrayList_add, range_object);
        env->DeleteLocalRef(range_object);
    }
    // ...
    return array_list;
}

jobject JNICALL tree_included_ranges(JNIEnv *env, jobject thiz) {
    uint32_t length;
    TSTree *self = GET_POINTER(TSTree, thiz);
    TSRange *ranges = ts_tree_included_ranges(self, &length);   
    if (length == 0 || ranges == nullptr)
        return NEW_OBJECT(ArrayList, 0);
    
    jobject array_list = NEW_OBJECT(ArrayList, (jint)length);
    for (uint32_t i = 0; i < length; ++i) {
        jobject range_object = marshal_range(env, &ranges[i]);
        CALL_METHOD(Boolean, array_list, ArrayList_add, range_object);
        env->DeleteLocalRef(range_object);
    }
    // ...
    return array_list;
}

void JNICALL tree_dot_graph(JNIEnv *env, jobject thiz, jstring pathname) {
    TSTree *self = GET_POINTER(TSTree, thiz);
    const char *path =env->GetStringUTFChars(pathname, nullptr); 
    // here may requires MANAGE_EXTERNAL_STORAGE permission
    int fp = open(path, O_CREAT|O_RDWR, 0666);
    if(fp < 0) 
        LOGE("Error: %s\n", strerror(errno));
    else 
        ts_tree_print_dot_graph(self, fp);
    
    env->ReleaseStringUTFChars(pathname, path);
}

extern const JNINativeMethod TSTree_methods[] = {
    {"copy", "(J)J", (void *)&tree_copy},
    {"delete", "(J)V", (void *)&tree_delete},
    {"getRootNode", "()L" PACKAGE "TSNode;", (void *)&tree_get_root_node},
    {"rootNodeWithOffset", "(IL" PACKAGE "TSPoint;)L" PACKAGE "TSNode;",
     (void *)&tree_root_node_with_offset},
    {"edit", "(L" PACKAGE "TSInputEdit;)V", (void *)&tree_edit},
    {"changedRanges", "(L" PACKAGE "TSTree;)Ljava/util/List;", (void *)&tree_changed_ranges},
    {"includedRanges", "()Ljava/util/List;", (void *)&tree_included_ranges},
    {"dotGraph", "(Ljava/lang/String;)V", (void *)&tree_dot_graph}
};

extern const size_t TSTree_methods_size = sizeof TSTree_methods / sizeof(JNINativeMethod);

#ifdef __cplusplus
}
#endif // __cplusplus

