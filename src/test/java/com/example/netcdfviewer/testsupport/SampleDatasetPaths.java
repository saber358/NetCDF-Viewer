package com.example.netcdfviewer.testsupport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public final class SampleDatasetPaths {
    private static final Logger logger = LoggerFactory.getLogger(SampleDatasetPaths.class);

    private SampleDatasetPaths() {
    }

    /*
     * ========================================================================
     * 步骤1：解析单个样例文件路径
     * ========================================================================
     * 目标：
     *   1) 从当前目录或指定目录开始向上查找样例文件
     *   2) 兼容主工作区和 .worktrees 子工作区
     * 操作要点：
     *   1) 逐级检查当前目录及其父目录
     *   2) 找到后返回绝对规范路径
     */
    public static Path resolve(String fileName) {
        logger.info("开始解析样例文件路径, fileName={}", fileName);

        // 1.1 从当前工作目录开始查找样例文件。
        Path resolved = resolve(Path.of("."), fileName);

        logger.info("样例文件路径解析完成, fileName={}, resolved={}", fileName, resolved);
        return resolved;
    }

    /*
     * ========================================================================
     * 步骤2：从指定起点回溯样例文件
     * ========================================================================
     * 目标：
     *   1) 支持测试显式传入起始目录
     *   2) 给缺失样例文件提供清晰报错
     * 操作要点：
     *   1) 记录完整搜索链路
     *   2) 未命中时抛出 IllegalArgumentException
     */
    static Path resolve(Path startDirectory, String fileName) {
        logger.info("开始从指定目录解析样例文件, startDirectory={}, fileName={}", startDirectory, fileName);

        // 2.1 校验样例文件名，避免空字符串导致搜索链路无意义。
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("Sample file name must not be blank.");
        }

        // 2.2 规范化起始目录，保证后续父目录回溯稳定。
        Path current = startDirectory.toAbsolutePath().normalize();
        List<Path> searchedDirectories = new ArrayList<>();

        // 2.3 逐级向上查找目标样例文件。
        while (current != null) {
            searchedDirectories.add(current);
            Path candidate = current.resolve(fileName);
            if (Files.isRegularFile(candidate)) {
                Path resolved = candidate.toAbsolutePath().normalize();
                logger.info("从指定目录解析样例文件完成, fileName={}, resolved={}", fileName, resolved);
                return resolved;
            }
            current = current.getParent();
        }

        logger.info("指定目录样例文件解析失败, fileName={}, searchedDirectories={}", fileName, searchedDirectories);
        throw new IllegalArgumentException("Could not find sample dataset " + fileName + " under " + searchedDirectories);
    }

    /*
     * ========================================================================
     * 步骤3：定位最近的样例目录
     * ========================================================================
     * 目标：
     *   1) 找到当前目录或上层目录中最近的 .nc 样例目录
     *   2) 给批量样例测试提供稳定入口
     * 操作要点：
     *   1) 逐级回溯目录
     *   2) 只认当前层级下的 .nc 文件，不做递归
     */
    public static Path findNearestDirectoryContainingNcFiles() {
        logger.info("开始定位最近的样例目录");

        // 3.1 从当前工作目录开始向上搜索包含 .nc 文件的目录。
        Path resolved = findNearestDirectoryContainingNcFiles(Path.of("."));

        logger.info("最近的样例目录定位完成, directory={}", resolved);
        return resolved;
    }

    /*
     * ========================================================================
     * 步骤4：从指定起点回溯样例目录
     * ========================================================================
     * 目标：
     *   1) 支持测试显式传入起始目录
     *   2) 在 worktree 场景下回到主工作区样例目录
     * 操作要点：
     *   1) 每层目录都尝试扫描 .nc 文件
     *   2) 首次命中即返回
     */
    public static Path findNearestDirectoryContainingNcFiles(Path startDirectory) {
        logger.info("开始从指定目录定位最近的样例目录, startDirectory={}", startDirectory);

        // 4.1 规范化起始目录，保证目录回溯稳定。
        Path current = startDirectory.toAbsolutePath().normalize();
        List<Path> searchedDirectories = new ArrayList<>();

        // 4.2 逐级检查目录中是否存在 .nc 文件。
        while (current != null) {
            searchedDirectories.add(current);
            if (containsNcFiles(current)) {
                logger.info("从指定目录定位样例目录完成, directory={}", current);
                return current;
            }
            current = current.getParent();
        }

        logger.info("指定目录样例目录定位失败, searchedDirectories={}", searchedDirectories);
        throw new IllegalArgumentException("Could not find any .nc sample directory under " + searchedDirectories);
    }

    /*
     * ========================================================================
     * 步骤5：列出当前样例目录下的 .nc 文件
     * ========================================================================
     * 目标：
     *   1) 给本地样例遍历测试提供统一入口
     *   2) 维持既有按文件大小排序的行为
     * 操作要点：
     *   1) 只读取单层目录
     *   2) 忽略非 .nc 文件
     */
    public static List<Path> findNcFiles(Path directory) throws IOException {
        logger.info("开始列出样例目录下的 .nc 文件, directory={}", directory);

        // 5.1 扫描目录下文件并筛选 .nc 样例。
        try (Stream<Path> files = Files.list(directory)) {
            List<Path> resolved = files
                .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".nc"))
                .sorted(Comparator.comparingLong(SampleDatasetPaths::safeSize))
                .map(path -> path.toAbsolutePath().normalize())
                .toList();
            logger.info("样例目录扫描完成, directory={}, count={}", directory, resolved.size());
            return resolved;
        }
    }

    private static boolean containsNcFiles(Path directory) {
        try {
            return !findNcFiles(directory).isEmpty();
        } catch (IOException exception) {
            return false;
        }
    }

    private static long safeSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException exception) {
            return Long.MAX_VALUE;
        }
    }
}
