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
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputConnection;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.lang.reflect.Array;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import org.json.JSONArray;
import org.json.JSONObject;

/* loaded from: classes2.dex */
public class KeyboardService extends InputMethodService implements View.OnClickListener {
    private static final String KEY_LAST_RESET_DATE = "last_reset_date";
    private static final String KEY_TOTAL_REPORTS = "total_reports_all_time";
    private static final String KEY_WEEKLY_PREFIX = "weekly_";
    private static final String PREFS_STATS = "GTAndroidStats";
    private LinearLayout cheatsMenuContainer;
    private EditText hpIdInput;
    private View hpInputContainer;
    private View keyboardView;
    private LinearLayout menuLayout;
    private View menuView;
    private SharedPreferences prefs;
    private BroadcastReceiver sizeChangeReceiver;
    private BroadcastReceiver transparencyChangeReceiver;
    private boolean isRussian = true;
    private boolean isUpperCase = false;
    private boolean showMenu = false;
    private boolean showKeyboard = true;
    private boolean showSymbols = false;
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

    @Override // android.inputmethodservice.InputMethodService, android.app.Service
    public void onCreate() {
        super.onCreate();
        this.prefs = getSharedPreferences("GTAndroidData", 0);
        this.adminLevel = this.prefs.getInt("admin_level", 1);
        this.keyboardSize = this.prefs.getInt("keyboard_size", 1);
        this.keyboardTransparency = this.prefs.getInt("keyboard_transparency", 100);
        this.sizeChangeReceiver = new BroadcastReceiver() { // from class: gtandroid.universal.KeyboardService.1
            @Override // android.content.BroadcastReceiver
            public void onReceive(Context context, Intent intent) {
                if (intent != null && "KEYBOARD_SIZE_CHANGED".equals(intent.getAction())) {
                    KeyboardService.this.keyboardSize = KeyboardService.this.prefs.getInt("keyboard_size", 1);
                    KeyboardService.this.updateKeyboardSize();
                    if (KeyboardService.this.menuLayout != null && KeyboardService.this.menuLayout.getVisibility() == 0) {
                        KeyboardService.this.refreshCurrentMenu();
                    }
                }
            }
        };
        registerReceiver(this.sizeChangeReceiver, new IntentFilter("KEYBOARD_SIZE_CHANGED"));
        this.transparencyChangeReceiver = new BroadcastReceiver() { // from class: gtandroid.universal.KeyboardService.2
            @Override // android.content.BroadcastReceiver
            public void onReceive(Context context, Intent intent) {
                if (intent != null && "KEYBOARD_TRANSPARENCY_CHANGED".equals(intent.getAction())) {
                    KeyboardService.this.keyboardTransparency = KeyboardService.this.prefs.getInt("keyboard_transparency", 100);
                    KeyboardService.this.applyKeyboardTransparency(KeyboardService.this.keyboardTransparency);
                }
            }
        };
        registerReceiver(this.transparencyChangeReceiver, new IntentFilter("KEYBOARD_TRANSPARENCY_CHANGED"));
        checkAndResetWeeklyStats();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void refreshCurrentMenu() {
        if (this.menuLayout != null && this.menuLayout.getVisibility() == 0 && this.menuLayout.getChildCount() > 0) {
            View firstChild = this.menuLayout.getChildAt(0);
            if (firstChild instanceof TextView) {
                TextView title = (TextView) firstChild;
                String text = title.getText().toString();
                if (text.contains("АДМИН")) {
                    showAdminPanel();
                } else if (text.contains("ОБЫЧНАЯ")) {
                    showNormalPanel();
                } else if (text.contains("БЫСТРЫЙ ОТВЕТ")) {
                    showQuickAnswersMenu();
                } else if (text.contains("ТЕЛЕПОРТ")) {
                    showTeleportMenu();
                } else if (text.contains("ЧИТЫ")) {
                    showCheatsMenu();
                } else if (text.contains("СТАТИСТИКА")) {
                    showStatisticsMenu();
                } else if (text.contains("ТП К СЕБЕ")) {
                    showTpToMeMenu();
                }
            }
        }
        applyKeyboardTransparency(this.keyboardTransparency);
    }

    @Override // android.inputmethodservice.InputMethodService
    public View onCreateInputView() {
        this.keyboardView = getLayoutInflater().inflate(R.layout.keyboard, (ViewGroup) null);
        setupButtons();
        createMenu();
        applyKeyboardSize();
        applyKeyboardTransparency(this.keyboardTransparency);
        return this.keyboardView;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void applyKeyboardTransparency(int transparencyPercent) {
        if (this.keyboardView == null) {
            return;
        }
        int alpha = (int) ((transparencyPercent / 100.0d) * 255.0d);
        LinearLayout keyboardContainer = (LinearLayout) this.keyboardView.findViewById(R.id.keyboardContainer);
        if (keyboardContainer != null && keyboardContainer.getBackground() != null) {
            keyboardContainer.getBackground().mutate().setAlpha(alpha);
        }
        TextView titleText = (TextView) this.keyboardView.findViewById(R.id.keyboardTitle);
        if (titleText != null && titleText.getBackground() != null) {
            titleText.getBackground().mutate().setAlpha(alpha);
        }
        if (this.menuLayout != null && this.menuLayout.getBackground() != null) {
            this.menuLayout.getBackground().mutate().setAlpha(alpha);
        }
        applyTransparencyToContainersOnly(this.keyboardView, alpha);
    }

    private void applyTransparencyToContainersOnly(View view, int alpha) {
        if (view == null || (view instanceof Button) || (view instanceof EditText)) {
            return;
        }
        Drawable bg = view.getBackground();
        if (bg != null) {
            try {
                bg.mutate().setAlpha(alpha);
            } catch (Exception e) {
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                applyTransparencyToContainersOnly(group.getChildAt(i), alpha);
            }
        }
    }

    private View.OnTouchListener getButtonTouchListener(int pressedColor, int normalColor) {
        return new View.OnTouchListener() { // from class: gtandroid.universal.KeyboardService.3
            @Override // android.view.View.OnTouchListener
            public boolean onTouch(View v, MotionEvent event) {
                return false;
            }
        };
    }

    private void updateMenuButtonsHeight(View view, int height) {
        if (view == null) {
            return;
        }
        if (view instanceof LinearLayout) {
            LinearLayout layout = (LinearLayout) view;
            for (int i = 0; i < layout.getChildCount(); i++) {
                updateMenuButtonsHeight(layout.getChildAt(i), height);
            }
            return;
        }
        if (view instanceof ScrollView) {
            ScrollView scrollView = (ScrollView) view;
            if (scrollView.getChildCount() > 0) {
                updateMenuButtonsHeight(scrollView.getChildAt(0), height);
                return;
            }
            return;
        }
        if (view instanceof Button) {
            Button btn = (Button) view;
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) btn.getLayoutParams();
            if (params != null) {
                params.height = height;
                btn.setLayoutParams(params);
                float textSize = getFontMultiplier() * 12.0f;
                btn.setTextSize(textSize);
                return;
            }
            return;
        }
        if ((view instanceof TextView) && !(view instanceof Button)) {
            TextView tv = (TextView) view;
            float textSize2 = getFontMultiplier() * 16.0f;
            tv.setTextSize(textSize2);
        }
    }

    private void changeButtonsHeight(LinearLayout container, int height) {
        if (container == null) {
            return;
        }
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            if (child instanceof LinearLayout) {
                changeButtonsHeight((LinearLayout) child, height);
            } else if (child instanceof Button) {
                Button btn = (Button) child;
                LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) btn.getLayoutParams();
                if (params != null) {
                    params.height = height;
                    btn.setLayoutParams(params);
                    float textSize = getFontMultiplier() * 18.0f;
                    btn.setTextSize(textSize);
                }
            }
        }
    }

    private void applyKeyboardSize() {
        if (this.keyboardView == null) {
            return;
        }
        this.keyboardSize = this.prefs.getInt("keyboard_size", 1);
        String percentText = getPercentText();
        int normalButtonHeight = getButtonHeight(42);
        int smallButtonHeight = getButtonHeight(34);
        int topButtonHeight = getButtonHeight(38);
        LinearLayout russianLayout = (LinearLayout) this.keyboardView.findViewById(R.id.russianLayout);
        if (russianLayout != null) {
            changeButtonsHeight(russianLayout, normalButtonHeight);
        }
        LinearLayout englishLayout = (LinearLayout) this.keyboardView.findViewById(R.id.englishLayout);
        if (englishLayout != null) {
            changeButtonsHeight(englishLayout, normalButtonHeight);
        }
        LinearLayout numbersRowRu = (LinearLayout) this.keyboardView.findViewById(R.id.numbersRowTop_ru);
        if (numbersRowRu != null) {
            changeButtonsHeight(numbersRowRu, smallButtonHeight);
        }
        LinearLayout numbersRowEn = (LinearLayout) this.keyboardView.findViewById(R.id.numbersRowTop_en);
        if (numbersRowEn != null) {
            changeButtonsHeight(numbersRowEn, smallButtonHeight);
        }
        LinearLayout symbolsRowRu = (LinearLayout) this.keyboardView.findViewById(R.id.symbolsRowTop_ru);
        if (symbolsRowRu != null) {
            changeButtonsHeight(symbolsRowRu, smallButtonHeight);
        }
        LinearLayout symbolsRowEn = (LinearLayout) this.keyboardView.findViewById(R.id.symbolsRowTop_en);
        if (symbolsRowEn != null) {
            changeButtonsHeight(symbolsRowEn, smallButtonHeight);
        }
        LinearLayout numbersRowTopRu = (LinearLayout) this.keyboardView.findViewById(R.id.numbersRowTop_ru);
        if (numbersRowTopRu != null) {
            changeButtonsHeight(numbersRowTopRu, smallButtonHeight);
        }
        LinearLayout numbersRowTopEn = (LinearLayout) this.keyboardView.findViewById(R.id.numbersRowTop_en);
        if (numbersRowTopEn != null) {
            changeButtonsHeight(numbersRowTopEn, smallButtonHeight);
        }
        LinearLayout symbolsRowTopRu = (LinearLayout) this.keyboardView.findViewById(R.id.symbolsRowTop_ru);
        if (symbolsRowTopRu != null) {
            changeButtonsHeight(symbolsRowTopRu, smallButtonHeight);
        }
        LinearLayout symbolsRowTopEn = (LinearLayout) this.keyboardView.findViewById(R.id.symbolsRowTop_en);
        if (symbolsRowTopEn != null) {
            changeButtonsHeight(symbolsRowTopEn, smallButtonHeight);
        }
        LinearLayout keyboardContainer = (LinearLayout) this.keyboardView.findViewById(R.id.keyboardContainer);
        if (keyboardContainer != null && keyboardContainer.getChildCount() > 1) {
            View secondChild = keyboardContainer.getChildAt(1);
            if (secondChild instanceof LinearLayout) {
                changeButtonsHeight((LinearLayout) secondChild, topButtonHeight);
            }
        }
        TextView titleText = (TextView) this.keyboardView.findViewById(R.id.keyboardTitle);
        if (titleText != null) {
            float titleSize = 12.0f * getFontMultiplier();
            titleText.setTextSize(titleSize);
            titleText.setText("⌨️ GTAndroid" + this.adminLevel + " ур. [" + percentText + "]");
        }
    }

    private String getPercentText() {
        switch (this.keyboardSize) {
        }
        return "100%";
    }

    private float getFontMultiplier() {
        switch (this.keyboardSize) {
        }
        return 1.0f;
    }

    public void updateKeyboardSize() {
        if (this.keyboardView != null) {
            applyKeyboardSize();
        }
    }

    private int dpToPx(float dp) {
        return (int) TypedValue.applyDimension(1, dp, getResources().getDisplayMetrics());
    }

    private int getButtonHeight(int baseHeightDp) {
        float multiplier;
        switch (this.keyboardSize) {
            case 0:
                multiplier = 0.8f;
                break;
            case 1:
                multiplier = 1.0f;
                break;
            case 2:
                multiplier = 1.2f;
                break;
            case 3:
                multiplier = 1.4f;
                break;
            case 4:
                multiplier = 1.6f;
                break;
            case 5:
                multiplier = 1.8f;
                break;
            case 6:
                multiplier = 2.0f;
                break;
            default:
                multiplier = 1.0f;
                break;
        }
        return dpToPx(baseHeightDp * multiplier * 0.5f);
    }

    private void setupButtons() {
        setupRussianButtons();
        setupEnglishButtons();
        Button btnSwitchRu = (Button) this.keyboardView.findViewById(R.id.btnSwitchLang_ru);
        if (btnSwitchRu != null) {
            btnSwitchRu.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.4
                @Override // android.view.View.OnClickListener
                public void onClick(View v) {
                    KeyboardService.this.switchLanguage();
                }
            });
        }
        Button btnSwitchEn = (Button) this.keyboardView.findViewById(R.id.btnSwitchLangEn);
        if (btnSwitchEn != null) {
            btnSwitchEn.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.5
                @Override // android.view.View.OnClickListener
                public void onClick(View v) {
                    KeyboardService.this.switchLanguage();
                }
            });
        }
        Button btnAdmin = (Button) this.keyboardView.findViewById(R.id.btnAdmin);
        if (btnAdmin != null) {
            btnAdmin.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.6
                @Override // android.view.View.OnClickListener
                public void onClick(View v) {
                    KeyboardService.this.showAdminPanel();
                }
            });
        }
        Button btnNormal = (Button) this.keyboardView.findViewById(R.id.btnNormal);
        if (btnNormal != null) {
            btnNormal.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.7
                @Override // android.view.View.OnClickListener
                public void onClick(View v) {
                    KeyboardService.this.showNormalPanel();
                }
            });
        }
        Button btnTeleport = (Button) this.keyboardView.findViewById(R.id.btnTeleport);
        if (btnTeleport != null) {
            btnTeleport.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.8
                @Override // android.view.View.OnClickListener
                public void onClick(View v) {
                    KeyboardService.this.showTeleportMenu();
                }
            });
        }
        Button btnQuickAnswer = (Button) this.keyboardView.findViewById(R.id.btnQuickAnswer);
        if (btnQuickAnswer != null) {
            btnQuickAnswer.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.9
                @Override // android.view.View.OnClickListener
                public void onClick(View v) {
                    KeyboardService.this.showQuickAnswersMenu();
                }
            });
        }
        Button btnToggleSymbols_ru = (Button) this.keyboardView.findViewById(R.id.btnToggleSymbolsTop_ru);
        if (btnToggleSymbols_ru != null) {
            btnToggleSymbols_ru.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.10
                @Override // android.view.View.OnClickListener
                public void onClick(View v) {
                    KeyboardService.this.toggleSymbolsPage();
                }
            });
        }
        Button btnToggleSymbols_ru2 = (Button) this.keyboardView.findViewById(R.id.btnToggleSymbolsTop_ru2);
        if (btnToggleSymbols_ru2 != null) {
            btnToggleSymbols_ru2.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.11
                @Override // android.view.View.OnClickListener
                public void onClick(View v) {
                    KeyboardService.this.toggleSymbolsPage();
                }
            });
        }
        Button btnToggleSymbols_en = (Button) this.keyboardView.findViewById(R.id.btnToggleSymbolsTop_en);
        if (btnToggleSymbols_en != null) {
            btnToggleSymbols_en.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.12
                @Override // android.view.View.OnClickListener
                public void onClick(View v) {
                    KeyboardService.this.toggleSymbolsPage();
                }
            });
        }
        Button btnFamWar = (Button) this.keyboardView.findViewById(R.id.btnFamWar);
        if (btnFamWar != null) {
            btnFamWar.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.13
                @Override // android.view.View.OnClickListener
                public void onClick(View v) {
                    KeyboardService.this.showFamWarMenu();
                }
            });
        }
        Button btnToggleSymbols_en2 = (Button) this.keyboardView.findViewById(R.id.btnToggleSymbolsTop_en2);
        if (btnToggleSymbols_en2 != null) {
            btnToggleSymbols_en2.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.14
                @Override // android.view.View.OnClickListener
                public void onClick(View v) {
                    KeyboardService.this.toggleSymbolsPage();
                }
            });
        }
    }

    private void createMenu() {
        this.menuView = this.keyboardView.findViewById(R.id.menuContainer);
        if (this.menuView != null) {
            this.menuLayout = (LinearLayout) this.menuView;
            this.menuLayout.removeAllViews();
            this.menuLayout.setBackground(GlassUI.glassSelector(getApplicationContext(), -14540254));
            this.menuLayout.setPadding(5, 5, 5, 5);
            this.menuLayout.setVisibility(8);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showAdminPanel() {
        if (this.menuLayout == null) {
            return;
        }
        this.menuLayout.removeAllViews();
        this.menuLayout.setVisibility(0);
        View keyboardLayout = this.keyboardView.findViewById(R.id.keyboardLayout);
        if (keyboardLayout != null) {
            keyboardLayout.setVisibility(8);
        }
        TextView title = new TextView(this);
        title.setText("АДМИН ПАНЕЛЬ");
        title.setTextColor(-1);
        title.setTextSize(getFontMultiplier() * 16.0f);
        title.setGravity(17);
        title.setPadding(5, 10, 5, 10);
        title.setBackground(GlassUI.glassSelector(getApplicationContext(), -13421773));
        this.menuLayout.addView(title);
        LinearLayout containerAdmin = new LinearLayout(this);
        containerAdmin.setOrientation(1);
        containerAdmin.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        this.menuLayout.addView(containerAdmin);
        setupAdminButtons(containerAdmin);
        Button btnForms = new Button(this);
        btnForms.setText("📨 ADMIN-формы");
        btnForms.setAllCaps(false);
        btnForms.setTextColor(-1);
        btnForms.setBackground(GlassUI.glassSelector(getApplicationContext(), 0xFF6541A5));
        btnForms.setLayoutParams(new LinearLayout.LayoutParams(-1, getButtonHeight(44)));
        btnForms.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showKeyboardAdminForms(); }
        });
        containerAdmin.addView(btnForms);
        Button btnReportCatch = new Button(this);
        btnReportCatch.setText("🎯 Ловля репортов (60 сек.)");
        btnReportCatch.setAllCaps(false);
        btnReportCatch.setTextColor(-1);
        btnReportCatch.setBackground(GlassUI.glassSelector(getApplicationContext(), 0xFF287D66));
        btnReportCatch.setLayoutParams(new LinearLayout.LayoutParams(-1, getButtonHeight(44)));
        btnReportCatch.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showCaughtReports(); }
        });
        containerAdmin.addView(btnReportCatch);
        Button btnBack = new Button(this);
        btnBack.setText("← Назад к клавиатуре");
        btnBack.setLayoutParams(new LinearLayout.LayoutParams(-1, getButtonHeight(50)));
        btnBack.setBackground(GlassUI.glassSelector(getApplicationContext(), -10066330));
        btnBack.setTextColor(-1);
        btnBack.setTextSize(getFontMultiplier() * 16.0f);
        btnBack.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.15
            @Override // android.view.View.OnClickListener
            public void onClick(View v) {
                KeyboardService.this.showKeyboardView();
            }
        });
        this.menuLayout.addView(btnBack);
        updateMenuButtonsHeight(this.menuLayout, getButtonHeight(50));
        applyKeyboardTransparency(this.keyboardTransparency);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showFamWarMenu() {
        int minLevel;
        int buttonHeight;
        String str = "";
        if (this.menuLayout == null) {
            return;
        }
        this.menuLayout.removeAllViews();
        this.menuLayout.setVisibility(0);
        View keyboardLayout = this.keyboardView.findViewById(R.id.keyboardLayout);
        if (keyboardLayout != null) {
            keyboardLayout.setVisibility(8);
        }
        ScrollView scrollView = new ScrollView(this);
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(-1, -1));
        scrollView.setFillViewport(true);
        LinearLayout mainContainer = new LinearLayout(this);
        mainContainer.setOrientation(1);
        mainContainer.setPadding(5, 5, 5, 5);
        mainContainer.setBackground(GlassUI.glassSelector(getApplicationContext(), -15066598));
        TextView title = new TextView(this);
        title.setText("FAMWAR");
        title.setTextColor(-1);
        title.setTextSize(getFontMultiplier() * 16.0f);
        title.setGravity(17);
        title.setPadding(5, 10, 5, 10);
        mainContainer.addView(title);
        TextView adminLevelText = new TextView(this);
        adminLevelText.setText("Ваш уровень: " + this.adminLevel);
        adminLevelText.setTextColor(-16711681);
        adminLevelText.setGravity(17);
        adminLevelText.setPadding(5, 5, 5, 15);
        adminLevelText.setTextSize(getFontMultiplier() * 12.0f);
        mainContainer.addView(adminLevelText);
        try {
            String json = this.prefs.getString("famwar_commands", "[]");
            JSONArray jsonArray = new JSONArray(json);
            if (jsonArray.length() == 0) {
                try {
                    TextView tv = new TextView(this);
                    tv.setText("Нет команд FamWar\nДобавьте в приложении");
                    tv.setTextColor(-256);
                    tv.setGravity(17);
                    tv.setPadding(10, 50, 10, 50);
                    tv.setTextSize(getFontMultiplier() * 12.0f);
                    mainContainer.addView(tv);
                    minLevel = 10;
                } catch (Exception e) {
                    minLevel = 10;
                    e.printStackTrace();
                    TextView tv2 = new TextView(this);
                    tv2.setText("Ошибка: " + e.getMessage());
                    tv2.setTextColor(-65536);
                    tv2.setPadding(minLevel, minLevel, minLevel, minLevel);
                    tv2.setTextSize(getFontMultiplier() * 12.0f);
                    mainContainer.addView(tv2);
                    Button btnBack = new Button(this);
                    btnBack.setText("← Назад к клавиатуре");
                    btnBack.setLayoutParams(new LinearLayout.LayoutParams(-1, getButtonHeight(55)));
                    btnBack.setBackground(GlassUI.glassSelector(getApplicationContext(), -10066330));
                    btnBack.setTextColor(-1);
                    btnBack.setTextSize(getFontMultiplier() * 16.0f);
                    btnBack.setPadding(5, minLevel, 5, minLevel);
                    btnBack.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.17
                        @Override // android.view.View.OnClickListener
                        public void onClick(View v) {
                            KeyboardService.this.showKeyboardView();
                        }
                    });
                    mainContainer.addView(btnBack);
                    scrollView.addView(mainContainer);
                    this.menuLayout.addView(scrollView);
                    updateMenuButtonsHeight(this.menuLayout, getButtonHeight(55));
                    applyKeyboardTransparency(this.keyboardTransparency);
                }
            } else {
                int buttonHeight2 = getButtonHeight(70);
                float buttonTextSize = getFontMultiplier() * 12.0f;
                int i = 0;
                while (i < jsonArray.length()) {
                    JSONObject obj = jsonArray.getJSONObject(i);
                    final String name = obj.optString("name", "Команда");
                    final String command = obj.optString("command", str);
                    int i2 = i;
                    final String time = obj.optString("time", str);
                    float buttonTextSize2 = buttonTextSize;
                    final String reason = obj.optString("reason", str);
                    JSONArray jsonArray2 = jsonArray;
                    final int minLevel2 = Integer.parseInt(obj.optString("minlevel", "1"));
                    Button btn = new Button(this);
                    String str2 = str;
                    TextView adminLevelText2 = adminLevelText;
                    try {
                        btn.setText(name + "\n[Треб. " + minLevel2 + " ур.]");
                        btn.setLayoutParams(new LinearLayout.LayoutParams(-1, buttonHeight2));
                        int buttonHeight3 = buttonHeight2;
                        btn.setPadding(10, 5, 10, 5);
                        btn.setTextSize(buttonTextSize2);
                        btn.setMaxLines(2);
                        if (this.adminLevel >= minLevel2) {
                            try {
                                buttonHeight = buttonHeight3;
                                btn.setBackground(GlassUI.glassSelector(getApplicationContext(), -16733696));
                            } catch (Exception e2) {
                                buttonHeight = buttonHeight3;
                                minLevel = 10;
                                e2.printStackTrace();
                                TextView tv22 = new TextView(this);
                                tv22.setText("Ошибка: " + e2.getMessage());
                                tv22.setTextColor(-65536);
                                tv22.setPadding(minLevel, minLevel, minLevel, minLevel);
                                tv22.setTextSize(getFontMultiplier() * 12.0f);
                                mainContainer.addView(tv22);
                                Button btnBack2 = new Button(this);
                                btnBack2.setText("← Назад к клавиатуре");
                                btnBack2.setLayoutParams(new LinearLayout.LayoutParams(-1, getButtonHeight(55)));
                                btnBack2.setBackground(GlassUI.glassSelector(getApplicationContext(), -10066330));
                                btnBack2.setTextColor(-1);
                                btnBack2.setTextSize(getFontMultiplier() * 16.0f);
                                btnBack2.setPadding(5, minLevel, 5, minLevel);
                                btnBack2.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.17
                                    @Override // android.view.View.OnClickListener
                                    public void onClick(View v) {
                                        KeyboardService.this.showKeyboardView();
                                    }
                                });
                                mainContainer.addView(btnBack2);
                                scrollView.addView(mainContainer);
                                this.menuLayout.addView(scrollView);
                                updateMenuButtonsHeight(this.menuLayout, getButtonHeight(55));
                                applyKeyboardTransparency(this.keyboardTransparency);
                            }
                        } else {
                            buttonHeight = buttonHeight3;
                            btn.setBackground(GlassUI.glassSelector(getApplicationContext(), -5609984));
                        }
                        btn.setTextColor(-1);
                        String json2 = json;
                        View keyboardLayout2 = keyboardLayout;
                        minLevel = 10;
                        try {
                            btn.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.16
                                @Override // android.view.View.OnClickListener
                                public void onClick(View v) {
                                    KeyboardService.this.showFamWarIdInput(command, time, reason, minLevel2, name);
                                }
                            });
                            mainContainer.addView(btn);
                            View sep = new View(this);
                            sep.setLayoutParams(new LinearLayout.LayoutParams(-1, 3));
                            sep.setBackground(GlassUI.glassSelector(getApplicationContext(), -13421773));
                            mainContainer.addView(sep);
                            i = i2 + 1;
                            adminLevelText = adminLevelText2;
                            buttonHeight2 = buttonHeight;
                            jsonArray = jsonArray2;
                            str = str2;
                            json = json2;
                            buttonTextSize = buttonTextSize2;
                            keyboardLayout = keyboardLayout2;
                        } catch (Exception e3) {
                            e3.printStackTrace();
                            TextView tv222 = new TextView(this);
                            tv222.setText("Ошибка: " + e3.getMessage());
                            tv222.setTextColor(-65536);
                            tv222.setPadding(minLevel, minLevel, minLevel, minLevel);
                            tv222.setTextSize(getFontMultiplier() * 12.0f);
                            mainContainer.addView(tv222);
                            Button btnBack22 = new Button(this);
                            btnBack22.setText("← Назад к клавиатуре");
                            btnBack22.setLayoutParams(new LinearLayout.LayoutParams(-1, getButtonHeight(55)));
                            btnBack22.setBackground(GlassUI.glassSelector(getApplicationContext(), -10066330));
                            btnBack22.setTextColor(-1);
                            btnBack22.setTextSize(getFontMultiplier() * 16.0f);
                            btnBack22.setPadding(5, minLevel, 5, minLevel);
                            btnBack22.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.17
                                @Override // android.view.View.OnClickListener
                                public void onClick(View v) {
                                    KeyboardService.this.showKeyboardView();
                                }
                            });
                            mainContainer.addView(btnBack22);
                            scrollView.addView(mainContainer);
                            this.menuLayout.addView(scrollView);
                            updateMenuButtonsHeight(this.menuLayout, getButtonHeight(55));
                            applyKeyboardTransparency(this.keyboardTransparency);
                        }
                    } catch (Exception e4) {
                        minLevel = 10;
                    }
                }
                minLevel = 10;
            }
        } catch (Exception e5) {
            minLevel = 10;
        }
        Button btnBack222 = new Button(this);
        btnBack222.setText("← Назад к клавиатуре");
        btnBack222.setLayoutParams(new LinearLayout.LayoutParams(-1, getButtonHeight(55)));
        btnBack222.setBackground(GlassUI.glassSelector(getApplicationContext(), -10066330));
        btnBack222.setTextColor(-1);
        btnBack222.setTextSize(getFontMultiplier() * 16.0f);
        btnBack222.setPadding(5, minLevel, 5, minLevel);
        btnBack222.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.17
            @Override // android.view.View.OnClickListener
            public void onClick(View v) {
                KeyboardService.this.showKeyboardView();
            }
        });
        mainContainer.addView(btnBack222);
        scrollView.addView(mainContainer);
        this.menuLayout.addView(scrollView);
        updateMenuButtonsHeight(this.menuLayout, getButtonHeight(55));
        applyKeyboardTransparency(this.keyboardTransparency);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showFamWarIdInput(final String command, final String time, final String reason, final int requiredLevel, String commandName) {
        int index;
        if (this.menuLayout == null) {
            return;
        }
        this.menuLayout.removeAllViews();
        int i = 0;
        this.menuLayout.setVisibility(0);
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        linearLayout.setPadding(10, 10, 10, 10);
        linearLayout.setBackground(GlassUI.glassSelector(getApplicationContext(), -15066598));
        TextView title = new TextView(this);
        title.setText("" + commandName.toUpperCase() + "");
        int i2 = -1;
        title.setTextColor(-1);
        title.setTextSize(getFontMultiplier() * 16.0f);
        title.setGravity(17);
        title.setPadding(0, 10, 0, 15);
        linearLayout.addView(title);
        TextView hint = new TextView(this);
        hint.setText("Введите ID игрока:");
        hint.setTextColor(-1);
        hint.setTextSize(getFontMultiplier() * 12.0f);
        hint.setGravity(17);
        linearLayout.addView(hint);
        final EditText idInput = new EditText(this);
        idInput.setBackground(GlassUI.lightCard(this, 10.0f));
        idInput.setPadding(24, 18, 24, 18);
        idInput.setTextColor(-14935010);
        idInput.setHintTextColor(-7434605);
        int i3 = -2;
        idInput.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        idInput.setBackground(GlassUI.glassSelector(getApplicationContext(), -13421773));
        idInput.setTextColor(-1);
        idInput.setHint("ID (0-9)");
        idInput.setInputType(2);
        idInput.setPadding(10, 10, 10, 10);
        idInput.setTextSize(getFontMultiplier() * 14.0f);
        idInput.setGravity(17);
        linearLayout.addView(idInput);
        String[] numbers = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "C", "0", "⌫"};
        int buttonHeight = getButtonHeight(40);
        float buttonTextSize = 14.0f * getFontMultiplier();
        int row = 0;
        while (true) {
            float f = 1.0f;
            if (row < 4) {
                LinearLayout rowLayout = new LinearLayout(this);
                rowLayout.setOrientation(i);
                rowLayout.setLayoutParams(new LinearLayout.LayoutParams(i2, i3));
                int col = 0;
                while (col < 3 && (index = (row * 3) + col) < numbers.length) {
                    final String num = numbers[index];
                    Button btn = new Button(this);
                    btn.setText(num);
                    btn.setLayoutParams(new LinearLayout.LayoutParams(i, buttonHeight, f));
                    btn.setTextSize(buttonTextSize);
                    if (num.equals("C") || num.equals("⌫")) {
                        btn.setBackground(GlassUI.glassSelector(getApplicationContext(), -3381760));
                    } else {
                        btn.setBackground(GlassUI.glassSelector(getApplicationContext(), -12303292));
                    }
                    btn.setTextColor(-1);
                    btn.setPadding(3, 3, 3, 3);
                    btn.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.18
                        @Override // android.view.View.OnClickListener
                        public void onClick(View v) {
                            if (num.equals("C")) {
                                idInput.setText("");
                                return;
                            }
                            if (num.equals("⌫")) {
                                String current = idInput.getText().toString();
                                if (current.length() > 0) {
                                    idInput.setText(current.substring(0, current.length() - 1));
                                    return;
                                }
                                return;
                            }
                            idInput.append(num);
                        }
                    });
                    rowLayout.addView(btn);
                    if (col < 2) {
                        View separator = new View(this);
                        separator.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(2.0f), buttonHeight));
                        rowLayout.addView(separator);
                    }
                    col++;
                    f = 1.0f;
                    i = 0;
                }
                linearLayout.addView(rowLayout);
                if (row < 3) {
                    View separator2 = new View(this);
                    separator2.setLayoutParams(new LinearLayout.LayoutParams(-1, 2));
                    separator2.setBackground(GlassUI.glassSelector(getApplicationContext(), -13421773));
                    linearLayout.addView(separator2);
                }
                row++;
                i = 0;
                i2 = -1;
                i3 = -2;
            } else {
                LinearLayout buttonRow = new LinearLayout(this);
                buttonRow.setOrientation(0);
                buttonRow.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
                buttonRow.setPadding(0, 8, 0, 0);
                Button btnBack = new Button(this);
                btnBack.setText("← Назад");
                btnBack.setLayoutParams(new LinearLayout.LayoutParams(0, buttonHeight, 1.0f));
                btnBack.setBackground(GlassUI.glassSelector(getApplicationContext(), -10066330));
                btnBack.setTextColor(-1);
                btnBack.setTextSize(buttonTextSize);
                btnBack.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.19
                    @Override // android.view.View.OnClickListener
                    public void onClick(View v) {
                        KeyboardService.this.showFamWarMenu();
                    }
                });
                Button btnSend = new Button(this);
                btnSend.setText("ОТПРАВИТЬ");
                btnSend.setLayoutParams(new LinearLayout.LayoutParams(0, buttonHeight, 1.0f));
                btnSend.setBackground(GlassUI.glassSelector(getApplicationContext(), -16733696));
                btnSend.setTextColor(-1);
                btnSend.setTextSize(buttonTextSize);
                btnSend.setTypeface(null, 1);
                btnSend.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.20
                    @Override // android.view.View.OnClickListener
                    public void onClick(View v) {
                        String id = idInput.getText().toString().trim();
                        if (id.isEmpty()) {
                            Toast.makeText(KeyboardService.this, "❌ Введите ID игрока!", 0).show();
                            return;
                        }
                        StringBuilder fullCmd = new StringBuilder();
                        if (KeyboardService.this.adminLevel < requiredLevel) {
                            fullCmd.append("/a");
                        }
                        fullCmd.append(command).append("").append(id);
                        if (!time.isEmpty()) {
                            fullCmd.append("").append(time);
                        }
                        if (!reason.isEmpty()) {
                            fullCmd.append("").append(reason);
                        }
                        String finalCommand = fullCmd.toString();
                        KeyboardService.this.sendCommandToChat(finalCommand);
                        if (KeyboardService.this.adminLevel < requiredLevel) {
                            Toast.makeText(KeyboardService.this, "⚠️ Отправлено через /a: " + finalCommand, 1).show();
                        } else {
                            Toast.makeText(KeyboardService.this, "✅ Отправлено: " + finalCommand, 0).show();
                        }
                        KeyboardService.this.showKeyboardView();
                    }
                });
                buttonRow.addView(btnBack);
                View separator3 = new View(this);
                separator3.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(8.0f), buttonHeight));
                buttonRow.addView(separator3);
                buttonRow.addView(btnSend);
                linearLayout.addView(buttonRow);
                this.menuLayout.addView(linearLayout);
                updateMenuButtonsHeight(this.menuLayout, buttonHeight);
                applyKeyboardTransparency(this.keyboardTransparency);
                return;
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showNormalPanel() {
        if (this.menuLayout == null) {
            return;
        }
        this.menuLayout.removeAllViews();
        this.menuLayout.setVisibility(0);
        View keyboardLayout = this.keyboardView.findViewById(R.id.keyboardLayout);
        if (keyboardLayout != null) {
            keyboardLayout.setVisibility(8);
        }
        TextView title = new TextView(this);
        title.setText("ОБЫЧНАЯ ПАНЕЛЬ");
        title.setTextColor(-1);
        title.setTextSize(getFontMultiplier() * 16.0f);
        title.setGravity(17);
        title.setPadding(5, 10, 5, 10);
        title.setBackground(GlassUI.glassSelector(getApplicationContext(), -13421773));
        this.menuLayout.addView(title);
        ScrollView scrollView = new ScrollView(this);
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(-1, getButtonHeight(350)));
        LinearLayout containerNormal = new LinearLayout(this);
        containerNormal.setOrientation(1);
        scrollView.addView(containerNormal);
        this.menuLayout.addView(scrollView);
        loadUserButtons(containerNormal);
        Button btnBack = new Button(this);
        btnBack.setText("← Назад к клавиатуре");
        btnBack.setLayoutParams(new LinearLayout.LayoutParams(-1, getButtonHeight(50)));
        btnBack.setBackground(GlassUI.glassSelector(getApplicationContext(), -10066330));
        btnBack.setTextColor(-1);
        btnBack.setTextSize(getFontMultiplier() * 16.0f);
        btnBack.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.21
            @Override // android.view.View.OnClickListener
            public void onClick(View v) {
                KeyboardService.this.showKeyboardView();
            }
        });
        this.menuLayout.addView(btnBack);
        updateMenuButtonsHeight(this.menuLayout, getButtonHeight(50));
        applyKeyboardTransparency(this.keyboardTransparency);
    }

    private void showKeyboardAdminForms() {
        if (menuLayout == null) return;
        menuLayout.removeAllViews();
        TextView title = new TextView(this);
        title.setText("ADMIN-ФОРМЫ"); title.setTextColor(-1); title.setTextSize(16); title.setGravity(17);
        menuLayout.addView(title);
        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this); list.setOrientation(1);
        java.util.ArrayList<AdminFormManager.Form> forms = AdminFormManager.getPending(this);
        if (forms.isEmpty()) {
            TextView empty = new TextView(this); empty.setText("Новых форм пока нет"); empty.setTextColor(-1); empty.setPadding(12,20,12,20); list.addView(empty);
        }
        for (final AdminFormManager.Form form : forms) {
            TextView item = new TextView(this);
            item.setText("<ADM> (" + form.rank + ") " + form.nickname + "[" + form.id + "]\n" + form.command + "\nНажмите, чтобы принять");
            item.setTextColor(-1); item.setPadding(12,12,12,12); item.setBackground(GlassUI.glassSelector(getApplicationContext(), 0xFF4B3B5F));
            item.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
                String command = AdminFormManager.makeAcceptedCommand(KeyboardService.this, form);
                InputConnection ic = getCurrentInputConnection();
                if (ic != null) ic.commitText(command, 1);
                AdminFormManager.removeForm(KeyboardService.this, form.uniqueKey());
                Toast.makeText(KeyboardService.this, "Форма вставлена в поле ввода", Toast.LENGTH_SHORT).show();
                showKeyboardAdminForms();
            }});
            list.addView(item);
        }
        scroll.addView(list); menuLayout.addView(scroll, new LinearLayout.LayoutParams(-1, getButtonHeight(220)));
        Button back = new Button(this); back.setText("← Назад"); back.setAllCaps(false); back.setOnClickListener(new View.OnClickListener(){@Override public void onClick(View v){showAdminPanel();}}); menuLayout.addView(back);
    }

    private void showCaughtReports() {
        if (menuLayout == null) return;
        menuLayout.removeAllViews();
        TextView title = new TextView(this); title.setText("ЛОВЛЯ РЕПОРТОВ • 60 СЕК."); title.setTextColor(-1); title.setGravity(17); title.setPadding(8,12,8,12); menuLayout.addView(title);
        long now = System.currentTimeMillis();
        SharedPreferences rp = getSharedPreferences("GTAndroidReports", 0);
        JSONArray arr;
        try { arr = new JSONArray(rp.getString("recent", "[]")); } catch (Exception e) { arr = new JSONArray(); }
        JSONArray fresh = new JSONArray();
        for (int i=0; i<arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o != null && now - o.optLong("time", 0) <= 60000L) {
                fresh.put(o); TextView t = new TextView(this); t.setText(o.optString("text", "Репорт")); t.setTextColor(-1); t.setPadding(10,10,10,10); menuLayout.addView(t);
            }
        }
        rp.edit().putString("recent", fresh.toString()).apply();
        if (fresh.length() == 0) { TextView empty = new TextView(this); empty.setText("Репортов за последние 60 секунд нет. Раздел обновляется при поступлении текста репорта в доступности."); empty.setTextColor(-1); empty.setPadding(10,15,10,15); menuLayout.addView(empty); }
        Button refresh = new Button(this); refresh.setText("↻ Обновить"); refresh.setAllCaps(false); refresh.setOnClickListener(new View.OnClickListener(){@Override public void onClick(View v){showCaughtReports();}}); menuLayout.addView(refresh);
        Button back = new Button(this); back.setText("← Назад"); back.setAllCaps(false); back.setOnClickListener(new View.OnClickListener(){@Override public void onClick(View v){showAdminPanel();}}); menuLayout.addView(back);
    }

    private void setupAdminButtons(LinearLayout linearLayout) {
        String[] names = {"Ответить на репорт", "Наказать игрока", "Следить sp(off)", "Быстрая команда", "Точка спавна", "Читы", "Тп к себе", "Статистика"};
        int buttonHeight = getButtonHeight(90);
        float buttonTextSize = getFontMultiplier() * 10.0f;
        for (int row = 0; row < 3; row++) {
            LinearLayout rowLayout = new LinearLayout(this);
            int i = 0;
            rowLayout.setOrientation(0);
            rowLayout.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
            int col = 0;
            while (col < 3) {
                int index = (row * 3) + col;
                if (index >= names.length) {
                    break;
                }
                final String name = names[index];
                Button btn = new Button(this);
                btn.setText(name);
                btn.setLayoutParams(new LinearLayout.LayoutParams(i, buttonHeight, 1.0f));
                btn.setBackground(GlassUI.glassSelector(getApplicationContext(), -11184811));
                btn.setTextColor(-1);
                btn.setPadding(3, 3, 3, 3);
                btn.setTextSize(buttonTextSize);
                btn.setMaxLines(2);
                btn.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.22
                    @Override // android.view.View.OnClickListener
                    public void onClick(View v) {
                        if (name.equals("Читы")) {
                            KeyboardService.this.showCheatsMenu();
                            return;
                        }
                        if (name.equals("Наказать игрока")) {
                            KeyboardService.this.showPunishmentIdInput();
                            return;
                        }
                        if (name.equals("Ответить на репорт")) {
                            KeyboardService.this.showReportAnswerIdInput();
                            return;
                        }
                        if (name.equals("Точка спавна")) {
                            KeyboardService.this.showSpawnPointMenu();
                            return;
                        }
                        if (name.equals("Следить sp(off)")) {
                            KeyboardService.this.showSpectateMenu();
                            return;
                        }
                        if (name.equals("Быстрая команда")) {
                            KeyboardService.this.showQuickCommandMenu();
                        } else if (name.equals("Тп к себе")) {
                            KeyboardService.this.showTpToMeMenu();
                        } else if (name.equals("Статистика")) {
                            KeyboardService.this.showStatisticsMenu();
                        }
                    }
                });
                btn.setOnTouchListener(getButtonTouchListener(-8947849, -11184811));
                rowLayout.addView(btn);
                if (col < 2) {
                    View separator = new View(this);
                    separator.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(3.0f), -1));
                    rowLayout.addView(separator);
                }
                col++;
                i = 0;
            }
            linearLayout.addView(rowLayout);
            if (row < 2) {
                View separator2 = new View(this);
                separator2.setLayoutParams(new LinearLayout.LayoutParams(-1, 3));
                separator2.setBackground(GlassUI.glassSelector(getApplicationContext(), -13421773));
                linearLayout.addView(separator2);
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showPunishmentIdInput() {
        int index;
        if (this.menuLayout == null) {
            return;
        }
        this.menuLayout.removeAllViews();
        int i = 0;
        this.menuLayout.setVisibility(0);
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        linearLayout.setPadding(10, 10, 10, 10);
        linearLayout.setBackground(GlassUI.glassSelector(getApplicationContext(), -15066598));
        TextView title = new TextView(this);
        title.setText("ВВЕДИТЕ ID");
        int i2 = -1;
        title.setTextColor(-1);
        title.setTextSize(getFontMultiplier() * 16.0f);
        title.setGravity(17);
        title.setPadding(0, 10, 0, 15);
        linearLayout.addView(title);
        final EditText idInput = new EditText(this);
        idInput.setBackground(GlassUI.lightCard(this, 10.0f));
        idInput.setPadding(24, 18, 24, 18);
        idInput.setTextColor(-14935010);
        idInput.setHintTextColor(-7434605);
        int i3 = -2;
        idInput.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        idInput.setBackground(GlassUI.glassSelector(getApplicationContext(), -13421773));
        idInput.setTextColor(-1);
        idInput.setHint("ID игрока");
        idInput.setInputType(2);
        idInput.setPadding(10, 10, 10, 10);
        idInput.setTextSize(getFontMultiplier() * 14.0f);
        linearLayout.addView(idInput);
        String[] numbers = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "C", "0", "⌫"};
        int buttonHeight = getButtonHeight(50);
        float buttonTextSize = getFontMultiplier() * 14.0f;
        int row = 0;
        while (true) {
            float f = 1.0f;
            if (row < 4) {
                LinearLayout rowLayout = new LinearLayout(this);
                rowLayout.setOrientation(i);
                rowLayout.setLayoutParams(new LinearLayout.LayoutParams(i2, i3));
                int col = 0;
                while (col < 3 && (index = (row * 3) + col) < numbers.length) {
                    final String num = numbers[index];
                    Button btn = new Button(this);
                    btn.setText(num);
                    btn.setLayoutParams(new LinearLayout.LayoutParams(i, buttonHeight, f));
                    btn.setTextSize(buttonTextSize);
                    if (num.equals("C") || num.equals("⌫")) {
                        btn.setBackground(GlassUI.glassSelector(getApplicationContext(), -3381760));
                    } else {
                        btn.setBackground(GlassUI.glassSelector(getApplicationContext(), -12303292));
                    }
                    btn.setTextColor(-1);
                    btn.setPadding(5, 5, 5, 5);
                    btn.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.23
                        @Override // android.view.View.OnClickListener
                        public void onClick(View v) {
                            if (num.equals("C")) {
                                idInput.setText("");
                                return;
                            }
                            if (num.equals("⌫")) {
                                String current = idInput.getText().toString();
                                if (current.length() > 0) {
                                    idInput.setText(current.substring(0, current.length() - 1));
                                    return;
                                }
                                return;
                            }
                            idInput.append(num);
                        }
                    });
                    rowLayout.addView(btn);
                    if (col < 2) {
                        View separator = new View(this);
                        separator.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(3.0f), buttonHeight));
                        rowLayout.addView(separator);
                    }
                    col++;
                    i = 0;
                    f = 1.0f;
                }
                linearLayout.addView(rowLayout);
                if (row < 3) {
                    View separator2 = new View(this);
                    separator2.setLayoutParams(new LinearLayout.LayoutParams(-1, 3));
                    separator2.setBackground(GlassUI.glassSelector(getApplicationContext(), -13421773));
                    linearLayout.addView(separator2);
                }
                row++;
                i = 0;
                i2 = -1;
                i3 = -2;
            } else {
                LinearLayout buttonRow = new LinearLayout(this);
                buttonRow.setOrientation(0);
                buttonRow.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
                Button btnBack = new Button(this);
                btnBack.setText("← Назад");
                btnBack.setLayoutParams(new LinearLayout.LayoutParams(0, buttonHeight, 1.0f));
                btnBack.setBackground(GlassUI.glassSelector(getApplicationContext(), -10066330));
                btnBack.setTextColor(-1);
                btnBack.setTextSize(buttonTextSize);
                btnBack.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.24
                    @Override // android.view.View.OnClickListener
                    public void onClick(View v) {
                        KeyboardService.this.showKeyboardView();
                    }
                });
                Button btnContinue = new Button(this);
                btnContinue.setText("Продолжить →");
                btnContinue.setLayoutParams(new LinearLayout.LayoutParams(0, buttonHeight, 1.0f));
                btnContinue.setBackground(GlassUI.glassSelector(getApplicationContext(), -16733696));
                btnContinue.setTextColor(-1);
                btnContinue.setTextSize(buttonTextSize);
                btnContinue.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.25
                    @Override // android.view.View.OnClickListener
                    public void onClick(View v) {
                        String id = idInput.getText().toString();
                        if (!id.isEmpty()) {
                            KeyboardService.this.selectedPunishmentId = id;
                            KeyboardService.this.showPunishmentTypes();
                        } else {
                            Toast.makeText(KeyboardService.this, "❌ Введите ID", 0).show();
                        }
                    }
                });
                buttonRow.addView(btnBack);
                View separator3 = new View(this);
                separator3.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(5.0f), buttonHeight));
                buttonRow.addView(separator3);
                buttonRow.addView(btnContinue);
                linearLayout.addView(buttonRow);
                this.menuLayout.addView(linearLayout);
                updateMenuButtonsHeight(this.menuLayout, buttonHeight);
                applyKeyboardTransparency(this.keyboardTransparency);
                return;
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showCheatsMenu() {
        if (this.menuLayout == null) {
            return;
        }
        this.menuLayout.removeAllViews();
        this.menuLayout.setVisibility(0);
        View keyboardLayout = this.keyboardView.findViewById(R.id.keyboardLayout);
        if (keyboardLayout != null) {
            keyboardLayout.setVisibility(8);
        }
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(1);
        container.setPadding(10, 10, 10, 10);
        container.setBackground(GlassUI.glassSelector(getApplicationContext(), -15066598));
        TextView title = new TextView(this);
        title.setText("ЧИТЫ");
        title.setTextColor(-1);
        title.setTextSize(getFontMultiplier() * 16.0f);
        title.setGravity(17);
        title.setPadding(0, 10, 0, 15);
        container.addView(title);
        int buttonHeight = getButtonHeight(70);
        float buttonTextSize = getFontMultiplier() * 14.0f;
        Button btnImmortality = new Button(this);
        btnImmortality.setText("1. Бессмертие");
        btnImmortality.setLayoutParams(new LinearLayout.LayoutParams(-1, buttonHeight));
        btnImmortality.setBackground(GlassUI.glassSelector(getApplicationContext(), -12303292));
        btnImmortality.setTextColor(-1);
        btnImmortality.setTextSize(buttonTextSize);
        btnImmortality.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.26
            @Override // android.view.View.OnClickListener
            public void onClick(View v) {
                KeyboardService.this.sendCommandToChat("/gm");
                KeyboardService.this.showAdminPanel();
            }
        });
        container.addView(btnImmortality);
        addSeparator(container);
        Button btnMask = new Button(this);
        btnMask.setText("2. Маска");
        btnMask.setLayoutParams(new LinearLayout.LayoutParams(-1, buttonHeight));
        btnMask.setBackground(GlassUI.glassSelector(getApplicationContext(), -12303292));
        btnMask.setTextColor(-1);
        btnMask.setTextSize(buttonTextSize);
        btnMask.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.27
            @Override // android.view.View.OnClickListener
            public void onClick(View v) {
                KeyboardService.this.sendCommandToChat("/clist");
                KeyboardService.this.showAdminPanel();
            }
        });
        container.addView(btnMask);
        addSeparator(container);
        Button btnHp = new Button(this);
        btnHp.setText("3. Выдать ХП");
        btnHp.setLayoutParams(new LinearLayout.LayoutParams(-1, buttonHeight));
        btnHp.setBackground(GlassUI.glassSelector(getApplicationContext(), -12303292));
        btnHp.setTextColor(-1);
        btnHp.setTextSize(buttonTextSize);
        btnHp.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.28
            @Override // android.view.View.OnClickListener
            public void onClick(View v) {
                KeyboardService.this.showHpIdInput();
            }
        });
        container.addView(btnHp);
        addSeparator(container);
        Button btnCarId = new Button(this);
        btnCarId.setText("4. ID ТС");
        btnCarId.setLayoutParams(new LinearLayout.LayoutParams(-1, buttonHeight));
        btnCarId.setBackground(GlassUI.glassSelector(getApplicationContext(), -12303292));
        btnCarId.setTextColor(-1);
        btnCarId.setTextSize(buttonTextSize);
        btnCarId.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.29
            @Override // android.view.View.OnClickListener
            public void onClick(View v) {
                KeyboardService.this.sendCommandToChat("/dl");
                KeyboardService.this.showAdminPanel();
            }
        });
        container.addView(btnCarId);
        addSeparator(container);
        Button btnBack = new Button(this);
        btnBack.setText("← Назад к клавиатуре");
        btnBack.setLayoutParams(new LinearLayout.LayoutParams(-1, getButtonHeight(50)));
        btnBack.setBackground(GlassUI.glassSelector(getApplicationContext(), -10066330));
        btnBack.setTextColor(-1);
        btnBack.setTextSize(getFontMultiplier() * 14.0f);
        btnBack.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.30
            @Override // android.view.View.OnClickListener
            public void onClick(View v) {
                KeyboardService.this.showKeyboardView();
            }
        });
        container.addView(btnBack);
        this.menuLayout.addView(container);
        updateMenuButtonsHeight(this.menuLayout, getButtonHeight(50));
        applyKeyboardTransparency(this.keyboardTransparency);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showHpIdInput() {
        int index;
        if (this.menuLayout == null) {
            return;
        }
        this.menuLayout.removeAllViews();
        int i = 0;
        this.menuLayout.setVisibility(0);
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        linearLayout.setPadding(10, 10, 10, 10);
        linearLayout.setBackground(GlassUI.glassSelector(getApplicationContext(), -15066598));
        TextView title = new TextView(this);
        title.setText("ВЫДАТЬ ХП");
        int i2 = -1;
        title.setTextColor(-1);
        title.setTextSize(getFontMultiplier() * 16.0f);
        title.setGravity(17);
        title.setPadding(0, 10, 0, 15);
        linearLayout.addView(title);
        final EditText idInput = new EditText(this);
        idInput.setBackground(GlassUI.lightCard(this, 10.0f));
        idInput.setPadding(24, 18, 24, 18);
        idInput.setTextColor(-14935010);
        idInput.setHintTextColor(-7434605);
        int i3 = -2;
        idInput.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        idInput.setBackground(GlassUI.glassSelector(getApplicationContext(), -13421773));
        idInput.setTextColor(-1);
        idInput.setHint("ID игрока (0-9)");
        idInput.setInputType(2);
        idInput.setPadding(10, 10, 10, 10);
        idInput.setTextSize(getFontMultiplier() * 14.0f);
        linearLayout.addView(idInput);
        String[] numbers = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "C", "0", "⌫"};
        int buttonHeight = getButtonHeight(50);
        float buttonTextSize = getFontMultiplier() * 14.0f;
        int row = 0;
        while (true) {
            float f = 1.0f;
            if (row < 4) {
                LinearLayout rowLayout = new LinearLayout(this);
                rowLayout.setOrientation(i);
                rowLayout.setLayoutParams(new LinearLayout.LayoutParams(i2, i3));
                int col = 0;
                while (col < 3 && (index = (row * 3) + col) < numbers.length) {
                    final String num = numbers[index];
                    Button btn = new Button(this);
                    btn.setText(num);
                    btn.setLayoutParams(new LinearLayout.LayoutParams(i, buttonHeight, f));
                    btn.setTextSize(buttonTextSize);
                    if (num.equals("C") || num.equals("⌫")) {
                        btn.setBackground(GlassUI.glassSelector(getApplicationContext(), -3381760));
                    } else {
                        btn.setBackground(GlassUI.glassSelector(getApplicationContext(), -12303292));
                    }
                    btn.setTextColor(-1);
                    btn.setPadding(5, 5, 5, 5);
                    btn.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.31
                        @Override // android.view.View.OnClickListener
                        public void onClick(View v) {
                            if (num.equals("C")) {
                                idInput.setText("");
                                return;
                            }
                            if (num.equals("⌫")) {
                                String current = idInput.getText().toString();
                                if (current.length() > 0) {
                                    idInput.setText(current.substring(0, current.length() - 1));
                                    return;
                                }
                                return;
                            }
                            idInput.append(num);
                        }
                    });
                    rowLayout.addView(btn);
                    if (col < 2) {
                        View separator = new View(this);
                        separator.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(3.0f), buttonHeight));
                        rowLayout.addView(separator);
                    }
                    col++;
                    i = 0;
                    f = 1.0f;
                }
                linearLayout.addView(rowLayout);
                if (row < 3) {
                    View separator2 = new View(this);
                    separator2.setLayoutParams(new LinearLayout.LayoutParams(-1, 3));
                    separator2.setBackground(GlassUI.glassSelector(getApplicationContext(), -13421773));
                    linearLayout.addView(separator2);
                }
                row++;
                i = 0;
                i2 = -1;
                i3 = -2;
            } else {
                LinearLayout buttonRow = new LinearLayout(this);
                buttonRow.setOrientation(0);
                buttonRow.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
                Button btnBack = new Button(this);
                btnBack.setText("← Назад");
                btnBack.setLayoutParams(new LinearLayout.LayoutParams(0, buttonHeight, 1.0f));
                btnBack.setBackground(GlassUI.glassSelector(getApplicationContext(), -10066330));
                btnBack.setTextColor(-1);
                btnBack.setTextSize(buttonTextSize);
                btnBack.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.32
                    @Override // android.view.View.OnClickListener
                    public void onClick(View v) {
                        KeyboardService.this.showCheatsMenu();
                    }
                });
                Button btnSend = new Button(this);
                btnSend.setText("Отправить");
                btnSend.setLayoutParams(new LinearLayout.LayoutParams(0, buttonHeight, 1.0f));
                btnSend.setBackground(GlassUI.glassSelector(getApplicationContext(), -16733696));
                btnSend.setTextColor(-1);
                btnSend.setTextSize(buttonTextSize);
                btnSend.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.33
                    @Override // android.view.View.OnClickListener
                    public void onClick(View v) {
                        String id = idInput.getText().toString();
                        if (!id.isEmpty()) {
                            KeyboardService.this.sendCommandToChat("/hp" + id + " 100");
                            KeyboardService.this.showAdminPanel();
                        } else {
                            Toast.makeText(KeyboardService.this, "❌ Введите ID", 0).show();
                        }
                    }
                });
                buttonRow.addView(btnBack);
                View separator3 = new View(this);
                separator3.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(5.0f), buttonHeight));
                buttonRow.addView(separator3);
                buttonRow.addView(btnSend);
                linearLayout.addView(buttonRow);
                this.menuLayout.addView(linearLayout);
                updateMenuButtonsHeight(this.menuLayout, buttonHeight);
                applyKeyboardTransparency(this.keyboardTransparency);
                return;
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showStatisticsMenu() {
        if (this.menuLayout == null) {
            return;
        }
        this.menuLayout.removeAllViews();
        this.menuLayout.setVisibility(0);
        View keyboardLayout = this.keyboardView.findViewById(R.id.keyboardLayout);
        if (keyboardLayout != null) {
            keyboardLayout.setVisibility(8);
        }
        checkAndResetWeeklyStats();
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(1);
        container.setPadding(15, 15, 15, 15);
        container.setBackground(GlassUI.glassSelector(getApplicationContext(), -15066598));
        TextView title = new TextView(this);
        title.setText("СТАТИСТИКА РЕПОРТОВ");
        title.setTextColor(-1);
        title.setTextSize(getFontMultiplier() * 16.0f);
        title.setGravity(17);
        title.setPadding(0, 10, 0, 15);
        container.addView(title);
        SharedPreferences statsPrefs = getSharedPreferences(PREFS_STATS, 0);
        String[] days = {"Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"};
        int totalWeek = 0;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 7; i++) {
            int count = statsPrefs.getInt(KEY_WEEKLY_PREFIX + i, 0);
            totalWeek += count;
            sb.append(days[i]).append(" -").append(count).append("\n");
        }
        int allTime = statsPrefs.getInt(KEY_TOTAL_REPORTS, 0);
        TextView statsText = new TextView(this);
        statsText.setText(sb.toString() + "\nИтог -" + totalWeek + "\nЗа все время -" + allTime);
        statsText.setTextColor(-1);
        statsText.setTextSize(getFontMultiplier() * 14.0f);
        statsText.setPadding(10, 10, 10, 10);
        statsText.setBackground(GlassUI.glassSelector(getApplicationContext(), -14540254));
        container.addView(statsText);
        Button btnResetWeek = new Button(this);
        btnResetWeek.setText("🔄 Сбросить неделю");
        btnResetWeek.setLayoutParams(new LinearLayout.LayoutParams(-1, getButtonHeight(50)));
        btnResetWeek.setBackground(GlassUI.glassSelector(getApplicationContext(), -3381760));
        btnResetWeek.setTextColor(-1);
        btnResetWeek.setTextSize(getFontMultiplier() * 14.0f);
        btnResetWeek.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.34
            @Override // android.view.View.OnClickListener
            public void onClick(View v) {
                KeyboardService.this.resetWeeklyStatsManual();
                KeyboardService.this.showStatisticsMenu();
            }
        });
        container.addView(btnResetWeek);
        addSeparator(container);
        Button btnBack = new Button(this);
        btnBack.setText("← Назад к клавиатуре");
        btnBack.setLayoutParams(new LinearLayout.LayoutParams(-1, getButtonHeight(50)));
        btnBack.setBackground(GlassUI.glassSelector(getApplicationContext(), -10066330));
        btnBack.setTextColor(-1);
        btnBack.setTextSize(getFontMultiplier() * 14.0f);
        btnBack.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.35
            @Override // android.view.View.OnClickListener
            public void onClick(View v) {
                KeyboardService.this.showKeyboardView();
            }
        });
        container.addView(btnBack);
        this.menuLayout.addView(container);
        updateMenuButtonsHeight(this.menuLayout, getButtonHeight(50));
        applyKeyboardTransparency(this.keyboardTransparency);
    }

    private void checkAndResetWeeklyStats() {
        SharedPreferences statsPrefs = getSharedPreferences(PREFS_STATS, 0);
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Europe/Moscow"));
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        sdf.setTimeZone(TimeZone.getTimeZone("Europe/Moscow"));
        String todayStr = sdf.format(new Date());
        String lastResetDate = statsPrefs.getString(KEY_LAST_RESET_DATE, "");
        int dayOfWeek = calendar.get(7);
        int mondayBasedIndex = dayOfWeek == 1 ? 6 : dayOfWeek - 2;
        if (!lastResetDate.equals(todayStr) && mondayBasedIndex == 0) {
            resetWeeklyStatsInternal(statsPrefs);
            statsPrefs.edit().putString(KEY_LAST_RESET_DATE, todayStr).apply();
        }
    }

    private void resetWeeklyStatsInternal(SharedPreferences prefs) {
        SharedPreferences.Editor editor = prefs.edit();
        for (int i = 0; i < 7; i++) {
            editor.putInt(KEY_WEEKLY_PREFIX + i, 0);
        }
        editor.apply();
        Toast.makeText(this, "Недельная статистика сброшена", 0).show();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void resetWeeklyStatsManual() {
        SharedPreferences statsPrefs = getSharedPreferences(PREFS_STATS, 0);
        resetWeeklyStatsInternal(statsPrefs);
        Calendar.getInstance(TimeZone.getTimeZone("Europe/Moscow"));
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        sdf.setTimeZone(TimeZone.getTimeZone("Europe/Moscow"));
        statsPrefs.edit().putString(KEY_LAST_RESET_DATE, sdf.format(new Date())).apply();
    }

    private void incrementReportStat() {
        SharedPreferences statsPrefs = getSharedPreferences(PREFS_STATS, 0);
        SharedPreferences.Editor editor = statsPrefs.edit();
        int total = statsPrefs.getInt(KEY_TOTAL_REPORTS, 0);
        editor.putInt(KEY_TOTAL_REPORTS, total + 1);
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Europe/Moscow"));
        int dayOfWeek = calendar.get(7);
        int mondayBasedIndex = dayOfWeek == 1 ? 6 : dayOfWeek - 2;
        int daily = statsPrefs.getInt(KEY_WEEKLY_PREFIX + mondayBasedIndex, 0);
        editor.putInt(KEY_WEEKLY_PREFIX + mondayBasedIndex, daily + 1);
        editor.apply();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showTpToMeMenu() {
        if (this.menuLayout == null) {
            return;
        }
        this.menuLayout.removeAllViews();
        this.menuLayout.setVisibility(0);
        View keyboardLayout = this.keyboardView.findViewById(R.id.keyboardLayout);
        if (keyboardLayout != null) {
            keyboardLayout.setVisibility(8);
        }
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(1);
        container.setPadding(10, 10, 10, 10);
        container.setBackground(GlassUI.glassSelector(getApplicationContext(), -15066598));
        TextView title = new TextView(this);
        title.setText("ТП К СЕБЕ");
        title.setTextColor(-1);
        title.setTextSize(getFontMultiplier() * 16.0f);
        title.setGravity(17);
        title.setPadding(0, 10, 0, 15);
        container.addView(title);
        int buttonHeight = getButtonHeight(70);
        float buttonTextSize = getFontMultiplier() * 14.0f;
        Button btnCar = new Button(this);
        btnCar.setText("1. Тп тс к себе");
        btnCar.setLayoutParams(new LinearLayout.LayoutParams(-1, buttonHeight));
        btnCar.setBackground(GlassUI.glassSelector(getApplicationContext(), -12303292));
        btnCar.setTextColor(-1);
        btnCar.setTextSize(buttonTextSize);
        btnCar.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.36
            @Override // android.view.View.OnClickListener
            public void onClick(View v) {
                KeyboardService.this.showGenericIdInput("ТП ТС К СЕБЕ", "ID транспорта", new CommandCallback() { // from class: gtandroid.universal.KeyboardService.36.1
                    @Override // gtandroid.universal.KeyboardService.CommandCallback
                    public void onCommand(String id) {
                        KeyboardService.this.sendCommandToChat("/getcar" + id);
                    }
                });
            }
        });
        container.addView(btnCar);
        addSeparator(container);
        Button btnPlayerHere = new Button(this);
        btnPlayerHere.setText("2. Тп игрока к себе");
        btnPlayerHere.setLayoutParams(new LinearLayout.LayoutParams(-1, buttonHeight));
        btnPlayerHere.setBackground(GlassUI.glassSelector(getApplicationContext(), -12303292));
        btnPlayerHere.setTextColor(-1);
        btnPlayerHere.setTextSize(buttonTextSize);
        btnPlayerHere.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.37
            @Override // android.view.View.OnClickListener
            public void onClick(View v) {
                KeyboardService.this.showGenericIdInput("ТП ИГРОКА К СЕБЕ", "ID игрока", new CommandCallback() { // from class: gtandroid.universal.KeyboardService.37.1
                    @Override // gtandroid.universal.KeyboardService.CommandCallback
                    public void onCommand(String id) {
                        KeyboardService.this.sendCommandToChat("/gethere" + id);
                    }
                });
            }
        });
        container.addView(btnPlayerHere);
        addSeparator(container);
        Button btnGoTo = new Button(this);
        btnGoTo.setText("3. Тп к игроку");
        btnGoTo.setLayoutParams(new LinearLayout.LayoutParams(-1, buttonHeight));
        btnGoTo.setBackground(GlassUI.glassSelector(getApplicationContext(), -12303292));
        btnGoTo.setTextColor(-1);
        btnGoTo.setTextSize(buttonTextSize);
        btnGoTo.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.38
            @Override // android.view.View.OnClickListener
            public void onClick(View v) {
                KeyboardService.this.showGenericIdInput("ТП К ИГРОКУ", "ID игрока", new CommandCallback() { // from class: gtandroid.universal.KeyboardService.38.1
                    @Override // gtandroid.universal.KeyboardService.CommandCallback
                    public void onCommand(String id) {
                        KeyboardService.this.sendCommandToChat("/goto" + id);
                    }
                });
            }
        });
        container.addView(btnGoTo);
        addSeparator(container);
        Button btnBack = new Button(this);
        btnBack.setText("← Назад к клавиатуре");
        btnBack.setLayoutParams(new LinearLayout.LayoutParams(-1, getButtonHeight(50)));
        btnBack.setBackground(GlassUI.glassSelector(getApplicationContext(), -10066330));
        btnBack.setTextColor(-1);
        btnBack.setTextSize(getFontMultiplier() * 14.0f);
        btnBack.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.39
            @Override // android.view.View.OnClickListener
            public void onClick(View v) {
                KeyboardService.this.showKeyboardView();
            }
        });
        container.addView(btnBack);
        this.menuLayout.addView(container);
        updateMenuButtonsHeight(this.menuLayout, getButtonHeight(50));
        applyKeyboardTransparency(this.keyboardTransparency);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showGenericIdInput(String titleText, String hintText, final CommandCallback callback) {
        String[] numbers;
        TextView title;
        if (this.menuLayout == null) {
            return;
        }
        this.menuLayout.removeAllViews();
        int i = 0;
        this.menuLayout.setVisibility(0);
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        linearLayout.setPadding(10, 10, 10, 10);
        linearLayout.setBackground(GlassUI.glassSelector(getApplicationContext(), -15066598));
        TextView title2 = new TextView(this);
        title2.setText("" + titleText + "");
        int i2 = -1;
        title2.setTextColor(-1);
        title2.setTextSize(getFontMultiplier() * 16.0f);
        title2.setGravity(17);
        title2.setPadding(0, 10, 0, 15);
        linearLayout.addView(title2);
        final EditText idInput = new EditText(this);
        idInput.setBackground(GlassUI.lightCard(this, 10.0f));
        idInput.setPadding(24, 18, 24, 18);
        idInput.setTextColor(-14935010);
        idInput.setHintTextColor(-7434605);
        int i3 = -2;
        idInput.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        idInput.setBackground(GlassUI.glassSelector(getApplicationContext(), -13421773));
        idInput.setTextColor(-1);
        idInput.setHint(hintText);
        idInput.setInputType(2);
        idInput.setPadding(10, 10, 10, 10);
        idInput.setTextSize(getFontMultiplier() * 14.0f);
        linearLayout.addView(idInput);
        String[] numbers2 = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "C", "0", "⌫"};
        int buttonHeight = getButtonHeight(50);
        float buttonTextSize = getFontMultiplier() * 14.0f;
        int row = 0;
        while (true) {
            float f = 1.0f;
            if (row < 4) {
                LinearLayout rowLayout = new LinearLayout(this);
                rowLayout.setOrientation(i);
                rowLayout.setLayoutParams(new LinearLayout.LayoutParams(i2, i3));
                int col = 0;
                while (true) {
                    if (col >= 3) {
                        numbers = numbers2;
                        title = title2;
                        break;
                    }
                    int index = (row * 3) + col;
                    if (index >= numbers2.length) {
                        numbers = numbers2;
                        title = title2;
                        break;
                    }
                    final String num = numbers2[index];
                    Button btn = new Button(this);
                    btn.setText(num);
                    String[] numbers3 = numbers2;
                    TextView title3 = title2;
                    btn.setLayoutParams(new LinearLayout.LayoutParams(0, buttonHeight, f));
                    btn.setTextSize(buttonTextSize);
                    if (num.equals("C") || num.equals("⌫")) {
                        btn.setBackground(GlassUI.glassSelector(getApplicationContext(), -3381760));
                    } else {
                        btn.setBackground(GlassUI.glassSelector(getApplicationContext(), -12303292));
                    }
                    btn.setTextColor(-1);
                    btn.setPadding(5, 5, 5, 5);
                    btn.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.40
                        @Override // android.view.View.OnClickListener
                        public void onClick(View v) {
                            if (num.equals("C")) {
                                idInput.setText("");
                                return;
                            }
                            if (num.equals("⌫")) {
                                String current = idInput.getText().toString();
                                if (current.length() > 0) {
                                    idInput.setText(current.substring(0, current.length() - 1));
                                    return;
                                }
                                return;
                            }
                            idInput.append(num);
                        }
                    });
                    rowLayout.addView(btn);
                    if (col < 2) {
                        View separator = new View(this);
                        separator.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(3.0f), buttonHeight));
                        rowLayout.addView(separator);
                    }
                    col++;
                    numbers2 = numbers3;
                    title2 = title3;
                    f = 1.0f;
                }
                linearLayout.addView(rowLayout);
                if (row < 3) {
                    View separator2 = new View(this);
                    separator2.setLayoutParams(new LinearLayout.LayoutParams(-1, 3));
                    separator2.setBackground(GlassUI.glassSelector(getApplicationContext(), -13421773));
                    linearLayout.addView(separator2);
                }
                row++;
                numbers2 = numbers;
                title2 = title;
                i = 0;
                i2 = -1;
                i3 = -2;
            } else {
                LinearLayout buttonRow = new LinearLayout(this);
                buttonRow.setOrientation(0);
                buttonRow.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
                Button btnBack = new Button(this);
                btnBack.setText("← Назад");
                btnBack.setLayoutParams(new LinearLayout.LayoutParams(0, buttonHeight, 1.0f));
                btnBack.setBackground(GlassUI.glassSelector(getApplicationContext(), -10066330));
                btnBack.setTextColor(-1);
                btnBack.setTextSize(buttonTextSize);
                btnBack.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.41
                    @Override // android.view.View.OnClickListener
                    public void onClick(View v) {
                        KeyboardService.this.showTpToMeMenu();
                    }
                });
                Button btnSend = new Button(this);
                btnSend.setText("Отправить");
                btnSend.setLayoutParams(new LinearLayout.LayoutParams(0, buttonHeight, 1.0f));
                btnSend.setBackground(GlassUI.glassSelector(getApplicationContext(), -16733696));
                btnSend.setTextColor(-1);
                btnSend.setTextSize(buttonTextSize);
                btnSend.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.42
                    @Override // android.view.View.OnClickListener
                    public void onClick(View v) {
                        String id = idInput.getText().toString();
                        if (id.isEmpty()) {
                            Toast.makeText(KeyboardService.this, "❌ Введите ID", 0).show();
                        } else {
                            callback.onCommand(id);
                            KeyboardService.this.showKeyboardView();
                        }
                    }
                });
                buttonRow.addView(btnBack);
                View separator3 = new View(this);
                separator3.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(5.0f), buttonHeight));
                buttonRow.addView(separator3);
                buttonRow.addView(btnSend);
                linearLayout.addView(buttonRow);
                this.menuLayout.addView(linearLayout);
                updateMenuButtonsHeight(this.menuLayout, buttonHeight);
                applyKeyboardTransparency(this.keyboardTransparency);
                return;
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showKeyboardView() {
        if (this.menuLayout != null) {
            this.menuLayout.setVisibility(8);
        }
        View keyboardLayout = this.keyboardView.findViewById(R.id.keyboardLayout);
        if (keyboardLayout != null) {
            keyboardLayout.setVisibility(0);
        }
        this.showMenu = false;
    }

    private void toggleKeyboard() {
        this.showKeyboard = !this.showKeyboard;
        View keyboardLayout = this.keyboardView.findViewById(R.id.keyboardLayout);
        if (keyboardLayout != null) {
            keyboardLayout.setVisibility(this.showKeyboard ? 0 : 8);
        }
        if (this.menuView != null) {
            this.menuView.setVisibility(8);
            this.showMenu = false;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void toggleSymbolsPage() {
        Button[] toggleButtons;
        String toggleText;
        this.showSymbolsPage = !this.showSymbolsPage;
        LinearLayout numbersRow_ru = (LinearLayout) this.keyboardView.findViewById(R.id.numbersRowTop_ru);
        LinearLayout numbersRow_en = (LinearLayout) this.keyboardView.findViewById(R.id.numbersRowTop_en);
        LinearLayout symbolsRow_ru = (LinearLayout) this.keyboardView.findViewById(R.id.symbolsRowTop_ru);
        LinearLayout symbolsRow_en = (LinearLayout) this.keyboardView.findViewById(R.id.symbolsRowTop_en);
        LinearLayout numbersRowTop_ru = (LinearLayout) this.keyboardView.findViewById(R.id.numbersRowTop_ru);
        LinearLayout numbersRowTop_en = (LinearLayout) this.keyboardView.findViewById(R.id.numbersRowTop_en);
        LinearLayout symbolsRowTop_ru = (LinearLayout) this.keyboardView.findViewById(R.id.symbolsRowTop_ru);
        LinearLayout symbolsRowTop_en = (LinearLayout) this.keyboardView.findViewById(R.id.symbolsRowTop_en);
        Button btnToggle_ru = (Button) this.keyboardView.findViewById(R.id.btnToggleSymbolsTop_ru);
        Button btnToggle_ru2 = (Button) this.keyboardView.findViewById(R.id.btnToggleSymbolsTop_ru2);
        Button btnToggle_en = (Button) this.keyboardView.findViewById(R.id.btnToggleSymbolsTop_en);
        Button btnToggle_en2 = (Button) this.keyboardView.findViewById(R.id.btnToggleSymbolsTop_en2);
        Button btnToggleTop_ru = (Button) this.keyboardView.findViewById(R.id.btnToggleSymbolsTop_ru);
        Button btnToggleTop_ru2 = (Button) this.keyboardView.findViewById(R.id.btnToggleSymbolsTop_ru2);
        Button btnToggleTop_en = (Button) this.keyboardView.findViewById(R.id.btnToggleSymbolsTop_en);
        Button btnToggleTop_en2 = (Button) this.keyboardView.findViewById(R.id.btnToggleSymbolsTop_en2);
        int numbersVisibility = this.showSymbolsPage ? 8 : 0;
        int symbolsVisibility = this.showSymbolsPage ? 0 : 8;
        String toggleText2 = "123";
        View[] numbersRows = {numbersRow_ru, numbersRow_en, numbersRowTop_ru, numbersRowTop_en};
        int length = numbersRows.length;
        int i = 0;
        while (i < length) {
            int i2 = length;
            View row = numbersRows[i];
            if (row != null) {
                row.setVisibility(numbersVisibility);
            }
            i++;
            length = i2;
        }
        View[] symbolsRows = {symbolsRow_ru, symbolsRow_en, symbolsRowTop_ru, symbolsRowTop_en};
        int length2 = symbolsRows.length;
        int i3 = 0;
        while (i3 < length2) {
            int i4 = length2;
            View row2 = symbolsRows[i3];
            if (row2 != null) {
                row2.setVisibility(symbolsVisibility);
            }
            i3++;
            length2 = i4;
        }
        Button[] toggleButtons2 = {btnToggle_ru, btnToggle_ru2, btnToggle_en, btnToggle_en2, btnToggleTop_ru, btnToggleTop_ru2, btnToggleTop_en, btnToggleTop_en2};
        int length3 = toggleButtons2.length;
        int i5 = 0;
        while (i5 < length3) {
            int symbolsVisibility2 = symbolsVisibility;
            Button btn = toggleButtons2[i5];
            if (btn != null) {
                toggleButtons = toggleButtons2;
                toggleText = toggleText2;
                btn.setText(toggleText);
            } else {
                toggleButtons = toggleButtons2;
                toggleText = toggleText2;
            }
            i5++;
            toggleText2 = toggleText;
            symbolsVisibility = symbolsVisibility2;
            toggleButtons2 = toggleButtons;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void sendCommandToChat(String command) {
        boolean autoSend = getSharedPreferences("GTAndroidData", 0).getBoolean("send_automatically", true);
        if (command.trim().startsWith("/ans")) incrementReportStat();
        if (MyAccessibilityService.isAvailable()) {
            MyAccessibilityService.insertText(command);
            if (autoSend) {
                sendEnterKey();
                Toast.makeText(this, "✅ Отправлено: " + command, Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "✍️ Текст вставлен. Нажмите отправку в чате.", Toast.LENGTH_SHORT).show();
            }
        } else {
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                ic.commitText(command, 1);
                if (autoSend) {
                    sendEnterKey(ic);
                    Toast.makeText(this, "✅ Отправлено: " + command, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "✍️ Текст вставлен. Нажмите отправку в чате.", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private void sendEnterKey() {
        new Handler().postDelayed(new Runnable() { // from class: gtandroid.universal.KeyboardService.43
            @Override // java.lang.Runnable
            public void run() {
                InputConnection ic = KeyboardService.this.getCurrentInputConnection();
                if (ic != null) {
                    try {
                        long eventTime = System.currentTimeMillis();
                        for (int i = 0; i < 3; i++) {
                            ic.sendKeyEvent(new KeyEvent(eventTime + (i * 50), (i * 50) + eventTime, 0, 66, 0));
                            ic.sendKeyEvent(new KeyEvent((i * 50) + eventTime + 25, (i * 50) + eventTime + 25, 1, 66, 0));
                        }
                    } catch (Exception e) {
                        ic.commitText("\n", 1);
                    }
                }
            }
        }, 100L);
    }

    private void sendEnterKey(InputConnection ic) {
        try {
            long eventTime = System.currentTimeMillis();
            for (int i = 0; i < 3; i++) {
                ic.sendKeyEvent(new KeyEvent(eventTime + (i * 50), (i * 50) + eventTime, 0, 66, 0));
                ic.sendKeyEvent(new KeyEvent((i * 50) + eventTime + 25, (i * 50) + eventTime + 25, 1, 66, 0));
            }
        } catch (Exception e) {
            ic.commitText("\n", 1);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showReportAnswerIdInput() {
        String[] numbers;
        if (this.menuLayout == null) {
            return;
        }
        this.menuLayout.removeAllViews();
        int i = 0;
        this.menuLayout.setVisibility(0);
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        linearLayout.setPadding(10, 10, 10, 10);
        linearLayout.setBackground(GlassUI.glassSelector(getApplicationContext(), -15066598));
        TextView title = new TextView(this);
        title.setText("ОТВЕТ НА РЕПОРТ");
        int i2 = -1;
        title.setTextColor(-1);
        title.setTextSize(getFontMultiplier() * 16.0f);
        title.setGravity(17);
        title.setPadding(0, 10, 0, 15);
        linearLayout.addView(title);
        TextView hint = new TextView(this);
        hint.setText("Введите ID игрока:");
        hint.setTextColor(-1);
        hint.setTextSize(getFontMultiplier() * 14.0f);
        hint.setGravity(17);
        linearLayout.addView(hint);
        final EditText idInput = new EditText(this);
        idInput.setBackground(GlassUI.lightCard(this, 10.0f));
        idInput.setPadding(24, 18, 24, 18);
        idInput.setTextColor(-14935010);
        idInput.setHintTextColor(-7434605);
        int i3 = -2;
        idInput.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        idInput.setBackground(GlassUI.glassSelector(getApplicationContext(), -13421773));
        idInput.setTextColor(-1);
        idInput.setHint("ID игрока");
        idInput.setInputType(2);
        idInput.setPadding(10, 10, 10, 10);
        idInput.setTextSize(getFontMultiplier() * 14.0f);
        linearLayout.addView(idInput);
        String[] numbers2 = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "C", "0", "⌫"};
        int buttonHeight = getButtonHeight(50);
        float buttonTextSize = getFontMultiplier() * 14.0f;
        int row = 0;
        while (true) {
            float f = 1.0f;
            if (row < 4) {
                LinearLayout rowLayout = new LinearLayout(this);
                rowLayout.setOrientation(i);
                rowLayout.setLayoutParams(new LinearLayout.LayoutParams(i2, i3));
                int col = 0;
                while (true) {
                    if (col >= 3) {
                        numbers = numbers2;
                        break;
                    }
                    int index = (row * 3) + col;
                    if (index >= numbers2.length) {
                        numbers = numbers2;
                        break;
                    }
                    final String num = numbers2[index];
                    Button btn = new Button(this);
                    btn.setText(num);
                    String[] numbers3 = numbers2;
                    btn.setLayoutParams(new LinearLayout.LayoutParams(i, buttonHeight, f));
                    btn.setTextSize(buttonTextSize);
                    if (num.equals("C") || num.equals("⌫")) {
                        btn.setBackground(GlassUI.glassSelector(getApplicationContext(), -3381760));
                    } else {
                        btn.setBackground(GlassUI.glassSelector(getApplicationContext(), -12303292));
                    }
                    btn.setTextColor(-1);
                    btn.setPadding(5, 5, 5, 5);
                    btn.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.44
                        @Override // android.view.View.OnClickListener
                        public void onClick(View v) {
                            if (num.equals("C")) {
                                idInput.setText("");
                                return;
                            }
                            if (num.equals("⌫")) {
                                String current = idInput.getText().toString();
                                if (current.length() > 0) {
                                    idInput.setText(current.substring(0, current.length() - 1));
                                    return;
                                }
                                return;
                            }
                            idInput.append(num);
                        }
                    });
                    rowLayout.addView(btn);
                    if (col < 2) {
                        View separator = new View(this);
                        separator.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(3.0f), buttonHeight));
                        rowLayout.addView(separator);
                    }
                    col++;
                    numbers2 = numbers3;
                    i = 0;
                    f = 1.0f;
                }
                linearLayout.addView(rowLayout);
                if (row < 3) {
                    View separator2 = new View(this);
                    separator2.setLayoutParams(new LinearLayout.LayoutParams(-1, 3));
                    separator2.setBackground(GlassUI.glassSelector(getApplicationContext(), -13421773));
                    linearLayout.addView(separator2);
                }
                row++;
                numbers2 = numbers;
                i = 0;
                i2 = -1;
                i3 = -2;
            } else {
                LinearLayout buttonRow = new LinearLayout(this);
                buttonRow.setOrientation(0);
                buttonRow.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
                Button btnBack = new Button(this);
                btnBack.setText("← Назад");
                btnBack.setLayoutParams(new LinearLayout.LayoutParams(0, buttonHeight, 1.0f));
                btnBack.setBackground(GlassUI.glassSelector(getApplicationContext(), -10066330));
                btnBack.setTextColor(-1);
                btnBack.setTextSize(buttonTextSize);
                btnBack.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.45
                    @Override // android.view.View.OnClickListener
                    public void onClick(View v) {
                        KeyboardService.this.showAdminPanel();
                    }
                });
                Button btnContinue = new Button(this);
                btnContinue.setText("Продолжить →");
                btnContinue.setLayoutParams(new LinearLayout.LayoutParams(0, buttonHeight, 1.0f));
                btnContinue.setBackground(GlassUI.glassSelector(getApplicationContext(), -16733696));
                btnContinue.setTextColor(-1);
                btnContinue.setTextSize(buttonTextSize);
                btnContinue.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.46
                    @Override // android.view.View.OnClickListener
                    public void onClick(View v) {
                        String id = idInput.getText().toString();
                        if (!id.isEmpty()) {
                            KeyboardService.this.selectedReportId = id;
                            KeyboardService.this.showFollowTypeSelection(id);
                        } else {
                            Toast.makeText(KeyboardService.this, "❌ Введите ID", 0).show();
                        }
                    }
                });
                buttonRow.addView(btnBack);
                View separator3 = new View(this);
                separator3.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(5.0f), buttonHeight));
                buttonRow.addView(separator3);
                buttonRow.addView(btnContinue);
                linearLayout.addView(buttonRow);
                this.menuLayout.addView(linearLayout);
                updateMenuButtonsHeight(this.menuLayout, buttonHeight);
                applyKeyboardTransparency(this.keyboardTransparency);
                return;
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showFollowTypeSelection(final String playerId) {
        if (this.menuLayout == null) {
            return;
        }
        this.menuLayout.removeAllViews();
        this.menuLayout.setVisibility(0);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(1);
        container.setPadding(10, 10, 10, 10);
        container.setBackground(GlassUI.glassSelector(getApplicationContext(), -15066598));
        TextView title = new TextView(this);
        title.setText("ЗА КЕМ СЛЕДИТЬ?");
        title.setTextColor(-1);
        title.setTextSize(getFontMultiplier() * 16.0f);
        title.setGravity(17);
        title.setPadding(0, 10, 0, 15);
        container.addView(title);
        int buttonHeight = getButtonHeight(70);
        float buttonTextSize = getFontMultiplier() * 16.0f;
        Button btnPlayer = new Button(this);
        btnPlayer.setText("👤 Следить за игроком");
        btnPlayer.setLayoutParams(new LinearLayout.LayoutParams(-1, buttonHeight));
        btnPlayer.setBackground(GlassUI.glassSelector(getApplicationContext(), -16750900));
        btnPlayer.setTextColor(-1);
        btnPlayer.setTextSize(buttonTextSize);
        btnPlayer.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.47
            @Override // android.view.View.OnClickListener
            public void onClick(View v) {
                KeyboardService.this.sendCommandToChat("/ans" + playerId + " Приветствую, попытаюсь сейчас вам помочь.");
                KeyboardService.this.showFollowButtonScreen(playerId, playerId, false);
            }
        });
        container.addView(btnPlayer);
        View sep1 = new View(this);
        sep1.setLayoutParams(new LinearLayout.LayoutParams(-1, 3));
        sep1.setBackground(GlassUI.glassSelector(getApplicationContext(), -13421773));
        container.addView(sep1);
        Button btnViolator = new Button(this);
        btnViolator.setText("⚠️ Следить за нарушителем");
        btnViolator.setLayoutParams(new LinearLayout.LayoutParams(-1, buttonHeight));
        btnViolator.setBackground(GlassUI.glassSelector(getApplicationContext(), -3381760));
        btnViolator.setTextColor(-1);
        btnViolator.setTextSize(buttonTextSize);
        btnViolator.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.48
            @Override // android.view.View.OnClickListener
            public void onClick(View v) {
                KeyboardService.this.showFollowViolatorIdInput(playerId);
            }
        });
        container.addView(btnViolator);
        Button btnBack = new Button(this);
        btnBack.setText("← Назад к клавиатуре");
        btnBack.setLayoutParams(new LinearLayout.LayoutParams(-1, getButtonHeight(50)));
        btnBack.setBackground(GlassUI.glassSelector(getApplicationContext(), -10066330));
        btnBack.setTextColor(-1);
        btnBack.setTextSize(getFontMultiplier() * 14.0f);
        btnBack.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.49
            @Override // android.view.View.OnClickListener
            public void onClick(View v) {
                KeyboardService.this.showKeyboardView();
            }
        });
        container.addView(btnBack);
        this.menuLayout.addView(container);
        updateMenuButtonsHeight(this.menuLayout, getButtonHeight(50));
        applyKeyboardTransparency(this.keyboardTransparency);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showFollowButtonScreen(final String playerId, final String targetId, final boolean isViolator) {
        if (this.menuLayout == null) {
            return;
        }
        this.menuLayout.removeAllViews();
        this.menuLayout.setVisibility(0);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(1);
        container.setPadding(10, 10, 10, 10);
        container.setBackground(GlassUI.glassSelector(getApplicationContext(), -15066598));
        TextView title = new TextView(this);
        if (isViolator) {
            title.setText("НАРУШИТЕЛЬ: " + targetId + "");
        } else {
            title.setText("ИГРОК: " + targetId + "");
        }
        title.setTextColor(-1);
        title.setTextSize(getFontMultiplier() * 16.0f);
        title.setGravity(17);
        title.setPadding(0, 10, 0, 15);
        container.addView(title);
        Button btnFollow = new Button(this);
        btnFollow.setText("👁️ Следить");
        btnFollow.setLayoutParams(new LinearLayout.LayoutParams(-1, getButtonHeight(80)));
        btnFollow.setBackground(GlassUI.glassSelector(getApplicationContext(), -16733696));
        btnFollow.setTextColor(-1);
        btnFollow.setTextSize(getFontMultiplier() * 20.0f);
        btnFollow.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.50
            @Override // android.view.View.OnClickListener
            public void onClick(View v) {
                KeyboardService.this.sendCommandToChat("/sp" + targetId);
                if (isViolator) {
                    KeyboardService.this.showFollowViolatorActions(playerId, targetId);
                } else {
                    KeyboardService.this.showFollowPlayerActions(targetId);
                }
            }
        });
        container.addView(btnFollow);
        Button btnBack = new Button(this);
        btnBack.setText("← Назад");
        btnBack.setLayoutParams(new LinearLayout.LayoutParams(-1, getButtonHeight(50)));
        btnBack.setBackground(GlassUI.glassSelector(getApplicationContext(), -10066330));
        btnBack.setTextColor(-1);
        btnBack.setTextSize(getFontMultiplier() * 14.0f);
        btnBack.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.51
            @Override // android.view.View.OnClickListener
            public void onClick(View v) {
                KeyboardService.this.showFollowTypeSelection(playerId);
            }
        });
        container.addView(btnBack);
        this.menuLayout.addView(container);
        updateMenuButtonsHeight(this.menuLayout, getButtonHeight(50));
        applyKeyboardTransparency(this.keyboardTransparency);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showFollowPlayerActions(final String playerId) {
        ScrollView scrollView;
        TextView title;
        if (this.menuLayout == null) {
            return;
        }
        this.menuLayout.removeAllViews();
        int i = 0;
        this.menuLayout.setVisibility(0);
        ScrollView scrollView2 = new ScrollView(this);
        int i2 = -1;
        scrollView2.setLayoutParams(new LinearLayout.LayoutParams(-1, -1));
        scrollView2.setFillViewport(true);
        final LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        linearLayout.setPadding(2, 2, 2, 2);
        linearLayout.setBackground(GlassUI.glassSelector(getApplicationContext(), -15066598));
        TextView title2 = new TextView(this);
        title2.setText("ИГРОК: " + playerId + "");
        title2.setTextColor(-1);
        title2.setTextSize(getFontMultiplier() * 11.0f);
        title2.setGravity(17);
        title2.setPadding(2, 5, 2, 5);
        linearLayout.addView(title2);
        String[] buttons = {"Тп к игроку", "Выйти из слежки", "Наказать", "Чинить", "Перевернуть", "Подкинуть", "Спавн", "Статистика", "Заправить", "Лицензии", "Наказания", "Заморозить", "Тп икс", "Тп ткс", "Оружия игрока", "Офф Наручники", "Забрать оружие", "Отправить в /a"};
        int buttonHeight = getButtonHeight(50);
        float buttonTextSize = getFontMultiplier() * 7.0f;
        int row = 0;
        while (row < 4) {
            LinearLayout rowLayout = new LinearLayout(this);
            rowLayout.setOrientation(i);
            rowLayout.setLayoutParams(new LinearLayout.LayoutParams(i2, -2));
            rowLayout.setWeightSum(5);
            int col = 0;
            while (true) {
                if (col >= 5) {
                    scrollView = scrollView2;
                    title = title2;
                    break;
                }
                final int btnIndex = (row * 5) + col;
                if (btnIndex >= buttons.length) {
                    scrollView = scrollView2;
                    title = title2;
                    break;
                }
                Button btn = new Button(this);
                btn.setText(buttons[btnIndex]);
                TextView title3 = title2;
                ScrollView scrollView3 = scrollView2;
                btn.setLayoutParams(new LinearLayout.LayoutParams(0, buttonHeight, 1.0f));
                btn.setBackground(GlassUI.glassSelector(getApplicationContext(), -12303292));
                btn.setTextColor(-1);
                btn.setPadding(2, 2, 2, 2);
                btn.setTextSize(buttonTextSize);
                btn.setMaxLines(2);
                btn.setGravity(17);
                btn.setMinHeight(buttonHeight);
                btn.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.52
                    @Override // android.view.View.OnClickListener
                    public void onClick(View v) {
                        KeyboardService.this.handleFollowPlayerAction(btnIndex, playerId, linearLayout);
                    }
                });
                rowLayout.addView(btn);
                if (col < 5 - 1 && btnIndex < buttons.length - 1) {
                    View sep = new View(this);
                    sep.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(2.0f), buttonHeight));
                    sep.setBackground(GlassUI.glassSelector(getApplicationContext(), -14540254));
                    rowLayout.addView(sep);
                }
                col++;
                title2 = title3;
                scrollView2 = scrollView3;
            }
            linearLayout.addView(rowLayout);
            if (row < 3) {
                View sep2 = new View(this);
                sep2.setLayoutParams(new LinearLayout.LayoutParams(-1, 2));
                sep2.setBackground(GlassUI.glassSelector(getApplicationContext(), -13421773));
                linearLayout.addView(sep2);
            }
            row++;
            title2 = title;
            scrollView2 = scrollView;
            i = 0;
            i2 = -1;
        }
        ScrollView scrollView4 = scrollView2;
        Button btnBack = new Button(this);
        btnBack.setText("← Назад к клавиатуре");
        btnBack.setLayoutParams(new LinearLayout.LayoutParams(-1, getButtonHeight(55)));
        btnBack.setBackground(GlassUI.glassSelector(getApplicationContext(), -10066330));
        btnBack.setTextColor(-1);
        btnBack.setTextSize(getFontMultiplier() * 14.0f);
        btnBack.setPadding(5, 10, 5, 10);
        btnBack.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.53
            @Override // android.view.View.OnClickListener
            public void onClick(View v) {
                KeyboardService.this.showKeyboardView();
            }
        });
        linearLayout.addView(btnBack);
        scrollView4.addView(linearLayout);
        this.menuLayout.addView(scrollView4);
        updateMenuButtonsHeight(this.menuLayout, buttonHeight);
        applyKeyboardTransparency(this.keyboardTransparency);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void handleFollowPlayerAction(int index, String playerId, View container) {
        this.isViolatorMode = false;
        this.currentPlayerId = playerId;
        this.currentTargetId = playerId;
        switch (index) {
            case 0:
                sendCommandToChat("/spoff");
                showTpToPlayer(playerId);
                break;
            case 1:
                sendCommandToChat("/spoff");
                showKeyboardView();
                break;
            case 2:
                this.selectedPunishmentId = playerId;
                showPunishmentTypes();
                break;
            case 3:
                sendCommandToChat("/fixcar" + playerId);
                break;
            case 4:
                sendCommandToChat("/flip" + playerId);
                break;
            case 5:
                sendCommandToChat("/slap" + playerId);
                break;
            case 6:
                sendCommandToChat("/spawn" + playerId);
                break;
            case 7:
                sendCommandToChat("/stats" + playerId);
                break;
            case 8:
                sendCommandToChat("/setfuel" + playerId + " 150");
                break;
            case 9:
                sendCommandToChat("/alic" + playerId);
                break;
            case 10:
                sendCommandToChat("/getinfo" + playerId);
                break;
            case 11:
                sendCommandToChat("/freeze" + playerId);
                break;
            case 12:
                sendCommandToChat("/gethere" + playerId);
                break;
            case 13:
                showCarIdInputForTarget("/getcar", playerId, false);
                break;
            case 14:
                sendCommandToChat("/weap" + playerId);
                break;
            case 15:
                sendCommandToChat("/uncuff" + playerId);
                break;
            case 16:
                sendCommandToChat("/rgun" + playerId);
                break;
            case 17:
                sendCommandToChat("/a" + playerId + " Нуждается в помощи");
                break;
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showFollowViolatorIdInput(final String playerId) {
        String[] numbers;
        if (this.menuLayout == null) {
            return;
        }
        this.menuLayout.removeAllViews();
        int i = 0;
        this.menuLayout.setVisibility(0);
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        linearLayout.setPadding(10, 10, 10, 10);
        linearLayout.setBackground(GlassUI.glassSelector(getApplicationContext(), -15066598));
        TextView title = new TextView(this);
        title.setText("ID НАРУШИТЕЛЯ");
        int i2 = -1;
        title.setTextColor(-1);
        title.setTextSize(getFontMultiplier() * 16.0f);
        title.setGravity(17);
        linearLayout.addView(title);
        final EditText violatorIdInput = new EditText(this);
        violatorIdInput.setBackground(GlassUI.lightCard(this, 10.0f));
        violatorIdInput.setPadding(24, 18, 24, 18);
        violatorIdInput.setTextColor(-14935010);
        violatorIdInput.setHintTextColor(-7434605);
        int i3 = -2;
        violatorIdInput.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        violatorIdInput.setBackground(GlassUI.glassSelector(getApplicationContext(), -13421773));
        violatorIdInput.setTextColor(-1);
        violatorIdInput.setHint("ID нарушителя");
        violatorIdInput.setInputType(2);
        violatorIdInput.setPadding(10, 10, 10, 10);
        violatorIdInput.setTextSize(getFontMultiplier() * 14.0f);
        linearLayout.addView(violatorIdInput);
        String[] numbers2 = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "C", "0", "⌫"};
        int buttonHeight = getButtonHeight(50);
        float buttonTextSize = getFontMultiplier() * 14.0f;
        int row = 0;
        while (true) {
            float f = 1.0f;
            if (row < 4) {
                LinearLayout rowLayout = new LinearLayout(this);
                rowLayout.setOrientation(i);
                rowLayout.setLayoutParams(new LinearLayout.LayoutParams(i2, i3));
                int col = 0;
                while (true) {
                    if (col >= 3) {
                        numbers = numbers2;
                        break;
                    }
                    int index = (row * 3) + col;
                    if (index >= numbers2.length) {
                        numbers = numbers2;
                        break;
                    }
                    final String num = numbers2[index];
                    Button btn = new Button(this);
                    btn.setText(num);
                    String[] numbers3 = numbers2;
                    btn.setLayoutParams(new LinearLayout.LayoutParams(i, buttonHeight, f));
                    btn.setTextSize(buttonTextSize);
                    if (num.equals("C") || num.equals("⌫")) {
                        btn.setBackground(GlassUI.glassSelector(getApplicationContext(), -3381760));
                    } else {
                        btn.setBackground(GlassUI.glassSelector(getApplicationContext(), -12303292));
                    }
                    btn.setTextColor(-1);
                    btn.setPadding(5, 5, 5, 5);
                    btn.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.54
                        @Override // android.view.View.OnClickListener
                        public void onClick(View v) {
                            if (num.equals("C")) {
                                violatorIdInput.setText("");
                                return;
                            }
                            if (num.equals("⌫")) {
                                String current = violatorIdInput.getText().toString();
                                if (current.length() > 0) {
                                    violatorIdInput.setText(current.substring(0, current.length() - 1));
                                    return;
                                }
                                return;
                            }
                            violatorIdInput.append(num);
                        }
                    });
                    rowLayout.addView(btn);
                    if (col < 2) {
                        View separator = new View(this);
                        separator.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(3.0f), buttonHeight));
                        rowLayout.addView(separator);
                    }
                    col++;
                    numbers2 = numbers3;
                    i = 0;
                    f = 1.0f;
                }
                linearLayout.addView(rowLayout);
                if (row < 3) {
                    View separator2 = new View(this);
                    separator2.setLayoutParams(new LinearLayout.LayoutParams(-1, 3));
                    separator2.setBackground(GlassUI.glassSelector(getApplicationContext(), -13421773));
                    linearLayout.addView(separator2);
                }
                row++;
                numbers2 = numbers;
                i = 0;
                i2 = -1;
                i3 = -2;
            } else {
                LinearLayout buttonRow = new LinearLayout(this);
                buttonRow.setOrientation(0);
                buttonRow.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
                Button btnBack = new Button(this);
                btnBack.setText("← Назад");
                btnBack.setLayoutParams(new LinearLayout.LayoutParams(0, buttonHeight, 1.0f));
                btnBack.setBackground(GlassUI.glassSelector(getApplicationContext(), -10066330));
                btnBack.setTextColor(-1);
                btnBack.setTextSize(buttonTextSize);
                btnBack.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.55
                    @Override // android.view.View.OnClickListener
                    public void onClick(View v) {
                        KeyboardService.this.showFollowTypeSelection(playerId);
                    }
                });
                Button btnContinue = new Button(this);
                btnContinue.setText("Отправить");
                btnContinue.setLayoutParams(new LinearLayout.LayoutParams(0, buttonHeight, 1.0f));
                btnContinue.setBackground(GlassUI.glassSelector(getApplicationContext(), -16733696));
                btnContinue.setTextColor(-1);
                btnContinue.setTextSize(buttonTextSize);
                btnContinue.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.56
                    @Override // android.view.View.OnClickListener
                    public void onClick(View v) {
                        String violatorId = violatorIdInput.getText().toString();
                        if (!violatorId.isEmpty()) {
                            KeyboardService.this.sendCommandToChat("/ans" + playerId + " Приветствую, слежу за игроком с id [" + violatorId + "]");
                            KeyboardService.this.showFollowButtonScreen(playerId, violatorId, true);
                        } else {
                            Toast.makeText(KeyboardService.this, "❌ Введите ID", 0).show();
                        }
                    }
                });
                buttonRow.addView(btnBack);
                View separator3 = new View(this);
                separator3.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(5.0f), buttonHeight));
                buttonRow.addView(separator3);
                buttonRow.addView(btnContinue);
                linearLayout.addView(buttonRow);
                this.menuLayout.addView(linearLayout);
                updateMenuButtonsHeight(this.menuLayout, buttonHeight);
                applyKeyboardTransparency(this.keyboardTransparency);
                return;
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showFollowViolatorActions(final String playerId, final String violatorId) {
        int row;
        int cols;
        LinearLayout rowLayout;
        float buttonTextSize;
        String[] buttons;
        TextView title;
        int buttonHeight;
        if (this.menuLayout == null) {
            return;
        }
        this.menuLayout.removeAllViews();
        int row2 = 0;
        this.menuLayout.setVisibility(0);
        ScrollView scrollView = new ScrollView(this);
        int i = -1;
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(-1, -1));
        scrollView.setFillViewport(true);
        scrollView.setBackground(GlassUI.glassSelector(getApplicationContext(), -15066598));
        final LinearLayout mainContainer = new LinearLayout(this);
        mainContainer.setOrientation(1);
        mainContainer.setPadding(3, 3, 3, 3);
        mainContainer.setBackground(GlassUI.glassSelector(getApplicationContext(), -15066598));
        TextView title2 = new TextView(this);
        title2.setText("НАРУШИТЕЛЬ: " + violatorId + "");
        title2.setTextColor(-1);
        title2.setTextSize(getFontMultiplier() * 11.0f);
        title2.setGravity(17);
        title2.setPadding(3, 5, 3, 5);
        mainContainer.addView(title2);
        String[] buttons2 = {"Наказан", "Не нарушает", "Тп к игроку", "Выйти из слежки", "Наказать", "Чинить", "Перевернуть", "Подкинуть", "Спавн", "Статистика", "Заправить", "Лицензии", "Наказания", "Заморозить", "Тп икс", "Тп ткс", "Оружия игрока", "Офф Наручники", "Забрать оружие", "Отправить в /a"};
        int buttonHeight2 = getButtonHeight(50);
        float buttonTextSize2 = getFontMultiplier() * 7.0f;
        int cols2 = 5;
        int row3 = 0;
        for (int i2 = 5; row3 < i2; i2 = 5) {
            LinearLayout rowLayout2 = new LinearLayout(this);
            rowLayout2.setOrientation(row2);
            rowLayout2.setLayoutParams(new LinearLayout.LayoutParams(i, -2));
            rowLayout2.setWeightSum(cols2);
            int col = 0;
            while (true) {
                if (col >= cols2) {
                    row = row3;
                    cols = cols2;
                    rowLayout = rowLayout2;
                    buttonTextSize = buttonTextSize2;
                    buttons = buttons2;
                    title = title2;
                    buttonHeight = buttonHeight2;
                    break;
                }
                final int btnIndex = (row3 * cols2) + col;
                if (btnIndex >= buttons2.length) {
                    row = row3;
                    cols = cols2;
                    rowLayout = rowLayout2;
                    buttonTextSize = buttonTextSize2;
                    buttons = buttons2;
                    title = title2;
                    buttonHeight = buttonHeight2;
                    break;
                }
                LinearLayout rowLayout3 = rowLayout2;
                Button btn = new Button(this);
                btn.setText(buttons2[btnIndex]);
                int row4 = row3;
                TextView title3 = title2;
                btn.setLayoutParams(new LinearLayout.LayoutParams(0, buttonHeight2, 1.0f));
                btn.setBackground(GlassUI.glassSelector(getApplicationContext(), -12303292));
                btn.setTextColor(-1);
                btn.setPadding(2, 2, 2, 2);
                btn.setTextSize(buttonTextSize2);
                btn.setMaxLines(2);
                btn.setGravity(17);
                btn.setMinHeight(buttonHeight2);
                int cols3 = cols2;
                float buttonTextSize3 = buttonTextSize2;
                int buttonHeight3 = buttonHeight2;
                String[] buttons3 = buttons2;
                btn.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.57
                    @Override // android.view.View.OnClickListener
                    public void onClick(View v) {
                        KeyboardService.this.handleFollowViolatorAction(btnIndex, playerId, violatorId, mainContainer);
                    }
                });
                rowLayout3.addView(btn);
                if (col < cols3 - 1 && btnIndex < buttons3.length - 1) {
                    View sep = new View(this);
                    sep.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(2.0f), buttonHeight3));
                    sep.setBackground(GlassUI.glassSelector(getApplicationContext(), -14540254));
                    rowLayout3.addView(sep);
                }
                col++;
                row3 = row4;
                buttonHeight2 = buttonHeight3;
                buttons2 = buttons3;
                rowLayout2 = rowLayout3;
                cols2 = cols3;
                buttonTextSize2 = buttonTextSize3;
                title2 = title3;
            }
            mainContainer.addView(rowLayout);
            if (row < 4) {
                View sep2 = new View(this);
                sep2.setLayoutParams(new LinearLayout.LayoutParams(-1, 2));
                sep2.setBackground(GlassUI.glassSelector(getApplicationContext(), -13421773));
                mainContainer.addView(sep2);
            }
            row3 = row + 1;
            buttonHeight2 = buttonHeight;
            buttons2 = buttons;
            cols2 = cols;
            buttonTextSize2 = buttonTextSize;
            title2 = title;
            row2 = 0;
            i = -1;
        }
        Button btnBack = new Button(this);
        btnBack.setText("← Назад к клавиатуре");
        btnBack.setLayoutParams(new LinearLayout.LayoutParams(-1, getButtonHeight(55)));
        btnBack.setBackground(GlassUI.glassSelector(getApplicationContext(), -10066330));
        btnBack.setTextColor(-1);
        btnBack.setTextSize(getFontMultiplier() * 14.0f);
        btnBack.setPadding(5, 10, 5, 10);
        btnBack.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.58
            @Override // android.view.View.OnClickListener
            public void onClick(View v) {
                KeyboardService.this.showKeyboardView();
            }
        });
        mainContainer.addView(btnBack);
        scrollView.addView(mainContainer);
        this.menuLayout.addView(scrollView);
        updateMenuButtonsHeight(this.menuLayout, buttonHeight2);
        applyKeyboardTransparency(this.keyboardTransparency);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void handleFollowViolatorAction(int index, String playerId, String violatorId, View container) {
        this.isViolatorMode = true;
        this.currentPlayerId = playerId;
        this.currentTargetId = violatorId;
        switch (index) {
            case 0:
                sendCommandToChat("/ans" + playerId + " Игрок с id [" + violatorId + "] наказан, приятной игры.");
                break;
            case 1:
                sendCommandToChat("/ans" + playerId + " Игрок с id [" + violatorId + "] не нарушает, приятной игры.");
                break;
            case 2:
                sendCommandToChat("/spoff");
                showTpToPlayer(violatorId);
                break;
            case 3:
                sendCommandToChat("/spoff");
                showKeyboardView();
                break;
            case 4:
                this.selectedPunishmentId = violatorId;
                showPunishmentTypes();
                break;
            case 5:
                sendCommandToChat("/fixcar" + violatorId);
                break;
            case 6:
                sendCommandToChat("/flip" + violatorId);
                break;
            case 7:
                sendCommandToChat("/slap" + violatorId);
                break;
            case 8:
                sendCommandToChat("/spawn" + violatorId);
                break;
            case 9:
                sendCommandToChat("/stats" + violatorId);
                break;
            case 10:
                sendCommandToChat("/setfuel" + violatorId + " 150");
                break;
            case 11:
                sendCommandToChat("/alic" + violatorId);
                break;
            case 12:
                sendCommandToChat("/getinfo" + violatorId);
                break;
            case 13:
                sendCommandToChat("/freeze" + violatorId);
                break;
            case 14:
                sendCommandToChat("/gethere" + violatorId);
                break;
            case 15:
                showCarIdInputForTarget("/getcar", violatorId, true);
                break;
            case 16:
                sendCommandToChat("/weap" + violatorId);
                break;
            case 17:
                sendCommandToChat("/uncuff" + violatorId);
                break;
            case 18:
                sendCommandToChat("/rgun" + violatorId);
                break;
            case 19:
                sendCommandToChat("/a" + violatorId + " Нуждается в помощи");
                break;
        }
    }

    private void showTpToPlayer(final String playerId) {
        if (this.menuLayout == null) {
            return;
        }
        this.menuLayout.removeAllViews();
        this.menuLayout.setVisibility(0);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(1);
        container.setPadding(10, 10, 10, 10);
        container.setBackground(GlassUI.glassSelector(getApplicationContext(), -15066598));
        TextView title = new TextView(this);
        title.setText("ТП К ИГРОКУ");
        title.setTextColor(-1);
        title.setTextSize(getFontMultiplier() * 16.0f);
        title.setGravity(17);
        container.addView(title);
        Button btnTp = new Button(this);
        btnTp.setText("ТП к игроку");
        btnTp.setLayoutParams(new LinearLayout.LayoutParams(-1, getButtonHeight(60)));
        btnTp.setBackground(GlassUI.glassSelector(getApplicationContext(), -16733696));
        btnTp.setTextColor(-1);
        btnTp.setTextSize(getFontMultiplier() * 14.0f);
        btnTp.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.59
            @Override // android.view.View.OnClickListener
            public void onClick(View v) {
                KeyboardService.this.sendCommandToChat("/goto" + playerId);
                KeyboardService.this.showKeyboardView();
            }
        });
        container.addView(btnTp);
        Button btnBack = new Button(this);
        btnBack.setText("← Назад");
        btnBack.setLayoutParams(new LinearLayout.LayoutParams(-1, getButtonHeight(50)));
        btnBack.setBackground(GlassUI.glassSelector(getApplicationContext(), -10066330));
        btnBack.setTextColor(-1);
        btnBack.setTextSize(getFontMultiplier() * 14.0f);
        btnBack.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.60
            @Override // android.view.View.OnClickListener
            public void onClick(View v) {
                KeyboardService.this.showFollowPlayerActions(playerId);
            }
        });
        container.addView(btnBack);
        this.menuLayout.addView(container);
        updateMenuButtonsHeight(this.menuLayout, getButtonHeight(50));
        applyKeyboardTransparency(this.keyboardTransparency);
    }

    private void showCarIdInputForTarget(final String command, final String targetId, final boolean isViolator) {
        int index;
        if (this.menuLayout == null) {
            return;
        }
        this.menuLayout.removeAllViews();
        int i = 0;
        this.menuLayout.setVisibility(0);
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        linearLayout.setPadding(10, 10, 10, 10);
        linearLayout.setBackground(GlassUI.glassSelector(getApplicationContext(), -15066598));
        TextView title = new TextView(this);
        title.setText("ВВЕДИТЕ ID ТС");
        int i2 = -1;
        title.setTextColor(-1);
        title.setTextSize(getFontMultiplier() * 16.0f);
        title.setGravity(17);
        title.setPadding(0, 10, 0, 15);
        linearLayout.addView(title);
        final EditText carIdInput = new EditText(this);
        carIdInput.setBackground(GlassUI.lightCard(this, 10.0f));
        carIdInput.setPadding(24, 18, 24, 18);
        carIdInput.setTextColor(-14935010);
        carIdInput.setHintTextColor(-7434605);
        int i3 = -2;
        carIdInput.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        carIdInput.setBackground(GlassUI.glassSelector(getApplicationContext(), -13421773));
        carIdInput.setTextColor(-1);
        carIdInput.setHint("ID транспорта");
        carIdInput.setInputType(2);
        carIdInput.setPadding(10, 10, 10, 10);
        carIdInput.setTextSize(getFontMultiplier() * 14.0f);
        linearLayout.addView(carIdInput);
        String[] numbers = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "C", "0", "⌫"};
        int buttonHeight = getButtonHeight(55);
        float buttonTextSize = getFontMultiplier() * 16.0f;
        int row = 0;
        while (true) {
            float f = 1.0f;
            if (row < 4) {
                LinearLayout rowLayout = new LinearLayout(this);
                rowLayout.setOrientation(i);
                rowLayout.setLayoutParams(new LinearLayout.LayoutParams(i2, i3));
                int col = 0;
                while (col < 3 && (index = (row * 3) + col) < numbers.length) {
                    final String num = numbers[index];
                    Button btn = new Button(this);
                    btn.setText(num);
                    btn.setLayoutParams(new LinearLayout.LayoutParams(i, buttonHeight, f));
                    btn.setTextSize(buttonTextSize);
                    if (num.equals("C") || num.equals("⌫")) {
                        btn.setBackground(GlassUI.glassSelector(getApplicationContext(), -3381760));
                    } else {
                        btn.setBackground(GlassUI.glassSelector(getApplicationContext(), -12303292));
                    }
                    btn.setTextColor(-1);
                    btn.setPadding(5, 5, 5, 5);
                    btn.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.61
                        @Override // android.view.View.OnClickListener
                        public void onClick(View v) {
                            if (num.equals("C")) {
                                carIdInput.setText("");
                                return;
                            }
                            if (num.equals("⌫")) {
                                String current = carIdInput.getText().toString();
                                if (current.length() > 0) {
                                    carIdInput.setText(current.substring(0, current.length() - 1));
                                    return;
                                }
                                return;
                            }
                            carIdInput.append(num);
                        }
                    });
                    rowLayout.addView(btn);
                    if (col < 2) {
                        View separator = new View(this);
                        separator.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(3.0f), buttonHeight));
                        rowLayout.addView(separator);
                    }
                    col++;
                    i = 0;
                    f = 1.0f;
                }
                linearLayout.addView(rowLayout);
                if (row < 3) {
                    View separator2 = new View(this);
                    separator2.setLayoutParams(new LinearLayout.LayoutParams(-1, 3));
                    separator2.setBackground(GlassUI.glassSelector(getApplicationContext(), -13421773));
                    linearLayout.addView(separator2);
                }
                row++;
                i = 0;
                i2 = -1;
                i3 = -2;
            } else {
                LinearLayout buttonRow = new LinearLayout(this);
                buttonRow.setOrientation(0);
                buttonRow.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
                Button btnBack = new Button(this);
                btnBack.setText("← Назад");
                btnBack.setLayoutParams(new LinearLayout.LayoutParams(0, buttonHeight, 1.0f));
                btnBack.setBackground(GlassUI.glassSelector(getApplicationContext(), -10066330));
                btnBack.setTextColor(-1);
                btnBack.setTextSize(buttonTextSize);
                btnBack.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.62
                    @Override // android.view.View.OnClickListener
                    public void onClick(View v) {
                        if (isViolator) {
                            KeyboardService.this.showFollowViolatorActions(KeyboardService.this.currentPlayerId, KeyboardService.this.currentTargetId);
                        } else {
                            KeyboardService.this.showFollowPlayerActions(targetId);
                        }
                    }
                });
                Button btnSend = new Button(this);
                btnSend.setText("Отправить");
                btnSend.setLayoutParams(new LinearLayout.LayoutParams(0, buttonHeight, 1.0f));
                btnSend.setBackground(GlassUI.glassSelector(getApplicationContext(), -16733696));
                btnSend.setTextColor(-1);
                btnSend.setTextSize(buttonTextSize);
                btnSend.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.63
                    @Override // android.view.View.OnClickListener
                    public void onClick(View v) {
                        String carId = carIdInput.getText().toString();
                        if (!carId.isEmpty()) {
                            KeyboardService.this.sendCommandToChat(command + "" + carId);
                            Toast.makeText(KeyboardService.this, "✅ Отправлено: " + command + "" + carId, 0).show();
                            if (isViolator) {
                                KeyboardService.this.showFollowViolatorActions(KeyboardService.this.currentPlayerId, KeyboardService.this.currentTargetId);
                                return;
                            } else {
                                KeyboardService.this.showFollowPlayerActions(targetId);
                                return;
                            }
                        }
                        Toast.makeText(KeyboardService.this, "❌ Введите ID транспорта", 0).show();
                    }
                });
                buttonRow.addView(btnBack);
                View separator3 = new View(this);
                separator3.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(5.0f), buttonHeight));
                buttonRow.addView(separator3);
                buttonRow.addView(btnSend);
                linearLayout.addView(buttonRow);
                this.menuLayout.addView(linearLayout);
                updateMenuButtonsHeight(this.menuLayout, buttonHeight);
                applyKeyboardTransparency(this.keyboardTransparency);
                return;
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showPunishmentTypes() {
        String[] punishmentTypes;
        LinearLayout container;
        TextView title;
        if (this.menuLayout == null) {
            return;
        }
        this.menuLayout.removeAllViews();
        int i = 0;
        this.menuLayout.setVisibility(0);
        String[] punishmentTypes2 = {"Кик", "Бан", "Варн", "Джаил", "Мут", "UnБан", "UnВарн", "UnДжаил", "UnМут", "RМут", "UnRМут"};
        LinearLayout container2 = new LinearLayout(this);
        container2.setOrientation(1);
        container2.setPadding(10, 10, 10, 10);
        container2.setBackground(GlassUI.glassSelector(getApplicationContext(), -15066598));
        TextView title2 = new TextView(this);
        title2.setText("ВЫБЕРИТЕ ТИП");
        int i2 = -1;
        title2.setTextColor(-1);
        title2.setTextSize(getFontMultiplier() * 16.0f);
        title2.setGravity(17);
        title2.setPadding(0, 10, 0, 15);
        container2.addView(title2);
        ScrollView scrollView = new ScrollView(this);
        int i3 = -2;
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        scrollView.setFillViewport(true);
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        linearLayout.setPadding(5, 5, 5, 5);
        int buttonHeight = getButtonHeight(70);
        float buttonTextSize = getFontMultiplier() * 14.0f;
        int totalButtons = punishmentTypes2.length;
        int rows = (int) Math.ceil(totalButtons / 2);
        int row = 0;
        while (row < rows) {
            LinearLayout rowLayout = new LinearLayout(this);
            rowLayout.setOrientation(i);
            rowLayout.setLayoutParams(new LinearLayout.LayoutParams(i2, i3));
            rowLayout.setWeightSum(2);
            int col = 0;
            while (true) {
                if (col >= 2) {
                    punishmentTypes = punishmentTypes2;
                    container = container2;
                    title = title2;
                    break;
                }
                final int index = (row * 2) + col;
                if (index >= totalButtons) {
                    punishmentTypes = punishmentTypes2;
                    container = container2;
                    title = title2;
                    break;
                }
                final String typeName = punishmentTypes2[index];
                Button btn = new Button(this);
                btn.setText(typeName);
                String[] punishmentTypes3 = punishmentTypes2;
                TextView title3 = title2;
                LinearLayout container3 = container2;
                btn.setLayoutParams(new LinearLayout.LayoutParams(0, buttonHeight, 1.0f));
                btn.setBackground(GlassUI.glassSelector(getApplicationContext(), -12303292));
                btn.setTextColor(-1);
                btn.setPadding(10, 15, 10, 15);
                btn.setTextSize(buttonTextSize);
                btn.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.64
                    @Override // android.view.View.OnClickListener
                    public void onClick(View v) {
                        KeyboardService.this.showPunishmentButtonsForType(index, typeName);
                    }
                });
                rowLayout.addView(btn);
                if (col < 2 - 1 && index < totalButtons - 1) {
                    View separator = new View(this);
                    separator.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(5.0f), buttonHeight));
                    rowLayout.addView(separator);
                }
                col++;
                punishmentTypes2 = punishmentTypes3;
                title2 = title3;
                container2 = container3;
            }
            linearLayout.addView(rowLayout);
            if (row < rows - 1) {
                View sep = new View(this);
                sep.setLayoutParams(new LinearLayout.LayoutParams(-1, 5));
                sep.setBackground(GlassUI.glassSelector(getApplicationContext(), -13421773));
                linearLayout.addView(sep);
            }
            row++;
            punishmentTypes2 = punishmentTypes;
            title2 = title;
            container2 = container;
            i = 0;
            i2 = -1;
            i3 = -2;
        }
        LinearLayout linearLayout2 = container2;
        scrollView.addView(linearLayout);
        linearLayout2.addView(scrollView);
        Button btnBack = new Button(this);
        btnBack.setText("← Назад к клавиатуре");
        btnBack.setLayoutParams(new LinearLayout.LayoutParams(-1, getButtonHeight(55)));
        btnBack.setBackground(GlassUI.glassSelector(getApplicationContext(), -10066330));
        btnBack.setTextColor(-1);
        btnBack.setTextSize(getFontMultiplier() * 14.0f);
        btnBack.setPadding(10, 12, 10, 12);
        btnBack.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.65
            @Override // android.view.View.OnClickListener
            public void onClick(View v) {
                KeyboardService.this.showKeyboardView();
            }
        });
        linearLayout2.addView(btnBack);
        this.menuLayout.addView(linearLayout2);
        updateMenuButtonsHeight(this.menuLayout, getButtonHeight(55));
        applyKeyboardTransparency(this.keyboardTransparency);
    }

    /* JADX INFO: Access modifiers changed from: private */
    /* JADX WARN: Code restructure failed: missing block: B:83:0x00d8, code lost:
    
        if (r3.isEmpty() != false) goto L13;
     */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    public void showPunishmentButtonsForType(int r36, java.lang.String r37) {
        /*
            Method dump skipped, instructions count: 960
            To view this dump add '--comments-level debug' option
        */
        throw new UnsupportedOperationException("Method not decompiled: gtandroid.universal.KeyboardService.showPunishmentButtonsForType(int, java.lang.String):void");
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void executePunishment(String cmd, String time, String reason, int requiredLevel) {
        StringBuilder fullCmd = new StringBuilder();
        if (this.adminLevel < requiredLevel) {
            fullCmd.append("/a");
        }
        fullCmd.append(cmd).append("").append(this.selectedPunishmentId);
        if (!time.isEmpty()) {
            fullCmd.append("").append(time);
        }
        if (!reason.isEmpty()) {
            fullCmd.append("").append(reason);
        }
        String finalCommand = fullCmd.toString();
        sendCommandToChat(finalCommand);
        if (this.adminLevel < requiredLevel) {
            Toast.makeText(this, "⚠️ Отправлено через /a: " + finalCommand, 1).show();
        } else {
            Toast.makeText(this, "✅ Отправлено: " + finalCommand, 0).show();
        }
        showKeyboardView();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showSpectateMenu() {
        if (this.menuLayout == null) {
            return;
        }
        this.menuLayout.removeAllViews();
        this.menuLayout.setVisibility(0);
        View keyboardLayout = this.keyboardView.findViewById(R.id.keyboardLayout);
        if (keyboardLayout != null) {
            keyboardLayout.setVisibility(8);
        }
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(1);
        container.setPadding(10, 10, 10, 10);
        container.setBackground(GlassUI.glassSelector(getApplicationContext(), -15066598));
        TextView title = new TextView(this);
        title.setText("СЛЕЖКА");
        title.setTextColor(-1);
        title.setTextSize(getFontMultiplier() * 18.0f);
        title.setGravity(17);
        title.setPadding(0, 10, 0, 20);
        container.addView(title);
        Button btnSpectate = new Button(this);
        btnSpectate.setText("👁️ Следить");
        btnSpectate.setLayoutParams(new LinearLayout.LayoutParams(-1, getButtonHeight(70)));
        btnSpectate.setBackground(GlassUI.glassSelector(getApplicationContext(), -16733696));
        btnSpectate.setTextColor(-1);
        btnSpectate.setTextSize(getFontMultiplier() * 16.0f);
        btnSpectate.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.68
            @Override // android.view.View.OnClickListener
            public void onClick(View v) {
                KeyboardService.this.showSpectateIdInput();
            }
        });
        container.addView(btnSpectate);
        View sep1 = new View(this);
        sep1.setLayoutParams(new LinearLayout.LayoutParams(-1, 3));
        sep1.setBackground(GlassUI.glassSelector(getApplicationContext(), -13421773));
        container.addView(sep1);
        Button btnStopSpectate = new Button(this);
        btnStopSpectate.setText("⏹️ Выйти из слежки");
        btnStopSpectate.setLayoutParams(new LinearLayout.LayoutParams(-1, getButtonHeight(70)));
        btnStopSpectate.setBackground(GlassUI.glassSelector(getApplicationContext(), -3381760));
        btnStopSpectate.setTextColor(-1);
        btnStopSpectate.setTextSize(getFontMultiplier() * 16.0f);
        btnStopSpectate.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.69
            @Override // android.view.View.OnClickListener
            public void onClick(View v) {
                KeyboardService.this.sendCommandToChat("/spoff");
                Toast.makeText(KeyboardService.this, "✅ Отправлено: /spoff", 0).show();
                KeyboardService.this.showKeyboardView();
            }
        });
        container.addView(btnStopSpectate);
        Button btnBack = new Button(this);
        btnBack.setText("← Назад к клавиатуре");
        btnBack.setLayoutParams(new LinearLayout.LayoutParams(-1, getButtonHeight(55)));
        btnBack.setBackground(GlassUI.glassSelector(getApplicationContext(), -10066330));
        btnBack.setTextColor(-1);
        btnBack.setTextSize(getFontMultiplier() * 14.0f);
        btnBack.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.70
            @Override // android.view.View.OnClickListener
            public void onClick(View v) {
                KeyboardService.this.showKeyboardView();
            }
        });
        container.addView(btnBack);
        this.menuLayout.addView(container);
        updateMenuButtonsHeight(this.menuLayout, getButtonHeight(55));
        applyKeyboardTransparency(this.keyboardTransparency);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showSpectateIdInput() {
        int index;
        if (this.menuLayout == null) {
            return;
        }
        this.menuLayout.removeAllViews();
        int i = 0;
        this.menuLayout.setVisibility(0);
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        linearLayout.setPadding(10, 10, 10, 10);
        linearLayout.setBackground(GlassUI.glassSelector(getApplicationContext(), -15066598));
        TextView title = new TextView(this);
        title.setText("ВВЕДИТЕ ID");
        int i2 = -1;
        title.setTextColor(-1);
        title.setTextSize(getFontMultiplier() * 16.0f);
        title.setGravity(17);
        title.setPadding(0, 10, 0, 15);
        linearLayout.addView(title);
        final EditText idInput = new EditText(this);
        idInput.setBackground(GlassUI.lightCard(this, 10.0f));
        idInput.setPadding(24, 18, 24, 18);
        idInput.setTextColor(-14935010);
        idInput.setHintTextColor(-7434605);
        int i3 = -2;
        idInput.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        idInput.setBackground(GlassUI.glassSelector(getApplicationContext(), -13421773));
        idInput.setTextColor(-1);
        idInput.setHint("ID игрока");
        idInput.setInputType(2);
        idInput.setPadding(10, 10, 10, 10);
        idInput.setTextSize(getFontMultiplier() * 14.0f);
        linearLayout.addView(idInput);
        String[] numbers = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "C", "0", "⌫"};
        int buttonHeight = getButtonHeight(55);
        float buttonTextSize = getFontMultiplier() * 16.0f;
        int row = 0;
        while (true) {
            float f = 1.0f;
            if (row < 4) {
                LinearLayout rowLayout = new LinearLayout(this);
                rowLayout.setOrientation(i);
                rowLayout.setLayoutParams(new LinearLayout.LayoutParams(i2, i3));
                int col = 0;
                while (col < 3 && (index = (row * 3) + col) < numbers.length) {
                    final String num = numbers[index];
                    Button btn = new Button(this);
                    btn.setText(num);
                    btn.setLayoutParams(new LinearLayout.LayoutParams(i, buttonHeight, f));
                    btn.setTextSize(buttonTextSize);
                    if (num.equals("C") || num.equals("⌫")) {
                        btn.setBackground(GlassUI.glassSelector(getApplicationContext(), -3381760));
                    } else {
                        btn.setBackground(GlassUI.glassSelector(getApplicationContext(), -12303292));
                    }
                    btn.setTextColor(-1);
                    btn.setPadding(5, 5, 5, 5);
                    btn.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.71
                        @Override // android.view.View.OnClickListener
                        public void onClick(View v) {
                            if (num.equals("C")) {
                                idInput.setText("");
                                return;
                            }
                            if (num.equals("⌫")) {
                                String current = idInput.getText().toString();
                                if (current.length() > 0) {
                                    idInput.setText(current.substring(0, current.length() - 1));
                                    return;
                                }
                                return;
                            }
                            idInput.append(num);
                        }
                    });
                    rowLayout.addView(btn);
                    if (col < 2) {
                        View separator = new View(this);
                        separator.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(3.0f), buttonHeight));
                        rowLayout.addView(separator);
                    }
                    col++;
                    i = 0;
                    f = 1.0f;
                }
                linearLayout.addView(rowLayout);
                if (row < 3) {
                    View separator2 = new View(this);
                    separator2.setLayoutParams(new LinearLayout.LayoutParams(-1, 3));
                    separator2.setBackground(GlassUI.glassSelector(getApplicationContext(), -13421773));
                    linearLayout.addView(separator2);
                }
                row++;
                i = 0;
                i2 = -1;
                i3 = -2;
            } else {
                LinearLayout buttonRow = new LinearLayout(this);
                buttonRow.setOrientation(0);
                buttonRow.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
                Button btnBack = new Button(this);
                btnBack.setText("← Назад");
                btnBack.setLayoutParams(new LinearLayout.LayoutParams(0, buttonHeight, 1.0f));
                btnBack.setBackground(GlassUI.glassSelector(getApplicationContext(), -10066330));
                btnBack.setTextColor(-1);
                btnBack.setTextSize(buttonTextSize);
                btnBack.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.72
                    @Override // android.view.View.OnClickListener
                    public void onClick(View v) {
                        KeyboardService.this.showSpectateMenu();
                    }
                });
                Button btnSend = new Button(this);
                btnSend.setText("Следить");
                btnSend.setLayoutParams(new LinearLayout.LayoutParams(0, buttonHeight, 1.0f));
                btnSend.setBackground(GlassUI.glassSelector(getApplicationContext(), -16733696));
                btnSend.setTextColor(-1);
                btnSend.setTextSize(buttonTextSize);
                btnSend.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.73
                    @Override // android.view.View.OnClickListener
                    public void onClick(View v) {
                        String id = idInput.getText().toString();
                        if (!id.isEmpty()) {
                            KeyboardService.this.sendCommandToChat("/sp" + id);
                            Toast.makeText(KeyboardService.this, "✅ Отправлено: /sp" + id, 0).show();
                            KeyboardService.this.showKeyboardView();
                            return;
                        }
                        Toast.makeText(KeyboardService.this, "❌ Введите ID", 0).show();
                    }
                });
                buttonRow.addView(btnBack);
                View separator3 = new View(this);
                separator3.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(5.0f), buttonHeight));
                buttonRow.addView(separator3);
                buttonRow.addView(btnSend);
                linearLayout.addView(buttonRow);
                this.menuLayout.addView(linearLayout);
                updateMenuButtonsHeight(this.menuLayout, buttonHeight);
                applyKeyboardTransparency(this.keyboardTransparency);
                return;
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showSpawnPointMenu() {
        if (this.menuLayout == null) {
            return;
        }
        this.menuLayout.removeAllViews();
        this.menuLayout.setVisibility(0);
        View keyboardLayout = this.keyboardView.findViewById(R.id.keyboardLayout);
        if (keyboardLayout != null) {
            keyboardLayout.setVisibility(8);
        }
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(1);
        container.setPadding(10, 10, 10, 10);
        container.setBackground(GlassUI.glassSelector(getApplicationContext(), -15066598));
        TextView title = new TextView(this);
        title.setText("ТОЧКА СПАВНА");
        title.setTextColor(-1);
        title.setTextSize(getFontMultiplier() * 16.0f);
        title.setGravity(17);
        title.setPadding(0, 10, 0, 15);
        container.addView(title);
        int buttonHeight = getButtonHeight(60);
        float buttonTextSize = getFontMultiplier() * 14.0f;
        Button btnAz = new Button(this);
        btnAz.setText("1. Аз");
        btnAz.setLayoutParams(new LinearLayout.LayoutParams(-1, buttonHeight));
        btnAz.setBackground(GlassUI.glassSelector(getApplicationContext(), -12303292));
        btnAz.setTextColor(-1);
        btnAz.setTextSize(buttonTextSize);
        btnAz.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.74
            @Override // android.view.View.OnClickListener
            public void onClick(View v) {
                KeyboardService.this.sendCommandToChat("/az");
                KeyboardService.this.showKeyboardView();
                Toast.makeText(KeyboardService.this, "✅ Отправлено: /az", 0).show();
            }
        });
        container.addView(btnAz);
        View sep1 = new View(this);
        sep1.setLayoutParams(new LinearLayout.LayoutParams(-1, 2));
        sep1.setBackground(GlassUI.glassSelector(getApplicationContext(), -13421773));
        container.addView(sep1);
        Button btnSpawn = new Button(this);
        btnSpawn.setText("2. Спавн");
        btnSpawn.setLayoutParams(new LinearLayout.LayoutParams(-1, buttonHeight));
        btnSpawn.setBackground(GlassUI.glassSelector(getApplicationContext(), -12303292));
        btnSpawn.setTextColor(-1);
        btnSpawn.setTextSize(buttonTextSize);
        btnSpawn.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.75
            @Override // android.view.View.OnClickListener
            public void onClick(View v) {
                KeyboardService.this.showSpawnIdInput();
            }
        });
        container.addView(btnSpawn);
        View sep2 = new View(this);
        sep2.setLayoutParams(new LinearLayout.LayoutParams(-1, 2));
        sep2.setBackground(GlassUI.glassSelector(getApplicationContext(), -13421773));
        container.addView(sep2);
        Button btnMark = new Button(this);
        btnMark.setText("3. Марк");
        btnMark.setLayoutParams(new LinearLayout.LayoutParams(-1, buttonHeight));
        btnMark.setBackground(GlassUI.glassSelector(getApplicationContext(), -12303292));
        btnMark.setTextColor(-1);
        btnMark.setTextSize(buttonTextSize);
        btnMark.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.76
            @Override // android.view.View.OnClickListener
            public void onClick(View v) {
                KeyboardService.this.sendCommandToChat("/mark");
                KeyboardService.this.showKeyboardView();
                Toast.makeText(KeyboardService.this, "✅ Отправлено: /mark", 0).show();
            }
        });
        container.addView(btnMark);
        View sep3 = new View(this);
        sep3.setLayoutParams(new LinearLayout.LayoutParams(-1, 2));
        sep3.setBackground(GlassUI.glassSelector(getApplicationContext(), -13421773));
        container.addView(sep3);
        Button btnGoToMark = new Button(this);
        btnGoToMark.setText("4. ТаМарк");
        btnGoToMark.setLayoutParams(new LinearLayout.LayoutParams(-1, buttonHeight));
        btnGoToMark.setBackground(GlassUI.glassSelector(getApplicationContext(), -12303292));
        btnGoToMark.setTextColor(-1);
        btnGoToMark.setTextSize(buttonTextSize);
        btnGoToMark.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.77
            @Override // android.view.View.OnClickListener
            public void onClick(View v) {
                KeyboardService.this.sendCommandToChat("/gotomark");
                KeyboardService.this.showKeyboardView();
                Toast.makeText(KeyboardService.this, "✅ Отправлено: /gotomark", 0).show();
            }
        });
        container.addView(btnGoToMark);
        View sep4 = new View(this);
        sep4.setLayoutParams(new LinearLayout.LayoutParams(-1, 2));
        sep4.setBackground(GlassUI.glassSelector(getApplicationContext(), -13421773));
        container.addView(sep4);
        Button btnBack = new Button(this);
        btnBack.setText("← Назад к клавиатуре");
        btnBack.setLayoutParams(new LinearLayout.LayoutParams(-1, getButtonHeight(50)));
        btnBack.setBackground(GlassUI.glassSelector(getApplicationContext(), -10066330));
        btnBack.setTextColor(-1);
        btnBack.setTextSize(getFontMultiplier() * 14.0f);
        btnBack.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.78
            @Override // android.view.View.OnClickListener
            public void onClick(View v) {
                KeyboardService.this.showKeyboardView();
            }
        });
        container.addView(btnBack);
        this.menuLayout.addView(container);
        updateMenuButtonsHeight(this.menuLayout, getButtonHeight(50));
        applyKeyboardTransparency(this.keyboardTransparency);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showSpawnIdInput() {
        int index;
        if (this.menuLayout == null) {
            return;
        }
        this.menuLayout.removeAllViews();
        int i = 0;
        this.menuLayout.setVisibility(0);
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        linearLayout.setPadding(10, 10, 10, 10);
        linearLayout.setBackground(GlassUI.glassSelector(getApplicationContext(), -15066598));
        TextView title = new TextView(this);
        title.setText("ВВЕДИТЕ ID");
        int i2 = -1;
        title.setTextColor(-1);
        title.setTextSize(getFontMultiplier() * 16.0f);
        title.setGravity(17);
        title.setPadding(0, 10, 0, 15);
        linearLayout.addView(title);
        final EditText idInput = new EditText(this);
        idInput.setBackground(GlassUI.lightCard(this, 10.0f));
        idInput.setPadding(24, 18, 24, 18);
        idInput.setTextColor(-14935010);
        idInput.setHintTextColor(-7434605);
        int i3 = -2;
        idInput.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        idInput.setBackground(GlassUI.glassSelector(getApplicationContext(), -13421773));
        idInput.setTextColor(-1);
        idInput.setHint("ID игрока");
        idInput.setInputType(2);
        idInput.setPadding(10, 10, 10, 10);
        idInput.setTextSize(getFontMultiplier() * 14.0f);
        linearLayout.addView(idInput);
        String[] numbers = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "C", "0", "⌫"};
        int buttonHeight = getButtonHeight(50);
        float buttonTextSize = getFontMultiplier() * 14.0f;
        int row = 0;
        while (true) {
            float f = 1.0f;
            if (row < 4) {
                LinearLayout rowLayout = new LinearLayout(this);
                rowLayout.setOrientation(i);
                rowLayout.setLayoutParams(new LinearLayout.LayoutParams(i2, i3));
                int col = 0;
                while (col < 3 && (index = (row * 3) + col) < numbers.length) {
                    final String num = numbers[index];
                    Button btn = new Button(this);
                    btn.setText(num);
                    btn.setLayoutParams(new LinearLayout.LayoutParams(i, buttonHeight, f));
                    btn.setTextSize(buttonTextSize);
                    if (num.equals("C") || num.equals("⌫")) {
                        btn.setBackground(GlassUI.glassSelector(getApplicationContext(), -3381760));
                    } else {
                        btn.setBackground(GlassUI.glassSelector(getApplicationContext(), -12303292));
                    }
                    btn.setTextColor(-1);
                    btn.setPadding(5, 5, 5, 5);
                    btn.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.79
                        @Override // android.view.View.OnClickListener
                        public void onClick(View v) {
                            if (num.equals("C")) {
                                idInput.setText("");
                                return;
                            }
                            if (num.equals("⌫")) {
                                String current = idInput.getText().toString();
                                if (current.length() > 0) {
                                    idInput.setText(current.substring(0, current.length() - 1));
                                    return;
                                }
                                return;
                            }
                            idInput.append(num);
                        }
                    });
                    rowLayout.addView(btn);
                    if (col < 2) {
                        View separator = new View(this);
                        separator.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(3.0f), buttonHeight));
                        rowLayout.addView(separator);
                    }
                    col++;
                    i = 0;
                    f = 1.0f;
                }
                linearLayout.addView(rowLayout);
                if (row < 3) {
                    View separator2 = new View(this);
                    separator2.setLayoutParams(new LinearLayout.LayoutParams(-1, 3));
                    separator2.setBackground(GlassUI.glassSelector(getApplicationContext(), -13421773));
                    linearLayout.addView(separator2);
                }
                row++;
                i = 0;
                i2 = -1;
                i3 = -2;
            } else {
                LinearLayout buttonRow = new LinearLayout(this);
                buttonRow.setOrientation(0);
                buttonRow.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
                Button btnBack = new Button(this);
                btnBack.setText("← Назад");
                btnBack.setLayoutParams(new LinearLayout.LayoutParams(0, buttonHeight, 1.0f));
                btnBack.setBackground(GlassUI.glassSelector(getApplicationContext(), -10066330));
                btnBack.setTextColor(-1);
                btnBack.setTextSize(buttonTextSize);
                btnBack.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.80
                    @Override // android.view.View.OnClickListener
                    public void onClick(View v) {
                        KeyboardService.this.showSpawnPointMenu();
                    }
                });
                Button btnContinue = new Button(this);
                btnContinue.setText("Отправить");
                btnContinue.setLayoutParams(new LinearLayout.LayoutParams(0, buttonHeight, 1.0f));
                btnContinue.setBackground(GlassUI.glassSelector(getApplicationContext(), -16733696));
                btnContinue.setTextColor(-1);
                btnContinue.setTextSize(buttonTextSize);
                btnContinue.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.81
                    @Override // android.view.View.OnClickListener
                    public void onClick(View v) {
                        String id = idInput.getText().toString();
                        if (!id.isEmpty()) {
                            KeyboardService.this.sendCommandToChat("/spawn" + id);
                            KeyboardService.this.showKeyboardView();
                            Toast.makeText(KeyboardService.this, "✅ Отправлено: /spawn" + id, 0).show();
                            return;
                        }
                        Toast.makeText(KeyboardService.this, "❌ Введите ID", 0).show();
                    }
                });
                buttonRow.addView(btnBack);
                View separator3 = new View(this);
                separator3.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(5.0f), buttonHeight));
                buttonRow.addView(separator3);
                buttonRow.addView(btnContinue);
                linearLayout.addView(buttonRow);
                this.menuLayout.addView(linearLayout);
                updateMenuButtonsHeight(this.menuLayout, buttonHeight);
                applyKeyboardTransparency(this.keyboardTransparency);
                return;
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showQuickCommandMenu() {
        View keyboardLayout;
        ScrollView scrollView;
        TextView title;
        String[] commands;
        if (this.menuLayout == null) {
            return;
        }
        this.menuLayout.removeAllViews();
        int i = 0;
        this.menuLayout.setVisibility(0);
        View keyboardLayout2 = this.keyboardView.findViewById(R.id.keyboardLayout);
        if (keyboardLayout2 != null) {
            keyboardLayout2.setVisibility(8);
        }
        ScrollView scrollView2 = new ScrollView(this);
        int i2 = -1;
        scrollView2.setLayoutParams(new LinearLayout.LayoutParams(-1, -1));
        scrollView2.setFillViewport(true);
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        linearLayout.setPadding(5, 5, 5, 5);
        linearLayout.setBackground(GlassUI.glassSelector(getApplicationContext(), -15066598));
        TextView title2 = new TextView(this);
        title2.setText("БЫСТРАЯ КОМАНДА");
        title2.setTextColor(-1);
        title2.setTextSize(getFontMultiplier() * 14.0f);
        title2.setGravity(17);
        title2.setPadding(5, 10, 5, 10);
        linearLayout.addView(title2);
        String[] buttonNames = {"Спавн ТС (Радиус)", "Спавн ТС (ID)", "Гос волна", "Чинить", "Перевернуть", "Подкинуть", "Спавн", "Статистика", "Заправить", "Лицензия", "Заморозить", "Оружие игрока", "Офф наручники", "Забрать оружие", "Отправить в /a"};
        String[] commands2 = {"/spcars 3", "/spcar", "/acceptgnews", "/fixcar", "/flip", "/slap", "/spawn", "/stats", "/setfuel", "/alic", "/freeze", "/weap", "/uncuff", "/rgun", "/a"};
        int buttonHeight = getButtonHeight(70);
        float buttonTextSize = 11.0f * getFontMultiplier();
        int row = 0;
        for (int i3 = 5; row < i3; i3 = 5) {
            LinearLayout rowLayout = new LinearLayout(this);
            rowLayout.setOrientation(i);
            rowLayout.setLayoutParams(new LinearLayout.LayoutParams(i2, -2));
            rowLayout.setWeightSum(3);
            int col = 0;
            while (true) {
                if (col >= 3) {
                    keyboardLayout = keyboardLayout2;
                    scrollView = scrollView2;
                    title = title2;
                    commands = commands2;
                    break;
                }
                int index = (row * 3) + col;
                if (index >= buttonNames.length) {
                    keyboardLayout = keyboardLayout2;
                    scrollView = scrollView2;
                    title = title2;
                    commands = commands2;
                    break;
                }
                final String cmd = commands2[index];
                final String name = buttonNames[index];
                View keyboardLayout3 = keyboardLayout2;
                Button btn = new Button(this);
                btn.setText(name);
                TextView title3 = title2;
                String[] commands3 = commands2;
                ScrollView scrollView3 = scrollView2;
                btn.setLayoutParams(new LinearLayout.LayoutParams(0, buttonHeight, 1.0f));
                btn.setBackground(GlassUI.glassSelector(getApplicationContext(), -12303292));
                btn.setTextColor(-1);
                btn.setPadding(3, 3, 3, 3);
                btn.setTextSize(buttonTextSize);
                btn.setMaxLines(2);
                btn.setGravity(17);
                btn.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.82
                    @Override // android.view.View.OnClickListener
                    public void onClick(View v) {
                        if (name.equals("Спавн ТС (Радиус)") || name.equals("Гос волна")) {
                            KeyboardService.this.sendCommandToChat(cmd);
                            Toast.makeText(KeyboardService.this, "✅ Отправлено: " + cmd, 0).show();
                            KeyboardService.this.showKeyboardView();
                        } else if (name.equals("Заправить")) {
                            KeyboardService.this.showQuickCommandIdInputForFuel(cmd);
                        } else {
                            KeyboardService.this.showQuickCommandIdInput(cmd, name);
                        }
                    }
                });
                rowLayout.addView(btn);
                if (col < 3 - 1 && index < buttonNames.length - 1) {
                    View sep = new View(this);
                    sep.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(3.0f), buttonHeight));
                    sep.setBackground(GlassUI.glassSelector(getApplicationContext(), -13421773));
                    rowLayout.addView(sep);
                }
                col++;
                keyboardLayout2 = keyboardLayout3;
                title2 = title3;
                commands2 = commands3;
                scrollView2 = scrollView3;
            }
            linearLayout.addView(rowLayout);
            if (row < 4) {
                View sep2 = new View(this);
                sep2.setLayoutParams(new LinearLayout.LayoutParams(-1, 3));
                sep2.setBackground(GlassUI.glassSelector(getApplicationContext(), -13421773));
                linearLayout.addView(sep2);
            }
            row++;
            keyboardLayout2 = keyboardLayout;
            title2 = title;
            commands2 = commands;
            scrollView2 = scrollView;
            i = 0;
            i2 = -1;
        }
        ScrollView scrollView4 = scrollView2;
        Button btnBack = new Button(this);
        btnBack.setText("← Назад к клавиатуре");
        btnBack.setLayoutParams(new LinearLayout.LayoutParams(-1, getButtonHeight(55)));
        btnBack.setBackground(GlassUI.glassSelector(getApplicationContext(), -10066330));
        btnBack.setTextColor(-1);
        btnBack.setTextSize(getFontMultiplier() * 14.0f);
        btnBack.setPadding(5, 10, 5, 10);
        btnBack.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.83
            @Override // android.view.View.OnClickListener
            public void onClick(View v) {
                KeyboardService.this.showKeyboardView();
            }
        });
        linearLayout.addView(btnBack);
        scrollView4.addView(linearLayout);
        this.menuLayout.addView(scrollView4);
        updateMenuButtonsHeight(this.menuLayout, getButtonHeight(55));
        applyKeyboardTransparency(this.keyboardTransparency);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showQuickCommandIdInputForFuel(final String commandBase) {
        String[] numbers;
        if (this.menuLayout == null) {
            return;
        }
        this.menuLayout.removeAllViews();
        int i = 0;
        this.menuLayout.setVisibility(0);
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        linearLayout.setPadding(10, 10, 10, 10);
        linearLayout.setBackground(GlassUI.glassSelector(getApplicationContext(), -15066598));
        TextView title = new TextView(this);
        title.setText("ЗАПРАВИТЬ (150)");
        int i2 = -1;
        title.setTextColor(-1);
        title.setTextSize(getFontMultiplier() * 16.0f);
        title.setGravity(17);
        title.setPadding(0, 10, 0, 15);
        linearLayout.addView(title);
        TextView hint = new TextView(this);
        hint.setText("Введите ID игрока:");
        hint.setTextColor(-1);
        hint.setTextSize(getFontMultiplier() * 14.0f);
        hint.setGravity(17);
        linearLayout.addView(hint);
        final EditText idInput = new EditText(this);
        idInput.setBackground(GlassUI.lightCard(this, 10.0f));
        idInput.setPadding(24, 18, 24, 18);
        idInput.setTextColor(-14935010);
        idInput.setHintTextColor(-7434605);
        int i3 = -2;
        idInput.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        idInput.setBackground(GlassUI.glassSelector(getApplicationContext(), -13421773));
        idInput.setTextColor(-1);
        idInput.setHint("ID игрока");
        idInput.setInputType(2);
        idInput.setPadding(10, 10, 10, 10);
        idInput.setTextSize(getFontMultiplier() * 14.0f);
        linearLayout.addView(idInput);
        String[] numbers2 = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "C", "0", "⌫"};
        int buttonHeight = getButtonHeight(55);
        float buttonTextSize = getFontMultiplier() * 16.0f;
        int row = 0;
        while (true) {
            float f = 1.0f;
            if (row < 4) {
                LinearLayout rowLayout = new LinearLayout(this);
                rowLayout.setOrientation(i);
                rowLayout.setLayoutParams(new LinearLayout.LayoutParams(i2, i3));
                int col = 0;
                while (true) {
                    if (col >= 3) {
                        numbers = numbers2;
                        break;
                    }
                    int index = (row * 3) + col;
                    if (index >= numbers2.length) {
                        numbers = numbers2;
                        break;
                    }
                    final String num = numbers2[index];
                    Button btn = new Button(this);
                    btn.setText(num);
                    String[] numbers3 = numbers2;
                    btn.setLayoutParams(new LinearLayout.LayoutParams(i, buttonHeight, f));
                    btn.setTextSize(buttonTextSize);
                    if (num.equals("C") || num.equals("⌫")) {
                        btn.setBackground(GlassUI.glassSelector(getApplicationContext(), -3381760));
                    } else {
                        btn.setBackground(GlassUI.glassSelector(getApplicationContext(), -12303292));
                    }
                    btn.setTextColor(-1);
                    btn.setPadding(5, 5, 5, 5);
                    btn.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.84
                        @Override // android.view.View.OnClickListener
                        public void onClick(View v) {
                            if (num.equals("C")) {
                                idInput.setText("");
                                return;
                            }
                            if (num.equals("⌫")) {
                                String current = idInput.getText().toString();
                                if (current.length() > 0) {
                                    idInput.setText(current.substring(0, current.length() - 1));
                                    return;
                                }
                                return;
                            }
                            idInput.append(num);
                        }
                    });
                    rowLayout.addView(btn);
                    if (col < 2) {
                        View separator = new View(this);
                        separator.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(3.0f), buttonHeight));
                        rowLayout.addView(separator);
                    }
                    col++;
                    numbers2 = numbers3;
                    i = 0;
                    f = 1.0f;
                }
                linearLayout.addView(rowLayout);
                if (row < 3) {
                    View separator2 = new View(this);
                    separator2.setLayoutParams(new LinearLayout.LayoutParams(-1, 3));
                    separator2.setBackground(GlassUI.glassSelector(getApplicationContext(), -13421773));
                    linearLayout.addView(separator2);
                }
                row++;
                numbers2 = numbers;
                i = 0;
                i2 = -1;
                i3 = -2;
            } else {
                LinearLayout buttonRow = new LinearLayout(this);
                buttonRow.setOrientation(0);
                buttonRow.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
                Button btnBack = new Button(this);
                btnBack.setText("← Назад");
                btnBack.setLayoutParams(new LinearLayout.LayoutParams(0, buttonHeight, 1.0f));
                btnBack.setBackground(GlassUI.glassSelector(getApplicationContext(), -10066330));
                btnBack.setTextColor(-1);
                btnBack.setTextSize(buttonTextSize);
                btnBack.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.85
                    @Override // android.view.View.OnClickListener
                    public void onClick(View v) {
                        KeyboardService.this.showQuickCommandMenu();
                    }
                });
                Button btnSend = new Button(this);
                btnSend.setText("Отправить");
                btnSend.setLayoutParams(new LinearLayout.LayoutParams(0, buttonHeight, 1.0f));
                btnSend.setBackground(GlassUI.glassSelector(getApplicationContext(), -16733696));
                btnSend.setTextColor(-1);
                btnSend.setTextSize(buttonTextSize);
                btnSend.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.86
                    @Override // android.view.View.OnClickListener
                    public void onClick(View v) {
                        String id = idInput.getText().toString();
                        if (id.isEmpty()) {
                            Toast.makeText(KeyboardService.this, "❌ Введите ID", 0).show();
                            return;
                        }
                        String finalCommand = commandBase + "" + id + " 150";
                        KeyboardService.this.sendCommandToChat(finalCommand);
                        Toast.makeText(KeyboardService.this, "✅ Отправлено: " + finalCommand, 0).show();
                        KeyboardService.this.showKeyboardView();
                    }
                });
                buttonRow.addView(btnBack);
                View separator3 = new View(this);
                separator3.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(5.0f), buttonHeight));
                buttonRow.addView(separator3);
                buttonRow.addView(btnSend);
                linearLayout.addView(buttonRow);
                this.menuLayout.addView(linearLayout);
                updateMenuButtonsHeight(this.menuLayout, buttonHeight);
                applyKeyboardTransparency(this.keyboardTransparency);
                return;
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showQuickCommandIdInput(final String command, final String commandName) {
        String[] numbers;
        if (this.menuLayout == null) {
            return;
        }
        this.menuLayout.removeAllViews();
        int i = 0;
        this.menuLayout.setVisibility(0);
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        linearLayout.setPadding(10, 10, 10, 10);
        linearLayout.setBackground(GlassUI.glassSelector(getApplicationContext(), -15066598));
        TextView title = new TextView(this);
        title.setText("" + commandName.toUpperCase() + "");
        int i2 = -1;
        title.setTextColor(-1);
        title.setTextSize(getFontMultiplier() * 16.0f);
        title.setGravity(17);
        title.setPadding(0, 10, 0, 15);
        linearLayout.addView(title);
        TextView hint = new TextView(this);
        hint.setText("Введите ID игрока:");
        hint.setTextColor(-1);
        hint.setTextSize(getFontMultiplier() * 14.0f);
        hint.setGravity(17);
        linearLayout.addView(hint);
        final EditText idInput = new EditText(this);
        idInput.setBackground(GlassUI.lightCard(this, 10.0f));
        idInput.setPadding(24, 18, 24, 18);
        idInput.setTextColor(-14935010);
        idInput.setHintTextColor(-7434605);
        int i3 = -2;
        idInput.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        idInput.setBackground(GlassUI.glassSelector(getApplicationContext(), -13421773));
        idInput.setTextColor(-1);
        idInput.setHint("ID игрока");
        idInput.setInputType(2);
        idInput.setPadding(10, 10, 10, 10);
        idInput.setTextSize(getFontMultiplier() * 14.0f);
        linearLayout.addView(idInput);
        String[] numbers2 = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "C", "0", "⌫"};
        int buttonHeight = getButtonHeight(55);
        float buttonTextSize = getFontMultiplier() * 16.0f;
        int row = 0;
        while (true) {
            float f = 1.0f;
            if (row < 4) {
                LinearLayout rowLayout = new LinearLayout(this);
                rowLayout.setOrientation(i);
                rowLayout.setLayoutParams(new LinearLayout.LayoutParams(i2, i3));
                int col = 0;
                while (true) {
                    if (col >= 3) {
                        numbers = numbers2;
                        break;
                    }
                    int index = (row * 3) + col;
                    if (index >= numbers2.length) {
                        numbers = numbers2;
                        break;
                    }
                    final String num = numbers2[index];
                    Button btn = new Button(this);
                    btn.setText(num);
                    String[] numbers3 = numbers2;
                    btn.setLayoutParams(new LinearLayout.LayoutParams(i, buttonHeight, f));
                    btn.setTextSize(buttonTextSize);
                    if (num.equals("C") || num.equals("⌫")) {
                        btn.setBackground(GlassUI.glassSelector(getApplicationContext(), -3381760));
                    } else {
                        btn.setBackground(GlassUI.glassSelector(getApplicationContext(), -12303292));
                    }
                    btn.setTextColor(-1);
                    btn.setPadding(5, 5, 5, 5);
                    btn.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.87
                        @Override // android.view.View.OnClickListener
                        public void onClick(View v) {
                            if (num.equals("C")) {
                                idInput.setText("");
                                return;
                            }
                            if (num.equals("⌫")) {
                                String current = idInput.getText().toString();
                                if (current.length() > 0) {
                                    idInput.setText(current.substring(0, current.length() - 1));
                                    return;
                                }
                                return;
                            }
                            idInput.append(num);
                        }
                    });
                    rowLayout.addView(btn);
                    if (col < 2) {
                        View separator = new View(this);
                        separator.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(3.0f), buttonHeight));
                        rowLayout.addView(separator);
                    }
                    col++;
                    numbers2 = numbers3;
                    i = 0;
                    f = 1.0f;
                }
                linearLayout.addView(rowLayout);
                if (row < 3) {
                    View separator2 = new View(this);
                    separator2.setLayoutParams(new LinearLayout.LayoutParams(-1, 3));
                    separator2.setBackground(GlassUI.glassSelector(getApplicationContext(), -13421773));
                    linearLayout.addView(separator2);
                }
                row++;
                numbers2 = numbers;
                i = 0;
                i2 = -1;
                i3 = -2;
            } else {
                LinearLayout buttonRow = new LinearLayout(this);
                buttonRow.setOrientation(0);
                buttonRow.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
                Button btnBack = new Button(this);
                btnBack.setText("← Назад");
                btnBack.setLayoutParams(new LinearLayout.LayoutParams(0, buttonHeight, 1.0f));
                btnBack.setBackground(GlassUI.glassSelector(getApplicationContext(), -10066330));
                btnBack.setTextColor(-1);
                btnBack.setTextSize(buttonTextSize);
                btnBack.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.88
                    @Override // android.view.View.OnClickListener
                    public void onClick(View v) {
                        KeyboardService.this.showQuickCommandMenu();
                    }
                });
                Button btnSend = new Button(this);
                btnSend.setText("Отправить");
                btnSend.setLayoutParams(new LinearLayout.LayoutParams(0, buttonHeight, 1.0f));
                btnSend.setBackground(GlassUI.glassSelector(getApplicationContext(), -16733696));
                btnSend.setTextColor(-1);
                btnSend.setTextSize(buttonTextSize);
                btnSend.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.89
                    @Override // android.view.View.OnClickListener
                    public void onClick(View v) {
                        String finalCommand;
                        String id = idInput.getText().toString();
                        if (id.isEmpty()) {
                            Toast.makeText(KeyboardService.this, "❌ Введите ID", 0).show();
                            return;
                        }
                        if (commandName.equals("Отправить в /a")) {
                            finalCommand = command + "" + id + " Нуждается в помощи";
                        } else {
                            finalCommand = command + "" + id;
                        }
                        KeyboardService.this.sendCommandToChat(finalCommand);
                        Toast.makeText(KeyboardService.this, "✅ Отправлено: " + finalCommand, 0).show();
                        KeyboardService.this.showKeyboardView();
                    }
                });
                buttonRow.addView(btnBack);
                View separator3 = new View(this);
                separator3.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(5.0f), buttonHeight));
                buttonRow.addView(separator3);
                buttonRow.addView(btnSend);
                linearLayout.addView(buttonRow);
                this.menuLayout.addView(linearLayout);
                updateMenuButtonsHeight(this.menuLayout, buttonHeight);
                applyKeyboardTransparency(this.keyboardTransparency);
                return;
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showQuickAnswersMenu() {
        ScrollView scrollView;
        String str;
        int rows;
        TextView title;
        int row;
        JSONArray jsonArray;
        String str2 = "";
        if (this.menuLayout == null) {
            return;
        }
        this.menuLayout.removeAllViews();
        int i = 0;
        this.menuLayout.setVisibility(0);
        View keyboardLayout = this.keyboardView.findViewById(R.id.keyboardLayout);
        if (keyboardLayout != null) {
            keyboardLayout.setVisibility(8);
        }
        this.showMenu = true;
        ScrollView scrollView2 = new ScrollView(this);
        scrollView2.setLayoutParams(new LinearLayout.LayoutParams(-1, -1));
        scrollView2.setFillViewport(true);
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        linearLayout.setPadding(5, 5, 5, 5);
        linearLayout.setBackground(GlassUI.glassSelector(getApplicationContext(), -15066598));
        TextView title2 = new TextView(this);
        title2.setText("БЫСТРЫЙ ОТВЕТ");
        title2.setTextColor(-1);
        title2.setTextSize(getFontMultiplier() * 14.0f);
        title2.setGravity(17);
        title2.setPadding(5, 10, 5, 10);
        linearLayout.addView(title2);
        try {
            String json = this.prefs.getString("quick_answers", "[]");
            JSONArray jsonArray2 = new JSONArray(json);
            if (jsonArray2.length() == 0) {
                try {
                    TextView tv = new TextView(this);
                    tv.setText("Нет быстрых ответов\nДобавьте в приложении");
                    tv.setTextColor(-256);
                    tv.setGravity(17);
                    tv.setPadding(10, 50, 10, 50);
                    tv.setTextSize(getFontMultiplier() * 12.0f);
                    linearLayout.addView(tv);
                    scrollView = scrollView2;
                } catch (Exception e) {
                    scrollView = scrollView2;
                    e.printStackTrace();
                    TextView tv2 = new TextView(this);
                    tv2.setText("Ошибка: " + e.getMessage());
                    tv2.setTextColor(-65536);
                    tv2.setPadding(10, 10, 10, 10);
                    tv2.setTextSize(getFontMultiplier() * 12.0f);
                    linearLayout.addView(tv2);
                    Button btnBack = new Button(this);
                    btnBack.setText("← Назад к клавиатуре");
                    btnBack.setLayoutParams(new LinearLayout.LayoutParams(-1, getButtonHeight(55)));
                    btnBack.setBackground(GlassUI.glassSelector(getApplicationContext(), -10066330));
                    btnBack.setTextColor(-1);
                    btnBack.setTextSize(getFontMultiplier() * 14.0f);
                    btnBack.setPadding(5, 10, 5, 10);
                    btnBack.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.91
                        @Override // android.view.View.OnClickListener
                        public void onClick(View v) {
                            KeyboardService.this.showKeyboardView();
                        }
                    });
                    linearLayout.addView(btnBack);
                    ScrollView scrollView3 = scrollView;
                    scrollView3.addView(linearLayout);
                    this.menuLayout.addView(scrollView3);
                    updateMenuButtonsHeight(this.menuLayout, getButtonHeight(55));
                    applyKeyboardTransparency(this.keyboardTransparency);
                }
            } else {
                int buttonHeight = getButtonHeight(70);
                float buttonTextSize = getFontMultiplier() * 11.0f;
                int total = jsonArray2.length();
                int rows2 = ((total + 4) - 1) / 4;
                int row2 = 0;
                while (row2 < rows2) {
                    LinearLayout rowLayout = new LinearLayout(this);
                    rowLayout.setOrientation(i);
                    View keyboardLayout2 = keyboardLayout;
                    String json2 = json;
                    try {
                        rowLayout.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
                        rowLayout.setWeightSum(4);
                        int col = 0;
                        while (true) {
                            if (col >= 4) {
                                str = str2;
                                rows = rows2;
                                scrollView = scrollView2;
                                title = title2;
                                row = row2;
                                jsonArray = jsonArray2;
                                break;
                            }
                            int index = (row2 * 4) + col;
                            if (index >= total) {
                                str = str2;
                                rows = rows2;
                                scrollView = scrollView2;
                                title = title2;
                                row = row2;
                                jsonArray = jsonArray2;
                                break;
                            }
                            JSONObject obj = jsonArray2.getJSONObject(index);
                            TextView title3 = title2;
                            try {
                                JSONArray jsonArray3 = jsonArray2;
                                final String answerName = obj.optString("name", "Ответ");
                                final String answerText = obj.optString("answer", str2);
                                ScrollView scrollView4 = scrollView2;
                                final String command = obj.optString("command", str2);
                                String str3 = str2;
                                Button btn = new Button(this);
                                btn.setText(answerName);
                                int row3 = row2;
                                int rows3 = rows2;
                                btn.setLayoutParams(new LinearLayout.LayoutParams(0, buttonHeight, 1.0f));
                                btn.setBackground(GlassUI.glassSelector(getApplicationContext(), -12303292));
                                btn.setTextColor(-1);
                                btn.setPadding(3, 3, 3, 3);
                                btn.setTextSize(buttonTextSize);
                                btn.setMaxLines(2);
                                btn.setGravity(17);
                                btn.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.90
                                    @Override // android.view.View.OnClickListener
                                    public void onClick(View v) {
                                        KeyboardService.this.showQuickAnswerIdInput(command, answerText, answerName);
                                    }
                                });
                                btn.setOnTouchListener(getButtonTouchListener(-10066330, -12303292));
                                rowLayout.addView(btn);
                                if (col < 4 - 1 && index < total - 1) {
                                    View sep = new View(this);
                                    sep.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(3.0f), buttonHeight));
                                    sep.setBackground(GlassUI.glassSelector(getApplicationContext(), -13421773));
                                    rowLayout.addView(sep);
                                }
                                col++;
                                scrollView2 = scrollView4;
                                title2 = title3;
                                jsonArray2 = jsonArray3;
                                str2 = str3;
                                row2 = row3;
                                rows2 = rows3;
                            } catch (Exception e2) {
                                scrollView = scrollView2;
                                e2.printStackTrace();
                                TextView tv22 = new TextView(this);
                                tv22.setText("Ошибка: " + e2.getMessage());
                                tv22.setTextColor(-65536);
                                tv22.setPadding(10, 10, 10, 10);
                                tv22.setTextSize(getFontMultiplier() * 12.0f);
                                linearLayout.addView(tv22);
                                Button btnBack2 = new Button(this);
                                btnBack2.setText("← Назад к клавиатуре");
                                btnBack2.setLayoutParams(new LinearLayout.LayoutParams(-1, getButtonHeight(55)));
                                btnBack2.setBackground(GlassUI.glassSelector(getApplicationContext(), -10066330));
                                btnBack2.setTextColor(-1);
                                btnBack2.setTextSize(getFontMultiplier() * 14.0f);
                                btnBack2.setPadding(5, 10, 5, 10);
                                btnBack2.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.91
                                    @Override // android.view.View.OnClickListener
                                    public void onClick(View v) {
                                        KeyboardService.this.showKeyboardView();
                                    }
                                });
                                linearLayout.addView(btnBack2);
                                ScrollView scrollView32 = scrollView;
                                scrollView32.addView(linearLayout);
                                this.menuLayout.addView(scrollView32);
                                updateMenuButtonsHeight(this.menuLayout, getButtonHeight(55));
                                applyKeyboardTransparency(this.keyboardTransparency);
                            }
                        }
                    } catch (Exception e3) {
                        scrollView = scrollView2;
                        str = str2;
                        rows = rows2;
                        title = title2;
                        row = row2;
                        jsonArray = jsonArray2;
                    }
                    try {
                        linearLayout.addView(rowLayout);
                        int row4 = row;
                        if (row4 < rows - 1) {
                            View sep2 = new View(this);
                            sep2.setLayoutParams(new LinearLayout.LayoutParams(-1, 3));
                            sep2.setBackground(GlassUI.glassSelector(getApplicationContext(), -13421773));
                            linearLayout.addView(sep2);
                        }
                        row2 = row4 + 1;
                        keyboardLayout = keyboardLayout2;
                        json = json2;
                        scrollView2 = scrollView;
                        title2 = title;
                        jsonArray2 = jsonArray;
                        str2 = str;
                        rows2 = rows;
                        i = 0;
                    } catch (Exception e4) {
                        e4.printStackTrace();
                        TextView tv222 = new TextView(this);
                        tv222.setText("Ошибка: " + e4.getMessage());
                        tv222.setTextColor(-65536);
                        tv222.setPadding(10, 10, 10, 10);
                        tv222.setTextSize(getFontMultiplier() * 12.0f);
                        linearLayout.addView(tv222);
                        Button btnBack22 = new Button(this);
                        btnBack22.setText("← Назад к клавиатуре");
                        btnBack22.setLayoutParams(new LinearLayout.LayoutParams(-1, getButtonHeight(55)));
                        btnBack22.setBackground(GlassUI.glassSelector(getApplicationContext(), -10066330));
                        btnBack22.setTextColor(-1);
                        btnBack22.setTextSize(getFontMultiplier() * 14.0f);
                        btnBack22.setPadding(5, 10, 5, 10);
                        btnBack22.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.91
                            @Override // android.view.View.OnClickListener
                            public void onClick(View v) {
                                KeyboardService.this.showKeyboardView();
                            }
                        });
                        linearLayout.addView(btnBack22);
                        ScrollView scrollView322 = scrollView;
                        scrollView322.addView(linearLayout);
                        this.menuLayout.addView(scrollView322);
                        updateMenuButtonsHeight(this.menuLayout, getButtonHeight(55));
                        applyKeyboardTransparency(this.keyboardTransparency);
                    }
                }
                scrollView = scrollView2;
            }
        } catch (Exception e5) {
            scrollView = scrollView2;
        }
        Button btnBack222 = new Button(this);
        btnBack222.setText("← Назад к клавиатуре");
        btnBack222.setLayoutParams(new LinearLayout.LayoutParams(-1, getButtonHeight(55)));
        btnBack222.setBackground(GlassUI.glassSelector(getApplicationContext(), -10066330));
        btnBack222.setTextColor(-1);
        btnBack222.setTextSize(getFontMultiplier() * 14.0f);
        btnBack222.setPadding(5, 10, 5, 10);
        btnBack222.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.91
            @Override // android.view.View.OnClickListener
            public void onClick(View v) {
                KeyboardService.this.showKeyboardView();
            }
        });
        linearLayout.addView(btnBack222);
        ScrollView scrollView3222 = scrollView;
        scrollView3222.addView(linearLayout);
        this.menuLayout.addView(scrollView3222);
        updateMenuButtonsHeight(this.menuLayout, getButtonHeight(55));
        applyKeyboardTransparency(this.keyboardTransparency);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showQuickAnswerIdInput(final String command, final String answerText, String answerName) {
        String[] numbers;
        if (this.menuLayout == null) {
            return;
        }
        this.menuLayout.removeAllViews();
        int i = 0;
        this.menuLayout.setVisibility(0);
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        linearLayout.setPadding(10, 10, 10, 10);
        linearLayout.setBackground(GlassUI.glassSelector(getApplicationContext(), -15066598));
        TextView title = new TextView(this);
        title.setText("" + answerName.toUpperCase() + "");
        int i2 = -1;
        title.setTextColor(-1);
        title.setTextSize(getFontMultiplier() * 16.0f);
        title.setGravity(17);
        title.setPadding(0, 10, 0, 15);
        linearLayout.addView(title);
        TextView hint = new TextView(this);
        hint.setText("Введите ID игрока:");
        hint.setTextColor(-1);
        hint.setTextSize(getFontMultiplier() * 14.0f);
        hint.setGravity(17);
        linearLayout.addView(hint);
        final EditText idInput = new EditText(this);
        idInput.setBackground(GlassUI.lightCard(this, 10.0f));
        idInput.setPadding(24, 18, 24, 18);
        idInput.setTextColor(-14935010);
        idInput.setHintTextColor(-7434605);
        int i3 = -2;
        idInput.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        idInput.setBackground(GlassUI.glassSelector(getApplicationContext(), -13421773));
        idInput.setTextColor(-1);
        idInput.setHint("ID игрока");
        idInput.setInputType(2);
        idInput.setPadding(10, 10, 10, 10);
        idInput.setTextSize(getFontMultiplier() * 14.0f);
        linearLayout.addView(idInput);
        String[] numbers2 = {"1", "2", "3", "4", "5", "6", "7", "8", "9", "C", "0", "⌫"};
        int buttonHeight = getButtonHeight(55);
        float buttonTextSize = getFontMultiplier() * 16.0f;
        int row = 0;
        while (true) {
            float f = 1.0f;
            if (row < 4) {
                LinearLayout rowLayout = new LinearLayout(this);
                rowLayout.setOrientation(i);
                rowLayout.setLayoutParams(new LinearLayout.LayoutParams(i2, i3));
                int col = 0;
                while (true) {
                    if (col >= 3) {
                        numbers = numbers2;
                        break;
                    }
                    int index = (row * 3) + col;
                    if (index >= numbers2.length) {
                        numbers = numbers2;
                        break;
                    }
                    final String num = numbers2[index];
                    Button btn = new Button(this);
                    btn.setText(num);
                    String[] numbers3 = numbers2;
                    btn.setLayoutParams(new LinearLayout.LayoutParams(i, buttonHeight, f));
                    btn.setTextSize(buttonTextSize);
                    if (num.equals("C") || num.equals("⌫")) {
                        btn.setBackground(GlassUI.glassSelector(getApplicationContext(), -3381760));
                    } else {
                        btn.setBackground(GlassUI.glassSelector(getApplicationContext(), -12303292));
                    }
                    btn.setTextColor(-1);
                    btn.setPadding(5, 5, 5, 5);
                    btn.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.92
                        @Override // android.view.View.OnClickListener
                        public void onClick(View v) {
                            if (num.equals("C")) {
                                idInput.setText("");
                                return;
                            }
                            if (num.equals("⌫")) {
                                String current = idInput.getText().toString();
                                if (current.length() > 0) {
                                    idInput.setText(current.substring(0, current.length() - 1));
                                    return;
                                }
                                return;
                            }
                            idInput.append(num);
                        }
                    });
                    rowLayout.addView(btn);
                    if (col < 2) {
                        View separator = new View(this);
                        separator.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(3.0f), buttonHeight));
                        rowLayout.addView(separator);
                    }
                    col++;
                    numbers2 = numbers3;
                    i = 0;
                    f = 1.0f;
                }
                linearLayout.addView(rowLayout);
                if (row < 3) {
                    View separator2 = new View(this);
                    separator2.setLayoutParams(new LinearLayout.LayoutParams(-1, 3));
                    separator2.setBackground(GlassUI.glassSelector(getApplicationContext(), -13421773));
                    linearLayout.addView(separator2);
                }
                row++;
                numbers2 = numbers;
                i = 0;
                i2 = -1;
                i3 = -2;
            } else {
                LinearLayout buttonRow = new LinearLayout(this);
                buttonRow.setOrientation(0);
                buttonRow.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
                Button btnBack = new Button(this);
                btnBack.setText("← Назад");
                btnBack.setLayoutParams(new LinearLayout.LayoutParams(0, buttonHeight, 1.0f));
                btnBack.setBackground(GlassUI.glassSelector(getApplicationContext(), -10066330));
                btnBack.setTextColor(-1);
                btnBack.setTextSize(buttonTextSize);
                btnBack.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.93
                    @Override // android.view.View.OnClickListener
                    public void onClick(View v) {
                        KeyboardService.this.showQuickAnswersMenu();
                    }
                });
                Button btnSend = new Button(this);
                btnSend.setText("Отправить");
                btnSend.setLayoutParams(new LinearLayout.LayoutParams(0, buttonHeight, 1.0f));
                btnSend.setBackground(GlassUI.glassSelector(getApplicationContext(), -16733696));
                btnSend.setTextColor(-1);
                btnSend.setTextSize(buttonTextSize);
                btnSend.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.94
                    @Override // android.view.View.OnClickListener
                    public void onClick(View v) {
                        String finalCommand;
                        String id = idInput.getText().toString();
                        if (id.isEmpty()) {
                            Toast.makeText(KeyboardService.this, "❌ Введите ID", 0).show();
                            return;
                        }
                        if (command.isEmpty()) {
                            finalCommand = "/ans" + id + "" + answerText;
                        } else {
                            finalCommand = command + "" + id + "" + answerText;
                        }
                        KeyboardService.this.sendCommandToChat(finalCommand);
                        Toast.makeText(KeyboardService.this, "✅ Отправлено: " + finalCommand, 0).show();
                        KeyboardService.this.showKeyboardView();
                    }
                });
                buttonRow.addView(btnBack);
                View separator3 = new View(this);
                separator3.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(5.0f), buttonHeight));
                buttonRow.addView(separator3);
                buttonRow.addView(btnSend);
                linearLayout.addView(buttonRow);
                this.menuLayout.addView(linearLayout);
                updateMenuButtonsHeight(this.menuLayout, buttonHeight);
                applyKeyboardTransparency(this.keyboardTransparency);
                return;
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showTeleportMenu() {
        View keyboardLayout;
        TextView title;
        if (this.menuLayout == null) {
            return;
        }
        this.menuLayout.removeAllViews();
        int i = 0;
        this.menuLayout.setVisibility(0);
        View keyboardLayout2 = this.keyboardView.findViewById(R.id.keyboardLayout);
        if (keyboardLayout2 != null) {
            keyboardLayout2.setVisibility(8);
        }
        this.showMenu = true;
        ScrollView scrollView = new ScrollView(this);
        int i2 = -1;
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(-1, -1));
        scrollView.setFillViewport(true);
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        linearLayout.setPadding(5, 5, 5, 5);
        linearLayout.setBackground(GlassUI.glassSelector(getApplicationContext(), -15066598));
        TextView title2 = new TextView(this);
        title2.setText("ТЕЛЕПОРТ");
        title2.setTextColor(-1);
        title2.setTextSize(getFontMultiplier() * 16.0f);
        title2.setGravity(17);
        title2.setPadding(5, 10, 5, 10);
        linearLayout.addView(title2);
        String[] categories = {"🏛️ Общественные места", "🚉 Вокзалы", "🚗 Автосалоны / Стоянки", "🏛️ Гос. организации", "👹 Криминальные организации", "👷 Начальные работы", "⚙️ Работы", "🎭 Развлечения", "🚁 Транспортные узлы", "💰 Скупки", "📍 Ближайшие места"};
        int buttonHeight = getButtonHeight(70);
        float buttonTextSize = getFontMultiplier() * 12.0f;
        int row = 0;
        while (row < 6) {
            LinearLayout rowLayout = new LinearLayout(this);
            rowLayout.setOrientation(i);
            rowLayout.setLayoutParams(new LinearLayout.LayoutParams(i2, -2));
            rowLayout.setWeightSum(2);
            int col = 0;
            while (true) {
                if (col >= 2) {
                    keyboardLayout = keyboardLayout2;
                    title = title2;
                    break;
                }
                int index = (row * 2) + col;
                if (index >= categories.length) {
                    keyboardLayout = keyboardLayout2;
                    title = title2;
                    break;
                }
                final String category = categories[index];
                Button btn = new Button(this);
                btn.setText(category);
                View keyboardLayout3 = keyboardLayout2;
                TextView title3 = title2;
                btn.setLayoutParams(new LinearLayout.LayoutParams(i, buttonHeight, 1.0f));
                btn.setBackground(GlassUI.glassSelector(getApplicationContext(), -12303292));
                btn.setTextColor(-1);
                btn.setPadding(5, 5, 5, 5);
                btn.setTextSize(buttonTextSize);
                btn.setMaxLines(2);
                btn.setGravity(17);
                btn.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.95
                    @Override // android.view.View.OnClickListener
                    public void onClick(View v) {
                        KeyboardService.this.showCoordinatesByCategory(category);
                    }
                });
                btn.setOnTouchListener(getButtonTouchListener(-10066330, -12303292));
                rowLayout.addView(btn);
                if (col < 2 - 1 && index < categories.length - 1) {
                    View sep = new View(this);
                    sep.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(5.0f), buttonHeight));
                    rowLayout.addView(sep);
                }
                col++;
                keyboardLayout2 = keyboardLayout3;
                title2 = title3;
                i = 0;
            }
            linearLayout.addView(rowLayout);
            if (row < 5) {
                View sep2 = new View(this);
                sep2.setLayoutParams(new LinearLayout.LayoutParams(-1, 5));
                sep2.setBackground(GlassUI.glassSelector(getApplicationContext(), -13421773));
                linearLayout.addView(sep2);
            }
            row++;
            keyboardLayout2 = keyboardLayout;
            title2 = title;
            i = 0;
            i2 = -1;
        }
        Button btnBack = new Button(this);
        btnBack.setText("← Назад к клавиатуре");
        btnBack.setLayoutParams(new LinearLayout.LayoutParams(-1, getButtonHeight(55)));
        btnBack.setBackground(GlassUI.glassSelector(getApplicationContext(), -10066330));
        btnBack.setTextColor(-1);
        btnBack.setTextSize(getFontMultiplier() * 14.0f);
        btnBack.setPadding(5, 10, 5, 10);
        btnBack.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.96
            @Override // android.view.View.OnClickListener
            public void onClick(View v) {
                KeyboardService.this.showKeyboardView();
            }
        });
        linearLayout.addView(btnBack);
        scrollView.addView(linearLayout);
        this.menuLayout.addView(scrollView);
        updateMenuButtonsHeight(this.menuLayout, getButtonHeight(55));
        applyKeyboardTransparency(this.keyboardTransparency);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showCoordinatesByCategory(String category) {
        ScrollView scrollView;
        ScrollView scrollView2;
        TextView title;
        int row;
        int rows;
        String[][] coordinates;
        if (this.menuLayout == null) {
            return;
        }
        this.menuLayout.removeAllViews();
        int i = 0;
        this.menuLayout.setVisibility(0);
        ScrollView scrollView3 = new ScrollView(this);
        int i2 = -1;
        scrollView3.setLayoutParams(new LinearLayout.LayoutParams(-1, -1));
        scrollView3.setFillViewport(true);
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        linearLayout.setPadding(5, 5, 5, 5);
        linearLayout.setBackground(GlassUI.glassSelector(getApplicationContext(), -15066598));
        TextView title2 = new TextView(this);
        title2.setText("" + category + "");
        title2.setTextColor(-1);
        title2.setTextSize(getFontMultiplier() * 14.0f);
        title2.setGravity(17);
        title2.setPadding(5, 10, 5, 10);
        linearLayout.addView(title2);
        String[][] coordinates2 = getCoordinatesByCategory(category);
        int buttonHeight = getButtonHeight(70);
        float buttonTextSize = getFontMultiplier() * 11.0f;
        if (coordinates2.length == 0) {
            TextView tv = new TextView(this);
            tv.setText("Нет координат в этой категории");
            tv.setTextColor(-256);
            tv.setGravity(17);
            tv.setPadding(10, 50, 10, 50);
            tv.setTextSize(getFontMultiplier() * 12.0f);
            linearLayout.addView(tv);
            scrollView = scrollView3;
        } else {
            int total = coordinates2.length;
            int rows2 = ((total + 2) - 1) / 2;
            int row2 = 0;
            while (row2 < rows2) {
                LinearLayout rowLayout = new LinearLayout(this);
                rowLayout.setOrientation(i);
                rowLayout.setLayoutParams(new LinearLayout.LayoutParams(i2, -2));
                rowLayout.setWeightSum(2);
                int col = 0;
                while (true) {
                    if (col >= 2) {
                        scrollView2 = scrollView3;
                        title = title2;
                        row = row2;
                        rows = rows2;
                        coordinates = coordinates2;
                        break;
                    }
                    int index = (row2 * 2) + col;
                    if (index >= total) {
                        scrollView2 = scrollView3;
                        title = title2;
                        row = row2;
                        rows = rows2;
                        coordinates = coordinates2;
                        break;
                    }
                    String name = coordinates2[index][0];
                    TextView title3 = title2;
                    final String x = coordinates2[index][1];
                    ScrollView scrollView4 = scrollView3;
                    final String y = coordinates2[index][2];
                    final String z = coordinates2[index][3];
                    String[][] coordinates3 = coordinates2;
                    Button btn = new Button(this);
                    btn.setText(name);
                    int row3 = row2;
                    int rows3 = rows2;
                    btn.setLayoutParams(new LinearLayout.LayoutParams(0, buttonHeight, 1.0f));
                    btn.setBackground(GlassUI.glassSelector(getApplicationContext(), -12303292));
                    btn.setTextColor(-1);
                    btn.setPadding(5, 5, 5, 5);
                    btn.setTextSize(buttonTextSize);
                    btn.setMaxLines(2);
                    btn.setGravity(17);
                    btn.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.97
                        @Override // android.view.View.OnClickListener
                        public void onClick(View v) {
                            String command = "/pos" + x + "" + y + "" + z;
                            KeyboardService.this.sendCommandToChat(command);
                            Toast.makeText(KeyboardService.this, "✅ Отправлено: " + command, 0).show();
                            KeyboardService.this.showKeyboardView();
                        }
                    });
                    btn.setOnTouchListener(getButtonTouchListener(-10066330, -12303292));
                    rowLayout.addView(btn);
                    if (col < 2 - 1 && index < total - 1) {
                        View sep = new View(this);
                        sep.setLayoutParams(new LinearLayout.LayoutParams(dpToPx(5.0f), buttonHeight));
                        rowLayout.addView(sep);
                    }
                    col++;
                    coordinates2 = coordinates3;
                    title2 = title3;
                    scrollView3 = scrollView4;
                    row2 = row3;
                    rows2 = rows3;
                }
                linearLayout.addView(rowLayout);
                int row4 = row;
                if (row4 < rows - 1) {
                    View sep2 = new View(this);
                    sep2.setLayoutParams(new LinearLayout.LayoutParams(-1, 5));
                    sep2.setBackground(GlassUI.glassSelector(getApplicationContext(), -13421773));
                    linearLayout.addView(sep2);
                }
                row2 = row4 + 1;
                coordinates2 = coordinates;
                title2 = title;
                scrollView3 = scrollView2;
                rows2 = rows;
                i = 0;
                i2 = -1;
            }
            scrollView = scrollView3;
        }
        Button btnBack = new Button(this);
        btnBack.setText("← Назад");
        btnBack.setLayoutParams(new LinearLayout.LayoutParams(-1, getButtonHeight(55)));
        btnBack.setBackground(GlassUI.glassSelector(getApplicationContext(), -10066330));
        btnBack.setTextColor(-1);
        btnBack.setTextSize(getFontMultiplier() * 14.0f);
        btnBack.setPadding(5, 10, 5, 10);
        btnBack.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.98
            @Override // android.view.View.OnClickListener
            public void onClick(View v) {
                KeyboardService.this.showTeleportMenu();
            }
        });
        linearLayout.addView(btnBack);
        ScrollView scrollView5 = scrollView;
        scrollView5.addView(linearLayout);
        this.menuLayout.addView(scrollView5);
        updateMenuButtonsHeight(this.menuLayout, getButtonHeight(55));
        applyKeyboardTransparency(this.keyboardTransparency);
    }

    private String[][] getCoordinatesByCategory(String category) {
        if (!category.equals("🏛️ Общественные места")) {
            if (!category.equals("🚉 Вокзалы")) {
                if (!category.equals("🚗 Автосалоны / Стоянки")) {
                    if (!category.equals("🏛️ Гос. организации")) {
                        if (!category.equals("👹 Криминальные организации")) {
                            if (!category.equals("👷 Начальные работы")) {
                                if (!category.equals("⚙️ Работы")) {
                                    if (!category.equals("🎭 Развлечения")) {
                                        if (!category.equals("🚁 Транспортные узлы")) {
                                            if (!category.equals("💰 Скупки")) {
                                                if (category.equals("📍 Ближайшие места")) {
                                                    return new String[][]{new String[]{"Ближайший магазин", "200.25", "300.75", "10"}, new String[]{"Ближайшая заправка", "450.50", "150.25", "10"}, new String[]{"Ближайший парк", "100.75", "500.25", "9"}, new String[]{"Ближайшая больница", "350.25", "250.75", "11"}, new String[]{"Ближайший банк", "280.50", "320.25", "10"}};
                                                }
                                                return (String[][]) Array.newInstance((Class<?>) String.class, 0, 0);
                                            }
                                            return new String[][]{new String[]{"Скупка металла", "-1700.25", "300.75", "12"}, new String[]{"Скупка авто", "2000.50", "-1700.25", "15"}, new String[]{"Скупка электроники", "-1200.75", "800.25", "11"}, new String[]{"Ломбард", "750.25", "1100.75", "10"}, new String[]{"Антикварный магазин", "-800.50", "1200.25", "9"}};
                                        }
                                        return new String[][]{new String[]{"Вертолетная Южный", "2800.25", "-2300.75", "25"}, new String[]{"Вертолетная Арзамас", "-300.50", "700.25", "20"}, new String[]{"Морской порт Южный", "2900.75", "-2600.25", "5"}, new String[]{"Яхт-клуб", "-2000.25", "500.75", "3"}, new String[]{"Аэропорт", "3000.50", "-2800.75", "12"}};
                                    }
                                    return new String[][]{new String[]{"Grand Casino", "334.56", "2787.51", "7.84"}, new String[]{"Аукцион контейнеров", "463.24", "-393.23", "8.86"}, new String[]{"Дрифт зона", "2330.98", "1215.69", "19.85"}, new String[]{"Ночной клуб", "1200.25", "-800.75", "12"}, new String[]{"Кинотеатр", "850.50", "950.25", "11"}, new String[]{"Боулинг", "650.75", "-1200.50", "13"}};
                                }
                                return new String[][]{new String[]{"Такси", "762.17", "757.65", "13"}, new String[]{"Водитель автобуса", "783.76", "752.21", "13"}, new String[]{"Почтальон", "794.62", "1351.55", "15"}, new String[]{"Механик", "2150.53", "-1856.45", "19"}, new String[]{"Инкассатор", "1792.96", "-2278.38", "12"}, new String[]{"Дорожная служба Южного", "2649.64", "-1900.44", "23"}, new String[]{"Дорожная служба Арзамаса", "637.71", "899.11", "13"}, new String[]{"Мусоровоз", "-1660.12", "-955.61", "51"}};
                            }
                            return new String[][]{new String[]{"Завод Тесла", "-2541.26", "531.91", "10"}, new String[]{"Завод Твикс", "2264.48", "2150.62", "17"}, new String[]{"Шахта", "-1106.00", "1367.61", "32"}, new String[]{"Ферма", "950.72", "-915.00", "40"}, new String[]{"Сортировочный Центр", "794.62", "1351.55", "15"}, new String[]{"Нефтяная вышка", "-1500.25", "2000.75", "35"}};
                        }
                        return new String[][]{new String[]{"Тамбовская", "2423.09", "-1926.12", "23"}, new String[]{"Курганская", "2285.63", "1368.19", "12"}, new String[]{"Ореховская", "-283.71", "514.44", "14"}, new String[]{"Кавказ", "-2336.44", "31.38", "22"}, new String[]{"Черный рынок", "-2246.85", "240.62", "25"}, new String[]{"Подпольный клуб", "-1800.50", "450.25", "10"}};
                    }
                    return new String[][]{new String[]{"Правительство", "-79.21", "840.72", "18"}, new String[]{"Военная часть", "1897.71", "1722.09", "16"}, new String[]{"Больница Арзамас", "367.82", "1328.02", "12"}, new String[]{"Больница Южный", "2112.90", "-2388.44", "22"}, new String[]{"Новостная Сеть", "2133.40", "-1957.60", "21"}, new String[]{"Полиция Арзамас", "160.97", "1261.24", "13"}, new String[]{"Полиция Южный", "2578.18", "-2416.08", "22"}, new String[]{"ФСБ", "1869.18", "-2001.23", "32"}, new String[]{"Мэрия", "450.25", "1200.75", "15"}};
                }
                return new String[][]{new String[]{"Автосалон Эконом", "2326.82", "-1804.21", "22.45"}, new String[]{"Автосалон Комфорт", "2215.55", "2890.65", "12.13"}, new String[]{"Автосалон Премиум", "601.94", "1002.13", "12.00"}, new String[]{"Мотосалон", "341.13", "480.46", "12.30"}, new String[]{"Стоянка г.Арзамас", "421.53", "586.04", "11.98"}, new String[]{"Стоянка пгт.Батырево", "1749.30", "2464.37", "15.70"}, new String[]{"Стоянка г.Эдово", "-2404.05", "2681.66", "39.16"}, new String[]{"СТО Реактор", "711.90", "525.43", "12.00"}};
            }
            return new String[][]{new String[]{"Вокзал Арзамас", "-560.82", "931.22", "13"}, new String[]{"Вокзал Южный", "2745.63", "-2450.74", "22"}, new String[]{"Вокзал Батырево", "1813.30", "2513.97", "16"}, new String[]{"Вокзал Эдово", "-2476.94", "2838.97", "38"}, new String[]{"ЖД Вокзал Южный", "2498.34", "-2146.55", "23"}, new String[]{"Автовокзал Центральный", "950.25", "650.80", "12"}};
        }
        return new String[][]{new String[]{"Автошкола", "487.36", "2282.68", "13"}, new String[]{"Банк Южный", "2376.49", "-2141.47", "22"}, new String[]{"Банк Арзамас", "-139.42", "593.89", "13"}, new String[]{"Банк Батырево", "1852.34", "2041.30", "16"}, new String[]{"Авторынок", "805.11", "2294.79", "19"}, new String[]{"Почтовый отдел", "794.62", "1351.55", "15"}, new String[]{"Торговый центр", "1200.50", "-1500.25", "18"}, new String[]{"Стадион", "1800.75", "800.30", "14"}};
    }

    private void setupRussianButtons() {
        int i;
        String[] symbols;
        String[] keys = {"й", "ц", "у", "к", "е", "н", "ш", "щ", "з", "х", "ф", "ы", "в", "а", "п", "р", "о", "л", "ж", "э", "я", "ч", "с", "м", "и", "ь", "б", "ю"};
        for (String key : keys) {
            int resId = getResources().getIdentifier("btn" + key + "_ru", "id", getPackageName());
            Button btn = (Button) this.keyboardView.findViewById(resId);
            if (btn != null) {
                btn.setOnClickListener(this);
            }
        }
        Button btnG = (Button) this.keyboardView.findViewById(R.id.btn__ru_res_0x7f02005a);
        Button btnD = (Button) this.keyboardView.findViewById(R.id.btn__ru_res_0x7f02005b);
        Button btnT = (Button) this.keyboardView.findViewById(R.id.btn__ru_res_0x7f020068);
        Button btnE = (Button) this.keyboardView.findViewById(R.id.btne_ru);
        Button[] specialButtons = {btnG, btnD, btnT, btnE};
        for (Button btn2 : specialButtons) {
            if (btn2 != null) {
                btn2.setOnClickListener(this);
            }
        }
        int i2 = 1;
        while (true) {
            if (i2 > 9) {
                break;
            }
            int resId2 = getResources().getIdentifier("btnToggle" + i2 + "_ru", "id", getPackageName());
            Button btn3 = (Button) this.keyboardView.findViewById(resId2);
            if (btn3 != null) {
                btn3.setOnClickListener(this);
            }
            i2++;
        }
        Button btn0 = (Button) this.keyboardView.findViewById(R.id.btnToggle0Top_ru);
        if (btn0 != null) {
            btn0.setOnClickListener(this);
        }
        int i3 = 1;
        for (i = 9; i3 <= i; i = 9) {
            int resId3 = getResources().getIdentifier("btnToggle" + i3 + "Top_ru", "id", getPackageName());
            Button btn4 = (Button) this.keyboardView.findViewById(resId3);
            if (btn4 != null) {
                btn4.setOnClickListener(this);
            }
            i3++;
        }
        Button btn0Top = (Button) this.keyboardView.findViewById(R.id.btnToggle0Top_ru);
        if (btn0Top != null) {
            btn0Top.setOnClickListener(this);
        }
        Button btnToggleSymbolsTop_ru = (Button) this.keyboardView.findViewById(R.id.btnToggleSymbolsTop_ru);
        if (btnToggleSymbolsTop_ru != null) {
            btnToggleSymbolsTop_ru.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.99
                @Override // android.view.View.OnClickListener
                public void onClick(View v) {
                    KeyboardService.this.toggleSymbolsPage();
                }
            });
        }
        Button btnToggleSymbolsTop_ru2 = (Button) this.keyboardView.findViewById(R.id.btnToggleSymbolsTop_ru2);
        if (btnToggleSymbolsTop_ru2 != null) {
            btnToggleSymbolsTop_ru2.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.100
                @Override // android.view.View.OnClickListener
                public void onClick(View v) {
                    KeyboardService.this.toggleSymbolsPage();
                }
            });
        }
        Button btnSpace = (Button) this.keyboardView.findViewById(R.id.btnSpace_ru);
        Button btnBackspace = (Button) this.keyboardView.findViewById(R.id.btnBackspace_ru);
        Button btnEnter = (Button) this.keyboardView.findViewById(R.id.btnEnter_ru);
        Button btnShift = (Button) this.keyboardView.findViewById(R.id.btnShift_ru);
        Button[] specialKeys = {btnSpace, btnBackspace, btnEnter, btnShift};
        int length = specialKeys.length;
        int i4 = 0;
        while (i4 < length) {
            int i5 = length;
            Button btn5 = specialKeys[i4];
            if (btn5 != null) {
                btn5.setOnClickListener(this);
            }
            i4++;
            length = i5;
        }
        if (btnShift != null) {
            btnShift.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.101
                @Override // android.view.View.OnClickListener
                public void onClick(View v) {
                    KeyboardService.this.toggleShift();
                }
            });
        }
        String[] symbols2 = {",", ".", "/", ":", ";", "?", "!", "#"};
        String[] symbolIds = {"Comma", "Period", "Slash", "Colon", "Semicolon", "Question", "Exclamation", "Hash"};
        int i6 = 0;
        while (true) {
            Button btnD2 = btnD;
            Button btnT2 = btnT;
            if (i6 >= symbols2.length) {
                break;
            }
            Button btnE2 = btnE;
            int resId4 = getResources().getIdentifier("btnSymbol" + symbolIds[i6] + "_ru", "id", getPackageName());
            Button btn6 = (Button) this.keyboardView.findViewById(resId4);
            if (btn6 != null) {
                final String symbol = symbols2[i6];
                btn6.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.102
                    @Override // android.view.View.OnClickListener
                    public void onClick(View v) {
                        InputConnection ic = KeyboardService.this.getCurrentInputConnection();
                        if (ic != null) {
                            ic.commitText(symbol, 1);
                        }
                    }
                });
            }
            i6++;
            btnD = btnD2;
            btnT = btnT2;
            btnE = btnE2;
        }
        int i7 = 0;
        while (i7 < symbols2.length) {
            int resId5 = getResources().getIdentifier("btnSymbol" + symbolIds[i7] + "Top_ru", "id", getPackageName());
            Button btn7 = (Button) this.keyboardView.findViewById(resId5);
            if (btn7 == null) {
                symbols = symbols2;
            } else {
                final String symbol2 = symbols2[i7];
                symbols = symbols2;
                btn7.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.103
                    @Override // android.view.View.OnClickListener
                    public void onClick(View v) {
                        InputConnection ic = KeyboardService.this.getCurrentInputConnection();
                        if (ic != null) {
                            ic.commitText(symbol2, 1);
                        }
                    }
                });
            }
            i7++;
            symbols2 = symbols;
        }
    }

    private void setupEnglishButtons() {
        String[] symbols;
        String[] keys = {"q", "w", "e", "r", "t", "y", "u", "i", "o", "p", "a", "s", "d", "f", "g", "h", "j", "k", "l", "z", "x", "c", "v", "b", "n", "m"};
        int i = 0;
        for (String key : keys) {
            int resId = getResources().getIdentifier("btn" + key + "_en", "id", getPackageName());
            Button btn = (Button) this.keyboardView.findViewById(resId);
            if (btn != null) {
                btn.setOnClickListener(this);
            }
        }
        for (int i2 = 1; i2 <= 9; i2++) {
            int resId2 = getResources().getIdentifier("btnToggle" + i2 + "_en", "id", getPackageName());
            Button btn2 = (Button) this.keyboardView.findViewById(resId2);
            if (btn2 != null) {
                btn2.setOnClickListener(this);
            }
        }
        Button btn0 = (Button) this.keyboardView.findViewById(R.id.btnToggle0Top_en);
        if (btn0 != null) {
            btn0.setOnClickListener(this);
        }
        for (int i3 = 1; i3 <= 9; i3++) {
            int resId3 = getResources().getIdentifier("btnToggle" + i3 + "Top_en", "id", getPackageName());
            Button btn3 = (Button) this.keyboardView.findViewById(resId3);
            if (btn3 != null) {
                btn3.setOnClickListener(this);
            }
        }
        Button btn0Top = (Button) this.keyboardView.findViewById(R.id.btnToggle0Top_en);
        if (btn0Top != null) {
            btn0Top.setOnClickListener(this);
        }
        Button btnToggleSymbolsTop_en = (Button) this.keyboardView.findViewById(R.id.btnToggleSymbolsTop_en);
        if (btnToggleSymbolsTop_en != null) {
            btnToggleSymbolsTop_en.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.104
                @Override // android.view.View.OnClickListener
                public void onClick(View v) {
                    KeyboardService.this.toggleSymbolsPage();
                }
            });
        }
        Button btnToggleSymbolsTop_en2 = (Button) this.keyboardView.findViewById(R.id.btnToggleSymbolsTop_en2);
        if (btnToggleSymbolsTop_en2 != null) {
            btnToggleSymbolsTop_en2.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.105
                @Override // android.view.View.OnClickListener
                public void onClick(View v) {
                    KeyboardService.this.toggleSymbolsPage();
                }
            });
        }
        Button btnSpace = (Button) this.keyboardView.findViewById(R.id.btnSpaceEn);
        Button btnBackspace = (Button) this.keyboardView.findViewById(R.id.btnBackspaceEn);
        Button btnEnter = (Button) this.keyboardView.findViewById(R.id.btnEnterEn);
        Button btnShift = (Button) this.keyboardView.findViewById(R.id.btnShiftEn);
        Button[] specialKeys = {btnSpace, btnBackspace, btnEnter, btnShift};
        int length = specialKeys.length;
        while (i < length) {
            String[] keys2 = keys;
            Button btn4 = specialKeys[i];
            if (btn4 != null) {
                btn4.setOnClickListener(this);
            }
            i++;
            keys = keys2;
        }
        if (btnShift != null) {
            btnShift.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.106
                @Override // android.view.View.OnClickListener
                public void onClick(View v) {
                    KeyboardService.this.toggleShift();
                }
            });
        }
        String[] symbols2 = {",", ".", "/", ":", ";", "?", "!", "#"};
        String[] symbolIds = {"Comma", "Period", "Slash", "Colon", "Semicolon", "Question", "Exclamation", "Hash"};
        int i4 = 0;
        while (true) {
            Button btn02 = btn0;
            Button btn0Top2 = btn0Top;
            if (i4 >= symbols2.length) {
                break;
            }
            Button btnToggleSymbolsTop_en3 = btnToggleSymbolsTop_en;
            int resId4 = getResources().getIdentifier("btnSymbol" + symbolIds[i4] + "_en", "id", getPackageName());
            Button btn5 = (Button) this.keyboardView.findViewById(resId4);
            if (btn5 != null) {
                final String symbol = symbols2[i4];
                btn5.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.107
                    @Override // android.view.View.OnClickListener
                    public void onClick(View v) {
                        InputConnection ic = KeyboardService.this.getCurrentInputConnection();
                        if (ic != null) {
                            ic.commitText(symbol, 1);
                        }
                    }
                });
            }
            i4++;
            btn0 = btn02;
            btn0Top = btn0Top2;
            btnToggleSymbolsTop_en = btnToggleSymbolsTop_en3;
        }
        int i5 = 0;
        while (i5 < symbols2.length) {
            int resId5 = getResources().getIdentifier("btnSymbol" + symbolIds[i5] + "Top_en", "id", getPackageName());
            Button btn6 = (Button) this.keyboardView.findViewById(resId5);
            if (btn6 == null) {
                symbols = symbols2;
            } else {
                final String symbol2 = symbols2[i5];
                symbols = symbols2;
                btn6.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.108
                    @Override // android.view.View.OnClickListener
                    public void onClick(View v) {
                        InputConnection ic = KeyboardService.this.getCurrentInputConnection();
                        if (ic != null) {
                            ic.commitText(symbol2, 1);
                        }
                    }
                });
            }
            i5++;
            symbols2 = symbols;
        }
    }

    @Override // android.view.View.OnClickListener
    public void onClick(View v) {
        InputConnection ic = getCurrentInputConnection();
        if (ic != null && (v instanceof Button)) {
            Button btn = (Button) v;
            String text = btn.getText().toString();
            if (text.equals("⌫")) {
                ic.deleteSurroundingText(1, 0);
                return;
            }
            if (text.equals("↵")) {
                sendEnterKeyWithPrefix(ic);
                return;
            }
            if (text.equals("пробел") || text.equals("space")) {
                ic.commitText(" ", 1);
                return;
            }
            if (text.equals("123") || text.equals("АБВ") || text.equals("ABC")) {
                toggleSymbolsPage();
                return;
            }
            if (text.equals("⇧")) {
                toggleShift();
                return;
            }
            if (text.equals("EN") || text.equals("RU")) {
                switchLanguage();
                return;
            }
            if (this.isUpperCase) {
                text = text.toUpperCase();
            }
            ic.commitText(text, 1);
        }
    }

    private void sendEnterKeyWithPrefix(InputConnection ic) {
        int chatMode = FloatingService.currentChatMode;
        int cheatMode = FloatingService.currentCheatMode;
        CharSequence beforeText = ic.getTextBeforeCursor(1000, 0);
        CharSequence afterText = ic.getTextAfterCursor(1000, 0);
        String fullText = beforeText != null ? "" + beforeText.toString() : "";
        if (afterText != null) {
            fullText = fullText + afterText.toString();
        }
        boolean isEmpty = fullText.trim().isEmpty();
        if (beforeText != null && beforeText.length() > 0) {
            ic.deleteSurroundingText(beforeText.length(), 0);
        }
        if (afterText != null && afterText.length() > 0) {
            ic.deleteSurroundingText(0, afterText.length());
        }
        String textToSend = "";
        if (!isEmpty || cheatMode <= 0) {
            if (chatMode == 1) {
                textToSend = "/n " + fullText;
            } else if (chatMode == 2) {
                textToSend = "/a " + fullText;
            } else {
                textToSend = fullText;
            }
        } else {
            switch (cheatMode) {
                case 1:
                    textToSend = "/gm";
                    break;
                case 2:
                    textToSend = "/dl";
                    break;
                case 3:
                    textToSend = "/clist";
                    break;
            }
            Toast.makeText(this, "✅ Отправлено: " + textToSend, 0).show();
            FloatingService.currentCheatMode = 0;
            getSharedPreferences("GTAndroidData", 0).edit().putInt("cheat_mode", 0).apply();
            Intent resetIntent = new Intent("CHEAT_MODE_RESET");
            sendBroadcast(resetIntent);
        }
        if (!textToSend.isEmpty()) {
            ic.commitText(textToSend, 1);
        }
        sendEnterKey(ic);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void toggleShift() {
        this.isUpperCase = !this.isUpperCase;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void switchLanguage() {
        this.isRussian = !this.isRussian;
        LinearLayout russianLayout = (LinearLayout) this.keyboardView.findViewById(R.id.russianLayout);
        LinearLayout englishLayout = (LinearLayout) this.keyboardView.findViewById(R.id.englishLayout);
        if (this.isRussian) {
            russianLayout.setVisibility(0);
            englishLayout.setVisibility(8);
        } else {
            russianLayout.setVisibility(8);
            englishLayout.setVisibility(0);
        }
    }

    private void loadUserButtons(LinearLayout container) {
        container.removeAllViews();
        int count = this.prefs.getInt("btn_count", 0);
        if (count == 0) {
            TextView tv = new TextView(this);
            tv.setText("Нет сохраненных кнопок");
            tv.setTextColor(-1);
            tv.setPadding(10, 10, 10, 10);
            tv.setTextSize(getFontMultiplier() * 12.0f);
            container.addView(tv);
            return;
        }
        int buttonHeight = getButtonHeight(50);
        float textSize = getFontMultiplier() * 12.0f;
        for (int i = 0; i < count; i++) {
            final String text = this.prefs.getString("btn_" + i + "_text", "");
            String name = this.prefs.getString("btn_" + i + "_name", "Кнопка");
            Button btn = new Button(this);
            btn.setText(name);
            btn.setLayoutParams(new LinearLayout.LayoutParams(-1, buttonHeight));
            btn.setBackground(GlassUI.glassSelector(getApplicationContext(), -11184811));
            btn.setTextColor(-1);
            btn.setTextSize(textSize);
            btn.setPadding(15, 12, 15, 12);
            btn.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.KeyboardService.109
                @Override // android.view.View.OnClickListener
                public void onClick(View v) {
                    KeyboardService.this.sendCommandToChat(text);
                }
            });
            container.addView(btn);
        }
    }

    @Override // android.inputmethodservice.InputMethodService, android.inputmethodservice.AbstractInputMethodService, android.app.Service
    public void onDestroy() {
        super.onDestroy();
        if (this.sizeChangeReceiver != null) {
            unregisterReceiver(this.sizeChangeReceiver);
        }
        if (this.transparencyChangeReceiver != null) {
            unregisterReceiver(this.transparencyChangeReceiver);
        }
    }

    private void addSeparator(LinearLayout container) {
        View sep = new View(this);
        sep.setLayoutParams(new LinearLayout.LayoutParams(-1, 2));
        sep.setBackground(GlassUI.glassSelector(getApplicationContext(), -13421773));
        container.addView(sep);
    }
}
