package com.example.netcdfviewer.model;

import java.util.Arrays;

public record MeshData(double[] x, double[] y, int[][] triangles) {
    public MeshData {
        if (x == null || y == null || triangles == null) {
            throw new IllegalArgumentException("Mesh arrays must not be null.");
        }
        if (x.length != y.length) {
            throw new IllegalArgumentException("Coordinate arrays must have the same length.");
        }
        x = Arrays.copyOf(x, x.length);
        y = Arrays.copyOf(y, y.length);
        triangles = Arrays.stream(triangles)
            .map(triangle -> Arrays.copyOf(triangle, triangle.length))
            .toArray(int[][]::new);
    }

    public int nodeCount() {
        return x.length;
    }

    public int triangleCount() {
        return triangles.length;
    }
}
