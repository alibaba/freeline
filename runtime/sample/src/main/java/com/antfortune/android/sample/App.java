package com.antfortune.android.sample;

import android.app.Application;

import com.antfortune.freeline.FreelineCore;

/**
 * Created by huangyong on 16/7/31.
 */
public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        FreelineCore.init(this);
    }
}
