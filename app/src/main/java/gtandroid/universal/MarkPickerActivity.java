package gtandroid.universal;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MarkPickerActivity extends Activity {

    private Handler handler = new Handler();
    private WindowManager wm;
    private View overlayView;

    private float selectedX = -1f;
    private float selectedY = -1f;

    private TextView coordsText;
    private TextView markCircle;
    private Button confirmBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this,
                    "Разрешите показ поверх других приложений",
                    Toast.LENGTH_LONG).show();
            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
            finish();
            return;
        }

        moveTaskToBack(true);
        handler.postDelayed(this::showCountdown, 300L);
    }

    private void showCountdown() {
        try {
            wm = (WindowManager) getSystemService(WINDOW_SERVICE);

            LinearLayout layout = new LinearLayout(this);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setGravity(Gravity.CENTER);
            layout.setPadding(dpToPx(20), dpToPx(20), dpToPx(20), dpToPx(20));

            GradientDrawable bg = new GradientDrawable();
            bg.setShape(GradientDrawable.OVAL);
            bg.setColor(0xDD1A0B2E);
            bg.setStroke(dpToPx(3), 0xFF7C3AED);
            layout.setBackground(bg);

            final TextView timerText = new TextView(this);
            timerText.setText("10");
            timerText.setTextColor(Color.WHITE);
            timerText.setTextSize(64f);
            timerText.setTypeface(null, android.graphics.Typeface.BOLD);
            timerText.setGravity(Gravity.CENTER);
            layout.addView(timerText);

            TextView caption = new TextView(this);
            caption.setText("сек");
            caption.setTextColor(0xFFA78BFA);
            caption.setTextSize(12f);
            caption.setGravity(Gravity.CENTER);
            layout.addView(caption);

            int size = dpToPx(120);
            WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    size,
                    size,
                    Build.VERSION.SDK_INT >= 26
                            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                            : WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            params.gravity = Gravity.TOP | Gravity.END;
            params.x = dpToPx(20);
            params.y = dpToPx(80);

            overlayView = layout;
            wm.addView(overlayView, params);

            final int[] seconds = {10};
            Runnable tick = new Runnable() {
                @Override public void run() {
                    seconds[0]--;
                    if (seconds[0] > 0) {
                        timerText.setText(String.valueOf(seconds[0]));
                        if (seconds[0] <= 3) {
                            timerText.setTextColor(0xFFFF453A);
                        } else if (seconds[0] <= 6) {
                            timerText.setTextColor(0xFFFFD84D);
                        }
                        handler.postDelayed(this, 1000L);
                    } else {
                        try {
                            if (overlayView != null) wm.removeView(overlayView);
                        } catch (Exception ignored) { }
                        overlayView = null;
                        showPicker();
                    }
                }
            };
            handler.postDelayed(tick, 1000L);

        } catch (Exception e) {
            Toast.makeText(this,
                    "Ошибка: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void showPicker() {
        try {
            FrameLayout layout = new FrameLayout(this);
            layout.setBackgroundColor(0x22000000);

            LinearLayout topBar = new LinearLayout(this);
            topBar.setOrientation(LinearLayout.VERTICAL);
            topBar.setBackgroundColor(0xCC1A0B2E);
            topBar.setPadding(dpToPx(12), dpToPx(8), dpToPx(12), dpToPx(8));
            topBar.setGravity(Gravity.CENTER);

            TextView hint = new TextView(this);
            hint.setText("👆 Нажмите на поле ввода в игре");
            hint.setTextColor(Color.WHITE);
            hint.setTextSize(13f);
            hint.setGravity(Gravity.CENTER);
            topBar.addView(hint);

            final TextView coords = new TextView(this);
            coords.setText("Координаты: не выбраны");
            coords.setTextColor(0xFFA78BFA);
            coords.setTextSize(12f);
            coords.setGravity(Gravity.CENTER);
            topBar.addView(coords);
            coordsText = coords;

            LinearLayout bottomBar = new LinearLayout(this);
            bottomBar.setOrientation(LinearLayout.HORIZONTAL);
            bottomBar.setBackgroundColor(0xDD1A0B2E);
            bottomBar.setPadding(dpToPx(20), dpToPx(20), dpToPx(20), dpToPx(20));
            bottomBar.setGravity(Gravity.CENTER);

            Button cancel = new Button(this);
            cancel.setText("ОТМЕНА");
            cancel.setAllCaps(false);
            cancel.setTextColor(Color.WHITE);
            cancel.setBackground(GlassUI.lightAccentButton(this, 0xFFDC2626));
            cancel.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
            cancel.setOnClickListener(v -> closeOverlay());
            bottomBar.addView(cancel);

            Button confirm = new Button(this);
            confirm.setText("✅ ПОДТВЕРДИТЬ");
            confirm.setAllCaps(false);
            confirm.setTextColor(Color.WHITE);
            confirm.setBackground(GlassUI.lightAccentButton(this, 0xFF10B981));
            LinearLayout.LayoutParams confirmLp =
                    new LinearLayout.LayoutParams(0, -2, 1f);
            confirmLp.leftMargin = dpToPx(10);
            confirm.setLayoutParams(confirmLp);
            confirm.setEnabled(false);
            confirm.setAlpha(0.5f);
            confirm.setOnClickListener(v -> confirmAndSave());
            bottomBar.addView(confirm);
            confirmBtn = confirm;

            TextView circle = new TextView(this);
            circle.setText("✕");
            circle.setTextColor(Color.WHITE);
            circle.setTextSize(24f);
            circle.setGravity(Gravity.CENTER);
            FrameLayout.LayoutParams circleLp =
                    new FrameLayout.LayoutParams(dpToPx(60), dpToPx(60));
            circle.setLayoutParams(circleLp);
            GradientDrawable circleBg = new GradientDrawable();
            circleBg.setShape(GradientDrawable.OVAL);
            circleBg.setColor(0xCC7C3AED);
            circleBg.setStroke(dpToPx(3), 0xFFA78BFA);
            circle.setBackground(circleBg);
            circle.setVisibility(View.GONE);
            layout.addView(circle);
            markCircle = circle;

            FrameLayout.LayoutParams topLp =
                    new FrameLayout.LayoutParams(-1, -2);
            topLp.gravity = Gravity.TOP;
            topLp.topMargin = dpToPx(40);
            layout.addView(topBar, topLp);

            FrameLayout.LayoutParams bottomLp =
                    new FrameLayout.LayoutParams(-1, -2);
            bottomLp.gravity = Gravity.BOTTOM;
            layout.addView(bottomBar, bottomLp);

            layout.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (event.getAction() == MotionEvent.ACTION_UP) {
                        float x = event.getRawX();
                        float y = event.getRawY();

                        int screenH = getResources().getDisplayMetrics().heightPixels;
                        if (y > screenH - dpToPx(110)) return true;

                        selectedX = x;
                        selectedY = y;
                        showMarkAt(x, y);
                        return true;
                    }
                    return true;
                }
            });

            final WindowManager.LayoutParams params = makePickerParams();
            overlayView = layout;
            wm.addView(overlayView, params);

        } catch (Exception e) {
            Toast.makeText(this,
                    "Ошибка: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void showMarkAt(float x, float y) {
        if (markCircle == null) return;

        int size = dpToPx(60);
        FrameLayout.LayoutParams lp =
                (FrameLayout.LayoutParams) markCircle.getLayoutParams();
        lp.leftMargin = (int) (x - size / 2f);
        lp.topMargin = (int) (y - size / 2f);
        markCircle.setLayoutParams(lp);
        markCircle.setVisibility(View.VISIBLE);

        if (coordsText != null) {
            coordsText.setText("✅ Координаты: X=" + (int) x + ", Y=" + (int) y);
            coordsText.setTextColor(0xFF00E5C0);
        }

        if (confirmBtn != null) {
            confirmBtn.setEnabled(true);
            confirmBtn.setAlpha(1.0f);
        }

        Toast.makeText(this,
                "📍 Точка: X=" + (int) x + ", Y=" + (int) y
                        + "\nНажмите «✅ Подтвердить»",
                Toast.LENGTH_SHORT).show();
    }

    private void confirmAndSave() {
        if (selectedX < 0 || selectedY < 0) {
            Toast.makeText(this, "❌ Сначала нажмите на место",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        MyAccessibilityService.setAutoOpenChatMark(selectedX, selectedY);
        getSharedPreferences("GTAndroidData", MODE_PRIVATE)
                .edit()
                .putBoolean("auto_open_chat_enabled", true)
                .apply();

        Toast.makeText(this,
                "✅ Метка установлена!\nX=" + (int) selectedX
                        + ", Y=" + (int) selectedY
                        + "\nАвто-чат включён",
                Toast.LENGTH_LONG).show();

        closeOverlay();
    }

    private void closeOverlay() {
        try {
            if (overlayView != null && wm != null) {
                wm.removeView(overlayView);
            }
        } catch (Exception ignored) { }
        overlayView = null;
        finish();
    }

    private WindowManager.LayoutParams makePickerParams() {
        return new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                Build.VERSION.SDK_INT >= 26
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
    }

    private int dpToPx(float dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp,
                getResources().getDisplayMetrics());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (overlayView != null && wm != null) {
            try { wm.removeView(overlayView); } catch (Exception ignored) { }
        }
        handler.removeCallbacksAndMessages(null);
    }
                }
