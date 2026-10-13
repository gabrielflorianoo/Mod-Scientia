package com.scientia.gametest;

import com.scientia.ScientiaMod;
import com.scientia.block.FilamentLampBlock;
import com.scientia.block.MachineBlock;
import com.scientia.blockentity.PrecisionBalanceBlockEntity;
import com.scientia.blockentity.ReactionVesselBlockEntity;
import com.scientia.core.chem.Mixture;
import com.scientia.item.BeakerItem;
import com.scientia.registry.ModBlocks;
import com.scientia.registry.ModItems;
import com.scientia.util.Sci;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * In-world tests of the core gameplay loops. Run with {@code ./gradlew runGameTestServer}.
 * The template "scientia:empty3x3x3" is an empty 3×3×3 structure in data/scientia/structures.
 */
@GameTestHolder(ScientiaMod.MODID)
@PrefixGameTestTemplate(false)
public final class ScientiaGameTests {
    private ScientiaGameTests() {
    }

    /** Battery → wire → lamp → wire → battery: the lamp must light from the solved current. */
    @GameTest(template = "empty3x3x3", timeoutTicks = 100)
    public static void circuitLightsLamp(GameTestHelper h) {
        BlockState battery = ModBlocks.BATTERY.get().defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH);
        h.setBlock(new BlockPos(0, 1, 1), battery);
        h.setBlock(new BlockPos(2, 1, 1), ModBlocks.FILAMENT_LAMP.get().defaultBlockState().setValue(FilamentLampBlock.FACING, Direction.NORTH));
        for (int x = 0; x < 3; x++) {
            h.setBlock(new BlockPos(x, 1, 0), ModBlocks.COPPER_WIRE.get().defaultBlockState());
            h.setBlock(new BlockPos(x, 1, 2), ModBlocks.COPPER_WIRE.get().defaultBlockState());
        }
        h.succeedWhen(() -> {
            BlockState lamp = h.getBlockState(new BlockPos(2, 1, 1));
            h.assertTrue(lamp.getValue(FilamentLampBlock.LEVEL) >= 14, "lamp should be near full brightness (≈6.6 W)");
        });
    }

    /** An open circuit (one wire missing) must leave the lamp dark. */
    @GameTest(template = "empty3x3x3", timeoutTicks = 60)
    public static void openCircuitStaysDark(GameTestHelper h) {
        h.setBlock(new BlockPos(0, 1, 1), ModBlocks.BATTERY.get().defaultBlockState().setValue(MachineBlock.FACING, Direction.NORTH));
        h.setBlock(new BlockPos(2, 1, 1), ModBlocks.FILAMENT_LAMP.get().defaultBlockState().setValue(FilamentLampBlock.FACING, Direction.NORTH));
        for (int x = 0; x < 3; x++) h.setBlock(new BlockPos(x, 1, 0), ModBlocks.COPPER_WIRE.get().defaultBlockState());
        h.setBlock(new BlockPos(0, 1, 2), ModBlocks.COPPER_WIRE.get().defaultBlockState());
        h.runAfterDelay(40, () -> {
            h.assertTrue(h.getBlockState(new BlockPos(2, 1, 1)).getValue(FilamentLampBlock.LEVEL) == 0, "open circuit must carry no current");
            h.succeed();
        });
    }

    /** The balance reports a nominal 100 g ingot within the ±4 % specimen variation. */
    @GameTest(template = "empty3x3x3")
    public static void balanceMeasuresIngot(GameTestHelper h) {
        BlockPos pos = new BlockPos(1, 1, 1);
        h.setBlock(pos, ModBlocks.PRECISION_BALANCE.get().defaultBlockState());
        PrecisionBalanceBlockEntity be = h.getBlockEntity(pos);
        be.items().setStackInSlot(0, new ItemStack(Items.IRON_INGOT));
        double m = be.reading();
        h.assertTrue(m >= 96 && m <= 104, "iron ingot sample mass should be 100 g ± 4 %, was " + m);
        h.succeed();
    }

    /** Vinegar + calcite in a sealed vessel: CO₂ forms, total mass is unchanged. */
    @GameTest(template = "empty3x3x3", timeoutTicks = 400)
    public static void sealedVesselConservesMass(GameTestHelper h) {
        BlockPos pos = new BlockPos(1, 1, 1);
        h.setBlock(pos, ModBlocks.REACTION_VESSEL.get().defaultBlockState());
        ReactionVesselBlockEntity be = h.getBlockEntity(pos);
        Mixture vinegar = new Mixture();
        vinegar.add("water", 13.244);
        vinegar.add("acetic_acid", 0.2098);
        be.items().setStackInSlot(ReactionVesselBlockEntity.SLOT_IN, BeakerItem.filled(ModItems.BEAKER.get(), vinegar));
        h.runAfterDelay(20, () -> {
            be.items().setStackInSlot(ReactionVesselBlockEntity.SLOT_OUT, ItemStack.EMPTY);
            be.handleAction(null, ReactionVesselBlockEntity.ACTION_STOPPER, 0);
            double before = be.massGrams() + 10.0; // + one 10 g portion of calcite powder
            be.items().setStackInSlot(ReactionVesselBlockEntity.SLOT_IN, new ItemStack(ModItems.CALCITE_POWDER.get()));
            h.runAfterDelay(300, () -> {
                h.assertTrue(be.mixture().contains("carbon_dioxide"), "CO2 should have formed");
                h.assertTrue(Math.abs(be.massGrams() - before) < 1e-6, "mass must be conserved in a sealed vessel");
                h.succeed();
            });
        });
    }

    @SuppressWarnings("unused")
    private static double grams(Mixture m) {
        return m.massGrams(Sci.db().substances());
    }
}
