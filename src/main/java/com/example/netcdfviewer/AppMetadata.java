package com.example.netcdfviewer;

/**
 * 统一维护应用元数据。
 * 这样可以保证界面、打包脚本和关于窗口使用同一套信息。
 */
public final class AppMetadata {
    // 应用显示名称。
    public static final String APP_NAME = "NetCDF Viewer";
    // 当前应用版本号。
    public static final String VERSION = "1.0.2";
    // 作者名称。
    public static final String AUTHOR_NAME = "lwj";
    // 作者联系邮箱。
    public static final String AUTHOR_EMAIL = "2762692204@qq.com";
    // 用于界面底部直接展示的作者信息文本。
    public static final String AUTHOR_LABEL = "Author: " + AUTHOR_NAME + " | " + AUTHOR_EMAIL;
    // 用于关于窗口和文档展示的简短描述。
    public static final String DESCRIPTION = "Desktop viewer for unstructured-triangle NetCDF planar visualization";

    private AppMetadata() {
        // 工具类不允许被实例化。
    }
}
