package crazypants.enderio.bench.scenarios;

import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.world.World;

import crazypants.enderio.bench.BenchScenario;
import crazypants.enderio.bench.StructureBuilder;
import crazypants.enderio.conduit.TileConduitBundle;
import crazypants.enderio.conduit.liquid.LiquidConduit;
import crazypants.enderio.machine.capbank.CapBankType;
import crazypants.enderio.machine.capbank.TileCapBank;

/**
 * S4 -- mixed/realistic. A smaller, all-subsystems-at-once composite used as a regression guard:
 * one line of bundles each carrying item + power + redstone + liquid conduits, with chests, alloy
 * smelters and cap banks attached to a subset of cells. Not a stress test on any single axis --
 * run this against every candidate branch, even one targeting S1/S2 only, to catch cross-
 * subsystem regressions in shared code such as {@code ConduitNetworkTickHandler}'s dispatch loop.
 */
public class S4MixedScenario implements BenchScenario {

    private static final int CHEST_CELLS = 15;
    private static final int SMELTER_CELLS = 5;
    private static final int CAPBANK_CELLS = 3;
    private static final int CELLS = CHEST_CELLS + SMELTER_CELLS + CAPBANK_CELLS; // 23

    @Override
    public String id() {
        return "S4";
    }

    @Override
    public long seed() {
        return 4004L;
    }

    @Override
    public int warmupTicks() {
        return 200;
    }

    @Override
    public int measureTicks() {
        return 1200;
    }

    @Override
    public void build(World world, StructureBuilder b) {
        // Phase 1: place every target/support block first (design doc section 3.2).
        for (int i = 0; i < CELLS; i++) {
            if (i < CHEST_CELLS) {
                TileEntityChest chest = b.placeChest(i, 1, 0);
                b.fillChestJunk(chest, b.random());
            } else if (i < CHEST_CELLS + SMELTER_CELLS) {
                b.placeAlloySmelter(i, 1, 0);
            } else {
                int capIndex = i - (CHEST_CELLS + SMELTER_CELLS);
                CapBankType type = capIndex == 0 ? CapBankType.SIMPLE
                        : capIndex == 1 ? CapBankType.ACTIVATED : CapBankType.CREATIVE;
                TileCapBank cap = b.placeCapBank(i, 1, 0, type);
                float fillRatio = type.isCreative() ? 1f : (capIndex + 1f) / (CAPBANK_CELLS + 1f);
                cap.setEnergyStored(Math.round(type.getMaxEnergyStored() * fillRatio));
            }
        }

        // Phase 2: build the mixed conduit line last -- item, power, redstone and liquid in
        // every bundle.
        for (int i = 0; i < CELLS; i++) {
            TileConduitBundle bundle = b.placeConduitBundle(i, 0, 0);
            b.addItemConduit(bundle);
            b.addPowerConduit(bundle, 0);
            b.addRedstoneConduit(bundle);
            b.addConduit(bundle, new LiquidConduit());
        }
    }
}
