package com.cognex.backup.util;

import javax.swing.ImageIcon;
import javax.swing.JFrame;
import java.awt.Image;
import java.awt.Toolkit;
import java.util.ArrayList;
import java.util.List;

/**
 * 应用图标加载与设置。
 * 图标资源位于 classpath /icons/ 下（src/main/resources/icons，随 fat jar 打包），
 * 提供多尺寸 PNG 供窗口标题栏/任务栏/Alt-Tab 按场景选用。
 *
 * 注意：java.awt.Taskbar 是 Java 9+ API，为保持 JRE 8 可运行，使用反射调用；
 * 窗口图标本身用 Java 6 起就有的 setIconImages 设置。
 */
public final class AppIcons {

    /** classpath 下的多尺寸图标（从小到大）。 */
    private static final String[] ICON_RESOURCES = {
            "/icons/app-icon-16.png",
            "/icons/app-icon-24.png",
            "/icons/app-icon-32.png",
            "/icons/app-icon-48.png",
            "/icons/app-icon-64.png",
            "/icons/app-icon-128.png",
            "/icons/app-icon-200.png"
    };

    private static List<Image> icons;

    private AppIcons() {
    }

    /** 加载（仅一次）并返回多尺寸图标列表；资源缺失时回退到 Toolkit 默认图标。 */
    public static synchronized List<Image> getIcons() {
        if (icons != null) {
            return icons;
        }
        List<Image> list = new ArrayList<>();
        Toolkit toolkit = Toolkit.getDefaultToolkit();
        for (String res : ICON_RESOURCES) {
            java.net.URL url = AppIcons.class.getResource(res);
            if (url != null) {
                // 用 ImageIcon 立即完成加载，避免窗口首次显示时图标空白
                list.add(new ImageIcon(toolkit.getImage(url)).getImage());
            }
        }
        icons = list;
        return icons;
    }

    /**
     * 设置窗口图标（多尺寸自动匹配），并在 Java 9+ 上尝试设置任务栏默认图标
     * （对未显式设置图标的次级窗口/对话框也生效）。
     */
    public static void applyTo(JFrame frame) {
        List<Image> list = getIcons();
        if (!list.isEmpty()) {
            frame.setIconImages(list);
        }
        setTaskbarIcon();
    }

    /** 反射调用 java.awt.Taskbar（Java 9+），Java 8 下静默跳过。 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void setTaskbarIcon() {
        try {
            Class<?> taskbarClass = Class.forName("java.awt.Taskbar");
            Class<?> featureClass = Class.forName("java.awt.Taskbar$Feature");
            Object iconImageFeature = Enum.valueOf((Class<Enum>) featureClass, "ICON_IMAGE");

            Object taskbar = taskbarClass.getMethod("getTaskbar").invoke(null);
            Boolean supported = (Boolean) taskbarClass
                    .getMethod("isSupported", featureClass)
                    .invoke(taskbar, iconImageFeature);
            if (!Boolean.TRUE.equals(supported)) {
                return;
            }
            List<Image> list = getIcons();
            if (list.isEmpty()) {
                return;
            }
            // 优先用不小于 64px 的尺寸作为任务栏图标，否则取最大一张
            Image best = list.get(list.size() - 1);
            for (Image img : list) {
                if (img.getWidth(null) >= 64) {
                    best = img;
                    break;
                }
            }
            taskbarClass.getMethod("setIconImage", Image.class).invoke(taskbar, best);
        } catch (Throwable ignored) {
            // Java 8 或平台不支持任务栏图标 API，忽略即可
        }
    }
}
