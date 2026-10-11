package gtandroid.universal;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.inputmethodservice.InputMethodService;
import android.os.Handler;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputConnection;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class KeyboardService extends InputMethodService implements View.OnClickListener {

    private static final String KEY_LAST_RESET_DATE = "last_reset_date";
    private static final String KEY_TOTAL_REPORTS = "total_reports_all_time";
    private static final String KEY_WEEKLY_PREFIX = "weekly_";
    private static final String PREFS_STATS = "GTAndroidStats";

    // Фиолетовая палитра
    private static final int PURPLE_MAIN   = 0xFF7C3AED;
    private static final int PURPLE_DARK   = 0xFF4C1D95;
    private static final int PURPLE_LIGHT  = 0xFFA78BFA;
    private static final int PURPLE_BG     = 0xFF1A0B2E;
    private static final int PURPLE_PANEL  = 0xFF2D1B4E;
    private static final int PURPLE_ACCENT = 0xFFC4B5FD;
    private static final int PURPLE_BTN    = 0xFF5856D6;
    private static final int RED_DANGER    = 0xFFDC2626;
    private static final int GREEN_OK      = 0xFF10B981;
    private static final int WHITE         = 0xFFFFFFFF;

    private View keyboardView;
    private View menuView;
    private LinearLayout menuLayout;

    private SharedPreferences prefs;
    private BroadcastReceiver sizeChangeReceiver;
    private BroadcastReceiver transparencyChangeReceiver;

    private boolean isRussian = true;
    private boolean isUpperCase = false;
    private boolean showMenu = false;
    private boolean showKeyboard = true;
    private boolean showSymbolsPage = false;

    private int adminLevel = 1;
    private int keyboardSize = 1;
    private int keyboardTransparency = 100;

    private String selectedPunishmentId = "";
    private String selectedReportId = "";
    private boolean isViolatorMode = false;
    private String currentPlayerId = "";
    private String currentTargetId = "";

    private interface CommandCallback {
        void onCommand(String str);
    }

    // ==================== LIFECYCLE ====================

    @Override
    public void onCreate() {
        super.onCreate();
        this.prefs = getSharedPreferences("GTAndroidData", 0);
        this.adminLevel = this.prefs.getInt("admin_level", 1);
        this.keyboardSize = this.prefs.getInt("keyboard_size", 1);
        this.keyboardTransparency = this.prefs.getInt("keyboard_transparency", 100);

        this.sizeChangeReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                if (i != null && "KEYBOARD_SIZE_CHANGED".equals(i.getAction())) {
                    keyboardSize = prefs.getInt("keyboard_size", 1);
                    updateKeyboardSize();
                    if (menuLayout != null && menuLayout.getVisibility() == View.VISIBLE) {
                        refreshCurrentMenu();
                    }
                }
            }
        };
        registerReceiver(sizeChangeReceiver, new IntentFilter("KEYBOARD_SIZE_CHANGED"));

        this.transparencyChangeReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                if (i != null && "KEYBOARD_TRANSPARENCY_CHANGED".equals(i.getAction())) {
                    keyboardTransparency = prefs.getInt("keyboard_transparency", 100);
                    applyKeyboardTransparency(keyboardTransparency);
                }
            }
        };
        registerReceiver(transparencyChangeReceiver,
                new IntentFilter("KEYBOARD_TRANSPARENCY_CHANGED"));

        checkAndResetWeeklyStats();
    }

    @Override
    public View onCreateInputView() {
        this.keyboardView = getLayoutInflater().inflate(R.layout.keyboard, null);
        setupButtons();
        createMenu();
        applyKeyboardSize();
        applyKeyboardTransparency(keyboardTransparency);
        return keyboardView;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try { if (sizeChangeReceiver != null) unregisterReceiver(sizeChangeReceiver); }
        catch (Exception ignored) { }
        try { if (transparencyChangeReceiver != null) unregisterReceiver(transparencyChangeReceiver); }
        catch (Exception ignored) { }
    }

    // ==================== МЕНЮ ====================

    private void createMenu() {
        this.menuView = keyboardView.findViewById(R.id.menuContainer);
        if (menuView != null) {
            this.menuLayout = (LinearLayout) menuView;
            this.menuLayout.removeAllViews();
            this.menuLayout.setBackground(GlassUI.glassSelector(getApplicationContext(), PURPLE_BG));
            this.menuLayout.setPadding(5, 5, 5, 5);
            this.menuLayout.setVisibility(View.GONE);
        }
    }

    private void refreshCurrentMenu() {
        if (menuLayout == null || menuLayout.getVisibility() != View.VISIBLE) return;
        if (menuLayout.getChildCount() == 0) return;
        View first = menuLayout.getChildAt(0);
        if (!(first instanceof TextView)) return;
        String text = ((TextView) first).getText().toString();
        if (text.contains("АДМИН")) showAdminPanel();
        else if (text.contains("ОБЫЧНАЯ")) showNormalPanel();
        else if (text.contains("БЫСТРЫЙ ОТВЕТ")) showQuickAnswersMenu();
        else if (text.contains("ТЕЛЕПОРТ")) showTeleportMenu();
        else if (text.contains("ЧИТЫ")) showCheatsMenu();
        else if (text.contains("СТАТИСТИКА")) showStatisticsMenu();
        else if (text.contains("ТП К СЕБЕ")) showTpToMeMenu();
        applyKeyboardTransparency(keyboardTransparency);
    }

    // ==================== РАЗМЕР / ПРОЗРАЧНОСТЬ ====================

    private int dpToPx(float dp) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }

    private String getPercentText() {
        switch (keyboardSize) {
            case 0: return "80%";
            case 1: return "100%";
            case 2: return "120%";
            case 3: return "140%";
            case 4: return "160%";
            case 5: return "180%";
            case 6: return "200%";
            default: return "100%";
        }
    }

    private float getFontMultiplier() {
        switch (keyboardSize) {
            case 0: return 0.8f;
            case 1: return 1.0f;
            case 2: return 1.2f;
            case 3: return 1.4f;
            case 4: return 1.6f;
            case 5: return 1.8f;
            case 6: return 2.0f;
            default: return 1.0f;
        }
    }

    private int getButtonHeight(int baseDp) {
        float mult;
        switch (keyboardSize) {
            case 0: mult = 0.8f; break;
            case 1: mult = 1.0f; break;
            case 2: mult = 1.2f; break;
            case 3: mult = 1.4f; break;
            case 4: mult = 1.6f; break;
            case 5: mult = 1.8f; break;
            case 6: mult = 2.0f; break;
            default: mult = 1.0f; break;
        }
        return dpToPx(baseDp * mult * 0.5f);
    }

    public void updateKeyboardSize() {
        if (keyboardView != null) applyKeyboardSize();
    }

    private void applyKeyboardSize() {
        if (keyboardView == null) return;
        keyboardSize = prefs.getInt("keyboard_size", 1);
        String percentText = getPercentText();

        int normalH = getButtonHeight(48);
        int smallH = getButtonHeight(42);
        int topH = getButtonHeight(44);

        int[] ids = {
                R.id.numbersRowTop_ru, R.id.numbersRowTop_en,
                R.id.symbolsRowTop_ru, R.id.symbolsRowTop_en
        };
        for (int id : ids) {
            LinearLayout ll = keyboardView.findViewById(id);
            if (ll != null) changeButtonsHeight(ll, smallH);
        }
        LinearLayout ru = keyboardView.findViewById(R.id.russianLayout);
        if (ru != null) changeButtonsHeight(ru, normalH);
        LinearLayout en = keyboardView.findViewById(R.id.englishLayout);
        if (en != null) changeButtonsHeight(en, normalH);

        LinearLayout kc = keyboardView.findViewById(R.id.keyboardContainer);
        if (kc != null && kc.getChildCount() > 1) {
            View second = kc.getChildAt(1);
            if (second instanceof LinearLayout)
                changeButtonsHeight((LinearLayout) second, topH);
        }
        TextView title = keyboardView.findViewById(R.id.keyboardTitle);
        if (title != null) {
            title.setTextSize(14f * getFontMultiplier());
            title.setTextColor(WHITE);
            title.setText("⌨️ RTools[+] " + adminLevel + " ур. [" + percentText + "]");
        }
    }

    private void changeButtonsHeight(LinearLayout container, int height) {
        if (container == null) return;
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            if (child instanceof LinearLayout) {
                changeButtonsHeight((LinearLayout) child, height);
            } else if (child instanceof Button) {
                Button btn = (Button) child;
                LinearLayout.LayoutParams lp =
                        (LinearLayout.LayoutParams) btn.getLayoutParams();
                if (lp != null) {
                    lp.height = height;
                    btn.setLayoutParams(lp);
                    btn.setTextSize(getFontMultiplier() * 20f);
                    btn.setTextColor(WHITE);
                }
            }
        }
    }

    private void applyKeyboardTransparency(int percent) {
        if (keyboardView == null) return;
        int alpha = (int) ((percent / 100.0d) * 255.0d);
        LinearLayout kc = keyboardView.findViewById(R.id.keyboardContainer);
        if (kc != null && kc.getBackground() != null)
            kc.getBackground().mutate().setAlpha(alpha);
        TextView title = keyboardView.findViewById(R.id.keyboardTitle);
        if (title != null && title.getBackground() != null)
            title.getBackground().mutate().setAlpha(alpha);
        if (menuLayout != null && menuLayout.getBackground() != null)
            menuLayout.getBackground().mutate().setAlpha(alpha);
        applyTransparencyToContainers(keyboardView, alpha);
    }

    private void applyTransparencyToContainers(View view, int alpha) {
        if (view == null || view instanceof Button || view instanceof EditText) return;
        Drawable bg = view.getBackground();
        if (bg != null) {
            try { bg.mutate().setAlpha(alpha); } catch (Exception ignored) { }
        }
        if (view instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) view;
            for (int i = 0; i < g.getChildCount(); i++) {
                applyTransparencyToContainers(g.getChildAt(i), alpha);
            }
        }
    }

    // ==================== ПАНЕЛИ ====================

    private void showAdminPanel() {
        if (menuLayout == null) return;
        menuLayout.removeAllViews();
        menuLayout.setVisibility(View.VISIBLE);
        requestHideSelf(0);

        menuLayout.addView(makeTitle("АДМИН ПАНЕЛЬ"));

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        menuLayout.addView(container);

        setupAdminButtons(container);

        menuLayout.addView(makeBackButton());
        updateMenuButtonsHeight(menuLayout, getButtonHeight(50));
        applyKeyboardTransparency(keyboardTransparency);
    }

    private void setupAdminButtons(LinearLayout container) {
        String[] names = {
                "Ответить на репорт", "Наказать игрока", "Следить sp(off)",
                "Быстрая команда", "Точка спавна", "Читы",
                "Тп к себе", "Статистика", "ADM-формы"
        };
        int buttonH = getButtonHeight(90);
        float textSize = getFontMultiplier() * 11f;

        for (int row = 0; row < 3; row++) {
            LinearLayout rowLayout = new LinearLayout(this);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            rowLayout.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));

            for (int col = 0; col < 3; col++) {
                int idx = row * 3 + col;
                if (idx >= names.length) break;
                final String name = names[idx];
                Button btn = new Button(this);
                btn.setText(name);
                btn.setLayoutParams(new LinearLayout.LayoutParams(0, buttonH, 1f));
                btn.setBackground(GlassUI.glassSelector(getApplicationContext(), PURPLE_MAIN));
                btn.setTextColor(WHITE);
                btn.setPadding(3, 3, 3, 3);
                btn.setTextSize(textSize);
                btn.setMaxLines(2);
                btn.setOnClickListener(v -> {
                    switch (name) {
                        case "Читы": showCheatsMenu(); break;
                        case "Наказать игрока": showPunishmentIdInput(); break;
                        case "Ответить на репорт": showReportAnswerIdInput(); break;
                        case "Точка спавна": showSpawnPointMenu(); break;
                        case "Следить sp(off)": showSpectateMenu(); break;
                        case "Быстрая команда": showQuickCommandMenu(); break;
                        case "Тп к себе": showTpToMeMenu(); break;
                        case "Статистика": showStatisticsMenu(); break;
                        case "ADM-формы": showKeyboardAdminForms(); break;
                    }
                });
                rowLayout.addView(btn);
                if (col < 2 && idx < names.length - 1) {
                    View sep = new View(this);
                    sep.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(3), -1));
                    rowLayout.addView(sep);
                }
            }
            container.addView(rowLayout);
            if (row < 2) {
                View sep = new View(this);
                sep.setLayoutParams(new LinearLayout.LayoutParams(-1, 3));
                sep.setBackground(GlassUI.glassSelector(getApplicationContext(), PURPLE_DARK));
                container.addView(sep);
            }
        }

        Button btnReportCatch = new Button(this);
        btnReportCatch.setText("🎯  Ловля репортов (60 сек)");
        btnReportCatch.setAllCaps(false);
        btnReportCatch.setTextColor(WHITE);
        btnReportCatch.setTextSize(getFontMultiplier() * 13f);
        btnReportCatch.setBackground(GlassUI.glassSelector(getApplicationContext(), 0xFF00B89A));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, getButtonHeight(50));
        rp.topMargin = dpToPx(6);
        btnReportCatch.setLayoutParams(rp);
        btnReportCatch.setOnClickListener(v -> showCaughtReports());
        container.addView(btnReportCatch);
    }

    private void showNormalPanel() {
        if (menuLayout == null) return;
        menuLayout.removeAllViews();
        menuLayout.setVisibility(View.VISIBLE);
        requestHideSelf(0);

        menuLayout.addView(makeTitle("ОБЫЧНАЯ ПАНЕЛЬ"));

        ScrollView sv = new ScrollView(this);
        sv.setLayoutParams(new LinearLayout.LayoutParams(-1, getButtonHeight(350)));
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        sv.addView(c);
        menuLayout.addView(sv);

        loadUserButtons(c);

        menuLayout.addView(makeBackButton());
        updateMenuButtonsHeight(menuLayout, getButtonHeight(50));
        applyKeyboardTransparency(keyboardTransparency);
    }

    private void showKeyboardView() {
        if (menuLayout != null) menuLayout.setVisibility(View.GONE);
        View kl = keyboardView.findViewById(R.id.keyboardLayout);
        if (kl != null) kl.setVisibility(View.VISIBLE);
        showMenu = false;
        requestShowSelf(0);
    }

    private void loadUserButtons(LinearLayout container) {
        container.removeAllViews();
        int count = prefs.getInt("btn_count", 0);
        if (count == 0) {
            TextView tv = new TextView(this);
            tv.setText("Нет сохранённых кнопок");
            tv.setTextColor(WHITE);
            tv.setPadding(10, 10, 10, 10);
            tv.setTextSize(getFontMultiplier() * 12f);
            container.addView(tv);
            return;
        }
        int buttonH = getButtonHeight(50);
        float textSize = getFontMultiplier() * 12f;
        for (int i = 0; i < count; i++) {
            final String text = prefs.getString("btn_" + i + "_text", "");
            String name = prefs.getString("btn_" + i + "_name", "Кнопка");
            Button btn = new Button(this);
            btn.setText(name);
            btn.setLayoutParams(new LinearLayout.LayoutParams(-1, buttonH));
            btn.setBackground(GlassUI.glassSelector(getApplicationContext(), PURPLE_BTN));
            btn.setTextColor(WHITE);
            btn.setTextSize(textSize);
            btn.setPadding(15, 12, 15, 12);
            btn.setOnClickListener(v -> sendCommandToChat(text));
            container.addView(btn);
        }
    }

    // ==================== НАКАЗАНИЯ ====================

    private void showPunishmentIdInput() {
        showGenericIdInput("ВВЕДИТЕ ID", "ID игрока", id -> {
            selectedPunishmentId = id;
            showPunishmentTypes();
        });
    }

    private void showPunishmentTypes() {
        if (menuLayout == null) return;
        menuLayout.removeAllViews();
        menuLayout.setVisibility(View.VISIBLE);
        requestHideSelf(0);

        menuLayout.addView(makeTitle("ВЫБЕРИТЕ ТИП"));

        final String[] types = {
                "Кик", "Бан", "Варн", "Джаил", "Мут",
                "UnБан", "UnВарн", "UnДжаил", "UnМут", "RМут", "UnRМут"
        };

        ScrollView sv = new ScrollView(this);
        sv.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        sv.addView(c);

        int buttonH = getButtonHeight(70);
        float textSize = getFontMultiplier() * 14f;
        int rows = (int) Math.ceil(types.length / 2.0);

        for (int row = 0; row < rows; row++) {
            LinearLayout rowLayout = new LinearLayout(this);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            rowLayout.setWeightSum(2f);
            for (int col = 0; col < 2; col++) {
                int idx = row * 2 + col;
                if (idx >= types.length) break;
                final String type = types[idx];
                Button btn = new Button(this);
                btn.setText(type);
                btn.setLayoutParams(new LinearLayout.LayoutParams(0, buttonH, 1f));
                btn.setBackground(GlassUI.glassSelector(getApplicationContext(), PURPLE_BTN));
                btn.setTextColor(WHITE);
                btn.setTextSize(textSize);
                btn.setPadding(10, 15, 10, 15);
                btn.setOnClickListener(v -> showPunishmentButtonsForType(idx, type));
                rowLayout.addView(btn);
                if (col == 0 && idx < types.length - 1) {
                    View sep = new View(this);
                    sep.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(5), buttonH));
                    rowLayout.addView(sep);
                }
            }
            c.addView(rowLayout);
            if (row < rows - 1) {
                View sep = new View(this);
                sep.setLayoutParams(new LinearLayout.LayoutParams(-1, 5));
                sep.setBackground(GlassUI.glassSelector(getApplicationContext(), PURPLE_DARK));
                c.addView(sep);
            }
        }
        menuLayout.addView(sv);
        menuLayout.addView(makeBackButton());
        updateMenuButtonsHeight(menuLayout, getButtonHeight(55));
        applyKeyboardTransparency(keyboardTransparency);
    }

    private void showPunishmentButtonsForType(int typeIndex, String typeName) {
        if (menuLayout == null) return;
        menuLayout.removeAllViews();
        menuLayout.setVisibility(View.VISIBLE);
        requestHideSelf(0);

        menuLayout.addView(makeTitle(typeName.toUpperCase()));

        String key = "punishment_" + typeName.toLowerCase(Locale.ROOT);
        JSONArray arr = null;
        try {
            arr = new JSONArray(prefs.getString(key, "[]"));
        } catch (Exception ignored) { }

        ScrollView sv = new ScrollView(this);
        sv.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        sv.addView(c);

        if (arr == null || arr.length() == 0) {
            TextView tv = new TextView(this);
            tv.setText("Нет наказаний этого типа.\nДобавьте в редакторе.");
            tv.setTextColor(WHITE);
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(20, 40, 20, 40);
            c.addView(tv);
        } else {
            int buttonH = getButtonHeight(70);
            float textSize = getFontMultiplier() * 12f;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;

                final String name = o.optString("name", "Наказание " + (i + 1));
                final String command = o.optString("command", "");
                final String time = o.optString("time", "");
                final String reason = o.optString("reason", "");

                int parsedLevel = 1;
                try { parsedLevel = Integer.parseInt(o.optString("minlevel", "1")); }
                catch (Exception ignored) { }
                final int minLevel = parsedLevel;

                Button btn = new Button(this);
                String label = name;
                if (adminLevel < minLevel) label += "  [нужен " + minLevel + " ур.]";
                btn.setText(label);
                btn.setLayoutParams(new LinearLayout.LayoutParams(-1, buttonH));
                btn.setBackground(GlassUI.glassSelector(getApplicationContext(),
                        adminLevel >= minLevel ? PURPLE_BTN : 0xFF6B7280));
                btn.setTextColor(WHITE);
                btn.setTextSize(textSize);
                btn.setPadding(15, 15, 15, 15);
                btn.setOnClickListener(v ->
                        executePunishment(command, time, reason, minLevel));
                c.addView(btn);

                View sep = new View(this);
                sep.setLayoutParams(new LinearLayout.LayoutParams(-1, 3));
                sep.setBackground(GlassUI.glassSelector(getApplicationContext(), PURPLE_DARK));
                c.addView(sep);
            }
        }

        menuLayout.addView(sv);

        Button back = makeBackButton();
        back.setOnClickListener(v -> showPunishmentTypes());
        menuLayout.addView(back);

        updateMenuButtonsHeight(menuLayout, getButtonHeight(55));
        applyKeyboardTransparency(keyboardTransparency);
    }

    private void executePunishment(String cmd, String time, String reason, int requiredLevel) {
        StringBuilder full = new StringBuilder();
        if (adminLevel < requiredLevel) full.append("/a ");
        full.append(cmd).append(" ").append(selectedPunishmentId);
        if (!time.isEmpty()) full.append(" ").append(time);
        if (!reason.isEmpty()) full.append(" ").append(reason);

        String finalCmd = full.toString();
        sendCommandToChat(finalCmd);
        if (adminLevel < requiredLevel) {
            Toast.makeText(this, "⚠️ Через /a: " + finalCmd, Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "✅ " + finalCmd, Toast.LENGTH_SHORT).show();
        }
        showKeyboardView();
    }

    // ==================== РЕПОРТЫ / СЛЕЖКА ====================

    private void showReportAnswerIdInput() {
        showGenericIdInput("ОТВЕТ НА РЕПОРТ", "ID игрока", id -> {
            selectedReportId = id;
            showFollowTypeSelection(id);
        });
    }

    private void showFollowTypeSelection(final String playerId) {
        if (menuLayout == null) return;
        menuLayout.removeAllViews();
        menuLayout.setVisibility(View.VISIBLE);
        requestHideSelf(0);

        menuLayout.addView(makeTitle("ЗА КЕМ СЛЕДИТЬ?"));

        int buttonH = getButtonHeight(70);
        float textSize = getFontMultiplier() * 16f;

        Button btnPlayer = new Button(this);
        btnPlayer.setText("👤 Следить за игроком");
        btnPlayer.setLayoutParams(new LinearLayout.LayoutParams(-1, buttonH));
        btnPlayer.setBackground(GlassUI.glassSelector(getApplicationContext(), PURPLE_BTN));
        btnPlayer.setTextColor(WHITE);
        btnPlayer.setTextSize(textSize);
        btnPlayer.setOnClickListener(v -> {
            sendCommandToChat("/ans " + playerId + " Приветствую, попытаюсь сейчас вам помочь.");
            showFollowButtonScreen(playerId, playerId, false);
        });
        menuLayout.addView(btnPlayer);

        View sep = new View(this);
        sep.setLayoutParams(new LinearLayout.LayoutParams(-1, 3));
        sep.setBackground(GlassUI.glassSelector(getApplicationContext(), PURPLE_DARK));
        menuLayout.addView(sep);

        Button btnViol = new Button(this);
        btnViol.setText("⚠️ Следить за нарушителем");
        btnViol.setLayoutParams(new LinearLayout.LayoutParams(-1, buttonH));
        btnViol.setBackground(GlassUI.glassSelector(getApplicationContext(), RED_DANGER));
        btnViol.setTextColor(WHITE);
        btnViol.setTextSize(textSize);
        btnViol.setOnClickListener(v -> showFollowViolatorIdInput(playerId));
        menuLayout.addView(btnViol);

        menuLayout.addView(makeBackButton());
        updateMenuButtonsHeight(menuLayout, getButtonHeight(50));
        applyKeyboardTransparency(keyboardTransparency);
    }

    private void showFollowButtonScreen(final String playerId, final String targetId,
                                        final boolean isViolator) {
        if (menuLayout == null) return;
        menuLayout.removeAllViews();
        menuLayout.setVisibility(View.VISIBLE);
        requestHideSelf(0);

        String header = isViolator
                ? "НАРУШИТЕЛЬ: " + targetId
                : "ИГРОК: " + targetId;
        menuLayout.addView(makeTitle(header));

        Button btnFollow = new Button(this);
        btnFollow.setText("👁️ Следить");
        btnFollow.setLayoutParams(new LinearLayout.LayoutParams(-1, getButtonHeight(80)));
        btnFollow.setBackground(GlassUI.glassSelector(getApplicationContext(), GREEN_OK));
        btnFollow.setTextColor(WHITE);
        btnFollow.setTextSize(getFontMultiplier() * 20f);
        btnFollow.setOnClickListener(v -> {
            sendCommandToChat("/sp " + targetId);
            if (isViolator) showFollowViolatorActions(playerId, targetId);
            else showFollowPlayerActions(targetId);
        });
        menuLayout.addView(btnFollow);

        Button back = makeBackButton();
        back.setOnClickListener(v -> showFollowTypeSelection(playerId));
        menuLayout.addView(back);
        updateMenuButtonsHeight(menuLayout, getButtonHeight(50));
        applyKeyboardTransparency(keyboardTransparency);
    }

    private void showFollowViolatorIdInput(final String playerId) {
        showGenericIdInput("ID НАРУШИТЕЛЯ", "ID нарушителя", id -> {
            sendCommandToChat("/ans " + playerId
                    + " Приветствую, слежу за игроком с id [" + id + "]");
            showFollowButtonScreen(playerId, id, true);
        });
    }

    private void showFollowPlayerActions(final String playerId) {
        if (menuLayout == null) return;
        menuLayout.removeAllViews();
        menuLayout.setVisibility(View.VISIBLE);
        requestHideSelf(0);

        menuLayout.addView(makeTitle("ИГРОК: " + playerId));

        String[] buttons = {
                "Тп к игроку", "Выйти из слежки", "Наказать", "Чинить",
                "Перевернуть", "Подкинуть", "Спавн", "Статистика",
                "Заправить", "Лицензии", "Наказания", "Заморозить",
                "Тп икс", "Тп ткс", "Оружия игрока",
                "Офф Наручники", "Забрать оружие", "Отправить в /a"
        };

        ScrollView sv = new ScrollView(this);
        sv.setLayoutParams(new LinearLayout.LayoutParams(-1, -1));
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        sv.addView(c);

        int buttonH = getButtonHeight(60);
        float textSize = getFontMultiplier() * 10f;

        for (int row = 0; row < 4; row++) {
            LinearLayout rowLayout = new LinearLayout(this);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            rowLayout.setWeightSum(5f);
            for (int col = 0; col < 5; col++) {
                int idx = row * 5 + col;
                if (idx >= buttons.length) break;
                Button btn = new Button(this);
                btn.setText(buttons[idx]);
                btn.setLayoutParams(new LinearLayout.LayoutParams(0, buttonH, 1f));
                btn.setBackground(GlassUI.glassSelector(getApplicationContext(), PURPLE_BTN));
                btn.setTextColor(WHITE);
                btn.setTextSize(textSize);
                btn.setPadding(2, 2, 2, 2);
                btn.setMaxLines(2);
                btn.setGravity(Gravity.CENTER);
                btn.setMinHeight(buttonH);
                final int i = idx;
                btn.setOnClickListener(v -> handleFollowPlayerAction(i, playerId));
                rowLayout.addView(btn);
                if (col < 4 && idx < buttons.length - 1) {
                    View sep = new View(this);
                    sep.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(2), buttonH));
                    rowLayout.addView(sep);
                }
            }
            c.addView(rowLayout);
            if (row < 3) {
                View sep = new View(this);
                sep.setLayoutParams(new LinearLayout.LayoutParams(-1, 2));
                sep.setBackground(GlassUI.glassSelector(getApplicationContext(), PURPLE_DARK));
                c.addView(sep);
            }
        }
        menuLayout.addView(sv);

        menuLayout.addView(makeBackButton());
        updateMenuButtonsHeight(menuLayout, getButtonHeight(55));
        applyKeyboardTransparency(keyboardTransparency);
    }

    private void handleFollowPlayerAction(int index, String playerId) {
        isViolatorMode = false;
        currentPlayerId = playerId;
        currentTargetId = playerId;
        switch (index) {
            case 0: sendCommandToChat("/spoff"); showTpToPlayer(playerId); break;
            case 1: sendCommandToChat("/spoff"); showKeyboardView(); break;
            case 2: selectedPunishmentId = playerId; showPunishmentTypes(); break;
            case 3: sendCommandToChat("/fixcar " + playerId); break;
            case 4: sendCommandToChat("/flip " + playerId); break;
            case 5: sendCommandToChat("/slap " + playerId); break;
            case 6: sendCommandToChat("/spawn " + playerId); break;
            case 7: sendCommandToChat("/stats " + playerId); break;
            case 8: sendCommandToChat("/setfuel " + playerId + " 150"); break;
            case 9: sendCommandToChat("/alic " + playerId); break;
            case 10: sendCommandToChat("/getinfo " + playerId); break;
            case 11: sendCommandToChat("/freeze " + playerId); break;
            case 12: sendCommandToChat("/gethere " + playerId); break;
            case 13: showCarIdInputForTarget("/getcar", playerId, false); break;
            case 14: sendCommandToChat("/weap " + playerId); break;
            case 15: sendCommandToChat("/uncuff " + playerId); break;
            case 16: sendCommandToChat("/rgun " + playerId); break;
            case 17: sendCommandToChat("/a " + playerId + " Нуждается в помощи"); break;
        }
    }

    private void showFollowViolatorActions(final String playerId, final String violatorId) {
        if (menuLayout == null) return;
        menuLayout.removeAllViews();
        menuLayout.setVisibility(View.VISIBLE);
        requestHideSelf(0);

        menuLayout.addView(makeTitle("НАРУШИТЕЛЬ: " + violatorId));

        String[] buttons = {
                "Наказан", "Не нарушает", "Тп к игроку", "Выйти из слежки",
                "Наказать", "Чинить", "Перевернуть", "Подкинуть",
                "Спавн", "Статистика", "Заправить", "Лицензии",
                "Наказания", "Заморозить", "Тп икс", "Тп ткс",
                "Оружия игрока", "Офф Наручники", "Забрать оружие", "Отправить в /a"
        };

        ScrollView sv = new ScrollView(this);
        sv.setLayoutParams(new LinearLayout.LayoutParams(-1, -1));
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        sv.addView(c);

        int buttonH = getButtonHeight(60);
        float textSize = getFontMultiplier() * 10f;

        for (int row = 0; row < 4; row++) {
            LinearLayout rowLayout = new LinearLayout(this);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            rowLayout.setWeightSum(5f);
            for (int col = 0; col < 5; col++) {
                int idx = row * 5 + col;
                if (idx >= buttons.length) break;
                Button btn = new Button(this);
                btn.setText(buttons[idx]);
                btn.setLayoutParams(new LinearLayout.LayoutParams(0, buttonH, 1f));
                btn.setBackground(GlassUI.glassSelector(getApplicationContext(), PURPLE_BTN));
                btn.setTextColor(WHITE);
                btn.setTextSize(textSize);
                btn.setPadding(2, 2, 2, 2);
                btn.setMaxLines(2);
                btn.setGravity(Gravity.CENTER);
                btn.setMinHeight(buttonH);
                final int i = idx;
                btn.setOnClickListener(v ->
                        handleFollowViolatorAction(i, playerId, violatorId));
                rowLayout.addView(btn);
                if (col < 4 && idx < buttons.length - 1) {
                    View sep = new View(this);
                    sep.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(2), buttonH));
                    rowLayout.addView(sep);
                }
            }
            c.addView(rowLayout);
            if (row < 3) {
                View sep = new View(this);
                sep.setLayoutParams(new LinearLayout.LayoutParams(-1, 2));
                sep.setBackground(GlassUI.glassSelector(getApplicationContext(), PURPLE_DARK));
                c.addView(sep);
            }
        }
        menuLayout.addView(sv);

        menuLayout.addView(makeBackButton());
        updateMenuButtonsHeight(menuLayout, getButtonHeight(55));
        applyKeyboardTransparency(keyboardTransparency);
    }

    private void handleFollowViolatorAction(int index, String playerId, String violatorId) {
        isViolatorMode = true;
        currentPlayerId = playerId;
        currentTargetId = violatorId;
        switch (index) {
            case 0: sendCommandToChat("/ans " + playerId
                    + " Игрок с id [" + violatorId + "] наказан, приятной игры."); break;
            case 1: sendCommandToChat("/ans " + playerId
                    + " Игрок с id [" + violatorId + "] не нарушает, приятной игры."); break;
            case 2: sendCommandToChat("/spoff"); showTpToPlayer(violatorId); break;
            case 3: sendCommandToChat("/spoff"); showKeyboardView(); break;
            case 4: selectedPunishmentId = violatorId; showPunishmentTypes(); break;
            case 5: sendCommandToChat("/fixcar " + violatorId); break;
            case 6: sendCommandToChat("/flip " + violatorId); break;
            case 7: sendCommandToChat("/slap " + violatorId); break;
            case 8: sendCommandToChat("/spawn " + violatorId); break;
            case 9: sendCommandToChat("/stats " + violatorId); break;
            case 10: sendCommandToChat("/setfuel " + violatorId + " 150"); break;
            case 11: sendCommandToChat("/alic " + violatorId); break;
            case 12: sendCommandToChat("/getinfo " + violatorId); break;
            case 13: sendCommandToChat("/freeze " + violatorId); break;
            case 14: sendCommandToChat("/gethere " + violatorId); break;
            case 15: showCarIdInputForTarget("/getcar", violatorId, true); break;
            case 16: sendCommandToChat("/weap " + violatorId); break;
            case 17: sendCommandToChat("/uncuff " + violatorId); break;
            case 18: sendCommandToChat("/rgun " + violatorId); break;
            case 19: sendCommandToChat("/a " + violatorId + " Нуждается в помощи"); break;
        }
    }

    private void showTpToPlayer(final String playerId) {
        if (menuLayout == null) return;
        menuLayout.removeAllViews();
        menuLayout.setVisibility(View.VISIBLE);
        requestHideSelf(0);

        menuLayout.addView(makeTitle("ТП К ИГРОКУ"));

        Button btnTp = new Button(this);
        btnTp.setText("ТП к игроку");
        btnTp.setLayoutParams(new LinearLayout.LayoutParams(-1, getButtonHeight(60)));
        btnTp.setBackground(GlassUI.glassSelector(getApplicationContext(), GREEN_OK));
        btnTp.setTextColor(WHITE);
        btnTp.setTextSize(getFontMultiplier() * 14f);
        btnTp.setOnClickListener(v -> {
            sendCommandToChat("/goto " + playerId);
            showKeyboardView();
        });
        menuLayout.addView(btnTp);

        Button back = makeBackButton();
        back.setOnClickListener(v -> showFollowPlayerActions(playerId));
        menuLayout.addView(back);
        updateMenuButtonsHeight(menuLayout, getButtonHeight(50));
        applyKeyboardTransparency(keyboardTransparency);
    }

    private void showCarIdInputForTarget(final String command, final String targetId,
                                         final boolean isViolator) {
        showGenericIdInput("ВВЕДИТЕ ID ТС", "ID транспорта", id -> {
            sendCommandToChat(command + " " + id);
            if (isViolator) showFollowViolatorActions(currentPlayerId, currentTargetId);
            else showFollowPlayerActions(targetId);
        });
    }

    // ==================== ID INPUT ====================

    private void showGenericIdInput(String titleText, String hintText,
                                    final CommandCallback callback) {
        if (menuLayout == null) return;
        menuLayout.removeAllViews();
        menuLayout.setVisibility(View.VISIBLE);
        requestHideSelf(0);

        menuLayout.addView(makeTitle(titleText));

        final EditText idInput = new EditText(this);
        idInput.setBackground(GlassUI.glassSelector(getApplicationContext(), PURPLE_PANEL));
        idInput.setTextColor(WHITE);
        idInput.setHintTextColor(PURPLE_LIGHT);
        idInput.setHint(hintText);
        idInput.setInputType(2);
        idInput.setPadding(10, 10, 10, 10);
        idInput.setTextSize(getFontMultiplier() * 14f);
        idInput.setGravity(Gravity.CENTER);
        menuLayout.addView(idInput, new LinearLayout.LayoutParams(-1, -2));

        String[] numbers = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "C", "0", "⌫"};
        int buttonH = getButtonHeight(50);
        float textSize = getFontMultiplier() * 14f;

        for (int row = 0; row < 4; row++) {
            LinearLayout rowLayout = new LinearLayout(this);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            rowLayout.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
            for (int col = 0; col < 3; col++) {
                int idx = row * 3 + col;
                if (idx >= numbers.length) break;
                final String num = numbers[idx];
                Button btn = new Button(this);
                btn.setText(num);
                btn.setLayoutParams(new LinearLayout.LayoutParams(0, buttonH, 1f));
                if (num.equals("C") || num.equals("⌫")) {
                    btn.setBackground(GlassUI.glassSelector(getApplicationContext(), RED_DANGER));
                } else {
                    btn.setBackground(GlassUI.glassSelector(getApplicationContext(), PURPLE_BTN));
                }
                btn.setTextColor(WHITE);
                btn.setTextSize(textSize);
                btn.setOnClickListener(v -> {
                    if (num.equals("C")) idInput.setText("");
                    else if (num.equals("⌫")) {
                        String cur = idInput.getText().toString();
                        if (cur.length() > 0)
                            idInput.setText(cur.substring(0, cur.length() - 1));
                    } else idInput.append(num);
                });
                rowLayout.addView(btn);
                if (col < 2) {
                    View sep = new View(this);
                    sep.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(3), buttonH));
                    rowLayout.addView(sep);
                }
            }
            menuLayout.addView(rowLayout);
        }

        LinearLayout btnRow = new LinearLayout(this);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);
        btnRow.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));

        Button btnBack = new Button(this);
        btnBack.setText("← Назад");
        btnBack.setLayoutParams(new LinearLayout.LayoutParams(0, buttonH, 1f));
        btnBack.setBackground(GlassUI.glassSelector(getApplicationContext(), PURPLE_DARK));
        btnBack.setTextColor(WHITE);
        btnBack.setTextSize(textSize);
        btnBack.setOnClickListener(v -> showKeyboardView());
        btnRow.addView(btnBack);

        View sep = new View(this);
        sep.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(5), buttonH));
        btnRow.addView(sep);

        Button btnSend = new Button(this);
        btnSend.setText("Отправить");
        btnSend.setLayoutParams(new LinearLayout.LayoutParams(0, buttonH, 1f));
        btnSend.setBackground(GlassUI.glassSelector(getApplicationContext(), GREEN_OK));
        btnSend.setTextColor(WHITE);
        btnSend.setTextSize(textSize);
        btnSend.setOnClickListener(v -> {
            String id = idInput.getText().toString();
            if (id.isEmpty()) {
                Toast.makeText(this, "❌ Введите ID", Toast.LENGTH_SHORT).show();
                return;
            }
            callback.onCommand(id);
        });
        btnRow.addView(btnSend);
        menuLayout.addView(btnRow);

        updateMenuButtonsHeight(menuLayout, buttonH);
        applyKeyboardTransparency(keyboardTransparency);
    }

    // ==================== СЛЕЖКА / СПАВН ====================

    private void showSpectateMenu() {
        if (menuLayout == null) return;
        menuLayout.removeAllViews();
        menuLayout.setVisibility(View.VISIBLE);
        requestHideSelf(0);
        menuLayout.addView(makeTitle("СЛЕЖКА"));

        int buttonH = getButtonHeight(70);

        Button btnSpectate = new Button(this);
        btnSpectate.setText("👁️ Следить");
        btnSpectate.setLayoutParams(new LinearLayout.LayoutParams(-1, buttonH));
        btnSpectate.setBackground(GlassUI.glassSelector(getApplicationContext(), GREEN_OK));
        btnSpectate.setTextColor(WHITE);
        btnSpectate.setTextSize(getFontMultiplier() * 16f);
        btnSpectate.setOnClickListener(v -> showSpectateIdInput());
        menuLayout.addView(btnSpectate);

        Button btnStop = new Button(this);
        btnStop.setText("⏹️ Выйти из слежки");
        btnStop.setLayoutParams(new LinearLayout.LayoutParams(-1, buttonH));
        btnStop.setBackground(GlassUI.glassSelector(getApplicationContext(), RED_DANGER));
        btnStop.setTextColor(WHITE);
        btnStop.setTextSize(getFontMultiplier() * 16f);
        btnStop.setOnClickListener(v -> {
            sendCommandToChat("/spoff");
            showKeyboardView();
        });
        menuLayout.addView(btnStop);

        menuLayout.addView(makeBackButton());
        updateMenuButtonsHeight(menuLayout, getButtonHeight(55));
        applyKeyboardTransparency(keyboardTransparency);
    }

    private void showSpectateIdInput() {
        showGenericIdInput("ВВЕДИТЕ ID", "ID игрока", id -> {
            sendCommandToChat("/sp " + id);
            showKeyboardView();
        });
    }

    private void showSpawnPointMenu() {
        if (menuLayout == null) return;
        menuLayout.removeAllViews();
        menuLayout.setVisibility(View.VISIBLE);
        requestHideSelf(0);
        menuLayout.addView(makeTitle("ТОЧКА СПАВНА"));

        int buttonH = getButtonHeight(60);
        float textSize = getFontMultiplier() * 14f;

        String[][] items = {
                {"1. Аз", "/az"},
                {"2. Спавн", "spawn"},
                {"3. Марк", "/mark"},
                {"4. ТаМарк", "/gotomark"}
        };

        for (String[] item : items) {
            Button btn = new Button(this);
            btn.setText(item[0]);
            btn.setLayoutParams(new LinearLayout.LayoutParams(-1, buttonH));
            btn.setBackground(GlassUI.glassSelector(getApplicationContext(), PURPLE_BTN));
            btn.setTextColor(WHITE);
            btn.setTextSize(textSize);
            if (item[1].equals("spawn")) {
                btn.setOnClickListener(v -> showGenericIdInput("СПАВН", "ID игрока", id -> {
                    sendCommandToChat("/spawn " + id);
                    showKeyboardView();
                }));
            } else {
                btn.setOnClickListener(v -> {
                    sendCommandToChat(item[1]);
                    showKeyboardView();
                });
            }
            menuLayout.addView(btn);

            View sep = new View(this);
            sep.setLayoutParams(new LinearLayout.LayoutParams(-1, 2));
            sep.setBackground(GlassUI.glassSelector(getApplicationContext(), PURPLE_DARK));
            menuLayout.addView(sep);
        }

        menuLayout.addView(makeBackButton());
        updateMenuButtonsHeight(menuLayout, getButtonHeight(50));
        applyKeyboardTransparency(keyboardTransparency);
    }

    // ==================== ЧИТЫ ====================

    private void showCheatsMenu() {
        if (menuLayout == null) return;
        menuLayout.removeAllViews();
        menuLayout.setVisibility(View.VISIBLE);
        requestHideSelf(0);
        menuLayout.addView(makeTitle("ЧИТЫ"));

        int buttonH = getButtonHeight(70);
        float textSize = getFontMultiplier() * 14f;

        String[][] cheats = {
                {"1. Бессмертие", "/gm"},
                {"2. Маска", "/clist"},
                {"3. Выдать ХП", "hp"},
                {"4. ID ТС", "/dl"}
        };
        for (String[] c : cheats) {
            Button btn = new Button(this);
            btn.setText(c[0]);
            btn.setLayoutParams(new LinearLayout.LayoutParams(-1, buttonH));
            btn.setBackground(GlassUI.glassSelector(getApplicationContext(), PURPLE_BTN));
            btn.setTextColor(WHITE);
            btn.setTextSize(textSize);
            if (c[1].equals("hp")) {
                btn.setOnClickListener(v -> showGenericIdInput("ВЫДАТЬ ХП", "ID игрока", id -> {
                    sendCommandToChat("/hp " + id + " 100");
                    showKeyboardView();
                }));
            } else {
                btn.setOnClickListener(v -> {
                    sendCommandToChat(c[1]);
                    showKeyboardView();
                });
            }
            menuLayout.addView(btn);
            View sep = new View(this);
            sep.setLayoutParams(new LinearLayout.LayoutParams(-1, 2));
            sep.setBackground(GlassUI.glassSelector(getApplicationContext(), PURPLE_DARK));
            menuLayout.addView(sep);
        }

        menuLayout.addView(makeBackButton());
        updateMenuButtonsHeight(menuLayout, getButtonHeight(50));
        applyKeyboardTransparency(keyboardTransparency);
    }

    // ==================== СТАТИСТИКА ====================

    private void showStatisticsMenu() {
        if (menuLayout == null) return;
        menuLayout.removeAllViews();
        menuLayout.setVisibility(View.VISIBLE);
        requestHideSelf(0);
        checkAndResetWeeklyStats();
        menuLayout.addView(makeTitle("СТАТИСТИКА РЕПОРТОВ"));

        SharedPreferences sp = getSharedPreferences(PREFS_STATS, 0);
        String[] days = {"Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"};
        int totalWeek = 0;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            int c = sp.getInt(KEY_WEEKLY_PREFIX + i, 0);
            totalWeek += c;
            sb.append(days[i]).append(" — ").append(c).append("\n");
        }
        int all = sp.getInt(KEY_TOTAL_REPORTS, 0);

        TextView tv = new TextView(this);
        tv.setText(sb + "\nИтог — " + totalWeek + "\nЗа всё время — " + all);
        tv.setTextColor(WHITE);
        tv.setTextSize(getFontMultiplier() * 14f);
        tv.setPadding(10, 10, 10, 10);
        tv.setBackground(GlassUI.glassSelector(getApplicationContext(), PURPLE_BG));
        menuLayout.addView(tv);

        Button reset = new Button(this);
        reset.setText("🔄 Сбросить неделю");
        reset.setLayoutParams(new LinearLayout.LayoutParams(-1, getButtonHeight(50)));
        reset.setBackground(GlassUI.glassSelector(getApplicationContext(), RED_DANGER));
        reset.setTextColor(WHITE);
        reset.setTextSize(getFontMultiplier() * 14f);
        reset.setOnClickListener(v -> { resetWeeklyStatsManual(); showStatisticsMenu(); });
        menuLayout.addView(reset);

        menuLayout.addView(makeBackButton());
        updateMenuButtonsHeight(menuLayout, getButtonHeight(50));
        applyKeyboardTransparency(keyboardTransparency);
    }

    private void checkAndResetWeeklyStats() {
        SharedPreferences sp = getSharedPreferences(PREFS_STATS, 0);
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("Europe/Moscow"));
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("Europe/Moscow"));
        String today = sdf.format(new Date());
        String last = sp.getString(KEY_LAST_RESET_DATE, "");

        int dow = cal.get(Calendar.DAY_OF_WEEK);
        int mondayIdx = (dow == Calendar.SUNDAY) ? 6 : dow - 2;
        if (!last.equals(today) && mondayIdx == 0) {
            SharedPreferences.Editor e = sp.edit();
            for (int i = 0; i < 7; i++) e.putInt(KEY_WEEKLY_PREFIX + i, 0);
            e.putString(KEY_LAST_RESET_DATE, today).apply();
        }
    }

    private void resetWeeklyStatsManual() {
        SharedPreferences sp = getSharedPreferences(PREFS_STATS, 0);
        SharedPreferences.Editor e = sp.edit();
        for (int i = 0; i < 7; i++) e.putInt(KEY_WEEKLY_PREFIX + i, 0);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("Europe/Moscow"));
        e.putString(KEY_LAST_RESET_DATE, sdf.format(new Date())).apply();
    }

    private void incrementReportStat() {
        SharedPreferences sp = getSharedPreferences(PREFS_STATS, 0);
        SharedPreferences.Editor e = sp.edit();
        e.putInt(KEY_TOTAL_REPORTS, sp.getInt(KEY_TOTAL_REPORTS, 0) + 1);
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("Europe/Moscow"));
        int dow = cal.get(Calendar.DAY_OF_WEEK);
        int idx = (dow == Calendar.SUNDAY) ? 6 : dow - 2;
        e.putInt(KEY_WEEKLY_PREFIX + idx, sp.getInt(KEY_WEEKLY_PREFIX + idx, 0) + 1);
        e.apply();
    }

    // ==================== ТП К СЕБЕ ====================

    private void showTpToMeMenu() {
        if (menuLayout == null) return;
        menuLayout.removeAllViews();
        menuLayout.setVisibility(View.VISIBLE);
        requestHideSelf(0);
        menuLayout.addView(makeTitle("ТП К СЕБЕ"));

        int buttonH = getButtonHeight(70);
        float textSize = getFontMultiplier() * 14f;

        Button btnCar = new Button(this);
        btnCar.setText("1. Тп ТС к себе");
        btnCar.setLayoutParams(new LinearLayout.LayoutParams(-1, buttonH));
        btnCar.setBackground(GlassUI.glassSelector(getApplicationContext(), PURPLE_BTN));
        btnCar.setTextColor(WHITE);
        btnCar.setTextSize(textSize);
        btnCar.setOnClickListener(v -> showGenericIdInput("ТП ТС К СЕБЕ", "ID транспорта",
                id -> sendCommandToChat("/getcar " + id)));
        menuLayout.addView(btnCar);

        Button btnPlayer = new Button(this);
        btnPlayer.setText("2. Тп игрока к себе");
        btnPlayer.setLayoutParams(new LinearLayout.LayoutParams(-1, buttonH));
        btnPlayer.setBackground(GlassUI.glassSelector(getApplicationContext(), PURPLE_BTN));
        btnPlayer.setTextColor(WHITE);
        btnPlayer.setTextSize(textSize);
        btnPlayer.setOnClickListener(v -> showGenericIdInput("ТП ИГРОКА К СЕБЕ", "ID игрока",
                id -> sendCommandToChat("/gethere " + id)));
        menuLayout.addView(btnPlayer);

        Button btnGoTo = new Button(this);
        btnGoTo.setText("3. Тп к игроку");
        btnGoTo.setLayoutParams(new LinearLayout.LayoutParams(-1, buttonH));
        btnGoTo.setBackground(GlassUI.glassSelector(getApplicationContext(), PURPLE_BTN));
        btnGoTo.setTextColor(WHITE);
        btnGoTo.setTextSize(textSize);
        btnGoTo.setOnClickListener(v -> showGenericIdInput("ТП К ИГРОКУ", "ID игрока",
                id -> sendCommandToChat("/goto " + id)));
        menuLayout.addView(btnGoTo);

        menuLayout.addView(makeBackButton());
        updateMenuButtonsHeight(menuLayout, getButtonHeight(50));
        applyKeyboardTransparency(keyboardTransparency);
    }

    // ==================== БЫСТРАЯ КОМАНДА ====================

    private void showQuickCommandMenu() {
        if (menuLayout == null) return;
        menuLayout.removeAllViews();
        menuLayout.setVisibility(View.VISIBLE);
        requestHideSelf(0);
        menuLayout.addView(makeTitle("БЫСТРАЯ КОМАНДА"));

        String[] names = {
                "Спавн ТС (Радиус)", "Спавн ТС (ID)", "Гос волна",
                "Чинить", "Перевернуть", "Подкинуть", "Спавн", "Статистика",
                "Заправить", "Лицензия", "Заморозить", "Оружие игрока",
                "Офф наручники", "Забрать оружие", "Отправить в /a"
        };
        String[] commands = {
                "/spcars 3", "/spcar", "/acceptgnews",
                "/fixcar", "/flip", "/slap", "/spawn", "/stats",
                "/setfuel", "/alic", "/freeze", "/weap",
                "/uncuff", "/rgun", "/a"
        };

        ScrollView sv = new ScrollView(this);
        sv.setLayoutParams(new LinearLayout.LayoutParams(-1, -1));
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        sv.addView(c);

        int buttonH = getButtonHeight(70);
        float textSize = getFontMultiplier() * 11f;

        for (int row = 0; row < 5; row++) {
            LinearLayout rowLayout = new LinearLayout(this);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            rowLayout.setWeightSum(3f);
            for (int col = 0; col < 3; col++) {
                int idx = row * 3 + col;
                if (idx >= names.length) break;
                final String name = names[idx];
                final String cmd = commands[idx];
                Button btn = new Button(this);
                btn.setText(name);
                btn.setLayoutParams(new LinearLayout.LayoutParams(0, buttonH, 1f));
                btn.setBackground(GlassUI.glassSelector(getApplicationContext(), PURPLE_BTN));
                btn.setTextColor(WHITE);
                btn.setTextSize(textSize);
                btn.setPadding(3, 3, 3, 3);
                btn.setMaxLines(2);
                btn.setGravity(Gravity.CENTER);
                btn.setOnClickListener(v -> {
                    if (name.equals("Спавн ТС (Радиус)") || name.equals("Гос волна")) {
                        sendCommandToChat(cmd);
                        showKeyboardView();
                    } else if (name.equals("Заправить")) {
                        showGenericIdInput("ЗАПРАВИТЬ (150)", "ID игрока",
                                id -> sendCommandToChat(cmd + " " + id + " 150"));
                    } else {
                        final String base = cmd;
                        final String n = name;
                        showGenericIdInput(n.toUpperCase(), "ID игрока", id -> {
                            String finalCmd = n.equals("Отправить в /a")
                                    ? base + " " + id + " Нуждается в помощи"
                                    : base + " " + id;
                            sendCommandToChat(finalCmd);
                        });
                    }
                });
                rowLayout.addView(btn);
                if (col < 2 && idx < names.length - 1) {
                    View sep = new View(this);
                    sep.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(3), buttonH));
                    rowLayout.addView(sep);
                }
            }
            c.addView(rowLayout);
            if (row < 4) {
                View sep = new View(this);
                sep.setLayoutParams(new LinearLayout.LayoutParams(-1, 3));
                sep.setBackground(GlassUI.glassSelector(getApplicationContext(), PURPLE_DARK));
                c.addView(sep);
            }
        }
        menuLayout.addView(sv);

        menuLayout.addView(makeBackButton());
        updateMenuButtonsHeight(menuLayout, getButtonHeight(55));
        applyKeyboardTransparency(keyboardTransparency);
    }

    // ==================== БЫСТРЫЙ ОТВЕТ ====================

    private void showQuickAnswersMenu() {
        if (menuLayout == null) return;
        menuLayout.removeAllViews();
        menuLayout.setVisibility(View.VISIBLE);
        requestHideSelf(0);
        menuLayout.addView(makeTitle("БЫСТРЫЙ ОТВЕТ"));

        try {
            JSONArray arr = new JSONArray(prefs.getString("quick_answers", "[]"));
            if (arr.length() == 0) {
                TextView tv = new TextView(this);
                tv.setText("Нет быстрых ответов.\nДобавьте в редакторе.");
                tv.setTextColor(WHITE);
                tv.setGravity(Gravity.CENTER);
                tv.setPadding(20, 60, 20, 60);
                menuLayout.addView(tv);
            } else {
                ScrollView sv = new ScrollView(this);
                sv.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
                LinearLayout c = new LinearLayout(this);
                c.setOrientation(LinearLayout.VERTICAL);
                sv.addView(c);

                int buttonH = getButtonHeight(70);
                float textSize = getFontMultiplier() * 11f;

                int rows = (arr.length() + 3) / 4;
                for (int row = 0; row < rows; row++) {
                    LinearLayout rowLayout = new LinearLayout(this);
                    rowLayout.setOrientation(LinearLayout.HORIZONTAL);
                    rowLayout.setWeightSum(4f);
                    for (int col = 0; col < 4; col++) {
                        int idx = row * 4 + col;
                        if (idx >= arr.length()) break;
                        JSONObject o = arr.getJSONObject(idx);
                        final String name = o.optString("name", "Ответ");
                        final String answer = o.optString("answer", "");
                        final String command = o.optString("command", "");
                        Button btn = new Button(this);
                        btn.setText(name);
                        btn.setLayoutParams(new LinearLayout.LayoutParams(0, buttonH, 1f));
                        btn.setBackground(GlassUI.glassSelector(getApplicationContext(), PURPLE_BTN));
                        btn.setTextColor(WHITE);
                        btn.setTextSize(textSize);
                        btn.setPadding(3, 3, 3, 3);
                        btn.setMaxLines(2);
                        btn.setGravity(Gravity.CENTER);
                        btn.setOnClickListener(v ->
                                showQuickAnswerIdInput(command, answer, name));
                        rowLayout.addView(btn);
                        if (col < 3 && idx < arr.length() - 1) {
                            View sep = new View(this);
                            sep.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(3), buttonH));
                            rowLayout.addView(sep);
                        }
                    }
                    c.addView(rowLayout);
                    if (row < rows - 1) {
                        View sep = new View(this);
                        sep.setLayoutParams(new LinearLayout.LayoutParams(-1, 3));
                        sep.setBackground(GlassUI.glassSelector(getApplicationContext(), PURPLE_DARK));
                        c.addView(sep);
                    }
                }
                menuLayout.addView(sv);
            }
        } catch (Exception ignored) { }

        menuLayout.addView(makeBackButton());
        updateMenuButtonsHeight(menuLayout, getButtonHeight(55));
        applyKeyboardTransparency(keyboardTransparency);
    }

    private void showQuickAnswerIdInput(final String command, final String answerText,
                                        String answerName) {
        showGenericIdInput(answerName.toUpperCase(), "ID игрока", id -> {
            String finalCmd;
            if (command == null || command.isEmpty()) {
                finalCmd = "/ans " + id + " " + answerText;
            } else {
                finalCmd = command + " " + id + " " + answerText;
            }
            sendCommandToChat(finalCmd);
        });
    }

    // ==================== ТЕЛЕПОРТ ====================

    private void showTeleportMenu() {
        if (menuLayout == null) return;
        menuLayout.removeAllViews();
        menuLayout.setVisibility(View.VISIBLE);
        requestHideSelf(0);
        menuLayout.addView(makeTitle("ТЕЛЕПОРТ"));

        String[] categories = {
                "🏛️ Общественные места", "🚉 Вокзалы", "🚗 Автосалоны / Стоянки",
                "🏛️ Гос. организации", "👹 Криминальные организации",
                "👷 Начальные работы", "⚙️ Работы", "🎭 Развлечения",
                "🚁 Транспортные узлы", "💰 Скупки", "📍 Ближайшие места"
        };

        ScrollView sv = new ScrollView(this);
        sv.setLayoutParams(new LinearLayout.LayoutParams(-1, -1));
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        sv.addView(c);

        int buttonH = getButtonHeight(70);
        float textSize = getFontMultiplier() * 12f;

        int rows = (categories.length + 1) / 2;
        for (int row = 0; row < rows; row++) {
            LinearLayout rowLayout = new LinearLayout(this);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            rowLayout.setWeightSum(2f);
            for (int col = 0; col < 2; col++) {
                int idx = row * 2 + col;
                if (idx >= categories.length) break;
                final String category = categories[idx];
                Button btn = new Button(this);
                btn.setText(category);
                btn.setLayoutParams(new LinearLayout.LayoutParams(0, buttonH, 1f));
                btn.setBackground(GlassUI.glassSelector(getApplicationContext(), PURPLE_BTN));
                btn.setTextColor(WHITE);
                btn.setTextSize(textSize);
                btn.setPadding(5, 5, 5, 5);
                btn.setMaxLines(2);
                btn.setGravity(Gravity.CENTER);
                btn.setOnClickListener(v -> showCoordinatesByCategory(category));
                rowLayout.addView(btn);
                if (col == 0 && idx < categories.length - 1) {
                    View sep = new View(this);
                    sep.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(5), buttonH));
                    rowLayout.addView(sep);
                }
            }
            c.addView(rowLayout);
            if (row < rows - 1) {
                View sep = new View(this);
                sep.setLayoutParams(new LinearLayout.LayoutParams(-1, 5));
                sep.setBackground(GlassUI.glassSelector(getApplicationContext(), PURPLE_DARK));
                c.addView(sep);
            }
        }
        menuLayout.addView(sv);

        menuLayout.addView(makeBackButton());
        updateMenuButtonsHeight(menuLayout, getButtonHeight(55));
        applyKeyboardTransparency(keyboardTransparency);
    }

    private void showCoordinatesByCategory(String category) {
        if (menuLayout == null) return;
        menuLayout.removeAllViews();
        menuLayout.setVisibility(View.VISIBLE);
        requestHideSelf(0);
        menuLayout.addView(makeTitle(category));

        String[][] coords = getCoordinatesByCategory(category);
        ScrollView sv = new ScrollView(this);
        sv.setLayoutParams(new LinearLayout.LayoutParams(-1, -1));
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        sv.addView(c);

        if (coords.length == 0) {
            TextView tv = new TextView(this);
            tv.setText("Нет координат в этой категории");
            tv.setTextColor(WHITE);
            tv.setGravity(Gravity.CENTER);
            tv.setPadding(10, 60, 10, 60);
            c.addView(tv);
        } else {
            int buttonH = getButtonHeight(70);
            float textSize = getFontMultiplier() * 11f;
            int rows = (coords.length + 1) / 2;
            for (int row = 0; row < rows; row++) {
                LinearLayout rowLayout = new LinearLayout(this);
                rowLayout.setOrientation(LinearLayout.HORIZONTAL);
                rowLayout.setWeightSum(2f);
                for (int col = 0; col < 2; col++) {
                    int idx = row * 2 + col;
                    if (idx >= coords.length) break;
                    final String name = coords[idx][0];
                    final String x = coords[idx][1];
                    final String y = coords[idx][2];
                    final String z = coords[idx][3];
                    Button btn = new Button(this);
                    btn.setText(name);
                    btn.setLayoutParams(new LinearLayout.LayoutParams(0, buttonH, 1f));
                    btn.setBackground(GlassUI.glassSelector(getApplicationContext(), PURPLE_BTN));
                    btn.setTextColor(WHITE);
                    btn.setTextSize(textSize);
                    btn.setPadding(5, 5, 5, 5);
                    btn.setMaxLines(2);
                    btn.setGravity(Gravity.CENTER);
                    btn.setOnClickListener(v -> {
                        String cmd = "/pos " + x + " " + y + " " + z;
                        sendCommandToChat(cmd);
                        showKeyboardView();
                    });
                    rowLayout.addView(btn);
                    if (col == 0 && idx < coords.length - 1) {
                        View sep = new View(this);
                        sep.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(5), buttonH));
                        rowLayout.addView(sep);
                    }
                }
                c.addView(rowLayout);
                if (row < rows - 1) {
                    View sep = new View(this);
                    sep.setLayoutParams(new LinearLayout.LayoutParams(-1, 5));
                    sep.setBackground(GlassUI.glassSelector(getApplicationContext(), PURPLE_DARK));
                    c.addView(sep);
                }
            }
        }
        menuLayout.addView(sv);

        Button back = makeBackButton();
        back.setOnClickListener(v -> showTeleportMenu());
        menuLayout.addView(back);
        updateMenuButtonsHeight(menuLayout, getButtonHeight(55));
        applyKeyboardTransparency(keyboardTransparency);
    }

    private String[][] getCoordinatesByCategory(String category) {
        switch (category) {
            case "🏛️ Общественные места":
                return new String[][]{
                        {"Автошкола", "487.36", "2282.68", "13"},
                        {"Банк Южный", "2376.49", "-2141.47", "22"},
                        {"Банк Арзамас", "-139.42", "593.89", "13"},
                        {"Банк Батырево", "1852.34", "2041.30", "16"},
                        {"Авторынок", "805.11", "2294.79", "19"},
                        {"Почтовый отдел", "794.62", "1351.55", "15"},
                        {"Торговый центр", "1200.50", "-1500.25", "18"},
                        {"Стадион", "1800.75", "800.30", "14"}
                };
            case "🚉 Вокзалы":
                return new String[][]{
                        {"Вокзал Арзамас", "-560.82", "931.22", "13"},
                        {"Вокзал Южный", "2745.63", "-2450.74", "22"},
                        {"Вокзал Батырево", "1813.30", "2513.97", "16"},
                        {"Вокзал Эдово", "-2476.94", "2838.97", "38"},
                        {"ЖД Вокзал Южный", "2498.34", "-2146.55", "23"},
                        {"Автовокзал Центральный", "950.25", "650.80", "12"}
                };
            case "🚗 Автосалоны / Стоянки":
                return new String[][]{
                        {"Автосалон Эконом", "2326.82", "-1804.21", "22.45"},
                        {"Автосалон Комфорт", "2215.55", "2890.65", "12.13"},
                        {"Автосалон Премиум", "601.94", "1002.13", "12.00"},
                        {"Мотосалон", "341.13", "480.46", "12.30"},
                        {"Стоянка г.Арзамас", "421.53", "586.04", "11.98"},
                        {"Стоянка пгт.Батырево", "1749.30", "2464.37", "15.70"},
                        {"Стоянка г.Эдово", "-2404.05", "2681.66", "39.16"},
                        {"СТО Реактор", "711.90", "525.43", "12.00"}
                };
            case "🏛️ Гос. организации":
                return new String[][]{
                        {"Правительство", "-79.21", "840.72", "18"},
                        {"Военная часть", "1897.71", "1722.09", "16"},
                        {"Больница Арзамас", "367.82", "1328.02", "12"},
                        {"Больница Южный", "2112.90", "-2388.44", "22"},
                        {"Новостная Сеть", "2133.40", "-1957.60", "21"},
                        {"Полиция Арзамас", "160.97", "1261.24", "13"},
                        {"Полиция Южный", "2578.18", "-2416.08", "22"},
                        {"ФСБ", "1869.18", "-2001.23", "32"},
                        {"Мэрия", "450.25", "1200.75", "15"}
                };
            case "👹 Криминальные организации":
                return new String[][]{
                        {"Тамбовская", "2423.09", "-1926.12", "23"},
                        {"Курганская", "2285.63", "1368.19", "12"},
                        {"Ореховская", "-283.71", "514.44", "14"},
                        {"Кавказ", "-2336.44", "31.38", "22"},
                        {"Черный рынок", "-2246.85", "240.62", "25"},
                        {"Подпольный клуб", "-1800.50", "450.25", "10"}
                };
            case "👷 Начальные работы":
                return new String[][]{
                        {"Завод Тесла", "-2541.26", "531.91", "10"},
                        {"Завод Твикс", "2264.48", "2150.62", "17"},
                        {"Шахта", "-1106.00", "1367.61", "32"},
                        {"Ферма", "950.72", "-915.00", "40"},
                        {"Сортировочный Центр", "794.62", "1351.55", "15"},
                        {"Нефтяная вышка", "-1500.25", "2000.75", "35"}
                };
            case "⚙️ Работы":
                return new String[][]{
                        {"Такси", "762.17", "757.65", "13"},
                        {"Водитель автобуса", "783.76", "752.21", "13"},
                        {"Почтальон", "794.62", "1351.55", "15"},
                        {"Механик", "2150.53", "-1856.45", "19"},
                        {"Инкассатор", "1792.96", "-2278.38", "12"},
                        {"Дорожная служба Южного", "2649.64", "-1900.44", "23"},
                        {"Дорожная служба Арзамаса", "637.71", "899.11", "13"},
                        {"Мусоровоз", "-1660.12", "-955.61", "51"}
                };
            case "🎭 Развлечения":
                return new String[][]{
                        {"Grand Casino", "334.56", "2787.51", "7.84"},
                        {"Аукцион контейнеров", "463.24", "-393.23", "8.86"},
                        {"Дрифт зона", "2330.98", "1215.69", "19.85"},
                        {"Ночной клуб", "1200.25", "-800.75", "12"},
                        {"Кинотеатр", "850.50", "950.25", "11"},
                        {"Боулинг", "650.75", "-1200.50", "13"}
                };
            case "🚁 Транспортные узлы":
                return new String[][]{
                        {"Вертолетная Южный", "2800.25", "-2300.75", "25"},
                        {"Вертолетная Арзамас", "-300.50", "700.25", "20"},
                        {"Морской порт Южный", "2900.75", "-2600.25", "5"},
                        {"Яхт-клуб", "-2000.25", "500.75", "3"},
                        {"Аэропорт", "3000.50", "-2800.75", "12"}
                };
            case "💰 Скупки":
                return new String[][]{
                        {"Скупка металла", "-1700.25", "300.75", "12"},
                        {"Скупка авто", "2000.50", "-1700.25", "15"},
                        {"Скупка электроники", "-1200.75", "800.25", "11"},
                        {"Ломбард", "750.25", "1100.75", "10"},
                        {"Антикварный магазин", "-800.50", "1200.25", "9"}
                };
            case "📍 Ближайшие места":
                return new String[][]{
                        {"Ближайший магазин", "200.25", "300.75", "10"},
                        {"Ближайшая заправка", "450.50", "150.25", "10"},
                        {"Ближайший парк", "100.75", "500.25", "9"},
                        {"Ближайшая больница", "350.25", "250.75", "11"},
                        {"Ближайший банк", "280.50", "320.25", "10"}
                };
            default:
                return new String[0][0];
        }
    }

    // ==================== ЛОВЛЯ РЕПОРТОВ ====================

    private void showCaughtReports() {
        if (menuLayout == null) return;
        menuLayout.removeAllViews();
        menuLayout.setVisibility(View.VISIBLE);
        requestHideSelf(0);
        menuLayout.addView(makeTitle("ЛОВЛЯ РЕПОРТОВ • 60 СЕК."));

        ScrollView sv = new ScrollView(this);
        sv.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        sv.addView(c);

        long now = System.currentTimeMillis();
        SharedPreferences rp = getSharedPreferences("GTAndroidReports", 0);
        JSONArray arr;
        try { arr = new JSONArray(rp.getString("recent", "[]")); }
        catch (Exception e) { arr = new JSONArray(); }

        JSONArray fresh = new JSONArray();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            if (now - o.optLong("time", 0) <= 60000L) {
                fresh.put(o);
                TextView t = new TextView(this);
                t.setText(o.optString("text", "Репорт"));
                t.setTextColor(WHITE);
                t.setTextSize(getFontMultiplier() * 12f);
                t.setPadding(15, 15, 15, 15);
                t.setBackground(GlassUI.glassSelector(getApplicationContext(), PURPLE_DARK));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                lp.bottomMargin = dpToPx(6);
                t.setLayoutParams(lp);
                c.addView(t);
            }
        }

        if (fresh.length() == 0) {
            TextView empty = new TextView(this);
            empty.setText("Репортов за последние 60 секунд нет.\nРаздел обновляется автоматически при поступлении.");
            empty.setTextColor(PURPLE_LIGHT);
            empty.setTextSize(getFontMultiplier() * 12f);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(20, 60, 20, 60);
            c.addView(empty);
        }
        menuLayout.addView(sv);

        Button refresh = new Button(this);
        refresh.setText("↻ Обновить");
        refresh.setAllCaps(false);
        refresh.setTextColor(WHITE);
        refresh.setBackground(GlassUI.glassSelector(getApplicationContext(), PURPLE_MAIN));
        refresh.setLayoutParams(new LinearLayout.LayoutParams(-1, getButtonHeight(50)));
        refresh.setOnClickListener(v -> showCaughtReports());
        menuLayout.addView(refresh);

        menuLayout.addView(makeBackButton());
        updateMenuButtonsHeight(menuLayout, getButtonHeight(50));
        applyKeyboardTransparency(keyboardTransparency);
    }

    // ==================== ADM-ФОРМЫ ====================

    private void showKeyboardAdminForms() {
        if (menuLayout == null) return;
        menuLayout.removeAllViews();
        menuLayout.setVisibility(View.VISIBLE);
        requestHideSelf(0);
        menuLayout.addView(makeTitle("ADMIN-ФОРМЫ"));

        ScrollView sv = new ScrollView(this);
        sv.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        sv.addView(list);

        java.util.ArrayList<AdminFormManager.Form> forms = AdminFormManager.getPending(this);
        if (forms.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("Новых форм пока нет");
            empty.setTextColor(PURPLE_LIGHT);
            empty.setTextSize(getFontMultiplier() * 13f);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(20, 60, 20, 60);
            list.addView(empty);
        }

        for (final AdminFormManager.Form form : forms) {
            TextView item = new TextView(this);
            item.setText("<ADM> (" + form.rank + ") " + form.nickname
                    + "[" + form.id + "]\n" + form.command + "\n\nНажмите, чтобы принять");
            item.setTextColor(WHITE);
            item.setTextSize(getFontMultiplier() * 12f);
            item.setPadding(15, 15, 15, 15);
            item.setBackground(GlassUI.glassSelector(getApplicationContext(), PURPLE_DARK));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = dpToPx(8);
            item.setLayoutParams(lp);
            item.setOnClickListener(v -> {
                String command = AdminFormManager.makeAcceptedCommand(
                        KeyboardService.this, form);
                InputConnection ic = getCurrentInputConnection();
                if (ic != null) ic.commitText(command, 1);
                AdminFormManager.removeForm(KeyboardService.this, form.uniqueKey());
                Toast.makeText(KeyboardService.this,
                        "Форма вставлена в поле ввода",
                        Toast.LENGTH_SHORT).show();
                showKeyboardAdminForms();
            });
            list.addView(item);
        }
        menuLayout.addView(sv);

        Button refresh = new Button(this);
        refresh.setText("↻ Обновить");
        refresh.setAllCaps(false);
        refresh.setTextColor(WHITE);
        refresh.setBackground(GlassUI.glassSelector(getApplicationContext(), PURPLE_MAIN));
        refresh.setLayoutParams(new LinearLayout.LayoutParams(-1, getButtonHeight(50)));
        refresh.setOnClickListener(v -> showKeyboardAdminForms());
        menuLayout.addView(refresh);

        menuLayout.addView(makeBackButton());
        updateMenuButtonsHeight(menuLayout, getButtonHeight(50));
        applyKeyboardTransparency(keyboardTransparency);
    }

    // ==================== ОТПРАВКА КОМАНД ====================

    private void sendCommandToChat(String command) {
        boolean autoSend = getSharedPreferences("GTAndroidData", 0)
                .getBoolean("send_automatically", true);

        if (command.trim().startsWith("/ans")) incrementReportStat();

        if (MyAccessibilityService.isAvailable()) {
            MyAccessibilityService.insertText(command);
            if (autoSend) {
                sendEnterKey();
                Toast.makeText(this, "✅ Отправлено: " + command,
                        Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "✍️ Текст вставлен. Нажмите отправку.",
                        Toast.LENGTH_SHORT).show();
            }
        } else {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                ic.commitText(command, 1);
                if (autoSend) {
                    sendEnterKey(ic);
                    Toast.makeText(this, "✅ Отправлено: " + command,
                            Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "✍️ Текст вставлен. Нажмите отправку.",
                            Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void sendEnterKey() {
        new Handler().postDelayed(() -> {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                try {
                    long t = System.currentTimeMillis();
                    ic.sendKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN,
                            KeyEvent.KEYCODE_ENTER, 0));
                    ic.sendKeyEvent(new KeyEvent(t + 25, t + 25,
                            KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER, 0));
                } catch (Exception e) {
                    ic.commitText("\n", 1);
                }
            }
        }, 100L);
    }

    private void sendEnterKey(InputConnection ic) {
        try {
            long t = System.currentTimeMillis();
            ic.sendKeyEvent(new KeyEvent(t, t, KeyEvent.ACTION_DOWN,
                    KeyEvent.KEYCODE_ENTER, 0));
            ic.sendKeyEvent(new KeyEvent(t + 25, t + 25,
                    KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER, 0));
        } catch (Exception e) {
            ic.commitText("\n", 1);
        }
    }

    // ==================== ВСПОМОГАТЕЛЬНОЕ ====================

    private TextView makeTitle(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(WHITE);
        tv.setTextSize(getFontMultiplier() * 16f);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(5, 10, 5, 10);
        tv.setBackground(GlassUI.glassSelector(getApplicationContext(), PURPLE_MAIN));
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        return tv;
    }

    private Button makeBackButton() {
        Button btn = new Button(this);
        btn.setText("← Назад к клавиатуре");
        btn.setLayoutParams(new LinearLayout.LayoutParams(-1, getButtonHeight(50)));
        btn.setBackground(GlassUI.glassSelector(getApplicationContext(), PURPLE_DARK));
        btn.setTextColor(WHITE);
        btn.setTextSize(getFontMultiplier() * 14f);
        btn.setOnClickListener(v -> showKeyboardView());
        return btn;
    }

    private void updateMenuButtonsHeight(View view, int height) {
        if (view == null) return;
        if (view instanceof LinearLayout) {
            LinearLayout ll = (LinearLayout) view;
            for (int i = 0; i < ll.getChildCount(); i++)
                updateMenuButtonsHeight(ll.getChildAt(i), height);
            return;
        }
        if (view instanceof ScrollView) {
            ScrollView sv = (ScrollView) view;
            if (sv.getChildCount() > 0)
                updateMenuButtonsHeight(sv.getChildAt(0), height);
            return;
        }
        if (view instanceof Button) {
            Button b = (Button) view;
            LinearLayout.LayoutParams lp =
                    (LinearLayout.LayoutParams) b.getLayoutParams();
            if (lp != null) {
                lp.height = height;
                b.setLayoutParams(lp);
                b.setTextSize(getFontMultiplier() * 12f);
                b.setTextColor(WHITE);
            }
            return;
        }
        if (view instanceof TextView && !(view instanceof Button)) {
            ((TextView) view).setTextSize(getFontMultiplier() * 16f);
            ((TextView) view).setTextColor(WHITE);
        }
    }

    // ==================== КНОПКИ КЛАВИАТУРЫ ====================

    private void setupButtons() {
        setupRussianButtons();
        setupEnglishButtons();

        Button btnAdmin = keyboardView.findViewById(R.id.btnAdmin);
        if (btnAdmin != null) btnAdmin.setOnClickListener(v -> showAdminPanel());
        Button btnNormal = keyboardView.findViewById(R.id.btnNormal);
        if (btnNormal != null) btnNormal.setOnClickListener(v -> showNormalPanel());
        Button btnTp = keyboardView.findViewById(R.id.btnTeleport);
        if (btnTp != null) btnTp.setOnClickListener(v -> showTeleportMenu());
        Button btnQA = keyboardView.findViewById(R.id.btnQuickAnswer);
        if (btnQA != null) btnQA.setOnClickListener(v -> showQuickAnswersMenu());

        Button btnFamWar = keyboardView.findViewById(R.id.btnFamWar);
        if (btnFamWar != null) {
            btnFamWar.setOnClickListener(v -> showKeyboardView());
        }

        Button btnSwitchRu = keyboardView.findViewById(R.id.btnSwitchLang_ru);
        if (btnSwitchRu != null) btnSwitchRu.setOnClickListener(v -> switchLanguage());
        Button btnSwitchEn = keyboardView.findViewById(R.id.btnSwitchLangEn);
        if (btnSwitchEn != null) btnSwitchEn.setOnClickListener(v -> switchLanguage());

        int[] toggleSymbolsIds = {
                R.id.btnToggleSymbolsTop_ru,
                R.id.btnToggleSymbolsTop_ru2,
                R.id.btnToggleSymbolsTop_en,
                R.id.btnToggleSymbolsTop_en2
        };
        for (int id : toggleSymbolsIds) {
            Button b = keyboardView.findViewById(id);
            if (b != null) b.setOnClickListener(v -> toggleSymbolsPage());
        }
    }

    private void setupRussianButtons() {
        String[] keys = {"й", "ц", "у", "к", "е", "н", "ш", "щ", "з", "х",
                "ф", "ы", "в", "а", "п", "р", "о", "л", "ж", "э",
                "я", "ч", "с", "м", "и", "ь", "б", "ю"};
        for (String k : keys) {
            int resId = getResources().getIdentifier(
                    "btn" + k + "_ru", "id", getPackageName());
            Button btn = keyboardView.findViewById(resId);
            if (btn != null) btn.setOnClickListener(this);
        }
        Button btnG = keyboardView.findViewById(R.id.btn__ru_res_0x7f02005a);
        Button btnD = keyboardView.findViewById(R.id.btn__ru_res_0x7f02005b);
        Button btnT = keyboardView.findViewById(R.id.btn__ru_res_0x7f020068);
        Button btnE = keyboardView.findViewById(R.id.btne_ru);
        Button[] extras = {btnG, btnD, btnT, btnE};
        for (Button b : extras) if (b != null) b.setOnClickListener(this);

        for (int i = 1; i <= 9; i++) {
            int rid = getResources().getIdentifier("btnToggle" + i + "_ru",
                    "id", getPackageName());
            Button b = keyboardView.findViewById(rid);
            if (b != null) b.setOnClickListener(this);
        }
        Button btn0 = keyboardView.findViewById(R.id.btnToggle0Top_ru);
        if (btn0 != null) btn0.setOnClickListener(this);
        for (int i = 1; i <= 9; i++) {
            int rid = getResources().getIdentifier("btnToggle" + i + "Top_ru",
                    "id", getPackageName());
            Button b = keyboardView.findViewById(rid);
            if (b != null) b.setOnClickListener(this);
        }
        Button btn0Top = keyboardView.findViewById(R.id.btnToggle0Top_ru);
        if (btn0Top != null) btn0Top.setOnClickListener(this);

        Button btnSpace = keyboardView.findViewById(R.id.btnSpace_ru);
        Button btnBackspace = keyboardView.findViewById(R.id.btnBackspace_ru);
        Button btnEnter = keyboardView.findViewById(R.id.btnEnter_ru);
        Button btnShift = keyboardView.findViewById(R.id.btnShift_ru);
        Button[] special = {btnSpace, btnBackspace, btnEnter, btnShift};
        for (Button b : special) if (b != null) b.setOnClickListener(this);
        if (btnShift != null) btnShift.setOnClickListener(v -> toggleShift());

        String[] symbols = {",", ".", "/", ":", ";", "?", "!", "#", "<", ">"};
        String[] ids = {"Comma", "Period", "Slash", "Colon", "Semicolon",
                "Question", "Exclamation", "Hash", "Less", "Greater"};
        for (int i = 0; i < symbols.length; i++) {
            final String s = symbols[i];
            int rid = getResources().getIdentifier("btnSymbol" + ids[i] + "_ru",
                    "id", getPackageName());
            Button b = keyboardView.findViewById(rid);
            if (b != null) {
                b.setOnClickListener(v -> {
                    InputConnection ic = getCurrentInputConnection();
                    if (ic != null) ic.commitText(s, 1);
                });
            }
        }
        for (int i = 0; i < symbols.length; i++) {
            final String s = symbols[i];
            int rid = getResources().getIdentifier("btnSymbol" + ids[i] + "Top_ru",
                    "id", getPackageName());
            Button b = keyboardView.findViewById(rid);
            if (b != null) {
                b.setOnClickListener(v -> {
                    InputConnection ic = getCurrentInputConnection();
                    if (ic != null) ic.commitText(s, 1);
                });
            }
        }
    }

    private void setupEnglishButtons() {
        String[] keys = {"q","w","e","r","t","y","u","i","o","p",
                "a","s","d","f","g","h","j","k","l",
                "z","x","c","v","b","n","m"};
        for (String k : keys) {
            int rid = getResources().getIdentifier("btn" + k + "_en",
                    "id", getPackageName());
            Button b = keyboardView.findViewById(rid);
            if (b != null) b.setOnClickListener(this);
        }
        for (int i = 1; i <= 9; i++) {
            int rid = getResources().getIdentifier("btnToggle" + i + "_en",
                    "id", getPackageName());
            Button b = keyboardView.findViewById(rid);
            if (b != null) b.setOnClickListener(this);
        }
        Button btn0 = keyboardView.findViewById(R.id.btnToggle0Top_en);
        if (btn0 != null) btn0.setOnClickListener(this);
        for (int i = 1; i <= 9; i++) {
            int rid = getResources().getIdentifier("btnToggle" + i + "Top_en",
                    "id", getPackageName());
            Button b = keyboardView.findViewById(rid);
            if (b != null) b.setOnClickListener(this);
        }
        Button btn0Top = keyboardView.findViewById(R.id.btnToggle0Top_en);
        if (btn0Top != null) btn0Top.setOnClickListener(this);

        Button btnSpace = keyboardView.findViewById(R.id.btnSpaceEn);
        Button btnBackspace = keyboardView.findViewById(R.id.btnBackspaceEn);
        Button btnEnter = keyboardView.findViewById(R.id.btnEnterEn);
        Button btnShift = keyboardView.findViewById(R.id.btnShiftEn);
        Button[] special = {btnSpace, btnBackspace, btnEnter, btnShift};
        for (Button b : special) if (b != null) b.setOnClickListener(this);
        if (btnShift != null) btnShift.setOnClickListener(v -> toggleShift());

        String[] symbols = {",", ".", "/", ":", ";", "?", "!", "#", "<", ">"};
        String[] ids = {"Comma", "Period", "Slash", "Colon", "Semicolon",
                "Question", "Exclamation", "Hash", "Less", "Greater"};
        for (int i = 0; i < symbols.length; i++) {
            final String s = symbols[i];
            int rid = getResources().getIdentifier("btnSymbol" + ids[i] + "_en",
                    "id", getPackageName());
            Button b = keyboardView.findViewById(rid);
            if (b != null) {
                b.setOnClickListener(v -> {
                    InputConnection ic = getCurrentInputConnection();
                    if (ic != null) ic.commitText(s, 1);
                });
            }
        }
        for (int i = 0; i < symbols.length; i++) {
            final String s = symbols[i];
            int rid = getResources().getIdentifier("btnSymbol" + ids[i] + "Top_en",
                    "id", getPackageName());
            Button b = keyboardView.findViewById(rid);
            if (b != null) {
                b.setOnClickListener(v -> {
                    InputConnection ic = getCurrentInputConnection();
                    if (ic != null) ic.commitText(s, 1);
                });
            }
        }
    }

    @Override
    public void onClick(View v) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null || !(v instanceof Button)) return;
        String text = ((Button) v).getText().toString();

        if (text.equals("⌫")) { ic.deleteSurroundingText(1, 0); return; }
        if (text.equals("↵")) { sendEnterKeyWithPrefix(ic); return; }
        if (text.equals("пробел") || text.equals("space") || text.equals(" ")) {
            ic.commitText(" ", 1); return;
        }
        if (text.equals("123") || text.equals("АБВ") || text.equals("ABC")) {
            toggleSymbolsPage(); return;
        }
        if (text.equals("⇧")) { toggleShift(); return; }
        if (text.equals("EN") || text.equals("RU")) { switchLanguage(); return; }

        if (isUpperCase) text = text.toUpperCase();
        ic.commitText(text, 1);
    }

    private void sendEnterKeyWithPrefix(InputConnection ic) {
        int chatMode = FloatingService.currentChatMode;
        CharSequence before = ic.getTextBeforeCursor(1000, 0);
        CharSequence after = ic.getTextAfterCursor(1000, 0);
        String full = (before != null ? before.toString() : "");
        if (after != null) full += after.toString();

        if (before != null && before.length() > 0)
            ic.deleteSurroundingText(before.length(), 0);
        if (after != null && after.length() > 0)
            ic.deleteSurroundingText(0, after.length());

        String toSend;
        if (chatMode == 1) toSend = "/n " + full;
        else if (chatMode == 2) toSend = "/a " + full;
        else toSend = full;

        if (!toSend.isEmpty()) ic.commitText(toSend, 1);
        sendEnterKey(ic);
    }

    private void toggleShift() { isUpperCase = !isUpperCase; }

    private void switchLanguage() {
        isRussian = !isRussian;
        LinearLayout ru = keyboardView.findViewById(R.id.russianLayout);
        LinearLayout en = keyboardView.findViewById(R.id.englishLayout);
        if (ru != null) ru.setVisibility(isRussian ? View.VISIBLE : View.GONE);
        if (en != null) en.setVisibility(isRussian ? View.GONE : View.VISIBLE);
    }

    private void toggleSymbolsPage() {
        showSymbolsPage = !showSymbolsPage;
        int numVis = showSymbolsPage ? View.GONE : View.VISIBLE;
        int symVis = showSymbolsPage ? View.VISIBLE : View.GONE;

        int[] numRows = {R.id.numbersRowTop_ru, R.id.numbersRowTop_en};
        for (int id : numRows) {
            View v = keyboardView.findViewById(id);
            if (v != null) v.setVisibility(numVis);
        }
        int[] symRows = {R.id.symbolsRowTop_ru, R.id.symbolsRowTop_en};
        for (int id : symRows) {
            View v = keyboardView.findViewById(id);
            if (v != null) v.setVisibility(symVis);
        }
        String t = "123";
        int[] toggles = {R.id.btnToggleSymbolsTop_ru, R.id.btnToggleSymbolsTop_ru2,
                R.id.btnToggleSymbolsTop_en, R.id.btnToggleSymbolsTop_en2};
        for (int id : toggles) {
            Button b = keyboardView.findViewById(id);
            if (b != null) b.setText(t);
        }
    }
                    }
