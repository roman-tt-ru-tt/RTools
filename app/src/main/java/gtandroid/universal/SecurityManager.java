package gtandroid.universal;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

/**
 * Авторизация: ник + ключ RTOOLS.
 * Уникальность ника — через удалённый ss.txt на Dropbox.
 */
public class SecurityManager {

    /** Версия приложения (для проверки обновлений). */
    private static final int CURRENT_APP_VERSION = 1;

    /** URL файла ss.txt (прямая ссылка, dl=1). */
    private static final String SS_FILE_URL =
            "https://www.dropbox.com/scl/fi/lfg2wzpl6kb2gi92ugj4y/ss.txt?rlkey=piy3f11s0myxhr5kbzbr5jdo9&st=jlf8q07y&dl=1";

    /** Ключ доступа. */
    private static final String ACCESS_KEY = "RTOOLS";

    /** Префиксы в ss.txt. */
    private static final String NICK_PREFIX = "NICK|";
    private static final String VERSION_SEPARATOR = "|";

    private static final String PREFS_NAME = "GTAndroidSecurity";
    private static final String KEY_NICKNAME = "nickname";
    private static final String KEY_AUTHENTICATED = "is_authenticated";
    private static final String KEY_AUTH_STATUS = "auth_status";
    private static final String KEY_LAST_UPDATE_CHECK = "last_update_check";

    private final Context context;
    private final SharedPreferences prefs;

    public interface SecurityCallback {
        /** success=true → можно запускать UI. message — текст ошибки, если success=false. */
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
    }

    /** Уже авторизован на этом устройстве? */
    public boolean isAuthenticated() {
        return prefs.getBoolean(KEY_AUTHENTICATED, false)
                && !prefs.getString(KEY_NICKNAME, "").isEmpty();
    }

    /** Получить сохранённый ник. */
    public String getNickname() {
        return prefs.getString(KEY_NICKNAME, "");
    }

    /**
     * Основная проверка доступа.
     * - Если уже авторизован — пропускаем.
     * - Иначе — просим ник+ключ.
     */
    public void checkAccess(final SecurityCallback callback) {
        if (callback == null) return;
        if (isAuthenticated()) {
            // Уже вошли — просто проверяем обновления в фоне
            new Handler(Looper.getMainLooper()).post(() ->
                    callback.onResult(true, null));
            checkForUpdatesQuietly();
            return;
        }
        // Не авторизован — MainActivity покажет диалог
        new Handler(Looper.getMainLooper()).post(() ->
                callback.onResult(false, "AUTH_REQUIRED"));
    }

    /**
     * Проверка ника и ключа.
     * @param nickname введённый ник
     * @param key введённый ключ
     * @param callback результат
     */
    public void verifyCredentials(final String nickname, final String key,
                                  final SecurityCallback callback) {
        if (callback == null) return;

        new Thread(() -> {
            // 1. Локальная проверка ключа
            if (key == null || !ACCESS_KEY.equals(key.trim())) {
                postResult(callback, false, "Неверный ключ доступа");
                return;
            }

            // 2. Проверка ника (формат)
            final String nick = nickname == null ? "" : nickname.trim();
            if (nick.length() < 3) {
                postResult(callback, false, "Ник должен быть не короче 3 символов");
                return;
            }
            if (nick.length() > 24) {
                postResult(callback, false, "Ник слишком длинный (макс. 24)");
                return;
            }
            if (!nick.matches("[A-Za-z0-9_\\-]+")) {
                postResult(callback, false,
                        "Ник может содержать только латиницу, цифры, _ и -");
                return;
            }

            // 3. Загрузка ss.txt и проверка занятости ника
            try {
                String content = downloadString(SS_FILE_URL);
                if (content == null || content.isEmpty()) {
                    postResult(callback, false, "Не удалось получить список ников");
                    return;
                }

                Set<String> taken = parseTakenNicks(content);
                // Ник не должен быть занят ДРУГИМИ (сравниваем без регистра)
                for (String t : taken) {
                    if (t.equalsIgnoreCase(nick)) {
                        postResult(callback, false,
                                "Этот ник уже занят. Выберите другой.");
                        return;
                    }
                }

                // 4. Успех — сохраняем
                saveAuth(nick);
                postResult(callback, true, null);

            } catch (Exception e) {
                postResult(callback, false,
                        "Ошибка проверки: " + e.getMessage());
            }
        }).start();
    }

    /** Сохранить состояние авторизации. */
    private void saveAuth(String nickname) {
        prefs.edit()
                .putBoolean(KEY_AUTHENTICATED, true)
                .putString(KEY_NICKNAME, nickname)
                .putString(KEY_AUTH_STATUS, "user")
                .apply();
    }

    /** Выход (на всякий случай). */
    public void logout() {
        prefs.edit()
                .remove(KEY_AUTHENTICATED)
                .remove(KEY_NICKNAME)
                .apply();
    }

    /** Разбор ss.txt: собираем занятые ники. */
    private Set<String> parseTakenNicks(String content) {
        Set<String> out = new HashSet<>();
        String[] lines = content.split("\\r?\\n");
        for (String raw : lines) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (line.startsWith(NICK_PREFIX)) {
                String nick = line.substring(NICK_PREFIX.length()).trim();
                if (!nick.isEmpty()) out.add(nick);
            }
        }
        return out;
    }

    /** Загрузка текста по URL. */
    private String downloadString(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setConnectTimeout(7000);
        c.setReadTimeout(7000);
        c.setRequestMethod("GET");
        c.setRequestProperty("User-Agent", "RTools/1.0");
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(c.getInputStream(), "UTF-8"))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        } finally {
            c.disconnect();
        }
    }

    /** Отправка результата в UI-поток. */
    private void postResult(final SecurityCallback cb,
                            final boolean ok, final String msg) {
        new Handler(Looper.getMainLooper()).post(() -> cb.onResult(ok, msg));
    }

    /** Тихая проверка обновлений (не мешает пользователю). */
    private void checkForUpdatesQuietly() {
        new Thread(() -> {
            try {
                String content = downloadString(SS_FILE_URL);
                if (content == null) return;
                String[] lines = content.split("\\r?\\n");
                for (int i = 0; i < lines.length - 1; i++) {
                    String versionStr = lines[i].trim();
                    if (versionStr.isEmpty() || versionStr.startsWith("#")
                            || versionStr.startsWith(NICK_PREFIX)) continue;
                    if (!versionStr.contains(VERSION_SEPARATOR)) continue;
                    String[] parts = versionStr.split("\\|", 2);
                    int ver;
                    try { ver = Integer.parseInt(parts[0].trim()); }
                    catch (NumberFormatException e) { continue; }
                    if (ver == CURRENT_APP_VERSION
                            && parts.length > 1
                            && !parts[1].trim().isEmpty()) {
                        final String url = parts[1].trim();
                        new Handler(Looper.getMainLooper()).post(() -> {
                            // Ничего не делаем — можно потом показать диалог
                        });
                        return;
                    }
                }
            } catch (Exception ignored) { }
        }).start();
    }

    /** Публичная проверка обновлений (если понадобится). */
    public void checkForUpdates(final UpdateCallback callback) {
        new Thread(() -> {
            try {
                String content = downloadString(SS_FILE_URL);
                if (content == null) {
                    postUpdate(callback, () -> callback.onError("Нет данных"));
                    return;
                }
                String[] lines = content.split("\\r?\\n");
                for (int i = 0; i < lines.length - 1; i++) {
                    String v = lines[i].trim();
                    if (!v.contains(VERSION_SEPARATOR)
                            || v.startsWith("#")
                            || v.startsWith(NICK_PREFIX)) continue;
                    String[] parts = v.split("\\|", 2);
                    int ver;
                    try { ver = Integer.parseInt(parts[0].trim()); }
                    catch (NumberFormatException e) { continue; }
                    if (ver == CURRENT_APP_VERSION) {
                        if (parts.length > 1 && !parts[1].trim().isEmpty()) {
                            final String url = parts[1].trim();
                            postUpdate(callback, () ->
                                    callback.onUpdateRequired(url));
                        } else {
                            postUpdate(callback, callback::onUpToDate);
                        }
                        return;
                    }
                }
                postUpdate(callback, callback::onUpToDate);
            } catch (final Exception e) {
                postUpdate(callback, () ->
                        callback.onError(e.getMessage()));
            }
        }).start();
    }

    private void postUpdate(final UpdateCallback cb, final Runnable r) {
        new Handler(Looper.getMainLooper()).post(r);
    }
                        }
