package com.unihub.app.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewOutlineProvider;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatImageView;
import com.unihub.app.R;

/** Round profile picture. Shows the saved picture, or the default picture (drawable/default_avatar) when there is none. */
public class AvatarView extends AppCompatImageView {
    public AvatarView(Context c) { this(c, null); }
    public AvatarView(Context c, @Nullable AttributeSet a) { this(c, a, 0); }

    public AvatarView(Context c, @Nullable AttributeSet a, int style) {
        super(c, a, style);
        setScaleType(ScaleType.CENTER_CROP);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) { o.setOval(0, 0, v.getWidth(), v.getHeight()); }
        });
        setClipToOutline(true);
        setRing(c.getColor(R.color.border));
        setImageResource(R.drawable.default_avatar);
    }

    /** Thin ring around the picture. */
    public void setRing(int color) {
        GradientDrawable ring = new GradientDrawable();
        ring.setShape(GradientDrawable.OVAL);
        ring.setStroke(Math.max(1, Math.round(1.5f * getResources().getDisplayMetrics().density)), color);
        ring.setColor(0x00000000);
        setForeground(ring);
    }

    /** null -> the default picture. */
    public void show(@Nullable Bitmap photo) {
        if (photo != null) setImageBitmap(photo);
        else setImageResource(R.drawable.default_avatar);
    }
}
