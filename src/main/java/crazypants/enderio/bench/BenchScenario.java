package crazypants.enderio.bench;

import net.minecraft.world.World;

/**
 * A single benchmark scenario: builds a fixed structure once (synchronously, inside
 * {@code FMLServerStartedEvent}) and declares how many warm-up/measurement ticks the run protocol
 * should use for it. See {@code deliverables/harness-design.md} section 1 for the scenario catalog.
 */
public interface BenchScenario {

    /**
     * Short id used on the command line, e.g. {@code -Denderio.bench=S1}.
     */
    String id();

    /**
     * Fixed seed for this scenario so placement is byte-identical across repeated runs.
     */
    long seed();

    /**
     * Build the scenario's structure. Called once, synchronously, from
     * {@link BenchHarness#onServerStarted}, before the first server tick.
     */
    void build(World world, StructureBuilder builder);

    /**
     * Ticks to record before the measurement window starts (see design doc section 5.4).
     */
    int warmupTicks();

    /**
     * Ticks to record as the measurement window (see design doc section 5.4).
     */
    int measureTicks();

    /**
     * Optional periodic hook, called once per tick (after the world has already ticked and the
     * conduit networks have already run for that tick) with the harness's own 0-based tick index.
     * Used by scenarios such as S2-churn that need to poke the network on a fixed cadence.
     */
    default void tick(int tickIndex) {}
}
