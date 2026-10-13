package com.scientia.registry;

import com.scientia.ScientiaMod;
import com.scientia.blockentity.BatteryBlockEntity;
import com.scientia.blockentity.CondenserBlockEntity;
import com.scientia.blockentity.DataRecorderBlockEntity;
import com.scientia.blockentity.ElectricMotorBlockEntity;
import com.scientia.blockentity.ElectrolysisStationBlockEntity;
import com.scientia.blockentity.GraduatedCylinderBlockEntity;
import com.scientia.blockentity.InsulatedKilnBlockEntity;
import com.scientia.blockentity.LabHeaterBlockEntity;
import com.scientia.blockentity.MaterialAnalyzerBlockEntity;
import com.scientia.blockentity.MicroscopeBlockEntity;
import com.scientia.blockentity.PrecisionBalanceBlockEntity;
import com.scientia.blockentity.ReactionVesselBlockEntity;
import com.scientia.blockentity.SolarPanelBlockEntity;
import com.scientia.blockentity.ThermalGeneratorBlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ScientiaMod.MODID);

    public static final RegistryObject<BlockEntityType<PrecisionBalanceBlockEntity>> PRECISION_BALANCE =
            reg("precision_balance", PrecisionBalanceBlockEntity::new, () -> ModBlocks.PRECISION_BALANCE.get());
    public static final RegistryObject<BlockEntityType<GraduatedCylinderBlockEntity>> GRADUATED_CYLINDER =
            reg("graduated_cylinder", GraduatedCylinderBlockEntity::new, () -> ModBlocks.GRADUATED_CYLINDER.get());
    public static final RegistryObject<BlockEntityType<LabHeaterBlockEntity>> LAB_HEATER =
            reg("lab_heater", LabHeaterBlockEntity::new, () -> ModBlocks.LAB_HEATER.get());
    public static final RegistryObject<BlockEntityType<CondenserBlockEntity>> CONDENSER =
            reg("condenser", CondenserBlockEntity::new, () -> ModBlocks.CONDENSER.get());
    public static final RegistryObject<BlockEntityType<ReactionVesselBlockEntity>> REACTION_VESSEL =
            reg("reaction_vessel", ReactionVesselBlockEntity::new, () -> ModBlocks.REACTION_VESSEL.get());
    public static final RegistryObject<BlockEntityType<MaterialAnalyzerBlockEntity>> MATERIAL_ANALYZER =
            reg("material_analyzer", MaterialAnalyzerBlockEntity::new, () -> ModBlocks.MATERIAL_ANALYZER.get());
    public static final RegistryObject<BlockEntityType<MicroscopeBlockEntity>> MICROSCOPE =
            reg("microscope", MicroscopeBlockEntity::new, () -> ModBlocks.MICROSCOPE.get());
    public static final RegistryObject<BlockEntityType<DataRecorderBlockEntity>> DATA_RECORDER =
            reg("data_recorder", DataRecorderBlockEntity::new, () -> ModBlocks.DATA_RECORDER.get());
    public static final RegistryObject<BlockEntityType<InsulatedKilnBlockEntity>> INSULATED_KILN =
            reg("insulated_kiln", InsulatedKilnBlockEntity::new, () -> ModBlocks.INSULATED_KILN.get());
    public static final RegistryObject<BlockEntityType<BatteryBlockEntity>> BATTERY =
            reg("battery", BatteryBlockEntity::new, () -> ModBlocks.BATTERY.get());
    public static final RegistryObject<BlockEntityType<ThermalGeneratorBlockEntity>> THERMAL_GENERATOR =
            reg("thermal_generator", ThermalGeneratorBlockEntity::new, () -> ModBlocks.THERMAL_GENERATOR.get());
    public static final RegistryObject<BlockEntityType<SolarPanelBlockEntity>> SOLAR_PANEL =
            reg("solar_panel", SolarPanelBlockEntity::new, () -> ModBlocks.SOLAR_PANEL.get());
    public static final RegistryObject<BlockEntityType<ElectricMotorBlockEntity>> ELECTRIC_MOTOR =
            reg("electric_motor", ElectricMotorBlockEntity::new, () -> ModBlocks.ELECTRIC_MOTOR.get());
    public static final RegistryObject<BlockEntityType<ElectrolysisStationBlockEntity>> ELECTROLYSIS_STATION =
            reg("electrolysis_station", ElectrolysisStationBlockEntity::new, () -> ModBlocks.ELECTROLYSIS_STATION.get());

    @SuppressWarnings("ConstantConditions")
    private static <T extends BlockEntity> RegistryObject<BlockEntityType<T>> reg(String name, BlockEntityType.BlockEntitySupplier<T> factory,
                                                                                   Supplier<Block> block) {
        return BLOCK_ENTITIES.register(name, () -> BlockEntityType.Builder.of(factory, block.get()).build(null));
    }

    private ModBlockEntities() {
    }
}
