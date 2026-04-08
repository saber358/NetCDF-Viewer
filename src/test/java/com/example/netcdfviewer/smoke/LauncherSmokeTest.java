package com.example.netcdfviewer.smoke;

import javafx.application.Application;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class LauncherSmokeTest {
    @Test
    void launcherEntryPointMustNotExtendJavaFxApplication() throws Exception {
        Class<?> launcherClass = Class.forName("com.example.netcdfviewer.Launcher");

        assertNotNull(launcherClass.getMethod("main", String[].class));
        assertFalse(Application.class.isAssignableFrom(launcherClass));
    }
}
