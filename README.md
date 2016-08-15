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
        classpath 'com.antfortune.freeline:gradle:0.5.0'
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

If you have a complex project structure, you may need to use freeline DSL to customize your build process. For more details about freeline DSL, see [Freeline DSL References](#freeline-dsl-references).

## Installation
- Windows: `gradlew.bat initFreeline`
- Linux/Mac: `./gradlew initFreeline`

`gradle initFreeline -Pmirror` or proxy might be useful, if you have trouble in downloading freeline dependency.

Note that, you should apply the freeline plugin dependency before you execute these commands. 

## Usage
On the root dir of your project :

- Daily build：`python freeline.py`
- Rebuild project：`python freeline.py -f`

If your project has manifest.xml, build.gradle or libs modified, freeline will automatically rebuild your project, you don't need to pay attention to what you just modified!

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

## Freeline DSL References
### Properties
| Property | Description |
|:---:|:---:|
|hack| The global switch for freeline hack process. |
|buildScript| The build script for your project. |
|productFlavor| The flavor your debuggable project use. |
|apkPath| The path of the build output apk file which would be installed to your device. |
|extraResourceDependencyPaths| The extra resources dependency paths which would be added to the aapt options. | 
|excludeResourceDependencyPaths| The resources dependency paths which would be removed from the aapt options. |
|excludeHackClasses| The classes which would skip the freeline class-inject process. |

### Property details
#### boolean hack
The global switch for freeline hack process, the default value is false.

#### String buildScript
The build script for your project, the default value is `gradle assemble{ProductFlavor}Debug`.

#### String productFlavor
The flavor your debuggable project use, the default is empty.

#### apkPath
The path of the build output apk file which would be installed to your device, the default value is `{main_module_dir}/build/outputs/apk/{main_module_name}-debug.apk`.

#### extraResourceDependencyPaths
The extra resources dependency paths which would be added to the aapt options, the default value is empty.

#### excludeResourceDependencyPaths
The resources dependency paths which would be removed from the aapt options, the default value is empty.

#### excludeHackClasses
The classes which would skip the freeline class-inject process, the default value is the class which has a parent class 'android/app/Application'.

## Troubleshooting
Note: Only Chinese Version Available Now

- **与Genymotion自带的adb发生冲突**

````
$ adb devices
adb server is out of date.  killing...
cannot bind 'tcp:5037'
ADB server didn't ACK
*failed to start daemon *
````
问题出现的原因是genymotion自带了adb工具，会造成冲突。解决的方式是将Genymotion所使用的adb改为androidsdk自带的adb。具体可以参考：[StackOverflow Link](http://stackoverflow.com/questions/26630398/how-to-use-adb-with-genymotion-on-mac)

- **每次增量编译的时候，不停地提示`sync value error`**

需要检查以下几个问题：

1. 是否注册了Application类
2. 是否在Application类中初始化了Freeline
3. 是否在同一台设备上安装了多个依赖了freeline的应用

需要在minifest注册正确的Application类，并在其中初始化freeline，以及在开发使用的设备上只安装一个依赖freeline的应用。

- **NoConfigFoundException：`/path/ not found, please execute gradlew checkBeforeCleanBuild first.`**
    
执行`./gradlew checkBeforeCleanBuild`或者`gradlew.bat checkBeforeCleanBuild`
    
- **Java增量编译无效**

需要检查一下所修改的类是否在Application类中被引用。部分机型如小米，在Application中被import，而没有被调用，仍然会预加载dex进来。导致其dex在freeline之前被初始化引用，造成无法增量生效的情况。解决方案是去掉无用import，将`FreelineCore.init(this);`放在Application类中的`onCreate()`函数的最前面调用。

- **Exception: `manifest merger failed: uses-sdk:minSdkVersion can not be smaller than 14 declared in library[com.antfortune.freeline:runtime:x.x.x]`**

工程的minSdkVersion比freeline:runtime来得低导致的，解决方案如下：

````xml
<uses-sdk
        android:minSdkVersion="9"
        android:targetSdkVersion="21"
        tools:overrideLibrary="com.antfortune.freeline"/>
````

## Thanks
- [Instant Run](https://developer.android.com/studio/run/index.html#instant-run)
- [Buck](https://github.com/facebook/buck)
- [LayoutCast](https://github.com/mmin18/LayoutCast)

## License
BSD License







