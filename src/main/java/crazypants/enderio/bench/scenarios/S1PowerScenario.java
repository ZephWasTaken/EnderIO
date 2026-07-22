package crazypants.enderio.bench.scenarios;

import net.minecraft.world.World;

import crazypants.enderio.bench.BenchScenario;
import crazypants.enderio.bench.StructureBuilder;
import crazypants.enderio.conduit.TileConduitBundle;
import crazypants.enderio.machine.capbank.CapBankType;
import crazypants.enderio.machine.capbank.TileCapBank;

/**
 * S1 -- power network. ~300 power conduits forming one connected branching grid, ~50 idle-but-
 * charging alloy smelters as receptors, ~10 non-multiblock cap banks seeded to different fill
 * ratios, and 3 creative cap banks standing in for "several generators" (see design doc section 1,
 * S1, for why a creative source is used instead of simulating a fuel-burning generator).
 */
public class S1PowerScenario implements BenchScenario {

    private static final int WIDTH = 20;
    private static final int DEPTH = 15;
    private static final int CELLS = WIDTH * DEPTH; // 300

    @Override
    public String id() {
        return "S1";
    }

    @Override
    public long seed() {
        return 1001L;
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
        int capBankIndex = 0;
        int capBankCount = 0;
        for (int i = 0; i < CELLS; i++) {
            if (i % 30 == 15) {
                capBankCount++;
            }
        }

        // Phase 1: place every target/support block first (design doc section 3.2) -- alloy
        // smelters (receptors), cap banks (storage) and creative cap banks (generator stand-ins)
        // -- one block above the power-conduit grid layer.
        for (int i = 0; i < CELLS; i++) {
            int gx = i % WIDTH;
            int gz = i / WIDTH;
            if (i % 6 == 0) {
                // Idle-but-charging: leave recipe input slots empty, matches the design's chosen
                // steady state for a real base.
                b.placeAlloySmelter(gx, 1, gz);
            } else if (i % 30 == 15) {
                boolean activated = (capBankIndex % 2) == 1;
                CapBankType type = activated ? CapBankType.ACTIVATED : CapBankType.SIMPLE;
                TileCapBank cap = b.placeCapBank(gx, 1, gz, type);
                float fillRatio = (capBankIndex + 1f) / (capBankCount + 1f);
                cap.setEnergyStored(Math.round(type.getMaxEnergyStored() * fillRatio));
                capBankIndex++;
            } else if (i % 100 == 50) {
                TileCapBank generator = b.placeCapBank(gx, 1, gz, CapBankType.CREATIVE);
                generator.setEnergyStored(CapBankType.CREATIVE.getMaxEnergyStored());
            }
        }

        // Phase 2: build the power-conduit grid last, in raster order. Each new conduit's
        // onAddedToBundle() synchronously scans all 6 neighbor directions, wiring up both
        // conduit-to-conduit and external connections to whatever is already in the world.
        for (int i = 0; i < CELLS; i++) {
            int gx = i % WIDTH;
            int gz = i / WIDTH;
            TileConduitBundle bundle = b.placeConduitBundle(gx, 0, gz);
            b.addPowerConduit(bundle, 0);
        }
    }
}
