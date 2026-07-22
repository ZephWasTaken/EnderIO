package crazypants.enderio.bench;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.WorldServer;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLServerStartedEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.gameevent.TickEvent.Phase;

import crazypants.enderio.bench.scenarios.S1PowerScenario;
import crazypants.enderio.bench.scenarios.S2ItemChurnScenario;
import crazypants.enderio.bench.scenarios.S2ItemScenario;
import crazypants.enderio.bench.scenarios.S3BundleFanoutScenario;
import crazypants.enderio.bench.scenarios.S4MixedScenario;

/**
 * Orchestrator for the local benchmark harness. Fully inert unless {@code -Denderio.bench=<id>}
 * (or the {@code ENDERIO_BENCH} environment variable, as a fallback for the unverified
 * {@code _JAVA_OPTIONS} injection path -- see design doc section 5.2) is set. See
 * {@code deliverables/harness-design.md} for the full design this class implements.
 */
public final class BenchHarness {

    public static final String SCENARIO = resolveProperty("enderio.bench", "ENDERIO_BENCH");
    public static final boolean ENABLED = SCENARIO != null && !SCENARIO.isEmpty();

    private BenchScenario scenario;
    private int warmupTicks;
    private int measureTicks;
    private long tickIndex = -1;
    private long tickStartNs;
    private BufferedWriter csv;
    private boolean shuttingDown;

    private BenchHarness() {}

    /**
     * Property-wins, env-var-fallback resolution, per the task's gating rule. Property is
     * checked first so a local {@code -D} flag always wins over a leaked/shared environment
     * variable.
     */
    private static String resolveProperty(String propertyName, String envName) {
        String prop = System.getProperty(propertyName);
        if (prop != null && !prop.isEmpty()) {
            return prop;
        }
        String env = System.getenv(envName);
        if (env != null && !env.isEmpty()) {
            return env;
        }
        return null;
    }

    public static void onServerStarted(FMLServerStartedEvent event) {
        if (!ENABLED) {
            return;
        }
        new BenchHarness().start();
    }

    private void start() {
        scenario = resolveScenario(SCENARIO);
        if (scenario == null) {
            System.err.println("[EnderIO Bench] Unknown scenario id '" + SCENARIO + "' -- harness will not run. "
                    + "Known ids: S1, S2, S2-churn, S3, S4");
            return;
        }

        WorldServer world = MinecraftServer.getServer().worldServerForDimension(0);
        applyGameRules(world);

        ChunkCoordinates spawn = world.getSpawnPoint();
        StructureBuilder builder = new StructureBuilder(world, spawn.posX, spawn.posY + 1, spawn.posZ,
                scenario.seed());
        scenario.build(world, builder);

        warmupTicks = scenario.warmupTicks();
        measureTicks = scenario.measureTicks();

        String label = sanitize(resolveProperty("enderio.bench.label", "ENDERIO_BENCH_LABEL"), "run");
        String runId = sanitize(resolveProperty("enderio.bench.run", "ENDERIO_BENCH_RUN"), "1");
        String gitCommit = System.getProperty("enderio.bench.gitCommit", "unknown");

        try {
            openCsv(scenario.id(), label, runId, gitCommit);
        } catch (IOException e) {
            System.err.println("[EnderIO Bench] Could not open results CSV, harness will not run: " + e.getMessage());
            scenario = null;
            return;
        }

        BenchTiming.reset();
        tickIndex = -1;
        FMLCommonHandler.instance().bus().register(this);
        System.out.println("[EnderIO Bench] scenario=" + scenario.id() + " label=" + label + " run=" + runId
                + " warmupTicks=" + warmupTicks + " measureTicks=" + measureTicks);
    }

    private static String sanitize(String value, String fallback) {
        if (value == null || value.isEmpty()) {
            return fallback;
        }
        return value.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static BenchScenario resolveScenario(String id) {
        if ("S1".equals(id)) {
            return new S1PowerScenario();
        } else if ("S2".equals(id)) {
            return new S2ItemScenario();
        } else if ("S2-churn".equals(id)) {
            return new S2ItemChurnScenario();
        } else if ("S3".equals(id)) {
            return new S3BundleFanoutScenario();
        } else if ("S4".equals(id)) {
            return new S4MixedScenario();
        }
        return null;
    }

    private static void applyGameRules(WorldServer world) {
        world.getGameRules().setOrCreateGameRule("doMobSpawning", "false");
        world.getGameRules().setOrCreateGameRule("doDaylightCycle", "false");
        world.getGameRules().setOrCreateGameRule("doWeatherCycle", "false");
        world.getGameRules().setOrCreateGameRule("randomTickSpeed", "0");
        world.getGameRules().setOrCreateGameRule("doFireTick", "false");
        world.getGameRules().setOrCreateGameRule("mobGriefing", "false");
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (scenario == null || shuttingDown) {
            return;
        }
        if (event.phase == Phase.START) {
            BenchTiming.reset();
            tickStartNs = System.nanoTime();
        } else {
            long totalNs = System.nanoTime() - tickStartNs;
            tickIndex++;
            int idx = (int) tickIndex;
            scenario.tick(idx);
            String phase = idx < warmupTicks ? "WARMUP" : "MEASURE";
            writeRow(idx, phase, totalNs);
            if (idx + 1 >= warmupTicks + measureTicks) {
                shutdown();
            }
        }
    }

    private void writeRow(int tickIdx, String phase, long totalNs) {
        long conduitNs = BenchTiming.getConduitNs();
        long powerNs = BenchTiming.getNsForSimpleName("PowerConduitNetwork");
        long itemNs = BenchTiming.getNsForSimpleName("ItemConduitNetwork");
        long liquidNs = BenchTiming.getNsForSimpleName("LiquidConduitNetwork")
                + BenchTiming.getNsForSimpleName("AdvancedLiquidConduitNetwork")
                + BenchTiming.getNsForSimpleName("EnderLiquidConduitNetwork");
        long redstoneNs = BenchTiming.getNsForSimpleName("RedstoneConduitNetwork");
        long gasNs = BenchTiming.getNsForSimpleName("GasConduitNetwork");
        long meNs = BenchTiming.getNsForSimpleName("MEConduitNetwork");
        long ocNs = BenchTiming.getNsForSimpleName("OCConduitNetwork");
        long knownNs = powerNs + itemNs + liquidNs + redstoneNs + gasNs + meNs + ocNs;
        long otherConduitNs = conduitNs - knownNs;

        StringBuilder row = new StringBuilder(96);
        row.append(tickIdx).append(',');
        row.append(phase).append(',');
        row.append(totalNs).append(',');
        row.append(conduitNs).append(',');
        row.append(powerNs).append(',');
        row.append(itemNs).append(',');
        row.append(liquidNs).append(',');
        row.append(redstoneNs).append(',');
        row.append(gasNs).append(',');
        row.append(meNs).append(',');
        row.append(ocNs).append(',');
        row.append(otherConduitNs);
        try {
            csv.write(row.toString());
            csv.newLine();
        } catch (IOException e) {
            System.err.println("[EnderIO Bench] Failed to write CSV row: " + e.getMessage());
        }
    }

    private void openCsv(String scenarioId, String label, String runId, String gitCommit) throws IOException {
        File dir = new File("bench-results" + File.separator + scenarioId);
        if (!dir.exists() && !dir.mkdirs() && !dir.exists()) {
            throw new IOException("could not create results directory: " + dir.getAbsolutePath());
        }
        File csvFile = new File(dir, label + "-run" + runId + ".csv");
        csv = new BufferedWriter(new FileWriter(csvFile, false));
        csv.write("tickIndex,phase,totalNs,conduitNs,powerNs,itemNs,liquidNs,redstoneNs,gasNs,meNs,ocNs,"
                + "otherConduitNs");
        csv.newLine();
        csv.flush();

        File metaFile = new File(dir, label + "-run" + runId + ".meta.json");
        try (BufferedWriter meta = new BufferedWriter(new FileWriter(metaFile, false))) {
            meta.write("{\n");
            meta.write("  \"scenario\": \"" + scenarioId + "\",\n");
            meta.write("  \"label\": \"" + label + "\",\n");
            meta.write("  \"runId\": \"" + runId + "\",\n");
            meta.write("  \"gitCommit\": \"" + gitCommit + "\",\n");
            meta.write("  \"warmupTicks\": " + warmupTicks + ",\n");
            meta.write("  \"measureTicks\": " + measureTicks + ",\n");
            meta.write("  \"seed\": " + scenario.seed() + ",\n");
            meta.write("  \"startTimeMillis\": " + System.currentTimeMillis() + "\n");
            meta.write("}\n");
        }
    }

    private void shutdown() {
        if (shuttingDown) {
            return;
        }
        shuttingDown = true;
        try {
            csv.flush();
            csv.close();
        } catch (IOException e) {
            System.err.println("[EnderIO Bench] Failed to close CSV: " + e.getMessage());
        }
        FMLCommonHandler.instance().bus().unregister(this);
        System.out.println("[EnderIO Bench] measurement complete, shutting down server.");
        MinecraftServer.getServer().initiateShutdown();
    }
}
