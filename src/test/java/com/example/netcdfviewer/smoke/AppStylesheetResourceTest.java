package com.example.netcdfviewer.smoke;

import atlantafx.base.theme.NordLight;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppStylesheetResourceTest {
    private static final Logger logger = Logger.getLogger(AppStylesheetResourceTest.class.getName());
    private static final Pattern COLOR_DEFINITION_PATTERN = Pattern.compile("(?m)^\\s*(-color-[a-zA-Z0-9_-]+)\\s*:");
    private static final Pattern COLOR_TOKEN_PATTERN = Pattern.compile("-color-[a-zA-Z0-9_-]+");

    @Test
    void customStylesheetUsesDefinedAtlantaFxColorVariables() throws Exception {
        /*
         * ========================================================================
         * 步骤1：读取样式资源
         * ========================================================================
         * 数据源：
         *   1) 应用自定义 app.css
         *   2) AtlantaFX NordLight 主题 css
         */
        logger.info("开始读取样式资源...");

        // 1.1 读取应用自定义样式。
        String appCss = readCssResource(AppStylesheetResourceTest.class.getResource("/css/app.css"));

        // 1.2 读取 AtlantaFX 主题样式。
        String nordLightCss = readCssResource(NordLight.class.getResource("nord-light.css"));

        logger.info("样式资源读取完成。");

        /*
         * ========================================================================
         * 步骤2：校验颜色变量
         * ========================================================================
         * 目标：
         *   1) 收集主题和自定义样式中的颜色定义
         *   2) 确认 app.css 没有引用未定义颜色变量
         */
        logger.info("开始校验颜色变量...");

        // 2.1 合并主题和应用样式中的颜色变量定义。
        Set<String> definedColors = new HashSet<>();
        definedColors.addAll(extractColorDefinitions(nordLightCss));
        definedColors.addAll(extractColorDefinitions(appCss));

        // 2.2 找出自定义样式中未定义的颜色变量。
        Set<String> undefinedColors = extractColorTokens(appCss);
        undefinedColors.removeAll(definedColors);

        assertTrue(undefinedColors.isEmpty(), "Undefined CSS color variables: " + undefinedColors);

        logger.info("颜色变量校验完成。");
    }

    private String readCssResource(URL resourceUrl) throws Exception {
        assertNotNull(resourceUrl);

        try (InputStream inputStream = resourceUrl.openStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private Set<String> extractColorDefinitions(String css) {
        Set<String> definitions = new HashSet<>();
        Matcher matcher = COLOR_DEFINITION_PATTERN.matcher(css);
        while (matcher.find()) {
            definitions.add(matcher.group(1));
        }
        return definitions;
    }

    private Set<String> extractColorTokens(String css) {
        Set<String> tokens = new HashSet<>();
        Matcher matcher = COLOR_TOKEN_PATTERN.matcher(css);
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        return tokens;
    }
}
