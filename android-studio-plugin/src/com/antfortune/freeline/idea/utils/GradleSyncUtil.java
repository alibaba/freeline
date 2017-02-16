package com.antfortune.freeline.idea.utils;

import com.intellij.openapi.project.Project;
import com.antfortune.freeline.idea.models.GradleSyncHandler;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/**
 * Created by pengwei on 2016/11/25.
 */
public class GradleSyncUtil {

    /**
     * 获取GradleSyncListener
     * android studio 2.3 GradleSyncListener类换包名了
     *
     * @return
     */
    private static Object getGradleSyncAdapter(GradleSyncHandler mHandler) {
        Class<?> adapter = null;
        Class<?> listener = null;
        try {
            adapter = Class.forName("com.android.tools.idea.gradle.project.sync.GradleSyncListener$Adapter");
            listener = Class.forName("com.android.tools.idea.gradle.project.sync.GradleSyncListener");
        } catch (ClassNotFoundException e) {
            try {
                adapter = Class.forName("com.android.tools.idea.gradle.project.GradleSyncListener$Adapter");
                listener = Class.forName("com.android.tools.idea.gradle.project.GradleSyncListener");
            } catch (ClassNotFoundException e1) {

            }
        }
        if (adapter != null && listener != null) {
            Object mObj = Proxy.newProxyInstance(GradleSyncUtil.class.getClassLoader(), new Class[]{listener}, mHandler);
            System.out.println("Proxy=" + mObj);
            try {
                Method successMethod = listener.getDeclaredMethod("syncSucceeded", new Class[]{Project.class});
                Method failureMethod = listener.getDeclaredMethod("syncFailed", new Class[]{Project.class, String.class});
                Object result = adapter.newInstance();
                successMethod.invoke(result, new Object[]{mObj});
                failureMethod.invoke(result, new Object[]{mObj});
                return result;
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InstantiationException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    /**
     * 同步gradle
     * android studio 2.3之前用GradleProjectImporter.getInstance().requestProjectSync(project, listener)
     * android studio 2.3后用GradleSyncInvoker.getInstance().requestProjectSync(project, null, listener);
     * @param project
     * @param mHandler
     */
    @Deprecated
    public static void startSync(Project project, GradleSyncHandler mHandler) {
        if (mHandler == null) {
            LogUtil.d("GradleSyncHandler == null");
            return;
        }
        Object adapter = getGradleSyncAdapter(mHandler);
        if (adapter == null) {
            LogUtil.d("getGradleSyncAdapter() return == null");
            return;
        }
        try {
            Class<?> syncClass = Class.forName("com.android.tools.idea.gradle.project.sync.GradleSyncInvoker");
            if (syncClass != null) {
                Method instance = syncClass.getMethod("getInstance", null);
                Object value = instance.invoke(null);
                Method syncMethod = syncClass.getMethod("requestProjectSyncAndSourceGeneration", new Class[]{Project.class, adapter.getClass()});
                syncMethod.invoke(value, project, adapter);
            }
        } catch (ClassNotFoundException e) {
            startSyncForOldVersion(project, adapter);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * 支持旧版本startSync
     * @param project
     * @param adapter
     */
    private static void startSyncForOldVersion(Project project, Object adapter) {
        try {
            Class<?> syncClass = Class.forName("com.android.tools.idea.gradle.project.GradleProjectImporter");
            if (syncClass != null) {
                Method instance = syncClass.getMethod("getInstance", null);
                Object value = instance.invoke(null);
                Method syncMethod = syncClass.getMethod("requestProjectSync", new Class[]{Project.class, adapter.getClass()});
                syncMethod.invoke(value, project, adapter);
            }
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

}
