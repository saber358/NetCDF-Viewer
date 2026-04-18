package com.example.netcdfviewer.render;

import com.example.netcdfviewer.model.MeshData;
import com.example.netcdfviewer.model.StructuredGridDomain;
import com.example.netcdfviewer.ui.ViewportState;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public final class WindBarbOverlayRenderer {
    private static final Logger logger = Logger.getLogger(WindBarbOverlayRenderer.class.getName());
    static final int SAMPLE_SPACING = 36;
    private static final double SHAFT_LENGTH = 22.0;
    private static final double FEATHER_LENGTH = 9.0;
    private static final double HALF_FEATHER_LENGTH = 5.0;
    private static final double FEATHER_SPACING = 4.0;
    private static final double FEATHER_ANGLE_RADIANS = Math.toRadians(60.0);
    private static final double CALM_SPEED_THRESHOLD = 2.5;
    private static final Color BARB_COLOR = Color.rgb(35, 35, 35, 0.85);
    private static final double MPS_TO_KNOTS = 1.9438444924406;

    public void render(GraphicsContext graphics, List<WindBarbGlyph> glyphs) {
        if (graphics == null || glyphs == null || glyphs.isEmpty()) {
            return;
        }
        graphics.save();
        graphics.setStroke(BARB_COLOR);
        graphics.setFill(BARB_COLOR);
        graphics.setLineWidth(1.15);
        try {
            for (WindBarbGlyph glyph : glyphs) {
                graphics.strokeLine(glyph.shaftStartX(), glyph.shaftStartY(), glyph.shaftEndX(), glyph.shaftEndY());
                for (WindBarbLine line : glyph.featherLines()) {
                    graphics.strokeLine(line.startX(), line.startY(), line.endX(), line.endY());
                }
                for (WindBarbTriangle triangle : glyph.flagTriangles()) {
                    graphics.fillPolygon(
                        new double[]{triangle.x1(), triangle.x2(), triangle.x3()},
                        new double[]{triangle.y1(), triangle.y2(), triangle.y3()},
                        3
                    );
                }
                if (glyph.calm()) {
                    graphics.strokeOval(glyph.shaftStartX() - 4.0, glyph.shaftStartY() - 4.0, 8.0, 8.0);
                }
            }
        } finally {
            graphics.restore();
        }
    }

    /*
     * ========================================================================
     * 步骤1：采样三角网风羽标记
     * ========================================================================
     * 目标：
     *   1) 按屏幕步长从三角网风场采样一组可直接绘制的风羽
     * 操作要点：
     *   1) 逐个采样点查询 u/v
     *   2) 仅保留有效风速结果
     */
    public List<WindBarbGlyph> sampleBarbs(
        MeshData mesh,
        double[] uValues,
        double[] vValues,
        ViewportState.Snapshot snapshot,
        int width,
        int height,
        boolean elementCentered,
        Double uFillValue,
        Double vFillValue,
        int layerIndex
    ) {
        logger.info(() -> "开始采样三角网风羽标记, width=" + width + ", height=" + height + ", layerIndex=" + layerIndex);
        if (mesh == null || uValues == null || vValues == null || snapshot == null || width <= 0 || height <= 0) {
            logger.info("三角网风羽采样输入无效，返回空结果");
            return List.of();
        }
        TriangleSpatialIndex spatialIndex = TriangleSpatialIndexCache.get(mesh);
        List<WindBarbGlyph> glyphs = new ArrayList<>();
        for (int screenY = SAMPLE_SPACING / 2; screenY < height; screenY += SAMPLE_SPACING) {
            for (int screenX = SAMPLE_SPACING / 2; screenX < width; screenX += SAMPLE_SPACING) {
                FlowVectorQuery.Result result = FlowVectorQuery.query(
                    mesh,
                    spatialIndex,
                    uValues,
                    vValues,
                    snapshot,
                    screenX,
                    screenY,
                    elementCentered,
                    uFillValue,
                    vFillValue,
                    layerIndex
                );
                if (!result.hasVelocity()) {
                    continue;
                }
                glyphs.add(buildGlyph(screenX, screenY, result.u(), result.v()));
            }
        }
        logger.info(() -> "三角网风羽标记采样完成, glyphCount=" + glyphs.size());
        return glyphs;
    }

    /*
     * ========================================================================
     * 步骤2：采样规则格网风羽标记
     * ========================================================================
     * 目标：
     *   1) 按屏幕步长从规则格网风场采样一组可直接绘制的风羽
     * 操作要点：
     *   1) 同时查询 U/V 两个网格分量
     *   2) 仅保留有效风速结果
     */
    public List<WindBarbGlyph> sampleStructuredBarbs(
        StructuredGridDomain uDomain,
        StructuredGridDomain vDomain,
        double[] uValues,
        double[] vValues,
        ViewportState.Snapshot snapshot,
        int width,
        int height,
        boolean uCellCentered,
        boolean vCellCentered,
        Double uFillValue,
        Double vFillValue,
        int layerIndex
    ) {
        logger.info(() -> "开始采样规则格网风羽标记, width=" + width + ", height=" + height + ", layerIndex=" + layerIndex);
        if (uDomain == null || vDomain == null || uValues == null || vValues == null || snapshot == null || width <= 0 || height <= 0) {
            logger.info("规则格网风羽采样输入无效，返回空结果");
            return List.of();
        }
        List<WindBarbGlyph> glyphs = new ArrayList<>();
        for (int screenY = SAMPLE_SPACING / 2; screenY < height; screenY += SAMPLE_SPACING) {
            for (int screenX = SAMPLE_SPACING / 2; screenX < width; screenX += SAMPLE_SPACING) {
                StructuredVectorQuery.Result result = StructuredVectorQuery.query(
                    uDomain,
                    vDomain,
                    uValues,
                    vValues,
                    snapshot,
                    screenX,
                    screenY,
                    uCellCentered,
                    vCellCentered,
                    uFillValue,
                    vFillValue,
                    layerIndex
                );
                if (!result.hasVelocity()) {
                    continue;
                }
                glyphs.add(buildGlyph(screenX, screenY, result.u(), result.v()));
            }
        }
        logger.info(() -> "规则格网风羽标记采样完成, glyphCount=" + glyphs.size());
        return glyphs;
    }

    private WindBarbGlyph buildGlyph(double screenX, double screenY, double u, double v) {
        double speed = Math.hypot(u, v);
        if (speed < CALM_SPEED_THRESHOLD) {
            return new WindBarbGlyph(screenX, screenY, screenX, screenY, List.of(), List.of(), true);
        }

        double directionX = -u / speed;
        double directionY = v / speed;
        double shaftEndX = screenX + directionX * SHAFT_LENGTH;
        double shaftEndY = screenY + directionY * SHAFT_LENGTH;
        double speedKnots = Math.round(speed * MPS_TO_KNOTS / 5.0) * 5.0;

        List<WindBarbTriangle> flagTriangles = new ArrayList<>();
        List<WindBarbLine> featherLines = new ArrayList<>();
        double shaftUnitX = directionX;
        double shaftUnitY = directionY;
        double featherDirX = Math.cos(FEATHER_ANGLE_RADIANS) * shaftUnitX - Math.sin(FEATHER_ANGLE_RADIANS) * shaftUnitY;
        double featherDirY = Math.sin(FEATHER_ANGLE_RADIANS) * shaftUnitX + Math.cos(FEATHER_ANGLE_RADIANS) * shaftUnitY;
        double cursorX = shaftEndX;
        double cursorY = shaftEndY;

        while (speedKnots >= 50.0) {
            double nextX = cursorX - shaftUnitX * FEATHER_SPACING;
            double nextY = cursorY - shaftUnitY * FEATHER_SPACING;
            flagTriangles.add(new WindBarbTriangle(
                cursorX,
                cursorY,
                nextX,
                nextY,
                nextX + featherDirX * FEATHER_LENGTH,
                nextY + featherDirY * FEATHER_LENGTH
            ));
            cursorX = nextX - shaftUnitX * FEATHER_SPACING;
            cursorY = nextY - shaftUnitY * FEATHER_SPACING;
            speedKnots -= 50.0;
        }

        while (speedKnots >= 10.0) {
            featherLines.add(new WindBarbLine(
                cursorX,
                cursorY,
                cursorX + featherDirX * FEATHER_LENGTH,
                cursorY + featherDirY * FEATHER_LENGTH
            ));
            cursorX -= shaftUnitX * FEATHER_SPACING;
            cursorY -= shaftUnitY * FEATHER_SPACING;
            speedKnots -= 10.0;
        }

        if (speedKnots >= 5.0) {
            featherLines.add(new WindBarbLine(
                cursorX,
                cursorY,
                cursorX + featherDirX * HALF_FEATHER_LENGTH,
                cursorY + featherDirY * HALF_FEATHER_LENGTH
            ));
        }

        return new WindBarbGlyph(screenX, screenY, shaftEndX, shaftEndY, featherLines, flagTriangles, false);
    }

    public record WindBarbGlyph(
        double shaftStartX,
        double shaftStartY,
        double shaftEndX,
        double shaftEndY,
        List<WindBarbLine> featherLines,
        List<WindBarbTriangle> flagTriangles,
        boolean calm
    ) {
        public WindBarbGlyph {
            featherLines = List.copyOf(featherLines);
            flagTriangles = List.copyOf(flagTriangles);
        }
    }

    public record WindBarbLine(double startX, double startY, double endX, double endY) {
    }

    public record WindBarbTriangle(double x1, double y1, double x2, double y2, double x3, double y3) {
    }
}
