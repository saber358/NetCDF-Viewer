package com.example.netcdfviewer.overlay;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * GeoJSON 海岸线叠加解析器。
 * 只提取线和面边界为描边路径。
 */
public final class GeoJsonOverlayLoader {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private GeoJsonOverlayLoader() {
        // 工具类不允许被实例化。
    }

    public static CoastlineOverlay load(Path path) throws IOException {
        JsonNode root = OBJECT_MAPPER.readTree(path.toFile());
        List<OverlayPath> paths = new ArrayList<>();
        collectGeometry(root, paths);
        return new CoastlineOverlay(path.toAbsolutePath().normalize(), path.getFileName().toString(), paths);
    }

    private static void collectGeometry(JsonNode node, List<OverlayPath> paths) {
        if (node == null || node.isNull()) {
            return;
        }
        String type = text(node.get("type"));
        if (type == null) {
            return;
        }
        switch (type) {
            case "FeatureCollection" -> {
                JsonNode features = node.get("features");
                if (features != null && features.isArray()) {
                    for (JsonNode feature : features) {
                        collectGeometry(feature, paths);
                    }
                }
            }
            case "Feature" -> collectGeometry(node.get("geometry"), paths);
            case "GeometryCollection" -> {
                JsonNode geometries = node.get("geometries");
                if (geometries != null && geometries.isArray()) {
                    for (JsonNode geometry : geometries) {
                        collectGeometry(geometry, paths);
                    }
                }
            }
            case "LineString" -> addLineString(node.get("coordinates"), paths);
            case "MultiLineString" -> addMultiLineString(node.get("coordinates"), paths);
            case "Polygon" -> addPolygon(node.get("coordinates"), paths);
            case "MultiPolygon" -> addMultiPolygon(node.get("coordinates"), paths);
            default -> {
                // 当前版本忽略点要素和其他不支持的几何类型。
            }
        }
    }

    private static void addLineString(JsonNode coordinates, List<OverlayPath> paths) {
        OverlayPath path = toPath(coordinates);
        if (path != null) {
            paths.add(path);
        }
    }

    private static void addMultiLineString(JsonNode coordinates, List<OverlayPath> paths) {
        if (coordinates == null || !coordinates.isArray()) {
            return;
        }
        for (JsonNode line : coordinates) {
            addLineString(line, paths);
        }
    }

    private static void addPolygon(JsonNode coordinates, List<OverlayPath> paths) {
        if (coordinates == null || !coordinates.isArray()) {
            return;
        }
        for (JsonNode ring : coordinates) {
            addLineString(ring, paths);
        }
    }

    private static void addMultiPolygon(JsonNode coordinates, List<OverlayPath> paths) {
        if (coordinates == null || !coordinates.isArray()) {
            return;
        }
        for (JsonNode polygon : coordinates) {
            addPolygon(polygon, paths);
        }
    }

    private static OverlayPath toPath(JsonNode coordinates) {
        if (coordinates == null || !coordinates.isArray()) {
            return null;
        }
        List<Double> xs = new ArrayList<>();
        List<Double> ys = new ArrayList<>();
        for (JsonNode point : coordinates) {
            if (point == null || !point.isArray() || point.size() < 2) {
                continue;
            }
            JsonNode xNode = point.get(0);
            JsonNode yNode = point.get(1);
            if (xNode == null || yNode == null || !xNode.isNumber() || !yNode.isNumber()) {
                continue;
            }
            xs.add(xNode.doubleValue());
            ys.add(yNode.doubleValue());
        }
        if (xs.size() < 2) {
            return null;
        }
        double[] x = new double[xs.size()];
        double[] y = new double[ys.size()];
        for (int index = 0; index < xs.size(); index++) {
            x[index] = xs.get(index);
            y[index] = ys.get(index);
        }
        return new OverlayPath(x, y);
    }

    private static String text(JsonNode node) {
        if (node == null || !node.isTextual()) {
            return null;
        }
        return node.textValue().trim();
    }
}
