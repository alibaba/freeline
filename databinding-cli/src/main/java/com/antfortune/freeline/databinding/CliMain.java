package com.antfortune.freeline.databinding;

import org.apache.commons.cli.*;

import java.io.File;
import java.util.Arrays;
import java.util.List;

/**
 * Created by huangyong on 16/10/21.
 */
public class CliMain {

    private static final String ARG_PACKAGE = "package";
    private static final String ARG_INPUT = "input";
    private static final String ARG_OUTPUT = "output";
    private static final String ARG_CLASSES = "classes";
    private static final String ARG_LAYOUT_INFO = "layout-info";
    private static final String ARG_LIBRARY = "library";
    private static final String ARG_VERSION = "version";
    private static final String ARG_SDK = "sdk";
    private static final String ARG_CHANGED = "changed-files";

    public static void main(String[] args) {
        Options options = new Options();
        options.addOption(Option.builder("p").desc("original package name")
                .longOpt(ARG_PACKAGE)
                .hasArg()
                .required()
                .build());
        options.addOption(Option.builder("i").desc("input resources directory path")
                .longOpt(ARG_INPUT)
                .hasArg()
                .required()
                .build());
        options.addOption(Option.builder("o").desc("output resources directory path")
                .longOpt(ARG_OUTPUT)
                .hasArg()
                .required()
                .build());
        options.addOption(Option.builder("d").desc("output layout info directory path")
                .longOpt(ARG_LAYOUT_INFO)
                .hasArg()
                .required()
                .build());
        options.addOption(Option.builder("c").desc("classes output directory path")
                .longOpt(ARG_CLASSES)
                .hasArg()
                .required()
                .build());
        options.addOption(Option.builder("l").desc("whether the module is library or not")
                .longOpt(ARG_LIBRARY)
                .hasArg()
                .required()
                .build());
        options.addOption(Option.builder("v").desc("minSdkVersion")
                .longOpt(ARG_VERSION)
                .hasArg()
                .required()
                .build());
        options.addOption(Option.builder("s").desc("Android sdk directory path")
                .longOpt(ARG_SDK)
                .hasArg()
                .required()
                .build());
        options.addOption(Option.builder("a").desc("changed file list, separated by `:`")
                .longOpt(ARG_CHANGED)
                .hasArg()
                .build());

        CommandLine commandLine;
        String packageName;
        String inputDirPath;
        String outputDirPath;
        String layoutInfoDirPath;
        String classOutputDirPath;
        String sdkDirectoryPath;
        String changedFilesList;
        boolean isLibrary;
        int minSdkVersion;

        try {
            commandLine = new DefaultParser().parse(options, args);

            packageName = commandLine.getOptionValue(ARG_PACKAGE);
            inputDirPath = commandLine.getOptionValue(ARG_INPUT);
            outputDirPath = commandLine.getOptionValue(ARG_OUTPUT);
            layoutInfoDirPath = commandLine.getOptionValue(ARG_LAYOUT_INFO);
            classOutputDirPath = commandLine.getOptionValue(ARG_CLASSES);
            sdkDirectoryPath = commandLine.getOptionValue(ARG_SDK);
            changedFilesList = commandLine.getOptionValue(ARG_CHANGED);
            isLibrary = Boolean.parseBoolean(commandLine.getOptionValue(ARG_LIBRARY));
            minSdkVersion = Integer.parseInt(commandLine.getOptionValue(ARG_VERSION));
        } catch (ParseException e) {
            System.err.println("Parse arguments error: " + e.getMessage() + "\n");
            printHelpMessage(options);
            return;
        }

        DataBindingHelper.init(packageName, minSdkVersion, classOutputDirPath, isLibrary);

        try {
            File inputDirectory = new File(inputDirPath);
            File outputDirectory = new File(outputDirPath);
            File layoutInfoDirectory = new File(layoutInfoDirPath);
            File sdkDirectory = new File(sdkDirectoryPath);
            boolean isIncremental = changedFilesList != null && !"".equals(changedFilesList);
            List<String> filesList = null;
            if (isIncremental) {
                filesList = Arrays.asList(changedFilesList.split(File.pathSeparator));
            }

            ProcessLayouts.run(isIncremental, inputDirectory, outputDirectory, layoutInfoDirectory, filesList);
            ExportDataBindingInfo.run(sdkDirectory, layoutInfoDirectory);
        } catch (Exception e) {
            System.err.println("process databinding error: " + e.getMessage() + "\n");
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void printHelpMessage(Options optionsContainer) {
        String header = "\nUse databinding-cli to process the layout files without gradle.\n";
        String footer = "\nFreeline DataBidning CLI 1.0.2, Please report issues at https://github.com/alibaba/freeline/issues\n";
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("java -jar databinding-cli.jar", header, optionsContainer, footer, true);
    }

}
