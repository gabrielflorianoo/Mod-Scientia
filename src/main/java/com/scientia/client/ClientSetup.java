package com.scientia.client;

import com.scientia.ScientiaMod;
import com.scientia.client.render.MachineRenderers;
import com.scientia.client.screen.MachineScreen;
import com.scientia.registry.ModBlockEntities;
import com.scientia.registry.ModMenus;
import com.scientia.registry.ModItems;
import com.scientia.util.Sci;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = ScientiaMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {
    }

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(ModMenus.MACHINE.get(), MachineScreen::new);
            // Item model override: filled beakers and canisters use a different icon.
            ItemProperties.register(ModItems.BEAKER.get(), ScientiaMod.id("filled"),
                    (stack, level, entity, seed) -> Sci.contentsOf(stack).isEmpty() ? 0f : 1f);
            ItemProperties.register(ModItems.GAS_CANISTER.get(), ScientiaMod.id("filled"),
                    (stack, level, entity, seed) -> Sci.contentsOf(stack).isEmpty() ? 0f : 1f);
        });
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.PRECISION_BALANCE.get(), MachineRenderers.Balance::new);
        event.registerBlockEntityRenderer(ModBlockEntities.GRADUATED_CYLINDER.get(), MachineRenderers.Cylinder::new);
        event.registerBlockEntityRenderer(ModBlockEntities.LAB_HEATER.get(), MachineRenderers.Heater::new);
        event.registerBlockEntityRenderer(ModBlockEntities.REACTION_VESSEL.get(), MachineRenderers.Vessel::new);
        event.registerBlockEntityRenderer(ModBlockEntities.CONDENSER.get(), MachineRenderers.Condenser::new);
        event.registerBlockEntityRenderer(ModBlockEntities.THERMAL_GENERATOR.get(), MachineRenderers.Generator::new);
        event.registerBlockEntityRenderer(ModBlockEntities.ELECTRIC_MOTOR.get(), MachineRenderers.Motor::new);
        event.registerBlockEntityRenderer(ModBlockEntities.ELECTROLYSIS_STATION.get(), MachineRenderers.Electrolysis::new);
    }

    /** Animated parts are separate OBJ models baked alongside the block models. */
    @SubscribeEvent
    public static void additionalModels(ModelEvent.RegisterAdditional event) {
        for (var rl : MachineRenderers.PART_MODELS) event.register(rl);
    }

    @SubscribeEvent
    public static void overlays(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "measurement_hud", new MeasurementHud());
    }
}
