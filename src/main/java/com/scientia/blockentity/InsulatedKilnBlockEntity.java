package com.scientia.blockentity;

import com.scientia.block.MachineBlock;
import com.scientia.journal.JournalService;
import com.scientia.registry.ModBlockEntities;
import com.scientia.registry.ModItems;
import com.scientia.util.Fuel;
import com.scientia.util.Sci;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Optional;

/**
 * A fuel-fired brick kiln with an energy balance:
 * <pre>C dT/dt = P_burner − UA (T − T_env) − P_charge</pre>
 * The burner (20 kW) is thermostatically limited to hold 1750 K. Each charge needs its own temperature
 * (smelting 1100 K, quicklime 1170 K, steel 1173 K, Portland cement 1720 K) and absorbs 15 kW while it heats
 * and reacts (≈120 kJ per item; a game approximation). An additive slot supplies carbon (coal/charcoal) for
 * carburising iron into steel, or clay for cement. Insulation lowers
 * UA, so an insulated kiln reaches temperature, smelts faster and uses far less fuel per item — the same
 * reason real kilns and furnaces are insulated.
 */
public class InsulatedKilnBlockEntity extends MachineBlockEntity implements Measurable {
    public static final int SLOT_IN = 0, SLOT_FUEL = 1, SLOT_OUT = 2, SLOT_INSULATION = 3, SLOT_ADDITIVE = 4;
    public static final double HEAT_CAPACITY = 2000;      // J/K of the kiln chamber lining
    public static final double BURNER_W = 20_000;
    public static final double SETPOINT_K = 1750, PROCESS_K = 1100;
    /** Steel: carburising iron at about 900 °C; ≈0.8 g of carbon enters each 100 g ingot and ≈10 g of packing charcoal burns. */
    public static final double STEEL_K = 1173, STEEL_CARBON_G = 10.8;
    /** Portland cement clinker forms at about 1450 °C (1723 K); ≈1 part clay to 3 parts limestone. */
    public static final double CEMENT_K = 1720, CEMENT_CLAY_G = 33;
    public static final double CHARGE_W = 15_000, ITEM_ENERGY_J = 120_000;
    public static final double UA_BRICK = 22;               // W/K, uninsulated brick walls
    public static final double WALL_AREA = 0.5, LAYER_THICKNESS = 0.01;
    /** Calcium oxide produced per gram of calcium carbonate: M(CaO)/M(CaCO3) = 56.08/100.09. */
    public static final double CAO_PER_CACO3 = 56.077 / 100.086;

    private double temperatureK = Double.NaN;
    private double fuelJ, fuelItemJ;
    private double chargeJ;
    private double fuelUsedJ;
    private int itemsMade;
    private double quicklimeG;
    private double carbonG;
    private double clayG;
    private String status = "";

    public InsulatedKilnBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.INSULATED_KILN.get(), pos, state, 5);
    }

    @Override
    public List<SlotDef> slots() {
        return List.of(new SlotDef(SLOT_IN, 16, 32, false, "Charge"), new SlotDef(SLOT_ADDITIVE, 16, 68, false, "Additive"),
                new SlotDef(SLOT_FUEL, 16, 104, false, "Fuel"), new SlotDef(SLOT_OUT, 228, 104, true, "Output"),
                new SlotDef(SLOT_INSULATION, 228, 32, false, "Insulation"));
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        if (slot == SLOT_FUEL) return Fuel.energyJ(stack) > 0;
        if (slot == SLOT_INSULATION) return insulationConductivity(stack) > 0;
        if (slot == SLOT_ADDITIVE) return carbonOf(stack) > 0 || stack.is(Items.CLAY_BALL);
        return super.isItemValid(slot, stack);
    }

    public static double insulationConductivity(ItemStack s) {
        if (s.is(ItemTags.WOOL)) return 0.04;
        if (s.is(Items.BRICKS)) return 0.7;
        if (s.is(Items.CLAY)) return 1.0;
        return 0;
    }

    public double lossCoefficient() {
        double k = insulationConductivity(items.getStackInSlot(SLOT_INSULATION));
        if (k <= 0) return UA_BRICK;
        return 1.0 / (1.0 / UA_BRICK + LAYER_THICKNESS * items.getStackInSlot(SLOT_INSULATION).getCount() / (k * WALL_AREA));
    }

    private double ambient() {
        return level == null ? 293.15 : Sci.ambientK(level, worldPosition);
    }

    public double temperatureK() {
        return Double.isNaN(temperatureK) ? ambient() : temperatureK;
    }

    public double progress() {
        return chargeJ / ITEM_ENERGY_J;
    }

    public double fuelFraction() {
        return fuelItemJ <= 0 ? 0 : Math.max(0, Math.min(1, fuelJ / fuelItemJ));
    }

    /** Average fuel energy used per item produced so far (J), or NaN. */
    public double fuelPerItem() {
        return itemsMade == 0 ? Double.NaN : fuelUsedJ / itemsMade;
    }

    public double heatLossW() {
        return lossCoefficient() * (temperatureK() - ambient());
    }

    /** Grams of carbon in a carbon additive (coal 85 g, charcoal 95 g per item in the material model). */
    private static double carbonOf(ItemStack s) {
        if (s.is(Items.COAL)) return 85;
        if (s.is(Items.CHARCOAL)) return 95;
        return 0;
    }

    private boolean steelRecipe(ItemStack input) {
        return input.is(Items.IRON_INGOT) && (carbonG >= STEEL_CARBON_G || carbonOf(items.getStackInSlot(SLOT_ADDITIVE)) > 0);
    }

    private boolean cementRecipe(ItemStack input) {
        return input.is(Items.CALCITE) && (clayG >= CEMENT_CLAY_G || items.getStackInSlot(SLOT_ADDITIVE).is(Items.CLAY_BALL));
    }

    /** Temperature the current charge needs. */
    public double requiredK() {
        ItemStack input = items.getStackInSlot(SLOT_IN);
        if (cementRecipe(input)) return CEMENT_K;
        if (steelRecipe(input)) return STEEL_K;
        if (input.is(ModItems.CALCITE_POWDER.get()) || input.is(Items.CALCITE)) return 1170;
        return PROCESS_K;
    }

    public String status() {
        return status;
    }

    private ItemStack resultFor(ItemStack input) {
        if (input.isEmpty()) return ItemStack.EMPTY;
        if (cementRecipe(input)) return new ItemStack(ModItems.PORTLAND_CEMENT.get());
        if (steelRecipe(input)) return new ItemStack(ModItems.STEEL_INGOT.get());
        if (input.is(ModItems.CALCITE_POWDER.get()) || input.is(Items.CALCITE)) return new ItemStack(ModItems.QUICKLIME.get());
        Optional<SmeltingRecipe> r = level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, new SimpleContainer(input), level);
        return r.map(rec -> rec.getResultItem(level.registryAccess()).copy()).orElse(ItemStack.EMPTY);
    }

    @Override
    public void serverTick() {
        super.serverTick();
        if (ticks % 10 != 0) return;
        double dt = 0.5;
        double t = temperatureK();
        ItemStack input = items.getStackInSlot(SLOT_IN);
        ItemStack result = resultFor(input);
        double needed = requiredK();
        boolean processing = !result.isEmpty() && t >= needed && canOutput(result);
        status = result.isEmpty() ? "" : t < needed ? String.format(java.util.Locale.ROOT, "needs %.0f K", needed) : "processing";
        double loss = lossCoefficient() * (t - ambient());
        double demand = loss + (processing ? CHARGE_W : 0) + HEAT_CAPACITY * Math.max(0, SETPOINT_K - t) / dt;
        boolean wantsHeat = !result.isEmpty() || t > ambient() + 50;
        double burner = 0;
        if (wantsHeat) {
            if (fuelJ <= 0) {
                ItemStack f = items.getStackInSlot(SLOT_FUEL);
                double e = Fuel.energyJ(f);
                if (e > 0 && !result.isEmpty()) {
                    fuelJ += e;
                    fuelItemJ = e;
                    f.shrink(1);
                    items.setStackInSlot(SLOT_FUEL, f);
                }
            }
            if (fuelJ > 0) {
                burner = Math.min(BURNER_W, Math.max(0, demand));
                burner = Math.min(burner, fuelJ / dt);
                fuelJ -= burner * dt;
                fuelUsedJ += burner * dt;
            }
        }
        double charge = processing ? Math.min(CHARGE_W, Math.max(0, burner - loss + HEAT_CAPACITY * (t - needed) / dt)) : 0;
        t += (burner - loss - charge) * dt / HEAT_CAPACITY;
        temperatureK = Math.max(ambient(), t);
        if (processing) {
            chargeJ += charge * dt;
            if (chargeJ >= ITEM_ENERGY_J) {
                chargeJ = 0;
                finishItem(input, result);
            }
        } else if (result.isEmpty()) {
            chargeJ = 0;
        }
        MachineBlock.setActive(level, worldPosition, burner > 0);
        requestSync();
    }

    private boolean canOutput(ItemStack result) {
        ItemStack out = items.getStackInSlot(SLOT_OUT);
        return out.isEmpty() || (ItemStack.isSameItemSameTags(out, result) && out.getCount() + result.getCount() <= out.getMaxStackSize());
    }

    private boolean consumeAdditive(boolean carbon) {
        ItemStack a = items.getStackInSlot(SLOT_ADDITIVE);
        if (carbon && carbonOf(a) > 0) {
            carbonG += carbonOf(a);
        } else if (!carbon && a.is(Items.CLAY_BALL)) {
            clayG += 100;
        } else {
            return false;
        }
        a.shrink(1);
        items.setStackInSlot(SLOT_ADDITIVE, a);
        return true;
    }

    private void finishItem(ItemStack input, ItemStack result) {
        ServerLevel sl = serverLevel();
        if (result.is(ModItems.STEEL_INGOT.get()) || result.is(ModItems.PORTLAND_CEMENT.get())) {
            boolean steel = result.is(ModItems.STEEL_INGOT.get());
            double need = steel ? STEEL_CARBON_G : CEMENT_CLAY_G;
            if ((steel ? carbonG : clayG) < need && !consumeAdditive(steel)) return;
            if (steel) carbonG -= need;
            else clayG -= need;
            input.shrink(1);
            items.setStackInSlot(SLOT_IN, input);
            output(SLOT_OUT, result);
            itemsMade++;
            if (sl != null) JournalService.grant(sl, lastUser, steel ? "craft:steel" : "craft:cement");
            return;
        }
        boolean lime = input.is(ModItems.CALCITE_POWDER.get()) || input.is(Items.CALCITE);
        String inputId = Sci.itemId(input.getItem());
        if (lime) {
            // Mass accounting: CaCO3 → CaO + CO2; whole 10 g portions of quicklime are output.
            double caco3 = input.is(Items.CALCITE) ? 100 : Sci.REAGENT_GRAMS;
            quicklimeG += caco3 * CAO_PER_CACO3;
            input.shrink(1);
            items.setStackInSlot(SLOT_IN, input);
            int n = (int) Math.floor(quicklimeG / Sci.REAGENT_GRAMS);
            if (n > 0) {
                ItemStack rest = output(SLOT_OUT, new ItemStack(ModItems.QUICKLIME.get(), n));
                quicklimeG -= (n - rest.getCount()) * Sci.REAGENT_GRAMS;
            }
            if (sl != null) {
                JournalService.grant(sl, lastUser, "reaction:limestone_decomposition");
                JournalService.grant(sl, lastUser, "reaction:any");
            }
        } else {
            input.shrink(1);
            items.setStackInSlot(SLOT_IN, input);
            output(SLOT_OUT, result);
            if (sl != null && inputId.equals("minecraft:raw_iron")) {
                JournalService.grant(sl, lastUser, "reaction:iron_smelting");
                JournalService.grant(sl, lastUser, "reaction:any");
            }
        }
        itemsMade++;
    }

    @Override
    public List<Reading> readings() {
        return List.of(new Reading("temperature", temperatureK(), 5, "K"));
    }

    @Override
    protected void saveState(CompoundTag tag) {
        tag.putDouble("Temp", temperatureK);
        tag.putDouble("Fuel", fuelJ);
        tag.putDouble("FuelItem", fuelItemJ);
        tag.putDouble("Charge", chargeJ);
        tag.putDouble("FuelUsed", fuelUsedJ);
        tag.putInt("Made", itemsMade);
        tag.putDouble("Lime", quicklimeG);
        tag.putDouble("Carbon", carbonG);
        tag.putDouble("Clay", clayG);
        tag.putString("Status", status);
    }

    @Override
    protected void loadState(CompoundTag tag) {
        temperatureK = tag.contains("Temp") ? tag.getDouble("Temp") : Double.NaN;
        fuelJ = tag.getDouble("Fuel");
        fuelItemJ = tag.getDouble("FuelItem");
        chargeJ = tag.getDouble("Charge");
        fuelUsedJ = tag.getDouble("FuelUsed");
        itemsMade = tag.getInt("Made");
        quicklimeG = tag.getDouble("Lime");
        carbonG = tag.getDouble("Carbon");
        clayG = tag.getDouble("Clay");
        status = tag.getString("Status");
    }
}
