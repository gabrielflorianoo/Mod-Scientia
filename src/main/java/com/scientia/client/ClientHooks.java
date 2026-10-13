package com.scientia.client;

import com.scientia.block.ScreenBlock;
import com.scientia.client.screen.CalculatorScreen;
import com.scientia.client.screen.JournalScreen;
import com.scientia.client.screen.MotionTrackScreen;
import com.scientia.client.screen.OpticalBenchScreen;
import com.scientia.client.screen.WorkbenchScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/** Client-only entry points called from common code through DistExecutor. */
@OnlyIn(Dist.CLIENT)
public final class ClientHooks {
    private ClientHooks() {
    }

    public static void openJournal() {
        Minecraft.getInstance().setScreen(new JournalScreen());
    }

    public static void openCalculator() {
        Minecraft.getInstance().setScreen(new CalculatorScreen());
    }

    public static void openBlockScreen(ScreenBlock.Kind kind, BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        switch (kind) {
            case WORKBENCH -> mc.setScreen(new WorkbenchScreen(pos));
            case OPTICAL_BENCH -> mc.setScreen(new OpticalBenchScreen(pos));
            case MOTION_TRACK -> mc.setScreen(new MotionTrackScreen(pos));
        }
    }
}
