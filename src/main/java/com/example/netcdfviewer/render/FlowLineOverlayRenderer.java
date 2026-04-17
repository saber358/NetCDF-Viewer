package com.example.netcdfviewer.render;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class FlowLineOverlayRenderer {
    private static final Color BASE_COLOR = Color.rgb(32, 121, 148, 0.34);
    private static final Color HIGHLIGHT_COLOR = Color.rgb(228, 250, 255, 0.9);
    private static final double BASE_LINE_WIDTH = 1.4;
    private static final double HIGHLIGHT_LINE_WIDTH = 2.8;
    private static final double MIN_HIGHLIGHT_LENGTH = 18.0;
    private static final double MAX_HIGHLIGHT_LENGTH = 72.0;
    private static final Logger logger = Logger.getLogger(FlowLineOverlayRenderer.class.getName());

    /*
     * ========================================================================
     * 步骤1：绘制流线底图和移动亮带
     * ========================================================================
     * 目标：
     *   1) 先绘制静态流线本体
     *   2) 再在每条流线上叠加一个沿线移动的亮带
     * 操作要点：
     *   1) 底线和亮带分两遍绘制
     *   2) 亮带位置按 phase 在总长度上循环
     */
    public void render(GraphicsContext graphics, List<FlowLineGenerator.FlowLine> lines, double phase) {
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("开始绘制流线叠加层, lineCount="
                + (lines == null ? 0 : lines.size())
                + ", phase="
                + phase);
        }

        // 1.1 输入为空时直接返回，避免无意义绘制。
        if (graphics == null || lines == null || lines.isEmpty()) {
            if (logger.isLoggable(Level.FINE)) {
                logger.fine("流线叠加层绘制结束, renderedLineCount=0");
            }
            return;
        }

        // 1.2 先绘制所有流线的静态本体。
        graphics.save();
        try {
            graphics.setStroke(BASE_COLOR);
            graphics.setLineWidth(BASE_LINE_WIDTH);
            for (FlowLineGenerator.FlowLine line : lines) {
                drawWholeLine(graphics, line);
            }

            // 1.3 再在每条流线上叠加一个随 phase 移动的亮带。
            graphics.setStroke(HIGHLIGHT_COLOR);
            graphics.setLineWidth(HIGHLIGHT_LINE_WIDTH);
            for (FlowLineGenerator.FlowLine line : lines) {
                drawHighlightBand(graphics, line, phase);
            }
        } finally {
            graphics.restore();
        }

        if (logger.isLoggable(Level.FINE)) {
            logger.fine("流线叠加层绘制结束, renderedLineCount=" + lines.size());
        }
    }

    /*
     * ========================================================================
     * 步骤2：绘制整条流线本体
     * ========================================================================
     * 目标：
     *   1) 用较淡的线条先铺出完整流线轮廓
     * 操作要点：
     *   1) 逐段连接相邻点
     */
    private void drawWholeLine(GraphicsContext graphics, FlowLineGenerator.FlowLine line) {
        List<FlowLineGenerator.FlowPoint> points = line.points();
        if (points.size() < 2) {
            return;
        }
        for (int index = 1; index < points.size(); index++) {
            FlowLineGenerator.FlowPoint previous = points.get(index - 1);
            FlowLineGenerator.FlowPoint current = points.get(index);
            graphics.strokeLine(previous.x(), previous.y(), current.x(), current.y());
        }
    }

    /*
     * ========================================================================
     * 步骤3：绘制沿线移动的亮带
     * ========================================================================
     * 目标：
     *   1) 按当前 phase 计算亮带中心位置
     *   2) 在对应长度区间内绘制更亮更粗的线段
     * 操作要点：
     *   1) 亮带长度跟随流线总长变化但限制上下界
     *   2) 到达线尾时循环回线头
     */
    private void drawHighlightBand(GraphicsContext graphics, FlowLineGenerator.FlowLine line, double phase) {
        if (line.points().size() < 2 || line.totalLength() <= 0.0) {
            return;
        }

        double normalizedPhase = phase - Math.floor(phase);
        double highlightLength = Math.max(
            MIN_HIGHLIGHT_LENGTH,
            Math.min(MAX_HIGHLIGHT_LENGTH, line.totalLength() * 0.28)
        );
        double center = normalizedPhase * line.totalLength();
        double start = center - highlightLength * 0.5;
        double end = center + highlightLength * 0.5;

        if (start < 0.0) {
            drawLengthRange(graphics, line, line.totalLength() + start, line.totalLength());
            drawLengthRange(graphics, line, 0.0, end);
            return;
        }
        if (end > line.totalLength()) {
            drawLengthRange(graphics, line, start, line.totalLength());
            drawLengthRange(graphics, line, 0.0, end - line.totalLength());
            return;
        }
        drawLengthRange(graphics, line, start, end);
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
            graphics.strokeLine(startPoint.x(), startPoint.y(), endPoint.x(), endPoint.y());
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
}
