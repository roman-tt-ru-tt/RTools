package gtandroid.universal;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class StatisticsActivity extends Activity {

    private static final String PREFS_STATS = "GTAndroidStats";
    private static final String KEY_WEEKLY_PREFIX = "weekly_";
    private static final String KEY_TOTAL_REPORTS = "total_reports_all_time";
    private static final String KEY_LAST_RESET_DATE = "last_reset_date";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF1A0B2E);
        root.setPadding(30, 40, 30, 30);

        TextView title = new TextView(this);
        title.setText("📊 Статистика за неделю");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(24f);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(0, 0, 0, 30);
        root.addView(title);

        SharedPreferences sp = getSharedPreferences(PREFS_STATS, MODE_PRIVATE);
        String[] days = {"Понедельник", "Вторник", "Среда", "Четверг",
                "Пятница", "Суббота", "Воскресенье"};

        int totalWeek = 0;
        for (int i = 0; i < 7; i++) {
            int count = sp.getInt(KEY_WEEKLY_PREFIX + i, 0);
            totalWeek += count;

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setBackground(GlassUI.glassSelector(this, 0xFF2D1B4E));
            row.setPadding(20, 15, 20, 15);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
            rowLp.bottomMargin = 8;

            TextView dayText = new TextView(this);
            dayText.setText(days[i]);
            dayText.setTextColor(0xFFFFFFFF);
            dayText.setTextSize(16f);
            dayText.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
            row.addView(dayText);

            TextView countText = new TextView(this);
            countText.setText(String.valueOf(count));
            countText.setTextColor(0xFF00E5C0);
            countText.setTextSize(18f);
            countText.setTypeface(null, android.graphics.Typeface.BOLD);
            row.addView(countText);

            root.addView(row, rowLp);
        }

        TextView total = new TextView(this);
        total.setText("Итог за неделю: " + totalWeek);
        total.setTextColor(0xFFFFD84D);
        total.setTextSize(20f);
        total.setTypeface(null, android.graphics.Typeface.BOLD);
        total.setGravity(Gravity.CENTER);
        total.setPadding(0, 30, 0, 10);
        root.addView(total);

        int all = sp.getInt(KEY_TOTAL_REPORTS, 0);
        TextView allTime = new TextView(this);
        allTime.setText("За всё время: " + all);
        allTime.setTextColor(0xFFA78BFA);
        allTime.setTextSize(16f);
        allTime.setGravity(Gravity.CENTER);
        allTime.setPadding(0, 0, 0, 30);
        root.addView(allTime);

        Button resetWeek = new Button(this);
        resetWeek.setText("🔄 Сбросить неделю");
        resetWeek.setAllCaps(false);
        resetWeek.setTextColor(0xFFFFFFFF);
        resetWeek.setBackground(GlassUI.lightAccentButton(this, 0xFFDC2626));
        resetWeek.setOnClickListener(v -> {
            SharedPreferences.Editor e = sp.edit();
            for (int i = 0; i < 7; i++) e.putInt(KEY_WEEKLY_PREFIX + i, 0);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            e.putString(KEY_LAST_RESET_DATE, sdf.format(new Date())).apply();
            recreate();
        });
        root.addView(resetWeek);

        Button closeBtn = new Button(this);
        closeBtn.setText("← Назад");
        closeBtn.setAllCaps(false);
        closeBtn.setTextColor(0xFFFFFFFF);
        closeBtn.setBackground(GlassUI.lightAccentButton(this, 0xFF7C3AED));
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(-1, -2);
        closeLp.topMargin = 20;
        closeBtn.setOnClickListener(v -> finish());
        root.addView(closeBtn, closeLp);

        setContentView(root);
    }
              }
