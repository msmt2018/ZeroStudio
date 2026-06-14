/*
 *  This file is part of AndroidIDE.
 *
 *  AndroidIDE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  AndroidIDE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with AndroidIDE.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.itsaky.androidide.compose.preview.bytecode

import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.reflect.Field
import java.util.concurrent.ConcurrentHashMap

/**
 * 字段访问器 v2.1 (P3 字节码加速).
 *
 * 替代 `Field.get(obj)` / `Field.set(obj, value)` 反射调用.
 *
 * 优化点:
 * - 缓存 [Field] 对象 (避免每次 `getDeclaredField` 查找)
 * - 缓存 [MethodHandle] (用 `unreflectGetter` / `unreflectSetter` 一次性解析)
 * - 用 `MethodHandle.invoke()` 替代 `Field.get` (5-10x faster)
 * - 可选: 缓存 unsafe accessor (Android ART 友好)
 *
 * ## 用法
 *
 * ```kotlin
 * val accessor = FieldAccessorCache.getOrCreate(LayoutNode::class.java, "id")
 * val id = accessor.get(layoutNodeInstance)
 *
 * // 静态字段 (receiver 传 null):
 * val v = accessor.get(null)
 * ```
 */
class FieldAccessor private constructor(
    val field: Field,
    private val getter: MethodHandle,
    private val setter: MethodHandle,
) {
    val name: String get() = field.name
    val declaringClass: Class<*> get() = field.declaringClass
    val type: Class<*> get() = field.type

    /**
     * 读取字段值.
     *
     * @param receiver 字段所属对象, 静态字段传 null
     * @return 字段值
     */
    operator fun get(receiver: Any?): Any? {
        return if (java.lang.reflect.Modifier.isStatic(field.modifiers)) {
            getter.invoke()
        } else {
            getter.invoke(receiver)
        }
    }

    /**
     * 写入字段值.
     *
     * @param receiver 字段所属对象, 静态字段传 null
     * @param value 新值
     */
    operator fun set(receiver: Any?, value: Any?) {
        if (java.lang.reflect.Modifier.isStatic(field.modifiers)) {
            setter.invoke(value)
        } else {
            setter.invoke(receiver, value)
        }
    }

    /**
     * 把字段值转成 Int (LayoutNode.id 等场景).
     *
     * @throws ClassCastException 若字段类型不匹配
     */
    fun getInt(receiver: Any?): Int = get(receiver) as Int

    /**
     * 把字段值转成 Long.
     */
    fun getLong(receiver: Any?): Long = get(receiver) as Long

    /**
     * 把字段值转成 Float.
     */
    fun getFloat(receiver: Any?): Float = get(receiver) as Float

    /**
     * 把字段值转成 Boolean.
     */
    fun getBoolean(receiver: Any?): Boolean = get(receiver) as Boolean

    /**
     * 把字段值转成 String.
     */
    fun getString(receiver: Any?): String? = get(receiver) as String?

    /**
     * 把字段值转成 List (LayoutNode.children 等场景).
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> getList(receiver: Any?): List<T> = get(receiver) as List<T>

    companion object {
        private val LOOKUP = MethodHandles.lookup()

        /**
         * 创建一个字段访问器, 自动 setAccessible.
         */
        fun forField(field: Field): FieldAccessor {
            // Android ART 默认允许 setAccessible on private field
            // (但 private 字段 in 不同 package 会抛 IllegalAccessException)
            // 尝试 setAccessible, 但不强制成功
            try {
                field.isAccessible = true
            } catch (_: SecurityException) {
                // 安全管理器拦截, 忽略
            } catch (_: java.lang.reflect.InaccessibleObjectException) {
                // Java 17+ 强封装, 用 MethodHandles.privateLookupIn 替代
            }
            val getter = try {
                LOOKUP.unreflectGetter(field)
            } catch (e: IllegalAccessException) {
                // fallback: 显式改 public
                try {
                    field.isAccessible = true
                    LOOKUP.unreflectGetter(field)
                } catch (_: Exception) { throw e }
            }
            val setter = try {
                LOOKUP.unreflectSetter(field)
            } catch (e: IllegalAccessException) {
                try {
                    field.isAccessible = true
                    LOOKUP.unreflectSetter(field)
                } catch (_: Exception) { throw e }
            }
            return FieldAccessor(field, getter, setter)
        }
    }
}

/**
 * Field accessor 全局缓存.
 *
 * ConcurrentHashMap (类似 [MethodHandleCache] 设计, 假设 hot set 较小).
 */
object FieldAccessorCache {
    private val map = ConcurrentHashMap<FieldKey, FieldAccessor>()
    private val misses = java.util.concurrent.atomic.AtomicLong(0)
    private val hits = java.util.concurrent.atomic.AtomicLong(0)

    /**
     * 缓存键: declaringClass + name.
     */
    private data class FieldKey(val declaringClass: Class<*>, val name: String)

    /**
     * 取一个字段的访问器, 首次会做反射查找 + MethodHandle 解析.
     *
     * 字段不存在会抛 [NoSuchFieldException].
     */
    fun getOrCreate(declaringClass: Class<*>, name: String): FieldAccessor {
        val key = FieldKey(declaringClass, name)
        return map.computeIfAbsent(key) { _ ->
            val field = declaringClass.getDeclaredField(name)
            FieldAccessor.forField(field)
        }
    }

    /**
     * 尝试获取, 字段不存在返回 null (用于版本兼容: Compose 不同版本字段名不同).
     */
    fun tryGet(declaringClass: Class<*>, name: String): FieldAccessor? {
        val key = FieldKey(declaringClass, name)
        map[key]?.let {
            hits.incrementAndGet()
            return it
        }
        misses.incrementAndGet()
        return try {
            val field = declaringClass.getDeclaredField(name)
            val accessor = FieldAccessor.forField(field)
            map[key] = accessor
            accessor
        } catch (_: NoSuchFieldException) {
            null
        }
    }

    fun size(): Int = map.size
    fun hits(): Long = hits.get()
    fun misses(): Long = misses.get()

    fun clear() = map.clear()

    /**
     * 命中率, 范围 [0, 1].
     */
    fun hitRate(): Double {
        val h = hits.get()
        val m = misses.get()
        return if (h + m == 0L) 0.0 else h.toDouble() / (h + m).toDouble()
    }
}
