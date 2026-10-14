package gtandroid.universal;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private static final int REQUEST_EXPORT = 888;
    private static final int REQUEST_IMPORT = 777;
    private static final int REQUEST_OVERLAY = 999;

    private Button btnToggleMenu;
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

                AlertDialog.Builder errorBuilder =
                        new AlertDialog.Builder(MainActivity.this, R.style.GlassDialogTheme);
                errorBuilder.setTitle("Доступ запрещён");
                errorBuilder.setMessage(message != null ? message : "Неизвестная ошибка");
                errorBuilder.setCancelable(false);
                errorBuilder.setPositiveButton("Закрыть", (d, w) ->
                        MainActivity.this.finish());
                errorBuilder.show();
            }
        });
    }

    private void showAuthDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 30, 40, 30);

        TextView nickLabel = new TextView(this);
        nickLabel.setText("Никнейм");
        nickLabel.setTextColor(0xff1c1c1e);
        nickLabel.setTextSize(14f);
        nickLabel.setPadding(0, 0, 0, 6);
        layout.addView(nickLabel);

        final EditText nickInput = new EditText(this);
        nickInput.setHint("Например: PlayerOne");
        nickInput.setInputType(InputType.TYPE_CLASS_TEXT);
        nickInput.setTextColor(0xff1c1c1e);
        nickInput.setHintTextColor(0xff8e8e93);
        nickInput.setBackground(GlassUI.lightCard(this, 10f));
        nickInput.setPadding(24, 18, 24, 18);
        layout.addView(nickInput);

        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 30));
        layout.addView(spacer);

        TextView keyLabel = new TextView(this);
        keyLabel.setText("Ключ доступа");
        keyLabel.setTextColor(0xff1c1c1e);
        keyLabel.setTextSize(14f);
        keyLabel.setPadding(0, 0, 0, 6);
        layout.addView(keyLabel);

        final EditText keyInput = new EditText(this);
        keyInput.setHint("Введите ключ");
        keyInput.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        keyInput.setTextColor(0xff1c1c1e);
        keyInput.setHintTextColor(0xff8e8e93);
        keyInput.setBackground(GlassUI.lightCard(this, 10f));
        keyInput.setPadding(24, 18, 24, 18);
        layout.addView(keyInput);

        View spacer2 = new View(this);
        spacer2.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 30));
        layout.addView(spacer2);

        TextView hint = new TextView(this);
        hint.setText("Ник должен быть уникальным.\nЕсли он занят — попробуйте другой.");
        hint.setTextColor(0xff8e8e93);
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
        this.mainContainer.setBackgroundColor(0xfff2f2f7);
        this.mainContainer.setPadding(16, 0, 16, 16);
        setContentView(this.mainContainer);
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
        // ЗАЩИТА: сбрасываем ссылку перед пересозданием
        this.btnToggleMenu = null;

        this.mainContainer.removeAllViews();

        TextView title = new TextView(this);
        title.setText("RTools[+]");
        title.setTextSize(30f);
        title.setTextColor(0xff1c1c1e);
        title.setGravity(android.view.Gravity.CENTER);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(0, 80, 0, 24);
        this.mainContainer.addView(title);

        TextView tvStatus = new TextView(this);
        tvStatus.setGravity(android.view.Gravity.CENTER);
        tvStatus.setTextSize(15f);
        tvStatus.setPadding(24, 18, 24, 18);
        tvStatus.setText("Вы вошли как: " + securityManager.getNickname() + "\n" +
                "Ключ: RTOOLS");
        tvStatus.setTextColor(0xff0a84ff);
        tvStatus.setBackground(GlassUI.lightCard(this, 16f));
        LinearLayout.LayoutParams statusParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
        statusParams.bottomMargin = 24;
        tvStatus.setLayoutParams(statusParams);
        this.mainContainer.addView(tvStatus);

        // Кнопка 1 — Активировать клавиатуру
        Button btnKeyboard = new Button(this);
        btnKeyboard.setText("⌨️ Активировать клавиатуру");
        btnKeyboard.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        btnKeyboard.setPadding(20, 25, 20, 25);
        btnKeyboard.setTextSize(17f);
        btnKeyboard.setTypeface(null, android.graphics.Typeface.BOLD);
        btnKeyboard.setTextColor(0xff0a84ff);
        btnKeyboard.setBackground(GlassUI.lightAccentButton(this, 0xff0a84ff));
        btnKeyboard.setOnClickListener(v -> {
            Intent intent = new Intent(
                    android.provider.Settings.ACTION_INPUT_METHOD_SETTINGS);
            startActivity(intent);
            Toast.makeText(MainActivity.this,
                    "Выбери RTools[+] Keyboard", Toast.LENGTH_LONG).show();
        });
        this.mainContainer.addView(btnKeyboard);

        // Кнопка 2 — Сменить экранную клавиатуру
        Button btnSwitchKeyboard = new Button(this);
        btnSwitchKeyboard.setText("🔄 Сменить экранную клавиатуру");
        btnSwitchKeyboard.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        btnSwitchKeyboard.setPadding(20, 25, 20, 25);
        btnSwitchKeyboard.setTextSize(17f);
        btnSwitchKeyboard.setTypeface(null, android.graphics.Typeface.BOLD);
        btnSwitchKeyboard.setTextColor(0xff30d158);
        btnSwitchKeyboard.setBackground(GlassUI.lightAccentButton(this, 0xff30d158));
        btnSwitchKeyboard.setOnClickListener(v -> showKeyboardPicker());
        LinearLayout.LayoutParams switchParams = new LinearLayout.LayoutParams(-1, -2);
        switchParams.topMargin = 12;
        this.mainContainer.addView(btnSwitchKeyboard, switchParams);

        // Кнопка 3 — Служба доступности
        Button accessibilityButton = new Button(this);
        accessibilityButton.setText("♿ Включить службу доступности");
        accessibilityButton.setAllCaps(false);
        accessibilityButton.setTextColor(0xff7547D9);
        accessibilityButton.setBackground(GlassUI.lightAccentButton(this, 0xFF7547D9));
        accessibilityButton.setPadding(20, 25, 20, 25);
        accessibilityButton.setTextSize(16f);
        accessibilityButton.setOnClickListener(v -> {
            Intent intent = new Intent(
                    android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
            Toast.makeText(MainActivity.this,
                    "Включите службу GTAndroid в разделе «Специальные возможности»",
                    Toast.LENGTH_LONG).show();
        });
        LinearLayout.LayoutParams accParams =
                new LinearLayout.LayoutParams(-1, -2);
        accParams.topMargin = 12;
        this.mainContainer.addView(accessibilityButton, accParams);

        // Кнопка 4 — Тест производительности (вместо Ловли сообщений)
        Button btnPerfTest = new Button(this);
        btnPerfTest.setText("⚡ Тест производительности");
        btnPerfTest.setAllCaps(false);
        btnPerfTest.setTextColor(0xffffffff);
        btnPerfTest.setTextSize(16f);
        btnPerfTest.setPadding(20, 25, 20, 25);
        btnPerfTest.setBackground(GlassUI.lightAccentButton(this, 0xFFFF9500));
        btnPerfTest.setOnClickListener(v ->
                startActivity(new Intent(this, PerfTestActivity.class)));
        LinearLayout.LayoutParams perfParams = new LinearLayout.LayoutParams(-1, -2);
        perfParams.topMargin = 12;
        this.mainContainer.addView(btnPerfTest, perfParams);

        View spacer = new View(this);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(-1, 40));
        this.mainContainer.addView(spacer);

        LinearLayout toggleContainer = new LinearLayout(this);
        toggleContainer.setOrientation(LinearLayout.VERTICAL);
        toggleContainer.setGravity(android.view.Gravity.CENTER);
        this.mainContainer.addView(toggleContainer);

        // Создаём кнопку BEFORE применения стиля
        this.btnToggleMenu = new Button(this);
        int circleSize = (int) (getResources().getDisplayMetrics().density * 160f);
        this.btnToggleMenu.setLayoutParams(
                new LinearLayout.LayoutParams(circleSize, circleSize));
        updateToggleButtonStyle();
        this.btnToggleMenu.setOnClickListener(v -> toggleFloatingMenu());
        toggleContainer.addView(this.btnToggleMenu);

        TextView toggleLabel = new TextView(this);
        toggleLabel.setText("Плавающее меню");
        toggleLabel.setTextSize(15f);
        toggleLabel.setTextColor(0xff8e8e93);
        toggleLabel.setGravity(android.view.Gravity.CENTER);
        toggleLabel.setPadding(0, 15, 0, 0);
        toggleContainer.addView(toggleLabel);
    }

    // ==================== ВЫБОР КЛАВИАТУРЫ ====================

    private void showKeyboardPicker() {
        try {
            InputMethodManager imm = (InputMethodManager)
                    getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.showInputMethodPicker();
                Toast.makeText(this,
                        "Выбери RTools[+] Keyboard в открывшемся списке",
                        Toast.LENGTH_LONG).show();
            } else {
                startActivity(new Intent(
                        android.provider.Settings.ACTION_INPUT_METHOD_SETTINGS));
            }
        } catch (Exception e) {
            Toast.makeText(this,
                    "Не удалось открыть выбор клавиатуры: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void updateToggleButtonStyle() {
        // ЗАЩИТА ОТ КРАША
        if (this.btnToggleMenu == null) return;
        int accent = this.isFloatingMenuEnabled ? 0xff30d158 : 0xff0a84ff;
        this.btnToggleMenu.setBackground(GlassUI.glassOval(this, accent));
        this.btnToggleMenu.setText(this.isFloatingMenuEnabled ? "ON" : "OFF");
        this.btnToggleMenu.setTextColor(0xffffffff);
        this.btnToggleMenu.setTextSize(26f);
        this.btnToggleMenu.setTypeface(null, android.graphics.Typeface.BOLD);
        this.btnToggleMenu.setGravity(android.view.Gravity.CENTER);
    }

    private void toggleFloatingMenu() {
        // ЗАЩИТА: если кнопки нет — пересоздаём UI
        if (this.btnToggleMenu == null) {
            if (this.mainContainer != null) {
                showMainScreen();
            }
            return;
        }

        if (this.isFloatingMenuEnabled) {
            stopService(new Intent(this, FloatingService.class));
            this.isFloatingMenuEnabled = false;
            updateToggleButtonStyle();
            return;
        }

        // 1. Overlay permission
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this,
                    "Разрешите показ поверх других приложений",
                    Toast.LENGTH_LONG).show();
            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQUEST_OVERLAY);
            return;
        }

        // 2. Accessibility
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
            return;
        }

        startService(new Intent(this, FloatingService.class));
        this.isFloatingMenuEnabled = true;
        updateToggleButtonStyle();
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
                    startActivity(new Intent(
                            Settings.ACTION_ACCESSIBILITY_SETTINGS));
                } else {
                    startService(new Intent(this, FloatingService.class));
                    this.isFloatingMenuEnabled = true;
                    updateToggleButtonStyle();
                    Toast.makeText(this, "Плавающее меню включено",
                            Toast.LENGTH_SHORT).show();
                }
            }
            return;
        }
        if (resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            if (requestCode == REQUEST_IMPORT) {
                importFromFile(uri);
            } else if (requestCode == REQUEST_EXPORT) {
                exportToFile(uri);
            }
        }
    }

    // ==================== РЕДАКТОР ====================

    private void showEditorScreen() {
        this.btnToggleMenu = null;

        this.mainContainer.removeAllViews();
        TextView header = new TextView(this);
        header.setText("Редактор");
        header.setTextSize(24f);
        header.setTextColor(0xff1c1c1e);
        header.setTypeface(null, android.graphics.Typeface.BOLD);
        header.setGravity(android.view.Gravity.CENTER);
        header.setPadding(0, 30, 0, 30);
        this.mainContainer.addView(header);

        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(30, 0, 30, 30);
        scroll.addView(content);
        this.mainContainer.addView(scroll);

        createEditorButton(content, "📝 Редактор наказаний", 0xff0a84ff, v -> showPunishmentEditor());
        createEditorButton(content, "💬 Редактор быстрого ответа", 0xff7547D9, v -> showQuickAnswersEditor());
        createEditorButton(content, "🔘 Редактор обычный", 0xff30d158, v -> showNormalEditor());
        createEditorButton(content, "⚔️ Редактор FamWar", 0xffaf52de, v -> showFamWarEditor());
    }

    private void createEditorButton(LinearLayout parent, String text, int color,
                                    View.OnClickListener listener) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        btn.setPadding(20, 25, 20, 25);
        btn.setTextSize(16f);
        btn.setTextColor(color);
        btn.setTypeface(null, android.graphics.Typeface.BOLD);
        btn.setAllCaps(false);
        btn.setBackground(GlassUI.lightAccentButton(this, color));
        btn.setOnClickListener(listener);
        parent.addView(btn);

        View space = new View(this);
        space.setLayoutParams(new LinearLayout.LayoutParams(-1, 20));
        parent.addView(space);
    }

    // ==================== НАСТРОЙКИ ====================

    private void showSettingsScreen() {
        this.btnToggleMenu = null;

        this.mainContainer.removeAllViews();
        TextView header = new TextView(this);
        header.setText("Настройки");
        header.setTextSize(24f);
        header.setTextColor(0xff1c1c1e);
        header.setTypeface(null, android.graphics.Typeface.BOLD);
        header.setGravity(android.view.Gravity.CENTER);
        header.setPadding(0, 30, 0, 30);
        this.mainContainer.addView(header);

        ScrollView scrollView = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(30, 0, 30, 30);
        scrollView.addView(content);
        this.mainContainer.addView(scrollView);

        addSectionTitle(content, "Клавиатура");
        createSettingButton(content, "📏 Размер клавиатуры", 0xffaf52de,
                v -> showKeyboardSizeDialog());
        createSettingButton(content, "🎨 Прозрачность клавиатуры", 0xff5856d6,
                v -> showTransparencyDialog());

        final boolean autoSendEnabled = this.prefs.getBoolean("send_automatically", true);
        createSettingButton(content,
                (autoSendEnabled ? "🟢" : "⚪") + " Автоотправка: " +
                        (autoSendEnabled ? "ВКЛ" : "ВЫКЛ"),
                0xff5856d6, v -> {
                    boolean next = !MainActivity.this.prefs
                            .getBoolean("send_automatically", true);
                    MainActivity.this.prefs.edit()
                            .putBoolean("send_automatically", next).apply();
                    Toast.makeText(MainActivity.this,
                            next ? "Автоотправка включена"
                                    : "Автоотправка выключена",
                            Toast.LENGTH_LONG).show();
                    MainActivity.this.showSettingsScreen();
                });

        addSectionTitle(content, "Экспорт / Импорт");
        createSettingButton(content, "📥 Импорт CFG", 0xff0a84ff,
                v -> importConfig());
        createSettingButton(content, "📤 Экспорт CFG", 0xff7547D9,
                v -> exportConfig());

        addSectionTitle(content, "Админ");
        int currentLevel = getIntPref("admin_level", 1);
        Button btnAdmin = new Button(this);
        btnAdmin.setText("👑 Уровень админа: " + currentLevel);
        btnAdmin.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        btnAdmin.setPadding(20, 20, 20, 20);
        btnAdmin.setTextSize(16f);
        btnAdmin.setTextColor(0xff30d158);
        btnAdmin.setTypeface(null, android.graphics.Typeface.BOLD);
        btnAdmin.setAllCaps(false);
        btnAdmin.setBackground(GlassUI.lightAccentButton(this, 0xff30d158));
        btnAdmin.setOnClickListener(v -> showAdminLevelDialog());
        content.addView(btnAdmin);

        View sp = new View(this);
        sp.setLayoutParams(new LinearLayout.LayoutParams(-1, 10));
        content.addView(sp);

        createSettingButton(content, "📨 ADM-формы (список команд)", 0xffff453a,
                v -> startActivity(new Intent(this, AdminFormsActivity.class)));

        addSectionTitle(content, "Аккаунт");
        createSettingButton(content, "🚪 Выйти из аккаунта", 0xffff453a,
                v -> {
                    new AlertDialog.Builder(MainActivity.this,
                            R.style.GlassDialogTheme)
                            .setTitle("Выход")
                            .setMessage("Уверены? Придётся вводить ник заново.")
                            .setPositiveButton("Выйти", (d, w) -> {
                                securityManager.logout();
                                Toast.makeText(MainActivity.this,
                                        "Вы вышли", Toast.LENGTH_SHORT).show();
                                finish();
                            })
                            .setNegativeButton("Отмена", null)
                            .show();
                });
    }

    private void addSectionTitle(LinearLayout parent, String title) {
        TextView tv = new TextView(this);
        tv.setText(title);
        tv.setTextSize(13f);
        tv.setTextColor(0xff8e8e93);
        tv.setPadding(4, 20, 0, 10);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        parent.addView(tv);
    }

    private void createSettingButton(LinearLayout parent, String text,
                                     int color, View.OnClickListener l) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        btn.setPadding(20, 15, 20, 15);
        btn.setTextSize(16f);
        btn.setTextColor(color);
        btn.setTypeface(null, android.graphics.Typeface.BOLD);
        btn.setAllCaps(false);
        btn.setBackground(GlassUI.lightAccentButton(this, color));
        btn.setOnClickListener(l);
        parent.addView(btn);

        View space = new View(this);
        space.setLayoutParams(new LinearLayout.LayoutParams(-1, 10));
        parent.addView(space);
    }

    private int getIntPref(String key, int defaultValue) {
        try {
            if (this.prefs.contains(key)) {
                try {
                    return this.prefs.getInt(key, defaultValue);
                } catch (ClassCastException e) {
                    String strValue = this.prefs.getString(
                            key, String.valueOf(defaultValue));
                    return Integer.parseInt(strValue);
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        return defaultValue;
    }

    // ================== РЕДАКТОР НАКАЗАНИЙ ==================

    private void showPunishmentEditor() {
        final String[] punishmentTypes = {
                "Кик", "Бан", "Варн", "Джаил", "Мут",
                "UnБан", "UnВарн", "UnДжаил", "UnМут", "RМут", "UnRМут"
        };
        new AlertDialog.Builder(this, R.style.GlassDialogTheme)
                .setTitle("ВЫБЕРИТЕ ТИП")
                .setItems(punishmentTypes, (dialog, which) ->
                        showPunishmentTypeEditor(punishmentTypes[which]))
                .show();
    }

    private void showPunishmentTypeEditor(final String typeName) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(20, 20, 20, 20);

        Button btnAdd = new Button(this);
        btnAdd.setText("➕ Добавить");
        btnAdd.setBackground(GlassUI.glassSelector(getApplicationContext(), 0xff30d158));
        btnAdd.setTextColor(0xffffffff);
        btnAdd.setOnClickListener(v -> showAddPunishmentDialog(typeName));
        layout.addView(btnAdd);

        Button btnList = new Button(this);
        btnList.setText("📋 Список");
        btnList.setBackground(GlassUI.glassSelector(getApplicationContext(), 0xff7547D9));
        btnList.setTextColor(0xffffffff);
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
        etName.setHint("Название (например: Помеха)");
        etName.setBackground(GlassUI.lightCard(this, 10f));
        etName.setPadding(24, 18, 24, 18);
        layout.addView(etName);

        final EditText etCommand = new EditText(this);
        etCommand.setHint("Команда (например: /kick)");
        etCommand.setBackground(GlassUI.lightCard(this, 10f));
        etCommand.setPadding(24, 18, 24, 18);
        layout.addView(etCommand);

        final EditText etTime = new EditText(this);
        etTime.setHint("Время (если нужно)");
        etTime.setBackground(GlassUI.lightCard(this, 10f));
        etTime.setPadding(24, 18, 24, 18);
        layout.addView(etTime);

        final EditText etReason = new EditText(this);
        etReason.setHint("Причина (например: 5.2 ОП)");
        etReason.setBackground(GlassUI.lightCard(this, 10f));
        etReason.setPadding(24, 18, 24, 18);
        layout.addView(etReason);

        final EditText etMinLevel = new EditText(this);
        etMinLevel.setHint("Мин. уровень (1-4)");
        etMinLevel.setInputType(InputType.TYPE_CLASS_NUMBER);
        etMinLevel.setBackground(GlassUI.lightCard(this, 10f));
        etMinLevel.setPadding(24, 18, 24, 18);
        layout.addView(etMinLevel);

        new AlertDialog.Builder(this, R.style.GlassDialogTheme)
                .setTitle("Добавить: " + typeName)
                .setView(layout)
                .setPositiveButton("Сохранить", (d, w) -> {
                    String name = etName.getText().toString().trim();
                    String command = etCommand.getText().toString().trim();
                    String time = etTime.getText().toString().trim();
                    String reason = etReason.getText().toString().trim();
                    String minLevel = etMinLevel.getText().toString().trim();
                    if (name.isEmpty() || command.isEmpty()) {
                        Toast.makeText(this, "Заполните название и команду!",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (minLevel.isEmpty()) minLevel = "1";
                    savePunishment(typeName, name, command, time, reason, minLevel);
                    Toast.makeText(this, "✅ Сохранено!", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void savePunishment(String type, String name, String command,
                                String time, String reason, String minLevel) {
        try {
            String key = "punishment_" + type.toLowerCase();
            JSONArray arr = new JSONArray(this.prefs.getString(key, "[]"));
            JSONObject o = new JSONObject();
            o.put("name", name);
            o.put("command", command);
            o.put("time", time);
            o.put("reason", reason);
            o.put("minlevel", minLevel);
            arr.put(o);
            this.prefs.edit().putString(key, arr.toString()).apply();
        } catch (Exception e) {
            Toast.makeText(this, "Ошибка: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
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
            LinearLayout container = new LinearLayout(this);
            container.setOrientation(LinearLayout.VERTICAL);
            container.setPadding(10, 10, 10, 10);

            for (int i = 0; i < arr.length(); i++) {
                final int index = i;
                JSONObject o = arr.getJSONObject(i);
                final String name = o.optString("name", "Наказание " + (i + 1));
                final String command = o.optString("command", "");
                final String time = o.optString("time", "");
                final String reason = o.optString("reason", "");
                final String minLevel = o.optString("minlevel", "1");

                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setPadding(20, 20, 20, 20);
                card.setBackground(GlassUI.glassSelector(getApplicationContext(), 0xffe5e5ea));

                TextView tvInfo = new TextView(this);
                StringBuilder sb = new StringBuilder();
                sb.append(i + 1).append(". ").append(name).append("\n");
                sb.append("📝 ").append(command).append("\n");
                if (!time.isEmpty()) sb.append("⏱ ").append(time).append("\n");
                if (!reason.isEmpty()) sb.append("💬 ").append(reason).append("\n");
                sb.append("👑 ").append(minLevel);
                tvInfo.setText(sb.toString());
                tvInfo.setTextColor(0xff1c1c1e);
                card.addView(tvInfo);

                LinearLayout btns = new LinearLayout(this);
                btns.setOrientation(LinearLayout.HORIZONTAL);
                Button btnEdit = new Button(this);
                btnEdit.setText("✏️");
                btnEdit.setBackground(GlassUI.glassSelector(getApplicationContext(), 0xff7547D9));
                btnEdit.setTextColor(0xffffffff);
                btnEdit.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
                btnEdit.setOnClickListener(v ->
                        showEditPunishmentDialog(typeName, index, name, command, time, reason, minLevel));
                Button btnDel = new Button(this);
                btnDel.setText("🗑️");
                btnDel.setBackground(GlassUI.glassSelector(getApplicationContext(), 0xffff453a));
                btnDel.setTextColor(0xffffffff);
                btnDel.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
                btnDel.setOnClickListener(v -> {
                    deletePunishment(typeName, index);
                    showPunishmentsListByType(typeName);
                });
                btns.addView(btnEdit);
                View sep = new View(this);
                sep.setLayoutParams(new LinearLayout.LayoutParams(10, -1));
                btns.addView(sep);
                btns.addView(btnDel);
                card.addView(btns);
                container.addView(card);

                View sp = new View(this);
                sp.setLayoutParams(new LinearLayout.LayoutParams(-1, 5));
                container.addView(sp);
            }
            scroll.addView(container);
            new AlertDialog.Builder(this, R.style.GlassDialogTheme)
                    .setTitle(typeName.toUpperCase())
                    .setView(scroll)
                    .setPositiveButton("Закрыть", null)
                    .show();
        } catch (Exception e) {
            Toast.makeText(this, "Ошибка: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void showEditPunishmentDialog(final String typeName, final int index,
                                          String cName, String cCmd, String cTime,
                                          String cReason, String cMin) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(20, 20, 20, 20);

        final EditText etName = new EditText(this);
        etName.setText(cName);
        etName.setBackground(GlassUI.lightCard(this, 10f));
        etName.setPadding(24, 18, 24, 18);
        layout.addView(etName);

        final EditText etCommand = new EditText(this);
        etCommand.setText(cCmd);
        etCommand.setBackground(GlassUI.lightCard(this, 10f));
        etCommand.setPadding(24, 18, 24, 18);
        layout.addView(etCommand);

        final EditText etTime = new EditText(this);
        etTime.setText(cTime);
        etTime.setBackground(GlassUI.lightCard(this, 10f));
        etTime.setPadding(24, 18, 24, 18);
        layout.addView(etTime);

        final EditText etReason = new EditText(this);
        etReason.setText(cReason);
        etReason.setBackground(GlassUI.lightCard(this, 10f));
        etReason.setPadding(24, 18, 24, 18);
        layout.addView(etReason);

        final EditText etMin = new EditText(this);
        etMin.setText(cMin);
        etMin.setInputType(InputType.TYPE_CLASS_NUMBER);
        etMin.setBackground(GlassUI.lightCard(this, 10f));
        etMin.setPadding(24, 18, 24, 18);
        layout.addView(etMin);

        new AlertDialog.Builder(this, R.style.GlassDialogTheme)
                .setTitle("Редактировать: " + typeName)
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
                    updatePunishment(typeName, index, n, c, t, r, m);
                    showPunishmentsListByType(typeName);
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void updatePunishment(String type, int index, String name,
                                  String command, String time,
                                  String reason, String minLevel) {
        try {
            String key = "punishment_" + type.toLowerCase();
            JSONArray arr = new JSONArray(this.prefs.getString(key, "[]"));
            JSONObject o = new JSONObject();
            o.put("name", name);
            o.put("command", command);
            o.put("time", time);
            o.put("reason", reason);
            o.put("minlevel", minLevel);
            arr.put(index, o);
            this.prefs.edit().putString(key, arr.toString()).apply();
        } catch (Exception e) {
            Toast.makeText(this, "Ошибка: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void deletePunishment(String typeName, int index) {
        try {
            String key = "punishment_" + typeName.toLowerCase();
            JSONArray arr = new JSONArray(this.prefs.getString(key, "[]"));
            arr.remove(index);
            this.prefs.edit().putString(key, arr.toString()).apply();
        } catch (Exception e) {
            Toast.makeText(this, "Ошибка: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // ================== БЫСТРЫЕ ОТВЕТЫ ==================

    private void showQuickAnswersEditor() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(20, 20, 20, 20);

        Button btnAdd = new Button(this);
        btnAdd.setText("➕ Добавить ответ");
        btnAdd.setBackground(GlassUI.glassSelector(getApplicationContext(), 0xff30d158));
        btnAdd.setTextColor(0xffffffff);
        btnAdd.setOnClickListener(v -> showAddQuickAnswerDialog());
        layout.addView(btnAdd);

        Button btnList = new Button(this);
        btnList.setText("📋 Список ответов");
        btnList.setBackground(GlassUI.glassSelector(getApplicationContext(), 0xff7547D9));
        btnList.setTextColor(0xffffffff);
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
        etName.setHint("Название (например: Приветствие)");
        etName.setBackground(GlassUI.lightCard(this, 10f));
        etName.setPadding(24, 18, 24, 18);
        layout.addView(etName);

        final EditText etCommand = new EditText(this);
        etCommand.setHint("Команда (например /pm) — можно пусто");
        etCommand.setBackground(GlassUI.lightCard(this, 10f));
        etCommand.setPadding(24, 18, 24, 18);
        layout.addView(etCommand);

        final EditText etAnswer = new EditText(this);
        etAnswer.setHint("Текст ответа");
        etAnswer.setLines(3);
        etAnswer.setBackground(GlassUI.lightCard(this, 10f));
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

    private void saveQuickAnswer(String name, String command, String answer) {
        try {
            JSONArray arr = new JSONArray(this.prefs.getString("quick_answers", "[]"));
            JSONObject o = new JSONObject();
            o.put("name", name);
            o.put("command", command);
            o.put("answer", answer);
            arr.put(o);
            this.prefs.edit().putString("quick_answers", arr.toString()).apply();
        } catch (Exception e) {
            Toast.makeText(this, "Ошибка: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
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
            LinearLayout container = new LinearLayout(this);
            container.setOrientation(LinearLayout.VERTICAL);
            container.setPadding(10, 10, 10, 10);

            for (int i = 0; i < arr.length(); i++) {
                final int index = i;
                JSONObject o = arr.getJSONObject(i);
                final String name = o.optString("name", "Ответ" + (i + 1));
                final String command = o.optString("command", "");
                final String answer = o.optString("answer", "");

                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setPadding(20, 20, 20, 20);
                card.setBackground(GlassUI.glassSelector(getApplicationContext(), 0xffe5e5ea));

                TextView tv = new TextView(this);
                tv.setText((i + 1) + ". " + name + "\n" +
                        "📝 " + (command.isEmpty() ? "(без команды)" : command) + "\n" +
                        "💬 " + answer);
                tv.setTextColor(0xff1c1c1e);
                card.addView(tv);

                LinearLayout btns = new LinearLayout(this);
                btns.setOrientation(LinearLayout.HORIZONTAL);
                Button btnEdit = new Button(this);
                btnEdit.setText("✏️");
                btnEdit.setBackground(GlassUI.glassSelector(getApplicationContext(), 0xff7547D9));
                btnEdit.setTextColor(0xffffffff);
                btnEdit.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
                btnEdit.setOnClickListener(v ->
                        showEditQuickAnswerDialog(index, name, command, answer));
                Button btnDel = new Button(this);
                btnDel.setText("🗑️");
                btnDel.setBackground(GlassUI.glassSelector(getApplicationContext(), 0xffff453a));
                btnDel.setTextColor(0xffffffff);
                btnDel.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
                btnDel.setOnClickListener(v -> {
                    deleteQuickAnswer(index);
                    showQuickAnswersList();
                });
                btns.addView(btnEdit);
                View sep = new View(this);
                sep.setLayoutParams(new LinearLayout.LayoutParams(10, -1));
                btns.addView(sep);
                btns.addView(btnDel);
                card.addView(btns);
                container.addView(card);

                View sp = new View(this);
                sp.setLayoutParams(new LinearLayout.LayoutParams(-1, 5));
                container.addView(sp);
            }
            scroll.addView(container);
            new AlertDialog.Builder(this, R.style.GlassDialogTheme)
                    .setTitle("БЫСТРЫЕ ОТВЕТЫ")
                    .setView(scroll)
                    .setPositiveButton("Закрыть", null)
                    .show();
        } catch (Exception e) {
            Toast.makeText(this, "Ошибка: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void showEditQuickAnswerDialog(final int index, String cName,
                                           String cCmd, String cAns) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(20, 20, 20, 20);

        final EditText etName = new EditText(this);
        etName.setText(cName);
        etName.setBackground(GlassUI.lightCard(this, 10f));
        etName.setPadding(24, 18, 24, 18);
        layout.addView(etName);

        final EditText etCommand = new EditText(this);
        etCommand.setText(cCmd);
        etCommand.setBackground(GlassUI.lightCard(this, 10f));
        etCommand.setPadding(24, 18, 24, 18);
        layout.addView(etCommand);

        final EditText etAnswer = new EditText(this);
        etAnswer.setText(cAns);
        etAnswer.setLines(3);
        etAnswer.setBackground(GlassUI.lightCard(this, 10f));
        etAnswer.setPadding(24, 18, 24, 18);
        layout.addView(etAnswer);

        new AlertDialog.Builder(this, R.style.GlassDialogTheme)
                .setTitle("Редактировать ответ")
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
                    updateQuickAnswer(index, n, c, a);
                    showQuickAnswersList();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void updateQuickAnswer(int index, String name, String cmd, String ans) {
        try {
            JSONArray arr = new JSONArray(this.prefs.getString("quick_answers", "[]"));
            JSONObject o = new JSONObject();
            o.put("name", name);
            o.put("command", cmd);
            o.put("answer", ans);
            arr.put(index, o);
            this.prefs.edit().putString("quick_answers", arr.toString()).apply();
        } catch (Exception e) {
            Toast.makeText(this, "Ошибка: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void deleteQuickAnswer(int index) {
        try {
            JSONArray arr = new JSONArray(this.prefs.getString("quick_answers", "[]"));
            arr.remove(index);
            this.prefs.edit().putString("quick_answers", arr.toString()).apply();
        } catch (Exception e) {
            Toast.makeText(this, "Ошибка: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // ================== ОБЫЧНЫЕ КНОПКИ ==================

    private void showNormalEditor() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(20, 20, 20, 20);

        Button btnAdd = new Button(this);
        btnAdd.setText("➕ Добавить кнопку");
        btnAdd.setBackground(GlassUI.glassSelector(getApplicationContext(), 0xff30d158));
        btnAdd.setTextColor(0xffffffff);
        btnAdd.setOnClickListener(v -> showAddButtonDialog());
        layout.addView(btnAdd);

        Button btnList = new Button(this);
        btnList.setText("📋 Список кнопок");
        btnList.setBackground(GlassUI.glassSelector(getApplicationContext(), 0xff7547D9));
        btnList.setTextColor(0xffffffff);
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
        etName.setHint("Название кнопки");
        etName.setBackground(GlassUI.lightCard(this, 10f));
        etName.setPadding(24, 18, 24, 18);
        layout.addView(etName);

        final EditText etText = new EditText(this);
        etText.setHint("Текст команды");
        etText.setBackground(GlassUI.lightCard(this, 10f));
        etText.setPadding(24, 18, 24, 18);
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
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(10, 10, 10, 10);

        for (int i = 0; i < count; i++) {
            final int index = i;
            final String name = this.prefs.getString("btn_" + i + "_name", "Кнопка");
            final String text = this.prefs.getString("btn_" + i + "_text", "");

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(20, 20, 20, 20);
            card.setBackground(GlassUI.glassSelector(getApplicationContext(), 0xffe5e5ea));

            TextView tv = new TextView(this);
            tv.setText((i + 1) + ". " + name + "\n📝 " + text);
            tv.setTextColor(0xff1c1c1e);
            card.addView(tv);

            LinearLayout btns = new LinearLayout(this);
            btns.setOrientation(LinearLayout.HORIZONTAL);
            Button btnEdit = new Button(this);
            btnEdit.setText("✏️");
            btnEdit.setBackground(GlassUI.glassSelector(getApplicationContext(), 0xff7547D9));
            btnEdit.setTextColor(0xffffffff);
            btnEdit.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
            btnEdit.setOnClickListener(v ->
                    showEditNormalButtonDialog(index, name, text));
            Button btnDel = new Button(this);
            btnDel.setText("🗑️");
            btnDel.setBackground(GlassUI.glassSelector(getApplicationContext(), 0xffff453a));
            btnDel.setTextColor(0xffffffff);
            btnDel.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
            btnDel.setOnClickListener(v -> {
                deleteNormalButton(index);
                loadCustomButtons();
                showNormalButtonsList();
            });
            btns.addView(btnEdit);
            View sep = new View(this);
            sep.setLayoutParams(new LinearLayout.LayoutParams(10, -1));
            btns.addView(sep);
            btns.addView(btnDel);
            card.addView(btns);
            container.addView(card);

            View sp = new View(this);
            sp.setLayoutParams(new LinearLayout.LayoutParams(-1, 5));
            container.addView(sp);
        }
        scroll.addView(container);
        new AlertDialog.Builder(this, R.style.GlassDialogTheme)
                .setTitle("ОБЫЧНЫЕ КНОПКИ")
                .setView(scroll)
                .setPositiveButton("Закрыть", null)
                .show();
    }

    private void showEditNormalButtonDialog(final int index, String cName, String cText) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(30, 20, 30, 20);

        final EditText etName = new EditText(this);
        etName.setText(cName);
        etName.setBackground(GlassUI.lightCard(this, 10f));
        etName.setPadding(24, 18, 24, 18);
        layout.addView(etName);

        final EditText etText = new EditText(this);
        etText.setText(cText);
        etText.setBackground(GlassUI.lightCard(this, 10f));
        etText.setPadding(24, 18, 24, 18);
        layout.addView(etText);

        new AlertDialog.Builder(this, R.style.GlassDialogTheme)
                .setTitle("Редактировать")
                .setView(layout)
                .setPositiveButton("Сохранить", (d, w) -> {
                    String n = etName.getText().toString();
                    String t = etText.getText().toString();
                    if (!n.isEmpty() && !t.isEmpty()) {
                        updateNormalButton(index, n, t);
                        loadCustomButtons();
                        showNormalButtonsList();
                    }
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void updateNormalButton(int index, String name, String text) {
        SharedPreferences.Editor editor = this.prefs.edit();
        editor.putString("btn_" + index + "_name", name);
        editor.putString("btn_" + index + "_text", text);
        editor.apply();
    }

    private void deleteNormalButton(int index) {
        int count = getIntPref("btn_count", 0);
        SharedPreferences.Editor editor = this.prefs.edit();
        for (int i = index; i < count - 1; i++) {
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
        btnAdd.setBackground(GlassUI.glassSelector(getApplicationContext(), 0xff30d158));
        btnAdd.setTextColor(0xffffffff);
        btnAdd.setOnClickListener(v -> showAddFamWarDialog());
        layout.addView(btnAdd);

        Button btnList = new Button(this);
        btnList.setText("📋 Список FamWar");
        btnList.setBackground(GlassUI.glassSelector(getApplicationContext(), 0xff7547D9));
        btnList.setTextColor(0xffffffff);
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
        etName.setBackground(GlassUI.lightCard(this, 10f));
        etName.setPadding(24, 18, 24, 18);
        layout.addView(etName);

        final EditText etCommand = new EditText(this);
        etCommand.setHint("Команда (например /heal)");
        etCommand.setBackground(GlassUI.lightCard(this, 10f));
        etCommand.setPadding(24, 18, 24, 18);
        layout.addView(etCommand);

        final EditText etTime = new EditText(this);
        etTime.setHint("Время (опц.)");
        etTime.setBackground(GlassUI.lightCard(this, 10f));
        etTime.setPadding(24, 18, 24, 18);
        layout.addView(etTime);

        final EditText etReason = new EditText(this);
        etReason.setHint("Причина (опц.)");
        etReason.setBackground(GlassUI.lightCard(this, 10f));
        etReason.setPadding(24, 18, 24, 18);
        layout.addView(etReason);

        final EditText etMin = new EditText(this);
        etMin.setHint("Мин. уровень (1-4)");
        etMin.setInputType(InputType.TYPE_CLASS_NUMBER);
        etMin.setBackground(GlassUI.lightCard(this, 10f));
        etMin.setPadding(24, 18, 24, 18);
        layout.addView(etMin);

        new AlertDialog.Builder(this, R.style.GlassDialogTheme)
                .setTitle("Добавить FamWar")
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
                    saveFamWar(n, c, t, r, m);
                    Toast.makeText(this, "✅ Сохранено!", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void saveFamWar(String name, String command, String time,
                            String reason, String min) {
        try {
            JSONArray arr = new JSONArray(this.prefs.getString("famwar_commands", "[]"));
            JSONObject o = new JSONObject();
            o.put("name", name);
            o.put("command", command);
            o.put("time", time);
            o.put("reason", reason);
            o.put("minlevel", min);
            arr.put(o);
            this.prefs.edit().putString("famwar_commands", arr.toString()).apply();
        } catch (Exception e) {
            Toast.makeText(this, "Ошибка: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
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
            LinearLayout container = new LinearLayout(this);
            container.setOrientation(LinearLayout.VERTICAL);
            container.setPadding(10, 10, 10, 10);

            for (int i = 0; i < arr.length(); i++) {
                final int index = i;
                JSONObject o = arr.getJSONObject(i);
                final String name = o.optString("name", "Команда " + (i + 1));
                final String command = o.optString("command", "");
                final String time = o.optString("time", "");
                final String reason = o.optString("reason", "");
                final String minLevel = o.optString("minlevel", "1");

                LinearLayout card = new LinearLayout(this);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setPadding(20, 20, 20, 20);
                card.setBackground(GlassUI.glassSelector(getApplicationContext(), 0xffe5e5ea));

                TextView tv = new TextView(this);
                StringBuilder sb = new StringBuilder();
                sb.append(i + 1).append(". ").append(name).append("\n");
                sb.append("📝 ").append(command).append("\n");
                if (!time.isEmpty()) sb.append("⏱ ").append(time).append("\n");
                if (!reason.isEmpty()) sb.append("💬 ").append(reason).append("\n");
                sb.append("👑 ").append(minLevel);
                tv.setText(sb.toString());
                tv.setTextColor(0xff1c1c1e);
                card.addView(tv);

                LinearLayout btns = new LinearLayout(this);
                btns.setOrientation(LinearLayout.HORIZONTAL);
                Button btnEdit = new Button(this);
                btnEdit.setText("✏️");
                btnEdit.setBackground(GlassUI.glassSelector(getApplicationContext(), 0xff7547D9));
                btnEdit.setTextColor(0xffffffff);
                btnEdit.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
                btnEdit.setOnClickListener(v ->
                        showEditFamWarDialog(index, name, command, time, reason, minLevel));
                Button btnDel = new Button(this);
                btnDel.setText("🗑️");
                btnDel.setBackground(GlassUI.glassSelector(getApplicationContext(), 0xffff453a));
                btnDel.setTextColor(0xffffffff);
                btnDel.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
                btnDel.setOnClickListener(v -> {
                    deleteFamWar(index);
                    showFamWarList();
                });
                btns.addView(btnEdit);
                View sep = new View(this);
                sep.setLayoutParams(new LinearLayout.LayoutParams(10, -1));
                btns.addView(sep);
                btns.addView(btnDel);
                card.addView(btns);
                container.addView(card);

                View sp = new View(this);
                sp.setLayoutParams(new LinearLayout.LayoutParams(-1, 5));
                container.addView(sp);
            }
            scroll.addView(container);
            new AlertDialog.Builder(this, R.style.GlassDialogTheme)
                    .setTitle("КОМАНДЫ FAMWAR")
                    .setView(scroll)
                    .setPositiveButton("Закрыть", null)
                    .show();
        } catch (Exception e) {
            Toast.makeText(this, "Ошибка: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void showEditFamWarDialog(final int index, String cName, String cCmd,
                                      String cTime, String cReason, String cMin) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(20, 20, 20, 20);

        final EditText etName = new EditText(this);
        etName.setText(cName);
        etName.setBackground(GlassUI.lightCard(this, 10f));
        etName.setPadding(24, 18, 24, 18);
        layout.addView(etName);

        final EditText etCmd = new EditText(this);
        etCmd.setText(cCmd);
        etCmd.setBackground(GlassUI.lightCard(this, 10f));
        etCmd.setPadding(24, 18, 24, 18);
        layout.addView(etCmd);

        final EditText etTime = new EditText(this);
        etTime.setText(cTime);
        etTime.setBackground(GlassUI.lightCard(this, 10f));
        etTime.setPadding(24, 18, 24, 18);
        layout.addView(etTime);

        final EditText etReason = new EditText(this);
        etReason.setText(cReason);
        etReason.setBackground(GlassUI.lightCard(this, 10f));
        etReason.setPadding(24, 18, 24, 18);
        layout.addView(etReason);

        final EditText etMin = new EditText(this);
        etMin.setText(cMin);
        etMin.setInputType(InputType.TYPE_CLASS_NUMBER);
        etMin.setBackground(GlassUI.lightCard(this, 10f));
        etMin.setPadding(24, 18, 24, 18);
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
                        Toast.makeText(this, "Заполните название и команду!",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (m.isEmpty()) m = "1";
                    updateFamWar(index, n, c, t, r, m);
                    showFamWarList();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void updateFamWar(int index, String name, String cmd,
                              String time, String reason, String min) {
        try {
            JSONArray arr = new JSONArray(this.prefs.getString("famwar_commands", "[]"));
            JSONObject o = new JSONObject();
            o.put("name", name);
            o.put("command", cmd);
            o.put("time", time);
            o.put("reason", reason);
            o.put("minlevel", min);
            arr.put(index, o);
            this.prefs.edit().putString("famwar_commands", arr.toString()).apply();
        } catch (Exception e) {
            Toast.makeText(this, "Ошибка: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void deleteFamWar(int index) {
        try {
            JSONArray arr = new JSONArray(this.prefs.getString("famwar_commands", "[]"));
            arr.remove(index);
            this.prefs.edit().putString("famwar_commands", arr.toString()).apply();
        } catch (Exception e) {
            Toast.makeText(this, "Ошибка: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // ================== ДИАЛОГИ НАСТРОЕК ==================

    private void showTransparencyDialog() {
        int current = getIntPref("keyboard_transparency", 100);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(30, 20, 30, 20);

        final TextView tvValue = new TextView(this);
        tvValue.setText("Прозрачность: " + current + "%");
        tvValue.setTextSize(18f);
        tvValue.setGravity(android.view.Gravity.CENTER);
        tvValue.setPadding(0, 0, 0, 20);
        layout.addView(tvValue);

        SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(100);
        seekBar.setProgress(current);
        seekBar.setPadding(20, 10, 20, 10);
        layout.addView(seekBar);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int p, boolean f) {
                tvValue.setText("Прозрачность: " + p + "%");
                MainActivity.this.prefs.edit()
                        .putInt("keyboard_transparency", p).apply();
                MainActivity.this.sendBroadcast(
                        new Intent("KEYBOARD_TRANSPARENCY_CHANGED"));
            }
            @Override public void onStartTrackingTouch(SeekBar sb) { }
            @Override public void onStopTrackingTouch(SeekBar sb) { }
        });

        new AlertDialog.Builder(this, R.style.GlassDialogTheme)
                .setTitle("ПРОЗРАЧНОСТЬ КЛАВИАТУРЫ")
                .setView(layout)
                .setPositiveButton("Закрыть", null)
                .show();
    }

    private void showKeyboardSizeDialog() {
        final String[] sizes = {"80%", "100%", "120%", "140%", "160%", "180%", "200%"};
        int current = getIntPref("keyboard_size", 1);
        new AlertDialog.Builder(this, R.style.GlassDialogTheme)
                .setTitle("РАЗМЕР КЛАВИАТУРЫ")
                .setSingleChoiceItems(sizes, current, (d, which) -> {
                    MainActivity.this.prefs.edit()
                            .putInt("keyboard_size", which).apply();
                    Toast.makeText(MainActivity.this,
                            "✅ Размер: " + sizes[which],
                            Toast.LENGTH_SHORT).show();
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
                .setItems(levels, (d, which) -> {
                    int level = which + 1;
                    MainActivity.this.prefs.edit()
                            .putInt("admin_level", level).apply();
                    Toast.makeText(MainActivity.this,
                            "Уровень: " + level,
                            Toast.LENGTH_SHORT).show();
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
