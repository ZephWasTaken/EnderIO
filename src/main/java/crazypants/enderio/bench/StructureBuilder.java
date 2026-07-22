package crazypants.enderio.bench;

import java.util.Random;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.world.World;

import crazypants.enderio.EnderIO;
import crazypants.enderio.conduit.IConduit;
import crazypants.enderio.conduit.TileConduitBundle;
import crazypants.enderio.conduit.item.ItemConduit;
import crazypants.enderio.conduit.power.PowerConduit;
import crazypants.enderio.conduit.redstone.InsulatedRedstoneConduit;
import crazypants.enderio.machine.alloy.TileAlloySmelter;
import crazypants.enderio.machine.capbank.CapBankType;
import crazypants.enderio.machine.capbank.TileCapBank;

/**
 * Placement helpers for bench scenarios. All coordinates passed to the {@code place*}/{@code
 * add*} methods are offsets relative to the origin the harness resolved from
 * {@code world.getSpawnPoint()} (see design doc section 3.3) so every scenario stays inside the
 * dedicated server's pre-loaded spawn-chunk area.
 *
 * <p>Per design doc section 3.2, callers must place every target/support block (chests, cap
 * banks, machines) at a given position <em>before</em> placing an adjacent conduit bundle and
 * calling {@code addConduit} on it -- external-connection detection runs synchronously at
 * {@code addConduit} time and does not retroactively re-scan.
 */
public final class StructureBuilder {

    // Silent bulk-edit flag: send to any connected clients, skip neighbor-update floods. There
    // are no players connected in a headless bench run, so this is mostly for parity with the
    // convention used elsewhere in this codebase (e.g. ConduitUtil).
    private static final int FLAGS = 2;

    private final World world;
    private final int originX;
    private final int originY;
    private final int originZ;
    private final Random random;

    public StructureBuilder(World world, int originX, int originY, int originZ, long seed) {
        this.world = world;
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
        this.random = new Random(seed);
    }

    public World world() {
        return world;
    }

    public Random random() {
        return random;
    }

    public int originX() {
        return originX;
    }

    public int originY() {
        return originY;
    }

    public int originZ() {
        return originZ;
    }

    public int x(int dx) {
        return originX + dx;
    }

    public int y(int dy) {
        return originY + dy;
    }

    public int z(int dz) {
        return originZ + dz;
    }

    public TileConduitBundle placeConduitBundle(int dx, int dy, int dz) {
        int px = x(dx);
        int py = y(dy);
        int pz = z(dz);
        world.setBlock(px, py, pz, EnderIO.blockConduitBundle, 0, FLAGS);
        return (TileConduitBundle) world.getTileEntity(px, py, pz);
    }

    public ItemConduit addItemConduit(TileConduitBundle bundle) {
        ItemConduit conduit = new ItemConduit(0);
        bundle.addConduit(conduit);
        return conduit;
    }

    public PowerConduit addPowerConduit(TileConduitBundle bundle, int subtype) {
        PowerConduit conduit = new PowerConduit(subtype);
        bundle.addConduit(conduit);
        return conduit;
    }

    public InsulatedRedstoneConduit addRedstoneConduit(TileConduitBundle bundle) {
        InsulatedRedstoneConduit conduit = new InsulatedRedstoneConduit();
        bundle.addConduit(conduit);
        return conduit;
    }

    public void addConduit(TileConduitBundle bundle, IConduit conduit) {
        bundle.addConduit(conduit);
    }

    public TileEntityChest placeChest(int dx, int dy, int dz) {
        int px = x(dx);
        int py = y(dy);
        int pz = z(dz);
        world.setBlock(px, py, pz, Blocks.chest, 0, FLAGS);
        return (TileEntityChest) world.getTileEntity(px, py, pz);
    }

    /**
     * Fills a chest with a varying number of stackable junk stacks (mixed stack sizes, some
     * slots left empty) so {@code NetworkedInventory.transferItems()}'s slot scan does real,
     * varying-length work every tick instead of hitting all-empty slots immediately.
     */
    public void fillChestJunk(TileEntityChest chest, Random rnd) {
        int slots = chest.getSizeInventory();
        int filled = 4 + rnd.nextInt(Math.max(1, slots - 4));
        for (int i = 0; i < filled; i++) {
            chest.setInventorySlotContents(i, new ItemStack(Blocks.cobblestone, 1 + rnd.nextInt(64)));
        }
    }

    public TileCapBank placeCapBank(int dx, int dy, int dz, CapBankType type) {
        int px = x(dx);
        int py = y(dy);
        int pz = z(dz);
        int meta = CapBankType.getMetaFromType(type);
        world.setBlock(px, py, pz, EnderIO.blockCapBank, meta, FLAGS);
        return (TileCapBank) world.getTileEntity(px, py, pz);
    }

    public TileAlloySmelter placeAlloySmelter(int dx, int dy, int dz) {
        int px = x(dx);
        int py = y(dy);
        int pz = z(dz);
        world.setBlock(px, py, pz, EnderIO.blockAlloySmelter, 0, FLAGS);
        return (TileAlloySmelter) world.getTileEntity(px, py, pz);
    }
}
