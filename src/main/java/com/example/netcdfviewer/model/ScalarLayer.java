package com.example.netcdfviewer.model;

import java.util.Arrays;

/**
 * 某个变量在某一层上的标量值快照。
 * 当前项目中这个对象主要作为结构化封装保留，便于后续扩展。
 */
public record ScalarLayer(VariableInfo variable, int layerIndex, double[] values) {
    public ScalarLayer {
        // 复制数值数组，避免外部修改影响内部状态。
        values = Arrays.copyOf(values, values.length);
    }
}
