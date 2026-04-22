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
