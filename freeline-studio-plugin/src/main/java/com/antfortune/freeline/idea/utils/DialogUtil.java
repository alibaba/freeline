package com.antfortune.freeline.idea.utils;

import com.intellij.openapi.ui.DialogBuilder;
import com.intellij.openapi.ui.Messages;

import javax.swing.*;

/**
 * Created by pengwei on 2016/11/2.
 */
public final class DialogUtil {

    /**
     * 创建普通对话框
     * @param message
     * @param okText
     * @param cancelText
     * @return
     */
    public static boolean createDialog(String message, String okText, String cancelText) {
        DialogBuilder builder = new DialogBuilder();
        builder.setTitle("Dialog Message");
        builder.resizable(false);
        builder.setCenterPanel(new JLabel(message, Messages.getInformationIcon(), SwingConstants.CENTER));
        builder.addOkAction().setText(okText);
        builder.addCancelAction().setText(cancelText);
        builder.setButtonsAlignment(SwingConstants.CENTER);
        return  builder.show() == 0;
    }
}
