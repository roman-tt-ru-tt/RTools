package gtandroid.universal;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private static final int REQUEST_EXPORT = 888;
    private static final int REQUEST_IMPORT = 777;
    private static final int REQUEST_OVERLAY = 999;

    private Switch switchFloating;
    ArrayList<String> customButtons = new ArrayList<>();
    private boolean isFloatingMenuEnabled = false;
    private ProgressDialog loadingDialog;
    private LinearLayout mainContainer;
    SharedPreferences prefs;
    private SecurityManager securityManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.securityManager = new SecurityManager(this);
        this.prefs = getSharedPreferences("GTAndroidData", 0);
        loadCustomButtons();
        showLoadingSpinner();
        checkSecurityAndStart();
    }

    private void showLoadingSpinner() {
        this.loadingDialog = new ProgressDialog(this);
        this.loadingDialog.setTitle("RTools[+]");
        this.loadingDialog.setMessage("Проверка доступа...");
        this.loadingDialog.setCancelable(false);
        this.loadingDialog.show();
    }

    private void hideLoadingSpinner() {
        if (this.loadingDialog != null && this.loadingDialog.isShowing()) {
            this.loadingDialog.dismiss();
        }
    }

    private void checkSecurityAndStart() {
        this.securityManager.checkAccess(new SecurityManager.SecurityCallback() {
            @Override
            public void onResult(boolean isAllowed, String message) {
                MainActivity.this.hideLoadingSpinner();
                if (isAllowed) {
                    Toast.makeText(MainActivity.this,
                            "С возвращением, " + securityManager.getNickname() + "!",
                            Toast.LENGTH_SHORT).show();
                    MainActivity.this.initUI();
                    return;
                }
                if ("AUTH_REQUIRED".equals(message)) {
                    MainActivity.this.showAuthDialog();
                    return;
                }
                AlertDialog.Builder b = new AlertDialog.Builder(
                        MainActivity.this, R.style.GlassDialogTheme);
                b.setTitle("Доступ запрещён");
                b.setMessage(message != null ? message : "Ошибка");
                b.setCancelable(false);
                b.setPositiveButton("Закрыть", (d, w) -> finish());
                b.show();
            }
        });
    }

    private void showAuthDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 30, 40, 30);

        TextView nickLabel = new TextView(this);
        nickLabel.setText("Никнейм");
        nickLabel.setTextColor(0xFFFFFFFF);
        nickLabel.setTextSize(14f);
        nickLabel.setPadding(0, 0, 0, 6);
        layout.addView(nickLabel);

        final EditText nickInput = new EditText(this);
        nickInput.setHint("Например: PlayerOne");
        nickInput.setInputType(InputType.TYPE_CLASS_TEXT);
        nickInput.setTextColor(0xFFFFFFFF);
        nickInput.setHintTextColor(0xFFA78BFA);
        nickInput.setBackground(GlassUI.glassSelector(this, 0xFF2D1B4E));
        nickInput.setPadding(24, 18, 24, 18);
        layout.addView(nickInput);

        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 30));
        layout.addView(spacer);

        TextView keyLabel = new TextView(this);
        keyLabel.setText("Ключ доступа");
        keyLabel.setTextColor(0xFFFFFFFF);
        keyLabel.setTextSize(14f);
        keyLabel.setPadding(0, 0, 0, 6);
        layout.addView(keyLabel);

        final EditText keyInput = new EditText(this);
        keyInput.setHint("Введите ключ");
        keyInput.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        keyInput.setTextColor(0xFFFFFFFF);
        keyInput.setHintTextColor(0xFFA78BFA);
        keyInput.setBackground(GlassUI.glassSelector(this, 0xFF2D1B4E));
        keyInput.setPadding(24, 18, 24, 18);
        layout.addView(keyInput);

        View spacer2 = new View(this);
        spacer2.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 30));
        layout.addView(spacer2);

        TextView hint = new TextView(this);
        hint.setText("Ник должен быть уникальным.\nЕсли он занят — попробуйте другой.");
        hint.setTextColor(0xFFA78BFA);
        hint.setTextSize(12f);
        hint.setGravity(android.view.Gravity.CENTER);
        layout.addView(hint);

        AlertDialog.Builder builder =
                new AlertDialog.Builder(this, R.style.GlassDialogTheme);
        builder.setTitle("Вход в RTools[+]");
        builder.setView(layout);
        builder.setCancelable(false);
        builder.setPositiveButton("Войти", null);
        builder.setNegativeButton("Выход", (d, w) -> finish());

        final AlertDialog dialog = builder.create();
        dialog.show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String nick = nickInput.getText().toString().trim();
            String key = keyInput.getText().toString().trim();

            if (nick.isEmpty()) {
                Toast.makeText(this, "Введите ник", Toast.LENGTH_SHORT).show();
                return;
            }
            if (key.isEmpty()) {
                Toast.makeText(this, "Введите ключ", Toast.LENGTH_SHORT).show();
                return;
            }

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(false);
            showLoadingSpinner();
            loadingDialog.setMessage("Проверка ника...");

            securityManager.verifyCredentials(nick, key,
                    new SecurityManager.SecurityCallback() {
                        @Override
                        public void onResult(boolean success, String message) {
                            hideLoadingSpinner();
                            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(true);
                            if (success) {
                                dialog.dismiss();
                                Toast.makeText(MainActivity.this,
                                        "Добро пожаловать, " + nick + "!",
                                        Toast.LENGTH_SHORT).show();
                                MainActivity.this.initUI();
                            } else {
                                Toast.makeText(MainActivity.this,
                                        message != null ? message : "Ошибка",
                                        Toast.LENGTH_LONG).show();
                            }
                        }
                    });
        });
    }

    private void initUI() {
        this.mainContainer = new LinearLayout(this);
        this.mainContainer.setOrientation(LinearLayout.VERTICAL);
        this.mainContainer.setBackgroundColor(0xFF1A0B2E);
        this.mainContainer.setPadding(16, 0, 16, 16);

        ScrollView scroll = new ScrollView(this);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(-1, -1));
        scroll.setBackgroundColor(0xFF1A0B2E);
        scroll.addView(this.mainContainer);
        setContentView(scroll);

        showMainScreen();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_main) { showMainScreen(); return true; }
        if (id == R.id.menu_editor) { showEditorScreen(); return true; }
        if (id == R.id.menu_settings) { showSettingsScreen(); return true; }
        return super.onOptionsItemSelected(item);
    }

    private void showMainScreen() {
        this.switchFloating = null;
        this.mainContainer.removeAllViews();

        TextView title = new TextView(this);
        title.setText("RTools[+]");
        title.setTextSize(30f);
        title.setTextColor(0xFFFFFFFF);
        title.setGravity(android.view.Gravity.CENTER);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(0, 60, 0, 24);
        this.mainContainer.addView(title);

        TextView tvStatus = new TextView(this);
        tvStatus.setGravity(android.view.Gravity.CENTER);
        tvStatus.setTextSize(15f);
        tvStatus.setPadding(24, 18, 24, 18);
        tvStatus.setText("Вы вошли как: " + securityManager.getNickname() + "\n" +
                "Ключ: RTOOLS");
        tvStatus.setTextColor(0xFFC4B5FD);
        tvStatus.setBackground(GlassUI.glassSelector(this, 0xFF2D1B4E));
        LinearLayout.LayoutParams statusParams =
                new LinearLayout.LayoutParams(-1, -2);
        statusParams.bottomMargin = 24;
        tvStatus.setLayoutParams(statusParams);
        this.mainContainer.addView(tvStatus);

        Button btnKeyboard = new Button(this);
        btnKeyboard.setText("⌨️ Активировать клавиатуру");
        btnKeyboard.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        btnKeyboard.setPadding(20, 25, 20, 25);
        btnKeyboard.setTextSize(16f);
        btnKeyboard.setTypeface(null, android.graphics.Typeface.BOLD);
        btnKeyboard.setTextColor(0xFFFFFFFF);
        btnKeyboard.setBackground(GlassUI.lightAccentButton(this, 0xFF7C3AED));
        btnKeyboard.setOnClickListener(v -> {
            startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS));
            Toast.makeText(this, "Выбери RTools[+] Keyboard",
                    Toast.LENGTH_LONG).show();
        });
        this.mainContainer.addView(btnKeyboard);

        Button btnSwitchKeyboard = new Button(this);
        btnSwitchKeyboard.setText("🔄 Сменить экранную клавиатуру");
        btnSwitchKeyboard.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        btnSwitchKeyboard.setPadding(20, 25, 20, 25);
        btnSwitchKeyboard.setTextSize(16f);
        btnSwitchKeyboard.setTypeface(null, android.graphics.Typeface.BOLD);
        btnSwitchKeyboard.setTextColor(0xFFFFFFFF);
        btnSwitchKeyboard.setBackground(GlassUI.lightAccentButton(this, 0xFF10B981));
        btnSwitchKeyboard.setOnClickListener(v -> showKeyboardPicker());
        LinearLayout.LayoutParams sk = new LinearLayout.LayoutParams(-1, -2);
        sk.topMargin = 12;
        this.mainContainer.addView(btnSwitchKeyboard, sk);

        Button accessibilityButton = new Button(this);
        accessibilityButton.setText("♿ Включить службу доступности");
        accessibilityButton.setAllCaps(false);
        accessibilityButton.setTextColor(0xFFFFFFFF);
        accessibilityButton.setBackground(GlassUI.lightAccentButton(this, 0xFF5856D6));
        accessibilityButton.setPadding(20, 25, 20, 25);
        accessibilityButton.setTextSize(16f);
        accessibilityButton.setOnClickListener(v -> {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
            Toast.makeText(this,
                    "Включите службу GTAndroid в разделе «Специальные возможности»",
                    Toast.LENGTH_LONG).show();
        });
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(-1, -2);
        ap.topMargin = 12;
        this.mainContainer.addView(accessibilityButton, ap);

        Button btnStats = new Button(this);
        btnStats.setText("📊 Статистика за неделю");
        btnStats.setAllCaps(false);
        btnStats.setTextColor(0xFFFFFFFF);
        btnStats.setTextSize(16f);
        btnStats.setPadding(20, 25, 20, 25);
        btnStats.setBackground(GlassUI.lightAccentButton(this, 0xFFFF9500));
        btnStats.setOnClickListener(v ->
                startActivity(new Intent(this, StatisticsActivity.class)));
        LinearLayout.LayoutParams st = new LinearLayout.LayoutParams(-1, -2);
        st.topMargin = 12;
        this.mainContainer.addView(btnStats, st);

        LinearLayout toggleContainer = new LinearLayout(this);
        toggleContainer.setOrientation(LinearLayout.HORIZONTAL);
        toggleContainer.setGravity(android.view.Gravity.CENTER_VERTICAL);
        toggleContainer.setBackground(GlassUI.glassSelector(this, 0xFF2D1B4E));
        toggleContainer.setPadding(20, 20, 20, 20);
        LinearLayout.LayoutParams tcLp = new LinearLayout.LayoutParams(-1, -2);
        tcLp.topMargin = 30;
        toggleContainer.setLayoutParams(tcLp);

        LinearLayout textBlock = new LinearLayout(this);
        textBlock.setOrientation(LinearLayout.VERTICAL);
        textBlock.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));

        TextView titleFloat = new TextView(this);
        titleFloat.setText("Тулс");
        titleFloat.setTextColor(0xFFFFFFFF);
        titleFloat.setTextSize(20f);
        titleFloat.setTypeface(null, android.graphics.Typeface.BOLD);
        textBlock.addView(titleFloat);

        final TextView subtitle = new TextView(this);
        subtitle.setText(this.isFloatingMenuEnabled ? "Включено" : "Выключено");
        subtitle.setTextColor(this.isFloatingMenuEnabled ? 0xFF10B981 : 0xFFDC2626);
        subtitle.setTextSize(14f);
        subtitle.setPadding(0, 4, 0, 0);
        textBlock.addView(subtitle);

        toggleContainer.addView(textBlock);

        this.switchFloating = new Switch(this);
        this.switchFloating.setChecked(this.isFloatingMenuEnabled);
        this.switchFloating.setShowText(false);
        this.switchFloating.setTrackResource(R.drawable.switch_track);
        this.switchFloating.setThumbResource(R.drawable.switch_thumb);
        this.switchFloating.setOnCheckedChangeListener(
                new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView,
                                                 boolean isChecked) {
                        toggleFloatingMenu(isChecked, subtitle);
                    }
                });
        toggleContainer.addView(this.switchFloating);

        this.mainContainer.addView(toggleContainer);
    }

    private void toggleFloatingMenu(boolean isChecked, TextView subtitle) {
        if (isChecked) {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this,
                        "Разрешите показ поверх других приложений",
                        Toast.LENGTH_LONG).show();
                Intent intent = new Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, REQUEST_OVERLAY);
                if (switchFloating != null) switchFloating.setChecked(false);
                return;
            }
            if (!MyAccessibilityService.isAvailable()) {
                new AlertDialog.Builder(this, R.style.GlassDialogTheme)
                        .setTitle("Нужны специальные возможности")
                        .setMessage("Для работы оверлея включите службу GTAndroid " +
                                "в разделе «Специальные возможности».")
                        .setPositiveButton("Открыть настройки", (d, w) ->
                                startActivity(new Intent(
                                        Settings.ACTION_ACCESSIBILITY_SETTINGS)))
                        .setNegativeButton("Отмена", null)
                        .show();
                if (switchFloating != null) switchFloating.setChecked(false);
                return;
            }
            startService(new Intent(this, FloatingService.class));
            this.isFloatingMenuEnabled = true;
            if (subtitle != null) {
                subtitle.setText("Включено");
                subtitle.setTextColor(0xFF10B981);
            }
        } else {
            stopService(new Intent(this, FloatingService.class));
            this.isFloatingMenuEnabled = false;
            if (subtitle != null) {
                subtitle.setText("Выключено");
                subtitle.setTextColor(0xFFDC2626);
            }
        }
    }

    private void showKeyboardPicker() {
        try {
            InputMethodManager imm = (InputMethodManager)
                    getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showInputMethodPicker();
                Toast.makeText(this,
                        "Выбери RTools[+] Keyboard",
                        Toast.LENGTH_LONG).show();
            } else {
                startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS));
            }
        } catch (Exception e) {
            Toast.makeText(this,
                    "Ошибка: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_OVERLAY) {
            if (Settings.canDrawOverlays(this)) {
                if (!MyAccessibilityService.isAvailable()) {
                    Toast.makeText(this,
                            "Теперь включите службу доступности",
                            Toast.LENGTH_LONG).show();
                    startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                } else {
                    startService(new Intent(this, FloatingService.class));
                    this.isFloatingMenuEnabled = true;
                    if (switchFloating != null) switchFloating.setChecked(true);
                    Toast.makeText(this, "Плавающее меню включено",
                            Toast.LENGTH_SHORT).show();
                }
            }
            return;
        }
        if (resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            if (requestCode == REQUEST_IMPORT) importFromFile(uri);
            else if (requestCode == REQUEST_EXPORT) exportToFile(uri);
        }
    }

    // ==================== РЕДАКТОР ====================

    private void showEditorScreen() {
        this.switchFloating = null;
        this.mainContainer.removeAllViews();

        TextView header = new TextView(this);
        header.setText("Редактор");
        header.setTextSize(24f);
        header.setTextColor(0xFFFFFFFF);
        header.setTypeface(null, android.graphics.Typeface.BOLD);
        header.setGravity(android.view.Gravity.CENTER);
        header.setPadding(0, 30, 0, 30);
        this.mainContainer.addView(header);

        createMenuButton("📝 Редактор наказаний", 0xFF7C3AED, v -> showPunishmentEditor());
        createMenuButton("💬 Редактор быстрого ответа", 0xFF5856D6, v -> showQuickAnswersEditor());
        createMenuButton("🔘 Редактор обычный", 0xFF10B981, v -> showNormalEditor());
        createMenuButton("⚔️ Редактор FamWar", 0xFFAF52DE, v -> showFamWarEditor());
    }

    private void createMenuButton(String text, int color, View.OnClickListener listener) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        btn.setPadding(20, 25, 20, 25);
        btn.setTextSize(16f);
        btn.setTextColor(0xFFFFFFFF);
        btn.setTypeface(null, android.graphics.Typeface.BOLD);
        btn.setAllCaps(false);
        btn.setBackground(GlassUI.lightAccentButton(this, color));
        btn.setOnClickListener(listener);
        this.mainContainer.addView(btn);

        View space = new View(this);
        space.setLayoutParams(new LinearLayout.LayoutParams(-1, 20));
        this.mainContainer.addView(space);
    }

    // ==================== НАСТРОЙКИ ====================

    private void showSettingsScreen() {
        this.switchFloating = null;
        this.mainContainer.removeAllViews();

        TextView header = new TextView(this);
        header.setText("Настройки");
        header.setTextSize(24f);
        header.setTextColor(0xFFFFFFFF);
        header.setTypeface(null, android.graphics.Typeface.BOLD);
        header.setGravity(android.view.Gravity.CENTER);
        header.setPadding(0, 30, 0, 30);
        this.mainContainer.addView(header);

        addSectionTitle("Клавиатура");
        createSettingButton("📏 Размер клавиатуры", 0xFFAF52DE, v -> showKeyboardSizeDialog());
        createSettingButton("🎨 Прозрачность клавиатуры", 0xFF5856D6, v -> showTransparencyDialog());

        final boolean autoSend = this.prefs.getBoolean("send_automatically", true);
        createSettingButton((autoSend ? "🟢" : "⚪") + " Автоотправка: "
                + (autoSend ? "ВКЛ" : "ВЫКЛ"), 0xFF5856D6, v -> {
            boolean next = !MainActivity.this.prefs.getBoolean("send_automatically", true);
            MainActivity.this.prefs.edit().putBoolean("send_automatically", next).apply();
            Toast.makeText(MainActivity.this,
                    next ? "Автоотправка ВКЛ" : "Автоотправка ВЫКЛ",
                    Toast.LENGTH_LONG).show();
            MainActivity.this.showSettingsScreen();
        });

        addSectionTitle("Автоматизация");
        final boolean autoOpen = this.prefs.getBoolean("auto_open_chat_enabled", false);
        createSettingButton((autoOpen ? "🟢" : "⚪") + " Открывать чат автоматически: "
                + (autoOpen ? "ВКЛ" : "ВЫКЛ"), 0xFF00B89A, v -> {
            boolean next = !MainActivity.this.prefs
                    .getBoolean("auto_open_chat_enabled", false);
            MainActivity.this.prefs.edit()
                    .putBoolean("auto_open_chat_enabled", next).apply();
            Toast.makeText(MainActivity.this,
                    next ? "Авто-чат ВКЛ\nНе забудь поставить метку"
                            : "Авто-чат ВЫКЛ",
                    Toast.LENGTH_LONG).show();
            MainActivity.this.showSettingsScreen();
        });

        createSettingButton("📍 Поставить метку для чата", 0xFFF59E0B, v -> {
            startActivity(new Intent(this, MarkPickerActivity.class));
        });

        final float[] mark = MyAccessibilityService.getAutoOpenChatMark();
        TextView markInfo = new TextView(this);
        if (mark != null) {
            markInfo.setText("📍 Метка установлена\n" +
                    "Координаты: X=" + (int) mark[0] + ", Y=" + (int) mark[1]);
            markInfo.setTextColor(0xFF00E5C0);
        } else {
            markInfo.setText("📍 Метка не установлена\nНажмите «Поставить метку»");
            markInfo.setTextColor(0xFFDC2626);
        }
        markInfo.setTextSize(13f);
        markInfo.setGravity(android.view.Gravity.CENTER);
        markInfo.setPadding(20, 15, 20, 15);
        markInfo.setBackground(GlassUI.glassSelector(this, 0xFF2D1B4E));
        LinearLayout.LayoutParams miLp = new LinearLayout.LayoutParams(-1, -2);
        miLp.bottomMargin = 15;
        this.mainContainer.addView(markInfo, miLp);

        addSectionTitle("Отчёты");
        createSettingButton("🗑 Удалить репорты за сегодня", 0xFFDC2626, v -> {
            new AlertDialog.Builder(this, R.style.GlassDialogTheme)
                    .setTitle("Удалить репорты за сегодня?")
                    .setMessage("Все репорты, полученные сегодня, будут удалены, а счётчик обнулён.")
                    .setPositiveButton("Удалить", (d, w) -> clearTodayReports())
                    .setNegativeButton("Отмена", null)
                    .show();
        });

        addSectionTitle("Экспорт / Импорт");
        createSettingButton("📥 Импорт CFG", 0xFF7C3AED, v -> importConfig());
        createSettingButton("📤 Экспорт CFG", 0xFF5856D6, v -> exportConfig());

        addSectionTitle("Админ");
        int currentLevel = getIntPref("admin_level", 1);
        Button btnAdmin = new Button(this);
        btnAdmin.setText("👑 Уровень админа: " + currentLevel);
        btnAdmin.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        btnAdmin.setPadding(20, 20, 20, 20);
        btnAdmin.setTextSize(16f);
        btnAdmin.setTextColor(0xFFFFFFFF);
        btnAdmin.setTypeface(null, android.graphics.Typeface.BOLD);
        btnAdmin.setAllCaps(false);
        btnAdmin.setBackground(GlassUI.lightAccentButton(this, 0xFF10B981));
        btnAdmin.setOnClickListener(v -> showAdminLevelDialog());
        this.mainContainer.addView(btnAdmin);

        View sp = new View(this);
        sp.setLayoutParams(new LinearLayout.LayoutParams(-1, 10));
        this.mainContainer.addView(sp);

        createSettingButton("📨 ADM-формы (список команд)", 0xFFDC2626,
                v -> startActivity(new Intent(this, AdminFormsActivity.class)));

        addSectionTitle("Аккаунт");
        createSettingButton("🚪 Выйти из аккаунта", 0xFFDC2626, v -> {
            new AlertDialog.Builder(MainActivity.this, R.style.GlassDialogTheme)
                    .setTitle("Выход")
                    .setMessage("Уверены? Придётся вводить ник заново.")
                    .setPositiveButton("Выйти", (d, w) -> {
                        securityManager.logout();
                        Toast.makeText(MainActivity.this, "Вы вышли",
                                Toast.LENGTH_SHORT).show();
                        finish();
                    })
                    .setNegativeButton("Отмена", null)
                    .show();
        });
    }

    private void clearTodayReports() {
        try {
            SharedPreferences rp = getSharedPreferences("GTAndroidReports", 0);
            String raw = rp.getString("recent", "[]");
            JSONArray arr = new JSONArray(raw);
            JSONArray out = new JSONArray();

            java.util.Calendar cal = java.util.Calendar.getInstance();
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
            cal.set(java.util.Calendar.MINUTE, 0);
            cal.set(java.util.Calendar.SECOND, 0);
            cal.set(java.util.Calendar.MILLISECOND, 0);
            long todayStart = cal.getTimeInMillis();

            int removed = 0;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                long t = o.optLong("time", 0);
                if (t >= todayStart) { removed++; continue; }
                out.put(o);
            }
            rp.edit().putString("recent", out.toString()).apply();

            SharedPreferences sp = getSharedPreferences("GTAndroidStats", 0);
            SharedPreferences.Editor e = sp.edit();
            e.putInt("total_reports_all_time", 0);
            for (int i = 0; i < 7; i++) e.putInt("weekly_" + i, 0);
            e.apply();

            sendBroadcast(new Intent("REPORT_STATS_RESET"));

            Toast.makeText(this, "✅ Удалено за сегодня: " + removed
                    + "\nСчётчик репортов сброшен", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Ошибка: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void addSectionTitle(String title) {
        TextView tv = new TextView(this);
        tv.setText(title);
        tv.setTextSize(13f);
        tv.setTextColor(0xFFA78BFA);
        tv.setPadding(4, 20, 0, 10);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        this.mainContainer.addView(tv);
    }

    private void createSettingButton(String text, int color, View.OnClickListener l) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        btn.setPadding(20, 15, 20, 15);
        btn.setTextSize(16f);
        btn.setTextColor(0xFFFFFFFF);
        btn.setTypeface(null, android.graphics.Typeface.BOLD);
        btn.setAllCaps(false);
        btn.setBackground(GlassUI.lightAccentButton(this, color));
        btn.setOnClickListener(l);
        this.mainContainer.addView(btn);

        View space = new View(this);
        space.setLayoutParams(new LinearLayout.LayoutParams(-1, 10));
        this.mainContainer.addView(space);
    }

    private int getIntPref(String key, int def) {
        try {
            if (this.prefs.contains(key)) {
                try { return this.prefs.getInt(key, def); }
                catch (ClassCastException e) {
                    String v = this.prefs.getString(key, String.valueOf(def));
                    return Integer.parseInt(v);
                }
            }
        } catch (Exception ignored) { }
        return def;
    }

    // ================== РЕДАКТОР НАКАЗАНИЙ ==================

    private void showPunishmentEditor() {
        final String[] types = {
                "Кик", "Бан", "Варн", "Джаил", "Мут",
                "UnБан", "UnВарн", "UnДжаил", "UnМут", "RМут", "UnRМут"
        };
        new AlertDialog.Builder(this, R.style.GlassDialogTheme)
                .setTitle("ВЫБЕРИТЕ ТИП")
                .setItems(types, (dialog, which) -> showPunishmentTypeEditor(types[which]))
                .show();
    }

    private void showPunishmentTypeEditor(final String typeName) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(20, 20, 20, 20);

        Button btnAdd = new Button(this);
        btnAdd.setText("➕ Добавить");
        btnAdd.setBackground(GlassUI.glassSelector(this, 0xFF10B981));
        btnAdd.setTextColor(0xFFFFFFFF);
        btnAdd.setOnClickListener(v -> showAddPunishmentDialog(typeName));
        layout.addView(btnAdd);

        Button btnList = new Button(this);
        btnList.setText("📋 Список");
        btnList.setBackground(GlassUI.glassSelector(this, 0xFF7C3AED));
        btnList.setTextColor(0xFFFFFFFF);
        btnList.setOnClickListener(v -> showPunishmentsListByType(typeName));
        layout.addView(btnList);

        new AlertDialog.Builder(this, R.style.GlassDialogTheme)
                .setTitle("Редактор: " + typeName)
                .setView(layout)
                .setNegativeButton("Закрыть", null)
                .show();
    }

    private void showAddPunishmentDialog(final String typeName) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(20, 20, 20, 20);

        final EditText etName = new EditText(this);
        etName.setHint("Название");
        etName.setBackground(GlassUI.glassSelector(this, 0xFF2D1B4E));
        etName.setTextColor(0xFFFFFFFF);
        etName.setHintTextColor(0xFFA78BFA);
        etName.setPadding(24, 18, 24, 18);
        layout.addView(etName);

        final EditText etCommand = new EditText(this);
        etCommand.setHint("Команда (например /kick)");
        etCommand.setBackground(GlassUI.glassSelector(this, 0xFF2D1B4E));
        etCommand.setTextColor(0xFFFFFFFF);
        etCommand.setHintTextColor(0xFFA78BFA);
        etCommand.setPadding(24, 18, 24, 18);
        layout.addView(etCommand);

        final EditText etTime = new EditText(this);
        etTime.setHint("Время (опц.)");
        etTime.setBackground(GlassUI.glassSelector(this, 0xFF2D1B4E));
        etTime.setTextColor(0xFFFFFFFF);
        etTime.setHintTextColor(0xFFA78BFA);
        etTime.setPadding(24, 18, 24, 18);
        layout.addView(etTime);

        final EditText etReason = new EditText(this);
        etReason.setHint("Причина (опц.)");
        etReason.setBackground(GlassUI.glassSelector(this, 0xFF2D1B4E));
        etReason.setTextColor(0xFFFFFFFF);
        etReason.setHintTextColor(0xFFA78BFA);
        etReason.setPadding(24, 18, 24, 18);
        layout.addView(etReason);

        final EditText etMin = new EditText(this);
        etMin.setHint("Мин. уровень (1-4)");
        etMin.setInputType(InputType.TYPE_CLASS_NUMBER);
        etMin.setBackground(GlassUI.glassSelector(this, 0xFF2D1B4E));
        etMin.setTextColor(0xFFFFFFFF);
        etMin.setHintTextColor(0xFFA78BFA);
        etMin.setPadding(24, 18, 24, 18);
        layout.addView(etMin);

        new AlertDialog.Builder(this, R.style.GlassDialogTheme)
                .setTitle("Добавить: " + typeName)
                .setView(layout)
                .setPositiveButton("Сохранить", (d, w) -> {
                    String n = etName.getText().toString().trim();
                    String c = etCommand.getText().toString().trim();
                    String t = etTime.getText().toString().trim();
                    String r = etReason.getText().toString().trim();
                    String m = etMin.getText().toString().trim();
                    if (n.isEmpty() || c.isEmpty()) {
                        Toast.makeText(this, "Заполните название и команду!",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (m.isEmpty()) m = "1";
                    savePunishment(typeName, n, c, t, r, m);
                    Toast.makeText(this, "✅ Сохранено!", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void savePunishment(String type, String name, String cmd,
                                String time, String reason, String min) {
        try {
            String key = "punishment_" + type.toLowerCase();
            JSONArray arr = new JSONArray(this.prefs.getString(key, "[]"));
            JSONObject o = new JSONObject();
            o.put("name", name); o.put("command", cmd);
            o.put("time", time); o.put("reason", reason);
            o.put("minlevel", min);
            arr.put(o);
            this.prefs.edit().putString(key, arr.toString()).apply();
        } catch (Exception ignored) { }
    }

    private void showPunishmentsListByType(final String typeName) {
        try {
            String key = "punishment_" + typeName.toLowerCase();
            JSONArray arr = new JSONArray(this.prefs.getString(key, "[]"));
            if (arr.length() == 0) {
                new AlertDialog.Builder(this, R.style.GlassDialogTheme)
                        .setTitle(typeName).setMessage("Список пуст")
                        .setPositiveButton("OK", null).show();
                return;
            }
            ScrollView scroll = new ScrollView(this);
            LinearLayout c = new LinearLayout(this);
            c.setOrientation(LinearLayout.VERTICAL);
            c.setPadding(10, 10, 10, 10);

            for (int i = 0; i < arr.length(); i++) {
                final int idx = i;
                JSONObject o = arr.getJSONObject(i);
                final String name = o.optString("name", "Наказание " + (i + 1));
                final String cmd = o.optString("command", "");
                final String time = o.optString("time", "");
                final String reason = o.optString("reason", "");
                final String min = o.optString("minlevel", "1");

                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setPadding(20, 20, 20, 20);
                card.setBackground(GlassUI.glassSelector(this, 0xFF2D1B4E));

                TextView tv = new TextView(this);
                tv.setText((i + 1) + ". " + name + "\n📝 " + cmd
                        + (time.isEmpty() ? "" : "\n⏱ " + time)
                        + (reason.isEmpty() ? "" : "\n💬 " + reason)
                        + "\n👑 " + min);
                tv.setTextColor(0xFFFFFFFF);
                card.addView(tv);

                LinearLayout btns = new LinearLayout(this);
                btns.setOrientation(LinearLayout.HORIZONTAL);
                Button btnEdit = new Button(this);
                btnEdit.setText("✏️");
                btnEdit.setBackground(GlassUI.glassSelector(this, 0xFF7C3AED));
                btnEdit.setTextColor(0xFFFFFFFF);
                btnEdit.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
                btnEdit.setOnClickListener(v ->
                        showEditPunishmentDialog(typeName, idx, name, cmd, time, reason, min));
                Button btnDel = new Button(this);
                btnDel.setText("🗑️");
                btnDel.setBackground(GlassUI.glassSelector(this, 0xFFDC2626));
                btnDel.setTextColor(0xFFFFFFFF);
                btnDel.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
                btnDel.setOnClickListener(v -> {
                    deletePunishment(typeName, idx);
                    showPunishmentsListByType(typeName);
                });
                btns.addView(btnEdit);
                View sp = new View(this);
                sp.setLayoutParams(new LinearLayout.LayoutParams(10, -1));
                btns.addView(sp);
                btns.addView(btnDel);
                card.addView(btns);
                c.addView(card);

                View sep = new View(this);
                sep.setLayoutParams(new LinearLayout.LayoutParams(-1, 5));
                c.addView(sep);
            }
            scroll.addView(c);
            new AlertDialog.Builder(this, R.style.GlassDialogTheme)
                    .setTitle(typeName.toUpperCase())
                    .setView(scroll)
                    .setPositiveButton("Закрыть", null)
                    .show();
        } catch (Exception ignored) { }
    }

    private void showEditPunishmentDialog(final String typeName, final int index,
                                          String cName, String cCmd, String cTime,
                                          String cReason, String cMin) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(20, 20, 20, 20);

        final EditText etName = new EditText(this);
        etName.setText(cName);
        etName.setBackground(GlassUI.glassSelector(this, 0xFF2D1B4E));
        etName.setTextColor(0xFFFFFFFF);
        layout.addView(etName);

        final EditText etCommand = new EditText(this);
        etCommand.setText(cCmd);
        etCommand.setBackground(GlassUI.glassSelector(this, 0xFF2D1B4E));
        etCommand.setTextColor(0xFFFFFFFF);
        layout.addView(etCommand);

        final EditText etTime = new EditText(this);
        etTime.setText(cTime);
        etTime.setBackground(GlassUI.glassSelector(this, 0xFF2D1B4E));
        etTime.setTextColor(0xFFFFFFFF);
        layout.addView(etTime);

        final EditText etReason = new EditText(this);
        etReason.setText(cReason);
        etReason.setBackground(GlassUI.glassSelector(this, 0xFF2D1B4E));
        etReason.setTextColor(0xFFFFFFFF);
        layout.addView(etReason);

        final EditText etMin = new EditText(this);
        etMin.setText(cMin);
        etMin.setInputType(InputType.TYPE_CLASS_NUMBER);
        etMin.setBackground(GlassUI.glassSelector(this, 0xFF2D1B4E));
        etMin.setTextColor(0xFFFFFFFF);
        layout.addView(etMin);

        new AlertDialog.Builder(this, R.style.GlassDialogTheme)
                .setTitle("Редактировать")
                .setView(layout)
                .setPositiveButton("Сохранить", (d, w) -> {
                    String n = etName.getText().toString().trim();
                    String c = etCommand.getText().toString().trim();
                    String t = etTime.getText().toString().trim();
                    String r = etReason.getText().toString().trim();
                    String m = etMin.getText().toString().trim();
                    if (n.isEmpty() || c.isEmpty()) {
                        Toast.makeText(this, "Заполните поля!",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (m.isEmpty()) m = "1";
                    updatePunishment(typeName, index, n, c, t, r, m);
                    showPunishmentsListByType(typeName);
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void updatePunishment(String type, int idx, String n, String c,
                                  String t, String r, String m) {
        try {
            String key = "punishment_" + type.toLowerCase();
            JSONArray arr = new JSONArray(this.prefs.getString(key, "[]"));
            JSONObject o = new JSONObject();
            o.put("name", n); o.put("command", c);
            o.put("time", t); o.put("reason", r);
            o.put("minlevel", m);
            arr.put(idx, o);
            this.prefs.edit().putString(key, arr.toString()).apply();
        } catch (Exception ignored) { }
    }

    private void deletePunishment(String typeName, int idx) {
        try {
            String key = "punishment_" + typeName.toLowerCase();
            JSONArray arr = new JSONArray(this.prefs.getString(key, "[]"));
            arr.remove(idx);
            this.prefs.edit().putString(key, arr.toString()).apply();
        } catch (Exception ignored) { }
    }

    // ================== БЫСТРЫЕ ОТВЕТЫ ==================

    private void showQuickAnswersEditor() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(20, 20, 20, 20);

        Button btnAdd = new Button(this);
        btnAdd.setText("➕ Добавить ответ");
        btnAdd.setBackground(GlassUI.glassSelector(this, 0xFF10B981));
        btnAdd.setTextColor(0xFFFFFFFF);
        btnAdd.setOnClickListener(v -> showAddQuickAnswerDialog());
        layout.addView(btnAdd);

        Button btnList = new Button(this);
        btnList.setText("📋 Список ответов");
        btnList.setBackground(GlassUI.glassSelector(this, 0xFF7C3AED));
        btnList.setTextColor(0xFFFFFFFF);
        btnList.setOnClickListener(v -> showQuickAnswersList());
        layout.addView(btnList);

        new AlertDialog.Builder(this, R.style.GlassDialogTheme)
                .setTitle("Редактор быстрых ответов")
                .setView(layout)
                .setNegativeButton("Закрыть", null)
                .show();
    }

    private void showAddQuickAnswerDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(20, 20, 20, 20);

        final EditText etName = new EditText(this);
        etName.setHint("Название");
        etName.setBackground(GlassUI.glassSelector(this, 0xFF2D1B4E));
        etName.setTextColor(0xFFFFFFFF);
        etName.setHintTextColor(0xFFA78BFA);
        etName.setPadding(24, 18, 24, 18);
        layout.addView(etName);

        final EditText etCommand = new EditText(this);
        etCommand.setHint("Команда (например /pm)");
        etCommand.setBackground(GlassUI.glassSelector(this, 0xFF2D1B4E));
        etCommand.setTextColor(0xFFFFFFFF);
        etCommand.setHintTextColor(0xFFA78BFA);
        etCommand.setPadding(24, 18, 24, 18);
        layout.addView(etCommand);

        final EditText etAnswer = new EditText(this);
        etAnswer.setHint("Текст ответа");
        etAnswer.setLines(3);
        etAnswer.setBackground(GlassUI.glassSelector(this, 0xFF2D1B4E));
        etAnswer.setTextColor(0xFFFFFFFF);
        etAnswer.setHintTextColor(0xFFA78BFA);
        etAnswer.setPadding(24, 18, 24, 18);
        layout.addView(etAnswer);

        new AlertDialog.Builder(this, R.style.GlassDialogTheme)
                .setTitle("Добавить быстрый ответ")
                .setView(layout)
                .setPositiveButton("Сохранить", (d, w) -> {
                    String n = etName.getText().toString().trim();
                    String c = etCommand.getText().toString().trim();
                    String a = etAnswer.getText().toString().trim();
                    if (n.isEmpty() || a.isEmpty()) {
                        Toast.makeText(this, "Заполните название и ответ!",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    saveQuickAnswer(n, c, a);
                    Toast.makeText(this, "✅ Сохранено!", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void saveQuickAnswer(String name, String cmd, String answer) {
        try {
            JSONArray arr = new JSONArray(this.prefs.getString("quick_answers", "[]"));
            JSONObject o = new JSONObject();
            o.put("name", name); o.put("command", cmd); o.put("answer", answer);
            arr.put(o);
            this.prefs.edit().putString("quick_answers", arr.toString()).apply();
        } catch (Exception ignored) { }
    }

    private void showQuickAnswersList() {
        try {
            JSONArray arr = new JSONArray(this.prefs.getString("quick_answers", "[]"));
            if (arr.length() == 0) {
                new AlertDialog.Builder(this, R.style.GlassDialogTheme)
                        .setTitle("Быстрые ответы").setMessage("Список пуст")
                        .setPositiveButton("OK", null).show();
                return;
            }
            ScrollView scroll = new ScrollView(this);
            LinearLayout c = new LinearLayout(this);
            c.setOrientation(LinearLayout.VERTICAL);
            c.setPadding(10, 10, 10, 10);

            for (int i = 0; i < arr.length(); i++) {
                final int idx = i;
                JSONObject o = arr.getJSONObject(i);
                final String name = o.optString("name", "Ответ" + (i + 1));
                final String cmd = o.optString("command", "");
                final String ans = o.optString("answer", "");

                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setPadding(20, 20, 20, 20);
                card.setBackground(GlassUI.glassSelector(this, 0xFF2D1B4E));

                TextView tv = new TextView(this);
                tv.setText((i + 1) + ". " + name + "\n📝 "
                        + (cmd.isEmpty() ? "(без команды)" : cmd) + "\n💬 " + ans);
                tv.setTextColor(0xFFFFFFFF);
                card.addView(tv);

                LinearLayout btns = new LinearLayout(this);
                btns.setOrientation(LinearLayout.HORIZONTAL);
                Button btnEdit = new Button(this);
                btnEdit.setText("✏️");
                btnEdit.setBackground(GlassUI.glassSelector(this, 0xFF7C3AED));
                btnEdit.setTextColor(0xFFFFFFFF);
                btnEdit.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
                btnEdit.setOnClickListener(v ->
                        showEditQuickAnswerDialog(idx, name, cmd, ans));
                Button btnDel = new Button(this);
                btnDel.setText("🗑️");
                btnDel.setBackground(GlassUI.glassSelector(this, 0xFFDC2626));
                btnDel.setTextColor(0xFFFFFFFF);
                btnDel.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
                btnDel.setOnClickListener(v -> {
                    deleteQuickAnswer(idx);
                    showQuickAnswersList();
                });
                btns.addView(btnEdit);
                View sp = new View(this);
                sp.setLayoutParams(new LinearLayout.LayoutParams(10, -1));
                btns.addView(sp);
                btns.addView(btnDel);
                card.addView(btns);
                c.addView(card);

                View sep = new View(this);
                sep.setLayoutParams(new LinearLayout.LayoutParams(-1, 5));
                c.addView(sep);
            }
            scroll.addView(c);
            new AlertDialog.Builder(this, R.style.GlassDialogTheme)
                    .setTitle("БЫСТРЫЕ ОТВЕТЫ")
                    .setView(scroll)
                    .setPositiveButton("Закрыть", null)
                    .show();
        } catch (Exception ignored) { }
    }

    private void showEditQuickAnswerDialog(final int idx, String cName,
                                           String cCmd, String cAns) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(20, 20, 20, 20);

        final EditText etName = new EditText(this);
        etName.setText(cName);
        etName.setBackground(GlassUI.glassSelector(this, 0xFF2D1B4E));
        etName.setTextColor(0xFFFFFFFF);
        layout.addView(etName);

        final EditText etCommand = new EditText(this);
        etCommand.setText(cCmd);
        etCommand.setBackground(GlassUI.glassSelector(this, 0xFF2D1B4E));
        etCommand.setTextColor(0xFFFFFFFF);
        layout.addView(etCommand);

        final EditText etAnswer = new EditText(this);
        etAnswer.setText(cAns);
        etAnswer.setLines(3);
        etAnswer.setBackground(GlassUI.glassSelector(this, 0xFF2D1B4E));
        etAnswer.setTextColor(0xFFFFFFFF);
        layout.addView(etAnswer);

        new AlertDialog.Builder(this, R.style.GlassDialogTheme)
                .setTitle("Редактировать")
                .setView(layout)
                .setPositiveButton("Сохранить", (d, w) -> {
                    String n = etName.getText().toString().trim();
                    String c = etCommand.getText().toString().trim();
                    String a = etAnswer.getText().toString().trim();
                    if (n.isEmpty() || a.isEmpty()) {
                        Toast.makeText(this, "Заполните поля!",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    updateQuickAnswer(idx, n, c, a);
                    showQuickAnswersList();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void updateQuickAnswer(int idx, String n, String c, String a) {
        try {
            JSONArray arr = new JSONArray(this.prefs.getString("quick_answers", "[]"));
            JSONObject o = new JSONObject();
            o.put("name", n); o.put("command", c); o.put("answer", a);
            arr.put(idx, o);
            this.prefs.edit().putString("quick_answers", arr.toString()).apply();
        } catch (Exception ignored) { }
    }

    private void deleteQuickAnswer(int idx) {
        try {
            JSONArray arr = new JSONArray(this.prefs.getString("quick_answers", "[]"));
            arr.remove(idx);
            this.prefs.edit().putString("quick_answers", arr.toString()).apply();
        } catch (Exception ignored) { }
    }

    // ================== ОБЫЧНЫЕ КНОПКИ ==================

    private void showNormalEditor() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(20, 20, 20, 20);

        Button btnAdd = new Button(this);
        btnAdd.setText("➕ Добавить кнопку");
        btnAdd.setBackground(GlassUI.glassSelector(this, 0xFF10B981));
        btnAdd.setTextColor(0xFFFFFFFF);
        btnAdd.setOnClickListener(v -> showAddButtonDialog());
        layout.addView(btnAdd);

        Button btnList = new Button(this);
        btnList.setText("📋 Список кнопок");
        btnList.setBackground(GlassUI.glassSelector(this, 0xFF7C3AED));
        btnList.setTextColor(0xFFFFFFFF);
        btnList.setOnClickListener(v -> showNormalButtonsList());
        layout.addView(btnList);

        new AlertDialog.Builder(this, R.style.GlassDialogTheme)
                .setTitle("🔘 Редактор обычный")
                .setView(layout)
                .setNegativeButton("Закрыть", null)
                .show();
    }

    private void showAddButtonDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(30, 20, 30, 20);

        final EditText etName = new EditText(this);
        etName.setHint("Название");
        etName.setBackground(GlassUI.glassSelector(this, 0xFF2D1B4E));
        etName.setTextColor(0xFFFFFFFF);
        etName.setHintTextColor(0xFFA78BFA);
        layout.addView(etName);

        final EditText etText = new EditText(this);
        etText.setHint("Текст команды");
        etText.setBackground(GlassUI.glassSelector(this, 0xFF2D1B4E));
        etText.setTextColor(0xFFFFFFFF);
        etText.setHintTextColor(0xFFA78BFA);
        layout.addView(etText);

        new AlertDialog.Builder(this, R.style.GlassDialogTheme)
                .setTitle("Добавить кнопку")
                .setView(layout)
                .setPositiveButton("Сохранить", (d, w) -> {
                    String n = etName.getText().toString();
                    String t = etText.getText().toString();
                    if (!n.isEmpty() && !t.isEmpty()) {
                        saveButton(n, t);
                        loadCustomButtons();
                        Toast.makeText(this, "Сохранено!", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void saveButton(String name, String text) {
        SharedPreferences.Editor editor = this.prefs.edit();
        int count = getIntPref("btn_count", 0);
        editor.putString("btn_" + count + "_name", name);
        editor.putString("btn_" + count + "_text", text);
        editor.putInt("btn_count", count + 1);
        editor.apply();
    }

    private void loadCustomButtons() {
        this.customButtons.clear();
        int count = getIntPref("btn_count", 0);
        for (int i = 0; i < count; i++) {
            String name = this.prefs.getString("btn_" + i + "_name", "");
            String text = this.prefs.getString("btn_" + i + "_text", "");
            this.customButtons.add(name + "|" + text);
        }
    }

    private void showNormalButtonsList() {
        int count = getIntPref("btn_count", 0);
        if (count == 0) {
            new AlertDialog.Builder(this, R.style.GlassDialogTheme)
                    .setTitle("Обычные кнопки").setMessage("Список пуст")
                    .setPositiveButton("OK", null).show();
            return;
        }
        ScrollView scroll = new ScrollView(this);
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(10, 10, 10, 10);

        for (int i = 0; i < count; i++) {
            final int idx = i;
            final String name = this.prefs.getString("btn_" + i + "_name", "Кнопка");
            final String text = this.prefs.getString("btn_" + i + "_text", "");

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(20, 20, 20, 20);
            card.setBackground(GlassUI.glassSelector(this, 0xFF2D1B4E));

            TextView tv = new TextView(this);
            tv.setText((i + 1) + ". " + name + "\n📝 " + text);
            tv.setTextColor(0xFFFFFFFF);
            card.addView(tv);

            LinearLayout btns = new LinearLayout(this);
            btns.setOrientation(LinearLayout.HORIZONTAL);
            Button btnEdit = new Button(this);
            btnEdit.setText("✏️");
            btnEdit.setBackground(GlassUI.glassSelector(this, 0xFF7C3AED));
            btnEdit.setTextColor(0xFFFFFFFF);
            btnEdit.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
            btnEdit.setOnClickListener(v ->
                    showEditNormalButtonDialog(idx, name, text));
            Button btnDel = new Button(this);
            btnDel.setText("🗑️");
            btnDel.setBackground(GlassUI.glassSelector(this, 0xFFDC2626));
            btnDel.setTextColor(0xFFFFFFFF);
            btnDel.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
            btnDel.setOnClickListener(v -> {
                deleteNormalButton(idx);
                loadCustomButtons();
                showNormalButtonsList();
            });
            btns.addView(btnEdit);
            View sp = new View(this);
            sp.setLayoutParams(new LinearLayout.LayoutParams(10, -1));
            btns.addView(sp);
            btns.addView(btnDel);
            card.addView(btns);
            c.addView(card);

            View sep = new View(this);
            sep.setLayoutParams(new LinearLayout.LayoutParams(-1, 5));
            c.addView(sep);
        }
        scroll.addView(c);
        new AlertDialog.Builder(this, R.style.GlassDialogTheme)
                .setTitle("ОБЫЧНЫЕ КНОПКИ")
                .setView(scroll)
                .setPositiveButton("Закрыть", null)
                .show();
    }

    private void showEditNormalButtonDialog(final int idx, String cName, String cText) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(30, 20, 30, 20);

        final EditText etName = new EditText(this);
        etName.setText(cName);
        etName.setBackground(GlassUI.glassSelector(this, 0xFF2D1B4E));
        etName.setTextColor(0xFFFFFFFF);
        layout.addView(etName);

        final EditText etText = new EditText(this);
        etText.setText(cText);
        etText.setBackground(GlassUI.glassSelector(this, 0xFF2D1B4E));
        etText.setTextColor(0xFFFFFFFF);
        layout.addView(etText);

        new AlertDialog.Builder(this, R.style.GlassDialogTheme)
                .setTitle("Редактировать")
                .setView(layout)
                .setPositiveButton("Сохранить", (d, w) -> {
                    String n = etName.getText().toString();
                    String t = etText.getText().toString();
                    if (!n.isEmpty() && !t.isEmpty()) {
                        updateNormalButton(idx, n, t);
                        loadCustomButtons();
                        showNormalButtonsList();
                    }
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void updateNormalButton(int idx, String n, String t) {
        SharedPreferences.Editor editor = this.prefs.edit();
        editor.putString("btn_" + idx + "_name", n);
        editor.putString("btn_" + idx + "_text", t);
        editor.apply();
    }

    private void deleteNormalButton(int idx) {
        int count = getIntPref("btn_count", 0);
        SharedPreferences.Editor editor = this.prefs.edit();
        for (int i = idx; i < count - 1; i++) {
            String nn = this.prefs.getString("btn_" + (i + 1) + "_name", "");
            String nt = this.prefs.getString("btn_" + (i + 1) + "_text", "");
            editor.putString("btn_" + i + "_name", nn);
            editor.putString("btn_" + i + "_text", nt);
        }
        editor.remove("btn_" + (count - 1) + "_name");
        editor.remove("btn_" + (count - 1) + "_text");
        editor.putInt("btn_count", count - 1);
        editor.apply();
    }

    // ================== FAMWAR ==================

    private void showFamWarEditor() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(20, 20, 20, 20);

        Button btnAdd = new Button(this);
        btnAdd.setText("➕ Добавить FamWar");
        btnAdd.setBackground(GlassUI.glassSelector(this, 0xFF10B981));
        btnAdd.setTextColor(0xFFFFFFFF);
        btnAdd.setOnClickListener(v -> showAddFamWarDialog());
        layout.addView(btnAdd);

        Button btnList = new Button(this);
        btnList.setText("📋 Список FamWar");
        btnList.setBackground(GlassUI.glassSelector(this, 0xFF7C3AED));
        btnList.setTextColor(0xFFFFFFFF);
        btnList.setOnClickListener(v -> showFamWarList());
        layout.addView(btnList);

        new AlertDialog.Builder(this, R.style.GlassDialogTheme)
                .setTitle("⚔️ Редактор FamWar")
                .setView(layout)
                .setNegativeButton("Закрыть", null)
                .show();
    }

    private void showAddFamWarDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(20, 20, 20, 20);

        final EditText etName = new EditText(this);
        etName.setHint("Название");
        etName.setBackground(GlassUI.glassSelector(this, 0xFF2D1B4E));
        etName.setTextColor(0xFFFFFFFF);
        etName.setHintTextColor(0xFFA78BFA);
        layout.addView(etName);

        final EditText etCmd = new EditText(this);
        etCmd.setHint("Команда");
        etCmd.setBackground(GlassUI.glassSelector(this, 0xFF2D1B4E));
        etCmd.setTextColor(0xFFFFFFFF);
        etCmd.setHintTextColor(0xFFA78BFA);
        layout.addView(etCmd);

        final EditText etTime = new EditText(this);
        etTime.setHint("Время (опц.)");
        etTime.setBackground(GlassUI.glassSelector(this, 0xFF2D1B4E));
        etTime.setTextColor(0xFFFFFFFF);
        etTime.setHintTextColor(0xFFA78BFA);
        layout.addView(etTime);

        final EditText etReason = new EditText(this);
        etReason.setHint("Причина (опц.)");
        etReason.setBackground(GlassUI.glassSelector(this, 0xFF2D1B4E));
        etReason.setTextColor(0xFFFFFFFF);
        etReason.setHintTextColor(0xFFA78BFA);
        layout.addView(etReason);

        final EditText etMin = new EditText(this);
        etMin.setHint("Мин. уровень (1-4)");
        etMin.setInputType(InputType.TYPE_CLASS_NUMBER);
        etMin.setBackground(GlassUI.glassSelector(this, 0xFF2D1B4E));
        etMin.setTextColor(0xFFFFFFFF);
        etMin.setHintTextColor(0xFFA78BFA);
        layout.addView(etMin);

        new AlertDialog.Builder(this, R.style.GlassDialogTheme)
                .setTitle("Добавить FamWar")
                .setView(layout)
                .setPositiveButton("Сохранить", (d, w) -> {
                    String n = etName.getText().toString().trim();
                    String c = etCmd.getText().toString().trim();
                    String t = etTime.getText().toString().trim();
                    String r = etReason.getText().toString().trim();
                    String m = etMin.getText().toString().trim();
                    if (n.isEmpty() || c.isEmpty()) {
                        Toast.makeText(this, "Заполните поля!",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (m.isEmpty()) m = "1";
                    saveFamWar(n, c, t, r, m);
                    Toast.makeText(this, "✅ Сохранено!", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void saveFamWar(String n, String c, String t, String r, String m) {
        try {
            JSONArray arr = new JSONArray(this.prefs.getString("famwar_commands", "[]"));
            JSONObject o = new JSONObject();
            o.put("name", n); o.put("command", c);
            o.put("time", t); o.put("reason", r);
            o.put("minlevel", m);
            arr.put(o);
            this.prefs.edit().putString("famwar_commands", arr.toString()).apply();
        } catch (Exception ignored) { }
    }

    private void showFamWarList() {
        try {
            JSONArray arr = new JSONArray(this.prefs.getString("famwar_commands", "[]"));
            if (arr.length() == 0) {
                new AlertDialog.Builder(this, R.style.GlassDialogTheme)
                        .setTitle("FamWar").setMessage("Список пуст")
                        .setPositiveButton("OK", null).show();
                return;
            }
            ScrollView scroll = new ScrollView(this);
            LinearLayout c = new LinearLayout(this);
            c.setOrientation(LinearLayout.VERTICAL);
            c.setPadding(10, 10, 10, 10);

            for (int i = 0; i < arr.length(); i++) {
                final int idx = i;
                JSONObject o = arr.getJSONObject(i);
                final String name = o.optString("name", "Команда " + (i + 1));
                final String cmd = o.optString("command", "");
                final String time = o.optString("time", "");
                final String reason = o.optString("reason", "");
                final String min = o.optString("minlevel", "1");

                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setPadding(20, 20, 20, 20);
                card.setBackground(GlassUI.glassSelector(this, 0xFF2D1B4E));

                TextView tv = new TextView(this);
                tv.setText((i + 1) + ". " + name + "\n📝 " + cmd
                        + (time.isEmpty() ? "" : "\n⏱ " + time)
                        + (reason.isEmpty() ? "" : "\n💬 " + reason)
                        + "\n👑 " + min);
                tv.setTextColor(0xFFFFFFFF);
                card.addView(tv);

                LinearLayout btns = new LinearLayout(this);
                btns.setOrientation(LinearLayout.HORIZONTAL);
                Button btnEdit = new Button(this);
                btnEdit.setText("✏️");
                btnEdit.setBackground(GlassUI.glassSelector(this, 0xFF7C3AED));
                btnEdit.setTextColor(0xFFFFFFFF);
                btnEdit.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
                btnEdit.setOnClickListener(v ->
                        showEditFamWarDialog(idx, name, cmd, time, reason, min));
                Button btnDel = new Button(this);
                btnDel.setText("🗑️");
                btnDel.setBackground(GlassUI.glassSelector(this, 0xFFDC2626));
                btnDel.setTextColor(0xFFFFFFFF);
                btnDel.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
                btnDel.setOnClickListener(v -> {
                    deleteFamWar(idx);
                    showFamWarList();
                });
                btns.addView(btnEdit);
                View sp = new View(this);
                sp.setLayoutParams(new LinearLayout.LayoutParams(10, -1));
                btns.addView(sp);
                btns.addView(btnDel);
                card.addView(btns);
                c.addView(card);

                View sep = new View(this);
                sep.setLayoutParams(new LinearLayout.LayoutParams(-1, 5));
                c.addView(sep);
            }
            scroll.addView(c);
            new AlertDialog.Builder(this, R.style.GlassDialogTheme)
                    .setTitle("КОМАНДЫ FAMWAR")
                    .setView(scroll)
                    .setPositiveButton("Закрыть", null)
                    .show();
        } catch (Exception ignored) { }
    }

    private void showEditFamWarDialog(final int idx, String cName, String cCmd,
                                      String cTime, String cReason, String cMin) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(20, 20, 20, 20);

        final EditText etName = new EditText(this);
        etName.setText(cName);
        etName.setBackground(GlassUI.glassSelector(this, 0xFF2D1B4E));
        etName.setTextColor(0xFFFFFFFF);
        layout.addView(etName);

        final EditText etCmd = new EditText(this);
        etCmd.setText(cCmd);
        etCmd.setBackground(GlassUI.glassSelector(this, 0xFF2D1B4E));
        etCmd.setTextColor(0xFFFFFFFF);
        layout.addView(etCmd);

        final EditText etTime = new EditText(this);
        etTime.setText(cTime);
        etTime.setBackground(GlassUI.glassSelector(this, 0xFF2D1B4E));
        etTime.setTextColor(0xFFFFFFFF);
        layout.addView(etTime);

        final EditText etReason = new EditText(this);
        etReason.setText(cReason);
        etReason.setBackground(GlassUI.glassSelector(this, 0xFF2D1B4E));
        etReason.setTextColor(0xFFFFFFFF);
        layout.addView(etReason);

        final EditText etMin = new EditText(this);
        etMin.setText(cMin);
        etMin.setInputType(InputType.TYPE_CLASS_NUMBER);
        etMin.setBackground(GlassUI.glassSelector(this, 0xFF2D1B4E));
        etMin.setTextColor(0xFFFFFFFF);
        layout.addView(etMin);

        new AlertDialog.Builder(this, R.style.GlassDialogTheme)
                .setTitle("Редактировать FamWar")
                .setView(layout)
                .setPositiveButton("Сохранить", (d, w) -> {
                    String n = etName.getText().toString().trim();
                    String c = etCmd.getText().toString().trim();
                    String t = etTime.getText().toString().trim();
                    String r = etReason.getText().toString().trim();
                    String m = etMin.getText().toString().trim();
                    if (n.isEmpty() || c.isEmpty()) {
                        Toast.makeText(this, "Заполните поля!",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (m.isEmpty()) m = "1";
                    updateFamWar(idx, n, c, t, r, m);
                    showFamWarList();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void updateFamWar(int idx, String n, String c, String t, String r, String m) {
        try {
            JSONArray arr = new JSONArray(this.prefs.getString("famwar_commands", "[]"));
            JSONObject o = new JSONObject();
            o.put("name", n); o.put("command", c);
            o.put("time", t); o.put("reason", r);
            o.put("minlevel", m);
            arr.put(idx, o);
            this.prefs.edit().putString("famwar_commands", arr.toString()).apply();
        } catch (Exception ignored) { }
    }

    private void deleteFamWar(int idx) {
        try {
            JSONArray arr = new JSONArray(this.prefs.getString("famwar_commands", "[]"));
            arr.remove(idx);
            this.prefs.edit().putString("famwar_commands", arr.toString()).apply();
        } catch (Exception ignored) { }
    }

    // ================== ДИАЛОГИ ==================

    private void showTransparencyDialog() {
        int current = getIntPref("keyboard_transparency", 100);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(30, 20, 30, 20);

        final TextView tvValue = new TextView(this);
        tvValue.setText("Прозрачность: " + current + "%");
        tvValue.setTextSize(18f);
        tvValue.setTextColor(0xFFFFFFFF);
        tvValue.setGravity(android.view.Gravity.CENTER);
        tvValue.setPadding(0, 0, 0, 20);
        layout.addView(tvValue);

        SeekBar sb = new SeekBar(this);
        sb.setMax(100);
        sb.setProgress(current);
        sb.setPadding(20, 10, 20, 10);
        layout.addView(sb);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int p, boolean f) {
                tvValue.setText("Прозрачность: " + p + "%");
                MainActivity.this.prefs.edit()
                        .putInt("keyboard_transparency", p).apply();
                MainActivity.this.sendBroadcast(
                        new Intent("KEYBOARD_TRANSPARENCY_CHANGED"));
            }
            @Override public void onStartTrackingTouch(SeekBar s) { }
            @Override public void onStopTrackingTouch(SeekBar s) { }
        });

        new AlertDialog.Builder(this, R.style.GlassDialogTheme)
                .setTitle("ПРОЗРАЧНОСТЬ")
                .setView(layout)
                .setPositiveButton("Закрыть", null)
                .show();
    }

    private void showKeyboardSizeDialog() {
        final String[] sizes = {"80%", "100%", "120%", "140%", "160%", "180%", "200%"};
        int current = getIntPref("keyboard_size", 1);
        new AlertDialog.Builder(this, R.style.GlassDialogTheme)
                .setTitle("РАЗМЕР КЛАВИАТУРЫ")
                .setSingleChoiceItems(sizes, current, (d, w) -> {
                    MainActivity.this.prefs.edit()
                            .putInt("keyboard_size", w).apply();
                    Toast.makeText(MainActivity.this,
                            "✅ Размер: " + sizes[w], Toast.LENGTH_SHORT).show();
                    MainActivity.this.sendBroadcast(
                            new Intent("KEYBOARD_SIZE_CHANGED"));
                    d.dismiss();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void showAdminLevelDialog() {
        String[] levels = {"1", "2", "3", "4"};
        new AlertDialog.Builder(this, R.style.GlassDialogTheme)
                .setTitle("Уровень админа")
                .setItems(levels, (d, w) -> {
                    int level = w + 1;
                    MainActivity.this.prefs.edit()
                            .putInt("admin_level", level).apply();
                    Toast.makeText(MainActivity.this,
                            "Уровень: " + level, Toast.LENGTH_SHORT).show();
                    MainActivity.this.showSettingsScreen();
                })
                .show();
    }

    // ================== ИМПОРТ / ЭКСПОРТ ==================

    private void exportConfig() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_TITLE, "RTools_config.cfg");
        startActivityForResult(intent, REQUEST_EXPORT);
    }

    private void importConfig() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        startActivityForResult(intent, REQUEST_IMPORT);
    }

    private void importFromFile(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            SharedPreferences.Editor editor = this.prefs.edit();
            editor.clear();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#") || line.trim().isEmpty()) continue;
                String[] parts = line.split("=", 2);
                if (parts.length == 2) {
                    editor.putString(parts[0].trim(), parts[1].trim());
                }
            }
            reader.close();
            editor.apply();

            fixIntPref("admin_level", 1);
            fixIntPref("btn_count", 0);
            fixIntPref("keyboard_size", 1);
            fixIntPref("keyboard_transparency", 100);

            loadCustomButtons();
            Toast.makeText(this, "✅ Импорт успешен!", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "❌ " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void fixIntPref(String key, int def) {
        try {
            if (this.prefs.contains(key)) {
                String v = this.prefs.getString(key, String.valueOf(def));
                this.prefs.edit().remove(key)
                        .putInt(key, Integer.parseInt(v)).apply();
            }
        } catch (Exception ignored) { }
    }

    private void exportToFile(Uri uri) {
        try {
            OutputStream os = getContentResolver().openOutputStream(uri);
            StringBuilder sb = new StringBuilder("# RTools Config\n\n");
            sb.append("admin_level=").append(getIntPref("admin_level", 1)).append("\n");
            sb.append("btn_count=").append(getIntPref("btn_count", 0)).append("\n");
            int count = getIntPref("btn_count", 0);
            for (int i = 0; i < count; i++) {
                sb.append("btn_").append(i).append("_name=")
                        .append(this.prefs.getString("btn_" + i + "_name", "")).append("\n");
                sb.append("btn_").append(i).append("_text=")
                        .append(this.prefs.getString("btn_" + i + "_text", "")).append("\n");
            }
            sb.append("quick_answers=")
                    .append(this.prefs.getString("quick_answers", "[]")).append("\n");
            sb.append("famwar_commands=")
                    .append(this.prefs.getString("famwar_commands", "[]")).append("\n");
            String[] types = {"кик", "бан", "варн", "джаил", "мут",
                    "unбан", "unварн", "unджаил", "unмут", "рмут", "unрмут"};
            for (String type : types) {
                String key = "punishment_" + type;
                sb.append(key).append("=")
                        .append(this.prefs.getString(key, "[]")).append("\n");
            }
            sb.append("keyboard_size=")
                    .append(getIntPref("keyboard_size", 1)).append("\n");
            sb.append("keyboard_transparency=")
                    .append(getIntPref("keyboard_transparency", 100)).append("\n");
            os.write(sb.toString().getBytes());
            os.close();
            Toast.makeText(this, "✅ Экспорт успешен!", Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            Toast.makeText(this, "❌ " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
                  }
