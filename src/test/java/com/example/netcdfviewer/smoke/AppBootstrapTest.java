package com.example.netcdfviewer.smoke;

import com.example.netcdfviewer.App;
import com.example.netcdfviewer.AppMetadata;
import com.example.netcdfviewer.ui.MainView;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.stage.Stage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppBootstrapTest {
    @BeforeAll
    static void initToolkit() {
        new JFXPanel();
        Platform.setImplicitExit(false);
    }

    @Test
    void bootstrapViewInitializesMainControllerState() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<MainView> viewRef = new AtomicReference<>();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                MainView view = App.createMainView(new Stage());
                viewRef.set(view);
            } catch (Throwable throwable) {
                errorRef.set(throwable);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(10, TimeUnit.SECONDS));
        if (errorRef.get() != null) {
            throw new AssertionError(errorRef.get());
        }

        MainView view = viewRef.get();
        assertEquals("Ready to open NetCDF file.", view.getStatusLabel().getText());
        assertTrue(view.getExportButton().isDisabled());
        assertTrue(view.getVisualizeButton().isDisabled());
        assertEquals("Viridis", view.getColorMapCombo().getValue());
        assertEquals("1.0.2", AppMetadata.VERSION);
        assertEquals("lwj", AppMetadata.AUTHOR_NAME);
        assertEquals("2762692204@qq.com", AppMetadata.AUTHOR_EMAIL);
        assertEquals("Author: lwj | 2762692204@qq.com", view.getAuthorLabel().getText());
        assertEquals("Help", view.getHelpMenu().getText());
        assertEquals("About", view.getAboutMenuItem().getText());
        assertNotNull(App.class.getResource("/icons/app-icon.png"));
        assertNotNull(App.class.getResource("/icons/app-icon.ico"));
    }
}
