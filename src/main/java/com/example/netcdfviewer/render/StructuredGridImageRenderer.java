package com.example.netcdfviewer.render;

import com.example.netcdfviewer.model.StructuredGridDomain;
import com.example.netcdfviewer.ui.ViewportState;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.logging.Logger;

/**
 * 标准格网离屏渲染器。
 * 当前版本支持 1D 规则轴标准格网，并使用像素缓冲写入方式提升并行渲染效率。
 */
public final class StructuredGridImageRenderer {
    private static final Logger logger = Logger.getLogger(StructuredGridImageRenderer.class.getName());
    private static final int BACKGROUND_ARGB = 0xFFF8FBFD;

    /*
     * ========================================================================
     * 步骤1：执行默认并行规则格网渲染
     * ========================================================================
     * 目标：
     *   1) 使用默认线程池渲染规则格网底图
     *   2) 保持现有调用入口不变
     * 操作要点：
     *   1) 直接委托到通用并行入口
     *   2) 默认走公共计算线程池
     */
    public BufferedImage render(
        int width,
        int height,
        StructuredGridDomain domain,
        double[] values,
        ColorMap colorMap,
        RangeStats rangeStats,
        ViewportState.Snapshot snapshot,
        boolean cellCentered,
        Double fillValue
    ) {
        return render(
            width,
            height,
            domain,
            values,
            colorMap,
            rangeStats,
            snapshot,
            cellCentered,
            fillValue,
            ForkJoinPool.commonPool()
        );
    }

    /*
     * ========================================================================
     * 步骤2：执行指定线程池规则格网渲染
     * ========================================================================
     * 目标：
     *   1) 允许主控制器把底图渲染放到共享计算线程池
     *   2) 避免每次渲染内部再创建新线程
     * 操作要点：
     *   1) 由调用方传入计算线程池
     *   2) 统一走内部像素缓冲渲染流程
     */
    public BufferedImage render(
        int width,
        int height,
        StructuredGridDomain domain,
        double[] values,
        ColorMap colorMap,
        RangeStats rangeStats,
        ViewportState.Snapshot snapshot,
        boolean cellCentered,
        Double fillValue,
        Executor renderExecutor
    ) {
        return renderInternal(
            width,
            height,
            domain,
            values,
            colorMap,
            rangeStats,
            snapshot,
            cellCentered,
            fillValue,
            renderExecutor,
            true
        );
    }

    /*
     * ========================================================================
     * 步骤3：执行顺序规则格网渲染
     * ========================================================================
     * 目标：
     *   1) 为测试提供与并行路径一致的顺序基线
     * 操作要点：
     *   1) 仅关闭并行切分
     *   2) 仍复用同一套像素缓冲算法
     */
    BufferedImage renderSequential(
        int width,
        int height,
        StructuredGridDomain domain,
        double[] values,
        ColorMap colorMap,
        RangeStats rangeStats,
        ViewportState.Snapshot snapshot,
        boolean cellCentered,
        Double fillValue
    ) {
        return renderInternal(
            width,
            height,
            domain,
            values,
            colorMap,
            rangeStats,
            snapshot,
            cellCentered,
            fillValue,
            null,
            false
        );
    }

    /*
     * ========================================================================
     * 步骤4：统一执行像素缓冲渲染
     * ========================================================================
     * 目标：
     *   1) 将规则格网渲染统一转换成像素缓冲填充
     *   2) 为顺序和并行模式复用同一套核心逻辑
     * 操作要点：
     *   1) 先构建屏幕到网格索引映射
     *   2) 再按像素行写入颜色
     */
    private BufferedImage renderInternal(
        int width,
        int height,
        StructuredGridDomain domain,
        double[] values,
        ColorMap colorMap,
        RangeStats rangeStats,
        ViewportState.Snapshot snapshot,
        boolean cellCentered,
        Double fillValue,
        Executor renderExecutor,
        boolean parallel
    ) {
        logger.info(() -> "开始渲染规则格网底图, width="
            + width
            + ", height="
            + height
            + ", parallel="
            + parallel);

        // 4.1 先创建目标图像并填充统一背景色。
        BufferedImage image = new BufferedImage(Math.max(1, width), Math.max(1, height), BufferedImage.TYPE_INT_ARGB);
        int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        Arrays.fill(pixels, BACKGROUND_ARGB);

        // 4.2 输入无效或范围为空时直接返回背景图。
        if (domain == null || rangeStats == null || rangeStats.empty()) {
            logger.info("规则格网底图渲染结束, rendered=false");
            return image;
        }
        if (!domain.grid().rectilinear()) {
            logger.info("规则格网底图渲染结束, rendered=false");
            return image;
        }

        double[] xAxis = domain.grid().xAxis();
        double[] yAxis = domain.grid().yAxis();
        if (xAxis == null || yAxis == null || xAxis.length == 0 || yAxis.length == 0 || values == null || values.length == 0) {
            logger.info("规则格网底图渲染结束, rendered=false");
            return image;
        }

        // 4.3 构建调色板和屏幕到网格索引映射。
        int[] palette = buildPaletteArgb(colorMap);
        double[] xBoundaries = cellCentered ? xAxis : nodeEdges(xAxis);
        double[] yBoundaries = cellCentered ? yAxis : nodeEdges(yAxis);
        int[] columnLookup = buildScreenIntervalLookup(xBoundaries, width, snapshot, true);
        int[] rowLookup = buildScreenIntervalLookup(yBoundaries, height, snapshot, false);
        int gridWidth = cellCentered ? Math.max(0, xAxis.length - 1) : xAxis.length;
        int gridHeight = cellCentered ? Math.max(0, yAxis.length - 1) : yAxis.length;
        if (gridWidth == 0 || gridHeight == 0) {
            logger.info("规则格网底图渲染结束, rendered=false");
            return image;
        }

        // 4.4 按配置选择顺序或并行路径写入像素缓冲。
        if (parallel && renderExecutor != null) {
            renderRowsParallel(
                pixels,
                width,
                height,
                values,
                columnLookup,
                rowLookup,
                gridWidth,
                palette,
                rangeStats,
                fillValue,
                renderExecutor
            );
        } else {
            renderRows(
                pixels,
                width,
                values,
                columnLookup,
                rowLookup,
                gridWidth,
                palette,
                rangeStats,
                fillValue,
                0,
                height
            );
        }

        logger.info(() -> "规则格网底图渲染结束, rendered=true, parallel=" + parallel);
        return image;
    }

    /*
     * ========================================================================
     * 步骤5：并行写入像素行
     * ========================================================================
     * 目标：
     *   1) 将像素缓冲按行块分发到多个工作线程
     * 操作要点：
     *   1) 每个任务写自己独占的像素行区间
     *   2) 全部完成后再返回图像
     */
    private void renderRowsParallel(
        int[] pixels,
        int width,
        int height,
        double[] values,
        int[] columnLookup,
        int[] rowLookup,
        int gridWidth,
        int[] palette,
        RangeStats rangeStats,
        Double fillValue,
        Executor renderExecutor
    ) {
        int stripeCount = Math.max(1, Math.min(height, Runtime.getRuntime().availableProcessors() * 4));
        int stripeHeight = Math.max(1, (int) Math.ceil(height / (double) stripeCount));
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int startRow = 0; startRow < height; startRow += stripeHeight) {
            final int stripeStartRow = startRow;
            final int stripeEndRow = Math.min(height, stripeStartRow + stripeHeight);
            futures.add(CompletableFuture.runAsync(
                () -> renderRows(
                    pixels,
                    width,
                    values,
                    columnLookup,
                    rowLookup,
                    gridWidth,
                    palette,
                    rangeStats,
                    fillValue,
                    stripeStartRow,
                    stripeEndRow
                ),
                renderExecutor
            ));
        }
        futures.forEach(CompletableFuture::join);
    }

    /*
     * ========================================================================
     * 步骤6：写入指定像素行区间
     * ========================================================================
     * 目标：
     *   1) 按屏幕行将规则格网值映射到像素缓冲
     * 操作要点：
     *   1) 每一行先定位网格行号
     *   2) 每个像素点再定位列号并写入颜色
     */
    private void renderRows(
        int[] pixels,
        int width,
        double[] values,
        int[] columnLookup,
        int[] rowLookup,
        int gridWidth,
        int[] palette,
        RangeStats rangeStats,
        Double fillValue,
        int startRow,
        int endRow
    ) {
        double min = rangeStats.min();
        double max = rangeStats.max();
        for (int screenY = startRow; screenY < endRow; screenY++) {
            int rowIndex = rowLookup[screenY];
            if (rowIndex < 0) {
                continue;
            }
            int rowOffset = screenY * width;
            for (int screenX = 0; screenX < width; screenX++) {
                int columnIndex = columnLookup[screenX];
                if (columnIndex < 0) {
                    continue;
                }
                int sampleIndex = rowIndex * gridWidth + columnIndex;
                if (sampleIndex < 0 || sampleIndex >= values.length) {
                    continue;
                }
                double value = values[sampleIndex];
                if (!RenderMath.isRenderableValue(value, fillValue)) {
                    continue;
                }
                int paletteIndex = (int) Math.round(RenderMath.normalize(value, min, max) * 255.0);
                pixels[rowOffset + screenX] = palette[paletteIndex];
            }
        }
    }

    /*
     * ========================================================================
     * 步骤7：构建屏幕到网格区间查找表
     * ========================================================================
     * 目标：
     *   1) 预先把屏幕像素映射到网格区间索引
     * 操作要点：
     *   1) 每个像素只查一次
     *   2) 后续渲染循环直接 O(1) 取行列索引
     */
    private int[] buildScreenIntervalLookup(
        double[] boundaries,
        int screenSize,
        ViewportState.Snapshot snapshot,
        boolean horizontal
    ) {
        int[] lookup = new int[Math.max(1, screenSize)];
        Arrays.fill(lookup, -1);
        for (int index = 0; index < boundaries.length - 1; index++) {
            double startScreen = horizontal ? snapshot.screenX(boundaries[index]) : snapshot.screenY(boundaries[index]);
            double endScreen = horizontal ? snapshot.screenX(boundaries[index + 1]) : snapshot.screenY(boundaries[index + 1]);
            int start = Math.max(0, (int) Math.floor(Math.min(startScreen, endScreen)));
            int end = Math.min(screenSize, (int) Math.ceil(Math.max(startScreen, endScreen)));
            for (int pixel = start; pixel < end; pixel++) {
                lookup[pixel] = index;
            }
        }
        return lookup;
    }

    /*
     * ========================================================================
     * 步骤8：构造节点控制边界
     * ========================================================================
     * 目标：
     *   1) 将节点轴扩展成控制边界数组
     * 操作要点：
     *   1) 首尾按半个网格间距外扩
     *   2) 中间边界取相邻节点中点
     */
    private double[] nodeEdges(double[] axis) {
        if (axis.length == 1) {
            return new double[]{axis[0] - 0.5, axis[0] + 0.5};
        }

        double[] edges = new double[axis.length + 1];
        edges[0] = axis[0] - (axis[1] - axis[0]) * 0.5;
        for (int index = 1; index < axis.length; index++) {
            edges[index] = (axis[index - 1] + axis[index]) * 0.5;
        }
        edges[axis.length] = axis[axis.length - 1] + (axis[axis.length - 1] - axis[axis.length - 2]) * 0.5;
        return edges;
    }

    /*
     * ========================================================================
     * 步骤9：构建 ARGB 调色板
     * ========================================================================
     * 目标：
     *   1) 提前把颜色映射转换成整型 ARGB
     * 操作要点：
     *   1) 渲染时直接按索引取色
     *   2) 避免像素循环里创建颜色对象
     */
    private int[] buildPaletteArgb(ColorMap colorMap) {
        int[] palette = new int[256];
        for (int index = 0; index < palette.length; index++) {
            javafx.scene.paint.Color fxColor = colorMap.colorAt(index / 255.0);
            int alpha = ((int) Math.round(fxColor.getOpacity() * 255.0)) << 24;
            int red = ((int) Math.round(fxColor.getRed() * 255.0)) << 16;
            int green = ((int) Math.round(fxColor.getGreen() * 255.0)) << 8;
            int blue = (int) Math.round(fxColor.getBlue() * 255.0);
            palette[index] = alpha | red | green | blue;
        }
        return palette;
    }
}
