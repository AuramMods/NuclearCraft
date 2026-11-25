package nc.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

final class WorldOutline {
    private static final BufferBuilder BUFFER = new BufferBuilder(256);
    private static final int[] EDGES = {
            0, 1, 2, 3, 4, 5, 6, 7,
            0, 2, 1, 3, 4, 6, 5, 7,
            0, 4, 1, 5, 2, 6, 3, 7
    };

    private WorldOutline() {}

    static Matrix4f viewRotation() {
        // Forge 1.20.1 passes the projection stack to AFTER_LEVEL. The shader applies projection separately.
        return new Matrix4f().set(new Matrix3f(RenderSystem.getInverseViewRotationMatrix()).invert());
    }

    static void begin() {
        BUFFER.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
    }

    static void box(Matrix4f matrix, double minX, double minY, double minZ,
                    double maxX, double maxY, double maxZ, float red, float green, float blue) {
        for (int corner : EDGES) {
            BUFFER.vertex(matrix, (float) ((corner & 1) == 0 ? minX : maxX),
                            (float) ((corner & 2) == 0 ? minY : maxY), (float) ((corner & 4) == 0 ? minZ : maxZ))
                    .color(red, green, blue, 1F).endVertex();
        }
    }

    static void draw() {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        try {
            BufferUploader.drawWithShader(BUFFER.end());
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
        }
    }
}
