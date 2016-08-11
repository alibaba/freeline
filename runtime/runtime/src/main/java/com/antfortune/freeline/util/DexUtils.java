package com.antfortune.freeline.util;

import android.os.Build.VERSION;
import android.util.Log;

import dalvik.system.DexClassLoader;
import dalvik.system.DexFile;
import dalvik.system.PathClassLoader;

import java.io.File;
import java.lang.reflect.Array;
import java.lang.reflect.Field;

public class DexUtils {

    private static final String TAG = "Freeline.hackDex";

    public static boolean inject(PathClassLoader classLoader, File dex, File opt) {
        Log.i(TAG, dex.getAbsolutePath() + " dex length: " + dex.length());
        Log.i(TAG, opt.getAbsolutePath() + " opt length: " + opt.length());

        DexFile[] dexFiles = null;
        Field pathListField = null;
        Field fDexElements = null;
        Object dstObject = null;

        try {
            Object newDexElements;
            int dexLength;
            if (VERSION.SDK_INT >= 14) {
                pathListField = ReflectUtil.fieldGetOrg(classLoader, Class.forName("dalvik.system.BaseDexClassLoader"), "pathList");
                fDexElements = ReflectUtil.fieldGetOrg(pathListField.get(classLoader), "dexElements");
                Object e = fDexElements.get(pathListField.get(classLoader));
                dstObject = e;
                dexFiles = new DexFile[Array.getLength(e)];
                for (int i = 0; i < Array.getLength(e); ++i) {
                    newDexElements = Array.get(e, i);
                    dexFiles[i] = (DexFile) ReflectUtil.fieldGet(newDexElements, "dexFile");
                }
            } else {
                pathListField = ReflectUtil.fieldGetOrg(classLoader, "mDexs");
                dstObject = pathListField.get(classLoader);
                dexFiles = new DexFile[Array.getLength(dstObject)];
                for (dexLength = 0; dexLength < Array.getLength(dstObject); ++dexLength) {
                    dexFiles[dexLength] = (DexFile) Array.get(dstObject, dexLength);
                }
            }
            dexLength = Array.getLength(dstObject) + 1;
            newDexElements = Array.newInstance(fDexElements.getType().getComponentType(), dexLength);

            DexClassLoader dynamicDex = new DexClassLoader(dex.getAbsolutePath(), opt.getAbsolutePath(), null, classLoader.getParent());
            Log.i(TAG, "after opt, dex len:" + dex.length() + "; opt len:" + opt.length());
            Object pathList = pathListField.get(dynamicDex);
            Object dexElements = fDexElements.get(pathList);
            Object firstDexElement = Array.get(dexElements, 0);
            Array.set(newDexElements, 0, firstDexElement);

            for (int i = 0; i < dexLength - 1; ++i) {
                Object element = Array.get(dstObject, i);
                Array.set(newDexElements, i + 1, element);
            }

            if (VERSION.SDK_INT >= 14) {
                fDexElements.set(pathListField.get(classLoader), newDexElements);
            } else {
                pathListField.set(classLoader, newDexElements);
            }
            return true;
        } catch (Exception e) {
            Log.e(TAG, "fail to override classloader " + classLoader + " with " + dex.getAbsolutePath(), e);
            return false;
        }
    }


}
