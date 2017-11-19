# Freeline

![Freeline](http://ww4.sinaimg.cn/large/006tNc79gw1f6ooza8pkuj30h804gjrk.jpg)

[![Release Version](https://img.shields.io/badge/release-0.8.8-red.svg)](https://github.com/alibaba/freeline/releases) [![BSD3 License](https://img.shields.io/badge/license-BSD3-blue.svg)](https://github.com/alibaba/freeline/blob/master/LICENSE) [![PRs Welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)](https://github.com/alibaba/freeline/pulls)

Freeline 是 Android 平台上的秒级编译方案，Instant Run 的替代品，也可以从 [Freeline 官方主页](https://www.freelinebuild.com/)来获取更多的信息。

Freeline 由[蚂蚁聚宝](https://www.antfortune.com/) Android 团队开发，它可以充分利用缓存文件，在几秒钟内迅速地对代码的改动进行编译并部署到设备上，有效地减少了日常开发中的大量重新编译与安装的耗时。

Freeline能够为Android开发者节省很多喝杯咖啡的时间 : )

## Freeline Insights
Freeline 将整个编译过程拆分成多个小模块，根据模块间的依赖关系并发进行编译任务。它可以对所有编译缓存文件做到充分地利用，真正地做到了“增量”编译。同时，它也提取了 buck 的部分组件（dx 与 DexMerger），来加速构建流程。Freeline 会在 app 运行时启动一条独立进程来运行 Socket Server 以进行开发机与 app 的通信，同时也能确保即使在 app 出现 crash 之后，仍然能够使增量编译持续生效。

Freeline 是通过 multi-dex 的方案来实现 Java 代码的增量更新的。与此同时，我们也专门对 aapt 工具做了深度优化，给出了独立的 FreelineAapt 资源打包工具用来生成增量的资源包以及加快资源包的打包速度。运行期的资源替换，Freeline 中使用了 Instant Run 的方案。除此之外，Freeline 也支持运行期动态更新 so 文件。

Freeline 会根据代码文件的变更情况，自动在全量编译与增量编译中进行切换。

实际上，Freeline 可以在经过简单的改造之后，作为线上热修复方案，通过动态下发增量资源，来对应用进行修复或者资源替换。经过压缩后的增量资源通常都会在 100kb 以下，用户可以在移动网络的环境下直接接收到热补丁。蚂蚁聚宝已经基于 Freeline 打造了完整的热修复框架，应用于线上问题修复以及 A/B Test。

[中文原理说明](https://yq.aliyun.com/articles/59122?spm=5176.8091938.0.0.1Bw3mU)

## Features
- 支持标准的多模块 Gradle 工程的增量构建
- 并发执行增量编译任务
- 进程级别异常隔离机制
- 支持 so 动态更新
- 支持 resource.arsc 缓存
- 支持 retrolambda
- 支持 DataBinding
- 支持各类主流注解库
- 支持 Windows，Linux，Mac 平台

以下列表为 Freeline 支持的热更新情况：

|| Java | drawable, layout, etc. | res/values | native so|
|:-----:|:----:|:----:|:----:|:----:|
| add    | √    | √    |√ |   √   |     
| change    | √    |  √   |√ |   √   | 
| remove   | √    |   √  |x|   -   | 

Freeline 已经分别在 API 17，19，22，23 的 Android 模拟器、Android 6.0 Nexus 6P 以及 Android 4.4 锤子手机上经过测试。如果想要充分体验 Freeline 增量编译的速度的话，最好使用 Android 5.0+ 的设备。

## Download
配置 project-level 的 build.gradle，加入 freeline-gradle 的依赖：

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
然后，在你的主 module 的 build.gradle 中，应用 freeline 插件的依赖：

````Gradle
apply plugin: 'com.antfortune.freeline'

android {
    ...
}
````

最后，在命令行执行以下命令来下载 freeline 的 python 和二进制依赖。

- Windows[CMD]: gradlew initFreeline
- Linux/Mac: ./gradlew initFreeline

对于国内的用户来说，如果你的下载的时候速度很慢，你也可以加上参数，执行`gradlew initFreeline -Pmirror`，这样就会从国内镜像地址来下载。

你也可以使用参数`-PfreelineVersion={your-specific-version}`来下载特定版本的 freeline 依赖。

如果你的工程结构较为复杂，在第一次使用 freeline 编译的时候报错了的话，你可以添加一些 freeline 提供的配置项，来适配你的工程。具体可以看 [Freeline DSL References](https://github.com/alibaba/freeline/wiki/Freeline-DSL-References)。

## Usage
Freeline 最快捷的使用方法就是直接安装 Android Studio 插件。

在 Android Studio 中，通过以下路径：

- MacOS
> Android Studio → Preferences... → Plugins → Browse repositories...

- Windows 和 Linux
> File → Settings... → Plugins → Browse repositories...

并搜索“freeline”，并安装。

![](http://ww4.sinaimg.cn/large/65e4f1e6gw1f82eknaeudj20tk01omxe.jpg)

直接点击 `Run Freeline` 的按钮，就可以享受 Freeline 带来的开发效率的提升啦（当然，你可能会先需要一个较为耗时的全量编译过程）。

插件也会提示你 Freeline 最新的版本是多少，你也可以通过插件来对 Freeline 进行更新。

非常感谢 [@pengwei1024](https://github.com/pengwei1024) 和 [@act262](https://github.com/act262) 帮助我们开发了这个非常棒的 Freeline 插件。

除此之外，你也可以通过在命令行执行 Python 脚本的方式来使用 Freeline，具体可以看我们的相应的链接 [Freeline CLI Usage](https://github.com/alibaba/freeline/wiki/Freeline-CLI-Usage)。

## Sample Usage
````
git clone git@github.com:alibaba/freeline.git
cd freeline/sample
./gradlew initFreeline
python freeline.py
````

## TODO
- 兼容性与稳定性的提升
- 多设备同时连接 PC 的支持

## Limitations
- 第一次增量资源编译的时候可能会有点慢
- 不支持删除带 id 的资源，否则可能导致 aapt 编译出错
- 暂不支持抽象类的增量编译
- 不支持开启 Jack 编译
- 不支持 Kotlin/Groovy/Scala

## Contributing
不论是小的修改，还是大的feature更新，我们都非常欢迎大家为 Freeline 贡献代码。

## Troubleshooting
See [wiki](https://github.com/alibaba/freeline/wiki/%E5%B8%B8%E8%A7%81%E9%97%AE%E9%A2%98).

## Thanks
- [Instant Run](https://developer.android.com/studio/run/index.html#instant-run)
- [Buck](https://github.com/facebook/buck)
- [LayoutCast](https://github.com/mmin18/LayoutCast)

## License
BSD3 License
