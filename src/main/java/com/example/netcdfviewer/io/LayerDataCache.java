package com.example.netcdfviewer.io;

import com.example.netcdfviewer.model.VariableInfo;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * 变量层数据缓存。
 * 按数据集、变量名和层号缓存已经读取的 double 数组，减少渲染时重复打开 NetCDF 文件。
 */
public final class LayerDataCache {
    private static final Logger logger = Logger.getLogger(LayerDataCache.class.getName());
    private final long maxBytes;
    private final Map<Key, Entry> entries = new LinkedHashMap<>(16, 0.75f, true);
    private long currentBytes;

    public LayerDataCache(long maxBytes) {
        this.maxBytes = Math.max(Double.BYTES, maxBytes);
    }

    /*
     * ========================================================================
     * 步骤1：读取或加载变量层数据
     * ========================================================================
     * 目标：
     *   1) 命中缓存时直接返回已有数组
     *   2) 未命中时调用加载器读取数据并写入缓存
     * 操作要点：
     *   1) 缓存键由源文件、变量名和层号组成
     *   2) 单层数据超过缓存上限时直接返回但不缓存
     */
    public double[] getOrLoad(ParsedDataset dataset, VariableInfo variableInfo, int layerIndex, LayerLoader loader)
        throws IOException {
        logger.info(() -> "开始读取变量层缓存, variable=" + variableInfo.name() + ", layerIndex=" + layerIndex);

        // 1.1 构造当前读取请求的缓存键。
        Key key = Key.from(dataset, variableInfo, layerIndex);

        // 1.2 缓存命中时直接返回。
        synchronized (this) {
            Entry cachedEntry = entries.get(key);
            if (cachedEntry != null) {
                logger.info(() -> "变量层缓存读取完成, source=cache, variable=" + variableInfo.name());
                return cachedEntry.values();
            }
        }

        // 1.3 缓存未命中时读取真实数据。
        double[] values = loader.load();
        long bytes = estimateBytes(values);
        if (bytes > maxBytes) {
            logger.info(() -> "变量层缓存读取完成, source=loader, cached=false, bytes=" + bytes);
            return values;
        }

        // 1.4 写入缓存并按 LRU 规则淘汰旧数据。
        synchronized (this) {
            Entry concurrentEntry = entries.get(key);
            if (concurrentEntry != null) {
                logger.info(() -> "变量层缓存读取完成, source=cache, variable=" + variableInfo.name());
                return concurrentEntry.values();
            }
            entries.put(key, new Entry(values, bytes));
            currentBytes += bytes;
            evictIfNeeded();
        }

        logger.info(() -> "变量层缓存读取完成, source=loader, cached=true, currentBytes=" + currentBytes);
        return values;
    }

    /*
     * ========================================================================
     * 步骤2：移除指定数据集缓存
     * ========================================================================
     * 目标：
     *   1) 数据集被移除时释放对应层数据
     *   2) 保留其他数据集的缓存内容
     */
    public synchronized void removeSource(Path sourcePath) {
        logger.info(() -> "开始移除数据集层缓存, sourcePath=" + sourcePath);

        // 2.1 规范化目标源文件路径。
        Path normalizedSourcePath = normalize(sourcePath);

        // 2.2 遍历删除同一数据源下的缓存项。
        Iterator<Map.Entry<Key, Entry>> iterator = entries.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Key, Entry> entry = iterator.next();
            if (entry.getKey().sourcePath().equals(normalizedSourcePath)) {
                currentBytes -= entry.getValue().bytes();
                iterator.remove();
            }
        }

        logger.info(() -> "数据集层缓存移除完成, currentBytes=" + currentBytes);
    }

    /*
     * ========================================================================
     * 步骤3：清空全部层缓存
     * ========================================================================
     * 目标：
     *   1) 会话重置时释放所有缓存数组
     *   2) 同步归零容量计数
     */
    public synchronized void clear() {
        logger.info("开始清空变量层缓存...");

        // 3.1 清空缓存映射。
        entries.clear();
        // 3.2 重置当前容量。
        currentBytes = 0L;

        logger.info("变量层缓存清空完成。");
    }

    public synchronized int size() {
        return entries.size();
    }

    public synchronized long currentBytes() {
        return currentBytes;
    }

    /*
     * ========================================================================
     * 步骤4：按容量上限淘汰旧缓存
     * ========================================================================
     * 目标：
     *   1) 保证缓存总字节数不超过上限
     *   2) 优先淘汰最久未使用的层数据
     */
    private void evictIfNeeded() {
        logger.info(() -> "开始检查变量层缓存容量, currentBytes=" + currentBytes + ", maxBytes=" + maxBytes);

        // 4.1 按访问顺序从最旧条目开始淘汰。
        Iterator<Map.Entry<Key, Entry>> iterator = entries.entrySet().iterator();
        while (currentBytes > maxBytes && iterator.hasNext()) {
            Map.Entry<Key, Entry> eldest = iterator.next();
            currentBytes -= eldest.getValue().bytes();
            iterator.remove();
        }

        logger.info(() -> "变量层缓存容量检查完成, currentBytes=" + currentBytes);
    }

    private static long estimateBytes(double[] values) {
        return values == null ? 0L : values.length * (long) Double.BYTES;
    }

    private static Path normalize(Path path) {
        return path == null ? Path.of("") : path.toAbsolutePath().normalize();
    }

    @FunctionalInterface
    public interface LayerLoader {
        double[] load() throws IOException;
    }

    private record Key(Path sourcePath, String variableName, int layerIndex) {
        private static Key from(ParsedDataset dataset, VariableInfo variableInfo, int layerIndex) {
            return new Key(normalize(dataset.sourcePath()), variableInfo.name(), layerIndex);
        }
    }

    private record Entry(double[] values, long bytes) {
    }
}
