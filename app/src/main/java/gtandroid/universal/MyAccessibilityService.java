package gtandroid.universal;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
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

    private static final String GAME_PACKAGE = "com.grand.cr";
    private static final String REPORT_MARKER = "К-во репорта:";
    private static final String PREFS_CHAT = "GTAndroidChatLog";
    private static final String PREFS_REPORTS = "GTAndroidReports";
    private static final int MAX_CHAT_ENTRIES = 150;
    private static final int MAX_REPORT_ENTRIES = 100;
    private static final int MAX_TEXT_LENGTH = 2000;
    private static final int MAX_TREE_DEPTH = 6;
    private static final long MIN_EVENT_INTERVAL = 500L;

    private static final Pattern REPORT_CLEAN_PATTERN =
            Pattern.compile("^(.*?)\\s*\\[\\s*К-во репорта\\s*:\\s*(\\d+)\\s*\\]\\s*$");

    private static final Pattern NICK_ID_TEXT_PATTERN =
            Pattern.compile("^\\s*(\\S+?)\\[(\\d+)\\]\\s*:\\s*(.+?)\\s*$");

    /** Ищем отдельное число в тексте репорта (например, "2 ДМ" → 2). */
    private static final Pattern ID_IN_TEXT_PATTERN =
            Pattern.compile("\\b(\\d{1,6})\\b");

    private long lastEventAt = 0L;
    private String lastText = "";
    private long lastTextAt = 0L;

    public static boolean isAvailable() {
        return instance != null;
    }

    @Override
    public void onServiceConnected() {
        instance = this;
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        // Меньше событий — меньше крашей
        info.eventTypes = AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
                | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        // Убрано FLAG_RETRIEVE_INTERACTIVE_WINDOWS — частая причина крашей
        info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
        info.notificationTimeout = 300L;
        info.packageNames = new String[]{GAME_PACKAGE};
        setServiceInfo(info);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;

        // Ограничение частоты
        long now = System.currentTimeMillis();
        if (now - lastEventAt < MIN_EVENT_INTERVAL) return;
        lastEventAt = now;

        CharSequence pkg = event.getPackageName();
        if (pkg == null) return;
        if (!GAME_PACKAGE.equals(pkg.toString())) return;

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
                    CharSequence srcPkg = source.getPackageName();
                    if (srcPkg != null && GAME_PACKAGE.contentEquals(srcPkg)) {
                        collectNodeText(source, text, 0);
                    }
                } catch (Exception ignored) { }
                try { source.recycle(); } catch (Exception ignored) { }
            }

            String value = text.toString().trim();
            if (value.isEmpty()) return;
            if (value.length() > MAX_TEXT_LENGTH) {
                value = value.substring(0, MAX_TEXT_LENGTH);
            }

            if (value.contains(REPORT_MARKER)) {
                publishReportLines(value);
            }

            if (isChatText(value)) {
                saveChatLines(value);
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

    private boolean isChatText(String value) {
        for (String line : value.split("\\r?\\n")) {
            String t = line.trim();
            if (t.isEmpty()) continue;
            if (t.contains(REPORT_MARKER)) return true;
            if (t.contains("<ADM>")) return true;
            if (t.matches("^\\S+\\[\\d+\\]:\\s*.*")) return true;
            if (t.matches("^\\[?\\d{1,2}:\\d{2}\\]?\\s+\\S+\\[\\d+\\]:.*")) return true;
            if (t.startsWith("Администратор ")) return true;
            if (t.matches("^\\[\\S+\\]\\s+.*")) return true;
            if (t.matches("^[A-Za-zА-Яа-я0-9_]+:\\s+.*")) return true;
            if (t.startsWith("Добро пожаловать")
                    || t.startsWith("Сегодня действует")
                    || t.startsWith("Вам приятной игры")
                    || t.startsWith("Вход в игру")
                    || t.startsWith("Подключение к серверу")) {
                return true;
            }
            if (t.matches("^\\d{1,2}:\\d{2}\\s+.*")) return true;
        }
        return false;
    }

    private void publishReportLines(String value) {
        String[] lines = value.split("\\r?\\n");
        for (String rawLine : lines) {
            if (!rawLine.contains(REPORT_MARKER)) continue;
            String line = rawLine.trim();
            if (line.isEmpty()) continue;

            String cleanLine = line;
            int reportNumber = 0;
            Matcher m = REPORT_CLEAN_PATTERN.matcher(line);
            if (m.matches()) {
                cleanLine = m.group(1).trim();
                try { reportNumber = Integer.parseInt(m.group(2)); }
                catch (Exception ignored) { }
            }

            String nick = "";
            String id = "";
            String text = cleanLine;

            Matcher nid = NICK_ID_TEXT_PATTERN.matcher(cleanLine);
            if (nid.matches()) {
                nick = nid.group(1).trim();
                id = nid.group(2).trim();
                text = nid.group(3).trim();
            }

            // Ищем ID в тексте репорта (например "2 ДМ" → 2)
            String targetId = "";
            Matcher idm = ID_IN_TEXT_PATTERN.matcher(text);
            if (idm.find()) {
                targetId = idm.group(1);
            }

            long now = System.currentTimeMillis();
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

            for (int i = arr.length() - 1; i >= Math.max(0, arr.length() - 5); i--) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null
                        && o.optString("full", "").equals(fullLine)
                        && (time - o.optLong("time", 0)) < 2000L) {
                    return;
                }
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

    private void saveChatLines(String value) {
        String[] lines = value.split("\\r?\\n");
        long now = System.currentTimeMillis();
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) continue;
            if (line.equals(lastText) && now - lastTextAt < 1000L) continue;
            lastText = line;
            lastTextAt = now;
            saveChatToPrefs(line, now);
        }
    }

    private void saveChatToPrefs(String text, long time) {
        try {
            SharedPreferences sp = getSharedPreferences(PREFS_CHAT, MODE_PRIVATE);
            JSONArray arr;
            try {
                arr = new JSONArray(sp.getString("recent", "[]"));
            } catch (Exception e) {
                arr = new JSONArray();
            }
            for (int i = arr.length() - 1; i >= Math.max(0, arr.length() - 5); i--) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null
                        && o.optString("text", "").equals(text)
                        && (time - o.optLong("time", 0)) < 1500L) {
                    return;
                }
            }
            JSONObject obj = new JSONObject();
            obj.put("text", text);
            obj.put("time", time);
            arr.put(obj);
            while (arr.length() > MAX_CHAT_ENTRIES) arr.remove(0);
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

    public static void insertText(final String text) {
        if (instance == null) return;
        new Handler().postDelayed(new Runnable() {
            @Override public void run() {
                try {
                    AccessibilityNodeInfo rootNode = instance.getRootInActiveWindow();
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
                                    instance.getSystemService(CLIPBOARD_SERVICE);
                            ClipData clip = ClipData.newPlainText("RTools", text);
                            if (cm != null) cm.setPrimaryClip(clip);
                            targetNode.performAction(AccessibilityNodeInfo.ACTION_PASTE);
                        }
                    }
                    try { rootNode.recycle(); } catch (Exception ignored) { }
                } catch (Exception ignored) { }
            }
        }, 100L);
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
