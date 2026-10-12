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

public class MyAccessibilityService extends AccessibilityService {

    private static MyAccessibilityService instance;

    /** Имя пакета игры. ЗАМЕНИ если у тебя другое. */
    private static final String GAME_PACKAGE = "com.grand.mobile";

    private static final String REPORT_MARKER = "К-во репорта:";
    private static final String PREFS_CHAT = "GTAndroidChatLog";
    private static final int MAX_CHAT_ENTRIES = 200;
    private static final int MAX_TEXT_LENGTH = 5000;
    private static final int MAX_TREE_DEPTH = 8;

    private String lastText = "";
    private long lastTextAt = 0L;

    public static boolean isAvailable() {
        return instance != null;
    }

    @Override
    public void onServiceConnected() {
        instance = this;
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        // Только события текста и изменения содержимого
        info.eventTypes = AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
                | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                | AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        info.notificationTimeout = 200L;
        // Ограничение по пакету — ловим только Grand Mobile
        info.packageNames = new String[]{GAME_PACKAGE};
        setServiceInfo(info);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;

        // Проверка пакета
        CharSequence pkg = event.getPackageName();
        if (pkg == null) return;
        String packageName = pkg.toString();
        if (!GAME_PACKAGE.equals(packageName)) return;

        try {
            StringBuilder text = new StringBuilder();

            // 1. Текст из события
            if (event.getText() != null) {
                for (CharSequence item : event.getText()) {
                    if (item != null && item.length() > 0) {
                        if (text.length() > MAX_TEXT_LENGTH) break;
                        text.append(item).append('\n');
                    }
                }
            }

            // 2. Текст из source
            AccessibilityNodeInfo source = event.getSource();
            if (source != null) {
                if (source.getPackageName() != null
                        && GAME_PACKAGE.contentEquals(source.getPackageName())) {
                    collectNodeText(source, text, 0);
                }
                source.recycle();
            }

            String value = text.toString().trim();
            if (value.isEmpty()) return;
            if (value.length() > MAX_TEXT_LENGTH) {
                value = value.substring(0, MAX_TEXT_LENGTH);
            }

            // Ловим только если есть явные признаки чата
            if (isChatText(value)) {
                saveChatLines(value);
            }

            // Репорты
            if (value.contains(REPORT_MARKER)) publishReportLines(value);

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
     * Проверка: похоже ли это на сообщение чата?
     * В Grand Mobile чат обычно имеет формат:
     *   [HH:mm] Ник[ID]: текст
     *   Ник[ID]: текст
     *   [Ник] текст
     *   Ник: текст
     */
    private boolean isChatText(String value) {
        // Паттерны чата Grand Mobile (примерные)
        // 1. Есть квадратные скобки [xx] или [xx:xx] или [ник]
        // 2. Есть "Ник[ID]:" (типичный формат)
        // 3. Есть ": " в начале строки

        for (String line : value.split("\\r?\\n")) {
            String t = line.trim();
            if (t.isEmpty()) continue;

            // Ник[ID]: — самый частый формат чата
            if (t.matches("^\\[?\\d{1,2}:\\d{2}\\]?\\s*\\S+\\[\\d+\\].*")) return true;

            // Формат: Ник[ID]: текст
            if (t.matches("^\\S+\\[\\d+\\]:\\s*.*")) return true;

            // Формат: [Ник] текст
            if (t.matches("^\\[\\S+\\]\\s+.*")) return true;

            // Репорт: содержит "[К-во репорта:"
            if (t.contains(REPORT_MARKER)) return true;

            // ADM-форма
            if (t.contains("<ADM>")) return true;
        }
        return false;
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
            // Дубликат по тексту+времени
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

    private void publishReportLines(String value) {
        String[] lines = value.split("\\r?\\n");
        for (String rawLine : lines) {
            if (!rawLine.contains(REPORT_MARKER)) continue;
            String report = rawLine.trim();
            if (report.isEmpty()) continue;
            long now = System.currentTimeMillis();
            saveReportToRecent(report, now);
            Intent intent = new Intent("REPORT_CAUGHT");
            intent.putExtra("report_text", report);
            intent.putExtra("detected_at", now);
            sendBroadcast(intent);
        }
    }

    private void saveReportToRecent(String report, long time) {
        try {
            SharedPreferences sp = getSharedPreferences("GTAndroidReports", MODE_PRIVATE);
            JSONArray arr;
            try {
                arr = new JSONArray(sp.getString("recent", "[]"));
            } catch (Exception e) {
                arr = new JSONArray();
            }
            JSONObject obj = new JSONObject();
            obj.put("text", report);
            obj.put("time", time);
            arr.put(obj);
            while (arr.length() > 50) arr.remove(0);
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
