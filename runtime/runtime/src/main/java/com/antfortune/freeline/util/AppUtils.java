package com.antfortune.freeline.util;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.text.TextUtils;
import android.util.Log;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Created by huangyong on 16/7/30.
 */
public class AppUtils {

    private static final String TAG = "Freeline.AppUtils";

    public static boolean isApkDebugable(Context context) {
        try {
            ApplicationInfo info = context.getApplicationInfo();
            return (info.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
        } catch (Exception e) {

        }
        return false;
    }

    public static String getCurProcessName(Context context) {
        String strRet = null;
        try {
            Class<?> clazz = Class.forName("android.ddm.DdmHandleAppName");
            Method method = clazz.getDeclaredMethod("getAppName");
            strRet = (String) method.invoke(clazz);
        } catch (Exception e) {
            Log.w(TAG, e);
        }
        if (TextUtils.isEmpty(strRet)) {
            final int pid = android.os.Process.myPid();
            android.app.ActivityManager activityManager = (android.app.ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            final List<ActivityManager.RunningAppProcessInfo> runningAppProcesses = activityManager.getRunningAppProcesses();
            for (ActivityManager.RunningAppProcessInfo appProcess : runningAppProcesses) {
                if (appProcess.pid == pid) {
                    strRet = appProcess.processName;
                    break;
                }
            }
        }
        return strRet;
    }

    public static boolean isMainProcess(Context context) {
        String packageName = context.getPackageName();
        String processName = getCurProcessName(context);
        return packageName.equalsIgnoreCase(processName);
    }

    public static boolean isFreelineProcess(Context context) {
        String processName = getCurProcessName(context);
        return processName.endsWith(":freeline");
    }

    public static String findJniLibrary(Context context, String libName) {
        String result = null;
        ClassLoader classLoader = (context.getClassLoader());
        if (classLoader != null) {
            try {
                Method findLibraryMethod = classLoader.getClass().getMethod("findLibrary", new Class<?>[] { String.class });
                if (findLibraryMethod != null) {
                    Object objPath = findLibraryMethod.invoke(classLoader, new Object[] { libName });
                    if (objPath != null && objPath instanceof String) {
                        result = (String) objPath;
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, e.toString());
            }
        }

        return result;
    }

}
