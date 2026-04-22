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
