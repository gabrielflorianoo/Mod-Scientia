package com.scientia.registry;

import com.scientia.ScientiaMod;
import com.scientia.item.BeakerItem;
import com.scientia.item.CalculatorItem;
import com.scientia.item.GasCanisterItem;
import com.scientia.item.InstrumentItem;
import com.scientia.item.JournalItem;
import com.scientia.item.ReagentItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ScientiaMod.MODID);
    public static final List<RegistryObject<? extends Item>> TOOLS = new ArrayList<>();

    static {
        for (RegistryObject<? extends net.minecraft.world.level.block.Block> b : ModBlocks.ALL) {
            ITEMS.register(b.getId().getPath(), () -> new BlockItem(b.get(), new Item.Properties()));
        }
    }

    public static final RegistryObject<Item> SCIENCE_JOURNAL = tool("science_journal", () -> new JournalItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> CALCULATOR = tool("calculator", () -> new CalculatorItem(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> THERMOMETER = tool("thermometer", () -> new InstrumentItem(new Item.Properties().stacksTo(1), InstrumentItem.Kind.THERMOMETER));
    public static final RegistryObject<Item> MULTIMETER = tool("multimeter", () -> new InstrumentItem(new Item.Properties().stacksTo(1), InstrumentItem.Kind.MULTIMETER));
    public static final RegistryObject<Item> PH_METER = tool("ph_meter", () -> new InstrumentItem(new Item.Properties().stacksTo(1), InstrumentItem.Kind.PH_METER));
    public static final RegistryObject<Item> BEAKER = tool("beaker", () -> new BeakerItem(new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> GAS_CANISTER = tool("gas_canister", () -> new GasCanisterItem(new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> MORTAR_AND_PESTLE = tool("mortar_and_pestle", () -> new ReagentItem.Remainder(new Item.Properties().stacksTo(1)));
    public static final RegistryObject<Item> SALT_CRYSTALS = tool("salt_crystals", () -> new ReagentItem(new Item.Properties()));
    public static final RegistryObject<Item> CALCITE_POWDER = tool("calcite_powder", () -> new ReagentItem(new Item.Properties()));
    public static final RegistryObject<Item> QUICKLIME = tool("quicklime", () -> new ReagentItem(new Item.Properties()));
    public static final RegistryObject<Item> SLAKED_LIME = tool("slaked_lime", () -> new ReagentItem(new Item.Properties()));
    public static final RegistryObject<Item> COPPER_OXIDE_POWDER = tool("copper_oxide_powder", () -> new ReagentItem(new Item.Properties()));

    private static <T extends Item> RegistryObject<T> tool(String name, Supplier<T> s) {
        RegistryObject<T> ro = ITEMS.register(name, s);
        TOOLS.add(ro);
        return ro;
    }

    private ModItems() {
    }
}
