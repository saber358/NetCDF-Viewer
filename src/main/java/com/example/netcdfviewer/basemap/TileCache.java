package com.example.netcdfviewer.basemap;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

public final class TileCache {
    private static final Logger logger = Logger.getLogger(TileCache.class.getName());
    private final Path cacheDirectory;
    private final Map<String, BufferedImage> memoryCache;

    public TileCache(Path cacheDirectory, int maxEntries) {
        this.cacheDirectory = cacheDirectory;
        this.memoryCache = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, BufferedImage> eldest) {
                return size() > Math.max(1, maxEntries);
            }
        };
    }

    /*
     * ========================================================================
     * 步骤1：读取瓦片缓存
     * ========================================================================
     * 目标：
     *   1) 优先从内存读取
     *   2) 内存未命中时回退到磁盘缓存
     */
    public synchronized Optional<BufferedImage> get(String url) {
        logger.info(() -> "开始读取瓦片缓存, url=" + url);

        // 1.1 优先读取内存 LRU 缓存。
        BufferedImage memoryImage = memoryCache.get(url);
        if (memoryImage != null) {
            logger.info("瓦片缓存读取完成, source=memory");
            return Optional.of(memoryImage);
        }

        // 1.2 内存未命中时读取磁盘文件。
        Path path = cachePath(url);
        if (!Files.isRegularFile(path)) {
            logger.info("瓦片缓存读取完成, source=none");
            return Optional.empty();
        }
        try {
            // 1.3 解码成功后回填内存缓存。
            BufferedImage diskImage = ImageIO.read(path.toFile());
            if (diskImage == null) {
                logger.info("瓦片缓存读取完成, source=invalid");
                return Optional.empty();
            }
            memoryCache.put(url, diskImage);
            logger.info("瓦片缓存读取完成, source=disk");
            return Optional.of(diskImage);
        } catch (IOException exception) {
            logger.info(() -> "瓦片缓存读取完成, source=error, reason=" + exception.getMessage());
            return Optional.empty();
        }
    }

    /*
     * ========================================================================
     * 步骤2：写入瓦片缓存
     * ========================================================================
     * 目标：
     *   1) 同步写入内存缓存
     *   2) 将可解码图片持久化为 PNG 文件
     */
    public synchronized void put(String url, BufferedImage image) throws IOException {
        logger.info(() -> "开始写入瓦片缓存, url=" + url);

        // 2.1 写入内存缓存。
        memoryCache.put(url, image);

        // 2.2 写入磁盘 PNG 缓存。
        Path path = cachePath(url);
        Files.createDirectories(path.getParent());
        ImageIO.write(image, "png", path.toFile());

        logger.info(() -> "瓦片缓存写入完成, path=" + path);
    }

    private Path cachePath(String url) {
        String hash = sha256(url);
        return cacheDirectory.resolve(hash.substring(0, 2)).resolve(hash + ".png");
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder text = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                text.append(String.format("%02x", item));
            }
            return text.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 Java 运行时不支持 SHA-256。", exception);
        }
    }
}
