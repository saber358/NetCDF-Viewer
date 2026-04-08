package com.example.netcdfviewer.smoke;

import com.example.netcdfviewer.App;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AppSmokeTest {
    @Test
    void appClassExposesTitleConstant() {
        assertEquals("NetCDF Viewer", App.APP_NAME);
    }
}
