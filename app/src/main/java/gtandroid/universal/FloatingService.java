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
import java.util.Date;
import java.util.Locale;

public class FloatingService extends Service {

    public static int currentChatMode = 0;

    private WindowManager windowManager;
    private View floatingIconView;
    private LinearLayout menuView;
    private SharedPreferences prefs;

    private int chatMode = 0;
    private Handler iconUpdateHandler = new Handler();
    private Runnable iconUpdateRunnable;
    private BroadcastReceiver reportReceiver;

    private static final String PREFS_DATA = "GTAndroidData";
    private static final String KEY_UPTIME_START = "uptime_start_timestamp";
    private static final String KEY_UPTIME_DATE = "uptime_start_date";

    private static final String CHANNEL_ID = "rtools_float";
    private static final int NOTIF_ID = 3301;

    private static final int PURPLE_MAIN   = 0xFF7C3AED;
    private static final int PURPLE_DARK   = 0xFF4C1D95;
    private static final int PURPLE_LIGHT  = 0xFFA78BFA;
    private static final int PURPLE_BG     = 0xFF1A0B2E;
    private static final int PURPLE_PANEL  = 0xFF2D1B4E;
    private static final int PURPLE_ACCENT = 0xFFC4B5FD;
    private static final int WHITE         = 0xFFFFFFFF;

    private int menuX = -1, menuY = -1; // запоминаем позицию меню

    @Override
    public void onCreate() {
        super.onCreate();
        startPersistentForeground();

        this.prefs = getSharedPreferences(PREFS_DATA, MODE_PRIVATE);
        this.chatMode = this.prefs.getInt("chat_mode", 0);
        currentChatMode = this.chatMode;

        this.windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        ensureUptimeStarted();

        this.reportReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if ("REPORT_CAUGHT".equals(action)) {
                    incrementReportCount();
                    updateIconContent();
                    String text = intent.getStringExtra("report_text");
                    int num = intent.getIntExtra("report_number", 0);
                    String msg = "🎯 Пойман репорт";
                    if (num > 0) msg += " #" + num;
                    if (text != null && !text.isEmpty()) {
                        String shortText = text.length() > 40
                                ? text.substring(0, 40) + "..."
                                : text;
                        msg += "\n" + shortText;
                    }
                    Toast.makeText(FloatingService.this, msg,
                            Toast.LENGTH_LONG).show();
                } else if ("REPORT_STATS_RESET".equals(action)) {
                    updateIconContent();
                }
            }
        };
        IntentFilter f = new IntentFilter();
        f.addAction("REPORT_CAUGHT");
        f.addAction("REPORT_STATS_RESET");
        registerReceiver(this.reportReceiver, f);

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

    private void ensureUptimeStarted() {
        // Сессия начинается с момента запуска FloatingService
        if (prefs.getLong(KEY_UPTIME_START, 0L) == 0L) {
            prefs.edit()
                    .putLong(KEY_UPTIME_START, System.currentTimeMillis())
                    .apply();
        }
    }

    private long getUptimeMillis() {
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
        this.floatingIconView.setAlpha(0.85f);
        updateIconContent();

        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                iconSize, iconSize,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                android.graphics.PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = dpToPx(8f);
        params.y = dpToPx(80f);

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
                        v.setAlpha(1.0f);
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
                    case MotionEvent.ACTION_CANCEL:
                        v.setAlpha(0.85f);
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
    }

    private Drawable getReportCounterBackground(String count) {
        Bitmap bm = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bm);

        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setColor(PURPLE_BG);
        c.drawCircle(60, 60, 58, bg);

        Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(4f);
        border.setColor(PURPLE_LIGHT);
        c.drawCircle(60, 60, 56, border);

        Paint caption = new Paint(Paint.ANTI_ALIAS_FLAG);
        caption.setColor(PURPLE_ACCENT);
        caption.setTextAlign(Paint.Align.CENTER);
        caption.setTextSize(14f);
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
        this.menuView.setBackground(GlassUI.glassSelector(getApplicationContext(), PURPLE_DARK));
        this.menuView.setAlpha(0.95f);

        int width = Math.min(dpToPx(220f),
                getResources().getDisplayMetrics().widthPixels - dpToPx(12f));
        final WindowManager.LayoutParams mp = new WindowManager.LayoutParams(
                width,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                android.graphics.PixelFormat.TRANSLUCENT);
        mp.gravity = Gravity.TOP | Gravity.START;
        mp.x = Math.max(0, Math.min(x,
                getResources().getDisplayMetrics().widthPixels - width));
        mp.y = Math.max(dpToPx(30f), y);

        try {
            this.windowManager.addView(this.menuView, mp);
        } catch (Exception e) {
            if (this.floatingIconView != null) this.floatingIconView.setVisibility(View.VISIBLE);
            return;
        }

        setupMenuContent(this.menuView, mp);
    }

    private void closeMenu() {
        if (this.floatingIconView != null) this.floatingIconView.setVisibility(View.VISIBLE);
        if (this.menuView != null && this.windowManager != null) {
            try { this.windowManager.removeView(this.menuView); } catch (Exception ignored) { }
            this.menuView = null;
        }
    }

    private void setupMenuContent(LinearLayout root, final WindowManager.LayoutParams mp) {
        // ====== Заголовок, за который можно таскать меню ======
        TextView title = new TextView(this);
        title.setText("⋮⋮  RTools[+]  ⋮⋮");
        title.setTextColor(WHITE);
        title.setTextSize(12f);
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dpToPx(10), 0, dpToPx(10));
        title.setBackground(GlassUI.glassSelector(getApplicationContext(), PURPLE_MAIN));
        root.addView(title);

        // Таскаем меню за заголовок
        title.setOnTouchListener(new View.OnTouchListener() {
            private int startX, startY;
            private float downRawX, downRawY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = mp.x; startY = mp.y;
                        downRawX = event.getRawX(); downRawY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        mp.x = Math.max(0, startX + (int) (event.getRawX() - downRawX));
                        mp.y = Math.max(0, startY + (int) (event.getRawY() - downRawY));
                        try { windowManager.updateViewLayout(menuView, mp); }
                        catch (Exception ignored) { }
                        return true;
                }
                return false;
            }
        });

        root.addView(buildSessionPanel());
        root.addView(buildReportsPanel());
        root.addView(buildChatModePanel());

        Button closeBtn = new Button(this);
        closeBtn.setText("✕ ЗАКРЫТЬ");
        closeBtn.setAllCaps(false);
        closeBtn.setTextColor(WHITE);
        closeBtn.setTextSize(12f);
        closeBtn.setBackground(GlassUI.glassSelector(getApplicationContext(), 0xFFDC2626));
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(40));
        closeLp.setMargins(dpToPx(6), dpToPx(4), dpToPx(6), dpToPx(6));
        closeBtn.setLayoutParams(closeLp);
        closeBtn.setOnClickListener(v -> closeMenu());
        root.addView(closeBtn);
    }

    private View buildSessionPanel() {
        LinearLayout panel = makePanel("⏱  СЕССИЯ");
        final TextView tv = makeInfoRow(panel, "");

        final Handler h = new Handler();
        h.post(new Runnable() {
            @Override public void run() {
                tv.setText("⏱ " + formatUptime(getUptimeMillis()));
                h.postDelayed(this, 1000L);
            }
        });
        return panel;
    }

    private View buildReportsPanel() {
        LinearLayout panel = makePanel("📨  РЕПОРТЫ");
        final TextView tv = makeInfoRow(panel, "");

        final Handler h = new Handler();
        h.post(new Runnable() {
            @Override public void run() {
                tv.setText("📨 Поймано: " + getReportsTotal());
                h.postDelayed(this, 1000L);
            }
        });
        return panel;
    }

    private LinearLayout makePanel(String headerText) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(GlassUI.glassSelector(getApplicationContext(), PURPLE_PANEL));
        panel.setPadding(dpToPx(10), dpToPx(8), dpToPx(10), dpToPx(8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dpToPx(6), dpToPx(4), dpToPx(6), dpToPx(4));
        panel.setLayoutParams(lp);

        TextView header = new TextView(this);
        header.setText(headerText);
        header.setTextColor(PURPLE_ACCENT);
        header.setTextSize(10f);
        header.setTypeface(null, Typeface.BOLD);
        header.setPadding(0, 0, 0, dpToPx(4));
        panel.addView(header);
        return panel;
    }

    private TextView makeInfoRow(LinearLayout parent, String label) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(WHITE);
        tv.setTextSize(12f);
        tv.setPadding(0, dpToPx(2), 0, dpToPx(2));
        parent.addView(tv);
        return tv;
    }

    private View buildChatModePanel() {
        LinearLayout panel = makePanel("💬  РЕЖИМ ЧАТА");

        RadioGroup group = new RadioGroup(this);
        group.setOrientation(RadioGroup.VERTICAL);

        RadioButton rbNormal = makeRadio("Обычный", 0);
        RadioButton rbNonRp = makeRadio("/n", 1);
        RadioButton rbAdmin = makeRadio("/a", 2);

        group.addView(rbNormal);
        group.addView(rbNonRp);
        group.addView(rbAdmin);

        switch (chatMode) {
            case 1: rbNonRp.setChecked(true); break;
            case 2: rbAdmin.setChecked(true); break;
            default: rbNormal.setChecked(true); break;
        }

        group.setOnCheckedChangeListener((g, checkedId) -> {
            int newMode = 0;
            String toast = "Обычный";
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
        rb.setTextColor(WHITE);
        rb.setTextSize(12f);
        rb.setId(id);
        rb.setPadding(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4));
        return rb;
    }

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
