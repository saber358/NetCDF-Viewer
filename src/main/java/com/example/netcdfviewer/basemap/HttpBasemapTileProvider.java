package com.example.netcdfviewer.basemap;

import com.example.netcdfviewer.AppMetadata;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * HTTP XYZ 瓦片提供器。
 * 优先读取本地缓存，缓存未命中时再从在线底图服务下载。
 */
public final class HttpBasemapTileProvider implements BasemapTileProvider {
    // HTTP 瓦片提供器日志对象。
    private static final Logger logger = Logger.getLogger(HttpBasemapTileProvider.class.getName());
    // 瓦片请求超时时间。
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(8);
    // 在线瓦片请求标识。
    private static final String USER_AGENT = "NetCDFViewer/" + AppMetadata.VERSION
        + " (contact: " + AppMetadata.AUTHOR_EMAIL + ")";

    // HTTP 客户端。
    private final HttpClient httpClient;
    // 本地缓存根目录。
    private final Path cacheDirectory;

    public HttpBasemapTileProvider(Path cacheDirectory) {
        this(createHttpClient(), cacheDirectory);
    }

    HttpBasemapTileProvider(HttpClient httpClient, Path cacheDirectory) {
        this.httpClient = httpClient;
        this.cacheDirectory = cacheDirectory;
    }

    /*
     * ========================================================================
     * 步骤1：创建底图 HTTP 客户端
     * ========================================================================
     * 目标：
     *   1) 配置在线瓦片请求超时
     *   2) 使用系统代理访问 OSM 等在线底图
     */
    private static HttpClient createHttpClient() {
        logger.info("开始创建底图 HTTP 客户端...");

        // 1.1 创建带连接超时的 HTTP 客户端构造器。
        HttpClient.Builder builder = HttpClient.newBuilder().connectTimeout(REQUEST_TIMEOUT);

        // 1.2 读取系统代理，存在时交给 HttpClient 使用。
        BasemapHttpProxy.systemProxySelector().ifPresent(builder::proxy);

        logger.info("底图 HTTP 客户端创建完成。");
        return builder.build();
    }

    /*
     * ========================================================================
     * 步骤2：获取底图瓦片
     * ========================================================================
     * 目标：
     *   1) 优先使用磁盘缓存
     *   2) 缓存未命中时下载并写入缓存
     */
    @Override
    public Optional<BufferedImage> tile(BasemapLayer layer, int zoom, int tileX, int tileY) throws IOException {
        logger.info(() -> "开始获取底图瓦片, layer=" + layer.name() + ", zoom=" + zoom + ", tileX=" + tileX + ", tileY=" + tileY);

        // 2.1 计算当前瓦片缓存路径。
        Path cachePath = cachePath(layer, zoom, tileX, tileY);

        // 2.2 缓存命中时直接读取本地图像。
        if (Files.isRegularFile(cachePath)) {
            BufferedImage cachedImage = ImageIO.read(cachePath.toFile());
            if (cachedImage != null) {
                logger.info("底图瓦片获取完成, source=cache");
                return Optional.of(cachedImage);
            }
        }

        // 2.3 缓存未命中时下载远端瓦片。
        Optional<BufferedImage> downloadedImage = downloadTile(layer, zoom, tileX, tileY);
        if (downloadedImage.isEmpty()) {
            logger.info("底图瓦片获取完成, source=none");
            return Optional.empty();
        }

        // 2.4 下载成功后尽量写入缓存，缓存失败不阻断渲染。
        writeCacheQuietly(cachePath, downloadedImage.orElseThrow());

        logger.info("底图瓦片获取完成, source=network");
        return downloadedImage;
    }

    /*
     * ========================================================================
     * 步骤3：下载远端瓦片
     * ========================================================================
     * 目标：
     *   1) 通过 HTTP 读取瓦片二进制
     *   2) 只接受可解析的图片响应
     */
    private Optional<BufferedImage> downloadTile(BasemapLayer layer, int zoom, int tileX, int tileY) throws IOException {
        logger.info(() -> "开始下载底图瓦片, layer=" + layer.name());

        try {
            // 3.1 构造带 User-Agent 的 HTTP 请求。
            HttpRequest request = HttpRequest.newBuilder(URI.create(layer.tileUrl(zoom, tileX, tileY)))
                .timeout(REQUEST_TIMEOUT)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "image/png,image/jpeg,image/webp,*/*")
                .GET()
                .build();

            // 3.2 发送请求并读取字节响应。
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                logger.info(() -> "底图瓦片下载结束, status=" + response.statusCode());
                return Optional.empty();
            }

            // 3.3 将响应字节解析为图像。
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(response.body()));
            logger.info(() -> "底图瓦片下载结束, parsed=" + (image != null));
            return Optional.ofNullable(image);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("底图瓦片下载被中断。", exception);
        } catch (IllegalArgumentException exception) {
            throw new IOException("底图瓦片请求参数无效。", exception);
        }
    }

    /*
     * ========================================================================
     * 步骤4：写入瓦片缓存
     * ========================================================================
     * 目标：
     *   1) 将下载成功的瓦片保存到本地
     *   2) 缓存异常不影响主渲染
     */
    private void writeCacheQuietly(Path cachePath, BufferedImage image) {
        logger.info(() -> "开始写入底图瓦片缓存, path=" + cachePath);

        try {
            // 4.1 确保父目录存在。
            Files.createDirectories(cachePath.getParent());
            // 4.2 统一按 PNG 写入缓存。
            ImageIO.write(image, "png", cachePath.toFile());
            logger.info("底图瓦片缓存写入完成。");
        } catch (IOException exception) {
            logger.info(() -> "底图瓦片缓存写入失败, reason=" + exception.getMessage());
        }
    }

    private Path cachePath(BasemapLayer layer, int zoom, int tileX, int tileY) {
        return cacheDirectory
            .resolve(layer.cacheKey())
            .resolve(Integer.toString(zoom))
            .resolve(Integer.toString(tileX))
            .resolve(tileY + ".png");
    }
}
