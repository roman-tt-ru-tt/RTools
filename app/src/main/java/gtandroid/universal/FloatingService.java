package gtandroid.universal;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Плавающий виджет RTools[+].
 * По нажатию на иконку — открывает инфо-панель и режимы чата.
 * Больше НЕ содержит читов и ADM-форм.
 */
public class FloatingService extends Service {

    public static int currentChatMode = 0;

    private WindowManager windowManager;
    private View floatingIconView;
    private LinearLayout menuView;
    private SharedPreferences prefs;

    private int chatMode = 0;
    private Handler iconUpdateHandler = new Handler();
    private Runnable iconUpdateRunnable;

    // Репорты
    private BroadcastReceiver reportReceiver;

    // Uptime
    private static final String PREFS_DATA = "GTAndroidData";
    private static final String KEY_UPTIME_START = "uptime_start_timestamp";
    private static final String KEY_UPTIME_DATE = "uptime_start_date";

    private static final String CHANNEL_ID = "rtools_float";
    private static final int NOTIF_ID = 3301;

    @Override
    public void onCreate() {
        super.onCreate();
        startPersistentForeground();

        this.prefs = getSharedPreferences(PREFS_DATA, MODE_PRIVATE);
        this.chatMode = this.prefs.getInt("chat_mode", 0);
        currentChatMode = this.chatMode;

        this.windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        ensureUptimeStarted();

        // Приём уведомлений о пойманных репортах
        this.reportReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if ("REPORT_CAUGHT".equals(intent.getAction())) {
                    incrementReportCount();
                    updateIconContent();
                }
            }
        };
        registerReceiver(this.reportReceiver, new IntentFilter("REPORT_CAUGHT"));

        createFloatingIcon();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startPersistentForeground();
        ensureUptimeStarted();
        return START_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        try {
            Intent restart = new Intent(getApplicationContext(), FloatingService.class);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(restart);
            else startService(restart);
        } catch (Exception ignored) { }
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    private void startPersistentForeground() {
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel ch = new NotificationChannel(
                        CHANNEL_ID, "RTools[+]", NotificationManager.IMPORTANCE_LOW);
                ch.setDescription("Плавающее меню RTools[+]");
                NotificationManager nm =
                        (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                if (nm != null) nm.createNotificationChannel(ch);
            }
            Intent launch = new Intent(this, MainActivity.class);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
            PendingIntent pi = PendingIntent.getActivity(this, 0, launch, flags);
            Notification.Builder b = Build.VERSION.SDK_INT >= 26
                    ? new Notification.Builder(this, CHANNEL_ID)
                    : new Notification.Builder(this);
            b.setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("RTools[+]")
                    .setContentText("Плавающее меню активно")
                    .setContentIntent(pi)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true);
            startForeground(NOTIF_ID, b.build());
        } catch (Exception ignored) { }
    }

    // ================= UPTIME =================

    /** Инициализируем дату старта uptime: если день сменился — обнуляем. */
    private void ensureUptimeStarted() {
        String today = todayString();
        String savedDate = prefs.getString(KEY_UPTIME_DATE, "");
        long savedStart = prefs.getLong(KEY_UPTIME_START, 0L);

        if (savedStart == 0L || !today.equals(savedDate)) {
            prefs.edit()
                    .putLong(KEY_UPTIME_START, System.currentTimeMillis())
                    .putString(KEY_UPTIME_DATE, today)
                    .apply();
        }
    }

    private String todayString() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        return sdf.format(new Date());
    }

    private long getUptimeMillis() {
        // При каждом вызове — проверяем, не наступил ли новый день
        ensureUptimeStarted();
        long start = prefs.getLong(KEY_UPTIME_START, System.currentTimeMillis());
        return System.currentTimeMillis() - start;
    }

    private String formatUptime(long millis) {
        long s = millis / 1000;
        long h = s / 3600;
        long m = (s % 3600) / 60;
        long sec = s % 60;
        return String.format(Locale.US, "%02d:%02d:%02d", h, m, sec);
    }

    // ================= РЕПОРТЫ =================

    private static final String PREFS_STATS = "GTAndroidStats";
    private static final String KEY_REPORTS_TOTAL = "total_reports_all_time";

    private int getReportsTotal() {
        return getSharedPreferences(PREFS_STATS, MODE_PRIVATE)
                .getInt(KEY_REPORTS_TOTAL, 0);
    }

    private void incrementReportCount() {
        SharedPreferences sp = getSharedPreferences(PREFS_STATS, MODE_PRIVATE);
        sp.edit().putInt(KEY_REPORTS_TOTAL, sp.getInt(KEY_REPORTS_TOTAL, 0) + 1).apply();
    }

    // ================= ИКОНКА =================

    private int dpToPx(float dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }

    private void createFloatingIcon() {
        final int iconSize = dpToPx(56f);
        this.floatingIconView = new ImageButton(this);
        ((ImageButton) this.floatingIconView).setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        ((ImageButton) this.floatingIconView).setBackgroundColor(0);
        updateIconContent();

        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                iconSize, iconSize,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                android.graphics.PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 0;
        params.y = dpToPx(33f);

        try {
            this.windowManager.addView(this.floatingIconView, params);
        } catch (Exception ignored) { }

        this.floatingIconView.setOnTouchListener(new View.OnTouchListener() {
            private int startX, startY;
            private float downRawX, downRawY;
            private boolean moved;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = params.x; startY = params.y;
                        downRawX = event.getRawX(); downRawY = event.getRawY();
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int dx = (int) (event.getRawX() - downRawX);
                        int dy = (int) (event.getRawY() - downRawY);
                        if (Math.abs(dx) > dpToPx(4) || Math.abs(dy) > dpToPx(4)) moved = true;
                        if (moved) {
                            params.x = Math.max(0, startX + dx);
                            int maxY = Math.max(0,
                                    getResources().getDisplayMetrics().heightPixels - iconSize);
                            params.y = Math.max(0, Math.min(maxY, startY + dy));
                            try { windowManager.updateViewLayout(floatingIconView, params); }
                            catch (Exception ignored) { }
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!moved) openMenu(params.x, params.y);
                        return true;
                }
                return true;
            }
        });

        startIconUpdater();
    }

    private void startIconUpdater() {
        this.iconUpdateRunnable = new Runnable() {
            @Override public void run() {
                updateIconContent();
                iconUpdateHandler.postDelayed(this, 1000L);
            }
        };
        this.iconUpdateHandler.post(this.iconUpdateRunnable);
    }

    private void updateIconContent() {
        if (this.floatingIconView == null) return;
        int reports = getReportsTotal();
        ((ImageButton) this.floatingIconView)
                .setBackground(getReportCounterBackground(String.valueOf(reports)));
        ((ImageButton) this.floatingIconView).setContentDescription(
                "Репортов: " + reports);
    }

    private Drawable getReportCounterBackground(String count) {
        Bitmap bm = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bm);

        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setColor(Color.BLACK);
        c.drawCircle(60, 60, 58, bg);

        Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(3f);
        border.setColor(Color.parseColor("#6C4BFF"));
        c.drawCircle(60, 60, 57, border);

        Paint caption = new Paint(Paint.ANTI_ALIAS_FLAG);
        caption.setColor(Color.WHITE);
        caption.setTextAlign(Paint.Align.CENTER);
        caption.setTextSize(14f);
        caption.setTypeface(Typeface.DEFAULT);
        c.drawText("РЕПОРТЫ", 60, 43, caption);

        Paint number = new Paint(Paint.ANTI_ALIAS_FLAG);
        number.setColor(Color.WHITE);
        number.setTextAlign(Paint.Align.CENTER);
        number.setTypeface(Typeface.DEFAULT_BOLD);
        number.setTextSize(count.length() > 3 ? 27f : 34f);
        c.drawText(count, 60, 80, number);

        return new BitmapDrawable(getResources(), bm);
    }

    // ================= МЕНЮ =================

    private void openMenu(int x, int y) {
        if (this.floatingIconView != null) this.floatingIconView.setVisibility(View.GONE);
        if (this.menuView != null && this.windowManager != null) {
            try { this.windowManager.removeView(this.menuView); } catch (Exception ignored) { }
        }

        this.menuView = new LinearLayout(this);
        this.menuView.setOrientation(LinearLayout.VERTICAL);
        this.menuView.setBackground(GlassUI.glassSelector(getApplicationContext(), 0xdd1c1c1e));

        int width = Math.min(dpToPx(310f),
                getResources().getDisplayMetrics().widthPixels - dpToPx(12f));
        WindowManager.LayoutParams mp = new WindowManager.LayoutParams(
                width,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                android.graphics.PixelFormat.TRANSLUCENT);
        mp.gravity = Gravity.TOP | Gravity.START;
        mp.x = Math.max(0, Math.min(x,
                getResources().getDisplayMetrics().widthPixels - width));
        mp.y = Math.max(dpToPx(33f), y);

        try {
            this.windowManager.addView(this.menuView, mp);
        } catch (Exception e) {
            if (this.floatingIconView != null) this.floatingIconView.setVisibility(View.VISIBLE);
            return;
        }

        setupMenuContent(this.menuView);
    }

    private void closeMenu() {
        if (this.floatingIconView != null) this.floatingIconView.setVisibility(View.VISIBLE);
        if (this.menuView != null && this.windowManager != null) {
            try { this.windowManager.removeView(this.menuView); } catch (Exception ignored) { }
            this.menuView = null;
        }
    }

    private void setupMenuContent(LinearLayout root) {
        // Заголовок
        TextView title = new TextView(this);
        title.setText("RTools[+]  •  ПАНЕЛЬ");
        title.setTextColor(Color.WHITE);
        title.setTextSize(14f);
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dpToPx(14), 0, dpToPx(14));
        title.setBackground(GlassUI.glassSelector(getApplicationContext(), 0xff4a2d8f));
        root.addView(title);

        // ИНФО-ПАНЕЛЬ
        root.addView(buildInfoPanel());

        // РЕЖИМ ЧАТА
        root.addView(buildChatModePanel());

        // Кнопка закрыть
        Button closeBtn = new Button(this);
        closeBtn.setText("✕ ЗАКРЫТЬ");
        closeBtn.setAllCaps(false);
        closeBtn.setTextColor(Color.WHITE);
        closeBtn.setTextSize(13f);
        closeBtn.setBackground(GlassUI.glassSelector(getApplicationContext(), 0xffff453a));
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(48));
        closeLp.setMargins(dpToPx(10), dpToPx(6), dpToPx(10), dpToPx(10));
        closeBtn.setLayoutParams(closeLp);
        closeBtn.setOnClickListener(v -> closeMenu());
        root.addView(closeBtn);
    }

    private View buildInfoPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(GlassUI.glassSelector(getApplicationContext(), 0xff1b1b2e));
        panel.setPadding(dpToPx(14), dpToPx(12), dpToPx(14), dpToPx(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dpToPx(10), dpToPx(10), dpToPx(10), dpToPx(6));
        panel.setLayoutParams(lp);

        TextView header = new TextView(this);
        header.setText("📊  ИНФОРМАЦИЯ");
        header.setTextColor(0xff00e5c0);
        header.setTextSize(12f);
        header.setTypeface(null, Typeface.BOLD);
        header.setPadding(0, 0, 0, dpToPx(10));
        panel.addView(header);

        // Строки информации — обновляем каждую секунду
        final TextView tvTime = makeInfoRow(panel, "🕐  Время");
        final TextView tvDate = makeInfoRow(panel, "📅  Дата");
        final TextView tvUptime = makeInfoRow(panel, "⏱  В сети");
        final TextView tvReports = makeInfoRow(panel, "📨  Репортов");

        // Периодическое обновление
        final Handler h = new Handler();
        Runnable r = new Runnable() {
            @Override public void run() {
                SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm:ss", Locale.US);
                SimpleDateFormat dateFmt = new SimpleDateFormat("dd.MM.yyyy", Locale.US);
                Date now = new Date();
                tvTime.setText("🕐  Время:  " + timeFmt.format(now));
                tvDate.setText("📅  Дата:  " + dateFmt.format(now));
                tvUptime.setText("⏱  В сети:  " + formatUptime(getUptimeMillis()));
                tvReports.setText("📨  Репортов:  " + getReportsTotal());
                h.postDelayed(this, 1000L);
            }
        };
        h.post(r);

        return panel;
    }

    private TextView makeInfoRow(LinearLayout parent, String label) {
        TextView tv = new TextView(this);
        tv.setText(label + ":  —");
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(14f);
        tv.setPadding(0, dpToPx(4), 0, dpToPx(4));
        parent.addView(tv);
        return tv;
    }

    private View buildChatModePanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(GlassUI.glassSelector(getApplicationContext(), 0xff2a1a4a));
        panel.setPadding(dpToPx(14), dpToPx(12), dpToPx(14), dpToPx(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dpToPx(10), dpToPx(6), dpToPx(10), dpToPx(6));
        panel.setLayoutParams(lp);

        TextView header = new TextView(this);
        header.setText("💬  РЕЖИМ ЧАТА");
        header.setTextColor(0xffb399ff);
        header.setTextSize(12f);
        header.setTypeface(null, Typeface.BOLD);
        header.setPadding(0, 0, 0, dpToPx(8));
        panel.addView(header);

        RadioGroup group = new RadioGroup(this);
        group.setOrientation(RadioGroup.VERTICAL);

        RadioButton rbNormal = makeRadio("⚪  Обычный чат", 0);
        RadioButton rbNonRp = makeRadio("💬  Нон-РП  (/n)", 1);
        RadioButton rbAdmin = makeRadio("📢  Админ  (/a)", 2);

        group.addView(rbNormal);
        group.addView(rbNonRp);
        group.addView(rbAdmin);

        // Текущее состояние
        switch (chatMode) {
            case 1: rbNonRp.setChecked(true); break;
            case 2: rbAdmin.setChecked(true); break;
            default: rbNormal.setChecked(true); break;
        }

        group.setOnCheckedChangeListener((g, checkedId) -> {
            int newMode = 0;
            String toast = "Обычный чат";
            if (checkedId == 1) { newMode = 1; toast = "Нон-РП (/n)"; }
            else if (checkedId == 2) { newMode = 2; toast = "Админ (/a)"; }
            chatMode = newMode;
            currentChatMode = newMode;
            prefs.edit().putInt("chat_mode", newMode).apply();
            Toast.makeText(FloatingService.this, "Режим: " + toast,
                    Toast.LENGTH_SHORT).show();
        });

        panel.addView(group);
        return panel;
    }

    private RadioButton makeRadio(String text, int id) {
        RadioButton rb = new RadioButton(this);
        rb.setText(text);
        rb.setTextColor(Color.WHITE);
        rb.setTextSize(14f);
        rb.setId(id);
        rb.setPadding(dpToPx(6), dpToPx(8), dpToPx(6), dpToPx(8));
        return rb;
    }

    // ================= LIFECYCLE =================

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (iconUpdateHandler != null && iconUpdateRunnable != null) {
            iconUpdateHandler.removeCallbacks(iconUpdateRunnable);
        }
        try {
            if (floatingIconView != null && windowManager != null)
                windowManager.removeView(floatingIconView);
        } catch (Exception ignored) { }
        try {
            if (menuView != null && windowManager != null)
                windowManager.removeView(menuView);
        } catch (Exception ignored) { }
        try {
            if (reportReceiver != null) unregisterReceiver(reportReceiver);
        } catch (Exception ignored) { }
    }
                                                 }
