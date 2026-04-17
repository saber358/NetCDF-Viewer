package com.example.netcdfviewer.render;

import com.example.netcdfviewer.model.MeshData;
import com.example.netcdfviewer.ui.ViewportState;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class FlowLineGenerator {
    static final int SEED_SPACING = 24;
    static final int OCCUPANCY_CELL_SIZE = 12;
    static final double STEP_PIXELS = 10.0;
    static final double MIN_SPEED = 1e-9;
    static final int MAX_STEPS_PER_DIRECTION = 28;
    static final double MIN_LINE_LENGTH = 18.0;
    private static final double VIEWPORT_MARGIN = 8.0;
    private static final Logger logger = Logger.getLogger(FlowLineGenerator.class.getName());

    /*
     * ========================================================================
     * 步骤1：生成当前视口的流线集合
     * ========================================================================
     * 目标：
     *   1) 根据当前速度场和视口生成稳定的屏幕流线
     *   2) 尽量压掉重复种子，避免叠加太密
     * 操作要点：
     *   1) 屏幕按固定间距投放种子
     *   2) 每个种子向前和向后积分
     *   3) 生成成功后写入占用网格抑制重复线
     */
    public List<FlowLine> generate(
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
        logger.info(() -> "开始生成流线集合, width="
            + width
            + ", height="
            + height
            + ", layerIndex="
            + layerIndex);

        // 1.1 基础输入无效时直接返回空结果。
        if (mesh == null
            || uValues == null
            || vValues == null
            || snapshot == null
            || width <= 0
            || height <= 0
            || snapshot.scale() <= 0.0) {
            logger.info("流线集合生成结束, lineCount=0");
            return List.of();
        }

        // 1.2 按屏幕占用网格记录已接受流线，减少重复种子生成。
        boolean[][] occupancy = new boolean[
            Math.max(1, (int) Math.ceil(height / (double) OCCUPANCY_CELL_SIZE))
        ][
            Math.max(1, (int) Math.ceil(width / (double) OCCUPANCY_CELL_SIZE))
        ];
        List<FlowLine> lines = new ArrayList<>();

        // 1.3 逐个屏幕种子尝试生成流线。
        for (int screenY = SEED_SPACING / 2; screenY < height; screenY += SEED_SPACING) {
            for (int screenX = SEED_SPACING / 2; screenX < width; screenX += SEED_SPACING) {
                if (isOccupied(occupancy, screenX, screenY)) {
                    continue;
                }

                FlowVectorQuery.Result seedVector = FlowVectorQuery.query(
                    mesh,
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
                if (!seedVector.hasVelocity() || seedVector.speed() <= MIN_SPEED) {
                    continue;
                }

                FlowLine line = buildLineFromSeed(
                    mesh,
                    uValues,
                    vValues,
                    snapshot,
                    width,
                    height,
                    elementCentered,
                    uFillValue,
                    vFillValue,
                    layerIndex,
                    screenX,
                    screenY
                );
                if (line == null || line.points().size() < 2 || line.totalLength() < MIN_LINE_LENGTH) {
                    continue;
                }

                lines.add(line);
                markOccupied(occupancy, line);
            }
        }

        logger.info(() -> "流线集合生成结束, lineCount=" + lines.size());
        return lines;
    }

    /*
     * ========================================================================
     * 步骤2：从单个种子构造一条完整流线
     * ========================================================================
     * 目标：
     *   1) 让同一个种子同时向前和向后延展
     * 操作要点：
     *   1) 先分别积分出两个方向的点列
     *   2) 再合并成一条连续折线
     */
    private FlowLine buildLineFromSeed(
        MeshData mesh,
        double[] uValues,
        double[] vValues,
        ViewportState.Snapshot snapshot,
        int width,
        int height,
        boolean elementCentered,
        Double uFillValue,
        Double vFillValue,
        int layerIndex,
        double seedScreenX,
        double seedScreenY
    ) {
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("开始从种子构造流线, seedScreenX=" + seedScreenX + ", seedScreenY=" + seedScreenY);
        }

        // 2.1 先沿速度方向和反方向分别积分。
        List<FlowPoint> backward = integrateDirection(
            mesh,
            uValues,
            vValues,
            snapshot,
            width,
            height,
            elementCentered,
            uFillValue,
            vFillValue,
            layerIndex,
            seedScreenX,
            seedScreenY,
            -1.0
        );
        List<FlowPoint> forward = integrateDirection(
            mesh,
            uValues,
            vValues,
            snapshot,
            width,
            height,
            elementCentered,
            uFillValue,
            vFillValue,
            layerIndex,
            seedScreenX,
            seedScreenY,
            1.0
        );

        // 2.2 将后向结果翻转，与种子点和前向结果拼成完整折线。
        Collections.reverse(backward);
        List<FlowPoint> points = new ArrayList<>(backward.size() + forward.size() + 1);
        points.addAll(backward);
        points.add(new FlowPoint(seedScreenX, seedScreenY));
        points.addAll(forward);

        // 2.3 点太少时直接视为无效流线。
        if (points.size() < 2) {
            if (logger.isLoggable(Level.FINE)) {
                logger.fine("种子流线构造结束, pointCount=0");
            }
            return null;
        }

        FlowLine line = FlowLine.fromPoints(points);
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("种子流线构造结束, pointCount=" + line.points().size() + ", totalLength=" + line.totalLength());
        }
        return line;
    }

    /*
     * ========================================================================
     * 步骤3：沿单一方向积分流线
     * ========================================================================
     * 目标：
     *   1) 从种子点持续追踪速度方向
     * 操作要点：
     *   1) 使用固定屏幕步长换算到世界坐标
     *   2) 命中失败、值无效或走出视口时停止
     */
    private List<FlowPoint> integrateDirection(
        MeshData mesh,
        double[] uValues,
        double[] vValues,
        ViewportState.Snapshot snapshot,
        int width,
        int height,
        boolean elementCentered,
        Double uFillValue,
        Double vFillValue,
        int layerIndex,
        double seedScreenX,
        double seedScreenY,
        double direction
    ) {
        if (logger.isLoggable(Level.FINE)) {
            logger.fine("开始积分单向流线, seedScreenX="
                + seedScreenX
                + ", seedScreenY="
                + seedScreenY
                + ", direction="
                + direction);
        }

        // 3.1 固定屏幕步长按当前缩放换算成世界步长。
        double stepWorld = STEP_PIXELS / snapshot.scale();
        double worldX = snapshot.worldX(seedScreenX);
        double worldY = snapshot.worldY(seedScreenY);
        List<FlowPoint> points = new ArrayList<>();

        // 3.2 逐步积分，直到命中失败、值无效或超出限制。
        for (int step = 0; step < MAX_STEPS_PER_DIRECTION; step++) {
            double sampleScreenX = snapshot.screenX(worldX);
            double sampleScreenY = snapshot.screenY(worldY);
            FlowVectorQuery.Result vector = FlowVectorQuery.query(
                mesh,
                uValues,
                vValues,
                snapshot,
                sampleScreenX,
                sampleScreenY,
                elementCentered,
                uFillValue,
                vFillValue,
                layerIndex
            );
            if (!vector.hasVelocity() || vector.speed() <= MIN_SPEED) {
                break;
            }

            double nextWorldX = worldX + direction * stepWorld * vector.u() / vector.speed();
            double nextWorldY = worldY + direction * stepWorld * vector.v() / vector.speed();
            double nextScreenX = snapshot.screenX(nextWorldX);
            double nextScreenY = snapshot.screenY(nextWorldY);
            if (!insideViewport(nextScreenX, nextScreenY, width, height)) {
                break;
            }

            double segmentLength = Math.hypot(nextScreenX - sampleScreenX, nextScreenY - sampleScreenY);
            if (segmentLength <= 0.25) {
                break;
            }

            points.add(new FlowPoint(nextScreenX, nextScreenY));
            worldX = nextWorldX;
            worldY = nextWorldY;
        }

        if (logger.isLoggable(Level.FINE)) {
            logger.fine("单向流线积分结束, pointCount=" + points.size() + ", direction=" + direction);
        }
        return points;
    }

    /*
     * ========================================================================
     * 步骤4：读写种子占用网格
     * ========================================================================
     * 目标：
     *   1) 将已经接受的流线投影到简化屏幕网格
     * 操作要点：
     *   1) 已占用单元直接跳过后续种子
     *   2) 接受一条流线后把它经过的单元全部标记
     */
    private boolean isOccupied(boolean[][] occupancy, double screenX, double screenY) {
        int cellX = clampCell((int) Math.floor(screenX / OCCUPANCY_CELL_SIZE), occupancy[0].length);
        int cellY = clampCell((int) Math.floor(screenY / OCCUPANCY_CELL_SIZE), occupancy.length);
        return occupancy[cellY][cellX];
    }

    private void markOccupied(boolean[][] occupancy, FlowLine line) {
        for (FlowPoint point : line.points()) {
            int cellX = clampCell((int) Math.floor(point.x() / OCCUPANCY_CELL_SIZE), occupancy[0].length);
            int cellY = clampCell((int) Math.floor(point.y() / OCCUPANCY_CELL_SIZE), occupancy.length);
            occupancy[cellY][cellX] = true;
        }
    }

    private int clampCell(int value, int length) {
        return Math.max(0, Math.min(value, length - 1));
    }

    private boolean insideViewport(double screenX, double screenY, int width, int height) {
        return screenX >= -VIEWPORT_MARGIN
            && screenX <= width + VIEWPORT_MARGIN
            && screenY >= -VIEWPORT_MARGIN
            && screenY <= height + VIEWPORT_MARGIN;
    }

    public record FlowLine(List<FlowPoint> points, double[] cumulativeLengths, double totalLength) {
        public FlowLine {
            points = List.copyOf(points);
            cumulativeLengths = Arrays.copyOf(cumulativeLengths, cumulativeLengths.length);
        }

        static FlowLine fromPoints(List<FlowPoint> points) {
            double[] cumulativeLengths = new double[points.size()];
            double totalLength = 0.0;
            for (int index = 1; index < points.size(); index++) {
                FlowPoint previous = points.get(index - 1);
                FlowPoint current = points.get(index);
                totalLength += Math.hypot(current.x() - previous.x(), current.y() - previous.y());
                cumulativeLengths[index] = totalLength;
            }
            return new FlowLine(points, cumulativeLengths, totalLength);
        }
    }

    public record FlowPoint(double x, double y) {
    }
}
