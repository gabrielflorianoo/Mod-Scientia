package com.scientia.registry;

import com.scientia.ScientiaMod;
import com.scientia.block.BatteryBlock;
import com.scientia.block.CircuitSwitchBlock;
import com.scientia.block.CopperWireBlock;
import com.scientia.block.ElectricMotorBlock;
import com.scientia.block.ElectrolysisStationBlock;
import com.scientia.block.FilamentLampBlock;
import com.scientia.block.MachineBlock;
import com.scientia.block.ResistorBlock;
import com.scientia.block.ScreenBlock;
import com.scientia.block.SolarPanelBlock;
import com.scientia.block.ThermalGeneratorBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, ScientiaMod.MODID);
    /** Every block, in creative-tab order; each gets a BlockItem. */
    public static final List<RegistryObject<? extends Block>> ALL = new ArrayList<>();

    private static BlockBehaviour.Properties metal() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(2.5f, 6f).sound(SoundType.METAL)
                .noOcclusion().requiresCorrectToolForDrops();
    }

    private static BlockBehaviour.Properties glass() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(1.0f).sound(SoundType.GLASS).noOcclusion();
    }

    // ---- Laboratory
    public static final RegistryObject<Block> SCIENTIFIC_WORKBENCH = reg("scientific_workbench",
            () -> new ScreenBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5f).sound(SoundType.WOOD).noOcclusion(),
                    ScreenBlock.Kind.WORKBENCH, ScreenBlock.SHAPE_TABLE));
    public static final RegistryObject<Block> PRECISION_BALANCE = reg("precision_balance",
            () -> new MachineBlock(metal(), ModBlockEntities.PRECISION_BALANCE, MachineBlock.SHAPE_LOW));
    public static final RegistryObject<Block> GRADUATED_CYLINDER = reg("graduated_cylinder",
            () -> new MachineBlock(glass(), ModBlockEntities.GRADUATED_CYLINDER, MachineBlock.SHAPE_SLIM));
    public static final RegistryObject<Block> LAB_HEATER = reg("lab_heater",
            () -> new MachineBlock(metal(), ModBlockEntities.LAB_HEATER, MachineBlock.SHAPE_HEATER));
    public static final RegistryObject<Block> CONDENSER = reg("condenser",
            () -> new MachineBlock(glass(), ModBlockEntities.CONDENSER, MachineBlock.SHAPE_FULL));
    public static final RegistryObject<Block> REACTION_VESSEL = reg("reaction_vessel",
            () -> new MachineBlock(glass(), ModBlockEntities.REACTION_VESSEL, MachineBlock.SHAPE_VESSEL));
    public static final RegistryObject<Block> MATERIAL_ANALYZER = reg("material_analyzer",
            () -> new MachineBlock(metal(), ModBlockEntities.MATERIAL_ANALYZER, MachineBlock.SHAPE_FULL));
    public static final RegistryObject<Block> MICROSCOPE = reg("microscope",
            () -> new MachineBlock(metal(), ModBlockEntities.MICROSCOPE, MachineBlock.SHAPE_SLIM));
    public static final RegistryObject<Block> DATA_RECORDER = reg("data_recorder",
            () -> new MachineBlock(metal(), ModBlockEntities.DATA_RECORDER, MachineBlock.SHAPE_FULL));
    public static final RegistryObject<Block> INSULATED_KILN = reg("insulated_kiln",
            () -> new MachineBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(3.5f).sound(SoundType.STONE)
                    .requiresCorrectToolForDrops().lightLevel(s -> s.getValue(MachineBlock.ACTIVE) ? 13 : 0),
                    ModBlockEntities.INSULATED_KILN, MachineBlock.SHAPE_FULL));
    public static final RegistryObject<Block> OPTICAL_BENCH = reg("optical_bench",
            () -> new ScreenBlock(metal(), ScreenBlock.Kind.OPTICAL_BENCH, ScreenBlock.SHAPE_TABLE));
    public static final RegistryObject<Block> MOTION_TRACK = reg("motion_track",
            () -> new ScreenBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2f).sound(SoundType.WOOD).noOcclusion(),
                    ScreenBlock.Kind.MOTION_TRACK, ScreenBlock.SHAPE_TRACK));

    // ---- Electrical
    public static final RegistryObject<Block> COPPER_WIRE = reg("copper_wire",
            () -> new CopperWireBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(0.3f).sound(SoundType.COPPER).noOcclusion()));
    public static final RegistryObject<Block> BATTERY = reg("battery", () -> new BatteryBlock(metal()));
    public static final RegistryObject<Block> RESISTOR = reg("resistor", () -> new ResistorBlock(metal()));
    public static final RegistryObject<Block> CIRCUIT_SWITCH = reg("circuit_switch", () -> new CircuitSwitchBlock(metal()));
    public static final RegistryObject<Block> FILAMENT_LAMP = reg("filament_lamp",
            () -> new FilamentLampBlock(glass().lightLevel(s -> s.getValue(FilamentLampBlock.LEVEL))));
    public static final RegistryObject<Block> ELECTRIC_MOTOR = reg("electric_motor", () -> new ElectricMotorBlock(metal()));
    public static final RegistryObject<Block> THERMAL_GENERATOR = reg("thermal_generator",
            () -> new ThermalGeneratorBlock(metal().lightLevel(s -> s.getValue(MachineBlock.ACTIVE) ? 7 : 0)));
    public static final RegistryObject<Block> SOLAR_PANEL = reg("solar_panel", () -> new SolarPanelBlock(metal()));
    public static final RegistryObject<Block> ELECTROLYSIS_STATION = reg("electrolysis_station", () -> new ElectrolysisStationBlock(glass()));

    private static <T extends Block> RegistryObject<T> reg(String name, Supplier<T> block) {
        RegistryObject<T> ro = BLOCKS.register(name, block);
        ALL.add(ro);
        return ro;
    }

    private ModBlocks() {
    }
}
