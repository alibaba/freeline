package com.antfortune.freeline.databinding;

import org.apache.commons.cli.*;

import java.io.File;

/**
 * Created by huangyong on 16/10/21.
 */
public class CliMain {

    private static final String ARG_PACKAGE = "package";
    private static final String ARG_INPUT = "input";
    private static final String ARG_OUTPUT = "output";
    private static final String ARG_CLASSES = "classes";
    private static final String ARG_LIBRARY = "library";
    private static final String ARG_VERSION = "version";
    private static final String ARG_SDK = "sdk";

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

        CommandLine commandLine;
        String packageName;
        String inputDirPath;
        String outputDirPath;
        String classOutputDirPath;
        String sdkDirectoryPath;
        boolean isLibrary;
        int minSdkVersion;

        try {
            commandLine = new DefaultParser().parse(options, args);

            packageName = commandLine.getOptionValue(ARG_PACKAGE);
            inputDirPath = commandLine.getOptionValue(ARG_INPUT);
            outputDirPath = commandLine.getOptionValue(ARG_OUTPUT);
            classOutputDirPath = commandLine.getOptionValue(ARG_CLASSES);
            sdkDirectoryPath = commandLine.getOptionValue(ARG_SDK);
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
            File sdkDirectory = new File(sdkDirectoryPath);

            ProcessLayouts.run(inputDirectory, outputDirectory);
            ExportDataBindingInfo.run(sdkDirectory, outputDirectory);
        } catch (Exception e) {
            System.err.println("procosee databinding error: " + e.getMessage() + "\n");
        }
    }

    private static void printHelpMessage(Options optionsContainer) {
        String header = "\nUse databinding-cli to process the layout files without gradle.\n";
        String footer = "\nPlease report issues at https://github.com/alibaba/freeline/issues\n";
        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("java -jar databinding-cli.jar", header, optionsContainer, footer, true);
    }

}
