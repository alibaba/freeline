package com.antfortune.freeline.plugin.configuration;

import com.antfortune.freeline.plugin.utils.SystemUtil;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;

/**
 * Freeline run configuration implementation
 *
 * @author act262@gmail.com
 */
class FreeRunConfiguration extends ModuleBasedConfiguration<JavaRunConfigurationModule> {

    FreeRunConfiguration(@NotNull Project project, @NotNull ConfigurationFactory factory, String name) {
        super(name, new JavaRunConfigurationModule(project, false), factory);
    }

    @NotNull
    @Override
    public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
        // setting editor ui
        return new FreeSettingEditor();
    }

    @Override
    public void checkConfiguration() throws RuntimeConfigurationException {
        if (!SystemUtil.hasInitFreeline(getProject())) {
            throw new RuntimeConfigurationException("Not yet initialize freeline code", "Warning");
        }
    }

    @Nullable
    @Override
    public RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment executionEnvironment) throws ExecutionException {
        return new FreeRunState(executionEnvironment);
    }

    @Override
    public Collection<Module> getValidModules() {
        Module[] modules = ModuleManager.getInstance(getProject()).getModules();
        return Arrays.asList(modules);
    }

    /**
     * RunState
     */
    private class FreeRunState extends CommandLineState {

        FreeRunState(ExecutionEnvironment environment) {
            super(environment);
        }

        @NotNull
        @Override
        protected ProcessHandler startProcess() throws ExecutionException {
            // here just run one command: python freeline.py
            GeneralCommandLine commandLine = new GeneralCommandLine();
            ExecutionEnvironment environment = getEnvironment();
            commandLine.setWorkDirectory(environment.getProject().getBasePath());
            commandLine.setExePath("python");
            commandLine.addParameters("freeline.py");
            return new OSProcessHandler(commandLine);
        }

    }
}
