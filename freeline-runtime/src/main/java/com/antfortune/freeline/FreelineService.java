package com.antfortune.freeline;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.text.TextUtils;
import android.util.Log;

import com.antfortune.freeline.router.Router;
import com.antfortune.freeline.server.LongLinkServer;

import java.util.concurrent.TimeUnit;

/**
 * Created by huangyong on 16/7/31.
 */
public class FreelineService extends Service {

    private static final String LOG_TAG = "Freeline.Service";
    private static final int SERVICE_NOTIFICATION_ID = 8861;
    private static final String ACTION_KEEP_LIVE = ".Notification_RTC_WAKEUP_PUSH";

    private AlarmManager am = null;
    private PendingIntent mCheckSender = null;

    @Override
    public void onCreate() {
        super.onCreate();
        this.am = (AlarmManager) this.getSystemService(Context.ALARM_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(LOG_TAG, "onStartCommand Received start id " + startId + ", intent: " + intent);
        LongLinkServer.start(this.getApplication(), Router.getInstance());

        String marker = intent == null ? null : intent.getStringExtra("wakeup");
        if (TextUtils.isEmpty(marker)) {
            try {
                setForegroundService();
            } catch (Exception e) {
                Log.e(LOG_TAG, "setForegroundService fail", e);
            }
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            stopForeground(true);
            startAlarmTimer(TimeUnit.SECONDS.toMillis(5));
        } catch (Exception e) {
            Log.e(LOG_TAG, "stopForeground fail", e);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    protected void startAlarmTimer(long nextTime) {
        Log.i(LOG_TAG, "startAlarmTimer ELAPSED_REALTIME_WAKEUP! nextTime=" + nextTime);

        Intent intent = new Intent();
        intent.setAction(this.getPackageName() + ACTION_KEEP_LIVE);
        this.mCheckSender = PendingIntent.getBroadcast(this, 100, intent, 0);

        try {
            if (am != null && mCheckSender != null) {
                am.set(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + nextTime, mCheckSender);
            }
        } catch (Exception e) {
            Log.e(LOG_TAG, "startAlarmTimer fail", e);
        }
    }

    private void setForegroundService() {
        if (Build.VERSION.SDK_INT < 18) {
            startForeground(SERVICE_NOTIFICATION_ID, new Notification());
        } else {
            startForeground(SERVICE_NOTIFICATION_ID, new Notification());

            Intent innerIntent = new Intent(this, InnerService.class);
            startService(innerIntent);
        }
    }

    public static class InnerService extends Service {
        private static final String LOG_TAG_INNER = LOG_TAG + "$Inner";

        @Override
        public IBinder onBind(Intent intent) {
            return null;
        }

        @Override
        public int onStartCommand(Intent intent, int flags, int startId) {
            Log.i(LOG_TAG_INNER, "onStartCommand Received start id " + startId + ", intent: " + intent);
            try{
                startForeground(SERVICE_NOTIFICATION_ID, new Notification());
                stopForeground(true);
                stopSelf();
            } catch (Exception e) {
                Log.e(LOG_TAG_INNER, "startForeground, stopForeground, stopSelf fail", e);
            }
            return super.onStartCommand(intent, flags, startId);
        }

    }

}
