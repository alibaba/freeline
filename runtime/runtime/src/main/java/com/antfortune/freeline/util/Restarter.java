/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.antfortune.freeline.util;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.ArrayMap;
import android.util.Log;
import android.widget.Toast;

import com.antfortune.freeline.resources.MonkeyPatcher;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handler capable of restarting parts of the application in order for changes to become
 * apparent to the user:
 * <ul>
 *     <li> Apply a tiny change immediately (possible if we can detect that the change
 *          is only used in a limited context (such as in a layout) and we can directly
 *          poke the view hierarchy and schedule a paint
 *     <li> Apply a change to the current activity. We can restart just the activity
 *          while the app continues running.
 *     <li> Restart the app with state persistence (simulates what happens when a user
 *          puts an app in the background, then it gets killed by the memory monitor,
 *          and then restored when the user brings it back
 *     <li> Restart the app completely.
 * </ul>
 */
public class Restarter {
    private static final String LOG_TAG = "Freeline.Restarter";

    /** Restart an activity. Should preserve as much state as possible. */
    public static void restartActivityOnUiThread(final Activity activity) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (Log.isLoggable(LOG_TAG, Log.VERBOSE)) {
                    Log.v(LOG_TAG, "Resources updated: notify activities");
                }
                updateActivity(activity);
            }
        });
    }
    private static void restartActivity(Activity activity) {
        if (Log.isLoggable(LOG_TAG, Log.VERBOSE)) {
            Log.v(LOG_TAG, "About to restart " + activity.getClass().getSimpleName());
        }
        // You can't restart activities that have parents: find the top-most activity
        while (activity.getParent() != null) {
            if (Log.isLoggable(LOG_TAG, Log.VERBOSE)) {
                Log.v(LOG_TAG, activity.getClass().getSimpleName()
                        + " is not a top level activity; restarting "
                        + activity.getParent().getClass().getSimpleName() + " instead");
            }
            activity = activity.getParent();
        }
        // Directly supported by the framework!
        activity.recreate();
    }
    /**
     * Attempt to restart the app. Ideally this should also try to preserve as much state as
     * possible:
     * <ul>
     *     <li>The current activity</li>
     *     <li>If possible, state in the current activity, and</li>
     *     <li>The activity stack</li>
     * </ul>
     *
     * This may require some framework support. Apparently it may already be possible
     * (Dianne says to put the app in the background, kill it then restart it; need to
     * figure out how to do this.)
     */
    public static void restartApp(Context appContext,
                                  Collection<Activity> knownActivities,
                                  boolean toast) {
        if (!knownActivities.isEmpty()) {
            // Can't live patch resources; instead, try to restart the current activity
            Activity foreground = getForegroundActivity(appContext);
            if (foreground != null) {
                // http://stackoverflow.com/questions/6609414/howto-programatically-restart-android-app
                //noinspection UnnecessaryLocalVariable
                if (toast) {
                    showToast(foreground, "Restarting app to apply incompatible changes");
                }
                if (Log.isLoggable(LOG_TAG, Log.VERBOSE)) {
                    Log.v(LOG_TAG, "RESTARTING APP");
                }
                @SuppressWarnings("UnnecessaryLocalVariable") // fore code clarify
                        Context context = foreground;
                Intent intent = new Intent(context, foreground.getClass());
                int intentId = 0;
                PendingIntent pendingIntent = PendingIntent.getActivity(context, intentId,
                        intent, PendingIntent.FLAG_CANCEL_CURRENT);
                AlarmManager mgr = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
                mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 100, pendingIntent);
                if (Log.isLoggable(LOG_TAG, Log.VERBOSE)) {
                    Log.v(LOG_TAG, "Scheduling activity " + foreground
                            + " to start after exiting process");
                }
            } else {
                showToast(knownActivities.iterator().next(), "Unable to restart app");
                if (Log.isLoggable(LOG_TAG, Log.VERBOSE)) {
                    Log.v(LOG_TAG, "Couldn't find any foreground activities to restart " +
                            "for resource refresh");
                }
            }
            System.exit(0);
        }
    }
    static void showToast(final Activity activity, final String text) {
        if (Log.isLoggable(LOG_TAG, Log.VERBOSE)) {
            Log.v(LOG_TAG, "About to show toast for activity " + activity + ": " + text);
        }
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    Context context = activity.getApplicationContext();
                    if (context instanceof ContextWrapper) {
                        Context base = ((ContextWrapper) context).getBaseContext();
                        if (base == null) {
                            if (Log.isLoggable(LOG_TAG, Log.WARN)) {
                                Log.w(LOG_TAG, "Couldn't show toast: no base context");
                            }
                            return;
                        }
                    }
                    // For longer messages, leave the message up longer
                    int duration = Toast.LENGTH_SHORT;
                    if (text.length() >= 60 || text.indexOf('\n') != -1) {
                        duration = Toast.LENGTH_LONG;
                    }
                    // Avoid crashing when not available, e.g.
                    //   java.lang.RuntimeException: Can't create handler inside thread that has
                    //        not called Looper.prepare()
                    Toast.makeText(activity, text, duration).show();
                } catch (Throwable e) {
                    if (Log.isLoggable(LOG_TAG, Log.WARN)) {
                        Log.w(LOG_TAG, "Couldn't show toast", e);
                    }
                }
            }
        });
    }

    public static Activity getForegroundActivity(Context context) {
        List<Activity> list = getActivities(context, true);
        return list.isEmpty() ? null : list.get(0);
    }
    // http://stackoverflow.com/questions/11411395/how-to-get-current-foreground-activity-context-in-android
    public static List<Activity> getActivities(Context context, boolean foregroundOnly) {
        List<Activity> list = new ArrayList<Activity>();
        try {
            Class activityThreadClass = Class.forName("android.app.ActivityThread");
            Object activityThread = MonkeyPatcher.getActivityThread(context, activityThreadClass);
            Field activitiesField = activityThreadClass.getDeclaredField("mActivities");
            activitiesField.setAccessible(true);
            // TODO: On older platforms, cast this to a HashMap
            Collection c;
            Object collection = activitiesField.get(activityThread);
            if (collection instanceof HashMap) {
                // Older platforms
                Map activities = (HashMap) collection;
                c = activities.values();
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT &&
                    collection instanceof ArrayMap) {
                ArrayMap activities = (ArrayMap) collection;
                c = activities.values();
            } else {
                return list;
            }
            for (Object activityRecord : c) {
                Class activityRecordClass = activityRecord.getClass();
                if (foregroundOnly) {
                    Field pausedField = activityRecordClass.getDeclaredField("paused");
                    pausedField.setAccessible(true);
                    if (pausedField.getBoolean(activityRecord)) {
                        continue;
                    }
                }
                Field activityField = activityRecordClass.getDeclaredField("activity");
                activityField.setAccessible(true);
                Activity activity = (Activity) activityField.get(activityRecord);
                if (activity != null) {
                    list.add(activity);
                }
            }
        } catch (Throwable ignore) {
        }
        return list;
    }
    private static void updateActivity(Activity activity) {
        // This method can be called for activities that are not in the foreground, as long
        // as some of its resources have been updated. Therefore we'll need to make sure
        // that this activity is in the foreground, and if not do nothing. Ways to do
        // that are outlined here:
        // http://stackoverflow.com/questions/3667022/checking-if-an-android-application-is-running-in-the-background/5862048#5862048
        // Try to force re-layout; there are many approaches; see
        // http://stackoverflow.com/questions/5991968/how-to-force-an-entire-layout-view-refresh
        // This doesn't seem to update themes properly -- may need to do recreate() instead!
        //getWindow().getDecorView().findViewById(android.R.id.content).invalidate();
        // This is a bit of a sledgehammer. We should consider having an incremental updater,
        // similar to IntelliJ's Look &amp; Feel updater which iterates to the view hierarchy
        // and tries to incrementally refresh the LAF delegates and force a repaint.
        // On the other hand, we may never be able to succeed with that, since there could be
        // UI elements on the screen cached from callbacks. I should probably *not* attempt
        // to try to poke the user's data models; recreating the current layout should be
        // enough (e.g. if a layout references @string/foo, we'll recreate those widgets
        //    if (mLastContentView != -1) {
        //        setContentView(mLastContentView);
        //    } else {
        //        recreate();
        //    }
        // -- nope, even that's iffy. I had code which *after* calling setContentView would
        // do some findViewById calls etc to reinitialize views.
        //
        // So what I should really try to do is have some knowledge about what changed,
        // and see if I can figure out that the change is minor (e.g. doesn't affect themes
        // or layout parameters etc), and if so, just try to poke the view hierarchy directly,
        // and if not, just recreate
        //    if (changeManager.isSimpleDelta()) {
        //        changeManager.applyDirectly(this);
        //    } else {
        // Note: This doesn't handle manifest changes like changing the application title
        restartActivity(activity);
    }
    /** Show a toast when an activity becomes available (if possible). */
    public static void showToastWhenPossible(Context context, String message) {
        Activity activity = Restarter.getForegroundActivity(context);
        if (activity != null) {
            Restarter.showToast(activity, message);
        } else {
            // Only try for about 10 seconds
            showToastWhenPossible(context, message, 10);
        }
    }
    private static void showToastWhenPossible(
            final Context context,
            final String message,
            final int remainingAttempts) {
        Looper mainLooper = Looper.getMainLooper();
        Handler handler = new Handler(mainLooper);
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Activity activity = getForegroundActivity(context);
                if (activity != null) {
                    showToast(activity, message);
                } else {
                    if (remainingAttempts > 0) {
                        showToastWhenPossible(context, message, remainingAttempts - 1);
                    }
                }
            }
        }, 1000);
    }
}
