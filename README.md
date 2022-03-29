# Apk 监控插件

> ApkMonitorPlugin 是一个用来对 Android Apk 包进行监控的 Gradle 插件，对开发者无感知，在编译期做处理，`兼容 AndResGurad

## 版本说明

|版本号|功能|
|--|--|
|0.1.beta-SNAPSHOT|资源文件去重|
|0.2.beta-SNAPSHOT|添加资源文件去重日志记录|


## 功能

* 对 apk 中的重复资源文件进行去重

    **支持 Android R+ (targetSdkVersion >= 30)**


## 如何使用插件

> 由于暂未发布到 mavenCentral() 仓库，暂时只能使用 mavenLocal() 或者自行打包到自己的 maven 仓库

1. 编译插件到 mavenLocal() 中
    在终端执行以下命令，或者通过 Android Studio 的 Gradle 面板进行操作

    ```./gradlew :plugin:publishToMavenLocal```

    ![apk_monitor_plugin_doc_publish_maven_local.png](https://s2.loli.net/2022/03/29/MgKbkiq38IBZSXT.png)

2. 在项目中添加 classpath 依赖
    打开项目目录下的 `build.gradle` 文件，在 `repositories` 中添加 `mavenLocal()` 仓库，并添加 `classpath 'me.ppting.plugin:apkMonitor:0.1.beta` 依赖

    ![apk_monitor_plugin_doc_add_repo.png](https://s2.loli.net/2022/03/29/VIN31u2v8aSblMF.png)

3. 在 app module 中引入插件，并添加配置参数

    ***PS.参数默认都为 true，即打开状态***

    ```
    apply plugin: 'ApkMonitorPlugin'
    apkMonitorConfig {
        enable = true//是否打开插件
        debugEnable true//debug 模式下是否打开插件
        repeatConfig {//资源去重任务的配置
            enable true//是否打开去重任务
            debugEnable = true//debug 模式下是否打开去重任务
            ignoreList = ["ic_launcher_3.png"]//白名单列表，在这个列表中的资源文件将不会被删除
            enableReportMapping = true//是否记录去重日志，默认为 true
            mappingFilePath = ""//去重日志文件路径，默认为项目的 build 目录下，app/build，输出的日志文件为 repeatMapping.txt

        }
    }
    ```


## Todo:
* 去重文件的日志记录
* 大图检测
* 资源文件压缩