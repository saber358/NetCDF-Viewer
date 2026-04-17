package com.example.netcdfviewer.render;

import com.example.netcdfviewer.model.MeshData;
import com.example.netcdfviewer.ui.ViewportState;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public final class WaveArrowOverlayRenderer {
    static final int SAMPLE_SPACING = 28;
    static final double MIN_ARROW_LENGTH = 8.0;
    static final double MAX_ARROW_LENGTH = 26.0;
    private static final double HEAD_LENGTH = 5.0;
    private static final double HEAD_ANGLE_RADIANS = Math.toRadians(28.0);
    private static final Color ARROW_COLOR = Color.rgb(24, 92, 118, 0.72);
    private static final Logger logger = Logger.getLogger(WaveArrowOverlayRenderer.class.getName());

    /*
     * ========================================================================
     * 步骤1：绘制波场箭头叠加层
     * ========================================================================
     * 目标：
     *   1) 在已有标量底图之上绘制波向箭头
     *   2) 保持箭头绘制逻辑和控制器状态解耦
     * 操作要点：
     *   1) 先采样出屏幕箭头集合
     *   2) 再统一绘制主杆和箭头头部
     */
    public void render(
        GraphicsContext graphics,
        MeshData mesh,
        double[] directionValues,
        double[] wavelengthValues,
        ViewportState.Snapshot snapshot,
        int width,
        int height,
        boolean elementCentered,
        Double directionFillValue,
        Double wavelengthFillValue,
        int layerIndex,
        RangeStats wavelengthRange
    ) {
        logger.info(() -> "开始绘制波场箭头叠加层, width=" + width + ", height=" + height + ", layerIndex=" + layerIndex);

        // 1.1 先根据当前视口和波场值采样可绘制箭头。
        List<ArrowGlyph> arrows = sampleArrows(
            mesh,
            directionValues,
            wavelengthValues,
            snapshot,
            width,
            height,
            elementCentered,
            directionFillValue,
            wavelengthFillValue,
            layerIndex,
            wavelengthRange
        );
        if (arrows.isEmpty()) {
            logger.info(() -> "波场箭头叠加层绘制结束, arrowCount=0");
            return;
        }

        // 1.2 统一设置箭头图形样式，减少循环内状态切换。
        graphics.save();
        graphics.setStroke(ARROW_COLOR);
        graphics.setLineWidth(1.2);
        try {
            // 1.3 逐个绘制箭头主杆与箭头头部。
            for (ArrowGlyph arrow : arrows) {
                graphics.strokeLine(arrow.startX(), arrow.startY(), arrow.endX(), arrow.endY());
                drawHead(graphics, arrow);
            }
        } finally {
            graphics.restore();
        }

        logger.info(() -> "波场箭头叠加层绘制结束, arrowCount=" + arrows.size());
    }

    /*
     * ========================================================================
     * 步骤2：采样波场箭头
     * ========================================================================
     * 目标：
     *   1) 从当前屏幕网格中采样出可绘制的箭头集合
     * 操作要点：
     *   1) 屏幕按固定步长采样
     *   2) 每个采样点同时查询 wdir 和 wlen
     *   3) 只保留命中网格且数值有效的箭头
     */
    List<ArrowGlyph> sampleArrows(
        MeshData mesh,
        double[] directionValues,
        double[] wavelengthValues,
        ViewportState.Snapshot snapshot,
        int width,
        int height,
        boolean elementCentered,
        Double directionFillValue,
        Double wavelengthFillValue,
        int layerIndex,
        RangeStats wavelengthRange
    ) {
        logger.info(() -> "开始采样波场箭头, width=" + width + ", height=" + height + ", layerIndex=" + layerIndex);

        // 2.1 输入为空或当前层范围无效时直接返回空结果。
        if (mesh == null
            || directionValues == null
            || wavelengthValues == null
            || snapshot == null
            || width <= 0
            || height <= 0
            || wavelengthRange == null
            || wavelengthRange.empty()) {
            logger.info("波场箭头采样结束, arrowCount=0");
            return List.of();
        }

        // 2.2 按固定屏幕步长逐点采样，保持箭头密度稳定。
        List<ArrowGlyph> arrows = new ArrayList<>();
        for (int screenY = SAMPLE_SPACING / 2; screenY < height; screenY += SAMPLE_SPACING) {
            for (int screenX = SAMPLE_SPACING / 2; screenX < width; screenX += SAMPLE_SPACING) {
                // 2.3 在同一个采样点分别查询波向和波长。
                MeshPointQuery.Result directionResult = MeshPointQuery.query(
                    mesh,
                    directionValues,
                    snapshot,
                    screenX,
                    screenY,
                    elementCentered,
                    directionFillValue,
                    layerIndex
                );
                MeshPointQuery.Result wavelengthResult = MeshPointQuery.query(
                    mesh,
                    wavelengthValues,
                    snapshot,
                    screenX,
                    screenY,
                    elementCentered,
                    wavelengthFillValue,
                    layerIndex
                );

                // 2.4 任一波场值无效时直接跳过该采样点。
                if (!directionResult.hasValue() || !wavelengthResult.hasValue()) {
                    continue;
                }

                // 2.5 将波长映射成屏幕长度，再将波向角转换成箭头终点。
                double length = mapArrowLength(wavelengthResult.value(), wavelengthRange);
                double radians = Math.toRadians(directionResult.value());
                double dx = Math.cos(radians) * length;
                double dy = -Math.sin(radians) * length;
                arrows.add(new ArrowGlyph(
                    screenX,
                    screenY,
                    screenX + dx,
                    screenY + dy,
                    length
                ));
            }
        }

        logger.info(() -> "波场箭头采样结束, arrowCount=" + arrows.size());
        return arrows;
    }

    /*
     * ========================================================================
     * 步骤3：映射箭头长度
     * ========================================================================
     * 目标：
     *   1) 将波长值压缩到稳定的像素长度区间
     * 操作要点：
     *   1) 先做 0 到 1 归一化
     *   2) 再映射到配置的最短和最长箭头长度
     */
    double mapArrowLength(double wavelength, RangeStats wavelengthRange) {
        logger.info(() -> "开始映射波场箭头长度, wavelength=" + wavelength);

        // 3.1 先把波长值归一化到 0 到 1。
        double normalized = RenderMath.normalize(wavelength, wavelengthRange.min(), wavelengthRange.max());

        // 3.2 再映射到预设的像素长度区间。
        double length = MIN_ARROW_LENGTH + normalized * (MAX_ARROW_LENGTH - MIN_ARROW_LENGTH);

        logger.info(() -> "波场箭头长度映射完成, wavelength=" + wavelength + ", length=" + length);
        return length;
    }

    /*
     * ========================================================================
     * 步骤4：绘制箭头头部
     * ========================================================================
     * 目标：
     *   1) 为箭头主杆补出箭头头部
     * 操作要点：
     *   1) 通过主杆角度反推出左右两条短边
     */
    private void drawHead(GraphicsContext graphics, ArrowGlyph arrow) {
        // 4.1 根据主杆起终点求出当前箭头角度。
        double angle = Math.atan2(arrow.endY() - arrow.startY(), arrow.endX() - arrow.startX());

        // 4.2 以箭头终点为基准，构造左右两侧箭头头部边线。
        double leftX = arrow.endX() - Math.cos(angle - HEAD_ANGLE_RADIANS) * HEAD_LENGTH;
        double leftY = arrow.endY() - Math.sin(angle - HEAD_ANGLE_RADIANS) * HEAD_LENGTH;
        double rightX = arrow.endX() - Math.cos(angle + HEAD_ANGLE_RADIANS) * HEAD_LENGTH;
        double rightY = arrow.endY() - Math.sin(angle + HEAD_ANGLE_RADIANS) * HEAD_LENGTH;

        // 4.3 分别绘制箭头头部的两条边。
        graphics.strokeLine(arrow.endX(), arrow.endY(), leftX, leftY);
        graphics.strokeLine(arrow.endX(), arrow.endY(), rightX, rightY);
    }

    record ArrowGlyph(double startX, double startY, double endX, double endY, double length) {
    }
}
