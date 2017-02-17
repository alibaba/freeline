package com.antfortune.freeline;

import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.text.TextUtils;
import android.util.Log;

import com.antfortune.freeline.util.ReflectUtil;

import java.lang.reflect.Constructor;

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
        initFreelineConfig();
        createRealApplication();
        FreelineCore.init(this, realApplication);
        startRealApplication();
    }

    @Override
    public Context createPackageContext(String packageName, int flags) throws PackageManager.NameNotFoundException {
        Context c = realApplication.createPackageContext(packageName, flags);
        return c == null ? realApplication : c;
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

}
