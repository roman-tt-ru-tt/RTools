package gtandroid.universal;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MarkPickerActivity extends Activity {

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

        // Сворачиваем приложение — покажем оверлей
        moveTaskToBack(true);

        // Через 500мс показываем оверлей поверх экрана
        new android.os.Handler().postDelayed(this::showOverlay, 500L);
    }

    private void showOverlay() {
        try {
            final WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);

            LinearLayout overlay = new LinearLayout(this);
            overlay.setOrientation(LinearLayout.VERTICAL);
            overlay.setBackgroundColor(0x88000000); // полупрозрачный чёрный
            overlay.setGravity(Gravity.CENTER);

            TextView hint = new TextView(this);
            hint.setText("👆 Нажмите в место, где должен открываться чат\n" +
                    "(где находится поле ввода в игре)\n\n" +
                    "Нажмите «Отмена» для выхода");
            hint.setTextColor(Color.WHITE);
            hint.setTextSize(18f);
            hint.setGravity(Gravity.CENTER);
            hint.setPadding(40, 40, 40, 40);
            overlay.addView(hint);

            Button cancelBtn = new Button(this);
            cancelBtn.setText("Отмена");
            cancelBtn.setAllCaps(false);
            cancelBtn.setTextColor(Color.WHITE);
            cancelBtn.setBackground(GlassUI.lightAccentButton(this, 0xFFDC2626));
            cancelBtn.setOnClickListener(v -> {
                try { wm.removeView(overlay); } catch (Exception ignored) { }
                finish();
            });
            LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(-2, -2);
            cancelLp.topMargin = 20;
            overlay.addView(cancelBtn, cancelLp);

            int flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;

            final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    Build.VERSION.SDK_INT >= 26
                            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                            : WindowManager.LayoutParams.TYPE_PHONE,
                    flags,
                    PixelFormat.TRANSLUCENT);

            overlay.setOnTouchListener((v, event) -> {
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    float x = event.getRawX();
                    float y = event.getRawY();

                    // Сохраняем метку
                    MyAccessibilityService.setAutoOpenChatMark(x, y);

                    Toast.makeText(MarkPickerActivity.this,
                            "✅ Метка установлена: " + (int) x + ", " + (int) y,
                            Toast.LENGTH_LONG).show();

                    try { wm.removeView(overlay); } catch (Exception ignored) { }
                    finish();
                    return true;
                }
                return true;
            });

            wm.addView(overlay, params);

        } catch (Exception e) {
            Toast.makeText(this,
                    "Ошибка: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
            finish();
        }
    }
        }
