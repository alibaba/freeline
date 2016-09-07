package com.antfortune.freeline.plugin;

import com.intellij.openapi.components.ApplicationComponent;
import org.jetbrains.annotations.NotNull;

/**
 * Created by zcx on 16-9-6.
 */
public class SettingComponent implements ApplicationComponent {
    public SettingComponent() {
    }

    @Override
    public void initComponent() {
        // TODO: insert component initialization logic here
    }

    @Override
    public void disposeComponent() {
        // TODO: insert component disposal logic here
    }

    @Override
    @NotNull
    public String getComponentName() {
        return "SettingComponent";
    }

    public void show() {
        System.out.println("SettingComponent.show");
    }
}
