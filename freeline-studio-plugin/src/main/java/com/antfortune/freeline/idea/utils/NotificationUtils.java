package com.antfortune.freeline.idea.utils;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.SystemInfo;

public class NotificationUtils {

    private static final String TITLE = "Freeline Plugin";
    private static final NotificationGroup NOTIFICATION_GROUP = NotificationGroup.balloonGroup(TITLE);

    /**
     * show a Notification
     *
     * @param message
     * @param type
     */
    public static void showNotification(final String message,
                                        final NotificationType type) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
                Notification notification =
                        NOTIFICATION_GROUP.createNotification(TITLE, message, type, null);
                Notifications.Bus.notify(notification);
            }
        });
    }

    /**
     * show a error Notification
     *
     * @param message
     */
    public static void errorNotification(final String message) {
        showNotification(message, NotificationType.ERROR);
    }

    /**
     * error message dialog
     * @param message
     */
    public static void errorMsgDialog(String message) {
        Messages.showMessageDialog(message, "Error", Messages.getInformationIcon());
    }

    /**
     * show a info Notification
     *
     * @param message
     */
    public static void infoNotification(final String message) {
        showNotification(message, NotificationType.INFORMATION);
    }

    /**
     * python提示
     */
    public static void pythonNotFound() {
        String tip = "command 'python' not found. ";
        if (SystemInfo.isWindows) {
            tip += "  if first installation python or Set Environment variable, Please restart your computer";
        }
        errorNotification(tip);
    }

}
