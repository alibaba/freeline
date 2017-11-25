package com.antfortune.freeline.sample.common;

import android.content.Context;
import android.widget.Toast;

/**
 * Created by huangyong on 16/8/23.
 */
public class CommonTools {

    public static void toast(Context context) {
        Toast.makeText(context, R.string.resources_library_string, Toast.LENGTH_SHORT).show();
    }

}
