package com.example.netcdfviewer.model;

import java.util.Arrays;

public record ScalarLayer(VariableInfo variable, int layerIndex, double[] values) {
    public ScalarLayer {
        values = Arrays.copyOf(values, values.length);
    }
}
