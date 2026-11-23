package android.zero.studio.layouteditor.editor.callers.text;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.widget.AutoCompleteTextView;

import android.zero.studio.layouteditor.ProjectFile;
import android.zero.studio.layouteditor.managers.ProjectManager;
import android.zero.studio.layouteditor.managers.ValuesManager;
import android.zero.studio.layouteditor.tools.ValuesResourceParser;
import android.zero.studio.layouteditor.utils.DimensionUtil;

public class AutoCompleteTextViewCaller extends EditTextCaller {
    public static void setCompletionHint(View target, String value, Context context) {
        if (value.startsWith("@string/")) {
            ProjectFile project = ProjectManager.getInstance().getOpenedProject();
            value =
                ValuesManager.getValueFromResources(
                    ValuesResourceParser.TAG_STRING, value, project.getStringsPath());
        }
        ((AutoCompleteTextView) target).setCompletionHint(value);
    }

    public static void setThreshold(View target, String value, Context context) {
        ((AutoCompleteTextView) target).setThreshold((int) DimensionUtil.parse(value, context));
    }

    public static void setDropDownHeight(View target, String value, Context context) {
        ((AutoCompleteTextView) target).setDropDownHeight((int) DimensionUtil.parse(value, context));
    }

    public static void setDropDownHorizontalOffset(View target, String value, Context context) {
        ((AutoCompleteTextView) target)
            .setDropDownHorizontalOffset((int) DimensionUtil.parse(value, context));
    }

    public static void setDropDownVerticalOffset(View target, String value, Context context) {
        ((AutoCompleteTextView) target)
            .setDropDownVerticalOffset((int) DimensionUtil.parse(value, context));
    }

    public static void setDropDownWidth(View target, String value, Context context) {
        ((AutoCompleteTextView) target).setDropDownWidth((int) DimensionUtil.parse(value, context));
    }

    public static void setDropDownBackgroundResource(View target, String value, Context context) {
        ((AutoCompleteTextView) target).setDropDownBackgroundDrawable(new ColorDrawable(Color.parseColor(value)));
    }
}
