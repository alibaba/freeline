package com.antfortune.freeline;

import java.util.HashMap;

/**
 * Created by xianying on 16/3/16.
 */
public interface IDynamic {
    /***
     * packagid + newResPath
     * @param dynamicRes
     */
    boolean applyDynamicRes(HashMap<String, String> dynamicRes);

    String getOriginResPath(String packageId);

    void clearResourcesCache();
}
