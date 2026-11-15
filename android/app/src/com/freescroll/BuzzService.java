package com.freescroll;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/** Trap 10 (buzz): social apps cannot pull you back with notifications. */
public class BuzzService extends NotificationListenerService {
    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (!Prefs.silence(this) || sbn.isOngoing()) return;
        String pkg = sbn.getPackageName();
        if (pkg.equals(getPackageName())) return;
        if (FreeScrollService.isSocial(this, pkg)) {
            cancelNotification(sbn.getKey());
            Core.buzzSilenced();
        }
    }
}
