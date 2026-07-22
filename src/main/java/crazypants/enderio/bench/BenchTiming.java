package crazypants.enderio.bench;

import java.util.HashMap;
import java.util.Map;

/**
 * nanoTime accumulator for the current server tick's conduit-network work, plus a per-network-
 * class breakdown. Reset once per tick (at {@code ServerTickEvent.Phase.START}, from
 * {@link BenchHarness}) and read back at {@code Phase.END} before the CSV row is written.
 *
 * <p>Per-network-class entries are keyed by {@link Class#getSimpleName()} rather than by
 * {@code Class} object. Some network types ({@code GasConduitNetwork}, {@code MEConduitNetwork},
 * {@code OCConduitNetwork}) sit behind soft dependencies (Mekanism/AE2/OpenComputers) that are
 * only {@code compileOnly} on this project's classpath (see {@code dependencies.gradle}); keying
 * by name avoids ever needing a {@code Foo.class} literal for one of those types inside this
 * bench-only package, so recording timings never risks a classloading surprise on a server where
 * the corresponding mod isn't installed. The instances actually being timed are only ever
 * obtained from {@code cn.getClass()} on a network object the surrounding (non-bench) code
 * already constructed and is already running -- so this is purely a defensive choice on the
 * reading side, not a functional change to what gets measured.
 */
public final class BenchTiming {

    private static final Map<String, Long> perClassNs = new HashMap<>();
    private static long conduitNs;

    private BenchTiming() {}

    public static void reset() {
        perClassNs.clear();
        conduitNs = 0L;
    }

    public static void record(Class<?> networkClass, long ns) {
        String key = networkClass.getSimpleName();
        Long previous = perClassNs.get(key);
        perClassNs.put(key, previous == null ? ns : previous + ns);
        conduitNs += ns;
    }

    public static long getConduitNs() {
        return conduitNs;
    }

    public static long getNsForSimpleName(String simpleName) {
        Long value = perClassNs.get(simpleName);
        return value == null ? 0L : value;
    }
}
