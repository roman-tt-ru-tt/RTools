package gtandroid.universal;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

/* loaded from: classes2.dex */
public class MyAccessibilityService extends AccessibilityService {
    private static MyAccessibilityService instance;

    public static void insertTextAndPaste(String textToInsert) {
    }

    public static void pasteAndSend(String text) {
    }

    @Override // android.accessibilityservice.AccessibilityService
    public void onServiceConnected() {
        instance = this;
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_VIEW_SCROLLED\n                | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED\n                | AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED\n                | AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED\n                | AccessibilityEvent.TYPE_VIEW_FOCUSED;
        info.feedbackType = 16;
        info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        info.notificationTimeout = 100L;
        setServiceInfo(info);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        try {
            StringBuilder collected = new StringBuilder();
            for (CharSequence item : event.getText()) {
                if (item != null && item.length() > 0) collected.append(item).append('\\n');
            }

            AccessibilityNodeInfo source = event.getSource();
            if (source != null) {
                collectNodeText(source, collected, 0);
            }
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) {
                collectNodeText(root, collected, 0);
                root.recycle();
            }

            String value = collected.toString();
            if (value.trim().isEmpty()) return;

            // Админ-формы: прежняя обработка сохранена.
            for (AdminFormManager.Form form : AdminFormManager.parseForms(value.replace("< ADM>", "<ADM>"))) {
                if (AdminFormManager.addForm(this, form)) {
                    Intent intent = new Intent("ADMIN_FORM_RECEIVED");
                    intent.setPackage(getPackageName());
                    intent.putExtra("nickname", form.nickname);
                    intent.putExtra("id", form.id);
                    intent.putExtra("command", form.command);
                    sendBroadcast(intent);
                }
            }

            // Репорты: ищем только явные строки с автором и ID.
            handleDetectedReports(value);
        } catch (Exception ignored) {
        }
    }

    private void handleDetectedReports(String text) {
        String[] lines = text.split("\\r?\\n");
        long now = System.currentTimeMillis();
        android.content.SharedPreferences prefs = getSharedPreferences("GTAndroidReports", MODE_PRIVATE);
        JSONArray existing;
        try {
            existing = new JSONArray(prefs.getString("recent", "[]"));
        } catch (Exception e) {
            existing = new JSONArray();
        }

        JSONArray fresh = new JSONArray();
        for (int i = 0; i < existing.length(); i++) {
            JSONObject item = existing.optJSONObject(i);
            if (item != null && now - item.optLong("time", 0) <= 60000) fresh.put(item);
        }

        boolean changed = false;
        Pattern pattern = Pattern.compile("(?i)(?:репорт\\s+от\\s+)?([A-Za-zА-Яа-яЁё0-9_]{3,})\\[(\\d{1,6})\\]\\s*:?\\s*(.*)");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            String lower = line.toLowerCase(Locale.ROOT);
            if (!lower.contains("репорт от") && !lower.contains("report from") && !lower.contains("к-во репорта")) continue;
            Matcher matcher = pattern.matcher(line);
            if (!matcher.find()) continue;

            String nickname = matcher.group(1);
            String id = matcher.group(2);
            String body = matcher.group(3).trim();
            if (body.isEmpty() && i + 1 < lines.length) body = lines[i + 1].trim();
            if (body.isEmpty()) continue;

            String key = nickname + "[" + id + "]:" + body;
            boolean duplicate = false;
            for (int j = 0; j < fresh.length(); j++) {
                JSONObject item = fresh.optJSONObject(j);
                if (item != null && key.equals(item.optString("key"))) {
                    duplicate = true;
                    break;
                }
            }
            if (duplicate) continue;

            try {
                JSONObject item = new JSONObject();
                item.put("key", key);
                item.put("nickname", nickname);
                item.put("id", id);
                item.put("text", body);
                item.put("time", now);
                fresh.put(item);
                changed = true;
            } catch (Exception ignored) {
            }
        }

        if (changed) {
            prefs.edit().putString("recent", fresh.toString()).apply();
            Intent update = new Intent("REPORTS_UPDATED");
            update.setPackage(getPackageName());
            sendBroadcast(update);
        }
    }

    private void collectNodeText(AccessibilityNodeInfo node, StringBuilder out, int depth) {
        if (node == null || depth > 12) return;
        CharSequence text = node.getText();
        if (text != null && text.length() > 0) out.append(text).append('\n');
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                collectNodeText(child, out, depth + 1);
                child.recycle();
            }
        }
    }

    @Override // android.accessibilityservice.AccessibilityService
    public void onInterrupt() {
    }

    public static void insertText(final String text) {
        if (instance == null) {
            return;
        }
        new Handler().postDelayed(new Runnable() { // from class: gtandroid.universal.MyAccessibilityService.1
            @Override // java.lang.Runnable
            public void run() {
                AccessibilityNodeInfo targetNode;
                AccessibilityNodeInfo rootNode = MyAccessibilityService.instance.getRootInActiveWindow();
                if (rootNode != null && (targetNode = MyAccessibilityService.findEditableNode(rootNode)) != null) {
                    targetNode.performAction(1);
                    try {
                        Thread.sleep(50L);
                    } catch (InterruptedException e) {
                    }
                    Bundle arguments = new Bundle();
                    arguments.putCharSequence("ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE", text);
                    boolean success = targetNode.performAction(2097152, arguments);
                    if (!success) {
                        ClipboardManager clipboard = (ClipboardManager) MyAccessibilityService.instance.getSystemService("clipboard");
                        ClipData clip = ClipData.newPlainText("GTAndroid", text);
                        clipboard.setPrimaryClip(clip);
                        targetNode.performAction(32768);
                    }
                    rootNode.recycle();
                }
            }
        }, 100L);
    }

    public static void pressEnter() {
        if (instance == null) {
            return;
        }
        new Handler().postDelayed(new Runnable() { // from class: gtandroid.universal.MyAccessibilityService.2
            @Override // java.lang.Runnable
            public void run() {
                AccessibilityNodeInfo targetNode;
                AccessibilityNodeInfo rootNode = MyAccessibilityService.instance.getRootInActiveWindow();
                if (rootNode != null && (targetNode = MyAccessibilityService.findEditableNode(rootNode)) != null) {
                    Bundle arguments = new Bundle();
                    arguments.putCharSequence("ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE", "\n");
                    targetNode.performAction(2097152, arguments);
                    rootNode.recycle();
                }
            }
        }, 100L);
    }

    public static void insertTextAndSend(final String text) {
        if (instance == null) {
            return;
        }
        new Handler().postDelayed(new Runnable() { // from class: gtandroid.universal.MyAccessibilityService.3
            @Override // java.lang.Runnable
            public void run() {
                AccessibilityNodeInfo targetNode;
                AccessibilityNodeInfo rootNode = MyAccessibilityService.instance.getRootInActiveWindow();
                if (rootNode != null && (targetNode = MyAccessibilityService.findEditableNode(rootNode)) != null) {
                    targetNode.performAction(1);
                    try {
                        Thread.sleep(50L);
                    } catch (InterruptedException e) {
                    }
                    Bundle arguments = new Bundle();
                    arguments.putCharSequence("ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE", text + "\n");
                    targetNode.performAction(2097152, arguments);
                    rootNode.recycle();
                }
            }
        }, 100L);
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static AccessibilityNodeInfo findEditableNode(AccessibilityNodeInfo node) {
        AccessibilityNodeInfo result;
        if (node == null) {
            return null;
        }
        if (node.isEditable() && node.isEnabled() && node.isFocused()) {
            return node;
        }
        if (node.isEditable() && node.isEnabled()) {
            return node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null && (result = findEditableNode(child)) != null) {
                return result;
            }
        }
        return null;
    }

    public static boolean isAvailable() {
        return instance != null;
    }
}
