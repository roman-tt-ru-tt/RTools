package gtandroid.universal;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * SecurityManager для пользовательской сборки GTAndroid.
 *
 * Проверка пароля отключена.
 * Методы оставлены совместимыми с остальным приложением,
 * чтобы не пришлось менять вызовы из других классов.
 */
public class SecurityManager {

    private static final String PREFS_NAME = "security_prefs";

    private final Context context;
    private final SharedPreferences preferences;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public SecurityManager(Context context) {
        this.context = context.getApplicationContext();
        this.preferences = this.context.getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
        );
    }

    /**
     * Проверка доступа.
     *
     * В пользовательской версии пароль не требуется.
     */
    public void checkAccess(AccessCallback callback) {
        executor.execute(() -> {
            if (callback != null) {
                callback.onResult(true, null);
            }
        });
    }

    /**
     * Дополнительный вариант проверки доступа,
     * если его вызывает другой класс проекта.
     */
    public void verifyAccess(AccessCallback callback) {
        checkAccess(callback);
    }

    /**
     * Возвращает true, если доступ разрешён.
     */
    public boolean isAuthenticated() {
        return true;
    }

    /**
     * Авторизация больше не требуется.
     */
    public void authenticate(String password, AccessCallback callback) {
        if (callback != null) {
            callback.onResult(true, null);
        }
    }

    /**
     * Сбрасывает старое состояние авторизации.
     */
    public void logout() {
        preferences.edit()
                .clear()
                .apply();
    }

    /**
     * Удаляет старые данные блокировки/попыток.
     */
    public void resetSecurity() {
        preferences.edit()
                .clear()
                .apply();
    }

    /**
     * Проверка пароля отключена.
     */
    public boolean checkPassword(String password) {
        return true;
    }

    /**
     * Callback результата проверки.
     */
    public interface AccessCallback {
        void onResult(boolean success, String error);
    }

    /**
     * Освобождение ресурсов.
     */
    public void shutdown() {
        executor.shutdownNow();
    }
}
