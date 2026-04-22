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
