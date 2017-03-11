package com.antfortune.freeline.idea.models;

import com.intellij.openapi.components.*;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.Nullable;

/**
 * Created by huangyong on 17/2/14.
 */
@State(
        name = "FreelineConfigurationStorage",
        storages = @Storage(file = "freeline-configuration.xml", roamingType = RoamingType.DISABLED)
)
public class FreelineConfiguration implements PersistentStateComponent<FreelineConfiguration> {

    public boolean DISABLE_CRASH_REPORTER = false;

    @Nullable
    @Override
    public FreelineConfiguration getState() {
        return this;
    }

    @Override
    public void loadState(FreelineConfiguration freelineConfiguration) {
        XmlSerializerUtil.copyBean(freelineConfiguration, this);
    }

    public static FreelineConfiguration getInstance() {
        return ServiceManager.getService(FreelineConfiguration.class);
    }
}
