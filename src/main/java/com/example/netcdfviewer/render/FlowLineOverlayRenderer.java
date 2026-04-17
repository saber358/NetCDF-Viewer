package com.example.netcdfviewer.render;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class FlowLineOverlayRenderer {
    private static final Color BRUSH_COLOR = Color.rgb(12, 12, 12, 0.94);
    private static final double BRUSH_LINE_WIDTH = 2.2;
    private static final double MIN_STROKE_LENGTH = 18.0;
    private static final double MAX_STROKE_LENGTH = 44.0;
    private static final double MAX_STROKE_RATIO = 0.42;
    private static final double MIN_GAP_LENGTH = 20.0;
    private static final double GAP_RATIO = 0.75;
    private static final int MAX_STROKE_COUNT = 5;
    private static final Logger logger = Logger.getLogger(FlowLineOverlayRenderer.class.getName());

    /*
     * ========================================================================
     * 步骤1：绘制黑色流动笔触
     * ========================================================================
     * 目标：
     *   1) 不再画完整流线本体
     *   2) 只绘制一段一段顺着流向移动的黑色笔触
     * 操作要点：
     *   1) 先按 phase 计算每条流线当前应显示的笔触区间
     *   2) 再把这些区间裁到折线上绘制出来
     */
    public void render(GraphicsContext graphics, List<FlowLineGenerator.FlowLine> lines, double phase) {
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("开始绘制黑色流动笔触, lineCount="
                + (lines == null ? 0 : lines.size())
                + ", phase="
                + phase);
        }

        // 1.1 输入为空时直接返回，避免无意义绘制。
        if (graphics == null || lines == null || lines.isEmpty()) {
            if (logger.isLoggable(Level.FINE)) {
                logger.fine("黑色流动笔触绘制结束, renderedLineCount=0");
            }
            return;
        }

        // 1.2 统一设置黑色笔触样式，再逐条流线绘制。
        graphics.save();
        try {
            graphics.setStroke(BRUSH_COLOR);
            graphics.setLineWidth(BRUSH_LINE_WIDTH);
            graphics.setLineCap(StrokeLineCap.ROUND);
            graphics.setLineJoin(StrokeLineJoin.ROUND);

            // 1.3 每条流线只画当前相位对应的笔触片段，不画整条底线。
            for (FlowLineGenerator.FlowLine line : lines) {
                List<StrokeRange> ranges = buildStrokeRanges(line, phase);
                for (StrokeRange range : ranges) {
                    drawLengthRange(graphics, line, range.startLength(), range.endLength());
                }
            }
        } finally {
            graphics.restore();
        }

        if (logger.isLoggable(Level.FINE)) {
            logger.fine("黑色流动笔触绘制结束, renderedLineCount=" + lines.size());
        }
    }

    /*
     * ========================================================================
     * 步骤2：计算当前相位的笔触区间
     * ========================================================================
     * 目标：
     *   1) 根据流线总长和动画相位生成当前应显示的黑色笔触
     * 操作要点：
     *   1) 笔触长度取中等偏长
     *   2) 笔触之间保留间隙，避免重新出现完整流线本体
     *   3) 超过线尾时按循环方式回到线头
     */
    List<StrokeRange> buildStrokeRanges(FlowLineGenerator.FlowLine line, double phase) {
        // 2.1 流线无效或太短时直接返回空结果。
        if (line == null || line.points().size() < 2 || line.totalLength() <= 0.0) {
            return List.of();
        }

        double totalLength = line.totalLength();
        double normalizedPhase = phase - Math.floor(phase);

        // 2.2 计算单段黑色笔触长度，保持短笔触但不切得过碎。
        double preferredLength = Math.max(MIN_STROKE_LENGTH, Math.min(MAX_STROKE_LENGTH, totalLength * 0.20));
        double strokeLength = Math.min(preferredLength, totalLength * MAX_STROKE_RATIO);
        if (strokeLength <= 0.0) {
            return List.of();
        }

        // 2.3 计算笔触之间的间隙，并控制单条流线上的笔触数量。
        double gapLength = Math.max(MIN_GAP_LENGTH, strokeLength * GAP_RATIO);
        double step = strokeLength + gapLength;
        int strokeCount = Math.max(1, Math.min(MAX_STROKE_COUNT, (int) Math.ceil(totalLength / step)));

        // 2.4 让第一段笔触按相位前进，其余笔触按固定步长跟随。
        double halfLength = strokeLength * 0.5;
        double baseCenter = normalizedPhase * step + halfLength;
        List<StrokeRange> ranges = new ArrayList<>();
        for (int index = 0; index < strokeCount; index++) {
            double center = baseCenter + index * step;
            while (center >= totalLength) {
                center -= totalLength;
            }
            addWrappedRange(ranges, center - halfLength, center + halfLength, totalLength);
        }
        return ranges;
    }

    /*
     * ========================================================================
     * 步骤3：处理跨线尾的循环笔触
     * ========================================================================
     * 目标：
     *   1) 当笔触跨过线尾时拆成两段
     * 操作要点：
     *   1) 允许线尾和线头同时出现同一段笔触的两部分
     */
    private void addWrappedRange(List<StrokeRange> ranges, double startLength, double endLength, double totalLength) {
        // 3.1 完全在线段范围左侧或右侧时直接忽略。
        if (endLength <= 0.0 || startLength >= totalLength) {
            return;
        }

        // 3.2 起点越过线头时拆成尾部一段和头部一段。
        if (startLength < 0.0) {
            ranges.add(new StrokeRange(totalLength + startLength, totalLength));
            ranges.add(new StrokeRange(0.0, Math.min(endLength, totalLength)));
            return;
        }

        // 3.3 终点越过线尾时拆成尾部一段和头部一段。
        if (endLength > totalLength) {
            ranges.add(new StrokeRange(startLength, totalLength));
            ranges.add(new StrokeRange(0.0, endLength - totalLength));
            return;
        }

        // 3.4 正常情况直接记录单段笔触区间。
        ranges.add(new StrokeRange(startLength, endLength));
    }

    /*
     * ========================================================================
     * 步骤4：按长度区间裁切折线并绘制
     * ========================================================================
     * 目标：
     *   1) 从整条流线中裁出指定长度范围的部分线段
     * 操作要点：
     *   1) 逐段计算与目标长度区间的重叠
     *   2) 只绘制重叠部分
     */
    private void drawLengthRange(GraphicsContext graphics, FlowLineGenerator.FlowLine line, double startLength, double endLength) {
        if (endLength <= startLength) {
            return;
        }

        List<FlowLineGenerator.FlowPoint> points = line.points();
        double[] cumulativeLengths = line.cumulativeLengths();
        boolean hasVisibleSegment = false;
        graphics.beginPath();
        for (int index = 1; index < points.size(); index++) {
            double segmentStart = cumulativeLengths[index - 1];
            double segmentEnd = cumulativeLengths[index];
            if (segmentEnd <= startLength || segmentStart >= endLength || segmentEnd <= segmentStart) {
                continue;
            }

            double localStart = Math.max(startLength, segmentStart);
            double localEnd = Math.min(endLength, segmentEnd);
            FlowLineGenerator.FlowPoint startPoint = interpolatePoint(
                points.get(index - 1),
                points.get(index),
                (localStart - segmentStart) / (segmentEnd - segmentStart)
            );
            FlowLineGenerator.FlowPoint endPoint = interpolatePoint(
                points.get(index - 1),
                points.get(index),
                (localEnd - segmentStart) / (segmentEnd - segmentStart)
            );
            if (!hasVisibleSegment) {
                graphics.moveTo(startPoint.x(), startPoint.y());
                hasVisibleSegment = true;
            } else {
                graphics.lineTo(startPoint.x(), startPoint.y());
            }
            graphics.lineTo(endPoint.x(), endPoint.y());
        }

        if (hasVisibleSegment) {
            graphics.stroke();
        }
    }

    private FlowLineGenerator.FlowPoint interpolatePoint(
        FlowLineGenerator.FlowPoint start,
        FlowLineGenerator.FlowPoint end,
        double ratio
    ) {
        double clampedRatio = Math.max(0.0, Math.min(1.0, ratio));
        double x = start.x() + (end.x() - start.x()) * clampedRatio;
        double y = start.y() + (end.y() - start.y()) * clampedRatio;
        return new FlowLineGenerator.FlowPoint(x, y);
    }

    record StrokeRange(double startLength, double endLength) {
    }
}
