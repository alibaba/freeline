# Freeline

![Freeline](http://ww4.sinaimg.cn/large/006tNc79gw1f6ooza8pkuj30h804gjrk.jpg)

[![Release Version](https://img.shields.io/badge/release-0.8.8-red.svg)](https://github.com/alibaba/freeline/releases) [![BSD3 License](https://img.shields.io/badge/license-BSD3-blue.svg)](https://github.com/alibaba/freeline/blob/master/LICENSE) [![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)](https://github.com/alibaba/freeline/pulls)

*Freeline* is a super fast build tool for Android and an alternative to Instant Run. Caching reusable class files and resource indices, it enables incremental building Android apps, and optionally deploying the updates to your device by hot swap.

See [Freeline official website](https://www.freelinebuild.com) for more information.

Developed and used by **Ant Fortune ([about us](https://www.antfortune.com/ "about us")) Android Team**, Freeline has been significantly saving time in daily work. Inspiring by **Buck** and **Instant Run** but faster than ever, Freeline can make an incremental build in just a few seconds. No more recompile and reinstall again and again before seeing your modifications, Freeline brings life-changing development experience for Android.

[中文说明](README-zh.md)

## Freeline Insights
Freeline splits the build task into several small tasks that run concurrently. It makes full use of the compiled cache files to implement a real incremental build and uses some components of Buck such as dx and DexMerger to speed up the build tasks. Freeline runs a socket server in a separate process on device side, which connects with build tool on developer's machine, so that deployment can still take effects even if the main process crashes.

Freeline uses multi-dex solution for incremental dex hot swapping. A deeply optimized version of **aapt** tool (**FreelineAapt**) is made to generate incremental resource pack, which is several times faster than the original aapt tool and the resouce pack can be as small as 1kb. MonkeyPatcher from Instant Run is utilized to make hot resource replacement. Freeline support dynamic swap native .so library, you don't need to re-build your project after native .so files changed. 

Freeline will automatically switch between full build and incremental build.

Freeline is also a great basis for over-the-air hotpatching. Deliverying Freeline's incremental output, which can be packed into a zip file and usually less than 100 kb, it is able to take effect to fix crashes or other problems and replace resoucres dynamically. Statistical data over large amount cases show that it is effective for 99% of users. Please note that the OTA patch delivery system is out of scope of this project.

FreelineAapt will open source codes later. See wiki to know more about Freeline, only Chinese available now.

[中文原理说明](https://yq.aliyun.com/articles/59122?spm=5176.8091938.0.0.1Bw3mU)

## Features
- Speed up standard android gradle projects with multiple modules
- Concurrent tasks that incrementally build project
- Hot deploy even if the main process crashes
- Build incremental dex and resource pack
- Caching resource.arsc support
- Running on Windows, Linux and Mac
- Native so hot swap support
- Annotation support
- Retrolambda support
- DataBinding support

See the following table for changes support.

|| Java | drawable, layout, etc. | res/values | native so|
|:-----:|:----:|:----:|:----:|:----:|
| add | √ | √ | √ | √ |     
| change | √ | √ | √ | √ | 
| remove | √ | √ | x | - | 


Freeline has been tested with API versions 17, 19, 22, 23 on the android emulators, a Nexus 6p running marshmallow and a smartisan running Kitkat. Incremental resource patch would be much faster if the android device is running Lolipop or above.

## Download
Configure your project-level build.gradle to include freeline plugin:

````Gradle
buildscript {
    repositories {
        jcenter()
    }
    dependencies {
        classpath 'com.antfortune.freeline:gradle:0.8.8'
    }
}
````
Then, apply the freeline plugin in your android application module's build.gradle:

````Gradle
apply plugin: 'com.antfortune.freeline'

android {
    ...
}
````

Finally, execute the gradle task `initFreeline` to download freeline dependencies.

- Windows[CMD]: gradlew initFreeline
- Linux/Mac: ./gradlew initFreeline

For Chinese users, if you got stuck in the download process, you can execute `gradlew initFreeline -Pmirror` to speed up the progress.

You can also use `-PfreelineVersion={your-specific-version}` parameter to download the specific version of freeline's python and bin dependencies.

If you have a complex project structure, you may need to use freeline DSL to customize your build process. For more details about freeline DSL, see [Freeline DSL References](https://github.com/alibaba/freeline/wiki/Freeline-DSL-References).

## Usage
You can install freeline plugin in Android Studio for quick usage.

How to install? In Android Studio, go to:

- MacOS
> Android Studio → Preferences... → Plugins → Browse repositories...

- Windows and Linux
> File → Settings... → Plugins → Browse repositories...

and search for `freeline`.

![](http://ww4.sinaimg.cn/large/65e4f1e6gw1f82eknaeudj20tk01omxe.jpg)

Just use the `Run Freeline` button and enjoy it.

The plugin will show you the latest update of Freeline, you can use this plugin to update Freeline.

Many thanks to [@pengwei1024](https://github.com/pengwei1024) and [@act262](https://github.com/act262) for creating such an awesome plugin.

Besides, you can also execute python script in the command line in the root dir of your project. See [wiki](https://github.com/alibaba/freeline/wiki/Freeline-CLI-Usage) for more details.


## Sample Usage
````
git clone git@github.com:alibaba/freeline.git
cd freeline/sample
./gradlew initFreeline
python freeline.py
````

## TODO
- Compatibility Improvement
- Multiple Devices Connection Support

## Limitations
- Sync incremental resource pack to the device first time may be a bit slow
- Removing res/values is not supported, which may cause aapt exception
- Incrementally build abstract class is not support
- Not support Jack compile
- Not support Kotlin/Groovy/Scala

## Contributing
We are always very happy to have contributions, whether for trivial cleanups, big new features or other material rewards.

## Troubleshooting
See [wiki](https://github.com/alibaba/freeline/wiki/%E5%B8%B8%E8%A7%81%E9%97%AE%E9%A2%98).

## Thanks
- [Instant Run](https://developer.android.com/studio/run/index.html#instant-run)
- [Buck](https://github.com/facebook/buck)
- [LayoutCast](https://github.com/mmin18/LayoutCast)

## License
BSD3 License







