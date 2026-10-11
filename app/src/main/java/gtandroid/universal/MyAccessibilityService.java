package gtandroid.universal;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Toast;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import org.json.JSONArray;
import org.json.JSONObject;

public class MyAccessibilityService extends AccessibilityService {

    private static MyAccessibilityService instance;

    /** Более гибкий маркер — ловит и «[К-во репорта:», и «[ К-во репорта:». */
    private static final String REPORT_MARKER = "К-во репорта:";

    private String lastReportText = "";
    private long lastReportAt = 0L;

    public static boolean isAvailable() {
        return instance != null;
    }

    @Override
    public void onServiceConnected() {
        instance = this;
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes = AccessibilityEvent.TYPE_VIEW_SCROLLED
                | AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                | AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                | AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
                | AccessibilityEvent.TYPE_VIEW_FOCUSED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        info.notificationTimeout = 100L;
        setServiceInfo(info);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        try {
            StringBuilder text = new StringBuilder();
            if (event.getText() != null) {
                for (CharSequence item : event.getText()) {
                    if (item != null && item.length() > 0) text.append(item).append('\n');
                }
            }

            AccessibilityNodeInfo source = event.getSource();
            if (source != null) {
                collectNodeText(source, text, 0);
                source.recycle();
            }

            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) {
                collectNodeText(root, text, 0);
                root.recycle();
            }

            String value = text.toString();
            if (value.contains(REPORT_MARKER)) publishReportLines(value);

            // Обработка ADM-форм
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

    private void publishReportLines(String value) {
        String[] lines = value.split("\\r?\\n");
        for (String rawLine : lines) {
            if (!rawLine.contains(REPORT_MARKER)) continue;
            String report = rawLine.trim();
            if (report.isEmpty()) continue;
            long now = System.currentTimeMillis();
            if (report.equals(lastReportText) && now - lastReportAt < 2500L) continue;
            lastReportText = report;
            lastReportAt = now;

            // Сохраняем в список последних (для показа в "Ловля репортов")
            saveReportToRecent(report, now);

            // Broadcast для FloatingService
            Intent intent = new Intent("REPORT_CAUGHT");
            intent.putExtra("report_text", report);
            intent.putExtra("detected_at", now);
            sendBroadcast(intent);
        }
    }

    /** Сохраняет репорт в SharedPreferences GTAndroidReports.recent. */
    private void saveReportToRecent(String report, long time) {
        try {
            SharedPreferences sp = getSharedPreferences("GTAndroidReports", MODE_PRIVATE);
            JSONArray arr;
            try {
                arr = new JSONArray(sp.getString("recent", "[]"));
            } catch (Exception e) {
                arr = new JSONArray();
            }

            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null
                        && o.optString("text", "").equals(report)
                        && (time - o.optLong("time", 0)) < 2500L) {
                    return;
                }
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

    @Override
    public void onInterrupt() { }

    public static void insertText(final String text) {
        if (instance == null) return;
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                AccessibilityNodeInfo targetNode;
                AccessibilityNodeInfo rootNode = instance.getRootInActiveWindow();
                if (rootNode != null
                        && (targetNode = findEditableNode(rootNode)) != null) {
                    targetNode.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
                    try { Thread.sleep(50L); } catch (InterruptedException ignored) { }
                    Bundle arguments = new Bundle();
                    arguments.putCharSequence(
                            AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
                    boolean success = targetNode.performAction(
                            AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
                    if (!success) {
                        ClipboardManager clipboard = (ClipboardManager)
                                instance.getSystemService(CLIPBOARD_SERVICE);
                        ClipData clip = ClipData.newPlainText("RTools", text);
                        if (clipboard != null) clipboard.setPrimaryClip(clip);
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
            @Override
            public void run() {
                AccessibilityNodeInfo targetNode;
                AccessibilityNodeInfo rootNode = instance.getRootInActiveWindow();
                if (rootNode != null
                        && (targetNode = findEditableNode(rootNode)) != null) {
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
            @Override
            public void run() {
                AccessibilityNodeInfo targetNode;
                AccessibilityNodeInfo rootNode = instance.getRootInActiveWindow();
                if (rootNode != null
                        && (targetNode = findEditableNode(rootNode)) != null) {
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

    public static void insertTextAndPaste(String text) { }
    public static void pasteAndSend(String text) { }
                    }
