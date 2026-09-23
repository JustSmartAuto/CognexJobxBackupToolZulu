package com.cognex.backup.util;

import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.swing.FontIcon;

import javax.swing.*;
import java.awt.Color;

/** ikonli 图标助手：颜色取组件当前前景色，随 FlatLaf 主题保持一致。 */
public final class Icons {

    private Icons() {
    }

    /** 给按钮/复选框等设置 ikonli 图标。 */
    public static void setIcon(AbstractButton button, Ikon ikon, int size) {
        FontIcon icon = FontIcon.of(ikon, size);
        icon.setIconColor(button.getForeground());
        button.setIcon(icon);
    }

    /** 给说明文本标签设置灰色 info 图标（颜色跟随标签前景色）。 */
    public static void setHintIcon(JLabel hint) {
        setLabelIcon(hint, org.kordamp.ikonli.fontawesome5.FontAwesomeSolid.INFO_CIRCLE, 14);
    }

    /** 给任意标签设置 ikonli 图标（颜色跟随标签前景色）。 */
    public static void setLabelIcon(JLabel label, Ikon ikon, int size) {
        FontIcon icon = FontIcon.of(ikon, size);
        Color color = label.getForeground();
        icon.setIconColor(color != null ? color : Color.GRAY);
        label.setIcon(icon);
    }
}
