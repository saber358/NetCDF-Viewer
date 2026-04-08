package com.example.netcdfviewer.ui;

import com.example.netcdfviewer.AppMetadata;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainControllerLoadFileTest {
    @BeforeAll
    static void initToolkit() {
        new JFXPanel();
        Platform.setImplicitExit(false);
    }

    @Test
    void controllerLoadsVerifiedSampleDataset() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                MainView view = new MainView();
                MainController controller = new MainController(new Stage(), view);
                controller.initialize();

                Method openFile = MainController.class.getDeclaredMethod("openFile", Path.class);
                openFile.setAccessible(true);
                openFile.invoke(controller, Path.of("ydw.nc"));

                assertTrue(view.getDatasetLabel().getText().contains("ydw.nc"));
                assertFalse(view.getVariableList().getItems().isEmpty());
                assertTrue(view.getSummaryArea().getText().contains("Connectivity:"));
                assertTrue(view.getStatusLabel().getText().contains("Loaded"));
            } catch (Throwable throwable) {
                errorRef.set(throwable);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(15, TimeUnit.SECONDS));
        if (errorRef.get() != null) {
            throw new AssertionError(errorRef.get());
        }
    }

    @Test
    void aboutContentIncludesBrandingMetadata() {
        String content = MainController.aboutContent();

        assertTrue(content.contains(AppMetadata.APP_NAME));
        assertTrue(content.contains(AppMetadata.VERSION));
        assertTrue(content.contains(AppMetadata.AUTHOR_NAME));
        assertTrue(content.contains(AppMetadata.AUTHOR_EMAIL));
    }
}
