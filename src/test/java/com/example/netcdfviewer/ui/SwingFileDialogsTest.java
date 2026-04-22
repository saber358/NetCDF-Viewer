package com.example.netcdfviewer.ui;

import org.junit.jupiter.api.Test;

import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SwingFileDialogsTest {
    @Test
    void openChooserUsesNcExtensionFilter() {
        JFileChooser chooser = SwingFileDialogs.createOpenChooser(Path.of("."));

        assertEquals("打开 NetCDF 文件", chooser.getDialogTitle());
        assertFalse(chooser.isAcceptAllFileFilterUsed());
        assertTrue(chooser.getFileFilter() instanceof FileNameExtensionFilter);
        FileNameExtensionFilter filter = (FileNameExtensionFilter) chooser.getFileFilter();
        assertEquals("nc", filter.getExtensions()[0]);
    }

    @Test
    void saveChooserUsesPngExtensionFilter() {
        JFileChooser chooser = SwingFileDialogs.createSaveChooser(Path.of("."));

        assertEquals("导出 PNG", chooser.getDialogTitle());
        assertFalse(chooser.isAcceptAllFileFilterUsed());
        assertTrue(chooser.getFileFilter() instanceof FileNameExtensionFilter);
        FileNameExtensionFilter filter = (FileNameExtensionFilter) chooser.getFileFilter();
        assertEquals("png", filter.getExtensions()[0]);
    }
}
