package com.example.netcdfviewer.smoke;

import com.example.netcdfviewer.App;
import com.example.netcdfviewer.AppMetadata;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Properties;

import javax.xml.parsers.DocumentBuilderFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppMetadataResourceTest {
    @Test
    void filteredMetadataResourceMatchesPomVersion() throws Exception {
        Properties properties = new Properties();
        try (InputStream inputStream = App.class.getResourceAsStream("/app-metadata.properties")) {
            assertNotNull(inputStream, "Filtered metadata resource should exist.");
            properties.load(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
        }

        assertEquals(readPomVersion(), properties.getProperty("app.version"));
        assertEquals(properties.getProperty("app.name"), AppMetadata.APP_NAME);
        assertEquals(properties.getProperty("app.version"), AppMetadata.VERSION);
        assertEquals(properties.getProperty("app.description"), AppMetadata.DESCRIPTION);
    }

    @Test
    void pomConfiguresUtf8ForFilteredPropertiesResources() throws Exception {
        String pom = java.nio.file.Files.readString(Path.of("pom.xml"));

        assertTrue(pom.contains("<artifactId>maven-resources-plugin</artifactId>"));
        assertTrue(pom.contains("<propertiesEncoding>${project.build.sourceEncoding}</propertiesEncoding>"));
    }

    private String readPomVersion() throws Exception {
        var documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        var document = documentBuilder.parse(Path.of("pom.xml").toFile());
        return document.getElementsByTagName("version").item(0).getTextContent().trim();
    }
}
