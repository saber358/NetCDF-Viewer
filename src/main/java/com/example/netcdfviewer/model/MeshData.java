package com.example.netcdfviewer.model;

import java.util.Arrays;

/**
 * 三角网网格数据。
 * 该对象保存节点坐标数组和三角形连接关系数组。
 */
public record MeshData(double[] x, double[] y, int[][] triangles) {
    public MeshData {
        // 所有输入数组都必须存在，否则网格对象没有意义。
        if (x == null || y == null || triangles == null) {
            throw new IllegalArgumentException("Mesh arrays must not be null.");
        }
        // X 与 Y 坐标数组长度必须一致，确保每个节点都有完整坐标。
        if (x.length != y.length) {
            throw new IllegalArgumentException("Coordinate arrays must have the same length.");
        }
        // 复制 X 坐标数组，避免外部修改影响内部状态。
        x = Arrays.copyOf(x, x.length);
        // 复制 Y 坐标数组，避免外部修改影响内部状态。
        y = Arrays.copyOf(y, y.length);
        // 深拷贝三角形连接数组，避免外部修改影响内部状态。
        triangles = Arrays.stream(triangles)
            .map(triangle -> Arrays.copyOf(triangle, triangle.length))
            .toArray(int[][]::new);
    }

    public int nodeCount() {
        // 节点数量等于坐标数组长度。
        return x.length;
    }

    public int triangleCount() {
        // 三角形数量等于连接数组长度。
        return triangles.length;
    }
}
