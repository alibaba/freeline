package com.antfortune.freeline;

import android.app.Application;
import android.util.Log;

/**
 * Created by huangyong on 16/9/1.
 */
public class FreelineCore {

    private static final String TAG = "Freeline";

    public static void init(Application app, Application realApplication) {
        markFreeline();
    }

    public static void init(Application app) {
        markFreeline();
    }

    private static void markFreeline() {
        Log.i(TAG, "Freeline with runtime-no-op loaded!");
    }

}