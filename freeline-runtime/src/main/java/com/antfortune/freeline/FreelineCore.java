package com.antfortune.freeline;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import com.antfortune.freeline.gradle.GradleDynamic;
import com.antfortune.freeline.resources.MonkeyPatcher;
import com.antfortune.freeline.util.ActivityManager;
import com.antfortune.freeline.util.AppUtils;
import com.antfortune.freeline.util.DexUtils;
import com.antfortune.freeline.util.FileUtils;
import com.antfortune.freeline.util.NativeUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Field;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dalvik.system.PathClassLoader;

/**
 * Created by xianying on 16/3/16.
 */
public class FreelineCore {

    private static final String TAG = "Freeline";

    private static final String DYNAMIC_INFO_FILE_NAME = "FREELINE_DYNAMIC_INFO";

    private static final String SYNC_INFO_FILE_NAME = "FREELINE_SYNC_INFO";

    public static final String DEFAULT_PACKAGE_ID = "base-res.key";

    private static final String DYNAMIC_INFO_DEX_PATH_KEY = "dynamic_dex_key";

    private static final String DYNAMIC_INFO_DEX_DIR_KEY = "dynamic_dex_dir_key";

    private static final String DYNAMIC_INFO_OPT_PATH_KEY = "dynamic_opt_key";

    private static long sApkBuildFlag = 0;

    private static Application sApplication;

    private static Application sRealApplication;

    private static IDynamic sDynamic;

    public static void init(Application app, Application realApplication) {
        sRealApplication = realApplication;
        init(app, new GradleDynamic(app));
    }

    @Deprecated
    public static void init(Application app) {
        init(app, new GradleDynamic(app));
    }

    public static void init(Application app, IDynamic dynamicImpl) {
        Log.i(TAG, "freeline start initial process...");
        sApplication = app;
        setDynamicImpl(dynamicImpl);

        // 子进程也可以应用 patch
        //if (AppUtils.isApkDebugable(app) && AppUtils.isMainProcess(app)) {
        if (AppUtils.isApkDebugable(app)) {
            Log.i(TAG, "freeline init application");
            ActivityManager.initApplication(app);
            MonkeyPatcher.monkeyPatchApplication(app, app, sRealApplication, null);

            try {
                Object mPackageInfo = getPackageInfo(app);
                Field field = mPackageInfo.getClass().getDeclaredField("mClassLoader");
                field.setAccessible(true);
                PathClassLoader origin = (PathClassLoader) field.get(mPackageInfo);

                if (checkVersionChange()) {
                    Log.i(TAG, "the apk has recover, delete cache");
                    clearDynamicCache();
                    clearSyncCache();
                } else {
                    Log.i(TAG, "start to inject dex...");
                    injectDex(origin);
                    Log.i(TAG, "start to inject resources...");
                    injectResources();
                }

                Log.i(TAG, "start to load hackload.dex...");
                injectHackDex(app, origin);
                Log.i(TAG, "start to inject native lib...");
                injectHackNativeLib(app,origin);
            } catch (Exception e) {
                printStackTrace(e);
            }

            Log.i(TAG, "freeline init server");
            startLongLinkServer();
        }
    }

    public static void setDynamicImpl(IDynamic dynamicImpl) {
        sDynamic = dynamicImpl;
    }

    private static void startLongLinkServer() {
        Intent intent = new Intent(sApplication, FreelineService.class);
        sApplication.startService(intent);
    }

    private static String getDynamicDexPath() {
        return getDynamicInfoSp().getString(DYNAMIC_INFO_DEX_PATH_KEY, null);
    }

    private static String getDynamicDexDirPath() {
        return getDynamicInfoSp().getString(DYNAMIC_INFO_DEX_DIR_KEY, null);
    }

    private static String getDynamicDexOptPath() {
        return getDynamicInfoSp().getString(DYNAMIC_INFO_OPT_PATH_KEY, null);
    }

    public static void clearDynamicCache() {
        getDynamicInfoSp().edit().clear().commit();
        FileUtils.rm(new File(getDynamicInfoTempDir()));
        Log.i(TAG, "clear dynamic info sp cache");
    }

    public static void clearSyncCache() {
        getSyncInfoSp().edit().clear().commit();
        Log.i(TAG, "clear sync info sp cache");
    }

    public static long getBuildTime(Context context) {
        // 使用 ApkBuildFlag 作为基点进行对比，判断应用版本是否匹配
        long buildTime = getApkBuildFlag();
        Log.i(TAG, "Build Time is: " + buildTime);
        return buildTime;
    }

    public static long getApkBuildFlag() {
        if (sApkBuildFlag == 0) {
            try {
                InputStream is = sApplication.getAssets().open("apktime");
                int size = is.available();

                // Read the entire asset into a local byte buffer.
                byte[] buffer = new byte[size];
                is.read(buffer);
                is.close();

                String text = new String(buffer, "GB2312");
                Log.i(TAG, "ext:" + text);
                sApkBuildFlag = Long.parseLong(text);
            } catch (Exception e) {
                FreelineCore.printStackTrace(e);
            }
        }
        return sApkBuildFlag;
    }

    private static void copyAssets(Context context, String assetName, String strOutFileName) throws IOException {
        InputStream myInput;
        OutputStream myOutput = new FileOutputStream(strOutFileName);
        myInput = context.getAssets().open(assetName);
        byte[] buffer = new byte[1024];
        int length = myInput.read(buffer);
        while (length > 0) {
            myOutput.write(buffer, 0, length);
            length = myInput.read(buffer);
        }
        myOutput.flush();
        myInput.close();
        myOutput.close();
    }


    private static String getDynamicCacheDir() {
        File dir = new File(sApplication.getCacheDir(), "hack");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir.getAbsolutePath();
    }

    private static long getDynamicTime() {
        // 使用 ApkBuildFlag 作为基点进行对比，判断应用版本是否匹配
        long dynamicTime = getDynamicInfoSp().getLong("dynamicTime", getApkBuildFlag());
        Log.i(TAG, "Dynamic Time is: " + dynamicTime);
        return dynamicTime;
    }

    private static boolean checkVersionChange() {
        return getBuildTime(sApplication) > getDynamicTime();
    }


    private static Object getPackageInfo(Application app) throws NoSuchFieldException,
            IllegalArgumentException, IllegalAccessException {
        Context contextImpl = app.getBaseContext();
        Field mPackageInfoField = contextImpl.getClass().getDeclaredField(
                "mPackageInfo");
        mPackageInfoField.setAccessible(true);
        Object mPackageInfo = mPackageInfoField.get(contextImpl);
        return mPackageInfo;
    }

    private static void injectHackDex(Context context, PathClassLoader origin) {
        File hackDex = new File(getDynamicCacheDir(), "hackload.dex");
        if (!hackDex.exists() || hackDex.length() < 100) {
            try {
                copyAssets(context, "hackload.dex", hackDex.getAbsolutePath());
                Log.i(TAG, "copy hackload dex from assets success");
            } catch (Exception e) {
                printStackTrace(e);
            }
        }
        if (hackDex.exists() && hackDex.length() > 100) {
            File opt = new File(getDynamicCacheDir(), "opt");
            if (!opt.exists()) {
                opt.mkdirs();
            }
            DexUtils.inject(origin, hackDex, opt);
            Log.i(TAG, "load hackload，dex size:" + hackDex.length());
        }
    }

    private static void injectHackNativeLib(Context context, PathClassLoader classLoader) {
        // 修复so patch不生效问题
        try {
            NativeUtils.installNativeLibraryPath(classLoader, new File(getDynamicNativeDir()), false);
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }
        //NativeUtils.injectHackNativeLib(getDynamicNativeDir(), classLoader);
    }


    private static void injectDex(PathClassLoader origin) {
        String dexDirPath = getDynamicDexDirPath();
        if (!TextUtils.isEmpty(dexDirPath)) {
            File dexDir = new File(dexDirPath);
            if (dexDir.isDirectory()) {
                File[] dexFiles = dexDir.listFiles();

                if (dexFiles.length > 0) {
                    File opt = new File(getDynamicDexOptPath());
                    if (!opt.exists()) {
                        opt.mkdirs();
                    }
                    for (File dexFile : dexFiles) {
                        String dirName = generateStringMD5(dexFile.getName());
                        File dexOptDir = new File(opt, dirName);
                        if (!dexOptDir.exists()) {
                            dexOptDir.mkdirs();
                        }
                        DexUtils.inject(origin, dexFile, dexOptDir);
                    }
                    Log.i(TAG, "find increment package");
                }
            }
        }
    }

    public static void injectResources() {
        Map<String, ?> map = getDynamicInfoSp().getAll();
        Log.i(TAG, "dynamicInfoSp: " + map.toString());
        HashMap<String, String> resMap = new HashMap<String, String>();
        for (String key : map.keySet()) {
            if (key.contains("-")) {
                resMap.put(key, (String) map.get(key));
            }
        }
        Log.i(TAG, "resMap: " + resMap.toString());
        if (!resMap.isEmpty()) {
            applyDynamicRes(resMap);
        }
    }

    public static boolean applyDynamicDex(String dexFileStr, String dexOptDir) {
        Log.i(TAG, "apply dynamicDex " + dexFileStr);
        SharedPreferences sp = getDynamicInfoSp();
        SharedPreferences.Editor editor = sp.edit();
        //editor.putString(DYNAMIC_INFO_DEX_PATH_KEY, dexFileStr);
        editor.putString(DYNAMIC_INFO_DEX_DIR_KEY, dexFileStr);
        editor.putString(DYNAMIC_INFO_OPT_PATH_KEY, dexOptDir);
        editor.commit();
        return true;
    }

    public static void printStackTrace(Throwable aThrowable) {
        Writer result = new StringWriter();
        PrintWriter printWriter = new PrintWriter(result);
        aThrowable.printStackTrace(printWriter);
        String resultStr = result.toString();
        Log.e(TAG, resultStr);
    }


    public static String getDynamicResPath(String packageId) {
        return getDynamicInfoSp().getString(getDynamicResPathKey(packageId), null);
    }

    private static String getDynamicResPathKey(String packageId) {
        return packageId + ".key";
    }

    public static long getLastDynamicSyncId() {
        return getSyncInfoSp().getLong("lastSync", 0);
    }

    public static void saveLastDynamicSyncId(long sync) {
        getSyncInfoSp().edit().putLong("lastSync", sync).commit();
    }

    private static SharedPreferences getDynamicInfoSp() {
        return sApplication.getBaseContext().getSharedPreferences(DYNAMIC_INFO_FILE_NAME, Context.MODE_PRIVATE);
    }

    private static SharedPreferences getSyncInfoSp() {
        return sApplication.getBaseContext().getSharedPreferences(SYNC_INFO_FILE_NAME, Context.MODE_PRIVATE);
    }

    public static String getBundleFilePathByPackageId(String packageId) {
        if (sDynamic != null) {
            return sDynamic.getOriginResPath(packageId);
        }
        return null;
    }

    private static boolean applyDynamicRes(HashMap<String, String> dynamicRes) {
        if (sDynamic != null) {
            return sDynamic.applyDynamicRes(dynamicRes);
        }
        return false;
    }

    public static Application getRealApplication() {
        return sRealApplication;
    }

    public static void updateDynamicTime() {
        // 使用 ApkBuildFlag 作为基点进行对比，判断应用版本是否匹配
        long dynamicTime = getApkBuildFlag();
        Log.i(TAG, "update dynamic time: " + dynamicTime);
        getDynamicInfoSp().edit().putLong("dynamicTime", dynamicTime).commit();
    }

    /***
     * packagid + newResPath
     *
     * @param dynamicRes
     */
    public static boolean saveDynamicResInfo(HashMap<String, String> dynamicRes) {
        boolean result = true;
        SharedPreferences sp = getDynamicInfoSp();
        SharedPreferences.Editor editor = sp.edit();
        for (String packageId : dynamicRes.keySet()) {
            String pendingPath = dynamicRes.get(packageId);
            editor.putString(getDynamicResPathKey(packageId), pendingPath);
        }
        editor.commit();
        Log.i(TAG, "apply res :" + dynamicRes);
        injectResources();
        return result;
    }


    public static String getDynamicInfoTempDir() {
        File dir = new File(sApplication.getCacheDir(), "temp");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir.getAbsolutePath();
    }

    public static String getDynamicDexDir() {
        File dir = new File(getDynamicInfoTempDir(), "dex");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir.getAbsolutePath();
    }

    public static String getDynamicNativeDir() {
        File dir = new File(getDynamicInfoTempDir(), "native");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir.getAbsolutePath();
    }

    public static String getDynamicInfoTempPath(String packageId) {
        File dir;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            dir = new File(getDynamicInfoTempDir(), packageId + ".jar");
        } else {
            dir = new File(getDynamicInfoTempDir(), packageId);
        }
        return dir.getAbsolutePath();
    }

    public static String getUuid() {
        return String.valueOf(getApkBuildFlag());
    }

    private static String generateStringMD5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] bytes = md.digest(input.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte aByte : bytes) {
                sb.append(Integer.toHexString((aByte & 0xFF) | 0x100).substring(1, 3));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "MD5 algorithm not found.");
            return input;
        }
    }

    public static void clearResourcesCache() {
        if (sDynamic != null) {
            sDynamic.clearResourcesCache();
        }
    }

    public static Application getApplication() {
        return sApplication;
    }

    public static void updateActivity(String bundleName, String path) {
        Intent intent = new Intent();
        intent.setAction("android.intent.action.FreelineReceiver");
        intent.putExtra(FreelineReceiver.UUID, getUuid());
        intent.putExtra(FreelineReceiver.ACTION_KEY, FreelineReceiver.ACTION_UPDATE_ACTIVITY);
        intent.putExtra(FreelineReceiver.SP_KEY, bundleName);
        intent.putExtra(FreelineReceiver.SP_VALUE, path);
        sApplication.sendBroadcast(intent);
    }

    public static void restartApplication(String bundleName, String path, String dexPath, String dirPath) {
        Intent intent = new Intent();
        intent.setAction("android.intent.action.FreelineReceiver");
        intent.putExtra(FreelineReceiver.UUID, getUuid());
        intent.putExtra(FreelineReceiver.ACTION_KEY, FreelineReceiver.ACTION_RESTART_APPLICATION);
        intent.putExtra(FreelineReceiver.SP_KEY, bundleName);
        intent.putExtra(FreelineReceiver.SP_VALUE, path);
        intent.putExtra(FreelineReceiver.DEX_VALUE, dexPath);
        intent.putExtra(FreelineReceiver.OPT_VALUE, dirPath);
        sApplication.sendBroadcast(intent);
    }

    @Deprecated
    public static void saveDynamicInfo(String bundleName, String path) {
        Intent intent = new Intent();
        intent.setAction("android.intent.action.FreelineReceiver");
        intent.putExtra(FreelineReceiver.UUID, getUuid());
        intent.putExtra(FreelineReceiver.ACTION_KEY, FreelineReceiver.ACTION_SAVE_DYNAMIC_INFO);
        intent.putExtra(FreelineReceiver.SP_KEY, bundleName);
        intent.putExtra(FreelineReceiver.SP_VALUE, path);
        sApplication.sendBroadcast(intent);
    }

}