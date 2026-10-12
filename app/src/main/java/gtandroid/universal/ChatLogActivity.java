package gtandroid.universal;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ChatLogActivity extends Activity {

    private LinearLayout listContainer;
    private ScrollView scrollView;
    private final Handler handler = new Handler();
    private Runnable updater;
    private int lastCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF1A0B2E);
        root.setPadding(20, 40, 20, 20);

        TextView title = new TextView(this);
        title.setText("🧪 Ловля сообщений");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(22f);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 20);
        root.addView(title);

        TextView hint = new TextView(this);
        hint.setText("Здесь показываются ВСЕ текстовые события с экрана.\n" +
                "Открой игру и напиши что-нибудь в чат — появится здесь.");
        hint.setTextColor(0xFFA78BFA);
        hint.setTextSize(12f);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, 0, 0, 20);
        root.addView(hint);

        Button refresh = new Button(this);
        refresh.setText("↻ Обновить");
        refresh.setAllCaps(false);
        refresh.setTextColor(0xFFFFFFFF);
        refresh.setBackground(GlassUI.lightAccentButton(this, 0xFF7C3AED));
        refresh.setPadding(20, 20, 20, 20);
        refresh.setOnClickListener(v -> loadMessages(true));
        root.addView(refresh);

        Button clear = new Button(this);
        clear.setText("🗑 Очистить");
        clear.setAllCaps(false);
        clear.setTextColor(0xFFFFFFFF);
        clear.setBackground(GlassUI.lightAccentButton(this, 0xFFDC2626));
        clear.setPadding(20, 20, 20, 20);
        clear.setOnClickListener(v -> {
            getSharedPreferences("GTAndroidChatLog", MODE_PRIVATE)
                    .edit().clear().apply();
            loadMessages(true);
        });
        LinearLayout.LayoutParams clearLp = new LinearLayout.LayoutParams(-1, -2);
        clearLp.topMargin = 8;
        root.addView(clear, clearLp);

        scrollView = new ScrollView(this);
        scrollView.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1f));
        scrollView.setPadding(0, 16, 0, 0);

        listContainer = new LinearLayout(this);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(listContainer);
        root.addView(scrollView);

        setContentView(root);

        loadMessages(true);

        // Автообновление каждые 2 сек
        updater = new Runnable() {
            @Override public void run() {
                loadMessages(false);
                handler.postDelayed(this, 2000L);
            }
        };
        handler.postDelayed(updater, 2000L);
    }

    private void loadMessages(boolean forceScroll) {
        try {
            JSONArray arr = new JSONArray(
                    getSharedPreferences("GTAndroidChatLog", MODE_PRIVATE)
                            .getString("recent", "[]"));

            if (!forceScroll && arr.length() == lastCount) return;
            lastCount = arr.length();

            listContainer.removeAllViews();

            if (arr.length() == 0) {
                TextView empty = new TextView(this);
                empty.setText("Пока ничего не поймано.\nОткрой игру и напиши в чат.");
                empty.setTextColor(0xFFA78BFA);
                empty.setTextSize(14f);
                empty.setGravity(Gravity.CENTER);
                empty.setPadding(20, 60, 20, 60);
                listContainer.addView(empty);
            } else {
                SimpleDateFormat fmt = new SimpleDateFormat("HH:mm:ss", Locale.US);
                // Показываем последние 100
                int start = Math.max(0, arr.length() - 100);
                for (int i = arr.length() - 1; i >= start; i--) {
                    JSONObject o = arr.optJSONObject(i);
                    if (o == null) continue;
                    String text = o.optString("text", "");
                    long time = o.optLong("time", 0);

                    LinearLayout card = new LinearLayout(this);
                    card.setOrientation(LinearLayout.VERTICAL);
                    card.setPadding(20, 15, 20, 15);
                    card.setBackground(GlassUI.glassSelector(this, 0xFF2D1B4E));

                    TextView tvTime = new TextView(this);
                    tvTime.setText("🕐 " + fmt.format(new Date(time)));
                    tvTime.setTextColor(0xFFA78BFA);
                    tvTime.setTextSize(10f);
                    card.addView(tvTime);

                    TextView tvText = new TextView(this);
                    tvText.setText(text);
                    tvText.setTextColor(0xFFFFFFFF);
                    tvText.setTextSize(13f);
                    tvText.setPadding(0, 6, 0, 0);
                    card.addView(tvText);

                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                    lp.bottomMargin = 8;
                    listContainer.addView(card, lp);
                }
            }

            if (forceScroll) {
                scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
            }
        } catch (Exception ignored) { }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (updater != null) handler.removeCallbacks(updater);
    }
                  }
