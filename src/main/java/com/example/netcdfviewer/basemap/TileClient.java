package com.example.netcdfviewer.basemap;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

@FunctionalInterface
public interface TileClient {
    BufferedImage fetch(String url);

    static TileClient http(TileCache cache) {
        return new HttpTileClient(cache);
    }

    final class HttpTileClient implements TileClient {
        private static final Logger logger = Logger.getLogger(HttpTileClient.class.getName());
        private final TileCache cache;
        private final ExecutorService executor = Executors.newCachedThreadPool(runnable -> {
            Thread thread = new Thread(runnable, "tile-http-" + System.nanoTime());
            thread.setDaemon(true);
            return thread;
        });
        private HttpClient client;

        private HttpTileClient(TileCache cache) {
            this.cache = cache;
        }

        /*
         * ========================================================================
         * 步骤1：获取在线瓦片
         * ========================================================================
         * 目标：
         *   1) 优先复用本地缓存
         *   2) 缓存未命中时用 HTTP 下载并解码
         */
        @Override
        public BufferedImage fetch(String url) {
            logger.info(() -> "开始获取在线瓦片, url=" + url);
            try {
                // 1.1 优先从缓存读取，避免重复访问在线服务。
                if (cache != null) {
                    BufferedImage cached = cache.get(url).orElse(null);
                    if (cached != null) {
                        logger.info("在线瓦片获取完成, source=cache");
                        return cached;
                    }
                }

                // 1.2 缓存未命中时发起 HTTP 请求。
                HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(8))
                    .header("User-Agent", "NetCDFViewer/1.1")
                    .GET()
                    .build();
                HttpResponse<byte[]> response = client().send(request, HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    logger.info(() -> "在线瓦片获取完成, source=network, status=" + response.statusCode());
                    return null;
                }

                // 1.3 解码成功后写回缓存。
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(response.body()));
                if (image != null && cache != null) {
                    cache.put(url, image);
                }
                logger.info(() -> "在线瓦片获取完成, source=network, decoded=" + (image != null));
                return image;
            } catch (Exception exception) {
                logger.info(() -> "在线瓦片获取完成, source=error, reason=" + exception.getMessage());
                return null;
            }
        }

        private synchronized HttpClient client() {
            if (client == null) {
                client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .executor(executor)
                    .build();
            }
            return client;
        }
    }
}
