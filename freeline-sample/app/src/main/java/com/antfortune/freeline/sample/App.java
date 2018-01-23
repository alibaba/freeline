package com.antfortune.freeline.sample;

import android.app.Application;

import com.antfortune.freeline.FreelineCore;

/**
 * Created by huangyong on 16/8/5.
 */
public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        FreelineCore.init(this);
    }
}
