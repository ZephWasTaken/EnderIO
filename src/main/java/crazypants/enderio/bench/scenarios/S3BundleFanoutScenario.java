package crazypants.enderio.bench.scenarios;

import net.minecraft.world.World;

import crazypants.enderio.bench.BenchScenario;
import crazypants.enderio.bench.StructureBuilder;
import crazypants.enderio.conduit.TileConduitBundle;

/**
 * S3 -- bundle fan-out. Ten separate 100-bundle runs (rows), each bundle carrying item + power +
 * redstone conduits (the three lightest-per-type conduits, so the bundle-level fixed cost
 * dominates rather than being swamped by one type's transfer work). 1000 bundles x 3 conduits =
 * ~3000 conduit instances, forming 10 separate networks per conduit type (30 networks total).
 * Rows are spaced two blocks apart so they never touch, deliberately keeping many small networks
 * instead of one giant one. No chests, receptors, or generators are attached, and redstone signal
 * is left at its default (off) -- this isolates the fixed per-tick tax of merely existing.
 */
public class S3BundleFanoutScenario implements BenchScenario {

    private static final int ROWS = 10;
    private static final int BUNDLES_PER_ROW = 100;
    private static final int ROW_SPACING = 2;

    @Override
    public String id() {
        return "S3";
    }

    @Override
    public long seed() {
        return 3003L;
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
        for (int row = 0; row < ROWS; row++) {
            int gz = row * ROW_SPACING;
            for (int col = 0; col < BUNDLES_PER_ROW; col++) {
                TileConduitBundle bundle = b.placeConduitBundle(col, 0, gz);
                b.addItemConduit(bundle);
                b.addPowerConduit(bundle, 0);
                b.addRedstoneConduit(bundle);
            }
        }
    }
}
