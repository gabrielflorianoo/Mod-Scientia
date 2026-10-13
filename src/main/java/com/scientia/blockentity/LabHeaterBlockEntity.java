package com.scientia.blockentity;

import com.scientia.block.MachineBlock;
import com.scientia.config.ScientiaConfig;
import com.scientia.core.chem.Mixture;
import com.scientia.core.chem.ReactionEngine;
import com.scientia.core.physics.ThermalBody;
import com.scientia.item.BeakerItem;
import com.scientia.journal.JournalService;
import com.scientia.registry.ModBlockEntities;
import com.scientia.util.Fuel;
import com.scientia.util.Liquids;
import com.scientia.util.Sci;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Set;

/**
 * A fuel-fired laboratory heater (hot plate) holding up to 1 kg of contents in a beaker. Heating uses the
 * lumped thermal model with latent heats; losses depend on the optional insulating jacket. If a Reaction
 * Vessel sits on top and the heater's own beaker is empty, the heat goes to the vessel instead. Vapour is
 * collected by a Condenser placed above; otherwise it escapes.
 */
public class LabHeaterBlockEntity extends MachineBlockEntity implements Measurable, MixtureHolder {
    public static final int SLOT_FUEL = 0, SLOT_JACKET = 1, SLOT_OUT = 2;
    public static final int ACTION_TOGGLE = 0, ACTION_POWER = 1, ACTION_COLLECT = 2, ACTION_EMPTY = 3;
    public static final double[] POWER_LEVELS = {50, 100, 200};
    public static final double BURNER_EFFICIENCY = 0.5;
    public static final double BEAKER_HEAT_CAPACITY = BeakerItem.GLASS_MASS_G * 0.84;
    public static final double MAX_CONTENTS_G = 1000;
    /** Bare beaker heat-loss coefficient (W/K): convection + radiation from ~0.03 m² of surface. */
    public static final double UA_BARE = 0.6;
    public static final double JACKET_AREA = 0.03, JACKET_THICKNESS = 0.01;

    private final Mixture contents = new Mixture();
    private double temperatureK = Double.NaN;
    private boolean on;
    private int powerLevel = 1;
    private double fuelJ;
    private double fuelItemJ;
    private boolean boiling;
    private int boilingTicks;
    private boolean hadSolute;

    public LabHeaterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LAB_HEATER.get(), pos, state, 3);
    }

    @Override
    public List<SlotDef> slots() {
        return List.of(new SlotDef(SLOT_FUEL, 16, 100, false), new SlotDef(SLOT_JACKET, 16, 76, false), new SlotDef(SLOT_OUT, 228, 100, true));
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        if (slot == SLOT_FUEL) return Fuel.energyJ(stack) > 0;
        if (slot == SLOT_JACKET) return jacketConductivity(stack) > 0;
        return super.isItemValid(slot, stack);
    }

    /** Thermal conductivity (W/(m·K)) of a jacket material, or 0 if the item cannot be used as a jacket. */
    public static double jacketConductivity(ItemStack s) {
        if (s.is(ItemTags.WOOL)) return 0.04;
        if (s.is(Items.GLASS)) return 1.0;
        if (s.is(Items.BRICKS)) return 0.7;
        if (s.is(Items.IRON_BLOCK)) return 80.4;
        if (s.is(Items.COPPER_BLOCK)) return 401;
        if (s.is(ItemTags.PLANKS)) return 0.15;
        if (s.is(Items.STONE)) return 2.0;
        return 0;
    }

    /** Overall loss coefficient UA = 1 / (1/UA_bare + t/(kA)). */
    public double lossCoefficient() {
        double k = jacketConductivity(items.getStackInSlot(SLOT_JACKET));
        if (k <= 0) return UA_BARE;
        return 1.0 / (1.0 / UA_BARE + JACKET_THICKNESS / (k * JACKET_AREA));
    }

    public String jacketId() {
        ItemStack s = items.getStackInSlot(SLOT_JACKET);
        return s.isEmpty() ? "none" : Sci.itemId(s.getItem());
    }

    @Override
    public InteractionResult onUse(ServerPlayer player, InteractionHand hand, ItemStack held) {
        if (held.getItem() instanceof BeakerItem) {
            if (!BeakerItem.isEmpty(held)) {
                Mixture add = Sci.contentsOf(held);
                if (contents.massGrams(Sci.db().substances()) + add.massGrams(Sci.db().substances()) > MAX_CONTENTS_G) {
                    player.displayClientMessage(Component.translatable("scientia.msg.full").withStyle(ChatFormatting.GRAY), true);
                    return InteractionResult.CONSUME;
                }
                if (contents.isEmpty()) temperatureK = ambient();
                else mixTemperature(add, ambient());
                add.amounts().forEach(contents::add);
                replaceHeld(player, hand, held, new ItemStack(held.getItem()));
                level.playSound(null, worldPosition, SoundEvents.BOTTLE_EMPTY, SoundSource.BLOCKS, 0.7f, 1);
            } else if (!contents.isEmpty()) {
                Mixture taken = Liquids.takeLiquid(contents, BeakerItem.CAPACITY_ML * 0.997);
                if (taken.isEmpty()) return InteractionResult.PASS;
                replaceHeld(player, hand, held, BeakerItem.filled(held.getItem(), taken));
                level.playSound(null, worldPosition, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 0.7f, 1);
            } else {
                return InteractionResult.PASS;
            }
            requestSync();
            return InteractionResult.CONSUME;
        }
        if (Sci.material(held) != null && !Sci.substancesForItem(held).isEmpty() && !isItemValid(SLOT_FUEL, held) && !isItemValid(SLOT_JACKET, held)) {
            Mixture add = new Mixture(Sci.substancesForItem(held));
            // Ice from the world is at or below 0 °C; other solids start at room temperature.
            double addTemp = add.contains("ice") ? Math.min(ambient(), 273.15) : ambient();
            if (contents.isEmpty()) temperatureK = addTemp;
            else mixTemperature(add, addTemp);
            add.amounts().forEach(contents::add);
            if (!player.isCreative()) held.shrink(1);
            requestSync();
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    private static void replaceHeld(ServerPlayer player, InteractionHand hand, ItemStack held, ItemStack result) {
        if (player.isCreative()) {
            if (!player.getInventory().add(result)) player.drop(result, false);
            return;
        }
        if (held.getCount() == 1) {
            player.setItemInHand(hand, result);
        } else {
            held.shrink(1);
            if (!player.getInventory().add(result)) player.drop(result, false);
        }
    }

    /** Mixing two portions: the final temperature conserves enthalpy (sensible heat only). */
    private void mixTemperature(Mixture add, double addTemp) {
        double c1 = heatCapacityOf(contents), c2 = heatCapacityOf(add);
        if (c1 + c2 > 0) temperatureK = (c1 * temperatureK + c2 * addTemp) / (c1 + c2);
    }

    private static double heatCapacityOf(Mixture m) {
        return Sci.waterGrams(m) * ThermalBody.WATER_C + Sci.otherHeatCapacity(m);
    }

    private double ambient() {
        return level == null ? 293.15 : Sci.ambientK(level, worldPosition);
    }

    @Override
    public void handleAction(ServerPlayer player, int action, int arg) {
        switch (action) {
            case ACTION_TOGGLE -> on = !on;
            case ACTION_POWER -> powerLevel = (powerLevel + 1) % POWER_LEVELS.length;
            case ACTION_COLLECT -> collectSolids();
            case ACTION_EMPTY -> {
                contents.clear();
                temperatureK = Double.NaN;
            }
            default -> {
            }
        }
        requestSync();
    }

    /** Packages undissolved solids (e.g. salt left after evaporation) into 10 g reagent items. */
    private void collectSolids() {
        var subs = Sci.db().substances();
        for (var e : Sci.SOLID_ITEMS.entrySet()) {
            String id = e.getKey();
            double solid = contents.get(id) - Liquids.liquidPortion(contents, id, subs);
            int n = (int) Math.floor(solid * subs.molarMass(id) / Sci.REAGENT_GRAMS + 1e-9);
            if (n <= 0) continue;
            ItemStack rest = output(SLOT_OUT, new ItemStack(e.getValue().get(), n));
            int made = n - rest.getCount();
            contents.remove(id, made * Sci.REAGENT_GRAMS / subs.molarMass(id));
        }
    }

    public double powerW() {
        return POWER_LEVELS[powerLevel];
    }

    public boolean isOn() {
        return on;
    }

    public boolean isHeating() {
        return on && fuelJ > 0;
    }

    public double fuelFraction() {
        return fuelItemJ <= 0 ? 0 : Math.max(0, Math.min(1, fuelJ / fuelItemJ));
    }

    public boolean boiling() {
        return boiling;
    }

    public double ambientK() {
        return ambient();
    }

    public double boilingPointK() {
        return ThermalBody.boilingPointOfSolution(Sci.soluteParticleMolality(contents));
    }

    @Override
    public Mixture mixture() {
        return contents;
    }

    @Override
    public double temperatureK() {
        return Double.isNaN(temperatureK) ? ambient() : temperatureK;
    }

    @Override
    public void serverTick() {
        super.serverTick();
        int interval = ScientiaConfig.THERMAL_INTERVAL.get();
        if (ticks % interval != 0) return;
        double dt = interval / 20.0;
        double heat = 0;
        if (on) {
            if (fuelJ <= 0) {
                ItemStack f = items.getStackInSlot(SLOT_FUEL);
                double e = Fuel.energyJ(f);
                if (e > 0) {
                    fuelJ += e;
                    fuelItemJ = e;
                    f.shrink(1);
                    items.setStackInSlot(SLOT_FUEL, f);
                }
            }
            if (fuelJ > 0) {
                heat = powerW();
                fuelJ = Math.max(0, fuelJ - heat / BURNER_EFFICIENCY * dt);
            }
        }
        MachineBlock.setActive(level, worldPosition, heat > 0);
        BlockEntity above = level.getBlockEntity(worldPosition.above());
        if (contents.isEmpty()) {
            temperatureK = Double.NaN;
            boiling = false;
            if (heat > 0 && above instanceof ReactionVesselBlockEntity vessel) vessel.addExternalHeat(heat * dt);
            requestSync();
            return;
        }
        stepThermal(heat, dt, above);
        stepReactions(dt);
        requestSync();
    }

    private void stepThermal(double heat, double dt, BlockEntity above) {
        var subs = Sci.db().substances();
        if (Double.isNaN(temperatureK)) temperatureK = ambient();
        double waterG = contents.massOf("water", subs), iceG = contents.massOf("ice", subs);
        double other = Sci.otherHeatCapacity(contents) - iceG * (subs.get("ice").specificHeat()) + BEAKER_HEAT_CAPACITY;
        ThermalBody body = new ThermalBody(temperatureK, waterG, iceG, Math.max(0, other));
        double tb = boilingPointK();
        boolean solute = Sci.soluteParticleMolality(contents) > 0.01;
        hadSolute = solute;
        double vaporised = 0, melted = 0;
        int sub = Math.max(1, (int) Math.ceil(dt / 0.5));
        for (int i = 0; i < sub; i++) {
            ThermalBody.StepResult sr = body.step(heat, ambient(), lossCoefficient(), tb, dt / sub);
            vaporised += sr.vaporisedGrams();
            melted += sr.meltedGrams();
        }
        if (melted > 0 && serverLevel() != null) JournalService.grant(serverLevel(), lastUser, "phase:melting");
        temperatureK = body.temperatureK();
        contents.remove("water", contents.get("water"));
        contents.remove("ice", contents.get("ice"));
        if (body.waterGrams() > 1e-6) contents.add("water", body.waterGrams() / subs.molarMass("water"));
        if (body.iceGrams() > 1e-6) contents.add("ice", body.iceGrams() / subs.molarMass("ice"));
        boolean wasBoiling = boiling;
        boiling = vaporised > 0;
        ServerLevel sl = serverLevel();
        if (boiling) {
            boilingTicks++;
            if (above instanceof CondenserBlockEntity condenser) condenser.collect(vaporised, solute, lastUser);
            else if (sl != null && ticks % 20 == 0) sl.sendParticles(ParticleTypes.CLOUD, worldPosition.getX() + 0.5, worldPosition.getY() + 1.0, worldPosition.getZ() + 0.5, 2, 0.15, 0.05, 0.15, 0.01);
            if (sl != null) {
                JournalService.grant(sl, lastUser, "phase:boiling");
                if (boilingTicks == 10) {
                    ServerPlayer p = JournalService.online(sl, lastUser);
                    if (p != null) JournalService.resolveHypothesis(p, "phase_change", "equal", String.format(java.util.Locale.ROOT,
                            "While boiling, the temperature stayed at %.1f °C although the heater kept supplying %.0f W: the energy is used as latent heat of vaporisation.",
                            tb - 273.15, heat));
                }
            }
        } else {
            boilingTicks = 0;
        }
        if (wasBoiling && !contents.contains("water") && sl != null) {
            JournalService.grant(sl, lastUser, "separate:evaporation");
        }
    }

    private void stepReactions(double dt) {
        if (temperatureK() < 500) return; // the only heater-relevant reactions (oxidation of hot metals) need high temperature
        if (!contents.contains("oxygen")) contents.add("oxygen", 0.01); // air above the open beaker
        ReactionEngine.StepResult r = Sci.db().reactionEngine().step(contents,
                new ReactionEngine.Conditions(temperatureK(), Set.of("heat"), false, 0), dt);
        ServerLevel sl = serverLevel();
        for (ReactionEngine.Event e : r.events()) {
            if (sl != null) {
                JournalService.grant(sl, lastUser, "reaction:" + e.reaction().id());
                JournalService.grant(sl, lastUser, "reaction:any");
            }
        }
        contents.remove("oxygen", contents.get("oxygen"));
    }

    @Override
    public List<Reading> readings() {
        return List.of(new Reading("temperature", temperatureK(), 0.5, "K"));
    }

    @Override
    public void clientTick() {
        super.clientTick();
        if (boiling && level != null && level.random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.BUBBLE_POP, worldPosition.getX() + 0.3 + level.random.nextDouble() * 0.4,
                    worldPosition.getY() + 0.85, worldPosition.getZ() + 0.3 + level.random.nextDouble() * 0.4, 0, 0.02, 0);
        }
    }

    @Override
    protected void saveState(CompoundTag tag) {
        tag.put("Contents", Sci.writeMixture(contents));
        tag.putDouble("Temp", temperatureK);
        tag.putBoolean("On", on);
        tag.putInt("Power", powerLevel);
        tag.putDouble("Fuel", fuelJ);
        tag.putDouble("FuelItem", fuelItemJ);
        tag.putBoolean("Boiling", boiling);
        tag.putBoolean("Solute", hadSolute);
    }

    @Override
    protected void loadState(CompoundTag tag) {
        contents.clear();
        Sci.readMixture(tag.getCompound("Contents")).amounts().forEach(contents::add);
        temperatureK = tag.contains("Temp") ? tag.getDouble("Temp") : Double.NaN;
        on = tag.getBoolean("On");
        powerLevel = Math.max(0, Math.min(POWER_LEVELS.length - 1, tag.getInt("Power")));
        fuelJ = tag.getDouble("Fuel");
        fuelItemJ = tag.getDouble("FuelItem");
        boiling = tag.getBoolean("Boiling");
        hadSolute = tag.getBoolean("Solute");
    }
}
