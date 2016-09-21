package utils;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.application.ApplicationManager;

public class NotificationUtils {

    private static final String TITLE = "FreeLine Plugin";
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
     * @param message
     */
    public static void errorNotification(final String message) {
        showNotification(message, NotificationType.ERROR);
    }

    /**
     *  show a info Notification
     * @param message
     */
    public static void infoNotification(final String message) {
        showNotification(message, NotificationType.INFORMATION);
    }

}
