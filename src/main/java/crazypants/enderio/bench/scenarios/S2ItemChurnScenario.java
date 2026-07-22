package crazypants.enderio.bench.scenarios;

import crazypants.enderio.conduit.AbstractConduitNetwork;
import crazypants.enderio.conduit.item.ItemConduitNetwork;

/**
 * S2-churn -- identical structure to {@link S2ItemScenario}, but every {@link #CHURN_INTERVAL}
 * ticks during the measurement window the harness calls {@code ItemConduitNetwork.routesChanged()}
 * directly on one conduit's network, forcing every {@code NetworkedInventory} in the network to
 * rebuild its insert order (and, with the default config, re-run the BFS distance flood fill) on
 * the very next tick. This is the "topology-churn variant that repeatedly triggers route
 * re-sorts" scenario from the design doc.
 */
public class S2ItemChurnScenario extends S2ItemScenario {

    // Every measured tick pays the resort, so the median (not just p99) reflects the churn cost.
    private static final int CHURN_INTERVAL = 1;

    @Override
    public String id() {
        return "S2-churn";
    }

    @Override
    public long seed() {
        return 2003L;
    }

    @Override
    public void tick(int tickIndex) {
        if (churnAnchor == null || tickIndex < warmupTicks()) {
            return;
        }
        if ((tickIndex - warmupTicks()) % CHURN_INTERVAL != 0) {
            return;
        }
        AbstractConduitNetwork<?, ?> network = churnAnchor.getNetwork();
        if (network instanceof ItemConduitNetwork) {
            ((ItemConduitNetwork) network).routesChanged();
        }
    }
}
