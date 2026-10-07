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
import android.graphics.Point;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

/* loaded from: classes2.dex */
public class FloatingService extends Service {
    public static int currentChatMode = 0;
    public static int currentCheatMode = 0;
    private BroadcastReceiver cheatResetReceiver;
    private View floatingIconView;
    private Runnable iconUpdateRunnable;
    private LinearLayout menuView;
    private SharedPreferences prefs;
    private BroadcastReceiver updateIconReceiver;
    private BroadcastReceiver adminFormReceiver;
    private WindowManager windowManager;
    private int chatMode = 0;
    private int cheatMode = 0;
    private int keyboardSize = 1;
    private int keyboardTransparency = 100;
    private Handler iconUpdateHandler = new Handler();
    private static final String FORM_CHANNEL_ID = "gtandroid_forms";
    private static final int FORM_NOTIFICATION_ID = 3301;
    private LinearLayout adminFormsContainer;

    private void startPersistentForeground() {
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationChannel channel = new NotificationChannel(
                        FORM_CHANNEL_ID, "GTAndroid", NotificationManager.IMPORTANCE_LOW);
                channel.setDescription("Постоянная работа плавающего меню и ADMIN-форм");
                NotificationManager manager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                if (manager != null) manager.createNotificationChannel(channel);
            }
            Intent launchIntent = new Intent(this, MainActivity.class);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
            PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, launchIntent, flags);
            Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                    ? new Notification.Builder(this, FORM_CHANNEL_ID)
                    : new Notification.Builder(this);
            builder.setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("GTAndroid")
                    .setContentText("Плавающее меню и ADMIN-формы работают")
                    .setContentIntent(pendingIntent)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true);
            startForeground(FORM_NOTIFICATION_ID, builder.build());
        } catch (Exception ignored) {
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startPersistentForeground();
        return START_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        try {
            Intent restartIntent = new Intent(getApplicationContext(), FloatingService.class);
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(restartIntent);
            else startService(restartIntent);
        } catch (Exception ignored) {
        }
        super.onTaskRemoved(rootIntent);
    }

    @Override // android.app.Service
    public IBinder onBind(Intent intent) {
        return null;
    }

    private int dpToPx(float dp) {
        return (int) TypedValue.applyDimension(1, dp, getResources().getDisplayMetrics());
    }

    @Override // android.app.Service
    public void onCreate() {
        super.onCreate();
        startPersistentForeground();
        this.prefs = getSharedPreferences("GTAndroidData", 0);
        this.chatMode = this.prefs.getInt("chat_mode", 0);
        currentChatMode = this.chatMode;
        this.cheatMode = this.prefs.getInt("cheat_mode", 0);
        currentCheatMode = this.cheatMode;
        this.keyboardSize = this.prefs.getInt("keyboard_size", 1);
        this.keyboardTransparency = this.prefs.getInt("keyboard_transparency", 100);
        this.windowManager = (WindowManager) getSystemService("window");
        this.cheatResetReceiver = new BroadcastReceiver() { // from class: gtandroid.universal.FloatingService.1
            @Override // android.content.BroadcastReceiver
            public void onReceive(Context context, Intent intent) {
                if ("CHEAT_MODE_RESET".equals(intent.getAction())) {
                    FloatingService.this.cheatMode = 0;
                    FloatingService.currentCheatMode = 0;
                    FloatingService.this.updateIconBackground();
                }
            }
        };
        registerReceiver(this.cheatResetReceiver, new IntentFilter("CHEAT_MODE_RESET"));
        this.updateIconReceiver = new BroadcastReceiver() { // from class: gtandroid.universal.FloatingService.2
            @Override // android.content.BroadcastReceiver
            public void onReceive(Context context, Intent intent) {
                if ("UPDATE_FLOATING_ICON".equals(intent.getAction())) {
                    FloatingService.this.updateIconBackground();
                }
            }
        };
        registerReceiver(this.updateIconReceiver, new IntentFilter("UPDATE_FLOATING_ICON"));
        this.adminFormReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (adminFormsContainer != null) {
                    showAdminFormsTab(adminFormsContainer);
                }
                Toast.makeText(FloatingService.this, "📨 Новая ADMIN-форма", Toast.LENGTH_SHORT).show();
            }
        };
        registerReceiver(this.adminFormReceiver, new IntentFilter("ADMIN_FORM_RECEIVED"));
        createFloatingIcon();
    }

    private void createFloatingIcon() {
        final int iconSize = dpToPx(40.0f);
        this.floatingIconView = new ImageButton(this);
        ((ImageButton) this.floatingIconView).setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        ((ImageButton) this.floatingIconView).setBackgroundColor(0);
        updateIconBackground();
        final WindowManager.LayoutParams params = new WindowManager.LayoutParams(iconSize, iconSize, 2038, 8, -3);
        params.gravity = 8388659;
        params.x = 0;
        params.y = dpToPx(33.0f);
        this.windowManager.addView(this.floatingIconView, params);
        startIconUpdater();
        this.floatingIconView.setOnTouchListener(new View.OnTouchListener() { // from class: gtandroid.universal.FloatingService.3
            private long downTime;
            private float initialTouchX;
            private float initialTouchY;
            private float initialX;
            private float initialY;

            @Override // android.view.View.OnTouchListener
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case 0:
                        this.initialX = params.x;
                        this.initialY = params.y;
                        this.initialTouchX = event.getRawX();
                        this.initialTouchY = event.getRawY();
                        this.downTime = System.currentTimeMillis();
                        break;
                    case 1:
                        float moveDeltaX = event.getRawX();
                        float upDeltaX = moveDeltaX - this.initialTouchX;
                        float upDeltaY = event.getRawY() - this.initialTouchY;
                        float distance = (float) Math.sqrt((upDeltaX * upDeltaX) + (upDeltaY * upDeltaY));
                        long duration = System.currentTimeMillis() - this.downTime;
                        if (distance < 10.0f && duration < 300) {
                            FloatingService.this.openMenu(params.x, params.y);
                            break;
                        }
                        break;
                    case 2:
                        float moveDeltaX2 = event.getRawX() - this.initialTouchX;
                        float moveDeltaY = event.getRawY() - this.initialTouchY;
                        float newX = this.initialX + moveDeltaX2;
                        float newY = this.initialY + moveDeltaY;
                        Point displaySize = new Point();
                        FloatingService.this.windowManager.getDefaultDisplay().getSize(displaySize);
                        int screenWidth = displaySize.x;
                        int screenHeight = displaySize.y;
                        float newX2 = Math.max(0.0f, Math.min(newX, screenWidth - iconSize));
                        float newY2 = Math.max(0.0f, Math.min(newY, screenHeight - iconSize));
                        params.x = (int) newX2;
                        params.y = (int) newY2;
                        FloatingService.this.windowManager.updateViewLayout(FloatingService.this.floatingIconView, params);
                        break;
                }
                return true;
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void updateIconBackground() {
        String modeText = "";
        int bgColor = Color.parseColor("#0066CC");
        if (this.cheatMode > 0) {
            switch (this.cheatMode) {
                case 1:
                    modeText = "GM";
                    bgColor = Color.parseColor("#FF00FF");
                    break;
                case 2:
                    modeText = "DL";
                    bgColor = Color.parseColor("#FF00FF");
                    break;
                case 3:
                    modeText = "CL";
                    bgColor = Color.parseColor("#FF00FF");
                    break;
            }
        } else {
            switch (this.chatMode) {
                case 1:
                    modeText = "N";
                    bgColor = Color.parseColor("#FF6600");
                    break;
                case 2:
                    modeText = "A";
                    bgColor = Color.parseColor("#CC0000");
                    break;
                default:
                    modeText = "";
                    bgColor = Color.parseColor("#0066CC");
                    break;
            }
        }
        ((ImageButton) this.floatingIconView).setBackground(getCircularBackground("00:00", "0", modeText, bgColor));
    }

    private void startIconUpdater() {
        this.iconUpdateRunnable = new Runnable() { // from class: gtandroid.universal.FloatingService.4
            @Override // java.lang.Runnable
            public void run() {
                FloatingService.this.updateIconContent();
                FloatingService.this.iconUpdateHandler.postDelayed(this, 1000L);
            }
        };
        this.iconUpdateHandler.post(this.iconUpdateRunnable);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void updateIconContent() {
        if (this.floatingIconView == null) {
            return;
        }
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm");
        sdf.setTimeZone(TimeZone.getTimeZone("Europe/Moscow"));
        String timeStr = sdf.format(new Date());
        SharedPreferences statsPrefs = getSharedPreferences("GTAndroidStats", 0);
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("Europe/Moscow"));
        int dayOfWeek = cal.get(7);
        int mondayBasedIndex = dayOfWeek == 1 ? 6 : dayOfWeek - 2;
        int todayReports = statsPrefs.getInt("weekly_" + mondayBasedIndex, 0);
        String repStr = String.valueOf(todayReports);
        String modeText = "";
        int bgColor = Color.parseColor("#0066CC");
        if (this.cheatMode > 0) {
            switch (this.cheatMode) {
                case 1:
                    modeText = "GM";
                    bgColor = Color.parseColor("#FF00FF");
                    break;
                case 2:
                    modeText = "DL";
                    bgColor = Color.parseColor("#FF00FF");
                    break;
                case 3:
                    modeText = "CL";
                    bgColor = Color.parseColor("#FF00FF");
                    break;
            }
        } else {
            switch (this.chatMode) {
                case 1:
                    modeText = "N";
                    bgColor = Color.parseColor("#FF6600");
                    break;
                case 2:
                    modeText = "A";
                    bgColor = Color.parseColor("#CC0000");
                    break;
            }
        }
        ((ImageButton) this.floatingIconView).setBackground(getCircularBackground(timeStr, repStr, modeText, bgColor));
    }

    private Drawable getCircularBackground(String time, String reps, String modeText, int bgColor) {
        Bitmap bitmap = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        int r = (bgColor >> 16) & 255;
        int g = (bgColor >> 8) & 255;
        int b = bgColor & 255;
        int glassFill = (-436207616) | (((int) ((r * 0.82d) + 45.9d)) << 16) | (((int) ((g * 0.82d) + 45.9d)) << 8) | ((int) ((b * 0.82d) + 45.9d));
        Paint bgPaint = new Paint(1);
        bgPaint.setColor(glassFill);
        canvas.drawCircle(120 / 2, 120 / 2, 120 / 2, bgPaint);
        Paint borderPaint = new Paint(1);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(3.0f);
        borderPaint.setColor(1509949439);
        canvas.drawCircle(120 / 2, 120 / 2, (120 / 2) - 2, borderPaint);
        if (!modeText.isEmpty()) {
            Paint modePaint = new Paint(1);
            modePaint.setColor(-1);
            modePaint.setTextAlign(Paint.Align.CENTER);
            modePaint.setTextSize(18.0f);
            modePaint.setTypeface(Typeface.DEFAULT_BOLD);
            canvas.drawText(modeText, 120 / 2, 25.0f, modePaint);
        }
        Paint textPaint = new Paint(1);
        textPaint.setColor(-1);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(26.0f);
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        canvas.drawText(time, 120 / 2, (120 / 2) + 5, textPaint);
        Paint linePaint = new Paint(1);
        linePaint.setColor(-1);
        linePaint.setStrokeWidth(2.0f);
        canvas.drawLine(20.0f, (120 / 2) + 15, 120 - 20, (120 / 2) + 15, linePaint);
        textPaint.setTextSize(14.0f);
        textPaint.setTypeface(Typeface.DEFAULT);
        canvas.drawText("РЕП:", 120 / 2, (120 / 2) + 35, textPaint);
        textPaint.setTextSize(20.0f);
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
        canvas.drawText(reps, 120 / 2, (120 / 2) + 55, textPaint);
        return new BitmapDrawable(getResources(), bitmap);
    }

    @Override // android.app.Service
    public void onDestroy() {
        super.onDestroy();
        if (this.iconUpdateHandler != null && this.iconUpdateRunnable != null) {
            this.iconUpdateHandler.removeCallbacks(this.iconUpdateRunnable);
        }
        if (this.floatingIconView != null && this.windowManager != null) {
            this.windowManager.removeView(this.floatingIconView);
        }
        if (this.menuView != null && this.windowManager != null) {
            this.windowManager.removeView(this.menuView);
        }
        if (this.cheatResetReceiver != null) {
            unregisterReceiver(this.cheatResetReceiver);
        }
        if (this.updateIconReceiver != null) {
            unregisterReceiver(this.updateIconReceiver);
        }
        if (this.adminFormReceiver != null) {
            unregisterReceiver(this.adminFormReceiver);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void openMenu(int x, int y) {
        if (this.floatingIconView != null) {
            this.floatingIconView.setVisibility(8);
        }
        if (this.menuView != null && this.windowManager != null) {
            this.windowManager.removeView(this.menuView);
        }
        this.menuView = new LinearLayout(this);
        this.menuView.setOrientation(1);
        this.menuView.setBackground(GlassUI.glassSelector(getApplicationContext(), -587202560));
        this.menuView.setPadding(0, 0, 0, 0);
        WindowManager.LayoutParams menuParams = new WindowManager.LayoutParams(450, -2, 2038, 8, -3);
        menuParams.gravity = 8388659;
        menuParams.x = x;
        menuParams.y = dpToPx(33.0f) + y;
        this.windowManager.addView(this.menuView, menuParams);
        setupMenuContent(this.menuView, menuParams);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void closeMenu() {
        if (this.floatingIconView != null) {
            this.floatingIconView.setVisibility(0);
        }
        if (this.menuView != null && this.windowManager != null) {
            this.windowManager.removeView(this.menuView);
            this.menuView = null;
        }
    }

    private void setupMenuContent(LinearLayout linearLayout, final WindowManager.LayoutParams menuParams) {
        final LinearLayout dragHandle = new LinearLayout(this);
        dragHandle.setOrientation(0);
        dragHandle.setBackground(GlassUI.glassSelector(getApplicationContext(), -12303292));
        dragHandle.setPadding(10, 10, 10, 10);
        dragHandle.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        TextView dragText = new TextView(this);
        dragText.setText("ПЕРЕТАЩИ");
        dragText.setTextColor(-1);
        dragText.setTextSize(12.0f);
        dragText.setGravity(17);
        dragText.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        dragHandle.addView(dragText);
        linearLayout.addView(dragHandle);
        LinearLayout tabContainer = new LinearLayout(this);
        tabContainer.setOrientation(0);
        tabContainer.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        final Button tabChat = new Button(this);
        tabChat.setText("💬 Чат");
        tabChat.setLayoutParams(new LinearLayout.LayoutParams(0, dpToPx(60.0f), 1.0f));
        tabChat.setBackground(GlassUI.glassSelector(getApplicationContext(), -16750900));
        tabChat.setTextColor(-1);
        tabChat.setTextSize(12.0f);
        tabChat.setAllCaps(false);
        final Button tabCheats = new Button(this);
        tabCheats.setText("🎮 Читы");
        tabCheats.setLayoutParams(new LinearLayout.LayoutParams(0, dpToPx(60.0f), 1.0f));
        tabCheats.setBackground(GlassUI.glassSelector(getApplicationContext(), -12303292));
        tabCheats.setTextColor(-1);
        tabCheats.setTextSize(12.0f);
        tabCheats.setAllCaps(false);
        final Button tabSettings = new Button(this);
        tabSettings.setText("⚙️ Наст");
        tabSettings.setLayoutParams(new LinearLayout.LayoutParams(0, dpToPx(60.0f), 1.0f));
        tabSettings.setBackground(GlassUI.glassSelector(getApplicationContext(), -12303292));
        tabSettings.setTextColor(-1);
        tabSettings.setTextSize(12.0f);
        tabSettings.setAllCaps(false);
        final Button tabForms = new Button(this);
        tabForms.setText("📨 Формы");
        tabForms.setLayoutParams(new LinearLayout.LayoutParams(0, dpToPx(60.0f), 1.0f));
        tabForms.setBackground(GlassUI.glassSelector(getApplicationContext(), -12303292));
        tabForms.setTextColor(-1);
        tabForms.setTextSize(11.0f);
        tabForms.setAllCaps(false);
        tabContainer.addView(tabChat);
        tabContainer.addView(tabCheats);
        tabContainer.addView(tabSettings);
        tabContainer.addView(tabForms);
        linearLayout.addView(tabContainer);
        final LinearLayout contentContainer = new LinearLayout(this);
        contentContainer.setOrientation(1);
        contentContainer.setPadding(20, 20, 20, 20);
        contentContainer.setBackground(GlassUI.glassSelector(getApplicationContext(), -587202560));
        contentContainer.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        linearLayout.addView(contentContainer);
        ImageButton closeBtn = new ImageButton(this);
        closeBtn.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        closeBtn.setBackground(GlassUI.glassSelector(getApplicationContext(), -3407872));
        closeBtn.setLayoutParams(new LinearLayout.LayoutParams(-1, 50));
        closeBtn.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.FloatingService.5
            @Override // android.view.View.OnClickListener
            public void onClick(View v) {
                FloatingService.this.closeMenu();
            }
        });
        linearLayout.addView(closeBtn);
        showChatModeTab(contentContainer);
        tabChat.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.FloatingService.6
            @Override // android.view.View.OnClickListener
            public void onClick(View v) {
                tabChat.setBackground(GlassUI.glassSelector(FloatingService.this.getApplicationContext(), -16750900));
                tabCheats.setBackground(GlassUI.glassSelector(FloatingService.this.getApplicationContext(), -12303292));
                tabSettings.setBackground(GlassUI.glassSelector(FloatingService.this.getApplicationContext(), -12303292));
                tabForms.setBackground(GlassUI.glassSelector(FloatingService.this.getApplicationContext(), -12303292));
                FloatingService.this.showChatModeTab(contentContainer);
            }
        });
        tabCheats.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.FloatingService.7
            @Override // android.view.View.OnClickListener
            public void onClick(View v) {
                tabChat.setBackground(GlassUI.glassSelector(FloatingService.this.getApplicationContext(), -12303292));
                tabCheats.setBackground(GlassUI.glassSelector(FloatingService.this.getApplicationContext(), -16750900));
                tabSettings.setBackground(GlassUI.glassSelector(FloatingService.this.getApplicationContext(), -12303292));
                tabForms.setBackground(GlassUI.glassSelector(FloatingService.this.getApplicationContext(), -12303292));
                FloatingService.this.showCheatsTab(contentContainer);
            }
        });
        tabSettings.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.FloatingService.8
            @Override // android.view.View.OnClickListener
            public void onClick(View v) {
                tabChat.setBackground(GlassUI.glassSelector(FloatingService.this.getApplicationContext(), -12303292));
                tabCheats.setBackground(GlassUI.glassSelector(FloatingService.this.getApplicationContext(), -12303292));
                tabSettings.setBackground(GlassUI.glassSelector(FloatingService.this.getApplicationContext(), -16750900));
                tabForms.setBackground(GlassUI.glassSelector(FloatingService.this.getApplicationContext(), -12303292));
                FloatingService.this.showSettingsTab(contentContainer);
            }
        });
        tabForms.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tabChat.setBackground(GlassUI.glassSelector(FloatingService.this.getApplicationContext(), -12303292));
                tabCheats.setBackground(GlassUI.glassSelector(FloatingService.this.getApplicationContext(), -12303292));
                tabSettings.setBackground(GlassUI.glassSelector(FloatingService.this.getApplicationContext(), -12303292));
                tabForms.setBackground(GlassUI.glassSelector(FloatingService.this.getApplicationContext(), -16750900));
                FloatingService.this.showAdminFormsTab(contentContainer);
            }
        });
        dragHandle.setOnTouchListener(new View.OnTouchListener() { // from class: gtandroid.universal.FloatingService.9
            private float initialTouchX;
            private float initialTouchY;
            private float initialX;
            private float initialY;

            @Override // android.view.View.OnTouchListener
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case 0:
                        this.initialX = menuParams.x;
                        this.initialY = menuParams.y;
                        this.initialTouchX = event.getRawX();
                        this.initialTouchY = event.getRawY();
                        dragHandle.setBackground(GlassUI.glassSelector(FloatingService.this.getApplicationContext(), -10066330));
                        break;
                    case 1:
                        dragHandle.setBackground(GlassUI.glassSelector(FloatingService.this.getApplicationContext(), -12303292));
                        break;
                    case 2:
                        float moveDeltaX = event.getRawX() - this.initialTouchX;
                        float moveDeltaY = event.getRawY() - this.initialTouchY;
                        float newX = this.initialX + moveDeltaX;
                        float newY = this.initialY + moveDeltaY;
                        Point displaySize = new Point();
                        FloatingService.this.windowManager.getDefaultDisplay().getSize(displaySize);
                        int screenWidth = displaySize.x;
                        int screenHeight = displaySize.y;
                        float newX2 = Math.max(0.0f, Math.min(newX, screenWidth - 450));
                        float newY2 = Math.max(0.0f, Math.min(newY, screenHeight - 100));
                        menuParams.x = (int) newX2;
                        menuParams.y = (int) newY2;
                        FloatingService.this.windowManager.updateViewLayout(FloatingService.this.menuView, menuParams);
                        break;
                }
                return true;
            }
        });
    }

    private void showAdminFormsTab(LinearLayout linearLayout) {
        this.adminFormsContainer = linearLayout;
        linearLayout.removeAllViews();

        TextView title = new TextView(this);
        title.setText("ADM форма");
        title.setTextColor(-1);
        title.setTextSize(24.0f);
        title.setGravity(17);
        title.setPadding(0, 10, 0, 10);
        linearLayout.addView(title);

        TextView status = new TextView(this);
        status.setText("COMMAND CENTER  •  READY");
        status.setTextColor(Color.rgb(100, 210, 255));
        status.setTextSize(11.0f);
        status.setGravity(17);
        status.setPadding(0, 0, 0, 15);
        linearLayout.addView(status);

        java.util.ArrayList<AdminFormManager.Form> forms = AdminFormManager.getPending(this);
        if (forms.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Пока нет форм из админ-чата.");
            empty.setTextColor(Color.rgb(190, 180, 205));
            empty.setTextSize(18.0f);
            empty.setGravity(17);
            empty.setPadding(0, 20, 0, 25);
            linearLayout.addView(empty);
        } else {
            ScrollView scroll = new ScrollView(this);
            LinearLayout list = new LinearLayout(this);
            list.setOrientation(LinearLayout.VERTICAL);
            for (final AdminFormManager.Form form : forms) {
                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setPadding(18, 16, 18, 16);
                card.setBackground(GlassUI.glassSelector(getApplicationContext(), Color.rgb(83, 64, 96)));

                TextView source = new TextView(this);
                source.setText("<ADM> (" + form.rank + ") " + form.nickname + "[" + form.id + "]:");
                source.setTextColor(Color.WHITE);
                source.setTextSize(14.0f);
                source.setTypeface(null, Typeface.BOLD);
                card.addView(source);

                TextView command = new TextView(this);
                command.setText(form.command);
                command.setTextColor(Color.rgb(220, 205, 235));
                command.setTextSize(15.0f);
                command.setPadding(0, 8, 0, 12);
                card.addView(command);

                LinearLayout buttons = new LinearLayout(this);
                buttons.setOrientation(LinearLayout.HORIZONTAL);

                Button accept = new Button(this);
                accept.setText("✓ Принять");
                accept.setAllCaps(false);
                accept.setTextColor(Color.WHITE);
                accept.setBackground(GlassUI.lightAccentButton(this, Color.rgb(48, 209, 88)));
                accept.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1.0f));
                accept.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        String accepted = AdminFormManager.makeAcceptedCommand(FloatingService.this, form);
                        if (!MyAccessibilityService.isAvailable()) {
                            Toast.makeText(FloatingService.this,
                                    "Включите службу специальных возможностей GTAndroid", Toast.LENGTH_LONG).show();
                            return;
                        }
                        MyAccessibilityService.insertTextAndSend(accepted);
                        AdminFormManager.removeForm(FloatingService.this, form.uniqueKey());
                        showAdminFormsTab(adminFormsContainer);
                        Toast.makeText(FloatingService.this, "Отправлено: " + accepted, Toast.LENGTH_SHORT).show();
                    }
                });

                Button reject = new Button(this);
                reject.setText("✕ Удалить");
                reject.setAllCaps(false);
                reject.setTextColor(Color.WHITE);
                reject.setBackground(GlassUI.lightAccentButton(this, Color.rgb(255, 69, 58)));
                LinearLayout.LayoutParams rejectParams = new LinearLayout.LayoutParams(0, -2, 1.0f);
                rejectParams.leftMargin = dpToPx(8);
                reject.setLayoutParams(rejectParams);
                reject.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        AdminFormManager.removeForm(FloatingService.this, form.uniqueKey());
                        showAdminFormsTab(adminFormsContainer);
                    }
                });

                buttons.addView(accept);
                buttons.addView(reject);
                card.addView(buttons);
                LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
                cardParams.bottomMargin = dpToPx(10);
                list.addView(card, cardParams);
            }
            scroll.addView(list);
            linearLayout.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1.0f));
        }

        Button refresh = new Button(this);
        refresh.setText("↻ Обновить формы");
        refresh.setAllCaps(false);
        refresh.setTextColor(Color.WHITE);
        refresh.setBackground(GlassUI.glassSelector(getApplicationContext(), Color.rgb(105, 80, 120)));
        refresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAdminFormsTab(adminFormsContainer);
            }
        });
        linearLayout.addView(refresh);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showChatModeTab(LinearLayout linearLayout) {
        linearLayout.removeAllViews();
        TextView title = new TextView(this);
        title.setText("РЕЖИМ ЧАТА");
        title.setTextColor(-1);
        title.setTextSize(16.0f);
        title.setGravity(17);
        title.setPadding(0, 10, 0, 20);
        title.setTypeface(null, 1);
        linearLayout.addView(title);
        RadioGroup radioGroup = new RadioGroup(this);
        radioGroup.setOrientation(1);
        radioGroup.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        RadioButton rbAdmin = new RadioButton(this);
        rbAdmin.setText("📢 Админ чат (/a)");
        rbAdmin.setTextColor(-1);
        rbAdmin.setTextSize(15.0f);
        rbAdmin.setPadding(20, 12, 20, 12);
        rbAdmin.setId(1);
        RadioButton rbNonRp = new RadioButton(this);
        rbNonRp.setText("💬 Нон РП чат (/n)");
        rbNonRp.setTextColor(-1);
        rbNonRp.setTextSize(15.0f);
        rbNonRp.setPadding(20, 12, 20, 12);
        rbNonRp.setId(2);
        RadioButton rbNormal = new RadioButton(this);
        rbNormal.setText("⚪ Обычный режим");
        rbNormal.setTextColor(-1);
        rbNormal.setTextSize(15.0f);
        rbNormal.setPadding(20, 12, 20, 12);
        rbNormal.setId(0);
        radioGroup.addView(rbAdmin);
        radioGroup.addView(rbNonRp);
        radioGroup.addView(rbNormal);
        switch (this.chatMode) {
            case 1:
                rbNonRp.setChecked(true);
                break;
            case 2:
                rbAdmin.setChecked(true);
                break;
            default:
                rbNormal.setChecked(true);
                break;
        }
        radioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() { // from class: gtandroid.universal.FloatingService.10
            @Override // android.widget.RadioGroup.OnCheckedChangeListener
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (checkedId == 1) {
                    FloatingService.this.chatMode = 2;
                    FloatingService.currentChatMode = 2;
                    Toast.makeText(FloatingService.this, "Режим: Админ чат (/a)", 0).show();
                } else if (checkedId == 2) {
                    FloatingService.this.chatMode = 1;
                    FloatingService.currentChatMode = 1;
                    Toast.makeText(FloatingService.this, "Режим: Нон РП чат (/n)", 0).show();
                } else {
                    FloatingService.this.chatMode = 0;
                    FloatingService.currentChatMode = 0;
                    Toast.makeText(FloatingService.this, "Режим: Обычный", 0).show();
                }
                FloatingService.this.prefs.edit().putInt("chat_mode", FloatingService.this.chatMode).apply();
                FloatingService.this.updateIconBackground();
            }
        });
        linearLayout.addView(radioGroup);
        TextView hint = new TextView(this);
        hint.setText("Режим чата не сбрасывается после отправки");
        hint.setTextColor(-5592406);
        hint.setTextSize(11.0f);
        hint.setGravity(17);
        hint.setPadding(10, 20, 10, 10);
        linearLayout.addView(hint);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showCheatsTab(LinearLayout linearLayout) {
        linearLayout.removeAllViews();
        TextView title = new TextView(this);
        title.setText("ЧИТЫ");
        title.setTextColor(-1);
        title.setTextSize(16.0f);
        title.setGravity(17);
        title.setPadding(0, 10, 0, 20);
        title.setTypeface(null, 1);
        linearLayout.addView(title);
        RadioGroup radioGroup = new RadioGroup(this);
        radioGroup.setOrientation(1);
        radioGroup.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        RadioButton rbImmortality = new RadioButton(this);
        rbImmortality.setText("🛡️ Бессмертие (/gm)");
        rbImmortality.setTextColor(-1);
        rbImmortality.setTextSize(15.0f);
        rbImmortality.setPadding(20, 12, 20, 12);
        rbImmortality.setId(1);
        RadioButton rbCarId = new RadioButton(this);
        rbCarId.setText("🚗 ID ТС (/dl)");
        rbCarId.setTextColor(-1);
        rbCarId.setTextSize(15.0f);
        rbCarId.setPadding(20, 12, 20, 12);
        rbCarId.setId(2);
        RadioButton rbMask = new RadioButton(this);
        rbMask.setText("🎭 Маска (/clist)");
        rbMask.setTextColor(-1);
        rbMask.setTextSize(15.0f);
        rbMask.setPadding(20, 12, 20, 12);
        rbMask.setId(3);
        RadioButton rbNone = new RadioButton(this);
        rbNone.setText("⚪ Отключить читы");
        rbNone.setTextColor(-1);
        rbNone.setTextSize(15.0f);
        rbNone.setPadding(20, 12, 20, 12);
        rbNone.setId(0);
        radioGroup.addView(rbImmortality);
        radioGroup.addView(rbCarId);
        radioGroup.addView(rbMask);
        radioGroup.addView(rbNone);
        switch (this.cheatMode) {
            case 1:
                rbImmortality.setChecked(true);
                break;
            case 2:
                rbCarId.setChecked(true);
                break;
            case 3:
                rbMask.setChecked(true);
                break;
            default:
                rbNone.setChecked(true);
                break;
        }
        radioGroup.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() { // from class: gtandroid.universal.FloatingService.11
            @Override // android.widget.RadioGroup.OnCheckedChangeListener
            public void onCheckedChanged(RadioGroup group, int checkedId) {
                if (checkedId == 1) {
                    FloatingService.this.cheatMode = 1;
                    FloatingService.currentCheatMode = 1;
                    Toast.makeText(FloatingService.this, "Чит: Бессмертие (/gm) - одноразовый", 0).show();
                } else if (checkedId == 2) {
                    FloatingService.this.cheatMode = 2;
                    FloatingService.currentCheatMode = 2;
                    Toast.makeText(FloatingService.this, "Чит: ID ТС (/dl) - одноразовый", 0).show();
                } else if (checkedId == 3) {
                    FloatingService.this.cheatMode = 3;
                    FloatingService.currentCheatMode = 3;
                    Toast.makeText(FloatingService.this, "Чит: Маска (/clist) - одноразовый", 0).show();
                } else {
                    FloatingService.this.cheatMode = 0;
                    FloatingService.currentCheatMode = 0;
                }
                FloatingService.this.prefs.edit().putInt("cheat_mode", FloatingService.this.cheatMode).apply();
                FloatingService.this.updateIconBackground();
            }
        });
        linearLayout.addView(radioGroup);
        TextView hint = new TextView(this);
        hint.setText("Выберите чит, затем нажмите Enter на пустом поле.\nПосле отправки чит автоматически сбросится.");
        hint.setTextColor(-5592406);
        hint.setTextSize(11.0f);
        hint.setGravity(17);
        hint.setPadding(10, 20, 10, 10);
        linearLayout.addView(hint);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showSettingsTab(LinearLayout container) {
        container.removeAllViews();
        TextView title = new TextView(this);
        title.setText("НАСТРОЙКИ");
        title.setTextColor(-1);
        title.setTextSize(16.0f);
        title.setGravity(17);
        title.setPadding(0, 10, 0, 20);
        title.setTypeface(null, 1);
        container.addView(title);
        TextView sizeLabel = new TextView(this);
        sizeLabel.setText("📏 Размер клавиатуры");
        sizeLabel.setTextColor(-1);
        sizeLabel.setTextSize(14.0f);
        sizeLabel.setPadding(0, 10, 0, 5);
        container.addView(sizeLabel);
        final String[] sizeNames = {"80%", "100%", "120%", "140%", "160%", "180%", "200%"};
        final TextView sizeValue = new TextView(this);
        sizeValue.setText(sizeNames[this.keyboardSize]);
        sizeValue.setTextColor(-16733441);
        sizeValue.setTextSize(14.0f);
        sizeValue.setGravity(17);
        sizeValue.setPadding(0, 5, 0, 5);
        container.addView(sizeValue);
        SeekBar sizeSeekBar = new SeekBar(this);
        sizeSeekBar.setMax(6);
        sizeSeekBar.setProgress(this.keyboardSize);
        sizeSeekBar.setPadding(10, 5, 10, 5);
        container.addView(sizeSeekBar);
        sizeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() { // from class: gtandroid.universal.FloatingService.12
            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                sizeValue.setText(sizeNames[progress]);
            }

            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onStopTrackingTouch(SeekBar seekBar) {
                FloatingService.this.keyboardSize = seekBar.getProgress();
                FloatingService.this.prefs.edit().putInt("keyboard_size", FloatingService.this.keyboardSize).apply();
                Intent intent = new Intent("KEYBOARD_SIZE_CHANGED");
                FloatingService.this.sendBroadcast(intent);
                Toast.makeText(FloatingService.this, "Размер: " + sizeNames[FloatingService.this.keyboardSize], 0).show();
            }
        });
        View sep1 = new View(this);
        sep1.setLayoutParams(new LinearLayout.LayoutParams(-1, 1));
        sep1.setBackground(GlassUI.glassSelector(getApplicationContext(), -10066330));
        container.addView(sep1);
        TextView transpLabel = new TextView(this);
        transpLabel.setText("🎨 Прозрачность клавиатуры");
        transpLabel.setTextColor(-1);
        transpLabel.setTextSize(14.0f);
        transpLabel.setPadding(0, 15, 0, 5);
        container.addView(transpLabel);
        final TextView transpValue = new TextView(this);
        transpValue.setText(this.keyboardTransparency + "%");
        transpValue.setTextColor(-16733441);
        transpValue.setTextSize(14.0f);
        transpValue.setGravity(17);
        transpValue.setPadding(0, 5, 0, 5);
        container.addView(transpValue);
        SeekBar transpSeekBar = new SeekBar(this);
        transpSeekBar.setMax(100);
        transpSeekBar.setProgress(this.keyboardTransparency);
        transpSeekBar.setPadding(10, 5, 10, 5);
        container.addView(transpSeekBar);
        transpSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() { // from class: gtandroid.universal.FloatingService.13
            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                transpValue.setText(progress + "%");
            }

            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onStopTrackingTouch(SeekBar seekBar) {
                FloatingService.this.keyboardTransparency = seekBar.getProgress();
                FloatingService.this.prefs.edit().putInt("keyboard_transparency", FloatingService.this.keyboardTransparency).apply();
                Intent intent = new Intent("KEYBOARD_TRANSPARENCY_CHANGED");
                FloatingService.this.sendBroadcast(intent);
                Toast.makeText(FloatingService.this, "Прозрачность: " + FloatingService.this.keyboardTransparency + "%", 0).show();
            }
        });
    }

    public static void resetCheatMode() {
        currentCheatMode = 0;
    }
}
