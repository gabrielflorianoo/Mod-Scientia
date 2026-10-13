package com.scientia.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.scientia.ScientiaMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.model.data.ModelData;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

/** Rendering helpers shared by the block entity renderers. */
@OnlyIn(Dist.CLIENT)
public final class RenderUtil {
    public static final ResourceLocation LIQUID = ScientiaMod.id("block/liquid");
    private static final float DEG = (float) (Math.PI / 180.0);

    private RenderUtil() {
    }

    /** Blockstate-equivalent rotation (x, y in degrees) for a model authored facing north. */
    public static int[] rotationFor(Direction f) {
        return switch (f) {
            case NORTH -> new int[]{0, 0};
            case EAST -> new int[]{0, 90};
            case SOUTH -> new int[]{0, 180};
            case WEST -> new int[]{0, 270};
            case UP -> new int[]{270, 0};
            case DOWN -> new int[]{90, 0};
        };
    }

    /** Applies the same rotation the block model receives from its blockstate, about the block centre. */
    public static void applyFacing(PoseStack ps, Direction f) {
        int[] r = rotationFor(f);
        ps.translate(0.5, 0.5, 0.5);
        ps.mulPose(new Quaternionf().rotateYXZ(-r[1] * DEG, -r[0] * DEG, 0));
        ps.translate(-0.5, -0.5, -0.5);
    }

    public static void renderPart(ResourceLocation model, PoseStack ps, MultiBufferSource buffers, int light, RenderType type) {
        BakedModel baked = Minecraft.getInstance().getModelManager().getModel(model);
        Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(ps.last(), buffers.getBuffer(type), null, baked,
                1f, 1f, 1f, light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, type);
    }

    /** Draws an axis-aligned translucent box (block-space coordinates 0..1) textured with the liquid sprite. */
    public static void liquidBox(PoseStack ps, MultiBufferSource buffers, float x0, float y0, float z0, float x1, float y1, float z1,
                                 int rgb, float alpha, int light) {
        if (y1 - y0 < 1e-3) return;
        TextureAtlasSprite sp = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(LIQUID);
        VertexConsumer vc = buffers.getBuffer(RenderType.translucent());
        Matrix4f m = ps.last().pose();
        Matrix3f n = ps.last().normal();
        float r = ((rgb >> 16) & 255) / 255f, g = ((rgb >> 8) & 255) / 255f, b = (rgb & 255) / 255f;
        float u0 = sp.getU0(), u1 = sp.getU1(), v0 = sp.getV0(), v1 = sp.getV1();
        // top
        quad(vc, m, n, r, g, b, alpha, light, 0, 1, 0, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, u0, v0, u1, v1);
        // sides
        quad(vc, m, n, r, g, b, alpha, light, 0, 0, -1, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, u0, v0, u1, v1);
        quad(vc, m, n, r, g, b, alpha, light, 0, 0, 1, x1, y0, z1, x1, y1, z1, x0, y1, z1, x0, y0, z1, u0, v0, u1, v1);
        quad(vc, m, n, r, g, b, alpha, light, -1, 0, 0, x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0, u0, v0, u1, v1);
        quad(vc, m, n, r, g, b, alpha, light, 1, 0, 0, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, u0, v0, u1, v1);
    }

    private static void quad(VertexConsumer vc, Matrix4f m, Matrix3f n, float r, float g, float b, float a, int light,
                             float nx, float ny, float nz,
                             float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz,
                             float u0, float v0, float u1, float v1) {
        vc.vertex(m, ax, ay, az).color(r, g, b, a).uv(u0, v1).uv2(light).normal(n, nx, ny, nz).endVertex();
        vc.vertex(m, bx, by, bz).color(r, g, b, a).uv(u0, v0).uv2(light).normal(n, nx, ny, nz).endVertex();
        vc.vertex(m, cx, cy, cz).color(r, g, b, a).uv(u1, v0).uv2(light).normal(n, nx, ny, nz).endVertex();
        vc.vertex(m, dx, dy, dz).color(r, g, b, a).uv(u1, v1).uv2(light).normal(n, nx, ny, nz).endVertex();
    }
}
