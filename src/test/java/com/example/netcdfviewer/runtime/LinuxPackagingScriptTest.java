package com.example.netcdfviewer.runtime;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LinuxPackagingScriptTest {
    @Test
    void packageScriptTargetsLinuxDebOutput() throws Exception {
        Path scriptPath = Path.of("scripts", "package-linux-x86.ps1");

        assertTrue(Files.isRegularFile(scriptPath), "Linux packaging script should exist.");

        String script = Files.readString(scriptPath, StandardCharsets.UTF_8);
        assertTrue(script.contains("function Get-ProjectVersion"));
        assertTrue(script.contains("--type deb"));
        assertTrue(script.contains("linux-x86_64"));
        assertTrue(script.contains("javafx-base-21.0.8-linux.jar"));
        assertTrue(script.contains("NetCDFViewer-linux-x86_64-$appVersion.deb"));
        assertFalse(script.contains("1.0.2"));
    }
}
