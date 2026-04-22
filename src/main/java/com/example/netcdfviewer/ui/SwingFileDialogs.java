package com.example.netcdfviewer.ui;

import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 基于 Swing 的文件对话框工具。
 * 之所以使用 Swing 而不是 JavaFX FileChooser，是为了在当前打包环境下获得更稳定的表现。
 */
public final class SwingFileDialogs {
    private SwingFileDialogs() {
        // 工具类不允许被实例化。
    }

    static JFileChooser createOpenChooser(Path initialDirectory) {
        // 创建打开文件对话框。
        JFileChooser chooser = new JFileChooser();
        // 设置对话框标题。
        chooser.setDialogTitle("打开 NetCDF 文件");
        // 只允许选择文件。
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        // 禁用“所有文件”过滤器，避免误选。
        chooser.setAcceptAllFileFilterUsed(false);
        // 仅允许选择 .nc 文件。
        chooser.setFileFilter(new FileNameExtensionFilter("NetCDF 文件 (*.nc)", "nc"));
        // 如果初始目录存在，则作为默认打开路径。
        if (initialDirectory != null && Files.isDirectory(initialDirectory)) {
            chooser.setCurrentDirectory(initialDirectory.toFile());
        }
        return chooser;
    }

    static JFileChooser createSaveChooser(Path initialDirectory) {
        // 创建保存文件对话框。
        JFileChooser chooser = new JFileChooser();
        // 设置对话框标题。
        chooser.setDialogTitle("导出 PNG");
        // 只允许选择文件。
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        // 禁用“所有文件”过滤器，减少误操作。
        chooser.setAcceptAllFileFilterUsed(false);
        // 仅提示保存为 PNG。
        chooser.setFileFilter(new FileNameExtensionFilter("PNG 图像 (*.png)", "png"));
        // 如果初始目录存在，则作为默认保存路径。
        if (initialDirectory != null && Files.isDirectory(initialDirectory)) {
            chooser.setCurrentDirectory(initialDirectory.toFile());
        }
        return chooser;
    }

    static JFileChooser createOpenCoastlineChooser(Path initialDirectory) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("加载海岸线叠加层");
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setFileFilter(new FileNameExtensionFilter(
            "海岸线文件 (*.geojson, *.json, *.shp)",
            "geojson",
            "json",
            "shp"
        ));
        if (initialDirectory != null && Files.isDirectory(initialDirectory)) {
            chooser.setCurrentDirectory(initialDirectory.toFile());
        }
        return chooser;
    }

    public static Path chooseOpenFile(Path initialDirectory) {
        // 统一在 Swing EDT 线程中弹出打开文件对话框。
        return invokeOnEdt(() -> {
            JFileChooser chooser = createOpenChooser(initialDirectory);
            int result = chooser.showOpenDialog(null);
            // 如果用户取消或没有选中文件，则返回 null。
            if (result != JFileChooser.APPROVE_OPTION || chooser.getSelectedFile() == null) {
                return null;
            }
            // 返回所选文件路径。
            return chooser.getSelectedFile().toPath();
        });
    }

    public static Path chooseSavePngFile(Path initialDirectory) {
        // 统一在 Swing EDT 线程中弹出保存文件对话框。
        return invokeOnEdt(() -> {
            JFileChooser chooser = createSaveChooser(initialDirectory);
            int result = chooser.showSaveDialog(null);
            // 如果用户取消或没有选中文件，则返回 null。
            if (result != JFileChooser.APPROVE_OPTION || chooser.getSelectedFile() == null) {
                return null;
            }
            // 取出用户输入的保存路径。
            File file = chooser.getSelectedFile();
            String path = file.getAbsolutePath();
            // 如果用户没有手动写 .png 扩展名，则自动补上。
            if (!path.toLowerCase().endsWith(".png")) {
                file = new File(path + ".png");
            }
            return file.toPath();
        });
    }

    public static Path chooseOpenCoastlineFile(Path initialDirectory) {
        return invokeOnEdt(() -> {
            JFileChooser chooser = createOpenCoastlineChooser(initialDirectory);
            int result = chooser.showOpenDialog(null);
            if (result != JFileChooser.APPROVE_OPTION || chooser.getSelectedFile() == null) {
                return null;
            }
            return chooser.getSelectedFile().toPath();
        });
    }

    private static <T> T invokeOnEdt(DialogAction<T> action) {
        // 如果当前已经位于 EDT，则直接执行。
        if (SwingUtilities.isEventDispatchThread()) {
            return action.run();
        }
        // 用于接收执行结果。
        AtomicReference<T> result = new AtomicReference<>();
        // 用于接收执行异常。
        AtomicReference<RuntimeException> error = new AtomicReference<>();
        try {
            // 切换到 EDT 执行动作，确保 Swing 线程安全。
            SwingUtilities.invokeAndWait(() -> {
                try {
                    result.set(action.run());
                } catch (RuntimeException exception) {
                    error.set(exception);
                }
            });
        } catch (InterruptedException exception) {
            // 如果线程被打断，则恢复中断标记并抛出异常。
            Thread.currentThread().interrupt();
            throw new IllegalStateException("文件对话框被中断。", exception);
        } catch (InvocationTargetException exception) {
            // 如果 EDT 内部执行失败，则把原始异常原因向外抛出。
            throw new IllegalStateException("文件对话框打开失败。", exception.getCause());
        }
        // 如果执行过程中捕获到运行时异常，则继续向外抛出。
        if (error.get() != null) {
            throw error.get();
        }
        // 返回动作执行结果。
        return result.get();
    }

    @FunctionalInterface
    private interface DialogAction<T> {
        // 定义一个可在 EDT 中执行的回调接口。
        T run();
    }
}
