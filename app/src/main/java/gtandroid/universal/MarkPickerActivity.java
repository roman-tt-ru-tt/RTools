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

    private FrameLayout rootLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Проверяем overlay permission
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

        // Сворачиваем приложение — покажем оверлей поверх игры
        moveTaskToBack(true);

        // Через 500мс показываем счётчик
        handler.postDelayed(this::showCountdown, 500L);
    }

    // ==================== ШАГ 1: 10-СЕКУНДНЫЙ ОТСЧЁТ ====================

    private void showCountdown() {
        try {
            wm = (WindowManager) getSystemService(WINDOW_SERVICE);

            LinearLayout layout = new LinearLayout(this);
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setBackgroundColor(0xCC000000); // полупрозрачный чёрный
            layout.setGravity(Gravity.CENTER);

            TextView timerText = new TextView(this);
            timerText.setText("10");
            timerText.setTextColor(Color.WHITE);
            timerText.setTextSize(120f);
            timerText.setTypeface(null, android.graphics.Typeface.BOLD);
            timerText.setGravity(Gravity.CENTER);
            layout.addView(timerText);

            TextView hint = new TextView(this);
            hint.setText("Успей открыть игру и выбери место\nдля установки метки чата");
            hint.setTextColor(0xFFA78BFA);
            hint.setTextSize(18f);
            hint.setGravity(Gravity.CENTER);
            hint.setPadding(0, 30, 0, 0);
            layout.addView(hint);

            Button cancel = new Button(this);
            cancel.setText("ОТМЕНА");
            cancel.setAllCaps(false);
            cancel.setTextColor(Color.WHITE);
            cancel.setBackground(GlassUI.lightAccentButton(this, 0xFFDC2626));
            cancel.setOnClickListener(v -> closeOverlay());
            LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(-2, -2);
            cancelLp.topMargin = 60;
            layout.addView(cancel, cancelLp);

            final WindowManager.LayoutParams params = makeParams(true);
            rootLayout = new FrameLayout(this); // для будущей замены
            overlayView = layout;

            wm.addView(overlayView, params);

            // Запускаем счётчик
            final int[] seconds = {10};
            Runnable tick = new Runnable() {
                @Override public void run() {
                    seconds[0]--;
                    if (seconds[0] > 0) {
                        timerText.setText(String.valueOf(seconds[0]));
                        // Меняем цвет ближе к концу
                        if (seconds[0] <= 3) {
                            timerText.setTextColor(0xFFFF453A);
                        } else if (seconds[0] <= 6) {
                            timerText.setTextColor(0xFFFFD84D);
                        }
                        handler.postDelayed(this, 1000L);
                    } else {
                        timerText.setText("0");
                        // Показываем экран выбора
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

    // ==================== ШАГ 2: ЭКРАН ВЫБОРА ====================

    private void showPicker() {
        try {
            // Убираем счётчик
            if (overlayView != null) {
                try { wm.removeView(overlayView); } catch (Exception ignored) { }
            }

            FrameLayout layout = new FrameLayout(this);
            layout.setBackgroundColor(0x66000000); // лёгкий затемняющий фон, тапы проходят

            // Подсказка сверху
            LinearLayout topBar = new LinearLayout(this);
            topBar.setOrientation(LinearLayout.VERTICAL);
            topBar.setBackgroundColor(0xCC1A0B2E);
            topBar.setPadding(30, 30, 30, 30);
            topBar.setGravity(Gravity.CENTER);

            TextView hint = new TextView(this);
            hint.setText("👆 Нажмите в место, где должен открываться чат\n" +
                    "(на поле ввода в игре)");
            hint.setTextColor(Color.WHITE);
            hint.setTextSize(16f);
            hint.setGravity(Gravity.CENTER);
            topBar.addView(hint);

            TextView coords = new TextView(this);
            coords.setText("Координаты: не выбраны");
            coords.setTextColor(0xFFA78BFA);
            coords.setTextSize(14f);
            coords.setGravity(Gravity.CENTER);
            coords.setPadding(0, 15, 0, 0);
            topBar.addView(coords);
            coordsText = coords;

            // Кнопка "Отмена" снизу
            LinearLayout bottomBar = new LinearLayout(this);
            bottomBar.setOrientation(LinearLayout.HORIZONTAL);
            bottomBar.setBackgroundColor(0xCC1A0B2E);
            bottomBar.setPadding(20, 20, 20, 20);
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
            confirm.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
            confirm.setEnabled(false);
            confirm.setAlpha(0.5f);
            LinearLayout.LayoutParams confirmLp = (LinearLayout.LayoutParams) confirm.getLayoutParams();
            confirmLp.leftMargin = dpToPx(10);
            confirm.setOnClickListener(v -> confirmAndSave());
            bottomBar.addView(confirm);
            confirmBtn = confirm;

            // Круглая метка, которая будет двигаться
            TextView circle = new TextView(this);
            circle.setText("✕");
            circle.setTextColor(Color.WHITE);
            circle.setTextSize(24f);
            circle.setGravity(Gravity.CENTER);
            circle.setLayoutParams(new FrameLayout.LayoutParams(dpToPx(60), dpToPx(60)));
            GradientDrawable circleBg = new GradientDrawable();
            circleBg.setShape(GradientDrawable.OVAL);
            circleBg.setColor(0xCC7C3AED);
            circleBg.setStroke(dpToPx(3), 0xFFA78BFA);
            circle.setBackground(circleBg);
            circle.setVisibility(View.GONE);
            layout.addView(circle);
            markCircle = circle;

            // Верхняя панель — прикрепляем вверху
            FrameLayout.LayoutParams topLp = new FrameLayout.LayoutParams(-1, -2);
            topLp.gravity = Gravity.TOP;
            layout.addView(topBar, topLp);

            // Нижняя панель — прикрепляем внизу
            FrameLayout.LayoutParams bottomLp = new FrameLayout.LayoutParams(-1, -2);
            bottomLp.gravity = Gravity.BOTTOM;
            layout.addView(bottomBar, bottomLp);

            // Обработка тапа по свободному месту
            layout.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (event.getAction() == MotionEvent.ACTION_UP) {
                        float x = event.getRawX();
                        float y = event.getRawY();

                        // Игнорируем тапы по панелям
                        if (y < dpToPx(120)) return true;
                        int screenH = getResources().getDisplayMetrics().heightPixels;
                        if (y > screenH - dpToPx(120)) return true;

                        selectedX = x;
                        selectedY = y;
                        showMarkAt(x, y);
                        return true;
                    }
                    return true;
                }
            });

            final WindowManager.LayoutParams params = makeParams(false);
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

        // Смещаем на половину размера круга, чтобы центр был в точке тапа
        int size = dpToPx(60);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) markCircle.getLayoutParams();
        lp.leftMargin = (int) (x - size / 2f);
        lp.topMargin = (int) (y - size / 2f);
        markCircle.setLayoutParams(lp);
        markCircle.setVisibility(View.VISIBLE);

        // Обновляем текст координат
        if (coordsText != null) {
            coordsText.setText("✅ Координаты: X=" + (int) x + ", Y=" + (int) y);
            coordsText.setTextColor(0xFF00E5C0);
        }

        // Активируем кнопку "Подтвердить"
        if (confirmBtn != null) {
            confirmBtn.setEnabled(true);
            confirmBtn.setAlpha(1.0f);
        }

        Toast.makeText(this,
                "📍 Точка: X=" + (int) x + ", Y=" + (int) y
                        + "\nНажмите «✅ Подтвердить»",
                Toast.LENGTH_SHORT).show();
    }

    // ==================== ШАГ 3: ПОДТВЕРЖДЕНИЕ ====================

    private void confirmAndSave() {
        if (selectedX < 0 || selectedY < 0) {
            Toast.makeText(this, "❌ Сначала нажмите на место",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        // Сохраняем метку
        MyAccessibilityService.setAutoOpenChatMark(selectedX, selectedY);
        // Включаем авто-чат
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

    // ==================== ВСПОМОГАТЕЛЬНОЕ ====================

    private WindowManager.LayoutParams makeParams(boolean focusable) {
        int flags = WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
        if (!focusable) {
            flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        }
        return new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                Build.VERSION.SDK_INT >= 26
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                flags,
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
