package gtandroid.universal;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/**
 * Очередь пойманных репортов в памяти процесса.
 * Служба доступности и клавиатура живут в одном процессе, поэтому делят её напрямую.
 */
public final class ReportQueue {

    private static final String PREFS = "GTAndroidData";
    private static final String KEY_TTL_SEC = "report_ttl_seconds";
    private static final int DEFAULT_TTL_SEC = 60;
    private static final int MAX_ITEMS = 30;

    private static final ArrayList<Item> ITEMS = new ArrayList<>();
    private static int version = 0;

    private ReportQueue() { }

    public static final class Item {
        public final ReportParser.Report report;
        public final long capturedAt;

        Item(ReportParser.Report report, long capturedAt) {
            this.report = report;
            this.capturedAt = capturedAt;
        }
    }

    /** Сколько живёт репорт в панели (секунды). Настраивается ключом report_ttl_seconds (5..300). */
    public static long ttlMs(Context c) {
        SharedPreferences sp = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int sec = sp.getInt(KEY_TTL_SEC, DEFAULT_TTL_SEC);
        if (sec < 5) sec = 5;
        if (sec > 300) sec = 300;
        return sec * 1000L;
    }

    /** Добавляет репорт. false — такой же уже висит в очереди. */
    public static synchronized boolean offer(Context c, ReportParser.Report r) {
        if (r == null || r.message.isEmpty()) return false;
        prune(c);
        for (Item it : ITEMS) {
            if (it.report.key().equals(r.key())) return false;
        }
        ITEMS.add(new Item(r, System.currentTimeMillis()));
        while (ITEMS.size() > MAX_ITEMS) ITEMS.remove(0);
        version++;
        return true;
    }

    /** Актуальные репорты, новые сверху. */
    public static synchronized List<Item> snapshot(Context c) {
        prune(c);
        ArrayList<Item> out = new ArrayList<>(ITEMS);
        java.util.Collections.reverse(out);
        return out;
    }

    public static synchronized int count(Context c) {
        prune(c);
        return ITEMS.size();
    }

    public static synchronized void remove(Item item) {
        if (ITEMS.remove(item)) version++;
    }

    /** Меняется при добавлении/удалении/истечении — по нему панель понимает, что надо перерисоваться. */
    public static synchronized int version() {
        return version;
    }

    private static void prune(Context c) {
        long ttl = ttlMs(c);
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (int i = ITEMS.size() - 1; i >= 0; i--) {
            if (now - ITEMS.get(i).capturedAt > ttl) {
                ITEMS.remove(i);
                changed = true;
            }
        }
        if (changed) version++;
    }
}
