package com.example.netcdfviewer.render;

import javafx.scene.paint.Color;

@FunctionalInterface
public interface ColorMap {
    Color colorAt(double normalizedValue);
}
