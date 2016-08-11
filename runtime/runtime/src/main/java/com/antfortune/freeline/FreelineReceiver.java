package com.antfortune.freeline;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.util.Log;

import com.antfortune.freeline.util.ActivityManager;

import java.util.HashMap;

/**
 * Created by huangyong on 16/7/31.
 */
public class FreelineReceiver extends BroadcastReceiver {

    private static final String TAG = "Freeline.Receiver";

    public static final String ACTION_KEY = "action";

    public static final String SP_KEY = "sp_key";
    public static final String SP_VALUE = "sp_value";

    public static final int ACTION_UPDATE_ACTIVITY = 1;
    public static final int ACTION_RESTART_APPLICATION = 2;
    public static final int ACTION_SAVE_DYNAMIC_INFO = 3;

    @Override
    public void onReceive(Context context, Intent intent) {
        int type = intent.getIntExtra(ACTION_KEY, -1);
        Log.i(TAG, "receive action type: " + type);
        if (type == ACTION_UPDATE_ACTIVITY) {
            ActivityManager.restartActivity();
        } else if (type == ACTION_RESTART_APPLICATION) {
            ActivityManager.restart(FreelineCore.getApplication(), true);
        } else if (type == ACTION_SAVE_DYNAMIC_INFO) {
            String key = intent.getStringExtra(SP_KEY);
            String value = intent.getStringExtra(SP_VALUE);
            if (!TextUtils.isEmpty(key) && !TextUtils.isEmpty(value)) {
                HashMap<String, String> res = new HashMap<String, String>();
                Log.d(TAG, "destPath :" + value);
                res.put(key, value);
                FreelineCore.saveDynamicResInfo(res);
            }
        }
    }

}
