package gtandroid.universal;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Toast;
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
        info.eventTypes = 2089;
        info.feedbackType = 16;
        info.flags = 4;
        info.notificationTimeout = 100L;
        setServiceInfo(info);
    }

    @Override // android.accessibilityservice.AccessibilityService
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        try {
            CharSequence packageName = event.getPackageName();
            // Сервис слушает только события с текстом. Фильтр по пакету намеренно мягкий:
            // разные сборки Grand Mobile могут иметь разные packageName.
            StringBuilder text = new StringBuilder();
            for (CharSequence item : event.getText()) {
                if (item != null) text.append(item).append('\n');
            }
            if (text.length() == 0 && event.getSource() != null) {
                collectNodeText(event.getSource(), text, 0);
            }
            String value = text.toString();
            if (!value.contains("<ADM>") && event.getSource() != null) {
                StringBuilder sourceText = new StringBuilder();
                collectNodeText(event.getSource(), sourceText, 0);
                if (sourceText.length() > 0) value = sourceText.toString();
            }
            if (!value.contains("<ADM>")) return;

            for (AdminFormManager.Form form : AdminFormManager.parseForms(value)) {
                if (AdminFormManager.addForm(this, form)) {
                    Intent intent = new Intent("ADMIN_FORM_RECEIVED");
                    intent.putExtra("nickname", form.nickname);
                    intent.putExtra("id", form.id);
                    intent.putExtra("command", form.command);
                    sendBroadcast(intent);
                }
            }
        } catch (Exception ignored) {
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
