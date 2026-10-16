package gtandroid.universal;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.GestureDescription;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Path;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MyAccessibilityService extends AccessibilityService {

    private static MyAccessibilityService instance;

    private static final String GAME_PACKAGE_PREFIX = "com.grand";
    private static final String REPORT_MARKER = "К-во репорта:";
    private static final String GOV_NEWS_MARKER = "Появилась заявка на государственные новости";
    private static final String PREFS_REPORTS = "GTAndroidReports";
    private static final String PREFS_DATA = "GTAndroidData";
    private static final int MAX_REPORT_ENTRIES = 100;
    private static final int MAX_TEXT_LENGTH = 3000;
    private static final int MAX_TREE_DEPTH = 8;

    /** Ключ для хранения последних обработанных хешей. */
    private static final String KEY_RECENT_HASHES = "recent_hashes";

    /** Окно дедупа: если та же чистая строка пришла раньше 5 сек назад — пропуск. */
    private static final long DEDUP_WINDOW_MS = 5000L;

    /** Хеши старше 60 сек — выкидываем, чтобы не копить мусор. */
    private static final long HASH_TTL_MS = 60000L;

    private static final String KEY_AUTO_OPEN = "auto_open_chat_enabled";
    private static final String KEY_MARK_X = "auto_open_chat_x";
    private static final String KEY_MARK_Y = "auto_open_chat_y";

    private static final Pattern REPORT_CLEAN_PATTERN =
            Pattern.compile("^(.*?)\\s*\\[\\s*К-во репорта\\s*:\\s*(\\d+)\\s*\\]\\s*$");

    private static final Pattern NICK_ID_TEXT_PATTERN =
            Pattern.compile("^\\s*(\\S+?)\\[(\\d+)\\]\\s*:\\s*(.+?)\\s*$");

    private static final Pattern ID_IN_TEXT_PATTERN =
            Pattern.compile("\\b(\\d{1,6})\\b");

    private long lastEventAt = 0L;

    public static boolean isAvailable() {
        return instance != null;
    }

    private boolean isGamePackage(CharSequence pkg) {
        if (pkg == null) return false;
        return pkg.toString().startsWith(GAME_PACKAGE_PREFIX);
    }

    @Override
    public void onServiceConnected() {
        instance = this;
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
                | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                | AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                | AccessibilityEvent.TYPE_VIEW_SCROLLED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
        info.notificationTimeout = 150L;
        setServiceInfo(info);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        if (!isGamePackage(event.getPackageName())) return;

        long now = System.currentTimeMillis();
        if (now - lastEventAt < 100L) return;
        lastEventAt = now;

        try {
            StringBuilder text = new StringBuilder();

            if (event.getText() != null) {
                for (CharSequence item : event.getText()) {
                    if (item != null && item.length() > 0) {
                        if (text.length() > MAX_TEXT_LENGTH) break;
                        text.append(item).append('\n');
                    }
                }
            }

            AccessibilityNodeInfo source = event.getSource();
            if (source != null) {
                try {
                    if (isGamePackage(source.getPackageName())) {
                        collectNodeText(source, text, 0);
                    }
                } catch (Exception ignored) { }
                try { source.recycle(); } catch (Exception ignored) { }
            }

            try {
                AccessibilityNodeInfo root = getRootInActiveWindow();
                if (root != null) {
                    if (isGamePackage(root.getPackageName())) {
                        collectNodeText(root, text, 0);
                    }
                    try { root.recycle(); } catch (Exception ignored) { }
                }
            } catch (Exception ignored) { }

            String value = text.toString().trim();
            if (value.isEmpty()) return;
            if (value.length() > MAX_TEXT_LENGTH) {
                value = value.substring(0, MAX_TEXT_LENGTH);
            }

            if (value.contains(REPORT_MARKER)) {
                publishReportLines(value);
            }

            if (value.contains(GOV_NEWS_MARKER)) {
                handleGovNews(value);
            }

            if (value.contains("<ADM>")) {
                for (AdminFormManager.Form form : AdminFormManager.parseForms(value)) {
                    if (AdminFormManager.addForm(this, form)) {
                        Intent intent = new Intent("ADMIN_FORM_RECEIVED");
                        intent.putExtra("nickname", form.nickname);
                        intent.putExtra("id", form.id);
                        intent.putExtra("command", form.command);
                        sendBroadcast(intent);
                    }
                }
            }
        } catch (Exception ignored) { }
    }

    /** Государственные новости. */
    private void handleGovNews(String value) {
        SharedPreferences prefs = getSharedPreferences(PREFS_DATA, MODE_PRIVATE);
        if (!prefs.getBoolean("auto_gov_news_enabled", false)) return;

        long now = System.currentTimeMillis();
        long last = prefs.getLong("last_gov_news_time", 0L);
        if (now - last < 15000L) return;

        prefs.edit().putLong("last_gov_news_time", now).apply();

        Intent intent = new Intent("GOV_NEWS_CAUGHT");
        intent.putExtra("detected_at", now);
        sendBroadcast(intent);
    }

    /**
     * Дедуп по ЧИСТОЙ строке (без "[К-во репорта: N]").
     * Если та же строка пришла раньше чем 5 сек назад — пропускаем.
     * Если раньше 5 сек — считаем новым репортом.
     */
    private void publishReportLines(String value) {
        String[] lines = value.split("\\r?\\n");
        SharedPreferences rp = getSharedPreferences(PREFS_REPORTS, MODE_PRIVATE);

        for (String rawLine : lines) {
            if (!rawLine.contains(REPORT_MARKER)) continue;
            String line = rawLine.trim();
            if (line.isEmpty()) continue;

            // Парсим номер (нужен только для передачи в UI, для дедупа не используется)
            String cleanLine = line;
            int reportNumber = 0;
            Matcher m = REPORT_CLEAN_PATTERN.matcher(line);
            if (m.matches()) {
                cleanLine = m.group(1).trim();
                try { reportNumber = Integer.parseInt(m.group(2)); }
                catch (Exception ignored) { }
            }

            long now = System.currentTimeMillis();
            String hash = Integer.toHexString(cleanLine.hashCode());

            // Если такой же хеш уже обрабатывали менее 5 сек назад — пропускаем
            if (isDuplicateRecently(rp, hash, now)) continue;

            // Запоминаем обработанный хеш
            markHashProcessed(rp, hash, now);

            String nick = "", id = "", text = cleanLine;
            Matcher nid = NICK_ID_TEXT_PATTERN.matcher(cleanLine);
            if (nid.matches()) {
                nick = nid.group(1).trim();
                id = nid.group(2).trim();
                text = nid.group(3).trim();
            }

            String targetId = "";
            Matcher idm = ID_IN_TEXT_PATTERN.matcher(text);
            if (idm.find()) targetId = idm.group(1);

            saveReportToRecent(nick, id, text, cleanLine, reportNumber, targetId, now);

            Intent intent = new Intent("REPORT_CAUGHT");
            intent.putExtra("report_nick", nick);
            intent.putExtra("report_id", id);
            intent.putExtra("report_text", text);
            intent.putExtra("report_full", cleanLine);
            intent.putExtra("report_number", reportNumber);
            intent.putExtra("report_target_id", targetId);
            intent.putExtra("detected_at", now);
            sendBroadcast(intent);
        }
    }

    /**
     * Проверяет, встречался ли такой хеш за последние DEDUP_WINDOW_MS.
     * Формат хранения: "hash:time,hash:time,..."
     */
    private boolean isDuplicateRecently(SharedPreferences rp, String hash, long now) {
        String raw = rp.getString(KEY_RECENT_HASHES, "");
        if (raw.isEmpty()) return false;
        long cutoff = now - DEDUP_WINDOW_MS;
        String[] entries = raw.split(",");
        for (String entry : entries) {
            int sep = entry.indexOf(':');
            if (sep <= 0) continue;
            String h = entry.substring(0, sep);
            long t;
            try { t = Long.parseLong(entry.substring(sep + 1)); }
            catch (Exception e) { continue; }
            if (h.equals(hash) && t >= cutoff) return true;
        }
        return false;
    }

    /**
     * Добавляет хеш с временем. Чистит записи старше HASH_TTL_MS.
     */
    private void markHashProcessed(SharedPreferences rp, String hash, long now) {
        String raw = rp.getString(KEY_RECENT_HASHES, "");
        long cutoff = now - HASH_TTL_MS;

        StringBuilder sb = new StringBuilder();
        if (!raw.isEmpty()) {
            for (String entry : raw.split(",")) {
                int sep = entry.indexOf(':');
                if (sep <= 0) continue;
                long t;
                try { t = Long.parseLong(entry.substring(sep + 1)); }
                catch (Exception e) { continue; }
                if (t >= cutoff) {
                    if (sb.length() > 0) sb.append(',');
                    sb.append(entry);
                }
            }
        }
        if (sb.length() > 0) sb.append(',');
        sb.append(hash).append(':').append(now);

        rp.edit().putString(KEY_RECENT_HASHES, sb.toString()).apply();
    }

    private void saveReportToRecent(String nick, String id, String text,
                                     String fullLine, int reportNumber,
                                     String targetId, long time) {
        try {
            SharedPreferences sp = getSharedPreferences(PREFS_REPORTS, MODE_PRIVATE);
            JSONArray arr;
            try {
                arr = new JSONArray(sp.getString("recent", "[]"));
            } catch (Exception e) {
                arr = new JSONArray();
            }

            JSONObject obj = new JSONObject();
            obj.put("nick", nick);
            obj.put("id", id);
            obj.put("text", text);
            obj.put("full", fullLine);
            obj.put("report_number", reportNumber);
            obj.put("target_id", targetId);
            obj.put("time", time);
            arr.put(obj);

            while (arr.length() > MAX_REPORT_ENTRIES) arr.remove(0);
            sp.edit().putString("recent", arr.toString()).apply();
        } catch (Exception ignored) { }
    }

    private void collectNodeText(AccessibilityNodeInfo node, StringBuilder out, int depth) {
        if (node == null || depth > MAX_TREE_DEPTH) return;
        if (out.length() > MAX_TEXT_LENGTH) return;

        try {
            CharSequence text = node.getText();
            if (text != null && text.length() > 0) {
                out.append(text).append('\n');
            }
        } catch (Exception ignored) { }

        try {
            CharSequence desc = node.getContentDescription();
            if (desc != null && desc.length() > 0) {
                out.append(desc).append('\n');
            }
        } catch (Exception ignored) { }

        int childCount;
        try { childCount = node.getChildCount(); }
        catch (Exception e) { return; }

        for (int i = 0; i < childCount; i++) {
            if (out.length() > MAX_TEXT_LENGTH) break;
            AccessibilityNodeInfo child = null;
            try { child = node.getChild(i); }
            catch (Exception ignored) { }
            if (child != null) {
                collectNodeText(child, out, depth + 1);
                try { child.recycle(); } catch (Exception ignored) { }
            }
        }
    }

    @Override
    public void onInterrupt() { }

    // ==================== AUTO-OPEN-CHAT ====================

    public static boolean isAutoOpenChatEnabled() {
        if (instance == null) return false;
        SharedPreferences sp = instance.getSharedPreferences(PREFS_DATA, MODE_PRIVATE);
        return sp.getBoolean(KEY_AUTO_OPEN, false);
    }

    public static void setAutoOpenChatMark(float x, float y) {
        if (instance == null) return;
        SharedPreferences sp = instance.getSharedPreferences(PREFS_DATA, MODE_PRIVATE);
        sp.edit()
                .putFloat(KEY_MARK_X, x)
                .putFloat(KEY_MARK_Y, y)
                .putBoolean(KEY_AUTO_OPEN, true)
                .apply();
    }

    public static float[] getAutoOpenChatMark() {
        if (instance == null) return null;
        SharedPreferences sp = instance.getSharedPreferences(PREFS_DATA, MODE_PRIVATE);
        if (!sp.contains(KEY_MARK_X)) return null;
        return new float[]{sp.getFloat(KEY_MARK_X, 0), sp.getFloat(KEY_MARK_Y, 0)};
    }

    private void tapMark(float x, float y) {
        if (Build.VERSION.SDK_INT < 24) return;
        try {
            Path path = new Path();
            path.moveTo(x, y);
            GestureDescription.Builder builder = new GestureDescription.Builder();
            builder.addStroke(new GestureDescription.StrokeDescription(path, 0L, 50L));
            dispatchGesture(builder.build(), null, null);
        } catch (Exception ignored) { }
    }

    public static void tapMarkPublic(float x, float y) {
        if (instance == null) return;
        instance.tapMark(x, y);
    }

    // ==================== INSERT TEXT ====================

    public static void insertText(final String text) {
        if (instance == null) return;

        boolean autoOpen = isAutoOpenChatEnabled();
        float[] mark = autoOpen ? getAutoOpenChatMark() : null;

        if (mark == null) {
            new Handler().postDelayed(() -> instance.performInsertText(text), 200L);
            return;
        }

        boolean hasField = instance.hasActiveEditableField();

        if (hasField) {
            new Handler().postDelayed(() -> instance.performInsertText(text), 200L);
        } else {
            instance.tapMark(mark[0], mark[1]);
            new Handler().postDelayed(() -> {
                boolean nowHasField = instance.hasActiveEditableField();
                if (!nowHasField) {
                    instance.tapMark(mark[0], mark[1]);
                    new Handler().postDelayed(
                            () -> instance.performInsertText(text), 500L);
                } else {
                    instance.performInsertText(text);
                }
            }, 800L);
        }
    }

    private boolean hasActiveEditableField() {
        try {
            AccessibilityNodeInfo rootNode = getRootInActiveWindow();
            if (rootNode == null) return false;
            AccessibilityNodeInfo target = findEditableNode(rootNode);
            boolean result = target != null;
            try { rootNode.recycle(); } catch (Exception ignored) { }
            return result;
        } catch (Exception e) {
            return false;
        }
    }

    private void performInsertText(final String text) {
        try {
            AccessibilityNodeInfo rootNode = getRootInActiveWindow();
            if (rootNode == null) return;
            AccessibilityNodeInfo targetNode = findEditableNode(rootNode);
            if (targetNode != null) {
                targetNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
                Bundle arguments = new Bundle();
                arguments.putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
                boolean success = targetNode.performAction(
                        AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
                if (!success) {
                    ClipboardManager cm = (ClipboardManager)
                            getSystemService(CLIPBOARD_SERVICE);
                    ClipData clip = ClipData.newPlainText("RTools", text);
                    if (cm != null) cm.setPrimaryClip(clip);
                    targetNode.performAction(AccessibilityNodeInfo.ACTION_PASTE);
                }
            }
            try { rootNode.recycle(); } catch (Exception ignored) { }
        } catch (Exception ignored) { }
    }

    public static void insertTextAndSend(final String text) {
        insertText(text + "\n");
    }

    public static void pressEnter() {
        insertText("\n");
    }

    private static AccessibilityNodeInfo findEditableNode(AccessibilityNodeInfo node) {
        if (node == null) return null;
        try {
            if (node.isEditable() && node.isEnabled() && node.isFocused()) return node;
            if (node.isEditable() && node.isEnabled()) return node;
        } catch (Exception ignored) { }
        int childCount;
        try { childCount = node.getChildCount(); }
        catch (Exception e) { return null; }
        for (int i = 0; i < childCount; i++) {
            AccessibilityNodeInfo child = null;
            try { child = node.getChild(i); }
            catch (Exception ignored) { }
            if (child != null) {
                AccessibilityNodeInfo result = findEditableNode(child);
                if (result != null) return result;
            }
        }
        return null;
    }
    }
