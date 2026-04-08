package com.example.netcdfviewer.render;

import javafx.scene.paint.Color;

/**
 * 颜色映射接口。
 * 输入为 0 到 1 之间的归一化数值，输出为对应的颜色。
 */
@FunctionalInterface
public interface ColorMap {
    // 根据归一化值返回颜色。
    Color colorAt(double normalizedValue);
}
