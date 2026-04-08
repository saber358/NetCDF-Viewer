package com.example.netcdfviewer.runtime;

import com.example.netcdfviewer.io.NetcdfDatasetParser;

import java.nio.file.Path;

public final class ParserProbeMain {
    private ParserProbeMain() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            throw new IllegalArgumentException("Expected a NetCDF file path.");
        }
        var parser = new NetcdfDatasetParser();
        var dataset = parser.open(Path.of(args[0]));
        System.out.println("mesh=" + dataset.hasMesh() + ", plottable=" + dataset.plottableVariables().size());
    }
}
