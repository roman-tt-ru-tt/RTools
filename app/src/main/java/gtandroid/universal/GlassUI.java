package gtandroid.universal;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;

/* loaded from: classes2.dex */
public final class GlassUI {
    private static final float DEFAULT_RADIUS_DP = 12.0f;

    private GlassUI() {
    }

    private static int dp(Context ctx, float dp) {
        return (int) TypedValue.applyDimension(1, dp, ctx.getResources().getDisplayMetrics());
    }

    public static GradientDrawable glass(Context ctx, int baseColor) {
        return glass(ctx, baseColor, DEFAULT_RADIUS_DP, 179);
    }

    public static GradientDrawable glass(Context ctx, int baseColor, float radiusDp) {
        return glass(ctx, baseColor, radiusDp, 179);
    }

    public static GradientDrawable glass(Context ctx, int baseColor, float radiusDp, int fillAlpha) {
        int r = (baseColor >> 16) & 255;
        int g = (baseColor >> 8) & 255;
        int b = baseColor & 255;
        int fill = (fillAlpha << 24) | (((int) ((r * 0.82d) + 45.9d)) << 16) | (((int) ((g * 0.82d) + 45.9d)) << 8) | ((int) ((b * 0.82d) + 45.9d));
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(0);
        gd.setColor(fill);
        gd.setCornerRadius(dp(ctx, radiusDp));
        gd.setStroke(Math.max(1, dp(ctx, 1.0f)), 872415231);
        return gd;
    }

    public static GradientDrawable glassPressed(Context ctx, int baseColor) {
        return glassPressed(ctx, baseColor, DEFAULT_RADIUS_DP);
    }

    public static GradientDrawable glassPressed(Context ctx, int baseColor, float radiusDp) {
        return glass(ctx, baseColor, radiusDp, 230);
    }

    public static StateListDrawable glassSelector(Context ctx, int baseColor) {
        return glassSelector(ctx, baseColor, DEFAULT_RADIUS_DP);
    }

    public static StateListDrawable glassSelector(Context ctx, int baseColor, float radiusDp) {
        StateListDrawable sld = new StateListDrawable();
        sld.addState(new int[]{android.R.attr.state_pressed}, glassPressed(ctx, baseColor, radiusDp));
        sld.addState(new int[0], glass(ctx, baseColor, radiusDp));
        return sld;
    }

    public static GradientDrawable panel(Context ctx) {
        return glass(ctx, -14935010, 14.0f, 204);
    }

    public static GradientDrawable panelTop(Context ctx) {
        GradientDrawable gd = glass(ctx, -14935010, 20.0f, 224);
        float radius = dp(ctx, 20.0f);
        gd.setCornerRadii(new float[]{radius, radius, radius, radius, 0.0f, 0.0f, 0.0f, 0.0f});
        return gd;
    }

    public static GradientDrawable lightCard(Context ctx) {
        return lightCard(ctx, 16.0f);
    }

    public static GradientDrawable lightCard(Context ctx, float radiusDp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(0);
        gd.setColor(-419430401);
        gd.setCornerRadius(dp(ctx, radiusDp));
        gd.setStroke(Math.max(1, dp(ctx, 1.0f)), 335544320);
        return gd;
    }

    public static StateListDrawable lightAccentButton(Context ctx, int accentColor) {
        StateListDrawable sld = new StateListDrawable();
        GradientDrawable pressed = new GradientDrawable();
        pressed.setShape(0);
        pressed.setColor(blendWithWhite(accentColor, 0.55f, 51));
        pressed.setCornerRadius(dp(ctx, 16.0f));
        pressed.setStroke(Math.max(1, dp(ctx, 1.0f)), 570425344);
        GradientDrawable normal = new GradientDrawable();
        normal.setShape(0);
        normal.setColor(blendWithWhite(accentColor, 0.85f, 34));
        normal.setCornerRadius(dp(ctx, 16.0f));
        normal.setStroke(Math.max(1, dp(ctx, 1.0f)), 335544320);
        sld.addState(new int[]{android.R.attr.state_pressed}, pressed);
        sld.addState(new int[0], normal);
        return sld;
    }

    private static int blendWithWhite(int color, float whiteFraction, int alpha) {
        int r = (color >> 16) & 255;
        int g = (color >> 8) & 255;
        int b = color & 255;
        return (alpha << 24) | (((int) ((r * (1.0f - whiteFraction)) + (whiteFraction * 255.0f))) << 16) | (((int) ((g * (1.0f - whiteFraction)) + (whiteFraction * 255.0f))) << 8) | ((int) ((b * (1.0f - whiteFraction)) + (255.0f * whiteFraction)));
    }

    public static GradientDrawable glassOval(Context ctx, int baseColor) {
        int r = (baseColor >> 16) & 255;
        int g = (baseColor >> 8) & 255;
        int b = baseColor & 255;
        int fill = (-436207616) | (((int) ((r * 0.82d) + 45.9d)) << 16) | (((int) ((g * 0.82d) + 45.9d)) << 8) | ((int) ((b * 0.82d) + 45.9d));
        GradientDrawable gd = new GradientDrawable();
        gd.setShape(1);
        gd.setColor(fill);
        gd.setStroke(dp(ctx, 2.0f), 1509949439);
        return gd;
    }
}
