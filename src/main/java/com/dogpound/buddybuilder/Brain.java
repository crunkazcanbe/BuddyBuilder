package com.dogpound.buddybuilder;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import net.minecraft.world.WorldServer;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/** Client side: sends the order to the local model and runs the tools it asks for. */
public class Brain {
    public static final List<String> log = new CopyOnWriteArrayList<>();
    /** Who orders go to: null = my own player, otherwise a friend's UUID. */
    public static volatile UUID target;
    public static volatile String status = "Ready";
    private static volatile Thread worker;

    private static final String RULES = "You are playing inside a heavily modded Minecraft 1.12.2 world with the player. "
        + "Do what the player orders by calling tools. For building ALWAYS use find_blocks first and only use block ids it returned; "
        + "prefer good-looking modded blocks (asphalt, concrete, marble, street lamps) over plain vanilla ones. "
        + "Use get_position to know where things are. Make builds pretty: sidewalks, lamps, varied building colours. "
        + "Keep going until the order is fully done, then call say with a short, friendly summary. "
        + "Use remember for anything worth keeping about the player or your plans.";

    private static final String TOOLS = "["
        + tool("find_blocks", "Search every block in the modpack by words, e.g. 'asphalt', 'street lamp', 'white concrete'. Returns block ids.", "{\"query\":{\"type\":\"string\"}}", "[\"query\"]")
        + "," + tool("get_position", "Where the player is standing (x, y, z) and which way they face.", "{}", "[]")
        + "," + tool("build_road", "Lay a road on the ground from (x1,z1) to (x2,z2).",
            "{\"x1\":{\"type\":\"integer\"},\"z1\":{\"type\":\"integer\"},\"x2\":{\"type\":\"integer\"},\"z2\":{\"type\":\"integer\"},\"width\":{\"type\":\"integer\"},"
            + "\"surface\":{\"type\":\"string\",\"description\":\"block id for the road\"},\"edge\":{\"type\":\"string\",\"description\":\"sidewalk block id\"},"
            + "\"lamp\":{\"type\":\"string\",\"description\":\"lamp block id\"},\"lamp_every\":{\"type\":\"integer\"}}",
            "[\"x1\",\"z1\",\"x2\",\"z2\",\"width\",\"surface\"]")
        + "," + tool("build_building", "Build a building with its corner at (x,z).",
            "{\"x\":{\"type\":\"integer\"},\"z\":{\"type\":\"integer\"},\"width\":{\"type\":\"integer\"},\"depth\":{\"type\":\"integer\"},\"floors\":{\"type\":\"integer\"},"
            + "\"wall\":{\"type\":\"string\"},\"window\":{\"type\":\"string\"},\"roof\":{\"type\":\"string\"}}",
            "[\"x\",\"z\",\"width\",\"depth\",\"floors\",\"wall\",\"window\",\"roof\"]")
        + "," + tool("build_city", "Lay out a whole city: a grid of roads with sidewalks and lamps, and buildings on every lot, centred on (x,z).",
            "{\"x\":{\"type\":\"integer\"},\"z\":{\"type\":\"integer\"},\"blocks_across\":{\"type\":\"integer\",\"description\":\"city blocks per side: small=2, medium=3 or 4, huge=6 to 8\"},\"block_size\":{\"type\":\"integer\"},\"road_width\":{\"type\":\"integer\"},"
            + "\"road\":{\"type\":\"string\"},\"sidewalk\":{\"type\":\"string\"},\"lamp\":{\"type\":\"string\"},"
            + "\"building_walls\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}},\"window\":{\"type\":\"string\"},\"roof\":{\"type\":\"string\"}}",
            "[\"x\",\"z\",\"blocks_across\",\"block_size\",\"road_width\",\"road\",\"sidewalk\",\"lamp\",\"building_walls\"]")
        + "," + tool("say", "Tell the player something.", "{\"message\":{\"type\":\"string\"}}", "[\"message\"]")
        + "," + tool("remember", "Save a fact or plan so it is still known after Minecraft restarts.", "{\"fact\":{\"type\":\"string\"}}", "[\"fact\"]")
        + "," + tool("follow_me", "The friend follows the player around.", "{}", "[]")
        + "," + tool("stay_here", "The friend stops and waits where she is.", "{}", "[]")
        + "," + tool("wander", "The friend walks around nearby on her own.", "{}", "[]")
        + "," + tool("go_to", "Walk to (x,z). Moves the friend, or the player's own character when talking to the player.",
            "{\"x\":{\"type\":\"integer\"},\"z\":{\"type\":\"integer\"}}", "[\"x\",\"z\"]")
        + "," + tool("mine", "The player's own character goes and mines a block type (uses Baritone), e.g. 'iron_ore'.", "{\"block\":{\"type\":\"string\"}}", "[\"block\"]")
        + "," + tool("stop_walking", "Stop the player's own character walking or mining.", "{}", "[]")
        + "]";

    private static String tool(String name, String desc, String props, String req) {
        return "{\"type\":\"function\",\"function\":{\"name\":\"" + name + "\",\"description\":\"" + desc
            + "\",\"parameters\":{\"type\":\"object\",\"properties\":" + props + ",\"required\":" + req + "}}}";
    }

    /** Log to the menu and to buddylog.txt (Claude reads that to pick up where the local AI left off). */
    public static void note(String line) {
        log.add(line);
        try (FileWriter w = new FileWriter(new File(folder(), "buddylog.txt"), true)) {
            w.write(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm ").format(new java.util.Date()) + line.replaceAll("\u00a7.", "") + "\n");
        } catch (Exception ignored) { }
    }

    public static File folder() {
        File d = new File(Minecraft.getMinecraft().mcDataDir, "config/buddybuilder");
        d.mkdirs();
        return d;
    }

    /** Claude writes handoff.md: what we were doing and what comes next. */
    static String handoff() {
        try {
            String h = new String(Files.readAllBytes(new File(folder(), "handoff.md").toPath()), StandardCharsets.UTF_8).trim();
            return h.length() > 6000 ? h.substring(h.length() - 6000) : h;
        } catch (Exception e) {
            return "";
        }
    }

    static EntityBuddy friend(World w) {
        UUID u = target;
        if (u == null || !(w instanceof WorldServer)) return null;
        net.minecraft.entity.Entity e = ((WorldServer) w).getEntityFromUuid(u);
        return e instanceof EntityBuddy ? (EntityBuddy) e : null;
    }

    /** The system prompt: rules + who you are + Claude's handoff notes + her memories. */
    private static String system(World w) {
        StringBuilder sb = new StringBuilder(RULES);
        EntityBuddy f = friend(w);
        if (f != null) {
            sb.append("\n\nYou are ").append(f.getName()).append(", the player's companion (a girl). Friendship: ")
              .append(f.friendTitle()).append(" (").append(f.getFriendship()).append(" points). Talk warmly, like a real friend, in her voice. ")
              .append("Build tools make you walk to the site and build it.");
            if (!f.memories.isEmpty()) sb.append("\nThings you remember:\n- ").append(String.join("\n- ", f.memories));
        } else {
            sb.append("\n\nYou are controlling the player's OWN character. go_to and mine move the player; build tools build near them.");
        }
        String h = handoff();
        if (!h.isEmpty()) sb.append("\n\nNOTES LEFT BY CLAUDE (the main AI you are filling in for; continue this work):\n").append(h);
        return sb.toString();
    }

    public static boolean running() {
        return worker != null && worker.isAlive();
    }

    public static void order(String text) {
        if (running() || text.trim().isEmpty()) return;
        note("§b> " + text);
        worker = new Thread(() -> {
            try {
                think(text);
            } catch (Exception e) {
                note("§cProblem: " + e.getMessage());
            } finally {
                status = Builder.busy() ? "Building..." : "Ready";
            }
        }, "BuddyBrain");
        worker.setDaemon(true);
        worker.start();
    }

    public static void stop() {
        Thread w = worker;
        if (w != null) w.interrupt();
        IntegratedServer s = Minecraft.getMinecraft().getIntegratedServer();
        if (s != null) s.addScheduledTask(Builder::stop);
        status = "Stopped";
        note("§eStopped.");
    }

    public static void undo() {
        new Thread(() -> {
            try { note("§e" + onServer(Builder::undo)); } catch (Exception e) { note("§cUndo failed: " + e.getMessage()); }
        }, "BuddyUndo").start();
    }

    public static void buddyHere() {
        EntityPlayer p = Minecraft.getMinecraft().player;
        IntegratedServer s = Minecraft.getMinecraft().getIntegratedServer();
        if (s == null || p == null) return;
        double x = p.posX + 1.5, y = p.posY, z = p.posZ;
        s.addScheduledTask(() -> {
            EntityBuddy f = friend(s.getWorld(p.dimension));
            if (f != null) { f.setMode("follow"); f.setPositionAndUpdate(x, y, z); }
        });
        note("§dOn her way!");
    }

    /** Spawns a new friend next to the player; she is saved with the world from then on. */
    public static void newFriend(String name) {
        EntityPlayer p = Minecraft.getMinecraft().player;
        IntegratedServer s = Minecraft.getMinecraft().getIntegratedServer();
        if (s == null || p == null) { note("§cFriends only work in single-player for now."); return; }
        UUID owner = p.getUniqueID();
        double x = p.posX + p.getLookVec().x * 2, y = p.posY, z = p.posZ + p.getLookVec().z * 2;
        int dim = p.dimension;
        s.addScheduledTask(() -> {
            World w = s.getWorld(dim);
            EntityBuddy f = new EntityBuddy(w);
            f.setCustomNameTag(name);
            f.owner = owner;
            f.setPosition(x, y, z);
            f.remember("I met the player for the first time and they named me " + name + ".");
            w.spawnEntity(f);
            target = f.getUniqueID();
        });
        note("§d" + name + " joined you! Type a username and press Skin to dress her up.");
    }

    public static void setSkin(String skin) {
        String k = skin.toLowerCase(Locale.ROOT).replaceAll("\\.png$", "").replaceAll("[^a-z0-9_]", "");
        onServerAsync(w -> { EntityBuddy f = friend(w); if (f != null) f.setSkin(k); });
        note("§dNew look: " + k + " (downloaded once, then kept forever)");
    }

    public static void setMode(String mode) {
        onServerAsync(w -> { EntityBuddy f = friend(w); if (f != null) f.setMode(mode); });
        note("§d" + (mode.equals("follow") ? "Following you." : "Waiting here."));
    }

    private static void onServerAsync(java.util.function.Consumer<World> job) {
        IntegratedServer s = Minecraft.getMinecraft().getIntegratedServer();
        EntityPlayer p = Minecraft.getMinecraft().player;
        if (s != null && p != null) { int dim = p.dimension; s.addScheduledTask(() -> job.accept(s.getWorld(dim))); }
    }

    private static void think(String order) throws Exception {
        String sys = onServer(w -> {
            Builder.newJob();
            Builder.worker = target;
            EntityBuddy f = friend(w);
            if (f != null) { f.addFriendship(1); f.remember("The player said: " + (order.length() > 150 ? order.substring(0, 150) : order)); }
            return system(w);
        });
        JsonArray msgs = new JsonArray();
        msgs.add(msg("system", sys));
        msgs.add(msg("user", order));
        JsonArray tools = new JsonParser().parse(TOOLS).getAsJsonArray();
        for (int step = 0; step < 16 && !Thread.currentThread().isInterrupted(); step++) {
            shrink(msgs);
            status = "Thinking (" + (BuddyBuilder.useGpu ? "GPU" : "CPU") + ")...";
            JsonObject body = new JsonObject();
            body.addProperty("model", BuddyBuilder.useGpu ? BuddyBuilder.modelGpu : BuddyBuilder.modelCpu);
            body.add("messages", msgs);
            body.add("tools", tools);
            body.addProperty("temperature", 0.3);
            body.addProperty("max_tokens", 1500);
            JsonObject m = post(body).getAsJsonObject().getAsJsonArray("choices").get(0).getAsJsonObject().getAsJsonObject("message");
            JsonObject keep = new JsonObject();
            keep.addProperty("role", "assistant");
            keep.add("content", m.has("content") && !m.get("content").isJsonNull() ? m.get("content") : new JsonParser().parse("\"\""));
            JsonArray calls = m.has("tool_calls") && m.get("tool_calls").isJsonArray() ? m.getAsJsonArray("tool_calls") : new JsonArray();
            if (calls.size() > 0) keep.add("tool_calls", calls);
            msgs.add(keep);
            if (calls.size() == 0) {
                String said = keep.get("content").getAsString().replaceAll("(?s)<think>.*?</think>", "").trim();
                if (!said.isEmpty()) note("§aBuddy: §f" + said);
                return;
            }
            boolean done = false;
            for (JsonElement c : calls) {
                JsonObject f = c.getAsJsonObject().getAsJsonObject("function");
                String name = f.get("name").getAsString();
                JsonObject a;
                try {
                    a = new JsonParser().parse(f.get("arguments").getAsString()).getAsJsonObject();
                } catch (Exception bad) {
                    a = new JsonObject();
                }
                String result = run(name, a);
                if (name.equals("say")) done = true;
                JsonObject t = msg("tool", result);
                t.addProperty("tool_call_id", c.getAsJsonObject().has("id") ? c.getAsJsonObject().get("id").getAsString() : name);
                msgs.add(t);
            }
            if (done) return;
        }
    }

    private static String run(String name, JsonObject a) throws Exception {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer p = mc.player;
        switch (name) {
            case "find_blocks": {
                String q = str(a, "query");
                note("§7searching blocks: " + q);
                return findBlocks(q).toString();
            }
            case "get_position":
                return String.format(Locale.ROOT, "{\"x\":%d,\"y\":%d,\"z\":%d,\"facing\":\"%s\"}",
                    (int) Math.floor(p.posX), (int) Math.floor(p.posY), (int) Math.floor(p.posZ), p.getHorizontalFacing().getName());
            case "say":
                note("§aBuddy: §f" + str(a, "message"));
                return "said";
            case "build_road":
                note("§6road " + str(a, "surface") + " (" + num(a, "x1") + "," + num(a, "z1") + ") to (" + num(a, "x2") + "," + num(a, "z2") + ")");
                return onServer(w -> Builder.road(w, num(a, "x1"), num(a, "z1"), num(a, "x2"), num(a, "z2"), num(a, "width"),
                    str(a, "surface"), str(a, "edge"), str(a, "lamp"), a.has("lamp_every") ? num(a, "lamp_every") : 10));
            case "build_building":
                note("§6building " + num(a, "floors") + " floors of " + str(a, "wall"));
                return onServer(w -> Builder.building(w, num(a, "x"), num(a, "z"), num(a, "width"), num(a, "depth"), num(a, "floors"),
                    str(a, "wall"), str(a, "window"), str(a, "roof")));
            case "build_city": {
                List<String> walls = new ArrayList<>();
                if (a.has("building_walls") && a.get("building_walls").isJsonArray())
                    for (JsonElement e : a.getAsJsonArray("building_walls")) walls.add(e.getAsString());
                note("§6city " + num(a, "blocks_across") + "x" + num(a, "blocks_across") + " with " + str(a, "road") + " roads");
                return onServer(w -> Builder.city(w, num(a, "x"), num(a, "z"), num(a, "blocks_across"), num(a, "block_size"), num(a, "road_width"),
                    str(a, "road"), str(a, "sidewalk"), str(a, "lamp"), walls, str(a, "window"), str(a, "roof")));
            }
            case "remember": {
                String fact = str(a, "fact");
                note("§7(remembered: " + fact + ")");
                return onServer(w -> {
                    EntityBuddy f = friend(w);
                    if (f != null) f.remember(fact);
                    else try (FileWriter fw = new FileWriter(new File(folder(), "handoff.md"), true)) { fw.write("\n- (local AI) " + fact); } catch (Exception ignored) { }
                    return "remembered";
                });
            }
            case "follow_me": case "stay_here": case "wander": {
                String mode = name.equals("follow_me") ? "follow" : name.equals("stay_here") ? "stay" : "wander";
                return onServer(w -> { EntityBuddy f = friend(w); if (f == null) return "only a friend can do that"; f.setMode(mode); return "ok, " + mode; });
            }
            case "go_to": {
                int x = num(a, "x"), z = num(a, "z");
                note("§6walking to " + x + ", " + z);
                if (target == null) { baritone("#goto " + x + " " + z); return "walking there"; }
                return onServer(w -> { EntityBuddy f = friend(w); if (f == null) return "friend not loaded"; f.goTo(x, w.getHeight(x, z), z); return "on my way"; });
            }
            case "mine":
                if (target != null) return "only the player's own character can mine for now";
                note("§6mining " + str(a, "block"));
                baritone("#mine " + str(a, "block"));
                return "mining";
            case "stop_walking":
                baritone("#stop");
                return "stopped";
            default:
                return "no such tool";
        }
    }

    /** Block ids whose id or display name contains every word of the query. */
    static JsonArray findBlocks(String q) {
        String[] words = q.toLowerCase(Locale.ROOT).split("\\W+");
        JsonArray out = new JsonArray();
        for (ResourceLocation rl : Block.REGISTRY.getKeys()) {
            Block b = Block.REGISTRY.getObject(rl);
            String hay = (rl + " " + b.getLocalizedName()).toLowerCase(Locale.ROOT);
            boolean all = true;
            for (String w : words) if (!w.isEmpty() && !hay.contains(w)) { all = false; break; }
            if (all) {
                out.add(rl.toString());
                if (out.size() >= 12) break;
            }
        }
        return out;
    }

    /** Baritone reads chat lines starting with # as commands for the player's own character. */
    private static void baritone(String cmd) {
        Minecraft mc = Minecraft.getMinecraft();
        mc.addScheduledTask(() -> mc.player.sendChatMessage(cmd));
    }

    /** Old tool results (long block lists) get cut short so a big job like a city never overflows the model's memory. */
    private static void shrink(JsonArray msgs) {
        int seen = 0;
        for (int i = msgs.size() - 1; i >= 0; i--) {
            JsonObject m = msgs.get(i).getAsJsonObject();
            if (!"tool".equals(m.get("role").getAsString())) continue;
            if (++seen <= 4) continue;
            String c = m.get("content").getAsString();
            if (c.length() > 160) m.addProperty("content", c.substring(0, 160) + "...]");
        }
    }

    interface Job { String run(World w); }

    /** Builds happen on the integrated server thread; single-player only. */
    private static String onServer(Job job) throws Exception {
        IntegratedServer s = Minecraft.getMinecraft().getIntegratedServer();
        if (s == null) return "error: building only works in single-player";
        World w = s.getWorld(Minecraft.getMinecraft().player.dimension);
        FutureTask<String> t = new FutureTask<>(() -> job.run(w));
        s.addScheduledTask(t);
        String r = t.get(30, TimeUnit.SECONDS);
        status = "Building...";
        return r;
    }

    private static JsonElement post(JsonObject body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(BuddyBuilder.endpoint).openConnection();
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setConnectTimeout(5000);
        c.setReadTimeout(600000);
        c.setRequestProperty("Content-Type", "application/json");
        try (OutputStream o = c.getOutputStream()) {
            o.write(body.toString().getBytes(StandardCharsets.UTF_8));
        }
        if (c.getResponseCode() != 200) throw new Exception("AI server said " + c.getResponseCode());
        try (InputStreamReader r = new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8)) {
            return new JsonParser().parse(r);
        }
    }

    private static JsonObject msg(String role, String content) {
        JsonObject o = new JsonObject();
        o.addProperty("role", role);
        o.addProperty("content", content);
        return o;
    }

    private static String str(JsonObject a, String k) {
        return a.has(k) && !a.get(k).isJsonNull() ? a.get(k).getAsString() : "";
    }

    private static int num(JsonObject a, String k) {
        try { return a.has(k) ? (int) Math.round(a.get(k).getAsDouble()) : 0; } catch (Exception e) { return 0; }
    }
}
