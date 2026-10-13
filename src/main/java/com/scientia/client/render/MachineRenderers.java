package com.scientia.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.scientia.ScientiaMod;
import com.scientia.block.MachineBlock;
import com.scientia.blockentity.CondenserBlockEntity;
import com.scientia.blockentity.ElectricMotorBlockEntity;
import com.scientia.blockentity.ElectrolysisStationBlockEntity;
import com.scientia.blockentity.GraduatedCylinderBlockEntity;
import com.scientia.blockentity.LabHeaterBlockEntity;
import com.scientia.blockentity.PrecisionBalanceBlockEntity;
import com.scientia.blockentity.ReactionVesselBlockEntity;
import com.scientia.blockentity.ThermalGeneratorBlockEntity;
import com.scientia.core.chem.Mixture;
import com.scientia.util.Liquids;
import com.scientia.util.Sci;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Block entity renderers. Every animation is driven by synchronised machine state: an idle generator does
 * not spin, a balance pan sinks only under a real load, liquid levels show actual contents.
 */
@OnlyIn(Dist.CLIENT)
public final class MachineRenderers {
    public static final ResourceLocation BALANCE_PAN = ScientiaMod.id("block/precision_balance_pan");
    public static final ResourceLocation GENERATOR_ROTOR = ScientiaMod.id("block/thermal_generator_rotor");
    public static final ResourceLocation MOTOR_ROTOR = ScientiaMod.id("block/electric_motor_rotor");
    public static final ResourceLocation HEATER_COIL = ScientiaMod.id("block/lab_heater_coil");
    public static final ResourceLocation VESSEL_STOPPER = ScientiaMod.id("block/reaction_vessel_stopper");
    public static final List<ResourceLocation> PART_MODELS = List.of(BALANCE_PAN, GENERATOR_ROTOR, MOTOR_ROTOR, HEATER_COIL, VESSEL_STOPPER);

    private MachineRenderers() {
    }

    static int liquidColor(Mixture m) {
        if (m.contains("calcium_carbonate") && m.contains("water")) return 0xE8EEF0;   // milky precipitate
        if (m.contains("copper_ii_oxide")) return 0x2A2A2A;
        if (m.contains("chlorine")) return 0xC8E6A0;
        if (m.contains("acetic_acid")) return 0xE8D9A8;
        return 0x3F76E4;
    }

    // ------------------------------------------------------------------ balance

    public static final class Balance implements BlockEntityRenderer<PrecisionBalanceBlockEntity> {
        private final Font font;

        public Balance(BlockEntityRendererProvider.Context ctx) {
            this.font = ctx.getFont();
        }

        @Override
        public void render(@NotNull PrecisionBalanceBlockEntity be, float pt, @NotNull PoseStack ps, @NotNull MultiBufferSource buf, int light, int overlay) {
            BlockState s = be.getBlockState();
            ps.pushPose();
            RenderUtil.applyFacing(ps, s.getValue(MachineBlock.FACING));
            float offset = (be.prevPanOffset + (be.panOffset - be.prevPanOffset) * pt) * (1.5f / 16f);
            ps.pushPose();
            ps.translate(0, -offset, 0);
            RenderUtil.renderPart(BALANCE_PAN, ps, buf, light, RenderType.cutout());
            ItemStack item = be.items().getStackInSlot(0);
            if (!item.isEmpty()) {
                ps.translate(0.5, 9.4 / 16.0, 0.55);
                ps.mulPose(Axis.XP.rotationDegrees(90));
                ps.scale(0.32f, 0.32f, 0.32f);
                Minecraft.getInstance().getItemRenderer().renderStatic(item, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, ps, buf, be.getLevel(), 0);
            }
            ps.popPose();
            // Digital display on the front panel (model front faces north = -Z)
            double reading = be.reading();
            String text = Double.isNaN(reading) ? "-----" : Sci.fixed(reading, 2) + " g";
            ps.translate(0.5, 3.1 / 16.0, 0.9 / 16.0 - 0.001);
            ps.mulPose(Axis.YP.rotationDegrees(180));
            float scale = 1 / 160f;
            ps.scale(scale, -scale, scale);
            font.drawInBatch(text, -font.width(text) / 2f, -4, 0x6CFFE8, false, ps.last().pose(), buf, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
            ps.popPose();
        }
    }

    // ------------------------------------------------------------------ cylinder

    public static final class Cylinder implements BlockEntityRenderer<GraduatedCylinderBlockEntity> {
        public Cylinder(BlockEntityRendererProvider.Context ctx) {
        }

        @Override
        public void render(@NotNull GraduatedCylinderBlockEntity be, float pt, @NotNull PoseStack ps, @NotNull MultiBufferSource buf, int light, int overlay) {
            if (be.waterMl() <= 0) return;
            // Inner bore: x,z 6.6..9.4 px, from 1.5 px to 15 px for 0..100 mL
            float level = (float) (be.reading() / GraduatedCylinderBlockEntity.CAPACITY_ML);
            float y1 = (1.5f + 13.5f * level) / 16f;
            RenderUtil.liquidBox(ps, buf, 6.7f / 16, 1.5f / 16, 6.7f / 16, 9.3f / 16, y1, 9.3f / 16, 0x3F76E4, 0.55f, light);
        }
    }

    // ------------------------------------------------------------------ heater

    public static final class Heater implements BlockEntityRenderer<LabHeaterBlockEntity> {
        public Heater(BlockEntityRendererProvider.Context ctx) {
        }

        @Override
        public void render(@NotNull LabHeaterBlockEntity be, float pt, @NotNull PoseStack ps, @NotNull MultiBufferSource buf, int light, int overlay) {
            BlockState s = be.getBlockState();
            ps.pushPose();
            RenderUtil.applyFacing(ps, s.getValue(MachineBlock.FACING));
            if (s.getValue(MachineBlock.ACTIVE)) RenderUtil.renderPart(HEATER_COIL, ps, buf, LightTexture.FULL_BRIGHT, RenderType.cutout());
            ps.popPose();
            Mixture m = be.mixture();
            if (!m.isEmpty()) {
                // Beaker on the plate: inner 5..11 px, base at 8.2 px, 250 mL fills ~5 px
                float ml = (float) Math.min(1000, Liquids.volumeMl(m) + 20);
                float h = Math.min(6.5f, ml / 250f * 5f) / 16f;
                RenderUtil.liquidBox(ps, buf, 5.2f / 16, 8.3f / 16, 5.2f / 16, 10.8f / 16, 8.3f / 16 + h, 10.8f / 16, liquidColor(m), 0.6f, light);
            }
        }
    }

    // ------------------------------------------------------------------ reaction vessel

    public static final class Vessel implements BlockEntityRenderer<ReactionVesselBlockEntity> {
        public Vessel(BlockEntityRendererProvider.Context ctx) {
        }

        @Override
        public void render(@NotNull ReactionVesselBlockEntity be, float pt, @NotNull PoseStack ps, @NotNull MultiBufferSource buf, int light, int overlay) {
            Mixture m = be.mixture();
            if (!m.isEmpty()) {
                float ml = (float) Math.min(1000, Liquids.volumeMl(m) + 15);
                float y1 = (1.6f + 8.5f * ml / 1000f) / 16f;
                RenderUtil.liquidBox(ps, buf, 4.2f / 16, 1.6f / 16, 4.2f / 16, 11.8f / 16, y1, 11.8f / 16, liquidColor(m), 0.6f, light);
            }
            if (be.closed()) {
                ps.pushPose();
                RenderUtil.applyFacing(ps, be.getBlockState().getValue(MachineBlock.FACING));
                RenderUtil.renderPart(VESSEL_STOPPER, ps, buf, light, RenderType.cutout());
                ps.popPose();
            }
        }
    }

    // ------------------------------------------------------------------ condenser

    public static final class Condenser implements BlockEntityRenderer<CondenserBlockEntity> {
        public Condenser(BlockEntityRendererProvider.Context ctx) {
        }

        @Override
        public void render(@NotNull CondenserBlockEntity be, float pt, @NotNull PoseStack ps, @NotNull MultiBufferSource buf, int light, int overlay) {
            double ml = be.distillateMl();
            if (ml > 0.5) {
                float y1 = (1.2f + (float) Math.min(1.0, ml / 1000) * 4.5f) / 16f;
                RenderUtil.liquidBox(ps, buf, 9.6f / 16, 1.2f / 16, 9.6f / 16, 13.4f / 16, y1, 13.4f / 16, 0x5A8CF0, 0.5f, light);
            }
            if (be.flowing()) {
                // A thin stream running down the condenser outlet
                float t = (be.getLevel().getGameTime() % 20 + pt) / 20f;
                RenderUtil.liquidBox(ps, buf, 11.2f / 16, 5.8f / 16, 11.2f / 16, 11.8f / 16, (6.5f + t) / 16f, 11.8f / 16, 0x5A8CF0, 0.7f, light);
            }
        }
    }

    // ------------------------------------------------------------------ generator & motor

    public static final class Generator implements BlockEntityRenderer<ThermalGeneratorBlockEntity> {
        public Generator(BlockEntityRendererProvider.Context ctx) {
        }

        @Override
        public void render(@NotNull ThermalGeneratorBlockEntity be, float pt, @NotNull PoseStack ps, @NotNull MultiBufferSource buf, int light, int overlay) {
            ps.pushPose();
            RenderUtil.applyFacing(ps, be.getBlockState().getValue(MachineBlock.FACING));
            float angle = be.prevRotorAngle + ((be.rotorAngle - be.prevRotorAngle + 360f) % 360f) * pt;
            // Rotor axis runs east–west (model X axis) through (8, 9, 8) px
            ps.translate(0.5, 9 / 16.0, 0.5);
            ps.mulPose(Axis.XP.rotationDegrees(angle));
            ps.translate(-0.5, -9 / 16.0, -0.5);
            RenderUtil.renderPart(GENERATOR_ROTOR, ps, buf, light, RenderType.cutout());
            ps.popPose();
        }
    }

    public static final class Motor implements BlockEntityRenderer<ElectricMotorBlockEntity> {
        public Motor(BlockEntityRendererProvider.Context ctx) {
        }

        @Override
        public void render(@NotNull ElectricMotorBlockEntity be, float pt, @NotNull PoseStack ps, @NotNull MultiBufferSource buf, int light, int overlay) {
            ps.pushPose();
            RenderUtil.applyFacing(ps, be.getBlockState().getValue(MachineBlock.FACING));
            float angle = be.prevAngle + ((be.angle - be.prevAngle + 360f) % 360f) * pt;
            // Shaft is vertical in the model (Y axis) through the block centre
            ps.translate(0.5, 0, 0.5);
            ps.mulPose(Axis.YP.rotationDegrees(angle));
            ps.translate(-0.5, 0, -0.5);
            RenderUtil.renderPart(MOTOR_ROTOR, ps, buf, light, RenderType.cutout());
            ps.popPose();
        }
    }

    public static final class Electrolysis implements BlockEntityRenderer<ElectrolysisStationBlockEntity> {
        public Electrolysis(BlockEntityRendererProvider.Context ctx) {
        }

        @Override
        public void render(@NotNull ElectrolysisStationBlockEntity be, float pt, @NotNull PoseStack ps, @NotNull MultiBufferSource buf, int light, int overlay) {
            Mixture m = be.mixture();
            if (m.isEmpty()) return;
            float ml = (float) Math.min(1000, Liquids.volumeMl(m));
            float y1 = (2.2f + 7.5f * ml / 1000f) / 16f;
            RenderUtil.liquidBox(ps, buf, 2.6f / 16, 2.2f / 16, 3.6f / 16, 13.4f / 16, y1, 12.4f / 16, liquidColor(m), 0.5f, light);
        }
    }
}
