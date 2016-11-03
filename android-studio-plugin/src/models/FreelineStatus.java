package models;

import com.intellij.openapi.vfs.VirtualFile;
import utils.LogUtil;

import java.util.Collection;

/**
 * Created by pengwei on 2016/10/31.
 */
public class FreelineStatus {
    Collection<VirtualFile> gradleBuildFiles;
    boolean existClasspath = false;
    private VirtualFile classpathFile;
    boolean existPlugin = false;
    private VirtualFile pluginFile;
    //  mac下只有freeline文件夹 + freeline.py , win下有freeline.py + freeline/ + freeline_core/
    boolean existFreelineCore = false;

    public FreelineStatus setClasspathFile(VirtualFile classpathFile) {
        this.classpathFile = classpathFile;
        if (classpathFile != null) {
            existClasspath = true;
        }
        return this;
    }

    public FreelineStatus setPluginFile(VirtualFile pluginFile) {
        this.pluginFile = pluginFile;
        if (pluginFile != null) {
            existPlugin = true;
        }
        return this;
    }

    public FreelineStatus setExistFreelineCore(boolean existFreelineCore) {
        this.existFreelineCore = existFreelineCore;
        return this;
    }

    public boolean isExistClasspath() {
        return existClasspath;
    }

    public VirtualFile getClasspathFile() {
        return classpathFile;
    }

    public boolean isExistPlugin() {
        return existPlugin;
    }

    public VirtualFile getPluginFile() {
        return pluginFile;
    }

    public boolean isExistFreelineCore() {
        return existFreelineCore;
    }

    /**
     * 是否初始化Freeline
     * 满足一下三个条件
     * 1. 存在classpath 'com.antfortune.freeline:gradle:*'
     * 2. 存在apply plugin: 'com.antfortune.freeline'
     * 3. 存在freeline_core、release_tools、freeline.py
     * @return
     */
    public boolean hasInitFreeline() {
        LogUtil.d("existClasspath=%s,existPlugin=%s,existFreelineCore=%s", existClasspath, existPlugin, existFreelineCore);
        return existClasspath && existPlugin && existFreelineCore;
    }

    public Collection<VirtualFile> getGradleBuildFiles() {
        return gradleBuildFiles;
    }

    public FreelineStatus setGradleBuildFiles(Collection<VirtualFile> gradleBuildFiles) {
        this.gradleBuildFiles = gradleBuildFiles;
        return this;
    }
}
