package com.antfortune.freeline.idea.configuration;

import com.intellij.execution.CantRunException;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.Executor;
import com.intellij.execution.configurations.*;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.ConsoleView;
import com.intellij.openapi.options.SettingsEditor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.antfortune.freeline.idea.utils.FreelineUtil;

/**
 * Freeline run configuration implementation
 *
 * @author act262@gmail.com
 */
class FreeRunConfiguration extends RunConfigurationBase {

    FreeRunConfiguration(@NotNull Project project, @NotNull ConfigurationFactory factory, String name) {
        super(project, factory, name);
    }

    @NotNull
    @Override
    public SettingsEditor<? extends RunConfiguration> getConfigurationEditor() {
        // setting editor ui
        return new FreeSettingEditor();
    }

    @Override
    public void checkConfiguration() throws RuntimeConfigurationException {
        if (!FreelineUtil.hadInitFreeline(getProject())) {
            throw new RuntimeConfigurationException("Not yet initialize freeline code", "Warning");
        }
    }

    @Nullable
    @Override
    public RunProfileState getState(@NotNull Executor executor, @NotNull ExecutionEnvironment executionEnvironment) throws ExecutionException {
        return new FreeRunState(executionEnvironment);
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
            if (!FreelineUtil.hadInitFreeline(getProject())) {
                throw new CantRunException("Not yet initialized freeline code");
            }
            // here just run one command: python freeline.py
            GeneralCommandLine commandLine = new GeneralCommandLine();
            ExecutionEnvironment environment = getEnvironment();
            commandLine.setWorkDirectory(environment.getProject().getBasePath());
            commandLine.setExePath("python");
            commandLine.addParameters("freeline.py");
            return new OSProcessHandler(commandLine);
        }

        @Nullable
        @Override
        protected ConsoleView createConsole(@NotNull Executor executor) throws ExecutionException {
            ConsoleView console = super.createConsole(executor);
            // before run new task,clean log
            if (console != null) {
                console.clear();
            }
            return console;
        }
    }
}
