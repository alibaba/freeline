package com.antfortune.freeline;

import android.app.Application;
import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.antfortune.freeline.resources.MonkeyPatcher;
import com.antfortune.freeline.util.ReflectUtil;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Map;

/**
 * Created by huangyong on 16/9/14.
 */

public class FreelineApplication extends Application {

    protected static final String TAG = "FreelineApplication";

    private Class freelineConfigClazz;

    private Application realApplication;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "FreelineApplication#onCreate()");
        FreelineCore.init(this);
        initFreelineConfig();
        createRealApplication();
        replaceApplication();
        startRealApplication();
    }

    private void startRealApplication() {
        if (realApplication != null) {
            try {
                ReflectUtil.invokeMethod(Application.class, realApplication, "attach", new Class[]{Context.class}, new Object[]{getBaseContext()});
                Log.d(TAG, "realApplication#attach(Context)");
            } catch (Exception e) {
                FreelineCore.printStackTrace(e);
                Log.e(TAG, "attach with realApplication error");
            }

            realApplication.onCreate();
            Log.d(TAG, "realApplication#onCreate()");
        }
    }

    private void initFreelineConfig() {
        try {
            freelineConfigClazz = Class.forName("com.antfortune.freeline.FreelineConfig");
        } catch (Exception e) {
            FreelineCore.printStackTrace(e);
            Log.e(TAG, "initFreelineConfig error");
        }
    }

    private String getConfigValue(String fieldName) {
        try {
            return ReflectUtil.getStaticFieldValue(freelineConfigClazz, fieldName).toString();
        } catch (Exception e) {
            FreelineCore.printStackTrace(e);
            Log.e(TAG, "get config value error");
            return "";
        }
    }

    private void createRealApplication() {
        String applicationClass = getConfigValue("applicationClass");
        if (TextUtils.isEmpty(applicationClass)) {
            realApplication = new Application();
            Log.d(TAG, "create empty application.");
        } else {
            try {
                Class realClass = Class.forName(applicationClass);
                Constructor<? extends Application> constructor = realClass.getConstructor();
                this.realApplication = constructor.newInstance();
                Log.d(TAG, "create application: " + applicationClass);
            } catch (Exception e) {
                FreelineCore.printStackTrace(e);
                Log.e(TAG, "create real application error");
            }
        }
    }

    private void replaceApplication() {
        try {
            Log.d(TAG, "FreelineApplication#replaceApplication()");
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Field applicationField = getApplicationField();
            Field packagesField = getPackagesField(activityThreadClass);
            Field loadedApkField = getLoadedApkField();
            Object currentActivityThread = MonkeyPatcher.getActivityThread(this, activityThreadClass);

            Map<String, WeakReference<?>> packages = (Map<String, WeakReference<?>>) packagesField.get(currentActivityThread);
            for (Map.Entry<String, WeakReference<?>> entry : packages.entrySet()) {
                Object loadedApk = entry.getValue().get();
                if (loadedApk == null) {
                    continue;
                }
                applicationField.set(loadedApk, realApplication);
                if (loadedApkField != null) {
                    loadedApkField.setAccessible(true);
                    loadedApkField.set(realApplication, loadedApk);
                }
            }
            Log.d(TAG, "replace Application success.");
        } catch (Exception e) {
            FreelineCore.printStackTrace(e);
            Log.e(TAG, "replace application error.");
        }
    }

    private Field getApplicationField() throws ClassNotFoundException, NoSuchFieldException {
        Class<?> loadedApkClass;
        try {
            loadedApkClass = Class.forName("android.app.LoadedApk");
        } catch (ClassNotFoundException e) {
            loadedApkClass = Class.forName("android.app.ActivityThread$PackageInfo");
        }
        Field applicationField = loadedApkClass.getDeclaredField("mApplication");
        applicationField.setAccessible(true);
        return applicationField;
    }

    private Field getPackagesField(Class activityThreadClass) throws ClassNotFoundException, NoSuchFieldException {
        Field packagesField = activityThreadClass.getDeclaredField("mPackages");
        packagesField.setAccessible(true);
        return packagesField;
    }

    private Field getLoadedApkField() {
        try {
            return Application.class.getDeclaredField("mLoadedApk");
        } catch (NoSuchFieldException e) {
            return null;
        }
    }
}
