package com.antfortune.freeline.idea.configuration;

import com.intellij.execution.configurations.ConfigurationFactory;
import com.intellij.execution.configurations.ConfigurationType;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import com.antfortune.freeline.idea.utils.FreelineUtil;

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
        // enabled only had init project
        return FreelineUtil.hadInitFreeline(project);
    }

    @Override
    public boolean isConfigurationSingletonByDefault() {
        return true;
    }
}
