package com.example.netcdfviewer.model;

import java.util.Arrays;

public record TriangleDomain(MeshData mesh) implements SpatialDomain {
    public TriangleDomain {
        if (mesh == null) {
            throw new IllegalArgumentException("mesh must not be null");
        }
    }

    @Override
    public Kind kind() {
        return Kind.TRIANGLE_MESH;
    }

    @Override
    public double minX() {
        return Arrays.stream(mesh.x()).min().orElse(0.0);
    }

    @Override
    public double maxX() {
        return Arrays.stream(mesh.x()).max().orElse(0.0);
    }

    @Override
    public double minY() {
        return Arrays.stream(mesh.y()).min().orElse(0.0);
    }

    @Override
    public double maxY() {
        return Arrays.stream(mesh.y()).max().orElse(0.0);
    }
}
