package com.antfortune.freeline.util;

import android.os.Build;
import android.util.Log;

import java.lang.reflect.Array;
import java.lang.reflect.Field;

import dalvik.system.DexClassLoader;
import dalvik.system.PathClassLoader;

/**
 * Created by huangyong on 16/9/2.
 */

public class NativeUtils {

    private static final String TAG = "Freeline.hackNative";

    public static void injectHackNativeLib(String libraryDir, PathClassLoader classLoader) {
        try {
            Log.i(TAG, "native lib inject process start...");

            String refFieldName = "nativeLibraryPathElements";
            if (Build.VERSION.SDK_INT < 23){
                refFieldName = "nativeLibraryDirectories";
            }

            Object pathListObject = ReflectUtil.getField(classLoader, "pathList");
            Object nativeLibraryPathElementsObject = ReflectUtil.getField(pathListObject, refFieldName);

            Field nativeLibraryPathElementsFiled = ReflectUtil.fieldGetOrg(pathListObject, refFieldName);


            DexClassLoader dumbDexClassLoader = new DexClassLoader("", libraryDir, libraryDir, classLoader.getParent());
            Object dynamicNativeLibraryPathElements = ReflectUtil.getField(ReflectUtil.getField(dumbDexClassLoader, "pathList"), refFieldName);
            Object dynamicNativeLibraryPathElement = Array.get(dynamicNativeLibraryPathElements, 0);

            int lengthOfNewNativeLibraryPathElements = Array.getLength(nativeLibraryPathElementsObject) + 1;
            Object newNativeLibraryPathElements = Array.newInstance(nativeLibraryPathElementsFiled.getType().getComponentType(), lengthOfNewNativeLibraryPathElements);
            Array.set(newNativeLibraryPathElements, 0, dynamicNativeLibraryPathElement);

            for (int i = 1; i < lengthOfNewNativeLibraryPathElements; i++) {
                Object object = Array.get(nativeLibraryPathElementsObject, i - 1);
                Array.set(newNativeLibraryPathElements, i, object);
            }

            ReflectUtil.setField(pathListObject, refFieldName, newNativeLibraryPathElements);
            Log.i(TAG, "inject native lib success " + newNativeLibraryPathElements);
        } catch (Exception ex) {
            Log.e(TAG, "inject native lib failed", ex);
        }
    }

}
