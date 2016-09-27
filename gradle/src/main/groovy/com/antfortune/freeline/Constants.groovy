package com.antfortune.freeline

/**
 * Created by huangyong on 16/7/20.
 */
class Constants {

    public static final String FREELINE_BUILD_CACHE_DIR = "freeline"

    public static final List<String> FREELINE_CLASSES = new ArrayList<>()

    static {
        FREELINE_CLASSES.add('com/antfortune/freeline/FreelineApplication.class')
        FREELINE_CLASSES.add('com/antfortune/freeline/FreelineCore.class')
        FREELINE_CLASSES.add('com/antfortune/freeline/FreelineReceiver.class')
        FREELINE_CLASSES.add('com/antfortune/freeline/FreelineService.class')
        FREELINE_CLASSES.add('com/antfortune/freeline/FreelineService$InnerService.class')
        FREELINE_CLASSES.add('com/antfortune/freeline/IDynamic.class')
        FREELINE_CLASSES.add('com/antfortune/freeline/MiddlewareActivity.class')
        FREELINE_CLASSES.add('com/antfortune/freeline/gradle/GradleDynamic.class')
        FREELINE_CLASSES.add('com/antfortune/freeline/resources/MonkeyPatcher.class')
        FREELINE_CLASSES.add('com/antfortune/freeline/router/ISchemaAction.class')
        FREELINE_CLASSES.add('com/antfortune/freeline/router/Router.class')
        FREELINE_CLASSES.add('com/antfortune/freeline/router/schema/CheckResourceSchema.class')
        FREELINE_CLASSES.add('com/antfortune/freeline/router/schema/CheckSyncSchema.class')
        FREELINE_CLASSES.add('com/antfortune/freeline/router/schema/CloseLonglinkSchema.class')
        FREELINE_CLASSES.add('com/antfortune/freeline/router/schema/PushDexSchema.class')
        FREELINE_CLASSES.add('com/antfortune/freeline/router/schema/PushFullResourcePackSchema.class')
        FREELINE_CLASSES.add('com/antfortune/freeline/router/schema/PushNativeSchema.class')
        FREELINE_CLASSES.add('com/antfortune/freeline/router/schema/PushResourceSchema.class')
        FREELINE_CLASSES.add('com/antfortune/freeline/router/schema/RestartSchema.class')
        FREELINE_CLASSES.add('com/antfortune/freeline/server/EmbedHttpServer.class')
        FREELINE_CLASSES.add('com/antfortune/freeline/server/LongLinkServer.class')
        FREELINE_CLASSES.add('com/antfortune/freeline/util/ActivityManager.class')
        FREELINE_CLASSES.add('com/antfortune/freeline/util/AppUtils.class')
        FREELINE_CLASSES.add('com/antfortune/freeline/util/DexUtils.class')
        FREELINE_CLASSES.add('com/antfortune/freeline/util/FileUtils.class')
        FREELINE_CLASSES.add('com/antfortune/freeline/util/NativeUtils.class')
        FREELINE_CLASSES.add('com/antfortune/freeline/util/ReflectUtil.class')
        FREELINE_CLASSES.add('com/antfortune/freeline/BuildConfig.class')
        FREELINE_CLASSES.add('com/antfortune/freeline/FreelineConfig.class')
    }

}
