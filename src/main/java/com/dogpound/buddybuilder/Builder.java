package com.dogpound.buddybuilder;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Server side: turns build orders into block placements and places them a few hundred per tick. */
public class Builder {
    private static final ArrayDeque<Object[]> queue = new ArrayDeque<>(); // {BlockPos, IBlockState, undo list or null}
    private static final ArrayDeque<List<Object[]>> history = new ArrayDeque<>(); // one list of old blocks per order
    private static List<Object[]> group = new ArrayList<>();
    private static World world;
    public static volatile int placed, total;
    public static volatile java.util.UUID worker;
    private static BlockPos last;
    private static int tick;

    /** "mod:block" or "mod:block:meta" -> state, or null when the block doesn't exist. */
    @SuppressWarnings("deprecation")
    public static IBlockState state(String id) {
        if (id == null || id.isEmpty()) return null;
        int meta = 0;
        String[] p = id.split(":");
        if (p.length == 3) {
            try { meta = Integer.parseInt(p[2]); } catch (NumberFormatException ignored) { }
            id = p[0] + ":" + p[1];
        }
        ResourceLocation rl = new ResourceLocation(id);
        if (!Block.REGISTRY.containsKey(rl)) return null;
        Block b = Block.REGISTRY.getObject(rl);
        return meta == 0 ? b.getDefaultState() : b.getStateFromMeta(meta);
    }

    private static IBlockState or(String id, IBlockState fallback) {
        IBlockState s = state(id);
        return s != null ? s : fallback;
    }

    private static int ground(int x, int z) {
        return world.getTopSolidOrLiquidBlock(new BlockPos(x, 0, z)).getY() - 1;
    }

    /** Runs on the server thread. Collects the placements for one job, then queues them. */
    public static synchronized String road(World w, int x1, int z1, int x2, int z2, int width, String surface, String edge, String lamp, int lampEvery) {
        world = w;
        IBlockState top = or(surface, Blocks.STONEBRICK.getDefaultState());
        IBlockState side = state(edge), light = state(lamp);
        Map<BlockPos, IBlockState> out = new LinkedHashMap<>();
        double dx = x2 - x1, dz = z2 - z1, len = Math.max(1, Math.hypot(dx, dz));
        double px = -dz / len, pz = dx / len; // sideways
        int half = Math.max(1, width) / 2, outer = half + (side != null ? 1 : 0);
        Integer prevY = null;
        for (int i = 0; i <= (int) len; i++) {
            double cx = x1 + dx * i / len, cz = z1 + dz * i / len;
            int y = ground((int) Math.round(cx), (int) Math.round(cz));
            if (prevY != null) y = Math.max(prevY - 1, Math.min(prevY + 1, y)); // no cliffs in the road
            prevY = y;
            for (double o = -outer; o <= outer; o += 0.5) {
                BlockPos p = new BlockPos((int) Math.round(cx + px * o), y, (int) Math.round(cz + pz * o));
                boolean edgeCol = side != null && Math.abs(o) > half;
                out.put(p, edgeCol ? side : top);
                for (int up = 1; up <= 4; up++) out.put(p.up(up), Blocks.AIR.getDefaultState());
                for (int dn = 1; dn <= 3 && world.isAirBlock(p.down(dn)); dn++) out.put(p.down(dn), Blocks.DIRT.getDefaultState());
            }
            if (light != null && lampEvery > 0 && i % lampEvery == 0) {
                for (int s = -1; s <= 1; s += 2) {
                    BlockPos base = new BlockPos((int) Math.round(cx + px * s * (outer + 1)), y, (int) Math.round(cz + pz * s * (outer + 1)));
                    for (int h = 1; h <= 3; h++) out.put(base.up(h), Blocks.COBBLESTONE_WALL.getDefaultState());
                    out.put(base.up(4), light);
                }
            }
        }
        return push(out, "road");
    }

    public static synchronized String building(World w, int x, int z, int width, int depth, int floors, String wall, String window, String roof) {
        world = w;
        IBlockState wl = or(wall, Blocks.BRICK_BLOCK.getDefaultState());
        IBlockState win = or(window, Blocks.GLASS.getDefaultState());
        IBlockState rf = or(roof, Blocks.STONE_SLAB.getDefaultState());
        width = Math.max(4, Math.min(64, width)); depth = Math.max(4, Math.min(64, depth)); floors = Math.max(1, Math.min(30, floors));
        int y0 = ground(x + width / 2, z + depth / 2) + 1, h = floors * 4;
        Map<BlockPos, IBlockState> out = new LinkedHashMap<>();
        for (int i = 0; i < width; i++) for (int k = 0; k < depth; k++) {
            boolean shell = i == 0 || k == 0 || i == width - 1 || k == depth - 1;
            boolean corner = (i == 0 || i == width - 1) && (k == 0 || k == depth - 1);
            for (int dn = 1; dn <= 4 && world.isAirBlock(new BlockPos(x + i, y0 - dn, z + k)); dn++)
                out.put(new BlockPos(x + i, y0 - dn, z + k), wl); // foundation on slopes
            for (int y = 0; y <= h; y++) {
                BlockPos p = new BlockPos(x + i, y0 + y, z + k);
                int fy = y % 4;
                if (y == h) out.put(p, rf);
                else if (fy == 0) out.put(p, wl); // floor of each level
                else if (!shell) out.put(p, Blocks.AIR.getDefaultState());
                else if (!corner && (fy == 1 || fy == 2) && (i + k) % 2 == 1) out.put(p, win);
                else out.put(p, wl);
            }
        }
        int door = width / 2;
        out.put(new BlockPos(x + door, y0 + 1, z), Blocks.AIR.getDefaultState());
        out.put(new BlockPos(x + door, y0 + 2, z), Blocks.AIR.getDefaultState());
        return push(out, "building");
    }

    public static synchronized String city(World w, int cx, int cz, int across, int blockSize, int roadWidth, String roadId, String sidewalk, String lamp, List<String> walls, String window, String roof) {
        across = Math.max(1, Math.min(8, across)); blockSize = Math.max(10, Math.min(48, blockSize)); roadWidth = Math.max(3, Math.min(9, roadWidth));
        int street = roadWidth + 2, size = across * blockSize + (across + 1) * street;
        int x0 = cx - size / 2, z0 = cz - size / 2;
        StringBuilder r = new StringBuilder();
        for (int g = 0; g <= across; g++) {
            int off = g * (blockSize + street) + street / 2;
            r.append(road(w, x0, z0 + off, x0 + size, z0 + off, roadWidth, roadId, sidewalk, lamp, 12)).append("; ");
            r.append(road(w, x0 + off, z0, x0 + off, z0 + size, roadWidth, roadId, sidewalk, lamp, 12)).append("; ");
        }
        Random rnd = new Random(cx * 31L + cz);
        int n = 0;
        for (int a = 0; a < across; a++) for (int b = 0; b < across; b++) {
            int lx = x0 + street + a * (blockSize + street) + 2, lz = z0 + street + b * (blockSize + street) + 2;
            int lot = blockSize - 4, half = lot / 2 - 1;
            for (int q = 0; q < 4; q++) { // four buildings per city block
                String wall = walls == null || walls.isEmpty() ? null : walls.get(n++ % walls.size());
                building(w, lx + (q % 2) * (half + 2), lz + (q / 2) * (half + 2), half, half, 2 + rnd.nextInt(5), wall, window, roof);
            }
        }
        return "city of " + across + "x" + across + " blocks, " + (across * across * 4) + " buildings queued (" + total + " blocks total)";
    }

    private static String push(Map<BlockPos, IBlockState> out, String what) {
        for (Map.Entry<BlockPos, IBlockState> e : out.entrySet()) queue.add(new Object[]{e.getKey(), e.getValue(), group});
        total += out.size();
        return what + " queued, " + out.size() + " blocks";
    }

    /** Everything built after this is undone together by one press of Undo. */
    public static synchronized void newJob() {
        group = new ArrayList<>();
        history.push(group);
        while (history.size() > 20) history.removeLast();
    }

    /** Puts back whatever the last order replaced. */
    @SuppressWarnings("unchecked")
    public static synchronized String undo(World w) {
        queue.removeIf(j -> j[2] == group); // stop the last order if it's still going
        List<Object[]> old = history.poll();
        if (old == null || old.isEmpty()) return "Nothing to undo";
        world = w;
        for (int i = old.size() - 1; i >= 0; i--) queue.add(new Object[]{old.get(i)[0], old.get(i)[1], null});
        total += old.size();
        group = history.isEmpty() ? new ArrayList<>() : history.peek();
        return "Undoing " + old.size() + " blocks";
    }

    public static synchronized void stop() {
        queue.clear();
        total = placed;
    }

    public static boolean busy() {
        return !queue.isEmpty();
    }

    @SuppressWarnings("unchecked")
    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || world == null) return;
        synchronized (Builder.class) {
            for (int i = 0; i < BuddyBuilder.blocksPerTick && !queue.isEmpty(); i++) {
                Object[] j = queue.poll();
                BlockPos pos = (BlockPos) j[0];
                if (j[2] != null) ((List<Object[]>) j[2]).add(new Object[]{pos, world.getBlockState(pos)});
                world.setBlockState(pos, (IBlockState) j[1], 2);
                last = (BlockPos) j[0];
                placed++;
            }
        }
        if (last != null && ++tick % 20 == 0) moveBuddy();
        if (queue.isEmpty()) last = null;
    }

    /** The friend doing the work hops along with the build so you can see her building. */
    private static void moveBuddy() {
        if (worker == null || !(world instanceof net.minecraft.world.WorldServer)) return;
        net.minecraft.entity.Entity e = ((net.minecraft.world.WorldServer) world).getEntityFromUuid(worker);
        if (e instanceof EntityBuddy) {
            ((EntityBuddy) e).hasGoal = false;
            e.setPositionAndUpdate(last.getX() + 0.5, last.getY() + 1, last.getZ() + 0.5);
        }
    }
}
