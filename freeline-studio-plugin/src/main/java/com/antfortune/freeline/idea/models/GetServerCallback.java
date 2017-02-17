package com.antfortune.freeline.idea.models;

/**
 * Created by pengwei on 16/9/14.
 */
public interface GetServerCallback {
    void onSuccess(GradleDependencyEntity entity);
    void onFailure(String errMsg);
}
