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
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import gtandroid.universal.SecurityManager;
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

/* loaded from: classes2.dex */
public class MainActivity extends Activity {
    private static final String ADS_JSON_URL = "https://www.dropbox.com/scl/fi/09ruiljtbntgf9rgvhvq9/ads.json?rlkey=0w8aip1kjoyv37nt8cb02q1pc&st=yp4oxh66&dl=1";
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

    @Override // android.app.Activity
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.securityManager = new SecurityManager(this);
        this.prefs = getSharedPreferences("GTAndroidData", 0);
        loadCustomButtons();
        showLoadingSpinner();
        checkSecurityAndStart();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showLoadingSpinner() {
        this.loadingDialog = new ProgressDialog(this);
        this.loadingDialog.setTitle("Universal GTAndroid");
        this.loadingDialog.setMessage("Проверка обновлений...");
        this.loadingDialog.setCancelable(false);
        this.loadingDialog.show();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void hideLoadingSpinner() {
        if (this.loadingDialog != null && this.loadingDialog.isShowing()) {
            this.loadingDialog.dismiss();
        }
    }

    private void checkSecurityAndStart() {
        this.securityManager.checkAccess(new SecurityManager.SecurityCallback() { // from class: gtandroid.universal.MainActivity.1
            @Override // gtandroid.universal.SecurityManager.SecurityCallback
            public void onResult(boolean isAllowed, String message) {
                MainActivity.this.hideLoadingSpinner();
                if (isAllowed) {
                    MainActivity.this.initUI();
                    return;
                }
                if ("PASSWORD_REQUIRED".equals(message)) {
                    MainActivity.this.showPasswordDialog();
                    return;
                }
                if (message != null && message.startsWith("UPDATE_REQUIRED:")) {
                    String downloadUrl = message.substring("UPDATE_REQUIRED:".length());
                    MainActivity.this.showUpdateDialog(downloadUrl);
                    return;
                }
                if (message != null && message.startsWith("ERROR:")) {
                    String error = message.substring("ERROR:".length());
                    MainActivity.this.showErrorDialog(error);
                    return;
                }
                AlertDialog.Builder errorBuilder = new AlertDialog.Builder(MainActivity.this, R.style.GlassDialogTheme);
                errorBuilder.setTitle("Доступ запрещен");
                errorBuilder.setMessage(message);
                errorBuilder.setCancelable(false);
                errorBuilder.setPositiveButton("Закрыть", new DialogInterface.OnClickListener() { // from class: gtandroid.universal.MainActivity.1.1
                    @Override // android.content.DialogInterface.OnClickListener
                    public void onClick(DialogInterface dialog, int which) {
                        MainActivity.this.finish();
                    }
                });
                errorBuilder.show();
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showErrorDialog(String error) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.GlassDialogTheme);
        builder.setTitle("Ошибка");
        builder.setMessage(error);
        builder.setCancelable(false);
        builder.setPositiveButton("Закрыть", new DialogInterface.OnClickListener() { // from class: gtandroid.universal.MainActivity.2
            @Override // android.content.DialogInterface.OnClickListener
            public void onClick(DialogInterface dialog, int which) {
                MainActivity.this.finish();
            }
        });
        builder.show();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showPasswordDialog() {
        final EditText input = new EditText(this);
        input.setBackground(GlassUI.lightCard(this, 10.0f));
        input.setPadding(24, 18, 24, 18);
        input.setTextColor(-14935010);
        input.setHintTextColor(-7434605);
        input.setHint("Введите пароль");
        input.setInputType(129);
        int attempts = this.securityManager.getAttemptsLeft();
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.GlassDialogTheme);
        builder.setTitle("Ввод пароля (Осталось попыток: " + attempts + ")");
        builder.setView(input);
        builder.setPositiveButton("Войти", new DialogInterface.OnClickListener() { // from class: gtandroid.universal.MainActivity.3
            @Override // android.content.DialogInterface.OnClickListener
            public void onClick(DialogInterface dialog, int which) {
                String password = input.getText().toString();
                if (!password.isEmpty()) {
                    MainActivity.this.showLoadingSpinner();
                    MainActivity.this.loadingDialog.setMessage("Проверка пароля...");
                    MainActivity.this.securityManager.verifyPassword(password, new SecurityManager.SecurityCallback() { // from class: gtandroid.universal.MainActivity.3.1
                        @Override // gtandroid.universal.SecurityManager.SecurityCallback
                        public void onResult(boolean isAllowed, String message) {
                            MainActivity.this.hideLoadingSpinner();
                            if (isAllowed) {
                                Toast.makeText(MainActivity.this, "Успешный вход!", 0).show();
                                MainActivity.this.initUI();
                                return;
                            }
                            Toast.makeText(MainActivity.this, message, 1).show();
                            if (MainActivity.this.securityManager.getAttemptsLeft() > 0) {
                                MainActivity.this.showPasswordDialog();
                            } else {
                                MainActivity.this.finish();
                            }
                        }
                    });
                } else {
                    Toast.makeText(MainActivity.this, "Введите пароль", 0).show();
                    MainActivity.this.showPasswordDialog();
                }
            }
        });
        builder.setNegativeButton("Отмена", new DialogInterface.OnClickListener() { // from class: gtandroid.universal.MainActivity.4
            @Override // android.content.DialogInterface.OnClickListener
            public void onClick(DialogInterface dialog, int which) {
                MainActivity.this.finish();
            }
        });
        builder.setCancelable(false);
        builder.show();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showUpdateDialog(final String downloadUrl) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.GlassDialogTheme);
        builder.setTitle("Доступно обновление");
        builder.setMessage("Найдена новая версия приложения. Необходимо обновиться для продолжения работы.");
        builder.setCancelable(false);
        builder.setPositiveButton("Скачать и установить", new DialogInterface.OnClickListener() { // from class: gtandroid.universal.MainActivity.5
            @Override // android.content.DialogInterface.OnClickListener
            public void onClick(DialogInterface dialog, int which) {
                Intent browserIntent = new Intent("android.intent.action.VIEW", Uri.parse(downloadUrl));
                MainActivity.this.startActivity(browserIntent);
                MainActivity.this.finish();
            }
        });
        builder.setNegativeButton("Выход", new DialogInterface.OnClickListener() { // from class: gtandroid.universal.MainActivity.6
            @Override // android.content.DialogInterface.OnClickListener
            public void onClick(DialogInterface dialog, int which) {
                MainActivity.this.finish();
            }
        });
        builder.show();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void initUI() {
        this.mainContainer = new LinearLayout(this);
        this.mainContainer.setOrientation(1);
        this.mainContainer.setBackgroundColor(-855305);
        this.mainContainer.setPadding(16, 0, 16, 16);
        setContentView(this.mainContainer);
        showMainScreen();
        loadAdsInBackground();
    }

    @Override // android.app.Activity
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override // android.app.Activity
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.menu_main) {
            showMainScreen();
            return true;
        }
        if (id == R.id.menu_editor) {
            showEditorScreen();
            return true;
        }
        if (id == R.id.menu_settings) {
            showSettingsScreen();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showMainScreen() {
        mainContainer.removeAllViews();
        final float d = getResources().getDisplayMetrics().density;
        final int bg = android.graphics.Color.rgb(10,8,15);
        final int card = android.graphics.Color.rgb(24,20,32);
        final int white = android.graphics.Color.rgb(245,243,250);
        final int gray = android.graphics.Color.rgb(160,153,175);
        final int purple = android.graphics.Color.rgb(124,77,255);
        final int green = android.graphics.Color.rgb(48,209,88);
        mainContainer.setOrientation(LinearLayout.VERTICAL);
        mainContainer.setBackgroundColor(bg);
        mainContainer.setPadding(dp(14),dp(8),dp(14),dp(8));

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(content);
        mainContainer.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView hello = new TextView(this);
        hello.setText("Привет, Roman 👋"); hello.setTextColor(white); hello.setTextSize(25); hello.setTypeface(null,android.graphics.Typeface.BOLD);
        header.addView(hello,new LinearLayout.LayoutParams(0,-2,1));
        Button profile = new Button(this); profile.setText("R"); profile.setTextColor(white); profile.setTextSize(17); profile.setAllCaps(false);
        GradientDrawable pbg=new GradientDrawable(); pbg.setColor(purple); pbg.setShape(GradientDrawable.OVAL); profile.setBackground(pbg);
        header.addView(profile,new LinearLayout.LayoutParams(dp(48),dp(48)));
        profile.setOnClickListener(v -> showSettingsScreen());
        content.addView(header,new LinearLayout.LayoutParams(-1,dp(58)));

        TextView sub=new TextView(this); sub.setText("Добро пожаловать в RTools"); sub.setTextColor(gray); sub.setTextSize(13); content.addView(sub);
        addSpace(content,14);

        LinearLayout status=new LinearLayout(this); status.setOrientation(LinearLayout.VERTICAL); status.setPadding(dp(18),dp(15),dp(18),dp(15));
        GradientDrawable sbg=new GradientDrawable(); sbg.setColor(card); sbg.setCornerRadius(dp(20)); sbg.setStroke(dp(1),android.graphics.Color.rgb(52,43,67)); status.setBackground(sbg);
        LinearLayout sr=new LinearLayout(this); sr.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView st=new TextView(this); st.setText("Статус приложения"); st.setTextColor(white); st.setTextSize(16); st.setTypeface(null,android.graphics.Typeface.BOLD); sr.addView(st,new LinearLayout.LayoutParams(0,-2,1));
        TextView online=new TextView(this); online.setText("● ONLINE"); online.setTextColor(green); online.setTextSize(11); online.setTypeface(null,android.graphics.Typeface.BOLD); sr.addView(online); status.addView(sr);
        TextView access=new TextView(this); access.setText("RTools готов к работе"); access.setTextColor(gray); access.setTextSize(13); access.setPadding(0,dp(7),0,0); status.addView(access);
        content.addView(status,new LinearLayout.LayoutParams(-1,-2));
        addSpace(content,18);

        addSectionTitle(content,"Быстрые действия");
        Button keyboard=createModernButton("⌨  Клавиатура","Открыть настройки клавиатур",purple); content.addView(keyboard,new LinearLayout.LayoutParams(-1,dp(70)));
        keyboard.setOnClickListener(v -> { try { startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)); } catch(Exception e) { Toast.makeText(this,"Не удалось открыть настройки",Toast.LENGTH_SHORT).show(); }});
        addSpace(content,10);
        Button floating=createModernButton("⚡  Плавающее меню",isFloatingMenuEnabled?"Сейчас включено":"Сейчас выключено",isFloatingMenuEnabled?green:card); content.addView(floating,new LinearLayout.LayoutParams(-1,dp(70)));
        floating.setOnClickListener(v -> { toggleFloatingMenu(); showMainScreen(); });

        addSpace(content,18); addSectionTitle(content,"Инструменты");
        Button editor=createModernButton("📝  Редактор","Наказания, ответы, кнопки и FamWar",purple); content.addView(editor,new LinearLayout.LayoutParams(-1,dp(70))); editor.setOnClickListener(v -> showEditorScreen());
        addSpace(content,10);
        Button settings=createModernButton("⚙  Настройки","Размер, прозрачность, импорт и экспорт",card); content.addView(settings,new LinearLayout.LayoutParams(-1,dp(70))); settings.setOnClickListener(v -> showSettingsScreen());
        addSpace(content,18); addSectionTitle(content,"Новости и партнёры");
        HorizontalScrollView ads=new HorizontalScrollView(this); ads.setHorizontalScrollBarEnabled(false); LinearLayout adsRow=new LinearLayout(this); adsRow.setOrientation(LinearLayout.HORIZONTAL); TextView loading=new TextView(this); loading.setText("Загрузка новостей..."); loading.setTextColor(gray); loading.setGravity(android.view.Gravity.CENTER); GradientDrawable lbg=new GradientDrawable(); lbg.setColor(card); lbg.setCornerRadius(dp(18)); loading.setBackground(lbg); adsRow.addView(loading,new LinearLayout.LayoutParams(dp(230),dp(150))); ads.addView(adsRow); content.addView(ads,new LinearLayout.LayoutParams(-1,dp(160)));
        TextView version=new TextView(this); version.setText("Universal GTAndroid • RTools"); version.setTextColor(android.graphics.Color.rgb(100,94,112)); version.setTextSize(11); version.setGravity(android.view.Gravity.CENTER); content.addView(version,new LinearLayout.LayoutParams(-1,dp(35)));

        LinearLayout nav=new LinearLayout(this); nav.setGravity(android.view.Gravity.CENTER); nav.setPadding(dp(5),dp(4),dp(5),dp(4)); GradientDrawable nbg=new GradientDrawable(); nbg.setColor(android.graphics.Color.rgb(20,16,27)); nbg.setCornerRadius(dp(20)); nav.setBackground(nbg); mainContainer.addView(nav,new LinearLayout.LayoutParams(-1,dp(62)));
        Button home=createNavButton("⌂\nГлавная",purple); Button edit=createNavButton("📝\nРедактор",gray); Button set=createNavButton("⚙\nНастройки",gray); nav.addView(home,new LinearLayout.LayoutParams(0,-1,1)); nav.addView(edit,new LinearLayout.LayoutParams(0,-1,1)); nav.addView(set,new LinearLayout.LayoutParams(0,-1,1)); edit.setOnClickListener(v -> showEditorScreen()); set.setOnClickListener(v -> showSettingsScreen());
    }

    private int dp(int value) { return (int)(value * getResources().getDisplayMetrics().density + 0.5f); }

    private void addSpace(LinearLayout parent,int heightDp) { View v=new View(this); parent.addView(v,new LinearLayout.LayoutParams(-1,dp(heightDp))); }

    private void addSectionTitle(LinearLayout parent,String title) { TextView t=new TextView(this); t.setText(title); t.setTextColor(android.graphics.Color.rgb(245,243,250)); t.setTextSize(19); t.setTypeface(null,android.graphics.Typeface.BOLD); t.setPadding(dp(3),dp(3),0,dp(9)); parent.addView(t,new LinearLayout.LayoutParams(-1,-2)); }

    private Button createModernButton(String title,String subtitle,int accent) {
        Button b=new Button(this); b.setAllCaps(false); b.setGravity(android.view.Gravity.CENTER_VERTICAL); b.setText(title+"\n"+"       "+subtitle); b.setTextColor(android.graphics.Color.rgb(245,243,250)); b.setTextSize(14); b.setTypeface(null,android.graphics.Typeface.BOLD); b.setPadding(dp(16),0,dp(16),0); GradientDrawable g=new GradientDrawable(); g.setColor(accent==android.graphics.Color.rgb(124,77,255)?android.graphics.Color.rgb(38,29,56):accent==android.graphics.Color.rgb(48,209,88)?android.graphics.Color.rgb(24,49,33):accent); g.setCornerRadius(dp(19)); g.setStroke(dp(1),android.graphics.Color.rgb(54,46,67)); b.setBackground(g); return b;
    }

    private Button createNavButton(String text,int color) { Button b=new Button(this); b.setText(text); b.setTextColor(color); b.setTextSize(10); b.setAllCaps(false); b.setGravity(android.view.Gravity.CENTER); b.setBackgroundColor(android.graphics.Color.TRANSPARENT); return b; }

    private void loadAdsInBackground() {
        new Thread(new Runnable() { // from class: gtandroid.universal.MainActivity.9
            @Override // java.lang.Runnable
            public void run() {
                try {
                    String jsonContent = MainActivity.this.downloadString(MainActivity.ADS_JSON_URL);
                    if (jsonContent != null && !jsonContent.isEmpty()) {
                        final JSONArray adsArray = new JSONArray(jsonContent);
                        MainActivity.this.runOnUiThread(new Runnable() { // from class: gtandroid.universal.MainActivity.9.1
                            @Override // java.lang.Runnable
                            public void run() {
                                MainActivity.this.displayAds(adsArray);
                            }
                        });
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void displayAds(JSONArray adsArray) {
        HorizontalScrollView hsv;
        String str;
        ImageView imgView;
        String str2 = "";
        int i = 1;
        int i2 = this.mainContainer.getChildCount() - 1;
        while (true) {
            if (i2 < 0) {
                hsv = null;
                break;
            }
            View child = this.mainContainer.getChildAt(i2);
            if (!(child instanceof HorizontalScrollView)) {
                i2--;
            } else {
                HorizontalScrollView hsv2 = (HorizontalScrollView) child;
                hsv = hsv2;
                break;
            }
        }
        if (hsv == null) {
            return;
        }
        int i3 = 0;
        LinearLayout linearLayout = (LinearLayout) hsv.getChildAt(0);
        linearLayout.removeAllViews();
        if (adsArray.length() == 0) {
            TextView noAds = new TextView(this);
            noAds.setText("Нет новостей");
            noAds.setTextColor(-7829368);
            noAds.setPadding(50, 150, 50, 150);
            linearLayout.addView(noAds);
            return;
        }
        int cardWidth = (int) (getResources().getDisplayMetrics().density * 200.0f);
        int i4 = 0;
        while (i4 < adsArray.length()) {
            try {
                JSONObject ad = adsArray.getJSONObject(i4);
                final String link = ad.optString("link", str2);
                String imageUrl = ad.optString("image", str2);
                String text = ad.optString("text", "Новости");
                LinearLayout adCard = new LinearLayout(this);
                adCard.setOrientation(i);
                adCard.setLayoutParams(new LinearLayout.LayoutParams(cardWidth, -1));
                adCard.setPadding(5, 5, 5, 5);
                GradientDrawable bg = new GradientDrawable();
                bg.setColor(-1);
                bg.setCornerRadius(getResources().getDisplayMetrics().density * 14.0f);
                bg.setStroke(i, 335544320);
                adCard.setBackground(bg);
                LinearLayout.LayoutParams adCardParams = (LinearLayout.LayoutParams) adCard.getLayoutParams();
                adCardParams.setMargins(6, i3, 6, i3);
                adCard.setLayoutParams(adCardParams);
                adCard.setClickable(true);
                if (!link.isEmpty()) {
                    adCard.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.MainActivity.10
                        @Override // android.view.View.OnClickListener
                        public void onClick(View v) {
                            try {
                                Intent intent = new Intent("android.intent.action.VIEW", Uri.parse(link));
                                MainActivity.this.startActivity(intent);
                            } catch (Exception e) {
                                Toast.makeText(MainActivity.this, "Не удалось открыть ссылку", 0).show();
                            }
                        }
                    });
                }
                if (imageUrl.isEmpty()) {
                    str = str2;
                } else {
                    try {
                        imgView = new ImageView(this);
                        str = str2;
                    } catch (Exception e) {
                        imgView = new ImageView(this);
                        str = str2;
                    }
                    try {
                        imgView.setLayoutParams(new LinearLayout.LayoutParams(-1, 250));
                        imgView.setScaleType(ImageView.ScaleType.FIT_XY);
                        try {
                            imgView.setAdjustViewBounds(false);
                            loadImageAsync(imageUrl, imgView);
                            adCard.addView(imgView);
                        } catch (Exception e2) {
                            e2.printStackTrace();
                            i4++;
                            str2 = str;
                            i = 1;
                            i3 = 0;
                        }
                    } catch (Exception e3) {
                        e3.printStackTrace();
                        i4++;
                        str2 = str;
                        i = 1;
                        i3 = 0;
                    }
                }
                TextView tvText = new TextView(this);
                tvText.setText(text);
                tvText.setTextSize(12.0f);
                tvText.setTextColor(-13421773);
                tvText.setGravity(17);
                tvText.setPadding(8, 10, 8, 10);
                tvText.setMaxLines(2);
                tvText.setEllipsize(TextUtils.TruncateAt.END);
                adCard.addView(tvText);
                linearLayout.addView(adCard);
                if (i4 < adsArray.length() - 1) {
                    try {
                        View spacer = new View(this);
                        spacer.setLayoutParams(new LinearLayout.LayoutParams(8, -1));
                        linearLayout.addView(spacer);
                    } catch (Exception e4) {
                        e4.printStackTrace();
                        i4++;
                        str2 = str;
                        i = 1;
                        i3 = 0;
                    }
                }
            } catch (Exception e5) {
                str = str2;
            }
            i4++;
            str2 = str;
            i = 1;
            i3 = 0;
        }
    }

    private void loadImageAsync(final String url, final ImageView imageView) {
        new Thread(new Runnable() { // from class: gtandroid.universal.MainActivity.11
            @Override // java.lang.Runnable
            public void run() {
                try {
                    InputStream is = (InputStream) new URL(url).getContent();
                    final Bitmap bitmap = BitmapFactory.decodeStream(is);
                    if (bitmap != null) {
                        MainActivity.this.runOnUiThread(new Runnable() { // from class: gtandroid.universal.MainActivity.11.1
                            @Override // java.lang.Runnable
                            public void run() {
                                imageView.setImageBitmap(bitmap);
                            }
                        });
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public String downloadString(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        conn.setRequestMethod("GET");
        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder sb = new StringBuilder();
        while (true) {
            String line = in.readLine();
            if (line != null) {
                sb.append(line).append("\n");
            } else {
                in.close();
                return sb.toString();
            }
        }
    }

    private void updateToggleButtonStyle() {
        int accent = this.isFloatingMenuEnabled ? -13318311 : -50384;
        this.btnToggleMenu.setBackground(GlassUI.glassOval(this, accent));
        this.btnToggleMenu.setText(this.isFloatingMenuEnabled ? "ON" : "OFF");
        this.btnToggleMenu.setTextColor(-1);
        this.btnToggleMenu.setTextSize(26.0f);
        this.btnToggleMenu.setTypeface(null, 1);
        this.btnToggleMenu.setGravity(17);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void toggleFloatingMenu() {
        if (this.isFloatingMenuEnabled) {
            stopService(new Intent(this, (Class<?>) FloatingService.class));
            this.isFloatingMenuEnabled = false;
        } else if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent("android.settings.action.MANAGE_OVERLAY_PERMISSION", Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, REQUEST_OVERLAY);
        } else {
            startService(new Intent(this, (Class<?>) FloatingService.class));
            this.isFloatingMenuEnabled = true;
        }
        updateToggleButtonStyle();
    }

    @Override // android.app.Activity
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_OVERLAY) {
            if (Settings.canDrawOverlays(this)) {
                startService(new Intent(this, (Class<?>) FloatingService.class));
                this.isFloatingMenuEnabled = true;
                updateToggleButtonStyle();
                Toast.makeText(this, "Плавающее меню включено", 0).show();
                return;
            }
            return;
        }
        if (resultCode == -1 && data != null && data.getData() != null) {
            Uri uri = data.getData();
            if (requestCode == REQUEST_IMPORT) {
                importFromFile(uri);
            } else if (requestCode == REQUEST_EXPORT) {
                exportToFile(uri);
            }
        }
    }

    private void showEditorScreen() {
        this.mainContainer.removeAllViews();
        TextView header = new TextView(this);
        header.setText("Редактор");
        header.setTextSize(24.0f);
        header.setTextColor(-14935010);
        header.setTypeface(null, 1);
        header.setGravity(17);
        header.setPadding(0, 30, 0, 30);
        this.mainContainer.addView(header);
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(1);
        content.setPadding(30, 0, 30, 30);
        scroll.addView(content);
        this.mainContainer.addView(scroll);
        createEditorButton(content, "📝 Редактор наказаний", -16087809, new View.OnClickListener() { // from class: gtandroid.universal.MainActivity.12
            @Override // android.view.View.OnClickListener
            public void onClick(View v) {
                MainActivity.this.showPunishmentEditor();
            }
        });
        createEditorButton(content, "💬 Редактор быстрого ответа", -13318311, new View.OnClickListener() { // from class: gtandroid.universal.MainActivity.13
            @Override // android.view.View.OnClickListener
            public void onClick(View v) {
                MainActivity.this.showQuickAnswersEditor();
            }
        });
        createEditorButton(content, "🔘 Редактор обычный", -27392, new View.OnClickListener() { // from class: gtandroid.universal.MainActivity.14
            @Override // android.view.View.OnClickListener
            public void onClick(View v) {
                MainActivity.this.showNormalEditor();
            }
        });
        createEditorButton(content, "⚔️ Редактор FamWar", -5287202, new View.OnClickListener() { // from class: gtandroid.universal.MainActivity.15
            @Override // android.view.View.OnClickListener
            public void onClick(View v) {
                MainActivity.this.showFamWarEditor();
            }
        });
    }

    private void createEditorButton(LinearLayout parent, String text, int color, View.OnClickListener listener) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        btn.setPadding(20, 25, 20, 25);
        btn.setTextSize(16.0f);
        btn.setTextColor(color);
        btn.setTypeface(null, 1);
        btn.setAllCaps(false);
        btn.setBackground(GlassUI.lightAccentButton(this, color));
        btn.setOnClickListener(listener);
        parent.addView(btn);
        View space = new View(this);
        space.setLayoutParams(new LinearLayout.LayoutParams(-1, 20));
        parent.addView(space);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showSettingsScreen() {
        this.mainContainer.removeAllViews();
        TextView header = new TextView(this);
        header.setText("Настройки");
        header.setTextSize(24.0f);
        header.setTextColor(-14935010);
        header.setTypeface(null, 1);
        header.setGravity(17);
        header.setPadding(0, 30, 0, 30);
        this.mainContainer.addView(header);
        ScrollView scrollView = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(1);
        content.setPadding(30, 0, 30, 30);
        scrollView.addView(content);
        this.mainContainer.addView(scrollView);
        addSectionTitle(content, "Клавиатура");
        createSettingButton(content, "📏 Размер клавиатуры", -5287202, new View.OnClickListener() { // from class: gtandroid.universal.MainActivity.16
            @Override // android.view.View.OnClickListener
            public void onClick(View v) {
                MainActivity.this.showKeyboardSizeDialog();
            }
        });
        createSettingButton(content, "🎨 Прозрачность клавиатуры", -10987818, new View.OnClickListener() { // from class: gtandroid.universal.MainActivity.17
            @Override // android.view.View.OnClickListener
            public void onClick(View v) {
                MainActivity.this.showTransparencyDialog();
            }
        });
        addSectionTitle(content, "Экспорт/Импорт");
        createSettingButton(content, "📥 Импорт CFG", -16087809, new View.OnClickListener() { // from class: gtandroid.universal.MainActivity.18
            @Override // android.view.View.OnClickListener
            public void onClick(View v) {
                MainActivity.this.importConfig();
            }
        });
        createSettingButton(content, "📤 Экспорт CFG", -13318311, new View.OnClickListener() { // from class: gtandroid.universal.MainActivity.19
            @Override // android.view.View.OnClickListener
            public void onClick(View v) {
                MainActivity.this.exportConfig();
            }
        });
        addSectionTitle(content, "Админ лвл");
        int currentLevel = getIntPref("admin_level", 1);
        Button btnAdmin = new Button(this);
        btnAdmin.setText("👑 Уровень администратора: " + currentLevel);
        btnAdmin.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        btnAdmin.setPadding(20, 20, 20, 20);
        btnAdmin.setTextSize(16.0f);
        btnAdmin.setTextColor(-27392);
        btnAdmin.setTypeface(null, 1);
        btnAdmin.setAllCaps(false);
        btnAdmin.setBackground(GlassUI.lightAccentButton(this, -27392));
        btnAdmin.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.MainActivity.20
            @Override // android.view.View.OnClickListener
            public void onClick(View v) {
                MainActivity.this.showAdminLevelDialog();
            }
        });
        content.addView(btnAdmin);
    }

    private void createSettingButton(LinearLayout parent, String text, int color, View.OnClickListener listener) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        btn.setPadding(20, 15, 20, 15);
        btn.setTextSize(16.0f);
        btn.setTextColor(color);
        btn.setTypeface(null, 1);
        btn.setAllCaps(false);
        btn.setBackground(GlassUI.lightAccentButton(this, color));
        btn.setOnClickListener(listener);
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
                    String strValue = this.prefs.getString(key, String.valueOf(defaultValue));
                    return Integer.parseInt(strValue);
                }
            }
        } catch (Exception e2) {
            e2.printStackTrace();
        }
        return defaultValue;
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showPunishmentEditor() {
        final String[] punishmentTypes = {"Кик", "Бан", "Варн", "Джаил", "Мут", "UnБан", "UnВарн", "UnДжаил", "UnМут", "RМут", "UnRМут"};
        new AlertDialog.Builder(this, R.style.GlassDialogTheme).setTitle("ВЫБЕРИТЕ ТИП").setItems(punishmentTypes, new DialogInterface.OnClickListener() { // from class: gtandroid.universal.MainActivity.21
            @Override // android.content.DialogInterface.OnClickListener
            public void onClick(DialogInterface dialog, int which) {
                MainActivity.this.showPunishmentTypeEditor(punishmentTypes[which]);
            }
        }).show();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showPunishmentTypeEditor(final String typeName) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(1);
        layout.setPadding(20, 20, 20, 20);
        Button btnAdd = new Button(this);
        btnAdd.setText("➕ Добавить");
        btnAdd.setBackground(GlassUI.glassSelector(getApplicationContext(), -16733696));
        btnAdd.setTextColor(-1);
        btnAdd.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.MainActivity.22
            @Override // android.view.View.OnClickListener
            public void onClick(View v) {
                MainActivity.this.showAddPunishmentDialog(typeName);
            }
        });
        layout.addView(btnAdd);
        Button btnList = new Button(this);
        btnList.setText("📋 Список");
        btnList.setBackground(GlassUI.glassSelector(getApplicationContext(), -16750900));
        btnList.setTextColor(-1);
        btnList.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.MainActivity.23
            @Override // android.view.View.OnClickListener
            public void onClick(View v) {
                MainActivity.this.showPunishmentsListByType(typeName);
            }
        });
        layout.addView(btnList);
        new AlertDialog.Builder(this, R.style.GlassDialogTheme).setTitle("Редактор: " + typeName).setView(layout).setNegativeButton("Закрыть", (DialogInterface.OnClickListener) null).show();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showAddPunishmentDialog(final String typeName) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(1);
        layout.setPadding(20, 20, 20, 20);
        final EditText etName = new EditText(this);
        etName.setBackground(GlassUI.lightCard(this, 10.0f));
        etName.setPadding(24, 18, 24, 18);
        etName.setTextColor(-14935010);
        etName.setHintTextColor(-7434605);
        etName.setHint("Название (например: Помеха)");
        layout.addView(etName);
        final EditText etCommand = new EditText(this);
        etCommand.setBackground(GlassUI.lightCard(this, 10.0f));
        etCommand.setPadding(24, 18, 24, 18);
        etCommand.setTextColor(-14935010);
        etCommand.setHintTextColor(-7434605);
        etCommand.setHint("Команда (например: /kick)");
        layout.addView(etCommand);
        final EditText etTime = new EditText(this);
        etTime.setBackground(GlassUI.lightCard(this, 10.0f));
        etTime.setPadding(24, 18, 24, 18);
        etTime.setTextColor(-14935010);
        etTime.setHintTextColor(-7434605);
        etTime.setHint("Время (если нужно)");
        layout.addView(etTime);
        final EditText etReason = new EditText(this);
        etReason.setBackground(GlassUI.lightCard(this, 10.0f));
        etReason.setPadding(24, 18, 24, 18);
        etReason.setTextColor(-14935010);
        etReason.setHintTextColor(-7434605);
        etReason.setHint("Причина (например: 5.2 ОП)");
        layout.addView(etReason);
        final EditText etMinLevel = new EditText(this);
        etMinLevel.setBackground(GlassUI.lightCard(this, 10.0f));
        etMinLevel.setPadding(24, 18, 24, 18);
        etMinLevel.setTextColor(-14935010);
        etMinLevel.setHintTextColor(-7434605);
        etMinLevel.setHint("Мин. уровень (1-4)");
        etMinLevel.setInputType(2);
        layout.addView(etMinLevel);
        new AlertDialog.Builder(this, R.style.GlassDialogTheme).setTitle("Добавить: " + typeName).setView(layout).setPositiveButton("Сохранить", new DialogInterface.OnClickListener() { // from class: gtandroid.universal.MainActivity.24
            @Override // android.content.DialogInterface.OnClickListener
            public void onClick(DialogInterface dialog, int which) {
                String name = etName.getText().toString().trim();
                String command = etCommand.getText().toString().trim();
                String time = etTime.getText().toString().trim();
                String reason = etReason.getText().toString().trim();
                String minLevel = etMinLevel.getText().toString().trim();
                if (!name.isEmpty() && !command.isEmpty()) {
                    if (minLevel.isEmpty()) {
                        minLevel = "1";
                    }
                    MainActivity.this.savePunishment(typeName, name, command, time, reason, minLevel);
                    Toast.makeText(MainActivity.this, "✅ Сохранено!", 0).show();
                    return;
                }
                Toast.makeText(MainActivity.this, "Заполните название и команду!", 0).show();
            }
        }).setNegativeButton("Отмена", (DialogInterface.OnClickListener) null).show();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void savePunishment(String type, String name, String command, String time, String reason, String minLevel) {
        try {
            String key = "punishment_" + type.toLowerCase();
            JSONArray jsonArray = new JSONArray(this.prefs.getString(key, "[]"));
            JSONObject obj = new JSONObject();
            obj.put("name", name);
            obj.put("command", command);
            obj.put("time", time);
            obj.put("reason", reason);
            obj.put("minlevel", minLevel);
            jsonArray.put(obj);
            this.prefs.edit().putString(key, jsonArray.toString()).apply();
        } catch (Exception e) {
            Toast.makeText(this, "Ошибка: " + e.getMessage(), 0).show();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showPunishmentsListByType(final String typeName) {
        String str = "\n";
        try {
            String key = "punishment_" + typeName.toLowerCase();
            String json = this.prefs.getString(key, "[]");
            JSONArray jsonArray = new JSONArray(json);
            if (jsonArray.length() != 0) {
                ScrollView scrollView = new ScrollView(this);
                LinearLayout container = new LinearLayout(this);
                container.setOrientation(1);
                container.setPadding(10, 10, 10, 10);
                int i = 0;
                while (i < jsonArray.length()) {
                    final int index = i;
                    JSONObject obj = jsonArray.getJSONObject(i);
                    final String name = obj.optString("name", "Наказание " + (i + 1));
                    final String command = obj.optString("command", "");
                    final String time = obj.optString("time", "");
                    final String reason = obj.optString("reason", "");
                    final String minLevel = obj.optString("minlevel", "1");
                    LinearLayout linearLayout = new LinearLayout(this);
                    LinearLayout linearLayout2 = container;
                    linearLayout.setOrientation(1);
                    linearLayout.setPadding(10, 10, 10, 10);
                    linearLayout.setBackground(GlassUI.glassSelector(getApplicationContext(), -986896));
                    StringBuilder info = new StringBuilder();
                    int i2 = i;
                    info.append(i + 1).append(".").append(name).append(str);
                    info.append("📝 Команда:").append(command).append(str);
                    if (!time.isEmpty()) {
                        info.append("⏱️ Время:").append(time).append(str);
                    }
                    if (!reason.isEmpty()) {
                        info.append("💬 Причина:").append(reason).append(str);
                    }
                    info.append("👑 Мин. ур:").append(minLevel);
                    TextView tvInfo = new TextView(this);
                    tvInfo.setText(info.toString());
                    tvInfo.setTextColor(-16777216);
                    linearLayout.addView(tvInfo);
                    LinearLayout buttonLayout = new LinearLayout(this);
                    buttonLayout.setOrientation(0);
                    String str2 = str;
                    buttonLayout.setPadding(0, 10, 0, 0);
                    Button btnEdit = new Button(this);
                    btnEdit.setText("✏️ Редактировать");
                    btnEdit.setBackground(GlassUI.glassSelector(getApplicationContext(), -16750900));
                    btnEdit.setTextColor(-1);
                    String key2 = key;
                    btnEdit.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1.0f));
                    String json2 = json;
                    ScrollView scrollView2 = scrollView;
                    btnEdit.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.MainActivity.25
                        @Override // android.view.View.OnClickListener
                        public void onClick(View v) {
                            MainActivity.this.showEditPunishmentDialog(typeName, index, name, command, time, reason, minLevel);
                        }
                    });
                    Button btnDelete = new Button(this);
                    btnDelete.setText("🗑️ Удалить");
                    btnDelete.setBackground(GlassUI.glassSelector(getApplicationContext(), -3407872));
                    btnDelete.setTextColor(-1);
                    btnDelete.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1.0f));
                    btnDelete.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.MainActivity.26
                        @Override // android.view.View.OnClickListener
                        public void onClick(View v) {
                            MainActivity.this.deletePunishment(typeName, index);
                            MainActivity.this.showPunishmentsListByType(typeName);
                        }
                    });
                    buttonLayout.addView(btnEdit);
                    View sep = new View(this);
                    sep.setLayoutParams(new LinearLayout.LayoutParams(10, -1));
                    buttonLayout.addView(sep);
                    buttonLayout.addView(btnDelete);
                    linearLayout.addView(buttonLayout);
                    linearLayout2.addView(linearLayout);
                    if (i2 < jsonArray.length() - 1) {
                        View sepView = new View(this);
                        sepView.setLayoutParams(new LinearLayout.LayoutParams(-1, 5));
                        linearLayout2.addView(sepView);
                    }
                    i = i2 + 1;
                    container = linearLayout2;
                    str = str2;
                    key = key2;
                    json = json2;
                    scrollView = scrollView2;
                }
                ScrollView scrollView3 = scrollView;
                scrollView3.addView(container);
                new AlertDialog.Builder(this, R.style.GlassDialogTheme).setTitle("" + typeName.toUpperCase() + "").setView(scrollView3).setPositiveButton("Закрыть", (DialogInterface.OnClickListener) null).show();
                return;
            }
            new AlertDialog.Builder(this, R.style.GlassDialogTheme).setTitle(typeName).setMessage("Список пуст").setPositiveButton("OK", (DialogInterface.OnClickListener) null).show();
        } catch (Exception e) {
            Toast.makeText(this, "Ошибка: " + e.getMessage(), 0).show();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showEditPunishmentDialog(final String typeName, final int index, String currentName, String currentCommand, String currentTime, String currentReason, String currentMinLevel) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(1);
        layout.setPadding(20, 20, 20, 20);
        final EditText etName = new EditText(this);
        etName.setBackground(GlassUI.lightCard(this, 10.0f));
        etName.setPadding(24, 18, 24, 18);
        etName.setTextColor(-14935010);
        etName.setHintTextColor(-7434605);
        etName.setHint("Название (например: Помеха)");
        etName.setText(currentName);
        layout.addView(etName);
        final EditText etCommand = new EditText(this);
        etCommand.setBackground(GlassUI.lightCard(this, 10.0f));
        etCommand.setPadding(24, 18, 24, 18);
        etCommand.setTextColor(-14935010);
        etCommand.setHintTextColor(-7434605);
        etCommand.setHint("Команда (например: /kick)");
        etCommand.setText(currentCommand);
        layout.addView(etCommand);
        final EditText etTime = new EditText(this);
        etTime.setBackground(GlassUI.lightCard(this, 10.0f));
        etTime.setPadding(24, 18, 24, 18);
        etTime.setTextColor(-14935010);
        etTime.setHintTextColor(-7434605);
        etTime.setHint("Время (если нужно)");
        etTime.setText(currentTime);
        layout.addView(etTime);
        final EditText etReason = new EditText(this);
        etReason.setBackground(GlassUI.lightCard(this, 10.0f));
        etReason.setPadding(24, 18, 24, 18);
        etReason.setTextColor(-14935010);
        etReason.setHintTextColor(-7434605);
        etReason.setHint("Причина (например: 5.2 ОП)");
        etReason.setText(currentReason);
        layout.addView(etReason);
        final EditText etMinLevel = new EditText(this);
        etMinLevel.setBackground(GlassUI.lightCard(this, 10.0f));
        etMinLevel.setPadding(24, 18, 24, 18);
        etMinLevel.setTextColor(-14935010);
        etMinLevel.setHintTextColor(-7434605);
        etMinLevel.setHint("Мин. уровень (1-4)");
        etMinLevel.setText(currentMinLevel);
        etMinLevel.setInputType(2);
        layout.addView(etMinLevel);
        new AlertDialog.Builder(this, R.style.GlassDialogTheme).setTitle("Редактировать: " + typeName).setView(layout).setPositiveButton("Сохранить", new DialogInterface.OnClickListener() { // from class: gtandroid.universal.MainActivity.27
            @Override // android.content.DialogInterface.OnClickListener
            public void onClick(DialogInterface dialog, int which) {
                String name = etName.getText().toString().trim();
                String command = etCommand.getText().toString().trim();
                String time = etTime.getText().toString().trim();
                String reason = etReason.getText().toString().trim();
                String minLevel = etMinLevel.getText().toString().trim();
                if (!name.isEmpty() && !command.isEmpty()) {
                    if (minLevel.isEmpty()) {
                        minLevel = "1";
                    }
                    MainActivity.this.updatePunishment(typeName, index, name, command, time, reason, minLevel);
                    Toast.makeText(MainActivity.this, "✅ Обновлено!", 0).show();
                    MainActivity.this.showPunishmentsListByType(typeName);
                    return;
                }
                Toast.makeText(MainActivity.this, "Заполните название и команду!", 0).show();
            }
        }).setNegativeButton("Отмена", (DialogInterface.OnClickListener) null).show();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void updatePunishment(String type, int index, String name, String command, String time, String reason, String minLevel) {
        try {
            String key = "punishment_" + type.toLowerCase();
            JSONArray jsonArray = new JSONArray(this.prefs.getString(key, "[]"));
            JSONObject obj = new JSONObject();
            obj.put("name", name);
            obj.put("command", command);
            obj.put("time", time);
            obj.put("reason", reason);
            obj.put("minlevel", minLevel);
            jsonArray.put(index, obj);
            this.prefs.edit().putString(key, jsonArray.toString()).apply();
        } catch (Exception e) {
            Toast.makeText(this, "Ошибка: " + e.getMessage(), 0).show();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void deletePunishment(String typeName, int index) {
        try {
            String key = "punishment_" + typeName.toLowerCase();
            JSONArray jsonArray = new JSONArray(this.prefs.getString(key, "[]"));
            jsonArray.remove(index);
            this.prefs.edit().putString(key, jsonArray.toString()).apply();
            Toast.makeText(this, "✅ Удалено!", 0).show();
        } catch (Exception e) {
            Toast.makeText(this, "Ошибка: " + e.getMessage(), 0).show();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showQuickAnswersEditor() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(1);
        layout.setPadding(20, 20, 20, 20);
        Button btnAdd = new Button(this);
        btnAdd.setText("➕ Добавить ответ");
        btnAdd.setBackground(GlassUI.glassSelector(getApplicationContext(), -16733696));
        btnAdd.setTextColor(-1);
        btnAdd.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.MainActivity.28
            @Override // android.view.View.OnClickListener
            public void onClick(View v) {
                MainActivity.this.showAddQuickAnswerDialog();
            }
        });
        layout.addView(btnAdd);
        Button btnList = new Button(this);
        btnList.setText("📋 Список ответов");
        btnList.setBackground(GlassUI.glassSelector(getApplicationContext(), -16750900));
        btnList.setTextColor(-1);
        btnList.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.MainActivity.29
            @Override // android.view.View.OnClickListener
            public void onClick(View v) {
                MainActivity.this.showQuickAnswersList();
            }
        });
        layout.addView(btnList);
        new AlertDialog.Builder(this, R.style.GlassDialogTheme).setTitle("Редактор быстрых ответов").setView(layout).setNegativeButton("Закрыть", (DialogInterface.OnClickListener) null).show();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showAddQuickAnswerDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(1);
        layout.setPadding(20, 20, 20, 20);
        final EditText etName = new EditText(this);
        etName.setBackground(GlassUI.lightCard(this, 10.0f));
        etName.setPadding(24, 18, 24, 18);
        etName.setTextColor(-14935010);
        etName.setHintTextColor(-7434605);
        etName.setHint("Название (например: Приветствие)");
        layout.addView(etName);
        final EditText etCommand = new EditText(this);
        etCommand.setBackground(GlassUI.lightCard(this, 10.0f));
        etCommand.setPadding(24, 18, 24, 18);
        etCommand.setTextColor(-14935010);
        etCommand.setHintTextColor(-7434605);
        etCommand.setHint("Команда (например: /ans) - оставьте пустым если не нужна");
        layout.addView(etCommand);
        final EditText etAnswer = new EditText(this);
        etAnswer.setBackground(GlassUI.lightCard(this, 10.0f));
        etAnswer.setPadding(24, 18, 24, 18);
        etAnswer.setTextColor(-14935010);
        etAnswer.setHintTextColor(-7434605);
        etAnswer.setHint("Текст ответа (например: Приветствую!)");
        etAnswer.setLines(3);
        layout.addView(etAnswer);
        new AlertDialog.Builder(this, R.style.GlassDialogTheme).setTitle("Добавить быстрый ответ").setView(layout).setPositiveButton("Сохранить", new DialogInterface.OnClickListener() { // from class: gtandroid.universal.MainActivity.30
            @Override // android.content.DialogInterface.OnClickListener
            public void onClick(DialogInterface dialog, int which) {
                String name = etName.getText().toString().trim();
                String command = etCommand.getText().toString().trim();
                String answer = etAnswer.getText().toString().trim();
                if (!name.isEmpty() && !answer.isEmpty()) {
                    MainActivity.this.saveQuickAnswer(name, command, answer);
                    Toast.makeText(MainActivity.this, "✅ Сохранено!", 0).show();
                } else {
                    Toast.makeText(MainActivity.this, "Заполните название и ответ!", 0).show();
                }
            }
        }).setNegativeButton("Отмена", (DialogInterface.OnClickListener) null).show();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void saveQuickAnswer(String name, String command, String answer) {
        try {
            JSONArray jsonArray = new JSONArray(this.prefs.getString("quick_answers", "[]"));
            JSONObject obj = new JSONObject();
            obj.put("name", name);
            obj.put("command", command);
            obj.put("answer", answer);
            jsonArray.put(obj);
            this.prefs.edit().putString("quick_answers", jsonArray.toString()).apply();
        } catch (Exception e) {
            Toast.makeText(this, "Ошибка: " + e.getMessage(), 0).show();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showQuickAnswersList() {
        String str = "";
        try {
            String json = this.prefs.getString("quick_answers", "[]");
            JSONArray jsonArray = new JSONArray(json);
            if (jsonArray.length() != 0) {
                ScrollView scrollView = new ScrollView(this);
                LinearLayout linearLayout = new LinearLayout(this);
                int i = 1;
                linearLayout.setOrientation(1);
                int i2 = 10;
                linearLayout.setPadding(10, 10, 10, 10);
                int i3 = 0;
                while (i3 < jsonArray.length()) {
                    final int index = i3;
                    JSONObject obj = jsonArray.getJSONObject(i3);
                    final String name = obj.optString("name", "Ответ" + (i3 + 1));
                    final String command = obj.optString("command", str);
                    final String answer = obj.optString("answer", str);
                    LinearLayout linearLayout2 = new LinearLayout(this);
                    linearLayout2.setOrientation(i);
                    linearLayout2.setPadding(i2, i2, i2, i2);
                    linearLayout2.setBackground(GlassUI.glassSelector(getApplicationContext(), -986896));
                    TextView tvInfo = new TextView(this);
                    String cmdText = command.isEmpty() ? "(без команды)" : command;
                    String str2 = str;
                    tvInfo.setText((i3 + 1) + "." + name + "\n📝 Команда: " + cmdText + "\n💬 Ответ: " + answer);
                    tvInfo.setTextColor(-16777216);
                    linearLayout2.addView(tvInfo);
                    LinearLayout buttonLayout = new LinearLayout(this);
                    buttonLayout.setOrientation(0);
                    buttonLayout.setPadding(0, 10, 0, 0);
                    Button btnEdit = new Button(this);
                    btnEdit.setText("✏️ Редактировать");
                    btnEdit.setBackground(GlassUI.glassSelector(getApplicationContext(), -16750900));
                    btnEdit.setTextColor(-1);
                    String json2 = json;
                    btnEdit.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1.0f));
                    int i4 = i3;
                    btnEdit.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.MainActivity.31
                        @Override // android.view.View.OnClickListener
                        public void onClick(View v) {
                            MainActivity.this.showEditQuickAnswerDialog(index, name, command, answer);
                        }
                    });
                    Button btnDelete = new Button(this);
                    btnDelete.setText("🗑️ Удалить");
                    btnDelete.setBackground(GlassUI.glassSelector(getApplicationContext(), -3407872));
                    btnDelete.setTextColor(-1);
                    btnDelete.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1.0f));
                    btnDelete.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.MainActivity.32
                        @Override // android.view.View.OnClickListener
                        public void onClick(View v) {
                            MainActivity.this.deleteQuickAnswer(index);
                            MainActivity.this.showQuickAnswersList();
                        }
                    });
                    buttonLayout.addView(btnEdit);
                    View sep = new View(this);
                    sep.setLayoutParams(new LinearLayout.LayoutParams(10, -1));
                    buttonLayout.addView(sep);
                    buttonLayout.addView(btnDelete);
                    linearLayout2.addView(buttonLayout);
                    linearLayout.addView(linearLayout2);
                    if (i4 < jsonArray.length() - 1) {
                        View sepView = new View(this);
                        sepView.setLayoutParams(new LinearLayout.LayoutParams(-1, 5));
                        linearLayout.addView(sepView);
                    }
                    i3 = i4 + 1;
                    str = str2;
                    json = json2;
                    i = 1;
                    i2 = 10;
                }
                scrollView.addView(linearLayout);
                new AlertDialog.Builder(this, R.style.GlassDialogTheme).setTitle("БЫСТРЫЕ ОТВЕТЫ").setView(scrollView).setPositiveButton("Закрыть", (DialogInterface.OnClickListener) null).show();
                return;
            }
            new AlertDialog.Builder(this, R.style.GlassDialogTheme).setTitle("Быстрые ответы").setMessage("Список пуст").setPositiveButton("OK", (DialogInterface.OnClickListener) null).show();
        } catch (Exception e) {
            Toast.makeText(this, "Ошибка: " + e.getMessage(), 0).show();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showEditQuickAnswerDialog(final int index, String currentName, String currentCommand, String currentAnswer) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(1);
        layout.setPadding(20, 20, 20, 20);
        final EditText etName = new EditText(this);
        etName.setBackground(GlassUI.lightCard(this, 10.0f));
        etName.setPadding(24, 18, 24, 18);
        etName.setTextColor(-14935010);
        etName.setHintTextColor(-7434605);
        etName.setHint("Название (например: Приветствие)");
        etName.setText(currentName);
        layout.addView(etName);
        final EditText etCommand = new EditText(this);
        etCommand.setBackground(GlassUI.lightCard(this, 10.0f));
        etCommand.setPadding(24, 18, 24, 18);
        etCommand.setTextColor(-14935010);
        etCommand.setHintTextColor(-7434605);
        etCommand.setHint("Команда (например: /ans) - оставьте пустым если не нужна");
        etCommand.setText(currentCommand);
        layout.addView(etCommand);
        final EditText etAnswer = new EditText(this);
        etAnswer.setBackground(GlassUI.lightCard(this, 10.0f));
        etAnswer.setPadding(24, 18, 24, 18);
        etAnswer.setTextColor(-14935010);
        etAnswer.setHintTextColor(-7434605);
        etAnswer.setHint("Текст ответа (например: Приветствую!)");
        etAnswer.setText(currentAnswer);
        etAnswer.setLines(3);
        layout.addView(etAnswer);
        new AlertDialog.Builder(this, R.style.GlassDialogTheme).setTitle("Редактировать быстрый ответ").setView(layout).setPositiveButton("Сохранить", new DialogInterface.OnClickListener() { // from class: gtandroid.universal.MainActivity.33
            @Override // android.content.DialogInterface.OnClickListener
            public void onClick(DialogInterface dialog, int which) {
                String name = etName.getText().toString().trim();
                String command = etCommand.getText().toString().trim();
                String answer = etAnswer.getText().toString().trim();
                if (!name.isEmpty() && !answer.isEmpty()) {
                    MainActivity.this.updateQuickAnswer(index, name, command, answer);
                    Toast.makeText(MainActivity.this, "✅ Обновлено!", 0).show();
                    MainActivity.this.showQuickAnswersList();
                    return;
                }
                Toast.makeText(MainActivity.this, "Заполните название и ответ!", 0).show();
            }
        }).setNegativeButton("Отмена", (DialogInterface.OnClickListener) null).show();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void updateQuickAnswer(int index, String name, String command, String answer) {
        try {
            JSONArray jsonArray = new JSONArray(this.prefs.getString("quick_answers", "[]"));
            JSONObject obj = new JSONObject();
            obj.put("name", name);
            obj.put("command", command);
            obj.put("answer", answer);
            jsonArray.put(index, obj);
            this.prefs.edit().putString("quick_answers", jsonArray.toString()).apply();
        } catch (Exception e) {
            Toast.makeText(this, "Ошибка: " + e.getMessage(), 0).show();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void deleteQuickAnswer(int index) {
        try {
            JSONArray jsonArray = new JSONArray(this.prefs.getString("quick_answers", "[]"));
            jsonArray.remove(index);
            this.prefs.edit().putString("quick_answers", jsonArray.toString()).apply();
            Toast.makeText(this, "✅ Удалено!", 0).show();
        } catch (Exception e) {
            Toast.makeText(this, "Ошибка: " + e.getMessage(), 0).show();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showNormalEditor() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(1);
        layout.setPadding(20, 20, 20, 20);
        Button btnAdd = new Button(this);
        btnAdd.setText("➕ Добавить кнопку");
        btnAdd.setBackground(GlassUI.glassSelector(getApplicationContext(), -16733696));
        btnAdd.setTextColor(-1);
        btnAdd.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.MainActivity.34
            @Override // android.view.View.OnClickListener
            public void onClick(View v) {
                MainActivity.this.showAddButtonDialog();
            }
        });
        layout.addView(btnAdd);
        Button btnList = new Button(this);
        btnList.setText("📋 Список кнопок");
        btnList.setBackground(GlassUI.glassSelector(getApplicationContext(), -16750900));
        btnList.setTextColor(-1);
        btnList.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.MainActivity.35
            @Override // android.view.View.OnClickListener
            public void onClick(View v) {
                MainActivity.this.showNormalButtonsList();
            }
        });
        layout.addView(btnList);
        new AlertDialog.Builder(this, R.style.GlassDialogTheme).setTitle("🔘 Редактор обычный").setView(layout).setNegativeButton("Закрыть", (DialogInterface.OnClickListener) null).show();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showAddButtonDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(1);
        layout.setPadding(30, 20, 30, 20);
        final EditText etName = new EditText(this);
        etName.setBackground(GlassUI.lightCard(this, 10.0f));
        etName.setPadding(24, 18, 24, 18);
        etName.setTextColor(-14935010);
        etName.setHintTextColor(-7434605);
        etName.setHint("Название кнопки");
        layout.addView(etName);
        final EditText etText = new EditText(this);
        etText.setBackground(GlassUI.lightCard(this, 10.0f));
        etText.setPadding(24, 18, 24, 18);
        etText.setTextColor(-14935010);
        etText.setHintTextColor(-7434605);
        etText.setHint("Текст команды");
        layout.addView(etText);
        new AlertDialog.Builder(this, R.style.GlassDialogTheme).setTitle("Добавить кнопку").setView(layout).setPositiveButton("Сохранить", new DialogInterface.OnClickListener() { // from class: gtandroid.universal.MainActivity.36
            @Override // android.content.DialogInterface.OnClickListener
            public void onClick(DialogInterface dialog, int which) {
                String name = etName.getText().toString();
                String text = etText.getText().toString();
                if (!name.isEmpty() && !text.isEmpty()) {
                    MainActivity.this.saveButton(name, text);
                    Toast.makeText(MainActivity.this, "Сохранено!", 0).show();
                }
            }
        }).setNegativeButton("Отмена", (DialogInterface.OnClickListener) null).show();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void saveButton(String name, String text) {
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

    /* JADX INFO: Access modifiers changed from: private */
    public void showNormalButtonsList() {
        int count;
        int i = 0;
        int count2 = getIntPref("btn_count", 0);
        if (count2 != 0) {
            ScrollView scrollView = new ScrollView(this);
            LinearLayout linearLayout = new LinearLayout(this);
            int i2 = 1;
            linearLayout.setOrientation(1);
            int i3 = 10;
            linearLayout.setPadding(10, 10, 10, 10);
            int i4 = 0;
            while (i4 < count2) {
                final int index = i4;
                final String name = this.prefs.getString("btn_" + i4 + "_name", "Кнопка");
                final String text = this.prefs.getString("btn_" + i4 + "_text", "");
                LinearLayout linearLayout2 = new LinearLayout(this);
                linearLayout2.setOrientation(i2);
                linearLayout2.setPadding(i3, i3, i3, i3);
                linearLayout2.setBackground(GlassUI.glassSelector(getApplicationContext(), -986896));
                TextView tvInfo = new TextView(this);
                tvInfo.setText((i4 + 1) + "." + name + "\n📝 Команда: " + text);
                tvInfo.setTextColor(-16777216);
                linearLayout2.addView(tvInfo);
                LinearLayout buttonLayout = new LinearLayout(this);
                buttonLayout.setOrientation(i);
                buttonLayout.setPadding(i, i3, i, i);
                Button btnEdit = new Button(this);
                btnEdit.setText("✏️ Редактировать");
                btnEdit.setBackground(GlassUI.glassSelector(getApplicationContext(), -16750900));
                btnEdit.setTextColor(-1);
                btnEdit.setLayoutParams(new LinearLayout.LayoutParams(i, -2, 1.0f));
                btnEdit.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.MainActivity.37
                    @Override // android.view.View.OnClickListener
                    public void onClick(View v) {
                        MainActivity.this.showEditNormalButtonDialog(index, name, text);
                    }
                });
                Button btnDelete = new Button(this);
                btnDelete.setText("🗑️ Удалить");
                btnDelete.setBackground(GlassUI.glassSelector(getApplicationContext(), -3407872));
                btnDelete.setTextColor(-1);
                btnDelete.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1.0f));
                btnDelete.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.MainActivity.38
                    @Override // android.view.View.OnClickListener
                    public void onClick(View v) {
                        MainActivity.this.deleteNormalButton(index);
                        MainActivity.this.showNormalButtonsList();
                    }
                });
                buttonLayout.addView(btnEdit);
                View sep = new View(this);
                sep.setLayoutParams(new LinearLayout.LayoutParams(10, -1));
                buttonLayout.addView(sep);
                buttonLayout.addView(btnDelete);
                linearLayout2.addView(buttonLayout);
                linearLayout.addView(linearLayout2);
                if (i4 < count2 - 1) {
                    View sepView = new View(this);
                    count = count2;
                    sepView.setLayoutParams(new LinearLayout.LayoutParams(-1, 5));
                    linearLayout.addView(sepView);
                } else {
                    count = count2;
                }
                i4++;
                count2 = count;
                i = 0;
                i2 = 1;
                i3 = 10;
            }
            scrollView.addView(linearLayout);
            new AlertDialog.Builder(this, R.style.GlassDialogTheme).setTitle("ОБЫЧНЫЕ КНОПКИ").setView(scrollView).setPositiveButton("Закрыть", (DialogInterface.OnClickListener) null).show();
            return;
        }
        new AlertDialog.Builder(this, R.style.GlassDialogTheme).setTitle("Обычные кнопки").setMessage("Список пуст").setPositiveButton("OK", (DialogInterface.OnClickListener) null).show();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showEditNormalButtonDialog(final int index, String currentName, String currentText) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(1);
        layout.setPadding(30, 20, 30, 20);
        final EditText etName = new EditText(this);
        etName.setBackground(GlassUI.lightCard(this, 10.0f));
        etName.setPadding(24, 18, 24, 18);
        etName.setTextColor(-14935010);
        etName.setHintTextColor(-7434605);
        etName.setHint("Название кнопки");
        etName.setText(currentName);
        layout.addView(etName);
        final EditText etText = new EditText(this);
        etText.setBackground(GlassUI.lightCard(this, 10.0f));
        etText.setPadding(24, 18, 24, 18);
        etText.setTextColor(-14935010);
        etText.setHintTextColor(-7434605);
        etText.setHint("Текст команды");
        etText.setText(currentText);
        layout.addView(etText);
        new AlertDialog.Builder(this, R.style.GlassDialogTheme).setTitle("Редактировать кнопку").setView(layout).setPositiveButton("Сохранить", new DialogInterface.OnClickListener() { // from class: gtandroid.universal.MainActivity.39
            @Override // android.content.DialogInterface.OnClickListener
            public void onClick(DialogInterface dialog, int which) {
                String name = etName.getText().toString();
                String text = etText.getText().toString();
                if (!name.isEmpty() && !text.isEmpty()) {
                    MainActivity.this.updateNormalButton(index, name, text);
                    Toast.makeText(MainActivity.this, "✅ Обновлено!", 0).show();
                    MainActivity.this.showNormalButtonsList();
                    return;
                }
                Toast.makeText(MainActivity.this, "Заполните все поля!", 0).show();
            }
        }).setNegativeButton("Отмена", (DialogInterface.OnClickListener) null).show();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void updateNormalButton(int index, String name, String text) {
        SharedPreferences.Editor editor = this.prefs.edit();
        editor.putString("btn_" + index + "_name", name);
        editor.putString("btn_" + index + "_text", text);
        editor.apply();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void deleteNormalButton(int index) {
        int count = getIntPref("btn_count", 0);
        SharedPreferences.Editor editor = this.prefs.edit();
        for (int i = index; i < count - 1; i++) {
            String nextName = this.prefs.getString("btn_" + (i + 1) + "_name", "");
            String nextText = this.prefs.getString("btn_" + (i + 1) + "_text", "");
            editor.putString("btn_" + i + "_name", nextName);
            editor.putString("btn_" + i + "_text", nextText);
        }
        editor.remove("btn_" + (count - 1) + "_name");
        editor.remove("btn_" + (count - 1) + "_text");
        editor.putInt("btn_count", count - 1);
        editor.apply();
        Toast.makeText(this, "✅ Удалено!", 0).show();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showTransparencyDialog() {
        int currentTransparency = getIntPref("keyboard_transparency", 100);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(1);
        layout.setPadding(30, 20, 30, 20);
        final TextView tvValue = new TextView(this);
        tvValue.setText("Прозрачность: " + currentTransparency + "%");
        tvValue.setTextSize(18.0f);
        tvValue.setGravity(17);
        tvValue.setPadding(0, 0, 0, 20);
        layout.addView(tvValue);
        SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(100);
        seekBar.setProgress(currentTransparency);
        seekBar.setPadding(20, 10, 20, 10);
        layout.addView(seekBar);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() { // from class: gtandroid.universal.MainActivity.40
            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onProgressChanged(SeekBar seekBar2, int progress, boolean fromUser) {
                tvValue.setText("Прозрачность: " + progress + "%");
                MainActivity.this.prefs.edit().putInt("keyboard_transparency", progress).apply();
                Intent intent = new Intent("KEYBOARD_TRANSPARENCY_CHANGED");
                MainActivity.this.sendBroadcast(intent);
            }

            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onStartTrackingTouch(SeekBar seekBar2) {
            }

            @Override // android.widget.SeekBar.OnSeekBarChangeListener
            public void onStopTrackingTouch(SeekBar seekBar2) {
            }
        });
        new AlertDialog.Builder(this, R.style.GlassDialogTheme).setTitle("ПРОЗРАЧНОСТЬ КЛАВИАТУРЫ").setView(layout).setPositiveButton("Закрыть", (DialogInterface.OnClickListener) null).show();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showKeyboardSizeDialog() {
        final String[] sizes = {"80%", "100%", "120%", "140%", "160%", "180%", "200%"};
        int currentSize = getIntPref("keyboard_size", 1);
        new AlertDialog.Builder(this, R.style.GlassDialogTheme).setTitle("РАЗМЕР КЛАВИАТУРЫ").setSingleChoiceItems(sizes, currentSize, new DialogInterface.OnClickListener() { // from class: gtandroid.universal.MainActivity.41
            @Override // android.content.DialogInterface.OnClickListener
            public void onClick(DialogInterface dialog, int which) {
                MainActivity.this.prefs.edit().putInt("keyboard_size", which).apply();
                Toast.makeText(MainActivity.this, "✅ Размер: " + sizes[which], 0).show();
                Intent intent = new Intent("KEYBOARD_SIZE_CHANGED");
                MainActivity.this.sendBroadcast(intent);
                dialog.dismiss();
            }
        }).setNegativeButton("Отмена", (DialogInterface.OnClickListener) null).show();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showAdminLevelDialog() {
        String[] levels = {"1", "2", "3", "4"};
        new AlertDialog.Builder(this, R.style.GlassDialogTheme).setTitle("Уровень админа").setItems(levels, new DialogInterface.OnClickListener() { // from class: gtandroid.universal.MainActivity.42
            @Override // android.content.DialogInterface.OnClickListener
            public void onClick(DialogInterface dialog, int which) {
                int level = which + 1;
                MainActivity.this.prefs.edit().putInt("admin_level", level).apply();
                Toast.makeText(MainActivity.this, "Уровень: " + level, 0).show();
                MainActivity.this.showSettingsScreen();
            }
        }).show();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void exportConfig() {
        Intent intent = new Intent("android.intent.action.CREATE_DOCUMENT");
        intent.addCategory("android.intent.category.OPENABLE");
        intent.setType("*/*");
        intent.putExtra("android.intent.extra.TITLE", "GTAndroid_config.cfg");
        startActivityForResult(intent, REQUEST_EXPORT);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void importConfig() {
        Intent intent = new Intent("android.intent.action.OPEN_DOCUMENT");
        intent.addCategory("android.intent.category.OPENABLE");
        intent.setType("*/*");
        startActivityForResult(intent, REQUEST_IMPORT);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showFamWarEditor() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(1);
        layout.setPadding(20, 20, 20, 20);
        Button btnAdd = new Button(this);
        btnAdd.setText("➕ Добавить команду FamWar");
        btnAdd.setBackground(GlassUI.glassSelector(getApplicationContext(), -16733696));
        btnAdd.setTextColor(-1);
        btnAdd.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.MainActivity.43
            @Override // android.view.View.OnClickListener
            public void onClick(View v) {
                MainActivity.this.showAddFamWarDialog();
            }
        });
        layout.addView(btnAdd);
        Button btnList = new Button(this);
        btnList.setText("📋 Список команд FamWar");
        btnList.setBackground(GlassUI.glassSelector(getApplicationContext(), -16750900));
        btnList.setTextColor(-1);
        btnList.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.MainActivity.44
            @Override // android.view.View.OnClickListener
            public void onClick(View v) {
                MainActivity.this.showFamWarList();
            }
        });
        layout.addView(btnList);
        new AlertDialog.Builder(this, R.style.GlassDialogTheme).setTitle("⚔️ Редактор FamWar").setView(layout).setNegativeButton("Закрыть", (DialogInterface.OnClickListener) null).show();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showAddFamWarDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(1);
        layout.setPadding(20, 20, 20, 20);
        final EditText etName = new EditText(this);
        etName.setBackground(GlassUI.lightCard(this, 10.0f));
        etName.setPadding(24, 18, 24, 18);
        etName.setTextColor(-14935010);
        etName.setHintTextColor(-7434605);
        etName.setHint("Название (например: Лечение)");
        layout.addView(etName);
        final EditText etCommand = new EditText(this);
        etCommand.setBackground(GlassUI.lightCard(this, 10.0f));
        etCommand.setPadding(24, 18, 24, 18);
        etCommand.setTextColor(-14935010);
        etCommand.setHintTextColor(-7434605);
        etCommand.setHint("Команда (например: /heal)");
        layout.addView(etCommand);
        final EditText etTime = new EditText(this);
        etTime.setBackground(GlassUI.lightCard(this, 10.0f));
        etTime.setPadding(24, 18, 24, 18);
        etTime.setTextColor(-14935010);
        etTime.setHintTextColor(-7434605);
        etTime.setHint("Время (если нужно, например: 60)");
        layout.addView(etTime);
        final EditText etReason = new EditText(this);
        etReason.setBackground(GlassUI.lightCard(this, 10.0f));
        etReason.setPadding(24, 18, 24, 18);
        etReason.setTextColor(-14935010);
        etReason.setHintTextColor(-7434605);
        etReason.setHint("Причина (например: Лечение после боя)");
        layout.addView(etReason);
        final EditText etMinLevel = new EditText(this);
        etMinLevel.setBackground(GlassUI.lightCard(this, 10.0f));
        etMinLevel.setPadding(24, 18, 24, 18);
        etMinLevel.setTextColor(-14935010);
        etMinLevel.setHintTextColor(-7434605);
        etMinLevel.setHint("Мин. уровень (1-4)");
        etMinLevel.setInputType(2);
        layout.addView(etMinLevel);
        new AlertDialog.Builder(this, R.style.GlassDialogTheme).setTitle("Добавить команду FamWar").setView(layout).setPositiveButton("Сохранить", new DialogInterface.OnClickListener() { // from class: gtandroid.universal.MainActivity.45
            @Override // android.content.DialogInterface.OnClickListener
            public void onClick(DialogInterface dialog, int which) {
                String name = etName.getText().toString().trim();
                String command = etCommand.getText().toString().trim();
                String time = etTime.getText().toString().trim();
                String reason = etReason.getText().toString().trim();
                String minLevel = etMinLevel.getText().toString().trim();
                if (!name.isEmpty() && !command.isEmpty()) {
                    if (minLevel.isEmpty()) {
                        minLevel = "1";
                    }
                    MainActivity.this.saveFamWarCommand(name, command, time, reason, minLevel);
                    Toast.makeText(MainActivity.this, "✅ Сохранено!", 0).show();
                    return;
                }
                Toast.makeText(MainActivity.this, "Заполните название и команду!", 0).show();
            }
        }).setNegativeButton("Отмена", (DialogInterface.OnClickListener) null).show();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void saveFamWarCommand(String name, String command, String time, String reason, String minLevel) {
        try {
            JSONArray jsonArray = new JSONArray(this.prefs.getString("famwar_commands", "[]"));
            JSONObject obj = new JSONObject();
            obj.put("name", name);
            obj.put("command", command);
            obj.put("time", time);
            obj.put("reason", reason);
            obj.put("minlevel", minLevel);
            jsonArray.put(obj);
            this.prefs.edit().putString("famwar_commands", jsonArray.toString()).apply();
        } catch (Exception e) {
            Toast.makeText(this, "Ошибка: " + e.getMessage(), 0).show();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showFamWarList() {
        String str = "";
        String str2 = "\n";
        try {
            String json = this.prefs.getString("famwar_commands", "[]");
            JSONArray jsonArray = new JSONArray(json);
            if (jsonArray.length() != 0) {
                ScrollView scrollView = new ScrollView(this);
                LinearLayout container = new LinearLayout(this);
                container.setOrientation(1);
                container.setPadding(10, 10, 10, 10);
                int i = 0;
                while (i < jsonArray.length()) {
                    final int index = i;
                    JSONObject obj = jsonArray.getJSONObject(i);
                    final String name = obj.optString("name", "Команда " + (i + 1));
                    final String command = obj.optString("command", str);
                    final String time = obj.optString("time", str);
                    final String reason = obj.optString("reason", str);
                    final String minLevel = obj.optString("minlevel", "1");
                    LinearLayout linearLayout = new LinearLayout(this);
                    String str3 = str;
                    linearLayout.setOrientation(1);
                    linearLayout.setPadding(10, 10, 10, 10);
                    linearLayout.setBackground(GlassUI.glassSelector(getApplicationContext(), -986896));
                    StringBuilder info = new StringBuilder();
                    int i2 = i;
                    info.append(i + 1).append(".").append(name).append(str2);
                    info.append("📝 Команда:").append(command).append(str2);
                    if (!time.isEmpty()) {
                        info.append("⏱️ Время:").append(time).append(str2);
                    }
                    if (!reason.isEmpty()) {
                        info.append("💬 Причина:").append(reason).append(str2);
                    }
                    info.append("👑 Мин. ур:").append(minLevel);
                    TextView tvInfo = new TextView(this);
                    tvInfo.setText(info.toString());
                    tvInfo.setTextColor(-16777216);
                    linearLayout.addView(tvInfo);
                    LinearLayout buttonLayout = new LinearLayout(this);
                    buttonLayout.setOrientation(0);
                    buttonLayout.setPadding(0, 10, 0, 0);
                    Button btnEdit = new Button(this);
                    btnEdit.setText("✏️ Редактировать");
                    btnEdit.setBackground(GlassUI.glassSelector(getApplicationContext(), -16750900));
                    btnEdit.setTextColor(-1);
                    String str4 = str2;
                    btnEdit.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1.0f));
                    String json2 = json;
                    ScrollView scrollView2 = scrollView;
                    LinearLayout linearLayout2 = container;
                    btnEdit.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.MainActivity.46
                        @Override // android.view.View.OnClickListener
                        public void onClick(View v) {
                            MainActivity.this.showEditFamWarDialog(index, name, command, time, reason, minLevel);
                        }
                    });
                    Button btnDelete = new Button(this);
                    btnDelete.setText("🗑️ Удалить");
                    btnDelete.setBackground(GlassUI.glassSelector(getApplicationContext(), -3407872));
                    btnDelete.setTextColor(-1);
                    btnDelete.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1.0f));
                    btnDelete.setOnClickListener(new View.OnClickListener() { // from class: gtandroid.universal.MainActivity.47
                        @Override // android.view.View.OnClickListener
                        public void onClick(View v) {
                            MainActivity.this.deleteFamWarCommand(index);
                            MainActivity.this.showFamWarList();
                        }
                    });
                    buttonLayout.addView(btnEdit);
                    View sep = new View(this);
                    sep.setLayoutParams(new LinearLayout.LayoutParams(10, -1));
                    buttonLayout.addView(sep);
                    buttonLayout.addView(btnDelete);
                    linearLayout.addView(buttonLayout);
                    linearLayout2.addView(linearLayout);
                    if (i2 < jsonArray.length() - 1) {
                        View sepView = new View(this);
                        sepView.setLayoutParams(new LinearLayout.LayoutParams(-1, 5));
                        linearLayout2.addView(sepView);
                    }
                    i = i2 + 1;
                    container = linearLayout2;
                    str = str3;
                    str2 = str4;
                    json = json2;
                    scrollView = scrollView2;
                }
                ScrollView scrollView3 = scrollView;
                scrollView3.addView(container);
                new AlertDialog.Builder(this, R.style.GlassDialogTheme).setTitle("КОМАНДЫ FAMWAR").setView(scrollView3).setPositiveButton("Закрыть", (DialogInterface.OnClickListener) null).show();
                return;
            }
            new AlertDialog.Builder(this, R.style.GlassDialogTheme).setTitle("Команды FamWar").setMessage("Список пуст").setPositiveButton("OK", (DialogInterface.OnClickListener) null).show();
        } catch (Exception e) {
            Toast.makeText(this, "Ошибка: " + e.getMessage(), 0).show();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void showEditFamWarDialog(final int index, String currentName, String currentCommand, String currentTime, String currentReason, String currentMinLevel) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(1);
        layout.setPadding(20, 20, 20, 20);
        final EditText etName = new EditText(this);
        etName.setBackground(GlassUI.lightCard(this, 10.0f));
        etName.setPadding(24, 18, 24, 18);
        etName.setTextColor(-14935010);
        etName.setHintTextColor(-7434605);
        etName.setHint("Название (например: Лечение)");
        etName.setText(currentName);
        layout.addView(etName);
        final EditText etCommand = new EditText(this);
        etCommand.setBackground(GlassUI.lightCard(this, 10.0f));
        etCommand.setPadding(24, 18, 24, 18);
        etCommand.setTextColor(-14935010);
        etCommand.setHintTextColor(-7434605);
        etCommand.setHint("Команда (например: /heal)");
        etCommand.setText(currentCommand);
        layout.addView(etCommand);
        final EditText etTime = new EditText(this);
        etTime.setBackground(GlassUI.lightCard(this, 10.0f));
        etTime.setPadding(24, 18, 24, 18);
        etTime.setTextColor(-14935010);
        etTime.setHintTextColor(-7434605);
        etTime.setHint("Время (если нужно)");
        etTime.setText(currentTime);
        layout.addView(etTime);
        final EditText etReason = new EditText(this);
        etReason.setBackground(GlassUI.lightCard(this, 10.0f));
        etReason.setPadding(24, 18, 24, 18);
        etReason.setTextColor(-14935010);
        etReason.setHintTextColor(-7434605);
        etReason.setHint("Причина");
        etReason.setText(currentReason);
        layout.addView(etReason);
        final EditText etMinLevel = new EditText(this);
        etMinLevel.setBackground(GlassUI.lightCard(this, 10.0f));
        etMinLevel.setPadding(24, 18, 24, 18);
        etMinLevel.setTextColor(-14935010);
        etMinLevel.setHintTextColor(-7434605);
        etMinLevel.setHint("Мин. уровень (1-4)");
        etMinLevel.setText(currentMinLevel);
        etMinLevel.setInputType(2);
        layout.addView(etMinLevel);
        new AlertDialog.Builder(this, R.style.GlassDialogTheme).setTitle("Редактировать команду FamWar").setView(layout).setPositiveButton("Сохранить", new DialogInterface.OnClickListener() { // from class: gtandroid.universal.MainActivity.48
            @Override // android.content.DialogInterface.OnClickListener
            public void onClick(DialogInterface dialog, int which) {
                String name = etName.getText().toString().trim();
                String command = etCommand.getText().toString().trim();
                String time = etTime.getText().toString().trim();
                String reason = etReason.getText().toString().trim();
                String minLevel = etMinLevel.getText().toString().trim();
                if (!name.isEmpty() && !command.isEmpty()) {
                    if (minLevel.isEmpty()) {
                        minLevel = "1";
                    }
                    MainActivity.this.updateFamWarCommand(index, name, command, time, reason, minLevel);
                    Toast.makeText(MainActivity.this, "✅ Обновлено!", 0).show();
                    MainActivity.this.showFamWarList();
                    return;
                }
                Toast.makeText(MainActivity.this, "Заполните название и команду!", 0).show();
            }
        }).setNegativeButton("Отмена", (DialogInterface.OnClickListener) null).show();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void updateFamWarCommand(int index, String name, String command, String time, String reason, String minLevel) {
        try {
            JSONArray jsonArray = new JSONArray(this.prefs.getString("famwar_commands", "[]"));
            JSONObject obj = new JSONObject();
            obj.put("name", name);
            obj.put("command", command);
            obj.put("time", time);
            obj.put("reason", reason);
            obj.put("minlevel", minLevel);
            jsonArray.put(index, obj);
            this.prefs.edit().putString("famwar_commands", jsonArray.toString()).apply();
        } catch (Exception e) {
            Toast.makeText(this, "Ошибка: " + e.getMessage(), 0).show();
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void deleteFamWarCommand(int index) {
        try {
            JSONArray jsonArray = new JSONArray(this.prefs.getString("famwar_commands", "[]"));
            jsonArray.remove(index);
            this.prefs.edit().putString("famwar_commands", jsonArray.toString()).apply();
            Toast.makeText(this, "✅ Удалено!", 0).show();
        } catch (Exception e) {
            Toast.makeText(this, "Ошибка: " + e.getMessage(), 0).show();
        }
    }

    private void importFromFile(Uri uri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            SharedPreferences.Editor editor = this.prefs.edit();
            editor.clear();
            while (true) {
                String line = reader.readLine();
                if (line == null) {
                    break;
                }
                if (!line.startsWith("#") && !line.trim().isEmpty()) {
                    String[] parts = line.split("=", 2);
                    if (parts.length == 2) {
                        String key = parts[0].trim();
                        String value = parts[1].trim();
                        editor.putString(key, value);
                    }
                }
            }
            reader.close();
            editor.apply();
            try {
                String adminLevelStr = this.prefs.getString("admin_level", "1");
                this.prefs.edit().putInt("admin_level", Integer.parseInt(adminLevelStr)).apply();
            } catch (Exception e) {
            }
            this.prefs.edit().remove("admin_level").apply();
            try {
                String btnCountStr = this.prefs.getString("btn_count", "0");
                this.prefs.edit().putInt("btn_count", Integer.parseInt(btnCountStr)).apply();
            } catch (Exception e2) {
            }
            this.prefs.edit().remove("btn_count").apply();
            try {
                String kbSizeStr = this.prefs.getString("keyboard_size", "1");
                this.prefs.edit().putInt("keyboard_size", Integer.parseInt(kbSizeStr)).apply();
            } catch (Exception e3) {
            }
            this.prefs.edit().remove("keyboard_size").apply();
            try {
                String kbTransStr = this.prefs.getString("keyboard_transparency", "100");
                this.prefs.edit().putInt("keyboard_transparency", Integer.parseInt(kbTransStr)).apply();
            } catch (Exception e4) {
            }
            this.prefs.edit().remove("keyboard_transparency").apply();
            loadCustomButtons();
            Toast.makeText(this, "✅ Импорт успешен!", 1).show();
        } catch (Exception e5) {
            e5.printStackTrace();
            Toast.makeText(this, "❌ Ошибка: " + e5.getMessage(), 0).show();
        }
    }

    private void exportToFile(Uri uri) {
        try {
            OutputStream outputStream = getContentResolver().openOutputStream(uri);
            StringBuilder sb = new StringBuilder("# GTAndroid Config\n\n");
            sb.append("admin_level=").append(getIntPref("admin_level", 1)).append("\n");
            sb.append("btn_count=").append(getIntPref("btn_count", 0)).append("\n");
            int count = getIntPref("btn_count", 0);
            for (int i = 0; i < count; i++) {
                sb.append("btn_").append(i).append("_name=").append(this.prefs.getString("btn_" + i + "_name", "")).append("\n");
                sb.append("btn_").append(i).append("_text=").append(this.prefs.getString("btn_" + i + "_text", "")).append("\n");
            }
            sb.append("quick_answers=").append(this.prefs.getString("quick_answers", "[]")).append("\n");
            sb.append("famwar_commands=").append(this.prefs.getString("famwar_commands", "[]")).append("\n");
            String[] punishmentTypes = {"кик", "бан", "варн", "джаил", "мут", "unбан", "unварн", "unджаил", "unмут", "рмут", "unрмут"};
            for (String type : punishmentTypes) {
                String key = "punishment_" + type;
                sb.append(key).append("=").append(this.prefs.getString(key, "[]")).append("\n");
            }
            sb.append("keyboard_size=").append(getIntPref("keyboard_size", 1)).append("\n");
            sb.append("keyboard_transparency=").append(getIntPref("keyboard_transparency", 100)).append("\n");
            outputStream.write(sb.toString().getBytes());
            outputStream.close();
            Toast.makeText(this, "✅ Экспорт успешен!", 1).show();
        } catch (IOException e) {
            Toast.makeText(this, "❌ Ошибка: " + e.getMessage(), 0).show();
        }
    }
}
