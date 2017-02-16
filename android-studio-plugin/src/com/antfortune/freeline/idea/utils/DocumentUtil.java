package com.antfortune.freeline.idea.utils;

import com.intellij.codeInsight.actions.OptimizeImportsProcessor;
import com.intellij.codeInsight.actions.ReformatCodeAction;
import com.intellij.codeInsight.actions.ReformatCodeProcessor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;

/**
 * Created by pengwei on 2016/11/1.
 */
public class DocumentUtil {

    /**
     * 保存文档和设置
     */
    public static void saveDocument() {
        FileDocumentManager.getInstance().saveAllDocuments();
        ApplicationManager.getApplication().saveSettings();
    }

    /**
     * 格式化代码
     *
     * @param project
     * @param virtualFiles
     */
    public static void reformatCode(final Project project, final VirtualFile virtualFiles) {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
            @Override
            public void run() {
                PsiFile[] psiFiles = ReformatCodeAction.convertToPsiFiles(new VirtualFile[]{virtualFiles}, project);
                if (psiFiles != null && psiFiles.length == 1 && psiFiles[0] != null) {
                    new ReformatCodeProcessor(project, psiFiles[0], null, false).run();
                }
            }
        });
    }

    /**
     * 优化导入
     *
     * @param project
     * @param virtualFiles
     */
    public static void optimizeImports(Project project, VirtualFile... virtualFiles) {
        new OptimizeImportsProcessor(
                project, ReformatCodeAction.convertToPsiFiles(virtualFiles, project), null).run();
    }
}
