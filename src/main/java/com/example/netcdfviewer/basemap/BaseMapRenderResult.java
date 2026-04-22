package com.example.netcdfviewer.basemap;

import java.awt.image.BufferedImage;

public record BaseMapRenderResult(BufferedImage image, String message) {
    public static BaseMapRenderResult empty() {
        return new BaseMapRenderResult(null, null);
    }
}
