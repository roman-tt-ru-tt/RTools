package gtandroid.universal;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/* loaded from: classes2.dex */
public class SecurityManager {
    private static final int CURRENT_APP_VERSION = 1;
    private static final String KEY_ATTEMPTS_LEFT = "attempts_left";
    private static final String KEY_AUTH_EXPIRY = "auth_expiry";
    private static final String KEY_AUTH_STATUS = "auth_status";
    private static final String KEY_BLOCK_UNTIL = "block_until";
    private static final String KEY_IS_AUTHENTICATED = "is_authenticated";
    private static final String PREFS_NAME = "GTAndroidSecurity";
    private static final String UPDATE_FILE_URL = "https://www.dropbox.com/scl/fi/d15ay9947f1dbeco1f146/ss.txt?rlkey=v1w0qd35cgp1e9ph1pukqhbm3&st=wwhbbu6f&dl=1";
    private static final long authExpiry = 0;
    private Context context;
    private SharedPreferences prefs;

    public interface SecurityCallback {
        void onResult(boolean z, String str);
    }

    public interface UpdateCallback {
        void onError(String str);

        void onUpToDate();

        void onUpdateRequired(String str);
    }

    public SecurityManager(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS_NAME, 0);
    }

    public void checkAccess(final SecurityCallback callback) {
        long blockUntil = this.prefs.getLong(KEY_BLOCK_UNTIL, 0L);
        if (System.currentTimeMillis() < blockUntil) {
            long timeLeft = blockUntil - System.currentTimeMillis();
            long hours = timeLeft / 3600000;
            callback.onResult(false, "Доступ заблокирован. Осталось часов: " + hours);
        } else {
            if (blockUntil > 0) {
                this.prefs.edit().remove(KEY_BLOCK_UNTIL).remove(KEY_ATTEMPTS_LEFT).apply();
            }
            checkForUpdates(new UpdateCallback() { // from class: gtandroid.universal.SecurityManager.1
                @Override // gtandroid.universal.SecurityManager.UpdateCallback
                public void onUpdateRequired(String downloadUrl) {
                    callback.onResult(false, "UPDATE_REQUIRED: " + downloadUrl);
                }

                @Override // gtandroid.universal.SecurityManager.UpdateCallback
                public void onUpToDate() {
                    long authExpiry2 = SecurityManager.this.prefs.getLong(SecurityManager.KEY_AUTH_EXPIRY, 0L);
                    boolean isAuthenticated = SecurityManager.this.prefs.getBoolean(SecurityManager.KEY_IS_AUTHENTICATED, false);
                    String status = SecurityManager.this.prefs.getString(SecurityManager.KEY_AUTH_STATUS, "");
                    if (isAuthenticated && ("premium".equalsIgnoreCase(status) || System.currentTimeMillis() < authExpiry2)) {
                        callback.onResult(true, null);
                    } else {
                        callback.onResult(false, "PASSWORD_REQUIRED");
                    }
                }

                @Override // gtandroid.universal.SecurityManager.UpdateCallback
                public void onError(String error) {
                    callback.onResult(false, "ERROR: " + error);
                }
            });
        }
    }

    public void verifyPassword(final String inputPassword, final SecurityCallback callback) {
        new Thread(new Runnable() { // from class: gtandroid.universal.SecurityManager.2
            @Override // java.lang.Runnable
            public void run() {
                String[] parts;
                try {
                    String content = SecurityManager.this.downloadString("https://www.dropbox.com/scl/fi/4kyl7zhn1gab6mqkd0uyh/password.txt?rlkey=xkxh0lmxdsvxyr96bs5so6m9r&st=0q73l990&dl=1");
                    if (content != null && !content.isEmpty()) {
                        String[] lines = content.split("\n");
                        boolean found = false;
                        int length = lines.length;
                        char c = 0;
                        String statusValid = "bronze";
                        int i = 0;
                        int daysValid = 0;
                        while (true) {
                            if (i >= length) {
                                break;
                            }
                            String line = lines[i].trim();
                            if (!line.isEmpty()) {
                                if (line.contains("|")) {
                                    parts = line.split("\\|");
                                } else {
                                    parts = line.split("\\s+");
                                }
                                if (parts.length >= 2) {
                                    String pwd = parts[c].trim();
                                    try {
                                        daysValid = Integer.parseInt(parts[SecurityManager.CURRENT_APP_VERSION].trim());
                                        if (parts.length >= 3) {
                                            statusValid = parts[2].trim();
                                        } else {
                                            statusValid = "bronze";
                                        }
                                        String statusValid2 = inputPassword;
                                        if (pwd.equals(statusValid2)) {
                                            found = true;
                                            break;
                                        }
                                    } catch (NumberFormatException e) {
                                        e.printStackTrace();
                                    }
                                } else {
                                    continue;
                                }
                            }
                            i += SecurityManager.CURRENT_APP_VERSION;
                            c = 0;
                        }
                        if (found) {
                            SecurityManager.this.grantAccess(daysValid, statusValid);
                            SecurityManager.this.postResult(callback, true, null);
                        } else {
                            SecurityManager.this.handleFailedAttempt();
                            SecurityManager.this.postResult(callback, false, "Неверный пароль");
                        }
                        return;
                    }
                    SecurityManager.this.postError(callback, "Ошибка соединения с сервером паролей");
                } catch (Exception e2) {
                    SecurityManager.this.postError(callback, "Ошибка проверки: " + e2.getMessage());
                }
            }
        }).start();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void grantAccess(int days, String status) {
        long expiryTime;
        if ("premium".equalsIgnoreCase(status)) {
            expiryTime = System.currentTimeMillis() + 315360000000L;
        } else {
            long expiryTime2 = System.currentTimeMillis();
            expiryTime = expiryTime2 + (days * 24 * 60 * 60 * 1000);
        }
        this.prefs.edit().putBoolean(KEY_IS_AUTHENTICATED, true).putLong(KEY_AUTH_EXPIRY, expiryTime).putInt(KEY_ATTEMPTS_LEFT, 3).putString(KEY_AUTH_STATUS, status).apply();
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void handleFailedAttempt() {
        int attempts = this.prefs.getInt(KEY_ATTEMPTS_LEFT, 3) - 1;
        if (attempts <= 0) {
            long blockTime = System.currentTimeMillis() + 86400000;
            this.prefs.edit().putLong(KEY_BLOCK_UNTIL, blockTime).putInt(KEY_ATTEMPTS_LEFT, 0).apply();
        } else {
            this.prefs.edit().putInt(KEY_ATTEMPTS_LEFT, attempts).apply();
        }
    }

    public int getAttemptsLeft() {
        return this.prefs.getInt(KEY_ATTEMPTS_LEFT, 3);
    }

    public String getAuthStatus() {
        return this.prefs.getString(KEY_AUTH_STATUS, "bronze");
    }

    private void checkForUpdates(final UpdateCallback callback) {
        new Thread(new Runnable() { // from class: gtandroid.universal.SecurityManager.3
            @Override // java.lang.Runnable
            public void run() {
                int version;
                try {
                    String content = SecurityManager.this.downloadString(SecurityManager.UPDATE_FILE_URL);
                    if (content != null && !content.trim().isEmpty()) {
                        String[] lines = content.split("\n");
                        String downloadUrl = null;
                        int i = 0;
                        while (true) {
                            if (i >= lines.length - SecurityManager.CURRENT_APP_VERSION) {
                                break;
                            }
                            String versionStr = lines[i].trim();
                            String url = lines[i + SecurityManager.CURRENT_APP_VERSION].trim();
                            try {
                                version = Integer.parseInt(versionStr);
                            } catch (NumberFormatException e) {
                                version = -1;
                            }
                            if (version != SecurityManager.CURRENT_APP_VERSION) {
                                i += 2;
                            } else {
                                downloadUrl = url;
                                break;
                            }
                        }
                        if (downloadUrl != null && !downloadUrl.isEmpty()) {
                            final String finalUrl = downloadUrl;
                            new Handler(Looper.getMainLooper()).post(new Runnable() { // from class: gtandroid.universal.SecurityManager.3.2
                                @Override // java.lang.Runnable
                                public void run() {
                                    callback.onUpdateRequired(finalUrl);
                                }
                            });
                        } else {
                            new Handler(Looper.getMainLooper()).post(new Runnable() { // from class: gtandroid.universal.SecurityManager.3.3
                                @Override // java.lang.Runnable
                                public void run() {
                                    callback.onUpToDate();
                                }
                            });
                        }
                        return;
                    }
                    new Handler(Looper.getMainLooper()).post(new Runnable() { // from class: gtandroid.universal.SecurityManager.3.1
                        @Override // java.lang.Runnable
                        public void run() {
                            callback.onError("Файл обновлений не найден");
                        }
                    });
                } catch (Exception e2) {
                    new Handler(Looper.getMainLooper()).post(new Runnable() { // from class: gtandroid.universal.SecurityManager.3.4
                        @Override // java.lang.Runnable
                        public void run() {
                            callback.onError("Ошибка проверки обновлений: " + e2.getMessage());
                        }
                    });
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

    /* JADX INFO: Access modifiers changed from: private */
    public void postResult(final SecurityCallback callback, final boolean success, final String msg) {
        new Handler(Looper.getMainLooper()).post(new Runnable() { // from class: gtandroid.universal.SecurityManager.4
            @Override // java.lang.Runnable
            public void run() {
                callback.onResult(success, msg);
            }
        });
    }

    /* JADX INFO: Access modifiers changed from: private */
    public void postError(final SecurityCallback callback, final String msg) {
        new Handler(Looper.getMainLooper()).post(new Runnable() { // from class: gtandroid.universal.SecurityManager.5
            @Override // java.lang.Runnable
            public void run() {
                callback.onResult(false, "ERROR: " + msg);
            }
        });
    }
}
