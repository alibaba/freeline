package com.antfortune.freeline.idea.models;

import com.android.tools.idea.gradle.dsl.model.dependencies.ArtifactDependencyModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

/**
 * Created by pengwei on 16/9/14.
 * 兼容android studio不同版本
 * 2.1.2之前版本name()方法返回string， 之后返回GradleNullableValue
 */
public class ArtifactDependencyModelWrapper {

    private ArtifactDependencyModel model;

    public ArtifactDependencyModelWrapper(ArtifactDependencyModel model) {
        this.model = model;
    }

    @NotNull
    public String name() {
        Object value = getArtifactDependencyModelMethod("name");
        if (value != null) {
            return getGradleNotNullValue(value);
        }
        return "";
    }

    @Nullable
    public String group() {
        Object value = getArtifactDependencyModelMethod("group");
        if (value != null) {
            return getGradleNullableValue(value);
        }
        return "";
    }

    @Nullable
    public String version() {
        Object value = getArtifactDependencyModelMethod("version");
        if (value != null) {
            return getGradleNullableValue(value);
        }
        return "";
    }

    public String configurationName() {
        return model.configurationName();
    }

    /**
     * 反射调用ArtifactDependencyModel方法
     *
     * @param methodName
     * @return
     */
    private Object getArtifactDependencyModelMethod(String methodName) {
        try {
            Class<?> ArtifactDependencyModelClass = Class.forName("com.android.tools.idea.gradle.dsl.model.dependencies.ArtifactDependencyModel");
            Method groupMethod = ArtifactDependencyModelClass.getMethod(methodName);
            Object value = groupMethod.invoke(model);
            return value;
        } catch (Exception e) {
            return null;
        }
    }

    private String getGradleNullableValue(Object value) {
        return getGradleValue("GradleNullableValue", value);
    }

    private String getGradleNotNullValue(Object value) {
        return getGradleValue("GradleNotNullValue", value);
    }

    private String getGradleValue(String className, Object value) {
        if (value instanceof String) {
            return (String) value;
        }
        try {
            Class<?> classType = Class.forName("com.android.tools.idea.gradle.dsl.model.values." + className);
            if (value.getClass().equals(classType)) {
                Method valueMethod = classType.getMethod("value");
                value = valueMethod.invoke(value);
                if (value instanceof String) {
                    return (String) value;
                }
            }
        } catch (Exception e) {
        }
        return "";
    }


}
