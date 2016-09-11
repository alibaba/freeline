package com.antfortune.freeline.plugin.configuration;

import com.antfortune.freeline.plugin.utils.SystemUtil;
import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Freeline Configuration Factory
 *
 * @author act262@gmail.com
 */
class FreeConfigurationFactory extends ConfigurationFactory {

    FreeConfigurationFactory(@NotNull ConfigurationType type) {
        super(type);
    }

    @NotNull
    @Override
    public RunConfiguration createTemplateConfiguration(@NotNull Project project) {
        return new FreeRunConfiguration(project, this, "FreelineFactory");
    }

    @Override
    public boolean isApplicable(@NotNull Project project) {
        // 只在初始化了Freeline相关代码才可以通过这里运行
        return SystemUtil.hasInitFreeline(project);
    }

    @Override
    public boolean isConfigurationSingletonByDefault() {
        return true;
    }
}
