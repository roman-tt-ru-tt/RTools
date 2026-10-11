package gtandroid.universal;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Хранилище ADM-форм + список команд (встроенных и кастомных).
 */
public final class AdminFormManager {

    private static final String PREFS = "GTAndroidData";
    private static final String KEY_PENDING = "admin_forms_pending";
    private static final String KEY_ENABLED = "admin_form_enabled_commands";
    private static final String KEY_CUSTOM = "admin_form_custom_commands";

    /** Встроенные команды, требующие инициалов. */
    private static final String[] BUILTIN_WITH_INITIALS = {
            "/offjail", "/jail",
            "/offmute", "/mute",
            "/offwarn", "/warn",
            "/offban", "/ban",
            "/rmute", "/kick", "/sban"
    };

    /** Встроенные команды, НЕ требующие инициалов. */
    private static final String[] BUILTIN_NO_INITIALS = {
            "/unjail", "/offunjail",
            "/unwarn", "/offunwarn",
            "/unban", "/unmute", "/runmute",
            "/skick", "/uncuff",
            "/slap", "/spawn", "/spcar", "/spcars",
            "/setskin", "/fixcar", "/flip",
            "/freeze", "/hp", "/arm",
            "/setfuel", "/rgun", "/unfreeze"
    };

    /** Regex для парсинга ADM-форм из игрового чата. */
    private static final Pattern FORM = Pattern.compile(
            "(?im)<\\s*ADM\\s*>\\s*\\(([^)]*)\\)\\s+\\(?([^\\s\\[)]+)\\)?\\[(\\d{1,8})\\]\\s*:\\s*([^\\r\\n]+)");

    private AdminFormManager() { }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ==================== СПИСОК КОМАНД ====================

    /** Все команды: встроенные + кастомные. */
    public static String[] getAllCommands() {
        List<String> list = new ArrayList<>();
        for (String s : BUILTIN_WITH_INITIALS) list.add(s);
        for (String s : BUILTIN_NO_INITIALS) list.add(s);
        return list.toArray(new String[0]);
    }

    /** Встроенные команды (для UI). */
    public static String[] getBuiltinCommands() {
        return getAllCommands();
    }

    /** Кастомные команды, добавленные пользователем. */
    public static List<String> getCustomCommands(Context c) {
        List<String> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(
                    prefs(c).getString(KEY_CUSTOM, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                String cmd = arr.optString(i, "").trim();
                if (!cmd.isEmpty()) out.add(cmd);
            }
        } catch (Exception ignored) { }
        return out;
    }

    /** Добавить кастомную команду. */
    public static boolean addCustomCommand(Context c, String command) {
        String cmd = normalize(command);
        if (cmd.isEmpty()) return false;

        // Проверка на дубликаты среди всех
        for (String s : getAllCommands())
            if (s.equalsIgnoreCase(cmd)) return false;
        for (String s : getCustomCommands(c))
            if (s.equalsIgnoreCase(cmd)) return false;

        try {
            JSONArray arr = new JSONArray(
                    prefs(c).getString(KEY_CUSTOM, "[]"));
            arr.put(cmd);
            prefs(c).edit().putString(KEY_CUSTOM, arr.toString()).apply();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Удалить кастомную команду. */
    public static boolean removeCustomCommand(Context c, String command) {
        String cmd = normalize(command);
        try {
            JSONArray arr = new JSONArray(
                    prefs(c).getString(KEY_CUSTOM, "[]"));
            JSONArray out = new JSONArray();
            boolean removed = false;
            for (int i = 0; i < arr.length(); i++) {
                String item = arr.optString(i, "");
                if (item.equalsIgnoreCase(cmd)) { removed = true; continue; }
                out.put(item);
            }
            if (!removed) return false;
            prefs(c).edit().putString(KEY_CUSTOM, out.toString()).apply();

            // Заодно убираем из включённых
            Set<String> enabled = getEnabledCommands(c);
            enabled.remove(cmd.toLowerCase(Locale.ROOT));
            setEnabledCommands(c, enabled);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Требует ли команда инициалов? */
    public static boolean requiresInitials(String command) {
        String c = firstCommand(command);
        for (String s : BUILTIN_WITH_INITIALS) if (s.equals(c)) return true;
        return false;
    }

    // ==================== ВКЛЮЧЁННЫЕ КОМАНДЫ ====================

    /** Команды, формы которых нужно ловить. */
    public static synchronized Set<String> getEnabledCommands(Context c) {
        if (!prefs(c).contains(KEY_ENABLED)) {
            // Первый запуск — включаем все команды с инициалами
            LinkedHashSet<String> def = new LinkedHashSet<>();
            for (String s : BUILTIN_WITH_INITIALS) def.add(s);
            return def;
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        String raw = prefs(c).getString(KEY_ENABLED, "");
        for (String s : raw.split(",")) {
            String n = normalize(s);
            if (!n.isEmpty()) out.add(n);
        }
        return out;
    }

    public static synchronized void setEnabledCommands(Context c, Set<String> commands) {
        StringBuilder sb = new StringBuilder();
        for (String s : commands) {
            String n = normalize(s);
            if (n.isEmpty()) continue;
            if (sb.length() > 0) sb.append(',');
            sb.append(n);
        }
        prefs(c).edit().putString(KEY_ENABLED, sb.toString()).apply();
    }

    public static boolean isCommandEnabled(Context c, String command) {
        return getEnabledCommands(c).contains(firstCommand(command));
    }

    // ==================== ХРАНИЛИЩЕ ФОРМ ====================

    public static synchronized ArrayList<Form> getPending(Context c) {
        ArrayList<Form> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(
                    prefs(c).getString(KEY_PENDING, "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o != null) out.add(Form.fromJson(o));
            }
        } catch (Exception ignored) { }
        return out;
    }

    public static synchronized boolean addForm(Context c, Form f) {
        if (f == null || !isCommandEnabled(c, f.command)) return false;
        ArrayList<Form> a = getPending(c);
        for (Form old : a)
            if (old.uniqueKey().equals(f.uniqueKey())) return false;
        a.add(0, f);
        while (a.size() > 100) a.remove(a.size() - 1);
        save(c, a);
        return true;
    }

    public static synchronized void removeForm(Context c, String key) {
        ArrayList<Form> a = getPending(c);
        for (int i = a.size() - 1; i >= 0; i--)
            if (a.get(i).uniqueKey().equals(key)) a.remove(i);
        save(c, a);
    }

    public static synchronized void clearForms(Context c) {
        prefs(c).edit().remove(KEY_PENDING).apply();
    }

    private static void save(Context c, ArrayList<Form> forms) {
        JSONArray a = new JSONArray();
        for (Form f : forms) {
            try { a.put(f.toJson()); } catch (Exception ignored) { }
        }
        prefs(c).edit().putString(KEY_PENDING, a.toString()).apply();
    }

    // ==================== ПАРСЕР ====================

    public static ArrayList<Form> parseForms(String text) {
        ArrayList<Form> out = new ArrayList<>();
        if (text == null) return out;
        Matcher m = FORM.matcher(text.replace("< ADM>", "<ADM>"));
        while (m.find()) {
            String command = m.group(4).trim();
            if (!command.startsWith("/")) continue;
            String first = firstCommand(command);

            // Проверяем, что команда известна (встроенная или кастомная).
            // Для парсера без контекста — пропускаем только если не известна.
            boolean known = false;
            for (String s : getAllCommands())
                if (s.equals(first)) { known = true; break; }

            // Кастомные команды не проверяем здесь — они будут отфильтрованы
            // через isCommandEnabled при добавлении в БД.
            if (known || first.startsWith("/")) {
                out.add(new Form(
                        m.group(1).trim(),   // rank
                        m.group(2).trim(),   // nickname
                        m.group(3).trim(),   // id
                        command,
                        System.currentTimeMillis()));
            }
        }
        return out;
    }

    // ==================== ФОРМИРОВАНИЕ КОМАНДЫ ====================

    public static String makeAcceptedCommand(Context c, Form f) {
        String raw = f.command == null ? "" : f.command.trim();
        if (!requiresInitials(raw)) return raw;

        Matcher target = Pattern.compile("^\\(([^)]+)\\)\\s*(/.*)$").matcher(raw);
        if (target.matches())
            return target.group(2).trim() + " | " + makeInitials(target.group(1).trim());
        if (raw.contains(" | ")) return raw;

        String initials = makeInitials(f.nickname);
        return initials.isEmpty() ? raw : raw + " | " + initials;
    }

    public static String firstCommand(String command) {
        if (command == null) return "";
        String v = command.trim();
        if (v.startsWith("(")) {
            int i = v.indexOf(')');
            if (i >= 0 && i + 1 < v.length()) v = v.substring(i + 1).trim();
        }
        int s = v.indexOf(' ');
        return normalize(s >= 0 ? v.substring(0, s) : v);
    }

    private static String normalize(String s) {
        if (s == null) return "";
        s = s.trim().toLowerCase(Locale.ROOT);
        if (!s.isEmpty() && !s.startsWith("/")) s = "/" + s;
        return s;
    }

    public static String makeInitials(String nickname) {
        if (nickname == null) return "";
        String v = nickname.trim();
        int i = v.indexOf('_');
        if (i > 0 && i < v.length() - 1) {
            return v.substring(0, 1).toUpperCase(Locale.ROOT)
                    + "." + v.substring(i + 1);
        }
        return v;
    }

    // ==================== МОДЕЛЬ ФОРМЫ ====================

    public static final class Form {
        public final String rank;
        public final String nickname;
        public final String id;
        public final String command;
        public final long createdAt;

        public Form(String r, String n, String i, String c, long t) {
            this.rank = r; this.nickname = n; this.id = i;
            this.command = c; this.createdAt = t;
        }

        public String uniqueKey() {
            return rank + "|" + nickname + "|" + id + "|" + command;
        }

        public JSONObject toJson() throws Exception {
            JSONObject o = new JSONObject();
            o.put("rank", rank);
            o.put("nickname", nickname);
            o.put("id", id);
            o.put("command", command);
            o.put("createdAt", createdAt);
            return o;
        }

        public static Form fromJson(JSONObject o) {
            return new Form(
                    o.optString("rank", ""),
                    o.optString("nickname", ""),
                    o.optString("id", ""),
                    o.optString("command", ""),
                    o.optLong("createdAt", System.currentTimeMillis()));
        }
    }
                }
