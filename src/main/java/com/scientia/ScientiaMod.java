package com.scientia;

import com.mojang.logging.LogUtils;
import com.scientia.config.ScientiaConfig;
import com.scientia.core.data.ScienceDatabase;
import com.scientia.core.validation.ScienceValidator;
import com.scientia.network.ScientiaNetwork;
import com.scientia.registry.ModBlockEntities;
import com.scientia.registry.ModBlocks;
import com.scientia.registry.ModCreativeTabs;
import com.scientia.registry.ModItems;
import com.scientia.registry.ModMenus;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

import java.util.List;

/**
 * Scientia: real science, playable in Minecraft.
 * The scientific engine lives in {@code com.scientia.core} (no Minecraft dependency); this package
 * connects it to blocks, items, screens and saved data.
 */
@Mod(ScientiaMod.MODID)
public final class ScientiaMod {
    public static final String MODID = "scientia";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ScientiaMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        ModMenus.MENUS.register(modBus);
        ModCreativeTabs.TABS.register(modBus);
        modBus.addListener(this::commonSetup);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, ScientiaConfig.COMMON_SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ScientiaConfig.CLIENT_SPEC);
        loadScienceData();
    }

    /** The static science database ships inside the jar and is loaded identically on client and server. */
    private static void loadScienceData() {
        try {
            ScienceDatabase db = ScienceDatabase.loadFromClasspath(ScientiaMod.class);
            List<String> errors = ScienceValidator.validate(db);
            for (String e : errors) LOGGER.error("Science data problem: {}", e);
            ScienceDatabase.setInstance(db);
            LOGGER.info("Loaded science data: {} elements, {} substances, {} reactions, {} experiments, {} research nodes",
                    db.elements().size(), db.substances().all().size(), db.reactions().size(), db.experiments().size(),
                    db.research().all().size());
        } catch (Exception e) {
            throw new IllegalStateException("Scientia could not load its science data", e);
        }
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(ScientiaNetwork::register);
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }
}
