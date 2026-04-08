package com.example.netcdfviewer.ui;

import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

public final class SwingFileDialogs {
    private SwingFileDialogs() {
    }

    static JFileChooser createOpenChooser(Path initialDirectory) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Open NetCDF File");
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setFileFilter(new FileNameExtensionFilter("NetCDF files (*.nc)", "nc"));
        if (initialDirectory != null && Files.isDirectory(initialDirectory)) {
            chooser.setCurrentDirectory(initialDirectory.toFile());
        }
        return chooser;
    }

    static JFileChooser createSaveChooser(Path initialDirectory) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export PNG");
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setFileFilter(new FileNameExtensionFilter("PNG Image (*.png)", "png"));
        if (initialDirectory != null && Files.isDirectory(initialDirectory)) {
            chooser.setCurrentDirectory(initialDirectory.toFile());
        }
        return chooser;
    }

    public static Path chooseOpenFile(Path initialDirectory) {
        return invokeOnEdt(() -> {
            JFileChooser chooser = createOpenChooser(initialDirectory);
            int result = chooser.showOpenDialog(null);
            if (result != JFileChooser.APPROVE_OPTION || chooser.getSelectedFile() == null) {
                return null;
            }
            return chooser.getSelectedFile().toPath();
        });
    }

    public static Path chooseSavePngFile(Path initialDirectory) {
        return invokeOnEdt(() -> {
            JFileChooser chooser = createSaveChooser(initialDirectory);
            int result = chooser.showSaveDialog(null);
            if (result != JFileChooser.APPROVE_OPTION || chooser.getSelectedFile() == null) {
                return null;
            }
            File file = chooser.getSelectedFile();
            String path = file.getAbsolutePath();
            if (!path.toLowerCase().endsWith(".png")) {
                file = new File(path + ".png");
            }
            return file.toPath();
        });
    }

    private static <T> T invokeOnEdt(DialogAction<T> action) {
        if (SwingUtilities.isEventDispatchThread()) {
            return action.run();
        }
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<RuntimeException> error = new AtomicReference<>();
        try {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    result.set(action.run());
                } catch (RuntimeException exception) {
                    error.set(exception);
                }
            });
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("File dialog was interrupted.", exception);
        } catch (InvocationTargetException exception) {
            throw new IllegalStateException("File dialog failed to open.", exception.getCause());
        }
        if (error.get() != null) {
            throw error.get();
        }
        return result.get();
    }

    @FunctionalInterface
    private interface DialogAction<T> {
        T run();
    }
}
