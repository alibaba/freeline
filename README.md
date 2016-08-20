# Freeline

![Freeline](http://ww4.sinaimg.cn/large/006tNc79gw1f6ooza8pkuj30h804gjrk.jpg)

![Release Version](https://img.shields.io/badge/release-v0.5.0-red.svg) ![BSD License](https://img.shields.io/badge/license-BSD%20-blue.svg) ![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)

*Freeline* is a fast build and deployment tool for Android. Caching reusable class files and resource indices, it enables incrementally building Android apps, and optionally deploying the update to your device with hot swap.

Developed and used by **Ant Fortune Android Team**, Freeline has been significantly saving time in daily work. Inspiring by **Buck** and **Instant Run** but faster than ever, Freeline can finish an incremental build in just a few seconds. No more recompile and reinstall again and again before seeing your modifications, Freeline brings life-changing development experience for Android.

## Freeline Insights
Freeline splits the build task into several small tasks that run concurrently. It makes full use of the compiled cache files to implement a real incremental build and uses some components of Buck such as dx and DexMerger to speed up the build tasks. Freeline runs a socket server in a separate process on device side, which connects with build tool on developer's machine, so that deployment can still take effects even if the main process crashes.

Freeline uses multi-dex solution for incremental dex hot swapping. A deeply optimized version of **aapt** tool (**FreelineAapt**) is made to generate incremental resource pack, which can be as small as 1 kb. MonkeyPatcher from Instant Run is utilized to make hot resource replacement.

Freeline will automatically switch between full build and incremental build.

Freeline is also a great basis for over-the-air hotpatching. Deliverying Freeline's incremental output, which can be packed into a zip file and usually less than 100 kb, it is able to take effect to fix crashes or other problems and replace resoucres dynamically. Statistical data over large amount cases show that it is effective for 99% of users. Please note that the OTA patch delivery system is out of scope of this project.

FreelineAapt will open source codes later. See wiki to know more about Freeline, only Chinese available now.

[中文详细说明](https://github.com/alibaba/freeline/wiki)

## Features
- Speed up standard android gradle projects with multiple modules;
- Concurrent tasks that incrementally build project;
- Hot deploy even if the main process crashes;
- Build incremental dex and resource pack;
- Caching resource.arsc support;
- Running on Windows, Linux and Mac.

See the following table for changes support.

|| Java | drawable, layout, etc. | res/values |
|:-----:|:----:|:----:|:----:|
| add    | √    | √    |√ |
| change    | √    |  √   |√ |
| remove   | √    |   √  |x|

Incremental resource patch is not supported for Android api level lower than 20. Freeline will automatically delivery full resource pack to the device, which may be a bit slower than incremental resource pack.

Freeline has been tested with API versions 17, 19, 22, 23 on the android emulators, a Nexus 6p running marshmallow and a smartisan running Kitkat. Incremental resource patch would be much faster if the android device is running Lolipop or above.

## Download
Configure your project-level build.gradle to include freeline plugin:

````Gradle
buildscript {
    repositories {
        jcenter()
    }
    dependencies {
        classpath 'com.antfortune.freeline:gradle:0.5.1'
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
  compile 'com.antfortune.freeline:runtime:0.5.0'
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

## Usage
On the root dir of your project :

- increment build：`python freeline.py`
- 
    If your project has manifest.xml, build.gradle or libs modified, freeline will automatically rebuild your project, freeline will perform an incremental build on other change case,you don't need to pay attention to what you just modified!

- force full build：`python freeline.py -f`
- 
    when if freeline are not automatically change to full build ,you can force it through "-f"



## Sample Usage
````
git clone git@github.com:alibaba/freeline.git
cd freeline/sample
./gradlew initFreeline
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







