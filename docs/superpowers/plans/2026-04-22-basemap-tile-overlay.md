# Basemap Tile Overlay Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add OSM, Tianditu, and custom XYZ tile basemaps under the existing NetCDF render while preserving current point query, overlays, navigation preview, and PNG export behavior.

**Architecture:** Introduce a focused `basemap` package for tile definitions, URL expansion, Web Mercator math, caching, HTTP fetch, and offscreen tile rendering. Add a small JavaFX dialog for custom basemap settings, then wire current basemap selection into `MainController` so tiles render below the NetCDF scalar image and inside the existing composite preview cache.

**Tech Stack:** Java 17, JavaFX 21, Java HTTP Client, AWT `BufferedImage`, Maven, JUnit 5, existing Canvas render pipeline

---

## File Map

- Create: `src/main/java/com/example/netcdfviewer/basemap/BaseMapDefinition.java`
  Purpose: Immutable user-facing basemap definition containing one or more tile layers.
- Create: `src/main/java/com/example/netcdfviewer/basemap/BaseMapLayer.java`
  Purpose: Immutable tile layer definition with URL template, token, subdomains, and opacity.
- Create: `src/main/java/com/example/netcdfviewer/basemap/BaseMapPreset.java`
  Purpose: Factory for built-in OSM and Tianditu definitions.
- Create: `src/main/java/com/example/netcdfviewer/basemap/BaseMapSelection.java`
  Purpose: Represents current selection, including disabled state and custom definitions.
- Create: `src/main/java/com/example/netcdfviewer/basemap/BaseMapRenderResult.java`
  Purpose: Carries rendered tile image plus non-fatal status message.
- Create: `src/main/java/com/example/netcdfviewer/basemap/TileAddress.java`
  Purpose: Immutable XYZ tile coordinate.
- Create: `src/main/java/com/example/netcdfviewer/basemap/TileMath.java`
  Purpose: Convert longitude/latitude to Web Mercator tile coordinates and pick render zoom.
- Create: `src/main/java/com/example/netcdfviewer/basemap/TileUrlTemplate.java`
  Purpose: Expand `{z}`, `{x}`, `{y}`, `{s}`, and `{tk}` placeholders.
- Create: `src/main/java/com/example/netcdfviewer/basemap/TileCache.java`
  Purpose: Combine small in-memory LRU cache and disk cache under user home.
- Create: `src/main/java/com/example/netcdfviewer/basemap/TileClient.java`
  Purpose: Fetch and decode tile images with fixed timeouts.
- Create: `src/main/java/com/example/netcdfviewer/basemap/TileRenderer.java`
  Purpose: Render all visible XYZ tiles into a transparent `BufferedImage`.
- Create: `src/main/java/com/example/netcdfviewer/ui/BaseMapConfigDialog.java`
  Purpose: JavaFX dialog for entering custom basemap name, URL template, token, subdomains, and opacity.
- Modify: `src/main/java/com/example/netcdfviewer/ui/MainView.java`
  Purpose: Add “底图” menu, menu items, and getters.
- Modify: `src/main/java/com/example/netcdfviewer/ui/MainController.java`
  Purpose: Store current basemap selection, schedule tile render, draw tiles below NetCDF scalar layer, and show warnings.
- Modify: `README.md`
  Purpose: Document OSM, Tianditu token, and custom XYZ URL usage.
- Modify: `CHANGELOG.md`
  Purpose: Add unreleased basemap entry when this implementation is finished.
- Test: `src/test/java/com/example/netcdfviewer/basemap/TileMathTest.java`
  Purpose: Verify coordinate and zoom math.
- Test: `src/test/java/com/example/netcdfviewer/basemap/TileUrlTemplateTest.java`
  Purpose: Verify URL template expansion and subdomain choice.
- Test: `src/test/java/com/example/netcdfviewer/basemap/TileCacheTest.java`
  Purpose: Verify memory and disk cache behavior without network.
- Test: `src/test/java/com/example/netcdfviewer/basemap/TileRendererTest.java`
  Purpose: Verify renderer composes fake tiles and skips non-geographic domains.
- Modify: `src/test/java/com/example/netcdfviewer/ui/MainViewLayoutStabilityTest.java`
  Purpose: Verify the new basemap menu exists without changing layout stability.
- Modify: `src/test/java/com/example/netcdfviewer/ui/MainControllerLoadFileTest.java`
  Purpose: Verify basemap selection triggers render and does not break NetCDF load.

## Task 1: Add Basemap Definitions, URL Templates, and Tile Math

**Files:**
- Create: `src/main/java/com/example/netcdfviewer/basemap/BaseMapLayer.java`
- Create: `src/main/java/com/example/netcdfviewer/basemap/BaseMapDefinition.java`
- Create: `src/main/java/com/example/netcdfviewer/basemap/BaseMapSelection.java`
- Create: `src/main/java/com/example/netcdfviewer/basemap/BaseMapPreset.java`
- Create: `src/main/java/com/example/netcdfviewer/basemap/TileAddress.java`
- Create: `src/main/java/com/example/netcdfviewer/basemap/TileMath.java`
- Create: `src/main/java/com/example/netcdfviewer/basemap/TileUrlTemplate.java`
- Test: `src/test/java/com/example/netcdfviewer/basemap/TileMathTest.java`
- Test: `src/test/java/com/example/netcdfviewer/basemap/TileUrlTemplateTest.java`

- [ ] **Step 1: Write failing tile math tests**

Create `src/test/java/com/example/netcdfviewer/basemap/TileMathTest.java`:

```java
package com.example.netcdfviewer.basemap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TileMathTest {
    @Test
    void geographicBoundsAcceptsLongitudeLatitudeRanges() {
        assertTrue(TileMath.isGeographicBounds(110.0, 125.0, 20.0, 40.0));
        assertTrue(TileMath.isGeographicBounds(-180.0, 180.0, -85.0, 85.0));
    }

    @Test
    void geographicBoundsRejectsProjectedOrInvalidRanges() {
        assertFalse(TileMath.isGeographicBounds(400000.0, 410000.0, 3400000.0, 3410000.0));
        assertFalse(TileMath.isGeographicBounds(125.0, 110.0, 20.0, 40.0));
        assertFalse(TileMath.isGeographicBounds(110.0, 125.0, -90.0, 90.0));
    }

    @Test
    void lonLatToGlobalPixelMatchesWebMercatorOriginAtZoomOne() {
        TileMath.GlobalPixel pixel = TileMath.lonLatToGlobalPixel(0.0, 0.0, 1);

        assertEquals(256.0, pixel.x(), 0.000001);
        assertEquals(256.0, pixel.y(), 0.000001);
    }

    @Test
    void globalPixelToTileAddressFloorsPixelCoordinates() {
        TileAddress address = TileMath.globalPixelToAddress(new TileMath.GlobalPixel(513.0, 255.0), 2);

        assertEquals(2, address.z());
        assertEquals(2, address.x());
        assertEquals(0, address.y());
    }

    @Test
    void chooseZoomUsesViewportScaleAndClampsToSupportedRange() {
        assertEquals(3, TileMath.chooseZoom(4.0, 0, 18));
        assertEquals(0, TileMath.chooseZoom(0.000001, 0, 18));
        assertEquals(18, TileMath.chooseZoom(999999.0, 0, 18));
    }
}
```

- [ ] **Step 2: Write failing URL template tests**

Create `src/test/java/com/example/netcdfviewer/basemap/TileUrlTemplateTest.java`:

```java
package com.example.netcdfviewer.basemap;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TileUrlTemplateTest {
    @Test
    void expandReplacesXyzSubdomainAndToken() {
        BaseMapLayer layer = new BaseMapLayer(
            "测试",
            "https://t{s}.example.com/{z}/{x}/{y}.png?tk={tk}",
            "abc123",
            List.of("0", "1", "2"),
            0.75
        );

        String url = TileUrlTemplate.expand(layer, new TileAddress(5, 10, 12));

        assertEquals("https://t0.example.com/5/10/12.png?tk=abc123", url);
    }

    @Test
    void expandUsesBlankSubdomainWhenLayerHasNone() {
        BaseMapLayer layer = new BaseMapLayer(
            "测试",
            "https://tiles.example.com/{z}/{x}/{y}.png",
            "",
            List.of(),
            1.0
        );

        String url = TileUrlTemplate.expand(layer, new TileAddress(3, 4, 5));

        assertEquals("https://tiles.example.com/3/4/5.png", url);
    }

    @Test
    void blankTemplateIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new BaseMapLayer("坏底图", " ", "", List.of(), 1.0));
    }
}
```

- [ ] **Step 3: Run focused tests and verify they fail**

Run: `mvn -q -Dtest=TileMathTest,TileUrlTemplateTest test`

Expected: FAIL because the `basemap` package does not exist.

- [ ] **Step 4: Add the immutable basemap model**

Create `src/main/java/com/example/netcdfviewer/basemap/BaseMapLayer.java`:

```java
package com.example.netcdfviewer.basemap;

import java.util.List;

public record BaseMapLayer(
    String name,
    String urlTemplate,
    String token,
    List<String> subdomains,
    double opacity
) {
    public BaseMapLayer {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("底图图层名称不能为空。");
        }
        if (urlTemplate == null || urlTemplate.isBlank()) {
            throw new IllegalArgumentException("底图 URL 模板不能为空。");
        }
        token = token == null ? "" : token.trim();
        subdomains = subdomains == null ? List.of() : List.copyOf(subdomains);
        opacity = Math.max(0.0, Math.min(1.0, opacity));
    }
}
```

Create `src/main/java/com/example/netcdfviewer/basemap/BaseMapDefinition.java`:

```java
package com.example.netcdfviewer.basemap;

import java.util.List;

public record BaseMapDefinition(
    String id,
    String displayName,
    List<BaseMapLayer> layers,
    boolean tokenRequired
) {
    public BaseMapDefinition {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("底图 ID 不能为空。");
        }
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("底图名称不能为空。");
        }
        layers = layers == null ? List.of() : List.copyOf(layers);
    }

    public boolean enabled() {
        return !layers.isEmpty();
    }
}
```

Create `src/main/java/com/example/netcdfviewer/basemap/BaseMapSelection.java`:

```java
package com.example.netcdfviewer.basemap;

public record BaseMapSelection(BaseMapDefinition definition) {
    public static BaseMapSelection none() {
        return new BaseMapSelection(null);
    }

    public boolean enabled() {
        return definition != null && definition.enabled();
    }

    public String displayName() {
        return enabled() ? definition.displayName() : "无底图";
    }
}
```

Create `src/main/java/com/example/netcdfviewer/basemap/TileAddress.java`:

```java
package com.example.netcdfviewer.basemap;

public record TileAddress(int z, int x, int y) {
    public TileAddress {
        if (z < 0 || x < 0 || y < 0) {
            throw new IllegalArgumentException("瓦片编号不能为负数。");
        }
    }
}
```

- [ ] **Step 5: Add presets for OSM and Tianditu**

Create `src/main/java/com/example/netcdfviewer/basemap/BaseMapPreset.java`:

```java
package com.example.netcdfviewer.basemap;

import java.util.List;

public enum BaseMapPreset {
    OSM("osm", "OpenStreetMap 标准图"),
    TIANDITU_VECTOR("tianditu-vector", "天地图矢量图"),
    TIANDITU_IMAGE("tianditu-image", "天地图影像图"),
    TIANDITU_TERRAIN("tianditu-terrain", "天地图地形图");

    private final String id;
    private final String displayName;

    BaseMapPreset(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public BaseMapDefinition create(String token) {
        String normalizedToken = token == null ? "" : token.trim();
        return switch (this) {
            case OSM -> new BaseMapDefinition(
                id,
                displayName,
                List.of(new BaseMapLayer(
                    displayName,
                    "https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png",
                    "",
                    List.of("a", "b", "c"),
                    1.0
                )),
                false
            );
            case TIANDITU_VECTOR -> tianditu("vec_w", "cva_w", normalizedToken);
            case TIANDITU_IMAGE -> tianditu("img_w", "cia_w", normalizedToken);
            case TIANDITU_TERRAIN -> tianditu("ter_w", "cta_w", normalizedToken);
        };
    }

    private BaseMapDefinition tianditu(String baseLayer, String labelLayer, String token) {
        List<String> subdomains = List.of("0", "1", "2", "3", "4", "5", "6", "7");
        return new BaseMapDefinition(
            id,
            displayName,
            List.of(
                tiandituLayer(baseLayer, token, subdomains, 1.0),
                tiandituLayer(labelLayer, token, subdomains, 1.0)
            ),
            true
        );
    }

    private BaseMapLayer tiandituLayer(String layerName, String token, List<String> subdomains, double opacity) {
        String template = "https://t{s}.tianditu.gov.cn/" + layerName
            + "/wmts?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0&LAYER=" + layerName
            + "&STYLE=default&TILEMATRIXSET=w&FORMAT=tiles&TILEMATRIX={z}&TILEROW={y}&TILECOL={x}&tk={tk}";
        return new BaseMapLayer(layerName, template, token, subdomains, opacity);
    }
}
```

- [ ] **Step 6: Add URL expansion and tile math**

Create `src/main/java/com/example/netcdfviewer/basemap/TileUrlTemplate.java`:

```java
package com.example.netcdfviewer.basemap;

public final class TileUrlTemplate {
    private TileUrlTemplate() {
    }

    public static String expand(BaseMapLayer layer, TileAddress address) {
        String subdomain = resolveSubdomain(layer, address);
        return layer.urlTemplate()
            .replace("{z}", Integer.toString(address.z()))
            .replace("{x}", Integer.toString(address.x()))
            .replace("{y}", Integer.toString(address.y()))
            .replace("{s}", subdomain)
            .replace("{tk}", layer.token());
    }

    private static String resolveSubdomain(BaseMapLayer layer, TileAddress address) {
        if (layer.subdomains().isEmpty()) {
            return "";
        }
        int index = Math.floorMod(address.x() + address.y(), layer.subdomains().size());
        return layer.subdomains().get(index);
    }
}
```

Create `src/main/java/com/example/netcdfviewer/basemap/TileMath.java`:

```java
package com.example.netcdfviewer.basemap;

public final class TileMath {
    public static final int TILE_SIZE = 256;
    public static final double WEB_MERCATOR_MAX_LATITUDE = 85.05112878;

    private TileMath() {
    }

    public static boolean isGeographicBounds(double minX, double maxX, double minY, double maxY) {
        return Double.isFinite(minX)
            && Double.isFinite(maxX)
            && Double.isFinite(minY)
            && Double.isFinite(maxY)
            && minX >= -180.0
            && maxX <= 180.0
            && minY >= -WEB_MERCATOR_MAX_LATITUDE
            && maxY <= WEB_MERCATOR_MAX_LATITUDE
            && maxX > minX
            && maxY > minY;
    }

    public static int chooseZoom(double viewportScale, int minZoom, int maxZoom) {
        if (!Double.isFinite(viewportScale) || viewportScale <= 0.0) {
            return minZoom;
        }
        double zoom = Math.log(viewportScale * 360.0 / TILE_SIZE) / Math.log(2.0);
        int rounded = (int) Math.round(zoom);
        return Math.max(minZoom, Math.min(maxZoom, rounded));
    }

    public static GlobalPixel lonLatToGlobalPixel(double lon, double lat, int zoom) {
        double clippedLat = Math.max(-WEB_MERCATOR_MAX_LATITUDE, Math.min(WEB_MERCATOR_MAX_LATITUDE, lat));
        double sinLat = Math.sin(Math.toRadians(clippedLat));
        double mapSize = mapSize(zoom);
        double x = (lon + 180.0) / 360.0 * mapSize;
        double y = (0.5 - Math.log((1.0 + sinLat) / (1.0 - sinLat)) / (4.0 * Math.PI)) * mapSize;
        return new GlobalPixel(x, y);
    }

    public static TileAddress globalPixelToAddress(GlobalPixel pixel, int zoom) {
        int maxTile = (1 << zoom) - 1;
        int x = Math.max(0, Math.min(maxTile, (int) Math.floor(pixel.x() / TILE_SIZE)));
        int y = Math.max(0, Math.min(maxTile, (int) Math.floor(pixel.y() / TILE_SIZE)));
        return new TileAddress(zoom, x, y);
    }

    public static double mapSize(int zoom) {
        return (double) TILE_SIZE * (1 << zoom);
    }

    public record GlobalPixel(double x, double y) {
    }
}
```

- [ ] **Step 7: Run focused tests and commit**

Run: `mvn -q -Dtest=TileMathTest,TileUrlTemplateTest test`

Expected: PASS.

Commit:

```bash
git add src/main/java/com/example/netcdfviewer/basemap src/test/java/com/example/netcdfviewer/basemap/TileMathTest.java src/test/java/com/example/netcdfviewer/basemap/TileUrlTemplateTest.java
git commit -m "Add basemap tile definitions"
```

## Task 2: Add Tile Cache, Client, and Renderer

**Files:**
- Create: `src/main/java/com/example/netcdfviewer/basemap/TileCache.java`
- Create: `src/main/java/com/example/netcdfviewer/basemap/TileClient.java`
- Create: `src/main/java/com/example/netcdfviewer/basemap/BaseMapRenderResult.java`
- Create: `src/main/java/com/example/netcdfviewer/basemap/TileRenderer.java`
- Test: `src/test/java/com/example/netcdfviewer/basemap/TileCacheTest.java`
- Test: `src/test/java/com/example/netcdfviewer/basemap/TileRendererTest.java`

- [ ] **Step 1: Write failing cache tests**

Create `src/test/java/com/example/netcdfviewer/basemap/TileCacheTest.java`:

```java
package com.example.netcdfviewer.basemap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TileCacheTest {
    @TempDir
    Path tempDir;

    @Test
    void putAndGetRoundTripsImageThroughMemoryAndDisk() throws Exception {
        TileCache cache = new TileCache(tempDir, 8);
        BufferedImage image = image(Color.RED);

        cache.put("https://tiles.example.com/1/2/3.png", image);

        Optional<BufferedImage> fromMemory = cache.get("https://tiles.example.com/1/2/3.png");
        TileCache reopened = new TileCache(tempDir, 8);
        Optional<BufferedImage> fromDisk = reopened.get("https://tiles.example.com/1/2/3.png");

        assertTrue(fromMemory.isPresent());
        assertTrue(fromDisk.isPresent());
        assertEquals(Color.RED.getRGB(), fromDisk.get().getRGB(0, 0));
    }

    private static BufferedImage image(Color color) {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(color);
        graphics.fillRect(0, 0, 2, 2);
        graphics.dispose();
        return image;
    }
}
```

- [ ] **Step 2: Write failing renderer tests**

Create `src/test/java/com/example/netcdfviewer/basemap/TileRendererTest.java`:

```java
package com.example.netcdfviewer.basemap;

import com.example.netcdfviewer.model.SpatialDomain;
import com.example.netcdfviewer.ui.ViewportState;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class TileRendererTest {
    @Test
    void renderReturnsNullWhenSelectionIsDisabled() {
        TileRenderer renderer = new TileRenderer(url -> null);

        BaseMapRenderResult result = renderer.render(
            BaseMapSelection.none(),
            domain(110.0, 112.0, 20.0, 22.0),
            new ViewportState.Snapshot(100.0, 0.0, 2200.0),
            320,
            240
        );

        assertNull(result.image());
        assertNull(result.message());
    }

    @Test
    void renderSkipsNonGeographicDomainWithMessage() {
        TileRenderer renderer = new TileRenderer(url -> null);

        BaseMapRenderResult result = renderer.render(
            new BaseMapSelection(BaseMapPreset.OSM.create("")),
            domain(400000.0, 410000.0, 3400000.0, 3410000.0),
            new ViewportState.Snapshot(1.0, 0.0, 0.0),
            320,
            240
        );

        assertNull(result.image());
        assertEquals("当前坐标域不是经纬度，已跳过底图。", result.message());
    }

    @Test
    void renderDrawsTilesFromFakeClient() {
        TileRenderer renderer = new TileRenderer(url -> solid(Color.BLUE));

        BaseMapDefinition definition = new BaseMapDefinition(
            "custom",
            "测试底图",
            List.of(new BaseMapLayer("测试图层", "https://tiles.example.com/{z}/{x}/{y}.png", "", List.of(), 1.0)),
            false
        );
        BaseMapRenderResult result = renderer.render(
            new BaseMapSelection(definition),
            domain(110.0, 112.0, 20.0, 22.0),
            new ViewportState.Snapshot(100.0, -11000.0, 2200.0),
            160,
            120
        );

        assertNotNull(result.image());
        assertEquals(Color.BLUE.getRGB(), result.image().getRGB(80, 60));
    }

    private static SpatialDomain domain(double minX, double maxX, double minY, double maxY) {
        return new SpatialDomain() {
            @Override
            public Kind kind() {
                return Kind.STRUCTURED_GRID;
            }

            @Override
            public double minX() {
                return minX;
            }

            @Override
            public double maxX() {
                return maxX;
            }

            @Override
            public double minY() {
                return minY;
            }

            @Override
            public double maxY() {
                return maxY;
            }
        };
    }

    private static BufferedImage solid(Color color) {
        BufferedImage image = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(color);
        graphics.fillRect(0, 0, 256, 256);
        graphics.dispose();
        return image;
    }
}
```

- [ ] **Step 3: Run focused tests and verify they fail**

Run: `mvn -q -Dtest=TileCacheTest,TileRendererTest test`

Expected: FAIL because cache, client, result, and renderer do not exist.

- [ ] **Step 4: Add cache and client contracts**

Create `src/main/java/com/example/netcdfviewer/basemap/TileCache.java`:

```java
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

    public synchronized Optional<BufferedImage> get(String url) {
        logger.info(() -> "开始读取瓦片缓存, url=" + url);
        BufferedImage memoryImage = memoryCache.get(url);
        if (memoryImage != null) {
            logger.info("瓦片缓存读取完成, source=memory");
            return Optional.of(memoryImage);
        }
        Path path = cachePath(url);
        if (!Files.isRegularFile(path)) {
            logger.info("瓦片缓存读取完成, source=none");
            return Optional.empty();
        }
        try {
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

    public synchronized void put(String url, BufferedImage image) throws IOException {
        logger.info(() -> "开始写入瓦片缓存, url=" + url);
        memoryCache.put(url, image);
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
```

Create `src/main/java/com/example/netcdfviewer/basemap/TileClient.java`:

```java
package com.example.netcdfviewer.basemap;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
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
        private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

        private HttpTileClient(TileCache cache) {
            this.cache = cache;
        }

        @Override
        public BufferedImage fetch(String url) {
            logger.info(() -> "开始获取在线瓦片, url=" + url);
            try {
                if (cache != null) {
                    BufferedImage cached = cache.get(url).orElse(null);
                    if (cached != null) {
                        logger.info("在线瓦片获取完成, source=cache");
                        return cached;
                    }
                }
                HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(8))
                    .header("User-Agent", "NetCDFViewer/1.1")
                    .GET()
                    .build();
                HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    logger.info(() -> "在线瓦片获取完成, source=network, status=" + response.statusCode());
                    return null;
                }
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
    }
}
```

- [ ] **Step 5: Add render result and renderer**

Create `src/main/java/com/example/netcdfviewer/basemap/BaseMapRenderResult.java`:

```java
package com.example.netcdfviewer.basemap;

import java.awt.image.BufferedImage;

public record BaseMapRenderResult(BufferedImage image, String message) {
    public static BaseMapRenderResult empty() {
        return new BaseMapRenderResult(null, null);
    }
}
```

Create `src/main/java/com/example/netcdfviewer/basemap/TileRenderer.java`:

```java
package com.example.netcdfviewer.basemap;

import com.example.netcdfviewer.model.SpatialDomain;
import com.example.netcdfviewer.ui.ViewportState;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.logging.Logger;

public final class TileRenderer {
    private static final Logger logger = Logger.getLogger(TileRenderer.class.getName());
    private final TileClient tileClient;

    public TileRenderer(TileClient tileClient) {
        this.tileClient = tileClient;
    }

    public BaseMapRenderResult render(
        BaseMapSelection selection,
        SpatialDomain spatialDomain,
        ViewportState.Snapshot snapshot,
        int width,
        int height
    ) {
        logger.info(() -> "开始渲染在线底图, selection=" + (selection == null ? "null" : selection.displayName()));
        if (selection == null || !selection.enabled()) {
            logger.info("在线底图渲染结束, rendered=false, reason=disabled");
            return BaseMapRenderResult.empty();
        }
        if (spatialDomain == null
            || !TileMath.isGeographicBounds(spatialDomain.minX(), spatialDomain.maxX(), spatialDomain.minY(), spatialDomain.maxY())) {
            logger.info("在线底图渲染结束, rendered=false, reason=non-geographic");
            return new BaseMapRenderResult(null, "当前坐标域不是经纬度，已跳过底图。");
        }
        if (selection.definition().tokenRequired()
            && selection.definition().layers().stream().anyMatch(layer -> layer.token().isBlank())) {
            logger.info("在线底图渲染结束, rendered=false, reason=missing-token");
            return new BaseMapRenderResult(null, "天地图需要 token，已跳过底图。");
        }

        BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = output.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        int zoom = TileMath.chooseZoom(snapshot.scale(), 0, 18);
        for (BaseMapLayer layer : selection.definition().layers()) {
            drawLayer(graphics, layer, snapshot, width, height, zoom);
        }
        graphics.dispose();
        logger.info(() -> "在线底图渲染结束, rendered=true, zoom=" + zoom);
        return new BaseMapRenderResult(output, null);
    }

    private void drawLayer(Graphics2D graphics, BaseMapLayer layer, ViewportState.Snapshot snapshot, int width, int height, int zoom) {
        TileMath.GlobalPixel topLeft = TileMath.lonLatToGlobalPixel(snapshot.worldX(0.0), snapshot.worldY(0.0), zoom);
        TileMath.GlobalPixel bottomRight = TileMath.lonLatToGlobalPixel(snapshot.worldX(width), snapshot.worldY(height), zoom);
        TileAddress first = TileMath.globalPixelToAddress(topLeft, zoom);
        TileAddress last = TileMath.globalPixelToAddress(bottomRight, zoom);
        int minX = Math.min(first.x(), last.x());
        int maxX = Math.max(first.x(), last.x());
        int minY = Math.min(first.y(), last.y());
        int maxY = Math.max(first.y(), last.y());
        graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) layer.opacity()));
        for (int tileX = minX; tileX <= maxX; tileX++) {
            for (int tileY = minY; tileY <= maxY; tileY++) {
                TileAddress address = new TileAddress(zoom, tileX, tileY);
                BufferedImage tile = tileClient.fetch(TileUrlTemplate.expand(layer, address));
                if (tile == null) {
                    continue;
                }
                double screenX = tileX * TileMath.TILE_SIZE - topLeft.x();
                double screenY = tileY * TileMath.TILE_SIZE - topLeft.y();
                graphics.drawImage(tile, (int) Math.round(screenX), (int) Math.round(screenY), null);
            }
        }
        graphics.setComposite(AlphaComposite.SrcOver);
    }
}
```

- [ ] **Step 6: Run focused tests and commit**

Run: `mvn -q -Dtest=TileCacheTest,TileRendererTest test`

Expected: PASS.

Commit:

```bash
git add src/main/java/com/example/netcdfviewer/basemap src/test/java/com/example/netcdfviewer/basemap
git commit -m "Add basemap tile renderer"
```

## Task 3: Add Basemap UI Menu and Custom Dialog

**Files:**
- Create: `src/main/java/com/example/netcdfviewer/ui/BaseMapConfigDialog.java`
- Modify: `src/main/java/com/example/netcdfviewer/ui/MainView.java`
- Modify: `src/test/java/com/example/netcdfviewer/ui/MainViewLayoutStabilityTest.java`

- [ ] **Step 1: Write failing UI tests**

Modify `src/test/java/com/example/netcdfviewer/ui/MainViewLayoutStabilityTest.java` by adding:

```java
@Test
void mainViewExposesBasemapMenuItems() {
    MainView view = new MainView();

    assertEquals("底图", view.getBaseMapMenu().getText());
    assertEquals("无底图", view.getNoBaseMapMenuItem().getText());
    assertEquals("OpenStreetMap 标准图", view.getOsmBaseMapMenuItem().getText());
    assertEquals("天地图矢量图", view.getTiandituVectorBaseMapMenuItem().getText());
    assertEquals("自定义底图...", view.getCustomBaseMapMenuItem().getText());
}
```

- [ ] **Step 2: Run the focused test and verify it fails**

Run: `mvn -q -Dtest=MainViewLayoutStabilityTest test`

Expected: FAIL because the basemap menu getters do not exist.

- [ ] **Step 3: Add menu fields and getters to MainView**

Modify `src/main/java/com/example/netcdfviewer/ui/MainView.java`.

Add fields near the existing menu fields:

```java
// 底图菜单。
private final Menu baseMapMenu = new Menu("底图");
// 禁用底图菜单项。
private final MenuItem noBaseMapMenuItem = new MenuItem("无底图");
// OSM 底图菜单项。
private final MenuItem osmBaseMapMenuItem = new MenuItem("OpenStreetMap 标准图");
// 天地图矢量底图菜单项。
private final MenuItem tiandituVectorBaseMapMenuItem = new MenuItem("天地图矢量图");
// 天地图影像底图菜单项。
private final MenuItem tiandituImageBaseMapMenuItem = new MenuItem("天地图影像图");
// 天地图地形底图菜单项。
private final MenuItem tiandituTerrainBaseMapMenuItem = new MenuItem("天地图地形图");
// 自定义底图菜单项。
private final MenuItem customBaseMapMenuItem = new MenuItem("自定义底图...");
// 清除自定义底图参数菜单项。
private final MenuItem clearCustomBaseMapMenuItem = new MenuItem("清除自定义底图参数");
```

Replace the menu bar creation block:

```java
baseMapMenu.getItems().addAll(
    noBaseMapMenuItem,
    osmBaseMapMenuItem,
    tiandituVectorBaseMapMenuItem,
    tiandituImageBaseMapMenuItem,
    tiandituTerrainBaseMapMenuItem,
    customBaseMapMenuItem,
    clearCustomBaseMapMenuItem
);
MenuBar menuBar = new MenuBar(fileMenu, baseMapMenu, helpMenu);
```

Add getters near existing menu getters:

```java
public Menu getBaseMapMenu() {
    return baseMapMenu;
}

public MenuItem getNoBaseMapMenuItem() {
    return noBaseMapMenuItem;
}

public MenuItem getOsmBaseMapMenuItem() {
    return osmBaseMapMenuItem;
}

public MenuItem getTiandituVectorBaseMapMenuItem() {
    return tiandituVectorBaseMapMenuItem;
}

public MenuItem getTiandituImageBaseMapMenuItem() {
    return tiandituImageBaseMapMenuItem;
}

public MenuItem getTiandituTerrainBaseMapMenuItem() {
    return tiandituTerrainBaseMapMenuItem;
}

public MenuItem getCustomBaseMapMenuItem() {
    return customBaseMapMenuItem;
}

public MenuItem getClearCustomBaseMapMenuItem() {
    return clearCustomBaseMapMenuItem;
}
```

- [ ] **Step 4: Add the custom basemap dialog**

Create `src/main/java/com/example/netcdfviewer/ui/BaseMapConfigDialog.java`:

```java
package com.example.netcdfviewer.ui;

import com.example.netcdfviewer.basemap.BaseMapDefinition;
import com.example.netcdfviewer.basemap.BaseMapLayer;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.Window;

import java.util.Arrays;
import java.util.List;

public final class BaseMapConfigDialog {
    private BaseMapConfigDialog() {
    }

    public static Dialog<BaseMapDefinition> create(Window owner) {
        Dialog<BaseMapDefinition> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("自定义底图");
        dialog.setHeaderText("添加 XYZ 瓦片底图");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TextField nameField = new TextField("自定义底图");
        TextField urlField = new TextField("https://tiles.example.com/{z}/{x}/{y}.png");
        TextField tokenField = new TextField();
        TextField subdomainField = new TextField();
        TextField opacityField = new TextField("1.0");

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.setPadding(new Insets(10));
        grid.addRow(0, new Label("名称"), nameField);
        grid.addRow(1, new Label("URL 模板"), urlField);
        grid.addRow(2, new Label("Token"), tokenField);
        grid.addRow(3, new Label("子域名"), subdomainField);
        grid.addRow(4, new Label("透明度"), opacityField);
        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(button -> {
            if (button != ButtonType.OK) {
                return null;
            }
            String name = nameField.getText().trim();
            String template = urlField.getText().trim();
            String token = tokenField.getText().trim();
            List<String> subdomains = Arrays.stream(subdomainField.getText().split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
            double opacity = parseOpacity(opacityField.getText());
            return new BaseMapDefinition(
                "custom",
                name,
                List.of(new BaseMapLayer(name, template, token, subdomains, opacity)),
                false
            );
        });

        return dialog;
    }

    private static double parseOpacity(String value) {
        try {
            return Double.parseDouble(value.trim());
        } catch (Exception exception) {
            return 1.0;
        }
    }
}
```

- [ ] **Step 5: Run focused test and commit**

Run: `mvn -q -Dtest=MainViewLayoutStabilityTest test`

Expected: PASS.

Commit:

```bash
git add src/main/java/com/example/netcdfviewer/ui/MainView.java src/main/java/com/example/netcdfviewer/ui/BaseMapConfigDialog.java src/test/java/com/example/netcdfviewer/ui/MainViewLayoutStabilityTest.java
git commit -m "Add basemap menu"
```

## Task 4: Wire Basemap Rendering Into MainController

**Files:**
- Modify: `src/main/java/com/example/netcdfviewer/ui/MainController.java`
- Modify: `src/test/java/com/example/netcdfviewer/ui/MainControllerLoadFileTest.java`

- [ ] **Step 1: Write failing controller tests**

Modify `src/test/java/com/example/netcdfviewer/ui/MainControllerLoadFileTest.java` by adding:

```java
@Test
void selectingOsmBasemapKeepsStructuredDatasetRenderable() throws Exception {
    CountDownLatch initLatch = new CountDownLatch(1);
    AtomicReference<Throwable> errorRef = new AtomicReference<>();
    AtomicReference<MainView> viewRef = new AtomicReference<>();
    AtomicReference<Stage> stageRef = new AtomicReference<>();

    Platform.runLater(() -> {
        try {
            Stage stage = new Stage();
            MainView view = new MainView();
            MainController controller = new MainController(stage, view);
            controller.initialize();
            stage.setScene(new Scene(view, 1440, 900));
            stage.show();

            Method openFile = MainController.class.getDeclaredMethod("openFile", Path.class);
            openFile.setAccessible(true);
            openFile.invoke(controller, SampleDatasetPaths.resolve("XTPY-wrf.nc"));
            view.getOsmBaseMapMenuItem().fire();

            viewRef.set(view);
            stageRef.set(stage);
        } catch (Throwable throwable) {
            errorRef.set(throwable);
        } finally {
            initLatch.countDown();
        }
    });

    assertTrue(initLatch.await(10, TimeUnit.SECONDS));
    if (errorRef.get() != null) {
        throw new AssertionError(errorRef.get());
    }

    waitForRender(viewRef.get());
    closeStage(stageRef.get());

    assertFalse(viewRef.get().getOverlayLabel().isVisible());
    assertTrue(viewRef.get().getStatusLabel().getText().contains("已渲染")
        || viewRef.get().getStatusLabel().getText().contains("底图"));
}
```

- [ ] **Step 2: Run focused test and verify it fails**

Run: `mvn -q -Dtest=MainControllerLoadFileTest#selectingOsmBasemapKeepsStructuredDatasetRenderable test`

Expected: FAIL because menu actions and render frame fields are not wired.

- [ ] **Step 3: Add controller fields and initialization**

Modify `src/main/java/com/example/netcdfviewer/ui/MainController.java`.

Add imports:

```java
import com.example.netcdfviewer.basemap.BaseMapDefinition;
import com.example.netcdfviewer.basemap.BaseMapPreset;
import com.example.netcdfviewer.basemap.BaseMapRenderResult;
import com.example.netcdfviewer.basemap.BaseMapSelection;
import com.example.netcdfviewer.basemap.TileCache;
import com.example.netcdfviewer.basemap.TileClient;
import com.example.netcdfviewer.basemap.TileRenderer;
```

Add fields near renderers:

```java
// 在线底图瓦片渲染器。
private final TileRenderer tileRenderer = new TileRenderer(TileClient.http(new TileCache(
    Paths.get(System.getProperty("user.home", ".")).resolve(".netcdf-viewer").resolve("tile-cache"),
    512
)));
// 当前底图选择。
private BaseMapSelection currentBaseMapSelection = BaseMapSelection.none();
// 当前自定义底图配置。
private BaseMapDefinition customBaseMapDefinition;
// 最近一次成功渲染的在线底图。
private WritableImage latestBaseMapImage;
```

In `initialize()`, set basemap defaults:

```java
view.getClearCustomBaseMapMenuItem().setDisable(true);
```

- [ ] **Step 4: Wire basemap menu actions**

In `wireActions()`, add:

```java
view.getNoBaseMapMenuItem().setOnAction(event -> selectNoBaseMap());
view.getOsmBaseMapMenuItem().setOnAction(event -> selectPresetBaseMap(BaseMapPreset.OSM));
view.getTiandituVectorBaseMapMenuItem().setOnAction(event -> selectPresetBaseMap(BaseMapPreset.TIANDITU_VECTOR));
view.getTiandituImageBaseMapMenuItem().setOnAction(event -> selectPresetBaseMap(BaseMapPreset.TIANDITU_IMAGE));
view.getTiandituTerrainBaseMapMenuItem().setOnAction(event -> selectPresetBaseMap(BaseMapPreset.TIANDITU_TERRAIN));
view.getCustomBaseMapMenuItem().setOnAction(event -> configureCustomBaseMap());
view.getClearCustomBaseMapMenuItem().setOnAction(event -> clearCustomBaseMap());
```

Add methods:

```java
private void selectNoBaseMap() {
    currentBaseMapSelection = BaseMapSelection.none();
    latestBaseMapImage = null;
    setStatus("已关闭底图。");
    renderCurrentSelection();
}

private void selectPresetBaseMap(BaseMapPreset preset) {
    String token = "";
    currentBaseMapSelection = new BaseMapSelection(preset.create(token));
    latestBaseMapImage = null;
    setStatus("已选择底图：" + preset.displayName());
    renderCurrentSelection();
}

private void configureCustomBaseMap() {
    BaseMapConfigDialog.create(stage).showAndWait().ifPresent(definition -> {
        customBaseMapDefinition = definition;
        currentBaseMapSelection = new BaseMapSelection(definition);
        view.getClearCustomBaseMapMenuItem().setDisable(false);
        latestBaseMapImage = null;
        setStatus("已选择底图：" + definition.displayName());
        renderCurrentSelection();
    });
}

private void clearCustomBaseMap() {
    customBaseMapDefinition = null;
    if (currentBaseMapSelection.enabled() && "custom".equals(currentBaseMapSelection.definition().id())) {
        currentBaseMapSelection = BaseMapSelection.none();
    }
    view.getClearCustomBaseMapMenuItem().setDisable(true);
    latestBaseMapImage = null;
    setStatus("已清除自定义底图参数。");
    renderCurrentSelection();
}
```

This first controller pass uses empty Tianditu token. Task 5 adds token prompt behavior before final manual testing.

- [ ] **Step 5: Add basemap future to renderAsync**

Inside `renderAsync`, before `baseImageFuture`, add:

```java
CompletableFuture<BaseMapRenderResult> baseMapFuture = CompletableFuture.supplyAsync(
    () -> tileRenderer.render(currentBaseMapSelection, spatialDomain, snapshot, width, height),
    renderComputeExecutor
);
```

After `BufferedImage bufferedImage = baseImageFuture.join();`, add:

```java
BaseMapRenderResult baseMapResult = baseMapFuture.join();
```

Update `RenderFrame` construction:

```java
return new RenderFrame(
    baseMapResult.image() == null ? null : SwingFXUtils.toFXImage(baseMapResult.image(), null),
    SwingFXUtils.toFXImage(bufferedImage, null),
    waveResult.frame(),
    flowResult.frame(),
    windResult.frame(),
    mergeOverlayMessages(baseMapResult.message(), waveResult.message(), flowResult.message(), windResult.message())
);
```

Update `RenderFrame` record:

```java
private record RenderFrame(
    WritableImage baseMapImage,
    WritableImage image,
    WaveOverlayFrame waveOverlayFrame,
    FlowOverlayFrame flowOverlayFrame,
    WindOverlayFrame windOverlayFrame,
    String overlayMessage
) {
}
```

In `setOnSucceeded`, assign:

```java
latestBaseMapImage = frame.baseMapImage();
```

When clearing image caches in `renderCurrentSelection`, clear `latestBaseMapImage` together with `latestBaseImage`.

- [ ] **Step 6: Draw basemap before NetCDF scalar layer**

Modify `drawLatestFrame(boolean refreshCompositeCache)`:

```java
canvas.getGraphicsContext2D().clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
if (latestBaseMapImage != null) {
    canvas.getGraphicsContext2D().drawImage(latestBaseMapImage, 0, 0, canvas.getWidth(), canvas.getHeight());
}
canvas.getGraphicsContext2D().drawImage(latestBaseImage, 0, 0, canvas.getWidth(), canvas.getHeight());
```

- [ ] **Step 7: Run focused controller test and commit**

Run: `mvn -q -Dtest=MainControllerLoadFileTest#selectingOsmBasemapKeepsStructuredDatasetRenderable test`

Expected: PASS.

Commit:

```bash
git add src/main/java/com/example/netcdfviewer/ui/MainController.java src/test/java/com/example/netcdfviewer/ui/MainControllerLoadFileTest.java
git commit -m "Wire basemap rendering"
```

## Task 5: Finish Tianditu Token Handling, Docs, and Full Verification

**Files:**
- Modify: `src/main/java/com/example/netcdfviewer/ui/BaseMapConfigDialog.java`
- Modify: `src/main/java/com/example/netcdfviewer/ui/MainController.java`
- Modify: `README.md`
- Modify: `CHANGELOG.md`
- Test: existing suites

- [ ] **Step 1: Add token prompt helper**

In `MainController`, add:

```java
private String promptBaseMapToken(String title) {
    javafx.scene.control.TextInputDialog dialog = new javafx.scene.control.TextInputDialog();
    dialog.initOwner(stage);
    dialog.setTitle(title);
    dialog.setHeaderText(title);
    dialog.setContentText("请输入天地图 token：");
    return dialog.showAndWait().map(String::trim).orElse("");
}
```

Change `selectPresetBaseMap`:

```java
private void selectPresetBaseMap(BaseMapPreset preset) {
    String token = preset == BaseMapPreset.OSM ? "" : promptBaseMapToken(preset.displayName());
    currentBaseMapSelection = new BaseMapSelection(preset.create(token));
    latestBaseMapImage = null;
    setStatus("已选择底图：" + preset.displayName());
    renderCurrentSelection();
}
```

- [ ] **Step 2: Update README**

In `README.md`, add to the feature list:

```markdown
- 支持 OpenStreetMap、天地图和自定义 XYZ URL 在线底图
```

Add a usage subsection under feature descriptions:

```markdown
### 在线底图

“底图”菜单支持无底图、OpenStreetMap 标准图、天地图矢量图、天地图影像图、天地图地形图和自定义底图。

自定义底图 URL 模板支持 `{z}`、`{x}`、`{y}`、`{s}`、`{tk}` 占位符。

天地图需要用户自己的 token。token 不写入仓库，也不会作为默认值打包进程序。
```

- [ ] **Step 3: Update CHANGELOG**

Add an unreleased section at the top:

```markdown
## 未发布

- 新增在线底图叠加，支持 OpenStreetMap、天地图矢量 / 影像 / 地形和自定义 XYZ URL 模板
- 底图渲染接入现有 Canvas 渲染链路，位于 NetCDF 标量场下方，不影响单点查询和叠加层
- 新增瓦片 URL 模板、Web Mercator 瓦片换算、缓存和渲染测试
```

- [ ] **Step 4: Run focused basemap tests**

Run:

```bash
mvn -q -Dtest=TileMathTest,TileUrlTemplateTest,TileCacheTest,TileRendererTest test
```

Expected: PASS.

- [ ] **Step 5: Run controller and UI focused tests**

Run:

```bash
mvn -q -Dtest=MainViewLayoutStabilityTest,MainControllerLoadFileTest test
```

Expected: PASS.

- [ ] **Step 6: Run full test suite**

Run:

```bash
mvn -q test
```

Expected: PASS.

- [ ] **Step 7: Run compile check**

Run:

```bash
mvn -q -DskipTests compile
```

Expected: PASS.

- [ ] **Step 8: Scan for Chinese mojibake markers**

Run:

```bash
rg -n "�|Ã|Â|Ð|Î|Ê|Ò|Ó|µ|¼|¾|£|Å|Ç|Ñ|Ö" README.md CHANGELOG.md src/main/java src/test/java
```

Expected: no matches.

- [ ] **Step 9: Commit docs and polish**

Commit:

```bash
git add README.md CHANGELOG.md src/main/java/com/example/netcdfviewer/ui/MainController.java src/main/java/com/example/netcdfviewer/ui/BaseMapConfigDialog.java
git commit -m "Document basemap support"
```

## Implementation Notes

- Do not commit `desktop.ini` or old untracked installers.
- Keep all Chinese text UTF-8.
- Do not write any Tianditu token into source code, tests, docs, or commits.
- New public methods that hold multi-step logic should follow the existing large block comment plus `logger.info` style used by `MainController`.
- Tile network failures must return `null` tile images or status messages. They must not fail NetCDF rendering.
- PNG export already snapshots `visualizationBox`; once `drawLatestFrame` includes `latestBaseMapImage`, export includes the basemap without extra export code.

## Self-Review

- Spec coverage: The plan covers OSM, Tianditu vector/image/terrain, custom XYZ URL templates, token entry, geographic-only rendering, memory/disk caching, network failure skipping, render order, navigation preview, PNG export, tests, README, and CHANGELOG.
- Placeholder scan: No placeholder markers or unspecified implementation steps remain.
- Type consistency: The same names are used across tasks: `BaseMapDefinition`, `BaseMapLayer`, `BaseMapSelection`, `BaseMapPreset`, `TileAddress`, `TileMath`, `TileUrlTemplate`, `TileCache`, `TileClient`, `TileRenderer`, and `BaseMapRenderResult`.
