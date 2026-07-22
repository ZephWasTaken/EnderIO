package crazypants.enderio.bench.scenarios;

import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import crazypants.enderio.bench.BenchScenario;
import crazypants.enderio.bench.StructureBuilder;
import crazypants.enderio.conduit.TileConduitBundle;
import crazypants.enderio.conduit.item.ItemConduit;

/**
 * S2 -- item network. ~200 item conduits, one connected network, snake/grid layout. ~60 external
 * chests, pre-populated with a mix of stackable junk so the network's per-tick slot scan does
 * real, varying-length work instead of hitting all-empty slots immediately. A subset of the
 * chest-connected conduits get priorities/round-robin toggled directly through the real
 * {@code IItemConduit} API, exercising {@code Target.compareTo}'s priority/round-robin branches.
 */
public class S2ItemScenario implements BenchScenario {

    private static final int WIDTH = 20;
    private static final int DEPTH = 10;
    private static final int CELLS = WIDTH * DEPTH; // 200

    /**
     * A representative item conduit whose network the S2-churn variant pokes on a fixed cadence.
     */
    protected ItemConduit churnAnchor;

    @Override
    public String id() {
        return "S2";
    }

    @Override
    public long seed() {
        return 2002L;
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
        // Phase 1: place every external inventory first (design doc section 3.2), one block
        // above the item-conduit grid layer, and fill it with varying junk so the slot scan does
        // real work.
        for (int i = 0; i < CELLS; i++) {
            if (i % 10 < 3) {
                int gx = i % WIDTH;
                int gz = i / WIDTH;
                TileEntityChest chest = b.placeChest(gx, 1, gz);
                b.fillChestJunk(chest, b.random());
            }
        }

        // Phase 2: build the item-conduit grid last, in raster order.
        int priorityCounter = 0;
        for (int i = 0; i < CELLS; i++) {
            int gx = i % WIDTH;
            int gz = i / WIDTH;
            TileConduitBundle bundle = b.placeConduitBundle(gx, 0, gz);
            ItemConduit conduit = b.addItemConduit(bundle);
            if (i == 0) {
                churnAnchor = conduit;
            }
            if (i % 10 < 3) {
                // This cell has a chest directly above it -- exercise priority/round-robin on a
                // subset of the chest-facing conduits.
                conduit.setOutputPriority(ForgeDirection.UP, priorityCounter % 5);
                if (priorityCounter % 2 == 0) {
                    conduit.setRoundRobinEnabled(ForgeDirection.UP, true);
                }
                priorityCounter++;
            }
        }
    }
}
