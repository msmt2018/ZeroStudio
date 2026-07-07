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
#include "utils/ts_obj_utils.h"
#include "utils/ts_preconditions.h"

#include "ts_utils.h"

// ts_point_edit：根据编辑操作更新 point 和对应的字节偏移量
// 此函数会原地修改 point，并返回更新后的字节偏移量
static jint TSUtils_pointEdit(JNIEnv *env,
                              jclass self,
                              jobject point,
                              jint currentByte,
                              jobject edit) {
  req_nnp(env, point, "point");
  req_nnp(env, edit, "edit");

  TSPoint ts_point = _unmarshalPoint(env, point);
  TSInputEdit ts_edit = _unmarshalInputEdit(env, edit);

  uint32_t byte_val = (uint32_t) currentByte;
  ts_point_edit(&ts_point, &byte_val, &ts_edit);

  // 将更新后的 point 写回 Java 对象
  _marshalPointToExisting(env, point, ts_point);

  return (jint) byte_val;
}

// ts_range_edit：根据编辑操作更新 range
// 此函数会原地修改 range
static void TSUtils_rangeEdit(JNIEnv *env,
                               jclass self,
                               jobject range,
                               jobject edit) {
  req_nnp(env, range, "range");
  req_nnp(env, edit, "edit");

  TSRange ts_range = _unmarshalRange(env, range);
  TSInputEdit ts_edit = _unmarshalInputEdit(env, edit);

  ts_range_edit(&ts_range, &ts_edit);

  // 将更新后的 range 写回 Java 对象
  _marshalRangeToExisting(env, range, ts_range);
}

void TSUtils_Native__SetJniMethods(JNINativeMethod *methods, int count) {
  SET_JNI_METHOD(methods, TSUtils_Native_pointEdit, TSUtils_pointEdit);
  SET_JNI_METHOD(methods, TSUtils_Native_rangeEdit, TSUtils_rangeEdit);
}
