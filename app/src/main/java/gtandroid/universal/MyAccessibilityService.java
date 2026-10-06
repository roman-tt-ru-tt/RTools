package gtandroid.universal;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.os.Bundle;
import android.os.Handler;
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
