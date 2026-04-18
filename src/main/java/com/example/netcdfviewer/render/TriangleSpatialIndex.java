package com.example.netcdfviewer.render;

import com.example.netcdfviewer.model.MeshData;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public final class TriangleSpatialIndex {
    private static final Logger logger = Logger.getLogger(TriangleSpatialIndex.class.getName());
    private static final int TARGET_TRIANGLES_PER_BUCKET = 24;
    private static final int MAX_BUCKET_AXIS = 256;

    private final double minX;
    private final double maxX;
    private final double minY;
    private final double maxY;
    private final int bucketCountX;
    private final int bucketCountY;
    private final double bucketWidth;
    private final double bucketHeight;
    private final List<Integer>[] buckets;

    private TriangleSpatialIndex(
        double minX,
        double maxX,
        double minY,
        double maxY,
        int bucketCountX,
        int bucketCountY,
        double bucketWidth,
        double bucketHeight,
        List<Integer>[] buckets
    ) {
        this.minX = minX;
        this.maxX = maxX;
        this.minY = minY;
        this.maxY = maxY;
        this.bucketCountX = bucketCountX;
        this.bucketCountY = bucketCountY;
        this.bucketWidth = bucketWidth;
        this.bucketHeight = bucketHeight;
        this.buckets = buckets;
    }

    /*
     * ========================================================================
     * 步骤1：构建三角网空间索引
     * ========================================================================
     * 目标：
     *   1) 将三角形按包围盒分配到规则桶中
     *   2) 为后续点命中查询提供候选三角形集合
     * 操作要点：
     *   1) 先统计网格整体范围
     *   2) 再按三角形包围盒批量写入桶
     */
    public static TriangleSpatialIndex build(MeshData mesh) {
        logger.info(() -> "开始构建三角网空间索引, triangleCount="
            + (mesh == null ? 0 : mesh.triangleCount()));

        // 1.1 网格为空时返回退化索引，避免调用方做额外空判断。
        if (mesh == null || mesh.triangles().length == 0 || mesh.nodeCount() == 0) {
            @SuppressWarnings("unchecked")
            List<Integer>[] emptyBuckets = (List<Integer>[]) new List[1];
            emptyBuckets[0] = List.of();
            logger.info("三角网空间索引构建结束, bucketCountX=1, bucketCountY=1");
            return new TriangleSpatialIndex(0.0, 0.0, 0.0, 0.0, 1, 1, 1.0, 1.0, emptyBuckets);
        }

        // 1.2 先统计整个网格的世界坐标范围。
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (int nodeIndex = 0; nodeIndex < mesh.nodeCount(); nodeIndex++) {
            minX = Math.min(minX, mesh.x()[nodeIndex]);
            maxX = Math.max(maxX, mesh.x()[nodeIndex]);
            minY = Math.min(minY, mesh.y()[nodeIndex]);
            maxY = Math.max(maxY, mesh.y()[nodeIndex]);
        }

        // 1.3 根据三角形数量和范围估算桶数量，保持桶粒度稳定。
        int[] bucketShape = estimateBucketShape(mesh.triangleCount(), minX, maxX, minY, maxY);
        int bucketCountX = bucketShape[0];
        int bucketCountY = bucketShape[1];
        double bucketWidth = resolveBucketStep(minX, maxX, bucketCountX);
        double bucketHeight = resolveBucketStep(minY, maxY, bucketCountY);

        @SuppressWarnings("unchecked")
        List<Integer>[] buckets = (List<Integer>[]) new List[bucketCountX * bucketCountY];
        for (int index = 0; index < buckets.length; index++) {
            buckets[index] = new ArrayList<>();
        }

        // 1.4 逐个三角形按包围盒写入覆盖到的全部桶。
        for (int triangleIndex = 0; triangleIndex < mesh.triangles().length; triangleIndex++) {
            int[] triangle = mesh.triangles()[triangleIndex];
            double triangleMinX = Math.min(mesh.x()[triangle[0]], Math.min(mesh.x()[triangle[1]], mesh.x()[triangle[2]]));
            double triangleMaxX = Math.max(mesh.x()[triangle[0]], Math.max(mesh.x()[triangle[1]], mesh.x()[triangle[2]]));
            double triangleMinY = Math.min(mesh.y()[triangle[0]], Math.min(mesh.y()[triangle[1]], mesh.y()[triangle[2]]));
            double triangleMaxY = Math.max(mesh.y()[triangle[0]], Math.max(mesh.y()[triangle[1]], mesh.y()[triangle[2]]));

            int minBucketX = clampBucket(resolveBucketIndex(triangleMinX, minX, bucketWidth), bucketCountX);
            int maxBucketX = clampBucket(resolveBucketIndex(triangleMaxX, minX, bucketWidth), bucketCountX);
            int minBucketY = clampBucket(resolveBucketIndex(triangleMinY, minY, bucketHeight), bucketCountY);
            int maxBucketY = clampBucket(resolveBucketIndex(triangleMaxY, minY, bucketHeight), bucketCountY);
            for (int bucketY = minBucketY; bucketY <= maxBucketY; bucketY++) {
                for (int bucketX = minBucketX; bucketX <= maxBucketX; bucketX++) {
                    buckets[bucketY * bucketCountX + bucketX].add(triangleIndex);
                }
            }
        }

        logger.info(() -> "三角网空间索引构建结束, bucketCountX="
            + bucketCountX
            + ", bucketCountY="
            + bucketCountY);
        return new TriangleSpatialIndex(minX, maxX, minY, maxY, bucketCountX, bucketCountY, bucketWidth, bucketHeight, buckets);
    }

    /*
     * ========================================================================
     * 步骤2：查询候选三角形桶
     * ========================================================================
     * 目标：
     *   1) 将世界坐标快速映射到候选桶
     *   2) 返回当前点可能命中的三角形列表
     * 操作要点：
     *   1) 点落在网格范围外时直接返回空集合
     *   2) 桶内候选列表只读返回
     */
    public List<Integer> findCandidateTriangles(double worldX, double worldY) {
        // 2.1 无效坐标或明显超出网格范围时直接返回空结果。
        if (!Double.isFinite(worldX)
            || !Double.isFinite(worldY)
            || worldX < minX
            || worldX > maxX
            || worldY < minY
            || worldY > maxY) {
            return List.of();
        }

        // 2.2 将目标点映射到对应桶，并返回该桶内的候选三角形。
        int bucketX = clampBucket(resolveBucketIndex(worldX, minX, bucketWidth), bucketCountX);
        int bucketY = clampBucket(resolveBucketIndex(worldY, minY, bucketHeight), bucketCountY);
        return buckets[bucketY * bucketCountX + bucketX];
    }

    /*
     * ========================================================================
     * 步骤3：估算桶网格形状
     * ========================================================================
     * 目标：
     *   1) 让不同形状网格的桶数量接近均衡
     * 操作要点：
     *   1) 先按目标桶数估算总量
     *   2) 再按长宽比拆成 X/Y 两个方向
     */
    private static int[] estimateBucketShape(int triangleCount, double minX, double maxX, double minY, double maxY) {
        int targetBucketCount = Math.max(1, (int) Math.ceil(triangleCount / (double) TARGET_TRIANGLES_PER_BUCKET));
        double spanX = Math.max(1e-9, maxX - minX);
        double spanY = Math.max(1e-9, maxY - minY);
        double aspect = Math.sqrt(spanX / spanY);
        int bucketCountX = Math.max(1, Math.min(MAX_BUCKET_AXIS, (int) Math.ceil(Math.sqrt(targetBucketCount) * aspect)));
        int bucketCountY = Math.max(1, Math.min(MAX_BUCKET_AXIS, (int) Math.ceil(targetBucketCount / (double) bucketCountX)));
        return new int[]{bucketCountX, bucketCountY};
    }

    private static double resolveBucketStep(double min, double max, int bucketCount) {
        return Math.max(1e-9, (max - min) / Math.max(1, bucketCount));
    }

    private static int resolveBucketIndex(double value, double min, double step) {
        return (int) Math.floor((value - min) / step);
    }

    private static int clampBucket(int bucketIndex, int bucketCount) {
        return Math.max(0, Math.min(bucketCount - 1, bucketIndex));
    }
}
