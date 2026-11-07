package gtandroid.universal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Разбор строки репорта вида  "Ник[ID]: текст"  (с необязательным хвостом "[К-во репорта: N]").
 *
 * Если в тексте репорта есть число из 1-4 цифр (ID нарушителя), оно попадает в {@link Report#targets}
 * — по нему клавиатура рисует кнопку «Следить за [ID]».
 */
public final class ReportParser {

    /** Максимум цифр в ID нарушителя. */
    public static final int MAX_TARGET_DIGITS = 4;
    /** Максимум кнопок «Следить за» на один репорт. */
    public static final int MAX_TARGETS = 3;

    private static final Pattern COUNTER_TAIL = Pattern.compile(
            "\\s*\\[\\s*К-во репорта\\s*:\\s*\\d+\\s*\\]\\s*$");

    private static final Pattern LINE = Pattern.compile(
            "^\\s*(\\S+?)\\s*\\[\\s*(\\d{1,7})\\s*\\]\\s*:\\s*(.+?)\\s*$");

    /** Число из 1-4 цифр, не являющееся частью более длинного числа. "2ДМ" -> 2, "12345" -> нет. */
    private static final Pattern TARGET = Pattern.compile(
            "(?<!\\d)\\d{1," + MAX_TARGET_DIGITS + "}(?!\\d)");

    private ReportParser() { }

    public static final class Report {
        /** true, если строка распознана как "Ник[ID]: текст". */
        public final boolean parsed;
        public final String nick;
        public final String playerId;
        public final String message;
        /** Чистая строка без хвоста "[К-во репорта: N]". */
        public final String full;
        /** ID нарушителей из текста (без ID автора, без повторов). */
        public final List<String> targets;

        Report(boolean parsed, String nick, String playerId, String message,
               String full, List<String> targets) {
            this.parsed = parsed;
            this.nick = nick;
            this.playerId = playerId;
            this.message = message;
            this.full = full;
            this.targets = targets;
        }

        /** Ключ для защиты от дублей. */
        public String key() {
            return playerId + "|" + message;
        }
    }

    public static Report parse(String rawLine) {
        String line = rawLine == null ? "" : rawLine.trim();
        line = COUNTER_TAIL.matcher(line).replaceFirst("").trim();

        Matcher m = LINE.matcher(line);
        if (!m.matches()) {
            return new Report(false, "", "", line, line,
                    Collections.<String>emptyList());
        }
        String nick = m.group(1).trim();
        String id = m.group(2).trim();
        String text = m.group(3).trim();
        return new Report(true, nick, id, text, line, extractTargets(text, id));
    }

    static List<String> extractTargets(String text, String authorId) {
        ArrayList<String> out = new ArrayList<>();
        Matcher t = TARGET.matcher(text);
        while (t.find() && out.size() < MAX_TARGETS) {
            String n = stripZeros(t.group());
            if (n.equals(stripZeros(authorId))) continue;
            if (!out.contains(n)) out.add(n);
        }
        return out;
    }

    private static String stripZeros(String s) {
        int i = 0;
        while (i < s.length() - 1 && s.charAt(i) == '0') i++;
        return s.substring(i);
    }
}
