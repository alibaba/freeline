## Freeline 是什么?

Freeline 是一款 Android 平台上的秒级编译方案，能够显著地提高 Android 工程的编译速度。

## 为什么使用 Freeline？
对于一个大型的 Android 工程来说，每次简单地修改几行代码都需要编译上 5 分钟，再等待安装到设备上的 30 秒到 1 分钟，再重新进入到需要调试的 Activity。这个调试环节每天都在重复几十次，无形之中每天都有无数的时间浪费在上面（加班阿QAQ...

Google 推出了官方的 Instant Run，但大家都知道，这个方案有太多的 case 无法覆盖，甚至在一些比较大型的工程上基本上无法使用。

换 Mac、加内存、上 SSD、修改 Gradle 构建任务、组件化开发、迁移构建系统到 buck 上等等，聪明的 Android 工程师们想过很多来加速构建的方法，但是成本都不低。

在这样的背景下，我们开发了 Freeline 来加速 Android 工程的构建。Freeline 只有极低的侵入性（我们也在研究无侵入的方案），却能极大地提高 Android 工程师们的开发效率，大多数增量构建都能够在 10 秒内，甚至 1 秒完成，基本上告别了 Gradle 的卡、慢，笔记本不再风扇狂转、发热发烫。

## 谁在用？
开源至今，已有来自 **BAT、新美大、今日头条、携程、聚美优品等上百款 App** 使用了 Freeline 来提高工程师们的开发效率，Freeline 对轻量级到重量级的 Android 应用都提供了强有力的增量编译支持。

如果你也在使用 Freeline，欢迎告诉我们！ -> [链接戳我！](https://www.freelinebuild.com)

## Freeline 的维护情况
[Freeline 主工程](https://github.com/alibaba/freeline)目前主要由两位蚂蚁金服的工程师在维护，[Android Studio 插件](https://github.com/alibaba/freeline/tree/master/android-studio-plugin)目前主要由社区开发者维护。

Freeline 从本质上来说是 Gradle 构建系统上的 hack 解决方案，所以对于多种多样的 Android 工程可能还存在着一些兼容性问题（不过已经有上百个 App 验证过 Freeline 的有效性，其实也不要太过担心），这些兼容性问题也是维护期间主要要解决的问题。

未来，Freeline 还会持续跟进并支持每个最新的 Android 版本，如果你在使用过程中遇见任何问题，欢迎向我们提 issue，我们会尽快帮忙解决。

## Freeline 支持的特性
- 支持标准的多模块 Gradle 工程的增量构建
- 并发执行增量编译任务
- 进程级别异常隔离机制
- 支持 so 动态更新
- 支持 resource.arsc 缓存
- 支持 retrolambda
- 支持 DataBinding
- 支持各类主流注解库（APT）
- 支持 Windows，Linux，Mac 平台

以下列表为 Freeline 支持的热更新情况：

|| Java | drawable, layout, etc. | res/values | native so|
|:-----:|:----:|:----:|:----:|:----:|
| add    | √    | √    |√ |   √   |     
| change    | √    |  √   |√ |   √   | 
| remove   | √    |   √  |x|   -   | 

Freeline 已经分别在 API 17，19，22，23的 Android 模拟器、Android 6.0 Nexus 6P 以及 Android 4.4 锤子手机上经过测试。如果想要充分体验 Freeline 增量编译的速度的话，最好使用 Android 5.0+ 的设备。

## 开始使用

### 接入前的准备
对于 Windows 用户来说，你需要提前安装 Python 2.7+（Freeline 暂时还不支持 Python 3+），安装完之后需要重启一下 Android Studio。

对于 Linux/Mac 用户，如果你已经安装了 Python 3+，推荐你将其 `alias` 设为 `python3`，再单独安装 Python 2.7+，并作为默认的 Python 指令，避免与 Android Studio 插件自动运行的 `python` 命令冲突，导致无法正常使用插件。

### 如何接入？
我们提供了两种接入 Freeline 的方法，最简单的方法是通过 Android Studio 的插件来接入（实际上是对命令行的方式做了封装，提供自动化的解决方案），另一种则是手工通过修改配置与执行命令的方式来接入。

#### 方法一：Android Studio 插件
在最新版本的 Freeline 插件中，提供了自动化一键接入的方式，不需要像以前一样手动修改`build.gradle`配置文件了。

在Android Studio中，通过以下路径`Preferences → Plugins → Browse repositories`，搜索“freeline”，并安装。

![](/img/freeline-as-plugin.jpg)

直接点击 `Run Freeline`的按钮，就可以享受Freeline带来的开发效率的提升啦（当然，你可能会先需要一个较为耗时的全量编译过程）。

第一次使用的时候，插件会自动检测是否安装了 Freeline，如果没有安装的话会弹出提示，按照提示点击“确定”，插件就会自动为你修改配置文件，并自动安装 Freeline 的依赖文件。

#### 方法二：命令行方式接入
配置 project-level 的 build.gradle，加入 freeline-gradle 的依赖：

````gradle
buildscript {
    repositories {
        jcenter()
    }
    dependencies {
        classpath 'com.antfortune.freeline:gradle:x.x.x'
    }
}
````
然后，在你的主 module 的 build.gradle 中，应用 freeline 插件的依赖：

````gradle
apply plugin: 'com.antfortune.freeline'

android {
    ...
}
````

最后，在命令行执行以下命令来下载 freeline 的 python 和二进制依赖。

- Windows[CMD]: `gradlew initFreeline`
- Linux/Mac: `./gradlew initFreeline`

对于国内的用户来说，如果你的下载的时候速度很慢，你也可以加上参数，执行`gradlew initFreeline -Pmirror`，这样就会从国内镜像地址来下载。

### 接入有问题？
接入 Freeline 时有几点需要注意的[编译过程也会报错提醒]：
- debug 模式下不要开启混淆
- 如果有多个 productFlavor 的话，需要配置指定 flavor

如果第一次运行的时候出现了耗时长达十几分钟的话，需要注意一下是否卡在下载 gradle 文件了。检查的方法即手动执行 `gradlew checkBeforeCleanBuild`。如果是卡在下载 gradle 文件的话，可以查找一下网络上相关的资料看下如何解决这个问题。

其他更多关于 Freeline DSL 的使用，可以参考 [Freeline DSL References](#Freeline%20DSL)。

### 如何升级？
如果你安装了 freeline 插件的话，在 Android Studio 中通过以下路径：`Build → Freeline → Check Freeline Update`即可自动检测新版本，点击`update`按钮，插件会自动为你进行升级。

当然，你也可以使用命令行的方式来升级 Freeline，修改方法如下：

1. 修改 project-level 的`build.gradle`文件，将`classpath 'com.antfortune.freeline:gradle:x.x.x'`修改为最新版本
2. 在命令行中执行（国内的同学推荐加上`-Pmirror`参数）：
    - Windows[CMD]: `gradlew initFreeline`
    - Linux/Mac: `./gradlew initFreeline`

## Freeline 的局限

- 第一次增量资源编译的时候可能会有点慢，因为需要额外传递一个完整的资源包
- 不支持删除带id的资源，否则可能导致aapt编译出错
- 暂不支持抽象类的增量编译
    - 我们正在尝试解决这个问题
- 部分 APT 插件可能需要单独适配
    - 绝大部分 APT 插件还是都支持的
- 不支持开启 Jack 编译
    - 想要使用 lambda 的话，先使用 Retrolambda 吧
- 不支持 Kotlin / Groovy / Scala 
    - 能够支持这几个基于 JVM 的语言的工具叫做 [JRebel for Android](https://zeroturnaround.com/software/jrebel-for-android)，你可以看看

## Freeline 原理
Freeline 本质上是热更新技术在编译期的运用，通过对同一个 apk 进行持续地热更新来达到增量编译的效果。基于 Freeline 进行修改，也能够实现线上应用的热修复以及 A/B Test。

Freeline 与其他类似的加速构建方案，比如 Instant Run、Buck、JRebel for Android 有什么区别呢？开发者如何来选择加速构建方案呢？可以参考这个知乎回答：[有什么办法能加快Android Studio中Gradle build速度？ - Yong Huang 的回答 - 知乎](https://www.zhihu.com/question/36892290/answer/125762516)。

Freeline 的具体原理可参考以下文章：
- [Freeline - Android 平台上的秒级编译方案](https://yq.aliyun.com/articles/59122)
- [Android 秒级编译工具 Freeline 新特性支持！](https://yq.aliyun.com/articles/62334)

感觉以下开源项目为 Freeline 的顺利开发提供了帮助：
- [Instant Run](https://developer.android.com/studio/run/index.html#instant-run)
- [Buck](https://github.com/facebook/buck)
- [LayoutCast](https://github.com/mmin18/LayoutCast)

## 命令行工具使用

注意，以下说明中的`gradlew`分别指代 Windows 环境下`gradlew`，Linux/Mac 环境下的`./gradlew`。

### Gradle Task: initFreeline

主要用于下载 freeline 的二进制依赖（包括 python 文件等）和生成工程描述文件。

在 build.gradle 中应用了 freeline gradle 插件之后，`initFreeline`这个任务会被添加到 Gradle 工程的根工程上，可以执行`gradlew initFreelien`来执行这个任务。

我们也为 initFreeline 这个任务提供了多种参数方便开发者们做一些定制化的修改。

参数：
- mirror
    - 使用方法：`gradlew initFreeline -Pmirror`
    - 参数说明：将下载链接指向国内镜像。不加参数的时候，默认从 aws 上进行下载，如果你所处的网络环境访问 aws 有问题的话，加上这个参数可以让你获得更好的下载速度。
- freelineVersion
    - 使用方法：`gradlew initFreeline -PfreelineVersion=0.8.2`
    - 参数说明：下载指定版本的 freeline 依赖文件。**注意**，使用这个参数的时候，请务必保证 build.gradle 文件中配置的 freeline 版本与指定要下载的 freeline 版本的版本号是一致的，以免产生一些不必要的编译错误。
- freelineTargetUrl
    - 使用方法：`gradlew initFreeline -PfreelineTargetUrl="http://xxx.com/freeline.zip"`
    - 参数说明：从指定的链接处下载 freeline 依赖文件。
- freelineCdnUrl
    - 使用方法：`gradlew initFreeline -PfreelineCdnUrl="http://xxx.com"`
    - 参数说明：使用指定的镜像处下载，适合访问外网需要白名单，在内网自己搭建镜像的同学使用
- freelineLocal
    - 使用方法：`gradlew initFreeline -PfreelineLocal="your-local-freeline-path"`
    - 参数说明：使用已经下载好的 freeline 依赖文件来安装，参数指向下载好的 freeline 依赖文件的本地路径。

### Gradle Task: checkBeforeCleanBuild

主要用于生成工程描述文件，每次 freeline 全量编译前会先执行一下这个任务。当你执行 python 命令提示报错的时候：`freeline_core.exceptions.NoConfigFoundException: xxxxxx/project_description.json not found, please execute gradlew checkBeforeCleanBuild first.`，也需要先执行一下`gradlew checkBeforeCleanBuild`。

### Gradle Build

freeline 为了不污染日常编译的编译产物，在执行`gradlew assemble`之类的编译命令时，不会启动 freeline 植入的任务。如果你想要在命令行运行 freeline 的任务，查看 freeline 输出的日志的话，需要在编译命令中，加入参数`-PfreelineBuild=true`。

freeline gradle 插件会在编译时，自动为工程加入 freeline 的 runtime 依赖（对于 release、test 版本自动加入 no-op 版本），如果你希望能够将 freeline runtime 的源码引入到工程中的话，需要在 freeline DSL 中加入配置，如：

````gradle
freeline {
    ...
    autoDependency false
}
````

然后使用 freeline 重新进行编译。如果要手动在命令行进行编译的话，需要加入参数：`-PdisableAutoDependency=true`。

### freeline.py

使用 freeline 进行编译的主要入口，freeline 主要通过 python 来实现跨平台。

参数：
- -h
    - 使用方式：`python freeline.py -h`
    - 参数说明：输出 freeline 的参数使用说明
- -v
    - 使用方式：`python freeline.py -v`
    - 参数说明：获取 freeline.py 的版本号
- -f
    - 使用方式：`python freeline.py -f`
    - 参数说明：强制进行全量编译
- -d
    - 使用方式：`python freeline.py -d`（注：可与各类参数叠加使用）
    - 参数说明：输出 freeline 的调试日志（**注意：不是对 Android 工程进行调试**）
- -w
    - 使用方式：`python freeline.py -f -w`
    - 参数说明：让应用启动的时候等待调试工具连接（目前只支持全量编译的时候配合使用），日常调试推荐直接使用 Android Studio 的`attach debugger to Android process`

## Freeline DSL

Freeline DSL 用来辅助进行工程配置，帮助 freeline 与 Android 工程无缝集成。主要配置在 Android 的主 module 的 build.gradle 文件中，形如：

````gradle
android {
    ...

    freeline {
        ... // 具体的配置内容
    }
}
````

注意，当涉及到在配置中定位具体的文件路径的时候，为了便于团队协作共同使用同一份 build.gradle 配置文件，推荐使用类似这样的方式，来书写文件路径：`project.rootProject.file("your-relative-path").getAbsolutePath()`

以下为具体的配置的说明：
- **hack**
    - 引入版本：0.5.0，**目前已废弃**
    - 参数类型：boolean
    - 默认值：false
    - 参数说明：是否使用 freeline 来打包的全局开关

- **buildScript**
    - 引入版本：0.5.0
    - 参数类型：String
    - 默认值：gradlew :main_module:assemble{ProductFlavor}Debug
    - 参数说明：工程的全量编译脚本，freeline 在全量打包时使用这个命令脚本来编译出 apk 产物

- **productFlavor**
    - 引入版本：0.5.0
    - 参数类型：String
    - 默认值：""
    - 参数说明：当工程含有多个 productFlavor 的时候，需要指定一个 flavor。如果未指定 flavor，则可能造成编译失败报错退出，会有错误日志引导添加这个配置参数

- **apkPath**
    - 引入版本：0.5.0
    - 参数类型：String
    - 默认值：freeline 会在编译过程中自动去找到编译产物 apk 的路径
    - 参数说明：编译产物 apk 的路径，freeline 也有可能找到错误的编译产物路径，这个时候可以手动配置正确的路径参数

- **extraResourceDependencyPaths**
    - 引入版本：0.5.0
    - 参数类型：List[String]
    - 默认值：[]
    - 参数说明：额外的工程的资源依赖路径。如果使用 freeline 编译的过程中出现了 aapt 编译报错，提示资源没找到，有可能是某些资源路径未被 freeline 自动检测到，这时可以将缺失的资源路径添加到这个参数中。注意，资源路径只需要添加到 res 一级即可，不需要具体到 drawable/layout/\*.xml/\*.drawable 这样的路径

- **excludeResourceDependencyPaths**
    - 引入版本：0.5.0，**已不需要**
    - 参数类型：List[String]
    - 默认值：[]
    - 参数说明：排除会导致重复的资源路径。后期的版本中，通过对 aapt 的修改，绕过了这个问题，已不需要这个参数

- **excludeHackClasses**
    - 引入版本：0.5.0
    - 参数类型：List[String]
    - 默认值：[]
    - 参数说明：打包过程中，freeline 会对 class 进行插桩，默认会跳过父类为`android/app/Application`的类。如果你有特殊需求需要绕过插桩，可以通过这个配置项进行配置

- **packageName**
    - 引入版本：0.5.5
    - 参数类型：String
    - 默认值：applicationId
    - 参数说明：freeline 默认获取 applicationId 作为应用的包名。如果你有特殊需求，或者 freeline 获取到了错误的包名的话，可以通过这个配置项配置你指定的包名

- **launcher**
    - 引入版本：0.5.5
    - 参数类型：String
    - 默认值：""
    - 参数说明：freeline 默认从 AndroidManifest.xml 文件中去获取应用的 launcher Activity，并用于全量编译后自动启动应用。如果 freeline 获取的 launcher 有误的话，可以通过这个配置项配置你指定的 launcher

- **applicationProxy**
    - 引入版本：0.7.0
    - 参数类型：boolean
    - 默认值：true
    - 参数说明：为了便于快速集成，freeline 默认会替换应用的 Application 类，替换为 FreelineApplication。如果由于这个替换造成了 ClassNotFound 等问题，可以将这里的值置为 false，并手动在你的 Application 类中加入`FreelineCore.init(this);`。修改后，进行编译之前，记得先 clean，然后再来编译，避免无谓的错误出现

- **autoDependency**
    - 引入版本：0.7.0
    - 参数类型：boolean
    - 默认值：false
    - 参数说明：freeline 自动为应用加载了 runtime 依赖，在非 debug 的 buildType 加载的是 no-op 版本，如果你希望通过引源码的方式来加入 runtime 依赖，或者手动指定 runtime 的版本，需要将此配置项置为 true，然后再来手动加入依赖

## 常见问题

常见问题目录：
- [为什么叫 Freeline？](#%E4%B8%BA%E4%BB%80%E4%B9%88%E5%8F%AB%20Freeline%EF%BC%9F)
- [Freeline 与 Instant Run有什么区别？](#Freeline%20%E4%B8%8E%20Instant%20Run%E6%9C%89%E4%BB%80%E4%B9%88%E5%8C%BA%E5%88%AB%EF%BC%9F)
- [使用 Freeline 时遇见问题怎么办？](#%E4%BD%BF%E7%94%A8%20Freeline%20%E6%97%B6%E9%81%87%E8%A7%81%E9%97%AE%E9%A2%98%E6%80%8E%E4%B9%88%E5%8A%9E%EF%BC%9F)
- [如何进行断点调试？](#%E5%A6%82%E4%BD%95%E8%BF%9B%E8%A1%8C%E6%96%AD%E7%82%B9%E8%B0%83%E8%AF%95%EF%BC%9F)
- [是否支持 Kotlin、Groovy、Scala 等 JVM 语言？](#%E6%98%AF%E5%90%A6%E6%94%AF%E6%8C%81%20Kotlin%E3%80%81Groovy%E3%80%81Scala%20%E7%AD%89%20JVM%20%E8%AF%AD%E8%A8%80%EF%BC%9F)
- [是否可以开启 Jack 来使用 Java 8 的特性？](#%E6%98%AF%E5%90%A6%E5%8F%AF%E4%BB%A5%E5%BC%80%E5%90%AF%20Jack%20%E6%9D%A5%E4%BD%BF%E7%94%A8%20Java%208%20%E7%9A%84%E7%89%B9%E6%80%A7%EF%BC%9F)
- [是否会影响 release 打包？](#%E6%98%AF%E5%90%A6%E4%BC%9A%E5%BD%B1%E5%93%8D%20release%20%E6%89%93%E5%8C%85%EF%BC%9F)
- [为什么 Freeline 会进行全量编译？](#%E4%B8%BA%E4%BB%80%E4%B9%88%20Freeline%20%E4%BC%9A%E8%BF%9B%E8%A1%8C%E5%85%A8%E9%87%8F%E7%BC%96%E8%AF%91%EF%BC%9F)
- [不停地提示 check sync status failed /不停地全量编译？](#%E4%B8%8D%E5%81%9C%E5%9C%B0%E6%8F%90%E7%A4%BA%20check%20sync%20status%20failed%20%2F%E4%B8%8D%E5%81%9C%E5%9C%B0%E5%85%A8%E9%87%8F%E7%BC%96%E8%AF%91%EF%BC%9F)
- [Freeline "try to connect device/ connect_device_task failed."](#Freeline%20%22try%20to%20connect%20device%2F%20connect_device_task%20failed.%22)
- [java.lang.UnsupportedClassVersionError: com/android/build/gradle/AppPlugin : Unsupported major.minor version 52.0](#java.lang.UnsupportedClassVersionError%3A%20com%2Fandroid%2Fbuild%2Fgradle%2FAppPlugin%20%3A%20Unsupported%20major.minor%20version%2052.0)
- [资源编译出错：Public symbol xxxx declared here is not defined.](#%E8%B5%84%E6%BA%90%E7%BC%96%E8%AF%91%E5%87%BA%E9%94%99%EF%BC%9APublic%20symbol%20xxxx%20declared%20here%20is%20not%20defined.)
- [为什么一启动就 crash 报错 NoClassDefFoundError？](#%E4%B8%BA%E4%BB%80%E4%B9%88%E4%B8%80%E5%90%AF%E5%8A%A8%E5%B0%B1%20crash%20%E6%8A%A5%E9%94%99%20NoClassDefFoundError%EF%BC%9F)
- [切换回 Android Studio 的 RUN 时，编译出错](#%E5%88%87%E6%8D%A2%E5%9B%9E%20Android%20Studio%20%E7%9A%84%20RUN%20%E6%97%B6%EF%BC%8C%E7%BC%96%E8%AF%91%E5%87%BA%E9%94%99)
- [报错：NoConfigFoundException](#%E6%8A%A5%E9%94%99%EF%BC%9ANoConfigFoundException)
- [与Genymotion自带的adb发生冲突](#%E4%B8%8EGenymotion%E8%87%AA%E5%B8%A6%E7%9A%84adb%E5%8F%91%E7%94%9F%E5%86%B2%E7%AA%81)
- [manifest merge 报错](#manifest%20merge%20%E6%8A%A5%E9%94%99)
- [Windows 上为什么没有类似 Linux/Mac 上的进度条？](#Windows%20%E4%B8%8A%E4%B8%BA%E4%BB%80%E4%B9%88%E6%B2%A1%E6%9C%89%E7%B1%BB%E4%BC%BC%20Linux%2FMac%20%E4%B8%8A%E7%9A%84%E8%BF%9B%E5%BA%A6%E6%9D%A1%EF%BC%9F)
- [为什么一直卡在“build increment app”页面？](#%E4%B8%BA%E4%BB%80%E4%B9%88%E4%B8%80%E7%9B%B4%E5%8D%A1%E5%9C%A8%E2%80%9Cbuild%20increment%20app%E2%80%9D%E9%A1%B5%E9%9D%A2%EF%BC%9F)
- [Freeline 根本没有那么快，你们怎么可以瞎吹牛呢？](#Freeline%20%E6%A0%B9%E6%9C%AC%E6%B2%A1%E6%9C%89%E9%82%A3%E4%B9%88%E5%BF%AB%EF%BC%8C%E4%BD%A0%E4%BB%AC%E6%80%8E%E4%B9%88%E5%8F%AF%E4%BB%A5%E7%9E%8E%E5%90%B9%E7%89%9B%E5%91%A2%EF%BC%9F)

使用 Freeline 的过程中如果遇见了任何问题，可以先看下文档，或者搜索一下已有的 issue，如果还无法解决的话，可以在 [Github](https://github.com/alibaba/freeline/issues) 上给我们提 issue，我们会尽快帮你解决问题。

### 为什么叫 Freeline？

对的！是 **Freeline**，而不是 FreeLine！

**Freeline** 是一项[极限运动](https://en.wikipedia.org/wiki/Freeline_skates)，代表着我们对极致速度、极简、自由的追求。我们希望 Freeline 能够简单易用，并有着极致的编译速度。同时，我们也会持续地优化，继续追求 Android 编译的极致体验。

### Freeline 与 Instant Run有什么区别？

可以参考知乎回答：[有什么办法能加快Android Studio中Gradle build速度？ - Yong Huang 的回答 - 知乎](https://www.zhihu.com/question/36892290/answer/125762516)

答案中对比了 Instant Run、Buck (okbuck)、JRebel for Android、Freeline 这几种加速构建方案。

### 使用 Freeline 时遇见问题怎么办？

1. 查看文档
2. 查看已有的 issue
3. 以上两种方法都无法解决时，可以在 [Github](https://github.com/alibaba/freeline/issues) 上提 issue 寻求帮助

### 如何进行断点调试？

使用 freeline 进行调试跟平时调试基本上是一样的。推荐选择 Android Studio 工具栏上的`attach debugger to Android process`即可进行断点调试。如果需要在 Application 的逻辑中进行调试的话，可以使用命令`python freeline.py -f -w`，工程会在全量编译结束启动时，自动等待 debugger 工具的连接。

**注意**：`python freeline.py -d`仅仅是输出 freeline 的调试日志而已，并不是真的在对 Android 工程进行调试。

### 是否支持 Kotlin、Groovy、Scala 等 JVM 语言？

不支持。Freeline 基于 `*.java -> *.class -> *.dex`这样的编译链进行编译，并通过 multidex 的方案进行增量。故无法支持除 Java 之外的其他 JVM 语言。

### 是否可以开启 Jack 来使用 Java 8 的特性？

不支持，原因同上，Jack 改变了编译链（*.java –> *.jack –> *.dex）。

### 是否会影响 release 打包？

Freeline 对 release 打包几乎没有影响。Freeline 在 release 打包的时候自动添加的是`no-op`的 runtime 依赖，对`FreelineCore.init(this);`函数是一个空实现。如果你开启了 Application 代理的话，更是基本上毫无影响，请放心使用。

如果你还是实在放心不下的话，可以在打 release 包的时候，把 freeline 相关的内容注释掉，然后 clean and build。

### 为什么 Freeline 会进行全量编译？

Freeline 在以下几种情况下会自动进行全量编译：
- 发现 AndroidManifest.xml 有修改
- 发现 build.gradle 文件有修改
- 发现有超过 20 个 Java 文件有修改过（通常在使用 git 切换分支的情况会出现）

### 不停地提示 check sync status failed /不停地全量编译？

以下操作建议使用`python freeline.py -d`命令来查看详细日志：

通常，freeline 在全量编译后，会自动进行增量编译，但是在以下几种情况下，会从增量转入全量编译：
- 涉及到 build.gradle、settings.gradle、AndroidManifest.xml 文件有改动
- 增量编译时，发现有超过 20 个 java 文件出现改动

也有一种情况，每次都出现一句日志：`[WARNING] check sync status failed, a clean build will be automatically executed.`

这句日志的意思是，通过 adb 连接上的设备上找到了与本地编译的项目相同 uuid 的应用（通常是同个项目使用 freeline 打包安装上的），但是在进行基线校验的时候校验失败，需要重新打包编译。Freeline 的基线校验值由 apk 打包的时间与增量次数共同生成，用于保证本地编译的版本与设备上安装的版本是完全一致的。主要是在切换设备的时候，容易出现这个问题。

正常情况下，一次全量编译后就会恢复正常，但也有可能会反复出现这句日志，一直无法恢复增量编译，这种时候首先需要检查一下 PC 上是否连接了多台设备，或者 Android 模拟器 + 真实设备。如果有的话，首先保持只有一台设备或者模拟器。

如果还是反复出现这个日志的话，可以尝试把手机上的 apk 先卸载了，再重新用 freeline 编译安装。（如果是这个原因的话，可能是 freeline 的缓存不更新的 bug 导致的，近期会解决）

### Freeline "try to connect device/ connect_device_task failed."

排查方法如下（建议配合使用`python freeline.py -d`）：

1. 确定`FreelineCore.init(this);`加入到 Application 类中，且在`onCreate()`下的第一行，不要根据是否在主进程做特殊处理，否则可能导致`FreelineService`无法正常启动；**[Freeline 0.7.0+ 开始，默认开启了 Application 替换，这条可以不用检查]**
2. 确定`FreelineService`以及 freeline 相关组件是否正常 merge 到最终的 manifest 中，最终的 manifest 路径在`${module}/build/intermediates/manifests`中；
3. 确定`python freeline.py -v`与定义在 build.gradle 中的 freeline 的版本是否一致；
4. 确定是否刚刚执行了清空app数据的操作，freeline 缓存数据在`/data/data`路径，清空app数据也会导致连接不上的问题（执行 freeline 命令时，通常会有句明显的日志反复出现：`server result is -1`）；
5. 确定是否开启了网络代理导致`127.0.0.1`被重定向？
6. 一定要先使用 freeline 来打全量包，再来进行增量，否则也会出现这个问题。即，freeline 的全量编译与android-studio自带的RUN会存在冲突。

当上述问题都无法解决时，有个终极的解决方案就是**重启试试**...不少人通过重启顺利解决连接不上的问题。。。

### java.lang.UnsupportedClassVersionError: com/android/build/gradle/AppPlugin : Unsupported major.minor version 52.0

问题原因，从 Android Studio 2.2 开始，默认使用内置的 Java8 版本，如果你的系统环境变量中使用的还是 Java7 的话，就会出现这个问题，解决方案就是升级系统的 Java 版本。

### 资源编译出错：Public symbol xxxx declared here is not defined.

Aapt 打资源包报错。在aapt的参数中，缺少某些未被freeline自动识别的资源路径，导致部分资源id没有被找到。

解决方案，将缺少的资源路径，在`build.gradle`的freeline DSL中加入配置项，如：

````gradle
freeline {
    ...
    extraResourceDependencyPaths = ['/path/to/resource/directory1', '/path/to/resource/directory2']
}
````

注意，只需添加到`res`路径即可，不需要具体到`drawable`、`layout`的具体路径

### 为什么一启动就 crash 报错 NoClassDefFoundError？

修改一下 build.gradle 文件，添加配置项：

``` gradle
freeline {
    ...
    applicationProxy false
}
```

在你的Application类中加入：

``` java
public class YourApplication extends Application {

    public onCreate() {
         super.onCreate();
         FreelineCore.init(this);
    }
}
```

然后clean，重新打包即可解决问题。

### 切换回 Android Studio 的 RUN 时，编译出错

正常现象。推荐先执行`gradlew clean`后，再使用 Android Studio 的 RUN，就可以恢复正常了。

### 报错：NoConfigFoundException
提示如：NoConfigFoundException：`/path/ not found, please execute gradlew checkBeforeCleanBuild first.`
    
terminal 执行指令：

- Linux/Mac: `./gradlew checkBeforeCleanBuild`
- Windows: `gradlew.bat checkBeforeCleanBuild`

### 与Genymotion自带的adb发生冲突

````bash
$ adb devices
adb server is out of date.  killing...
cannot bind 'tcp:5037'
ADB server didn't ACK
*failed to start daemon *
````

问题出现的原因是 Genymotion 自带了 adb 工具，会造成冲突。解决的方式是将 Genymotion 所使用的 adb 改为 androidsdk 自带的 adb。具体可以参考：[StackOverflow - How to use adb with genymotion on mac?](http://stackoverflow.com/questions/26630398/how-to-use-adb-with-genymotion-on-mac)

### manifest merge 报错

提示如：Exception: `manifest merger failed: uses-sdk:minSdkVersion can not be smaller than 14 declared in library[com.antfortune.freeline:runtime:x.x.x]`

工程的 minSdkVersion 比 freeline-runtime 来得低导致的，解决方案如下：

````xml
<uses-sdk
        android:minSdkVersion="9"
        android:targetSdkVersion="21"
        tools:overrideLibrary="com.antfortune.freeline"/>
````

### Windows 上为什么没有类似 Linux/Mac 上的进度条？

Freeline 在 Windows 上默认开启 debug 模式，输出 debug output 信息，原因是 Windows CMD 的 terminal API 无法实现类似的进度条的功能，如果你知道如何实现的话，也欢迎给我们提交 PR :)

### 为什么一直卡在“build increment app”页面？

可以搜一下 Github 上相关的 issue，基本都是与你自己的工程或者机器的环境有关。神方法：重启试试。

### Freeline 根本没有那么快，你们怎么可以瞎吹牛呢？

这位朋友，欢迎你把使用 Freeline 时出现的日志提示，到 [Github](https://github.com/alibaba/freeline/issues) 上为我们提个 issue，让我们一起来探讨一下。

## EOF
如果对 wiki 有任何疑问或者需要订正的地方，欢迎联系我们。

Freeline Document wirtten by [Yong](https://github.com/lomanyong)


