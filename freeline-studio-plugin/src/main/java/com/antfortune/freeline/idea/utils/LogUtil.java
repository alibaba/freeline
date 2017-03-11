package com.antfortune.freeline.idea.utils;

import com.antfortune.freeline.idea.models.Constant;

/**
 * Created by pengwei on 2016/11/2.
 */
public class LogUtil {

    public static void d(String info) {
        if (!Constant.DEBUG_MODE) {
            return;
        }
        System.out.println(info);
    }

    public static void d(String info, Object...args) {
        if (!Constant.DEBUG_MODE) {
            return;
        }
        System.out.println(String.format(info, args));
    }

    public static void d(Object info) {
        if (!Constant.DEBUG_MODE) {
            return;
        }
        System.out.println(info);
    }

}
