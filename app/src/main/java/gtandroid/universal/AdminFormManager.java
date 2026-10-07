package gtandroid.universal;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Хранилище и обработчик ADMIN-форм.
 * Формат входящего сообщения:
 * <ADM> (1) Roman_Gupalovich[255]: /mute 1 60 2.1 ОП
 */
public final class AdminFormManager {
    private static final String PREFS = "GTAndroidData";
    private static final String KEY_PENDING = "admin_forms_pending";
    private static final String KEY_PUNISHMENTS = "admin_form_punishments";

    private static final Pattern FORM_PATTERN = Pattern.compile(
            "<ADM>\\s*\\(([^)]*)\\)\\s+([^\\s\\[]+)\\[(\\d+)\\]\\s*:\\s*(/[^\\r\\n]+)",
            Pattern.CASE_INSENSITIVE);

    private static final String DEFAULT_PUNISHMENTS =
            "/mute,/rmute,/ban,/rban,/warn,/rwarn,/jail,/unjail,/unmute,/unban,/unwarn,/kick";

    private AdminFormManager() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static synchronized ArrayList<Form> getPending(Context context) {
        ArrayList<Form> result = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs(context).getString(KEY_PENDING, "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.optJSONObject(i);
                if (o != null) result.add(Form.fromJson(o));
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    public static synchronized boolean addForm(Context context, Form form) {
        try {
            ArrayList<Form> forms = getPending(context);
            String key = form.uniqueKey();
            for (Form existing : forms) {
                if (existing.uniqueKey().equals(key)) return false;
            }
            forms.add(0, form);
            while (forms.size() > 50) forms.remove(forms.size() - 1);
            savePending(context, forms);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    public static synchronized void removeForm(Context context, String uniqueKey) {
        ArrayList<Form> forms = getPending(context);
        for (int i = forms.size() - 1; i >= 0; i--) {
            if (forms.get(i).uniqueKey().equals(uniqueKey)) forms.remove(i);
        }
        savePending(context, forms);
    }

    public static synchronized void clearForms(Context context) {
        prefs(context).edit().remove(KEY_PENDING).apply();
    }

    private static void savePending(Context context, ArrayList<Form> forms) {
        JSONArray array = new JSONArray();
        for (Form form : forms) array.put(form.toJson());
        prefs(context).edit().putString(KEY_PENDING, array.toString()).apply();
    }

    public static ArrayList<Form> parseForms(String text) {
        ArrayList<Form> result = new ArrayList<>();
        if (text == null || text.indexOf("<ADM>") < 0) return result;
        Matcher matcher = FORM_PATTERN.matcher(text);
        while (matcher.find()) {
            String command = matcher.group(4).trim();
            if (!command.startsWith("/")) continue;
            result.add(new Form(
                    matcher.group(1).trim(),
                    matcher.group(2).trim(),
                    matcher.group(3).trim(),
                    command,
                    System.currentTimeMillis()));
        }
        return result;
    }

    public static Set<String> getPunishmentCommands(Context context) {
        String value = prefs(context).getString(KEY_PUNISHMENTS, DEFAULT_PUNISHMENTS);
        HashSet<String> result = new HashSet<>();
        for (String item : value.split(",")) {
            String command = item.trim().toLowerCase(Locale.ROOT);
            if (!command.isEmpty()) {
                if (!command.startsWith("/")) command = "/" + command;
                result.add(command);
            }
        }
        return result;
    }

    public static String getPunishmentCommandsText(Context context) {
        String value = prefs(context).getString(KEY_PUNISHMENTS, DEFAULT_PUNISHMENTS);
        return value.replace(",", ", ");
    }

    public static void setPunishmentCommands(Context context, String text) {
        StringBuilder normalized = new StringBuilder();
        for (String item : text.split("[,\\n;]+")) {
            String command = item.trim().toLowerCase(Locale.ROOT);
            if (command.isEmpty()) continue;
            if (!command.startsWith("/")) command = "/" + command;
            if (normalized.length() > 0) normalized.append(',');
            normalized.append(command);
        }
        prefs(context).edit().putString(KEY_PUNISHMENTS,
                normalized.length() == 0 ? DEFAULT_PUNISHMENTS : normalized.toString()).apply();
    }

    public static String makeAcceptedCommand(Context context, Form form) {
        String raw = form.command == null ? "" : form.command.trim();
        if (!getPunishmentCommands(context).contains(firstCommand(raw))) {
            return raw;
        }

        // Для наказаний поддерживаем формат: (Roman_Blabla) /mute 1 60 2.1 ОП
        // В чат отправляется: /mute 1 60 2.1 ОП | R.Blabla
        Matcher targetMatcher = Pattern.compile("^\\(([^)]+)\\)\\s*(/.*)$").matcher(raw);
        if (targetMatcher.matches()) {
            String targetName = targetMatcher.group(1).trim();
            String command = targetMatcher.group(2).trim();
            return command + " | " + makeInitials(targetName);
        }

        // Если имя цели не указано в скобках, используем ник отправителя формы.
        return raw + " | " + makeInitials(form.nickname);
    }

    private static String firstCommand(String command) {
        if (command == null) return "";
        String value = command.trim();
        if (value.startsWith("(")) {
            int close = value.indexOf(')');
            if (close >= 0 && close + 1 < value.length()) {
                value = value.substring(close + 1).trim();
            }
        }
        int space = value.indexOf(' ');
        String result = space >= 0 ? value.substring(0, space) : value;
        return result.trim().toLowerCase(Locale.ROOT);
    }

    public static String makeInitials(String nickname) {
        if (nickname == null) return "";
        String value = nickname.trim();
        int underscore = value.indexOf('_');
        if (underscore > 0 && underscore < value.length() - 1) {
            String first = value.substring(0, underscore).trim();
            String last = value.substring(underscore + 1).trim();
            if (!first.isEmpty() && !last.isEmpty()) {
                return first.substring(0, 1).toUpperCase(Locale.ROOT) + "." + last;
            }
        }
        return value;
    }

    public static final class Form {
        public final String rank;
        public final String nickname;
        public final String id;
        public final String command;
        public final long createdAt;

        public Form(String rank, String nickname, String id, String command, long createdAt) {
            this.rank = rank;
            this.nickname = nickname;
            this.id = id;
            this.command = command;
            this.createdAt = createdAt;
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
