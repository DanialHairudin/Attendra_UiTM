package com.attendra.uitm.map;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;

import androidx.core.content.ContextCompat;

import com.attendra.uitm.R;

/** Small round dots used as markers, coloured with the app's status colours. */
public final class MapIcons {
    private MapIcons() { }

    public static Drawable dot(Context ctx, int colorRes) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        int size = dp(ctx, 18);
        g.setSize(size, size);
        g.setColor(ContextCompat.getColor(ctx, colorRes));
        g.setStroke(dp(ctx, 3), Color.WHITE);   // White ring so the dot is visible on any street colour
        return g;
    }

    /** Blue "you are here" dot, as a Bitmap for MyLocationNewOverlay. */
    public static Bitmap myLocationDot(Context ctx) {
        Drawable d = dot(ctx, R.color.my_location);
        Bitmap b = Bitmap.createBitmap(d.getIntrinsicWidth(), d.getIntrinsicHeight(),
                Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        d.setBounds(0, 0, c.getWidth(), c.getHeight());
        d.draw(c);
        return b;
    }

    public static int statusColor(String status) {
        if ("present".equals(status)) return R.color.present;
        if ("late".equals(status)) return R.color.late;
        return R.color.rejected;
    }

    static int dp(Context ctx, int v) {
        return Math.round(v * ctx.getResources().getDisplayMetrics().density);
    }
}
