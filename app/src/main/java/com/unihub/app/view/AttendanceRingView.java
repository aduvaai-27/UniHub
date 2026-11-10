package com.unihub.app.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import com.unihub.app.data.AttendanceLevel;

/** Custom View: circular progress ring with the percentage inside. Stroke and text scale with the view size. */
public class AttendanceRingView extends View {
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private float shown = 0f;

    public AttendanceRingView(Context c, AttributeSet a) {
        super(c, a);
        track.setStyle(Paint.Style.STROKE); track.setColor(c.getColor(com.unihub.app.R.color.track));
        arc.setStyle(Paint.Style.STROKE); arc.setStrokeCap(Paint.Cap.BUTT);
        text.setColor(c.getColor(com.unihub.app.R.color.ink)); text.setTextAlign(Paint.Align.CENTER); text.setFakeBoldText(true);
    }

    /** Shows the percentage straight away (no fill animation, so the ring never re-fills when the list is scrolled or the tab is reopened). */
    public void setPercent(float percent) {
        shown = percent;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight(), box = Math.min(w, h);
        float stroke = box * 0.12f;                                  // ring thickness follows the size
        track.setStrokeWidth(stroke); arc.setStrokeWidth(stroke);
        text.setTextSize(box * 0.24f);
        float size = box - stroke * 2;
        rect.set((w - size) / 2, (h - size) / 2, (w + size) / 2, (h + size) / 2);
        canvas.drawArc(rect, 0, 360, false, track);
        arc.setColor(AttendanceLevel.color(Math.round(shown)));
        canvas.drawArc(rect, -90, 360f * shown / 100f, false, arc);
        canvas.drawText(Math.round(shown) + "%", w / 2, h / 2 + text.getTextSize() / 3, text);
    }
}
