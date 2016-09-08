# Freeline

![Freeline](http://ww4.sinaimg.cn/large/006tNc79gw1f6ooza8pkuj30h804gjrk.jpg)

![Release Version](https://img.shields.io/badge/release-0.6.0-red.svg) ![BSD License](https://img.shields.io/badge/license-BSD%20-blue.svg) ![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)

*Freeline* is a fast build and deployment tool for Android. Caching reusable class files and resource indices, it enables incrementally building Android apps, and optionally deploying the update to your device with hot swap.

Developed and used by **Ant Fortune（ [about us](https://www.antfortune.com/ "about us") ） Android Team**, Freeline has been significantly saving time in daily work. Inspiring by **Buck** and **Instant Run** but faster than ever, Freeline can finish an incremental build in just a few seconds. No more recompile and reinstall again and again before seeing your modifications, Freeline brings life-changing development experience for Android.

## Freeline Insights
Freeline splits the build task into several small tasks that run concurrently. It makes full use of the compiled cache files to implement a real incremental build and uses some components of Buck such as dx and DexMerger to speed up the build tasks. Freeline runs a socket server in a separate process on device side, which connects with build tool on developer's machine, so that deployment can still take effects even if the main process crashes.

Freeline uses multi-dex solution for incremental dex hot swapping. A deeply optimized version of **aapt** tool (**FreelineAapt**) is made to generate incremental resource pack, which is several times faster than the original aapt tool and the resouce pack can be as small as 1kb. MonkeyPatcher from Instant Run is utilized to make hot resource replacement.

Freeline support dynamic swap native so ,you need not full build even if native so change. 

Freeline will automatically switch between full build and incremental build.

Freeline is also a great basis for over-the-air hotpatching. Deliverying Freeline's incremental output, which can be packed into a zip file and usually less than 100 kb, it is able to take effect to fix crashes or other problems and replace resoucres dynamically. Statistical data over large amount cases show that it is effective for 99% of users. Please note that the OTA patch delivery system is out of scope of this project.

FreelineAapt will open source codes later. See wiki to know more about Freeline, only Chinese available now.

[中文原理说明](https://yq.aliyun.com/articles/59122?spm=5176.8091938.0.0.1Bw3mU)

## Features
- Speed up standard android gradle projects with multiple modules;
- Concurrent tasks that incrementally build project;
- Hot deploy even if the main process crashes;
- Build incremental dex and resource pack;
- Caching resource.arsc support;
- Running on Windows, Linux and Mac.
- native so hot swap support;

See the following table for changes support.

|| Java | drawable, layout, etc. | res/values | native so|
|:-----:|:----:|:----:|:----:|:----:|
| add    | √    | √    |√ |   √   |     
| change    | √    |  √   |√ |   √   | 
| remove   | √    |   √  |x|   -   | 


Freeline has been tested with API versions 17, 19, 22, 23 on the android emulators, a Nexus 6p running marshmallow and a smartisan running Kitkat. Incremental resource patch would be much faster if the android device is running Lolipop or above.

## Download
Configure your project-level build.gradle to include freeline plugin:

````Gradle
buildscript {
    repositories {
        jcenter()
    }
    dependencies {
        classpath 'com.antfortune.freeline:gradle:0.6.1'
    }
}
````
Then, apply the freeline plugin in your android application module's build.gradle and add the freeline dependencies:

````Gradle
apply plugin: 'com.antfortune.freeline'

android {
    ...
    freeline {
        hack true
    }
}

dependencies {
  compile 'com.antfortune.freeline:runtime:0.6.1'
}
````
Finally, apply freeline in your application class.

````Java
public class App extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        FreelineCore.init(this);
    }
}
````

If you have a complex project structure, you may need to use freeline DSL to customize your build process. For more details about freeline DSL, see [Freeline DSL References](https://github.com/alibaba/freeline/wiki/Freeline-DSL-References).

## Installation
- Windows: `gradlew.bat initFreeline`
- Linux/Mac: `./gradlew initFreeline`

`gradle initFreeline -Pmirror` or proxy might be useful, if you have trouble in downloading freeline dependency.

Note that, you should apply the freeline plugin dependency before you execute these commands. 

## Update
After changing the version of freeline in build.gradle files, you should run `./gradlew initFreeline` or `gradlew.bat initFreeline` to download the latest freeline dependency.

## Usage

You should add **`JAVA_HOME`** variable in your system environment before executing freeline commands and install Python 2.7+  .


On the root dir of your project :

- `python freeline.py`

    freeline will automatically perform a full build on your project as follows:
    - manifest.xml modified
    - build.gradle modified
    - connect to other mobile phones after any incremental build
    
    and an incremental build on other change case (java or res modified ),you don't need to pay attention to what you just modified.

- `python freeline.py -f`

    when if you need a full build in other case , your can force it through adding "-f"


## Sample Usage
````
git clone git@github.com:alibaba/freeline.git
cd freeline/sample
./gradlew initFreeline
python freeline.py
````

## TODO
- Compatibility Improvement
- Android Studio Plugin
- Annotation Support
- Multiple Devices Connection Support

## Limitations
- Sync incremental resource pack to the device first time may be a bit slow
- Annotation is not supported currently, such as ButterKnife etc.
- Removing res/values is not supported, which may cause aapt exception

## Contributing
We are always very happy to have contributions, whether for trivial cleanups, big new features or other material rewards.

## Troubleshooting
See [wiki](https://github.com/alibaba/freeline/wiki/%E5%B8%B8%E8%A7%81%E9%97%AE%E9%A2%98).

## Thanks
- [Instant Run](https://developer.android.com/studio/run/index.html#instant-run)
- [Buck](https://github.com/facebook/buck)
- [LayoutCast](https://github.com/mmin18/LayoutCast)

## License
BSD License







