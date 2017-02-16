package com.antfortune.freeline.idea.actions;

/**
 * Created by huangyong on 17/2/15.
 */
public interface OnRequestCallback {

    void onSuccess();

    void onFailure(Exception e);

}
