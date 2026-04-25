package com.example.netcdfviewer.basemap;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * 底图 HTTP 代理解析器。
 * Windows 桌面环境下，Java 默认不会稳定读取当前用户代理。
 */
final class BasemapHttpProxy {
    // 底图代理解析日志对象。
    private static final Logger logger = Logger.getLogger(BasemapHttpProxy.class.getName());
    // Windows 当前用户 Internet 设置注册表路径。
    private static final String WINDOWS_INTERNET_SETTINGS =
        "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings";
    // 注册表读取最长等待时间。
    private static final long REGISTRY_QUERY_TIMEOUT_SECONDS = 2L;
    // 未显式写端口时使用的 HTTP 代理端口。
    private static final int DEFAULT_PROXY_PORT = 80;

    private BasemapHttpProxy() {
    }

    /*
     * ========================================================================
     * 步骤1：读取系统代理选择器
     * ========================================================================
     * 目标：
     *   1) 优先读取 JVM 和环境变量代理
     *   2) Windows 下补读当前用户 Internet 代理
     */
    static Optional<ProxySelector> systemProxySelector() {
        logger.info("开始读取底图系统代理...");

        // 1.1 读取 JVM 显式代理参数。
        Optional<ProxySelector> jvmProxySelector = fromJvmProxyProperties();
        if (jvmProxySelector.isPresent()) {
            logger.info("底图系统代理读取完成, source=jvm");
            return jvmProxySelector;
        }

        // 1.2 读取常见环境变量代理。
        Optional<ProxySelector> environmentProxySelector = fromEnvironment(System.getenv());
        if (environmentProxySelector.isPresent()) {
            logger.info("底图系统代理读取完成, source=env");
            return environmentProxySelector;
        }

        // 1.3 Windows 下读取当前用户代理。
        if (isWindows()) {
            Optional<ProxySelector> windowsProxySelector = fromWindowsRegistry();
            if (windowsProxySelector.isPresent()) {
                logger.info("底图系统代理读取完成, source=windows-registry");
                return windowsProxySelector;
            }
        }

        logger.info("底图系统代理读取完成, source=direct");
        return Optional.empty();
    }

    /*
     * ========================================================================
     * 步骤2：解析 Windows 注册表代理快照
     * ========================================================================
     * 目标：
     *   1) 读取 ProxyEnable、ProxyServer、ProxyOverride
     *   2) 生成带绕过规则的 ProxySelector
     */
    static Optional<ProxySelector> fromWindowsRegistrySnapshot(String snapshot) {
        logger.info("开始解析 Windows 注册表代理快照...");

        // 2.1 解析注册表输出中的键值。
        Map<String, String> values = parseRegistryValues(snapshot);

        // 2.2 未启用代理时保持直连。
        if (!isProxyEnabled(values.get("ProxyEnable"))) {
            logger.info("Windows 注册表代理快照解析完成, enabled=false");
            return Optional.empty();
        }

        // 2.3 读取代理地址和绕过规则。
        Optional<ProxySelector> proxySelector = fromProxyServer(
            values.get("ProxyServer"),
            values.getOrDefault("ProxyOverride", "")
        );

        logger.info(() -> "Windows 注册表代理快照解析完成, hasProxy=" + proxySelector.isPresent());
        return proxySelector;
    }

    /*
     * ========================================================================
     * 步骤3：读取 JVM 显式代理
     * ========================================================================
     * 目标：
     *   1) 支持 -Dhttps.proxyHost 和 -Dhttp.proxyHost
     *   2) 支持 nonProxyHosts 绕过规则
     */
    private static Optional<ProxySelector> fromJvmProxyProperties() {
        logger.info("开始读取 JVM 显式代理...");

        // 3.1 读取 HTTPS 代理，优先服务在线瓦片。
        Optional<ProxyEndpoint> httpsEndpoint = proxyEndpointFromHostPortProperties(
            "https.proxyHost",
            "https.proxyPort"
        );

        // 3.2 读取 HTTP 代理，兼容自定义 HTTP 瓦片源。
        Optional<ProxyEndpoint> httpEndpoint = proxyEndpointFromHostPortProperties(
            "http.proxyHost",
            "http.proxyPort"
        );

        // 3.3 无 JVM 代理时返回空。
        if (httpsEndpoint.isEmpty() && httpEndpoint.isEmpty()) {
            logger.info("JVM 显式代理读取完成, hasProxy=false");
            return Optional.empty();
        }

        // 3.4 组装按协议区分的代理表。
        Map<String, ProxyEndpoint> endpoints = new HashMap<>();
        httpsEndpoint.ifPresent(endpoint -> endpoints.put("https", endpoint));
        httpEndpoint.ifPresent(endpoint -> endpoints.put("http", endpoint));

        // 3.5 读取 JVM 绕过规则。
        String bypass = firstNonBlank(
            System.getProperty("http.nonProxyHosts"),
            System.getProperty("https.nonProxyHosts")
        ).orElse("");

        logger.info("JVM 显式代理读取完成, hasProxy=true");
        return Optional.of(new BypassAwareProxySelector(endpoints, parseBypassRules(bypass)));
    }

    /*
     * ========================================================================
     * 步骤4：读取环境变量代理
     * ========================================================================
     * 目标：
     *   1) 支持 HTTPS_PROXY、HTTP_PROXY
     *   2) 支持 NO_PROXY 绕过规则
     */
    private static Optional<ProxySelector> fromEnvironment(Map<String, String> environment) {
        logger.info("开始读取环境变量代理...");

        // 4.1 读取 HTTPS 代理变量。
        Optional<String> httpsProxy = firstNonBlank(
            environment.get("HTTPS_PROXY"),
            environment.get("https_proxy")
        );

        // 4.2 读取 HTTP 代理变量。
        Optional<String> httpProxy = firstNonBlank(
            environment.get("HTTP_PROXY"),
            environment.get("http_proxy")
        );

        // 4.3 无环境变量代理时返回空。
        if (httpsProxy.isEmpty() && httpProxy.isEmpty()) {
            logger.info("环境变量代理读取完成, hasProxy=false");
            return Optional.empty();
        }

        // 4.4 组装按协议区分的代理表。
        Map<String, ProxyEndpoint> endpoints = new HashMap<>();
        httpsProxy.flatMap(BasemapHttpProxy::parseProxyEndpoint)
            .ifPresent(endpoint -> endpoints.put("https", endpoint));
        httpProxy.flatMap(BasemapHttpProxy::parseProxyEndpoint)
            .ifPresent(endpoint -> endpoints.put("http", endpoint));
        if (endpoints.isEmpty()) {
            logger.info("环境变量代理读取完成, hasProxy=false");
            return Optional.empty();
        }

        // 4.5 读取环境变量绕过规则。
        String bypass = firstNonBlank(environment.get("NO_PROXY"), environment.get("no_proxy")).orElse("");

        logger.info("环境变量代理读取完成, hasProxy=true");
        return Optional.of(new BypassAwareProxySelector(endpoints, parseBypassRules(bypass)));
    }

    /*
     * ========================================================================
     * 步骤5：读取 Windows 当前用户注册表
     * ========================================================================
     * 目标：
     *   1) 调用 reg query 读取当前用户 Internet 设置
     *   2) 解析系统代理给 Java HttpClient 使用
     */
    private static Optional<ProxySelector> fromWindowsRegistry() {
        logger.info("开始读取 Windows 当前用户代理注册表...");

        Process process = null;
        try {
            // 5.1 启动 reg query 读取 Internet Settings。
            process = new ProcessBuilder("reg", "query", WINDOWS_INTERNET_SETTINGS)
                .redirectErrorStream(true)
                .start();

            // 5.2 限制注册表查询等待时间。
            if (!process.waitFor(REGISTRY_QUERY_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                logger.info("Windows 当前用户代理注册表读取完成, timeout=true");
                return Optional.empty();
            }

            // 5.3 查询失败时保持直连。
            int exitCode = process.exitValue();
            if (exitCode != 0) {
                logger.info(() -> "Windows 当前用户代理注册表读取完成, exitCode=" + exitCode);
                return Optional.empty();
            }

            // 5.4 读取并解析注册表输出。
            String snapshot = new String(process.getInputStream().readAllBytes(), Charset.defaultCharset());
            Optional<ProxySelector> proxySelector = fromWindowsRegistrySnapshot(snapshot);

            logger.info(() -> "Windows 当前用户代理注册表读取完成, hasProxy=" + proxySelector.isPresent());
            return proxySelector;
        } catch (IOException exception) {
            logger.info(() -> "Windows 当前用户代理注册表读取失败, reason=" + exception.getMessage());
            return Optional.empty();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            logger.info("Windows 当前用户代理注册表读取被中断。");
            return Optional.empty();
        }
    }

    private static Optional<ProxySelector> fromProxyServer(String proxyServer, String bypassRules) {
        if (proxyServer == null || proxyServer.isBlank()) {
            return Optional.empty();
        }
        Map<String, ProxyEndpoint> endpoints = parseProxyServer(proxyServer);
        if (endpoints.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new BypassAwareProxySelector(endpoints, parseBypassRules(bypassRules)));
    }

    private static Map<String, String> parseRegistryValues(String snapshot) {
        Map<String, String> values = new HashMap<>();
        if (snapshot == null || snapshot.isBlank()) {
            return values;
        }
        snapshot.lines()
            .map(String::trim)
            .filter(line -> !line.isBlank())
            .filter(line -> !line.startsWith("HKEY_"))
            .forEach(line -> {
                String[] parts = line.split("\\s+", 3);
                if (parts.length == 3) {
                    values.put(parts[0], parts[2].trim());
                }
            });
        return values;
    }

    private static boolean isProxyEnabled(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if ("0x1".equals(normalized) || "1".equals(normalized)) {
            return true;
        }
        try {
            return Integer.decode(normalized) == 1;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private static Map<String, ProxyEndpoint> parseProxyServer(String proxyServer) {
        Map<String, ProxyEndpoint> endpoints = new HashMap<>();
        String value = proxyServer.trim();
        if (value.contains("=")) {
            for (String part : value.split(";")) {
                String[] pair = part.split("=", 2);
                if (pair.length == 2) {
                    parseProxyEndpoint(pair[1]).ifPresent(endpoint ->
                        endpoints.put(pair[0].trim().toLowerCase(Locale.ROOT), endpoint)
                    );
                }
            }
            return endpoints;
        }
        parseProxyEndpoint(value).ifPresent(endpoint -> endpoints.put("*", endpoint));
        return endpoints;
    }

    private static Optional<ProxyEndpoint> proxyEndpointFromHostPortProperties(String hostProperty, String portProperty) {
        String host = System.getProperty(hostProperty);
        if (host == null || host.isBlank()) {
            return Optional.empty();
        }
        int port = parsePort(System.getProperty(portProperty)).orElse(DEFAULT_PROXY_PORT);
        return Optional.of(new ProxyEndpoint(host.trim(), port));
    }

    private static Optional<ProxyEndpoint> parseProxyEndpoint(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String decodedValue = URLDecoder.decode(value.trim(), StandardCharsets.UTF_8);
        String uriText = decodedValue.contains("://") ? decodedValue : "http://" + decodedValue;
        try {
            URI uri = URI.create(uriText);
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                return Optional.empty();
            }
            int port = uri.getPort() > 0 ? uri.getPort() : DEFAULT_PROXY_PORT;
            return Optional.of(new ProxyEndpoint(host, port));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private static Optional<Integer> parsePort(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Integer.parseInt(value.trim()));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private static List<String> parseBypassRules(String rules) {
        List<String> parsedRules = new ArrayList<>();
        if (rules == null || rules.isBlank()) {
            return parsedRules;
        }
        for (String rule : rules.split("[;,|]")) {
            String trimmedRule = rule.trim();
            if (!trimmedRule.isBlank()) {
                parsedRules.add(trimmedRule);
            }
        }
        return parsedRules;
    }

    private static boolean shouldBypass(String host, List<String> bypassRules) {
        if (host == null || host.isBlank()) {
            return true;
        }
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        for (String rule : bypassRules) {
            if (matchesBypassRule(normalizedHost, rule.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesBypassRule(String host, String rule) {
        if ("<local>".equals(rule)) {
            return !host.contains(".");
        }
        String normalizedRule = rule;
        int portIndex = normalizedRule.lastIndexOf(':');
        if (portIndex > -1 && normalizedRule.indexOf(']') < portIndex) {
            normalizedRule = normalizedRule.substring(0, portIndex);
        }
        String regex = Pattern.quote(normalizedRule).replace("*", "\\E.*\\Q");
        return Pattern.matches(regex, host);
    }

    private static Optional<String> firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return Optional.of(value.trim());
            }
        }
        return Optional.empty();
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private record ProxyEndpoint(String host, int port) {
        Proxy toProxy() {
            return new Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved(host, port));
        }
    }

    private static final class BypassAwareProxySelector extends ProxySelector {
        // 按协议保存的代理地址。
        private final Map<String, ProxyEndpoint> endpoints;
        // 代理绕过规则。
        private final List<String> bypassRules;

        private BypassAwareProxySelector(Map<String, ProxyEndpoint> endpoints, List<String> bypassRules) {
            this.endpoints = Map.copyOf(endpoints);
            this.bypassRules = List.copyOf(bypassRules);
        }

        @Override
        public List<Proxy> select(URI uri) {
            if (uri == null) {
                throw new IllegalArgumentException("底图瓦片 URI 不能为空。");
            }
            String host = uri.getHost();
            if (shouldBypass(host, bypassRules)) {
                return List.of(Proxy.NO_PROXY);
            }
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            ProxyEndpoint endpoint = endpoints.getOrDefault(scheme, endpoints.get("*"));
            if (endpoint == null) {
                return List.of(Proxy.NO_PROXY);
            }
            return List.of(endpoint.toProxy());
        }

        @Override
        public void connectFailed(URI uri, java.net.SocketAddress socketAddress, IOException exception) {
            logger.info(() -> "底图代理连接失败, uri=" + uri + ", reason=" + exception.getMessage());
        }
    }
}
