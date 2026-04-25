package com.example.netcdfviewer.io;

import com.example.netcdfviewer.model.VariableInfo;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class LayerDataCacheTest {
    @Test
    void returnsCachedLayerWithoutReloadingSameDatasetVariableAndLayer() throws Exception {
        LayerDataCache cache = new LayerDataCache(1024);
        ParsedDataset dataset = dataset("same.nc");
        VariableInfo variable = variable("temperature");
        AtomicInteger loadCount = new AtomicInteger();

        double[] first = cache.getOrLoad(dataset, variable, 0, () -> {
            loadCount.incrementAndGet();
            return new double[]{1.0, 2.0, 3.0};
        });
        double[] second = cache.getOrLoad(dataset, variable, 0, () -> {
            loadCount.incrementAndGet();
            return new double[]{9.0};
        });

        assertSame(first, second);
        assertEquals(1, loadCount.get());
    }

    @Test
    void evictsLeastRecentlyUsedLayerWhenByteLimitExceeded() throws Exception {
        LayerDataCache cache = new LayerDataCache(Double.BYTES * 4L);
        ParsedDataset dataset = dataset("evict.nc");
        VariableInfo firstVariable = variable("first");
        VariableInfo secondVariable = variable("second");
        VariableInfo thirdVariable = variable("third");
        AtomicInteger secondLoadCount = new AtomicInteger();

        cache.getOrLoad(dataset, firstVariable, 0, () -> new double[]{1.0, 1.1});
        cache.getOrLoad(dataset, secondVariable, 0, () -> {
            secondLoadCount.incrementAndGet();
            return new double[]{2.0, 2.1};
        });
        cache.getOrLoad(dataset, firstVariable, 0, () -> new double[]{8.0});
        cache.getOrLoad(dataset, thirdVariable, 0, () -> new double[]{3.0, 3.1});
        cache.getOrLoad(dataset, secondVariable, 0, () -> {
            secondLoadCount.incrementAndGet();
            return new double[]{2.2, 2.3};
        });

        assertEquals(2, secondLoadCount.get());
    }

    private static ParsedDataset dataset(String fileName) {
        return new ParsedDataset(
            Path.of(fileName),
            null,
            null,
            List.of(),
            null,
            List.of(),
            Map.of(),
            Map.of(),
            Map.of(),
            List.of(),
            null,
            null,
            null
        );
    }

    private static VariableInfo variable(String name) {
        return new VariableInfo(
            name,
            "double",
            List.of("node"),
            List.of(2),
            true,
            0,
            false,
            -1,
            null
        );
    }
}
