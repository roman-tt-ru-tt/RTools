package gtandroid.universal;

import android.app.Activity;
import android.os.Bundle;
import android.os.Debug;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class PerfTestActivity extends Activity {

    private LinearLayout logContainer;
    private TextView summaryView;
    private StringBuilder log = new StringBuilder();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF1A0B2E);
        root.setPadding(20, 40, 20, 20);

        TextView title = new TextView(this);
        title.setText("🧪 Тест производительности");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(22f);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 20);
        root.addView(title);

        summaryView = new TextView(this);
        summaryView.setTextColor(0xFFFFD84D);
        summaryView.setTextSize(14f);
        summaryView.setPadding(10, 10, 10, 10);
        summaryView.setBackground(GlassUI.lightCard(this, 10f));
        root.addView(summaryView);

        Button runTest = new Button(this);
        runTest.setText("▶ Запустить все тесты");
        runTest.setAllCaps(false);
        runTest.setTextColor(0xFFFFFFFF);
        runTest.setBackground(GlassUI.lightAccentButton(this, 0xFF10B981));
        runTest.setOnClickListener(v -> runTests());
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(-1, -2);
        btnLp.topMargin = 15;
        root.addView(runTest, btnLp);

        Button clear = new Button(this);
        clear.setText("🗑 Очистить");
        clear.setAllCaps(false);
        clear.setTextColor(0xFFFFFFFF);
        clear.setBackground(GlassUI.lightAccentButton(this, 0xFFDC2626));
        clear.setOnClickListener(v -> { log.setLength(0); logContainer.removeAllViews(); });
        LinearLayout.LayoutParams clearLp = new LinearLayout.LayoutParams(-1, -2);
        clearLp.topMargin = 8;
        root.addView(clear, clearLp);

        ScrollView scroll = new ScrollView(this);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1f));
        scroll.setPadding(0, 15, 0, 0);

        logContainer = new LinearLayout(this);
        logContainer.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(logContainer);
        root.addView(scroll);

        setContentView(root);

        // Показываем память сразу
        showMemoryStats();
    }

    private void runTests() {
        log.setLength(0);
        logContainer.removeAllViews();
        long startAll = System.currentTimeMillis();

        testMemoryUsage();
        testGlassUICreation();
        testTextProcessing();
        testSharedPrefs();
        testMainThreadBlocking();

        long totalTime = System.currentTimeMillis() - startAll;
        addLine("━━━━━━━━━━━━━━━━━━━━━━", 0xFFFFD84D);
        addLine("⏱ Общее время: " + totalTime + " мс", 0xFFFFFFFF);
        showMemoryStats();
    }

    private void testMemoryUsage() {
        addHeader("1. ПАМЯТЬ");
        Runtime rt = Runtime.getRuntime();
        long total = rt.totalMemory() / (1024 * 1024);
        long free = rt.freeMemory() / (1024 * 1024);
        long used = total - free;
        long max = rt.maxMemory() / (1024 * 1024);
        addLine("Использовано: " + used + " МБ", 0xFFFFFFFF);
        addLine("Свободно: " + free + " МБ", 0xFFFFFFFF);
        addLine("Всего: " + total + " МБ / Макс: " + max + " МБ", 0xFFFFFFFF);

        // Heap info
        try {
            Debug.MemoryInfo memInfo = new Debug.MemoryInfo();
            Debug.getMemoryInfo(memInfo);
            addLine("Native Heap: " + memInfo.nativePss / 1024 + " МБ", 0xFFA78BFA);
            addLine("Dalvik Heap: " + memInfo.dalvikPss / 1024 + " МБ", 0xFFA78BFA);
        } catch (Exception ignored) { }

        long usage = (used * 100) / Math.max(1, max);
        if (usage > 80) {
            addLine("⚠️ ВЫСОКОЕ ПОТРЕБЛЕНИЕ (" + usage + "%)", 0xFFFF453A);
        } else if (usage > 60) {
            addLine("⚠ Среднее потребление (" + usage + "%)", 0xFFFFD84D);
        } else {
            addLine("✅ Норма (" + usage + "%)", 0xFF10B981);
        }
    }

    private void testGlassUICreation() {
        addHeader("2. СОЗДАНИЕ GlassUI (×1000)");
        long start = System.currentTimeMillis();
        for (int i = 0; i < 1000; i++) {
            GlassUI.glassSelector(this, 0xFF7C3AED);
        }
        long time = System.currentTimeMillis() - start;
        addLine("Время: " + time + " мс", time < 500 ? 0xFF10B981 : 0xFFFF453A);
        if (time > 500) {
            addLine("⚠ Медленно! Проверь кэш", 0xFFFF453A);
        } else {
            addLine("✅ Кэш работает", 0xFF10B981);
        }
    }

    private void testTextProcessing() {
        addHeader("3. ОБРАБОТКА ТЕКСТА (×10000)");
        String sample = "Adm_Helper[12]: помогите [К-во репорта:2]";
        long start = System.currentTimeMillis();
        int count = 0;
        for (int i = 0; i < 10000; i++) {
            if (sample.contains("К-во репорта:")) count++;
            if (sample.matches("^\\S+\\[\\d+\\]:\\s*.*")) count++;
        }
        long time = System.currentTimeMillis() - start;
        addLine("Время: " + time + " мс", time < 1000 ? 0xFF10B981 : 0xFFFFD84D);
        if (time > 2000) {
            addLine("⚠ Медленно (regex)", 0xFFFF453A);
        } else {
            addLine("✅ Норма", 0xFF10B981);
        }
    }

    private void testSharedPrefs() {
        addHeader("4. SHARED PREFERENCES (×500)");
        long start = System.currentTimeMillis();
        for (int i = 0; i < 500; i++) {
            getSharedPreferences("GTAndroidStats", 0)
                    .getInt("total_reports_all_time", 0);
        }
        long time = System.currentTimeMillis() - start;
        addLine("Чтение: " + time + " мс", time < 300 ? 0xFF10B981 : 0xFFFFD84D);
    }

    private void testMainThreadBlocking() {
        addHeader("5. БЛОКИРОВКА ГЛАВНОГО ПОТОКА");
        final long[] frameTime = new long[1];
        final Handler h = new Handler();
        final int[] frames = {0};
        final long start = System.currentTimeMillis();
        final Runnable[] run = new Runnable[1];

        run[0] = new Runnable() {
            @Override public void run() {
                if (frames[0] < 60) {
                    frames[0]++;
                    h.postDelayed(this, 16);
                } else {
                    long totalTime = System.currentTimeMillis() - start;
                    double fps = 60000.0 / totalTime;
                    addLine("FPS (за 60 кадров): " + String.format(Locale.US, "%.1f", fps),
                            fps > 50 ? 0xFF10B981 : 0xFFFFD84D);
                    if (fps < 30) {
                        addLine("⚠ Низкий FPS — главный поток тормозит", 0xFFFF453A);
                    } else {
                        addLine("✅ FPS в норме", 0xFF10B981);
                    }
                }
            }
        };
        h.post(run[0]);

        addLine("Проверка идёт в фоне...", 0xFFA78BFA);
    }

    private void addHeader(String text) {
        TextView tv = new TextView(this);
        tv.setText("\n" + text);
        tv.setTextColor(0xFFC4B5FD);
        tv.setTextSize(15f);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        tv.setPadding(0, 10, 0, 4);
        logContainer.addView(tv);
    }

    private void addLine(String text, int color) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(color);
        tv.setTextSize(13f);
        tv.setPadding(10, 3, 10, 3);
        logContainer.addView(tv);
    }

    private void showMemoryStats() {
        Runtime rt = Runtime.getRuntime();
        long used = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
        long max = rt.maxMemory() / (1024 * 1024);
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.US);
        summaryView.setText("💾 Память: " + used + " / " + max + " МБ\n" +
                "🕐 " + sdf.format(new Date()));
    }
                       }
