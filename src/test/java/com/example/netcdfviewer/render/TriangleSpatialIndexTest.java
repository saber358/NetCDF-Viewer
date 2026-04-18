package com.example.netcdfviewer.render;

import com.example.netcdfviewer.model.MeshData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TriangleSpatialIndexTest {
    private static final MeshData MESH = new MeshData(
        new double[]{0.0, 10.0, 0.0, 20.0},
        new double[]{0.0, 0.0, 10.0, 0.0},
        new int[][]{
            {0, 1, 2},
            {1, 3, 2}
        }
    );

    @Test
    void findCandidateTrianglesReturnsOnlyRelevantTriangleBucket() {
        TriangleSpatialIndex index = TriangleSpatialIndex.build(MESH);

        List<Integer> candidates = index.findCandidateTriangles(2.0, 2.0);

        assertTrue(candidates.contains(0));
    }

    @Test
    void findCandidateTrianglesReturnsMultipleCandidatesNearSharedBounds() {
        TriangleSpatialIndex index = TriangleSpatialIndex.build(MESH);

        List<Integer> candidates = index.findCandidateTriangles(9.5, 0.5);

        assertTrue(candidates.contains(0));
        assertTrue(candidates.contains(1));
    }
}
