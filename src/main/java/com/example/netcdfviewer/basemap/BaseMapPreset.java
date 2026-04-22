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
