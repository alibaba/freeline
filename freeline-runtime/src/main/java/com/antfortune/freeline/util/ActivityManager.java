package com.antfortune.freeline.util;

import android.app.Activity;
import android.app.Application;
import android.app.Application.ActivityLifecycleCallbacks;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.antfortune.freeline.FreelineCore;
import com.antfortune.freeline.MiddlewareActivity;

import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Created by xianying on 16/3/16.
 */
public class ActivityManager {

    private static final String TAG = "Freeline.ActManager";

    public static final int ACTIVITY_NONE = 0;
    public static final int ACTIVITY_CREATED = 1;
    public static final int ACTIVITY_STARTED = 2;
    public static final int ACTIVITY_RESUMED = 3;

    private static final WeakHashMap<Activity, Integer> sActivitiesRefs = new WeakHashMap();

    private static long sFirstTaskId = 0L;

    private static final ActivityLifecycleCallbacks sLifecycleCallback = new Application.ActivityLifecycleCallbacks() {

        public void onActivityStopped(Activity activity) {
            sActivitiesRefs.put(activity, ACTIVITY_CREATED);
        }

        public void onActivityStarted(Activity activity) {
            sActivitiesRefs.put(activity, ACTIVITY_STARTED);
        }

        public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
        }

        public void onActivityResumed(Activity activity) {
            sActivitiesRefs.put(activity, ACTIVITY_RESUMED);
        }

        public void onActivityPaused(Activity activity) {
            sActivitiesRefs.put(activity, ACTIVITY_STARTED);
        }

        public void onActivityDestroyed(Activity activity) {
            sActivitiesRefs.remove(activity);
        }

        public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
            sActivitiesRefs.put(activity, ACTIVITY_CREATED);

            if (sFirstTaskId == 0L) {
                sFirstTaskId = activity.getTaskId();
            }
        }

    };

    public static void initApplication(Application app) {
        app.registerActivityLifecycleCallbacks(sLifecycleCallback);
    }

    public static boolean restart(final Context context, boolean confirm) {
        Activity top = getTopActivity();
        if(top instanceof MiddlewareActivity) {
            ((MiddlewareActivity)top).reset();
            return true;
        } else {
            try {
                Intent e = new Intent(context, MiddlewareActivity.class);
                e.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                e.putExtra("reset", confirm);
                context.startActivity(e);
                return true;
            } catch (Exception exception) {
                final String str = "Fail to increment build, make sure you have <Activity android:name=\"" + MiddlewareActivity.class.getName() + "\"/> registered in AndroidManifest.xml";
                Log.e(TAG, str);
                (new Handler(Looper.getMainLooper())).post(new Runnable() {
                    public void run() {
                        Toast.makeText(context, str, Toast.LENGTH_LONG).show();
                    }
                });
                return false;
            }
        }
    }

    public static void restartCurrentActivity() {
        final Activity top = getTopActivity();
        if(top instanceof Activity) {
            final Activity ac = (Activity)top;
            Intent i = ac.getIntent();
            boolean rst = false;
            Log.i(TAG, "activity " + ac.getComponentName() + " has singleTask:" + rst);
            i.addFlags(65536);
            ac.overridePendingTransition(0, 0);
            ac.startActivity(i);
            (new Handler(Looper.getMainLooper())).postDelayed(new Runnable() {
                public void run() {
                    Log.e(TAG, "first task id " + sFirstTaskId + " top actvitiy id " + ac.getTaskId());
                    Activity a = getTopActivity();
                    Log.e(TAG, "last top: " + top + " now top :" + a + " activity size :" + getAllActivities().length);
                    if (a == ac) {
                        ac.recreate();
                        Log.d(TAG, "restart :" + ac.getComponentName());
                    } else {
                        ac.finish();
                        ac.overridePendingTransition(0, 0);
                        Log.d(TAG, "finish :" + ac.getComponentName());
                    }

                }
            }, 200L);
        }

    }

    public static void restartForegroundActivity() {
        Activity foregroundActivity = Restarter.getForegroundActivity(FreelineCore.getApplication());
        if (foregroundActivity != null) {
            Restarter.restartActivityOnUiThread(foregroundActivity);
        }
    }
    
    public static void restartActivity() {
    	Activity[] activities = getAllActivities();
        if(activities != null && activities.length > 0) {
            (new Handler(Looper.getMainLooper())).post(new Runnable() {
                public void run() {
                    Exception err = null;
                    // Activity top = getTopActivity();
                    Activity[] activities = getAllActivities();
                    for (Activity a : activities) {
                        try {
                            a.recreate();
                            Log.d(TAG, "restartActivity :" + a.getComponentName());
                        } catch (Exception exception) {
                            err = exception;
                        }
                    }

                    if (err != null) {
                        throw new RuntimeException(err);
                    }
                }
            });
        }
    }

    public static Activity[] getAllActivities() {
        ArrayList<Activity> list = new ArrayList<>();
        for (Map.Entry<Activity, Integer> e : sActivitiesRefs.entrySet()) {
            Activity a = e.getKey();
            if (a != null && e.getValue().intValue() > 0) {
                list.add(a);
            }
        }
        return list.toArray(new Activity[list.size()]);
    }

    public static Activity getTopActivity() {
        Activity r = null;
        for (Map.Entry<Activity, Integer> e : sActivitiesRefs.entrySet()) {
            Activity a = e.getKey();
            if (a != null && e.getValue().intValue() == ACTIVITY_RESUMED) {
                r = a;
            }
        }
        return r;
    }




}
