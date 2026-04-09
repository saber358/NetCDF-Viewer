package com.example.netcdfviewer;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * 统一维护应用元数据。
 * 这样可以保证界面、打包脚本和关于窗口使用同一套信息。
 */
public final class AppMetadata {
    // 过滤后的应用元数据资源路径。
    private static final String METADATA_RESOURCE = "/app-metadata.properties";
    // 从类路径读取的元数据。
    private static final Properties METADATA = loadMetadata();
    // 应用显示名称。
    public static final String APP_NAME = metadataValue("app.name", "NetCDF Viewer");
    // 当前应用版本号。
    public static final String VERSION = metadataValue("app.version", "0.0.0");
    // 作者名称。
    public static final String AUTHOR_NAME = "lwj";
    // 作者联系邮箱。
    public static final String AUTHOR_EMAIL = "2762692204@qq.com";
    // 用于界面底部直接展示的作者信息文本。
    public static final String AUTHOR_LABEL = "Author: " + AUTHOR_NAME + " | " + AUTHOR_EMAIL;
    // 用于关于窗口和文档展示的简短描述。
    public static final String DESCRIPTION = metadataValue(
        "app.description",
        "Desktop viewer for unstructured triangle NetCDF datasets"
    );

    private AppMetadata() {
        // 工具类不允许被实例化。
    }

    private static Properties loadMetadata() {
        Properties properties = new Properties();
        try (InputStream stream = AppMetadata.class.getResourceAsStream(METADATA_RESOURCE)) {
            if (stream != null) {
                properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            }
        } catch (IOException ignored) {
            // 元数据资源读取失败时使用代码中的回退值继续运行。
        }
        return properties;
    }

    private static String metadataValue(String key, String fallback) {
        String value = METADATA.getProperty(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }
}
