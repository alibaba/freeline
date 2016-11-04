package utils;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;

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
}
