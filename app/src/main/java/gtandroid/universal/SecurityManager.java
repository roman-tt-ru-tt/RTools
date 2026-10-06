package gtandroid.universal;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class SecurityManager {

    private static final int CURRENT_APP_VERSION = 1;

    private static final String KEY_ATTEMPTS_LEFT = "attempts_left";
    private static final String KEY_AUTH_EXPIRY = "auth_expiry";
    private static final String KEY_AUTH_STATUS = "auth_status";
    private static final String KEY_BLOCK_UNTIL = "block_until";
    private static final String KEY_IS_AUTHENTICATED = "is_authenticated";

    private static final String PREFS_NAME = "GTAndroidSecurity";

    private static final String UPDATE_FILE_URL =
            "https://www.dropbox.com/scl/fi/d15ay9947f1dbeco1f146/ss.txt?rlkey=v1w0qd35cgp1e9ph1pukqhbm3&st=wwhbbu6f&dl=1";

    private final Context context;
    private final SharedPreferences prefs;

    public interface SecurityCallback {
        void onResult(boolean success, String message);
    }

    public interface UpdateCallback {
        void onError(String error);

        void onUpToDate();

        void onUpdateRequired(String downloadUrl);
    }

    public SecurityManager(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        // Сбрасываем старые блокировки авторизации.
        this.prefs.edit()
                .remove(KEY_BLOCK_UNTIL)
                .remove(KEY_ATTEMPTS_LEFT)
                .putBoolean(KEY_IS_AUTHENTICATED, true)
                .putString(KEY_AUTH_STATUS, "premium")
                .apply();
    }

    /**
     * Проверка доступа.
     *
     * Пароль отключён.
     * Доступ разрешается сразу.
     */
    public void checkAccess(final SecurityCallback callback) {
        if (callback == null) {
            return;
        }

        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                callback.onResult(true, null);
            }
        });
    }

    /**
     * Проверка пароля отключена.
     *
     * Метод сохранён, потому что его вызывает MainActivity.
     */
    public void verifyPassword(
            final String inputPassword,
            final SecurityCallback callback
    ) {
        if (callback == null) {
            return;
        }

        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                grantAccess(36500, "premium");
                callback.onResult(true, null);
            }
        });
    }

    /**
     * Количество попыток.
     *
     * Оставлено для совместимости с MainActivity.
     */
    public int getAttemptsLeft() {
        return 3;
    }

    /**
     * Статус доступа.
     */
    public String getAuthStatus() {
        return "premium";
    }

    /**
     * Сохраняем состояние разрешённого доступа.
     */
    private void grantAccess(int days, String status) {
        long expiryTime =
                System.currentTimeMillis()
                        + (days * 24L * 60L * 60L * 1000L);

        prefs.edit()
                .putBoolean(KEY_IS_AUTHENTICATED, true)
                .putLong(KEY_AUTH_EXPIRY, expiryTime)
                .putInt(KEY_ATTEMPTS_LEFT, 3)
                .putString(KEY_AUTH_STATUS, status)
                .remove(KEY_BLOCK_UNTIL)
                .apply();
    }

    /**
     * Оставлено для совместимости.
     */
    private void handleFailedAttempt() {
        // Пароль отключён — блокировка не применяется.
    }

    /**
     * Проверка обновлений.
     *
     * Эта часть оставлена без изменения.
     */
    private void checkForUpdates(final UpdateCallback callback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String content = downloadString(UPDATE_FILE_URL);

                    if (content != null && !content.trim().isEmpty()) {

                        String[] lines = content.split("\\n");

                        String downloadUrl = null;

                        int i = 0;

                        while (i < lines.length - 1) {

                            String versionStr = lines[i].trim();
                            String url = lines[i + 1].trim();

                            int version;

                            try {
                                version = Integer.parseInt(versionStr);
                            } catch (NumberFormatException e) {
                                version = -1;
                            }

                            if (version == CURRENT_APP_VERSION) {
                                downloadUrl = url;
                                break;
                            }

                            i += 2;
                        }

                        if (downloadUrl != null && !downloadUrl.isEmpty()) {

                            final String finalUrl = downloadUrl;

                            new Handler(Looper.getMainLooper()).post(
                                    new Runnable() {
                                        @Override
                                        public void run() {
                                            callback.onUpdateRequired(finalUrl);
                                        }
                                    }
                            );

                        } else {

                            new Handler(Looper.getMainLooper()).post(
                                    new Runnable() {
                                        @Override
                                        public void run() {
                                            callback.onUpToDate();
                                        }
                                    }
                            );
                        }

                    } else {

                        new Handler(Looper.getMainLooper()).post(
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        callback.onError(
                                                "Файл обновлений не найден"
                                        );
                                    }
                                }
                        );
                    }

                } catch (final Exception e) {

                    new Handler(Looper.getMainLooper()).post(
                            new Runnable() {
                                @Override
                                public void run() {
                                    callback.onError(
                                            "Ошибка проверки обновлений: "
                                                    + e.getMessage()
                                    );
                                }
                            }
                    );
                }
            }
        }).start();
    }

    /**
     * Загрузка удалённого текста.
     */
    private String downloadString(String urlStr) throws Exception {

        URL url = new URL(urlStr);

        HttpURLConnection connection =
                (HttpURLConnection) url.openConnection();

        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.setRequestMethod("GET");

        BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(
                                connection.getInputStream()
                        )
                );

        StringBuilder result = new StringBuilder();

        String line;

        while ((line = reader.readLine()) != null) {
            result.append(line).append("\n");
        }

        reader.close();
        connection.disconnect();

        return result.toString();
    }
    }
