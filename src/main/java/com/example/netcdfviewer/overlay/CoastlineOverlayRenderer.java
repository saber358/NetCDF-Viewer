package com.example.netcdfviewer.overlay;

import com.example.netcdfviewer.ui.ViewportState;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

/**
 * 海岸线叠加绘制器。
 * 在当前主图之上按当前视口投影绘制世界坐标路径。
 */
public final class CoastlineOverlayRenderer {
    private static final Color STROKE = Color.rgb(15, 23, 42, 0.88);
    private static final double STROKE_WIDTH = 1.15;

    public void render(GraphicsContext graphics, CoastlineOverlay overlay, ViewportState.Snapshot snapshot) {
        if (graphics == null || overlay == null || snapshot == null) {
            return;
        }
        graphics.setStroke(STROKE);
        graphics.setLineWidth(STROKE_WIDTH);
        for (OverlayPath path : overlay.paths()) {
            if (path.pointCount() < 2) {
                continue;
            }
            double[] screenX = new double[path.pointCount()];
            double[] screenY = new double[path.pointCount()];
            for (int index = 0; index < path.pointCount(); index++) {
                screenX[index] = snapshot.screenX(path.x()[index]);
                screenY[index] = snapshot.screenY(path.y()[index]);
            }
            graphics.strokePolyline(screenX, screenY, path.pointCount());
        }
    }
}
