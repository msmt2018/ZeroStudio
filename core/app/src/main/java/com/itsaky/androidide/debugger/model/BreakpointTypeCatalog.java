/*
 *  ZeroStudio IDE - 断点类型总目录 (Phase 20 重构)
 *
 *  4 大类 + 13 个子类型,统一描述每个断点类型的:
 *    - 类别 (Gutter / Variables / Breakpoints Window / Browser)
 *    - 用户展示名 / 描述
 *    - 入口点 (在哪个 UI 位置触发)
 *    - 对应 IdeBreakpoint.Kind + 附加配置
 *    - 创建/修改入口
 *
 *  这里是"目录 + 元数据"。具体创建由 BreakpointManager 完成。
 */

package com.itsaky.androidide.debugger.model;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import com.itsaky.androidide.R;

public final class BreakpointTypeCatalog {

    private BreakpointTypeCatalog() {}

    public enum Category {
        GUTTER,
        VARIABLES,
        WINDOW,
        BROWSER
    }

    /** 入口 — 告诉 UI 这个断点类型应该出现在哪里。 */
    public enum EntryPoint {
        // Gutter (5 个)
        GUTTER_LINE_CLICK,         // 1. 普通行断点 — 单击空白行
        GUTTER_METHOD_DECL_CLICK,  // 2. 方法/函数入口断点 — 函数/接口声明行单击
        GUTTER_BP_RIGHT_CLICK,     // 3. 条件断点 — 右键已有 → 弹条件输入
        GUTTER_BP_RIGHT_CLICK_LOG, // 4. 日志断点 — 右键已有 → 日志消息
        GUTTER_INLINE_CLICK,       // 5. 内联断点 — 行内多表达式子位置

        // Variables (2 个)
        VAR_FIELD_RIGHT_CLICK_MOD, // 6. 字段修改断点 — Variables 行右键 → Break on Modification
        VAR_FIELD_RIGHT_CLICK_ACC, // 7. 字段读取断点 — Variables 行右键 → Break on Access
        VAR_INSTANCE_RIGHT_CLICK,  // 8. 实例过滤器 — Variables 行右键 → Filter by this instance

        // Breakpoints Window (3 个)
        WINDOW_EXCEPTION_ADD,      // 9. 异常断点 — Window + → 异常类名
        WINDOW_SYMBOLIC_ADD,       // 10. 符号断点 — Window + → 函数名
        WINDOW_DEPENDENT_TOGGLE,   // 11. 依赖断点 — 在已有断点的详情中勾选 "Only enable after B is hit"

        // Browser (3 个, Phase 20 接口预留)
        BROWSER_DOM_ADD,           // 12. DOM 断点
        BROWSER_XHR_ADD,           // 13. XHR/Fetch 断点
        BROWSER_EVENT_ADD;         // 14. EventListener 断点 (注: 第 4 类实际有 3 个)
    }

    /** 一个具体断点类型的元数据。 */
    public static final class Entry {
        @NonNull public final Category category;
        @NonNull public final EntryPoint entryPoint;
        @NonNull public final IdeBreakpoint.Kind kind;
        @StringRes public final int titleRes;
        @StringRes public final int descRes;
        @DrawableRes public final int iconRes;
        /** 是否需要"elementName"(异常类名 / 字段名 / 函数名 / 选择器)。 */
        public final boolean needsElement;
        /** 是否需要"hit condition"。 */
        public final boolean needsCondition;
        /** 是否需要"log message"。 */
        public final boolean needsLogMessage;
        /** 是否需要"hit count"。 */
        public final boolean needsHitCount;
        /** 默认 临时 (命中后自动删)。 */
        public final boolean defaultTemporary;
        /** 默认 catchCaught / catchUncaught / methodEntry 等 — 通过 ide-breakpoint Kind 提供。 */
        public final boolean defaultWatchModification;
        public final boolean defaultWatchAccess;
        public final boolean defaultMethodEntry;
        public final boolean defaultMethodExit;
        public final boolean defaultCatchCaught;
        public final boolean defaultCatchUncaught;

        Entry(@NonNull Category c, @NonNull EntryPoint ep, @NonNull IdeBreakpoint.Kind k,
              @StringRes int title, @StringRes int desc, @DrawableRes int icon,
              boolean needsElement, boolean needsCondition, boolean needsLogMessage,
              boolean needsHitCount, boolean temporary,
              boolean watchMod, boolean watchAcc,
              boolean methodEntry, boolean methodExit,
              boolean catchCaught, boolean catchUncaught) {
            this.category = c;
            this.entryPoint = ep;
            this.kind = k;
            this.titleRes = title;
            this.descRes = desc;
            this.iconRes = icon;
            this.needsElement = needsElement;
            this.needsCondition = needsCondition;
            this.needsLogMessage = needsLogMessage;
            this.needsHitCount = needsHitCount;
            this.defaultTemporary = temporary;
            this.defaultWatchModification = watchMod;
            this.defaultWatchAccess = watchAcc;
            this.defaultMethodEntry = methodEntry;
            this.defaultMethodExit = methodExit;
            this.defaultCatchCaught = catchCaught;
            this.defaultCatchUncaught = catchUncaught;
        }

        @NonNull
        @Override
        public String toString() {
            return "Entry{category=" + category + ", kind=" + kind + "}";
        }
    }

    // ========== 13 个标准条目 ==========

    public static final Entry ENTRY_LINE = new Entry(
            Category.GUTTER, EntryPoint.GUTTER_LINE_CLICK, IdeBreakpoint.Kind.LINE,
            R.string.debugger_bp_type_line_title,
            R.string.debugger_bp_type_line_desc,
            R.drawable.ic_bp_line,
            false, false, false, false, false,
            false, false, false, false, false, false);

    public static final Entry ENTRY_METHOD = new Entry(
            Category.GUTTER, EntryPoint.GUTTER_METHOD_DECL_CLICK, IdeBreakpoint.Kind.METHOD,
            R.string.debugger_bp_type_method_title,
            R.string.debugger_bp_type_method_desc,
            R.drawable.ic_bp_method,
            true, false, false, false, false,
            false, false, true, false, false, false);

    public static final Entry ENTRY_CONDITION = new Entry(
            Category.GUTTER, EntryPoint.GUTTER_BP_RIGHT_CLICK, IdeBreakpoint.Kind.LINE,
            R.string.debugger_bp_type_condition_title,
            R.string.debugger_bp_type_condition_desc,
            R.drawable.ic_bp_condition,
            false, true, false, true, false,
            false, false, false, false, false, false);

    public static final Entry ENTRY_LOG = new Entry(
            Category.GUTTER, EntryPoint.GUTTER_BP_RIGHT_CLICK_LOG, IdeBreakpoint.Kind.LINE,
            R.string.debugger_bp_type_log_title,
            R.string.debugger_bp_type_log_desc,
            R.drawable.ic_bp_log,
            false, false, true, false, false,
            false, false, false, false, false, false);

    public static final Entry ENTRY_INLINE = new Entry(
            Category.GUTTER, EntryPoint.GUTTER_INLINE_CLICK, IdeBreakpoint.Kind.LINE,
            R.string.debugger_bp_type_inline_title,
            R.string.debugger_bp_type_inline_desc,
            R.drawable.ic_bp_inline,
            true, false, false, true, true,
            false, false, false, false, false, false);

    public static final Entry ENTRY_WATCH_MOD = new Entry(
            Category.VARIABLES, EntryPoint.VAR_FIELD_RIGHT_CLICK_MOD,
            IdeBreakpoint.Kind.FIELD_WATCHPOINT,
            R.string.debugger_bp_type_watch_mod_title,
            R.string.debugger_bp_type_watch_mod_desc,
            R.drawable.ic_bp_watchpoint,
            true, false, false, false, false,
            true, false, false, false, false, false);

    public static final Entry ENTRY_WATCH_ACC = new Entry(
            Category.VARIABLES, EntryPoint.VAR_FIELD_RIGHT_CLICK_ACC,
            IdeBreakpoint.Kind.FIELD_WATCHPOINT,
            R.string.debugger_bp_type_watch_acc_title,
            R.string.debugger_bp_type_watch_acc_desc,
            R.drawable.ic_bp_watchpoint,
            true, false, false, false, false,
            false, true, false, false, false, false);

    public static final Entry ENTRY_INSTANCE_FILTER = new Entry(
            Category.VARIABLES, EntryPoint.VAR_INSTANCE_RIGHT_CLICK,
            IdeBreakpoint.Kind.LINE,
            R.string.debugger_bp_type_instance_title,
            R.string.debugger_bp_type_instance_desc,
            R.drawable.ic_bp_instance,
            true, true, false, false, false,
            false, false, false, false, false, false);

    public static final Entry ENTRY_EXCEPTION = new Entry(
            Category.WINDOW, EntryPoint.WINDOW_EXCEPTION_ADD, IdeBreakpoint.Kind.EXCEPTION,
            R.string.debugger_bp_type_exception_title,
            R.string.debugger_bp_type_exception_desc,
            R.drawable.ic_bp_exception,
            true, false, false, false, false,
            false, false, false, false, true, true);

    public static final Entry ENTRY_SYMBOLIC = new Entry(
            Category.WINDOW, EntryPoint.WINDOW_SYMBOLIC_ADD, IdeBreakpoint.Kind.METHOD,
            R.string.debugger_bp_type_symbolic_title,
            R.string.debugger_bp_type_symbolic_desc,
            R.drawable.ic_bp_symbolic,
            true, false, false, false, false,
            false, false, true, false, false, false);

    public static final Entry ENTRY_DEPENDENT = new Entry(
            Category.WINDOW, EntryPoint.WINDOW_DEPENDENT_TOGGLE, IdeBreakpoint.Kind.LINE,
            R.string.debugger_bp_type_dependent_title,
            R.string.debugger_bp_type_dependent_desc,
            R.drawable.ic_bp_dependent,
            true, false, false, false, false,
            false, false, false, false, false, false);

    public static final Entry ENTRY_DOM = new Entry(
            Category.BROWSER, EntryPoint.BROWSER_DOM_ADD, IdeBreakpoint.Kind.LINE,
            R.string.debugger_bp_type_dom_title,
            R.string.debugger_bp_type_dom_desc,
            R.drawable.ic_bp_dom,
            true, false, false, false, true,
            false, false, false, false, false, false);

    public static final Entry ENTRY_XHR = new Entry(
            Category.BROWSER, EntryPoint.BROWSER_XHR_ADD, IdeBreakpoint.Kind.LINE,
            R.string.debugger_bp_type_xhr_title,
            R.string.debugger_bp_type_xhr_desc,
            R.drawable.ic_bp_xhr,
            true, false, false, false, true,
            false, false, false, false, false, false);

    public static final Entry ENTRY_EVENT = new Entry(
            Category.BROWSER, EntryPoint.BROWSER_EVENT_ADD, IdeBreakpoint.Kind.LINE,
            R.string.debugger_bp_type_event_title,
            R.string.debugger_bp_type_event_desc,
            R.drawable.ic_bp_event,
            true, false, false, false, true,
            false, false, false, false, false, false);

    @NonNull
    public static Entry[] all() {
        return new Entry[] {
                ENTRY_LINE, ENTRY_METHOD, ENTRY_CONDITION, ENTRY_LOG, ENTRY_INLINE,
                ENTRY_WATCH_MOD, ENTRY_WATCH_ACC, ENTRY_INSTANCE_FILTER,
                ENTRY_EXCEPTION, ENTRY_SYMBOLIC, ENTRY_DEPENDENT,
                ENTRY_DOM, ENTRY_XHR, ENTRY_EVENT
        };
    }

    @NonNull
    public static Entry[] forCategory(@NonNull Category cat) {
        java.util.List<Entry> out = new java.util.ArrayList<>();
        for (Entry e : all()) if (e.category == cat) out.add(e);
        return out.toArray(new Entry[0]);
    }

    @NonNull
    public static Category[] categories() {
        return new Category[] {
                Category.GUTTER, Category.VARIABLES, Category.WINDOW, Category.BROWSER
        };
    }

    @Nullable
    public static Entry fromEntryPoint(@NonNull EntryPoint ep) {
        for (Entry e : all()) if (e.entryPoint == ep) return e;
        return null;
    }
}
