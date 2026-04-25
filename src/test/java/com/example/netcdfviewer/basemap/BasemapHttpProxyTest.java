package com.example.netcdfviewer.basemap;

import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.URI;
import java.util.Optional;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BasemapHttpProxyTest {
    // 底图代理测试日志对象。
    private static final Logger logger = Logger.getLogger(BasemapHttpProxyTest.class.getName());

    @Test
    void windowsRegistryProxyIsUsedForRemoteTilesAndBypassedForLocalTiles() {
        /*
         * ========================================================================
         * 步骤1：准备 Windows 当前用户代理快照
         * ========================================================================
         * 目标：
         *   1) 模拟 PowerShell 能访问 OSM 时使用的用户代理
         *   2) 保留 localhost 和 127.* 的直连规则
         */
        logger.info("开始准备 Windows 当前用户代理快照...");

        // 1.1 构造 reg query 的关键输出内容。
        String registrySnapshot = """
            HKEY_CURRENT_USER\\Software\\Microsoft\\Windows\\CurrentVersion\\Internet Settings
                ProxyEnable    REG_DWORD    0x1
                ProxyServer    REG_SZ    127.0.0.1:7897
                ProxyOverride    REG_SZ    localhost;127.*;<local>
            """;

        logger.info("Windows 当前用户代理快照准备完成。");

        /*
         * ========================================================================
         * 步骤2：解析代理选择器
         * ========================================================================
         * 目标：
         *   1) 从注册表快照提取 HTTP 代理地址
         *   2) 生成可供 HttpClient 使用的 ProxySelector
         */
        logger.info("开始解析代理选择器...");

        // 2.1 解析注册表快照。
        Optional<ProxySelector> proxySelector = BasemapHttpProxy.fromWindowsRegistrySnapshot(registrySnapshot);

        // 2.2 校验代理选择器已生成。
        assertTrue(proxySelector.isPresent());

        logger.info("代理选择器解析完成。");

        /*
         * ========================================================================
         * 步骤3：校验远端瓦片走代理
         * ========================================================================
         * 目标：
         *   1) OSM 远端瓦片使用用户代理
         *   2) 避免 Java 直连被错误 DNS 或网络策略阻断
         */
        logger.info("开始校验远端瓦片走代理...");

        // 3.1 读取远端 OSM 瓦片的代理结果。
        Proxy remoteProxy = proxySelector.get()
            .select(URI.create("https://tile.openstreetmap.org/1/1/1.png"))
            .get(0);

        // 3.2 校验代理类型和端口。
        InetSocketAddress remoteAddress = (InetSocketAddress) remoteProxy.address();
        assertEquals(Proxy.Type.HTTP, remoteProxy.type());
        assertEquals("127.0.0.1", remoteAddress.getHostString());
        assertEquals(7897, remoteAddress.getPort());

        logger.info("远端瓦片代理校验完成。");

        /*
         * ========================================================================
         * 步骤4：校验本地瓦片保持直连
         * ========================================================================
         * 目标：
         *   1) 自定义 localhost 底图不被错误转发到代理
         *   2) 127.* 本地服务仍然直连
         */
        logger.info("开始校验本地瓦片保持直连...");

        // 4.1 校验 localhost 直连。
        Proxy localhostProxy = proxySelector.get()
            .select(URI.create("http://localhost:8080/1/1/1.png"))
            .get(0);
        assertEquals(Proxy.NO_PROXY, localhostProxy);

        // 4.2 校验 127.* 直连。
        Proxy loopbackProxy = proxySelector.get()
            .select(URI.create("http://127.0.0.1:8080/1/1/1.png"))
            .get(0);
        assertEquals(Proxy.NO_PROXY, loopbackProxy);

        logger.info("本地瓦片直连校验完成。");
    }
}
