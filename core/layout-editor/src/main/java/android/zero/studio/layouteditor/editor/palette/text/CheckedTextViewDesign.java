package android.zero.studio.layouteditor.editor.palette.text;

import android.content.Context;
import android.graphics.Canvas;
import androidx.appcompat.widget.AppCompatCheckedTextView;

import android.zero.studio.layouteditor.utils.Constants;
import android.zero.studio.layouteditor.utils.Utils;

public class CheckedTextViewDesign extends AppCompatCheckedTextView {

    private boolean drawStrokeEnabled;
    private boolean isBlueprint;

    public CheckedTextViewDesign(Context context) {
        super(context);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);

        if (drawStrokeEnabled)
            Utils.drawDashPathStroke(
                this, canvas, isBlueprint ? Constants.BLUEPRINT_DASH_COLOR : Constants.DESIGN_DASH_COLOR);
    }

    public void setStrokeEnabled(boolean enabled) {
        drawStrokeEnabled = enabled;
        invalidate();
    }

    @Override
    public void draw(Canvas canvas) {
        if (isBlueprint) Utils.drawDashPathStroke(this, canvas, Constants.BLUEPRINT_DASH_COLOR);
        else super.draw(canvas);
    }

    public void setBlueprint(boolean isBlueprint) {
        this.isBlueprint = isBlueprint;
        invalidate();
    }
}
