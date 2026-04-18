package com.example.netcdfviewer.render;

import com.example.netcdfviewer.model.MeshData;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.logging.Logger;

final class TriangleSpatialIndexCache {
    private static final Logger logger = Logger.getLogger(TriangleSpatialIndexCache.class.getName());
    private static final Map<MeshData, TriangleSpatialIndex> CACHE = new WeakHashMap<>();

    private TriangleSpatialIndexCache() {
    }

    /*
     * ========================================================================
     * 步骤1：获取三角网空间索引缓存
     * ========================================================================
     * 目标：
     *   1) 同一个 MeshData 只构建一次空间索引
     *   2) 在多次采样和查询之间复用索引对象
     * 操作要点：
     *   1) 使用弱引用缓存避免长期持有旧数据集
     *   2) 整个获取流程做同步，保证并发安全
     */
    static TriangleSpatialIndex get(MeshData mesh) {
        logger.info(() -> "开始获取三角网空间索引缓存, triangleCount="
            + (mesh == null ? 0 : mesh.triangleCount()));

        synchronized (CACHE) {
            // 1.1 网格为空时直接退化为即时构建结果。
            if (mesh == null) {
                TriangleSpatialIndex index = TriangleSpatialIndex.build(null);
                logger.info("三角网空间索引缓存获取结束, cached=false");
                return index;
            }

            // 1.2 命中缓存时直接返回，未命中时构建后写回。
            TriangleSpatialIndex index = CACHE.get(mesh);
            if (index == null) {
                index = TriangleSpatialIndex.build(mesh);
                CACHE.put(mesh, index);
                logger.info("三角网空间索引缓存获取结束, cached=false");
                return index;
            }

            logger.info("三角网空间索引缓存获取结束, cached=true");
            return index;
        }
    }
}
