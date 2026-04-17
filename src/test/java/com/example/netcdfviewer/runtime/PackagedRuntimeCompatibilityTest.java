package com.example.netcdfviewer.runtime;

import com.example.netcdfviewer.testsupport.SampleDatasetPaths;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackagedRuntimeCompatibilityTest {
    @Test
    void parserWorksUnderPackagedRuntimeModuleSet() throws Exception {
        Process process = packagedProcess(
            ParserProbeMain.class.getName(),
            SampleDatasetPaths.resolve("HBHQY.nc").toString()
        );
        String output = readOutput(process);
        int exitCode = process.waitFor();

        assertEquals(0, exitCode, output);
        assertTrue(output.contains("mesh=true"), output);
    }

    @Test
    void exportWorksUnderPackagedRuntimeModuleSet() throws Exception {
        Path outputFile = Path.of("target", "packaged-export-probe.png").toAbsolutePath();
        Files.deleteIfExists(outputFile);

        Process process = packagedProcess(
            ExportProbeMain.class.getName(),
            SampleDatasetPaths.resolve("ydw.nc").toString(),
            outputFile.toString()
        );
        String output = readOutput(process);
        int exitCode = process.waitFor();

        assertEquals(0, exitCode, output);
        assertTrue(Files.isRegularFile(outputFile), output);
        assertTrue(Files.size(outputFile) > 0L, output);
    }

    @Test
    void packageScriptDerivesVersionFromPomAndPreservesIconMetadata() throws Exception {
        String script = Files.readString(Path.of("scripts", "package-exe.ps1"), StandardCharsets.UTF_8);

        assertTrue(script.contains("function Get-ProjectVersion"));
        assertTrue(script.contains("function Get-ProjectArtifactId"));
        assertTrue(script.contains("--app-version $appVersion"));
        assertTrue(script.contains("--vendor lwj"));
        assertTrue(script.contains("--icon"));
        assertTrue(script.contains("app-icon.ico"));
        assertFalse(script.contains("1.0.2"));
    }

    private String m2(String... parts) {
        Path path = Path.of(System.getProperty("user.home"), ".m2", "repository");
        for (String part : parts) {
            path = path.resolve(part);
        }
        return path.toAbsolutePath().toString();
    }

    private Process packagedProcess(String mainClass, String... args) throws Exception {
        String classpath = System.getProperty("java.class.path");
        String javafxModulePath = String.join(File.pathSeparator,
            m2("org", "openjfx", "javafx-base", "21.0.8", "javafx-base-21.0.8-win.jar"),
            m2("org", "openjfx", "javafx-graphics", "21.0.8", "javafx-graphics-21.0.8-win.jar"),
            m2("org", "openjfx", "javafx-controls", "21.0.8", "javafx-controls-21.0.8-win.jar"),
            m2("org", "openjfx", "javafx-swing", "21.0.8", "javafx-swing-21.0.8-win.jar")
        );

        String[] command = new String[8 + args.length];
        command[0] = Path.of(System.getProperty("java.home"), "bin", "java.exe").toString();
        command[1] = "--module-path";
        command[2] = javafxModulePath;
        command[3] = "--limit-modules";
        command[4] = "java.base,java.compiler,java.datatransfer,java.desktop,java.logging,java.naming,java.prefs,java.security.jgss,java.xml,javafx.base,javafx.graphics,javafx.controls,javafx.swing,jdk.jfr,jdk.unsupported,jdk.unsupported.desktop";
        command[5] = "-cp";
        command[6] = classpath;
        command[7] = mainClass;
        System.arraycopy(args, 0, command, 8, args.length);

        return new ProcessBuilder(command)
            .directory(Path.of(".").toFile())
            .redirectErrorStream(true)
            .start();
    }

    private String readOutput(Process process) throws Exception {
        return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }
}
