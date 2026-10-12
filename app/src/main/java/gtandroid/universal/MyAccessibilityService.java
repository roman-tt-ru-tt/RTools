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
    private static final int MAX_CHAT_ENTRIES = 200;
    private static final int MAX_REPORT_ENTRIES = 50;
    private static final int MAX_TEXT_LENGTH = 5000;
    private static final int MAX_TREE_DEPTH = 8;

    /** Regex для извлечения номера репорта. */
    private static final Pattern REPORT_COUNT_PATTERN =
            Pattern.compile("\\[\\s*К-во репорта\\s*:\\s*(\\d+)\\s*\\]");

    /** Regex для извлечения "чистого" текста репорта (без [К-во...]). */
    private static final Pattern REPORT_CLEAN_PATTERN =
            Pattern.compile("^(.*?)\\s*\\[\\s*К-во репорта\\s*:\\s*(\\d+)\\s*\\]\\s*$");

    private String lastText = "";
    private long lastTextAt = 0L;

    public static boolean isAvailable() {
        return instance != null;
    }

    @Override
    public void onServiceConnected() {
        instance = this;
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
                | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                | AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        info.notificationTimeout = 200L;
        info.packageNames = new String[]{GAME_PACKAGE};
        setServiceInfo(info);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;

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
                CharSequence srcPkg = source.getPackageName();
                if (srcPkg != null && GAME_PACKAGE.contentEquals(srcPkg)) {
                    collectNodeText(source, text, 0);
                }
                source.recycle();
            }

            String value = text.toString().trim();
            if (value.isEmpty()) return;
            if (value.length() > MAX_TEXT_LENGTH) {
                value = value.substring(0, MAX_TEXT_LENGTH);
            }

            // Репорты — обрабатываем ВСЕГДА, даже если isChatText=false
            if (value.contains(REPORT_MARKER)) {
                publishReportLines(value);
            }

            // Остальной чат
            if (isChatText(value)) {
                saveChatLines(value);
            }

            // ADM-формы
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

    /**
     * Проверяет, похоже ли содержимое на сообщение чата CRMP.
     */
    private boolean isChatText(String value) {
        for (String line : value.split("\\r?\\n")) {
            String t = line.trim();
            if (t.isEmpty()) continue;

            // 1. Репорт
            if (t.contains(REPORT_MARKER)) return true;

            // 2. ADM-форма
            if (t.contains("<ADM>")) return true;

            // 3. Ник[ID]: текст
            if (t.matches("^\\S+\\[\\d+\\]:\\s*.*")) return true;

            // 4. [HH:mm] Ник[ID]: текст
            if (t.matches("^\\[?\\d{1,2}:\\d{2}\\]?\\s+\\S+\\[\\d+\\]:.*")) return true;

            // 5. "Администратор Ник выдал ..."
            if (t.startsWith("Администратор ") || t.startsWith("Администратор\t")) return true;

            // 6. [Ник] текст
            if (t.matches("^\\[\\S+\\]\\s+.*")) return true;

            // 7. Ник: текст
            if (t.matches("^[A-Za-zА-Яа-я0-9_]+:\\s+.*")) return true;

            // 8. Системные сообщения
            if (t.startsWith("Добро пожаловать")
                    || t.startsWith("Сегодня действует")
                    || t.startsWith("Вам приятной игры")
                    || t.startsWith("Вход в игру")
                    || t.startsWith("Подключение к серверу")) {
                return true;
            }

            // 9. Строка со временем в начале
            if (t.matches("^\\d{1,2}:\\d{2}\\s+.*")) return true;
        }
        return false;
    }

    /**
     * Обработка репортов.
     * Ищет все строки с "К-во репорта:", извлекает чистый текст и номер.
     */
    private void publishReportLines(String value) {
        String[] lines = value.split("\\r?\\n");
        for (String rawLine : lines) {
            if (!rawLine.contains(REPORT_MARKER)) continue;
            String report = rawLine.trim();
            if (report.isEmpty()) continue;

            // Извлекаем "чистый" текст репорта и номер
            String cleanText = report;
            int reportNumber = 0;
            Matcher m = REPORT_CLEAN_PATTERN.matcher(report);
            if (m.matches()) {
                cleanText = m.group(1).trim();
                try { reportNumber = Integer.parseInt(m.group(2)); }
                catch (Exception ignored) { }
            }

            long now = System.currentTimeMillis();

            // Сохраняем в GTAndroidReports
            saveReportToRecent(cleanText, reportNumber, now);

            // Broadcast для FloatingService
            Intent intent = new Intent("REPORT_CAUGHT");
            intent.putExtra("report_text", cleanText);
            intent.putExtra("report_number", reportNumber);
            intent.putExtra("detected_at", now);
            sendBroadcast(intent);
        }
    }

    private void saveReportToRecent(String text, int reportNumber, long time) {
        try {
            SharedPreferences sp = getSharedPreferences(PREFS_REPORTS, MODE_PRIVATE);
            JSONArray arr;
            try {
                arr = new JSONArray(sp.getString("recent", "[]"));
            } catch (Exception e) {
                arr = new JSONArray();
            }

            // Проверка дубликатов
            for (int i = arr.length() - 1; i >= Math.max(0, arr.length() - 5); i--) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null
                        && o.optString("text", "").equals(text)
                        && (time - o.optLong("time", 0)) < 2000L) {
                    return;
                }
            }

            JSONObject obj = new JSONObject();
            obj.put("text", text);
            obj.put("report_number", reportNumber);
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

        CharSequence text = node.getText();
        if (text != null && text.length() > 0) {
            out.append(text).append('\n');
        }
        CharSequence desc = node.getContentDescription();
        if (desc != null && desc.length() > 0) {
            out.append(desc).append('\n');
        }

        int childCount = node.getChildCount();
        for (int i = 0; i < childCount; i++) {
            if (out.length() > MAX_TEXT_LENGTH) break;
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                collectNodeText(child, out, depth + 1);
                child.recycle();
            }
        }
    }

    @Override
    public void onInterrupt() { }

    public static void insertText(final String text) {
        if (instance == null) return;
        new Handler().postDelayed(new Runnable() {
            @Override public void run() {
                AccessibilityNodeInfo targetNode;
                AccessibilityNodeInfo rootNode = instance.getRootInActiveWindow();
                if (rootNode != null && (targetNode = findEditableNode(rootNode)) != null) {
                    targetNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
                    try { Thread.sleep(50L); } catch (InterruptedException ignored) { }
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
                    rootNode.recycle();
                }
            }
        }, 100L);
    }

    public static void insertTextAndSend(final String text) {
        if (instance == null) return;
        new Handler().postDelayed(new Runnable() {
            @Override public void run() {
                AccessibilityNodeInfo targetNode;
                AccessibilityNodeInfo rootNode = instance.getRootInActiveWindow();
                if (rootNode != null && (targetNode = findEditableNode(rootNode)) != null) {
                    targetNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
                    try { Thread.sleep(50L); } catch (InterruptedException ignored) { }
                    Bundle arguments = new Bundle();
                    arguments.putCharSequence(
                            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                            text + "\n");
                    targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
                    rootNode.recycle();
                }
            }
        }, 100L);
    }

    public static void pressEnter() {
        if (instance == null) return;
        new Handler().postDelayed(new Runnable() {
            @Override public void run() {
                AccessibilityNodeInfo targetNode;
                AccessibilityNodeInfo rootNode = instance.getRootInActiveWindow();
                if (rootNode != null && (targetNode = findEditableNode(rootNode)) != null) {
                    Bundle arguments = new Bundle();
                    arguments.putCharSequence(
                            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "\n");
                    targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
                    rootNode.recycle();
                }
            }
        }, 100L);
    }

    private static AccessibilityNodeInfo findEditableNode(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if (node.isEditable() && node.isEnabled() && node.isFocused()) return node;
        if (node.isEditable() && node.isEnabled()) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                AccessibilityNodeInfo result = findEditableNode(child);
                if (result != null) return result;
            }
        }
        return null;
    }
                }
