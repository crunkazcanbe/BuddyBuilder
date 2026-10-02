package com.dogpound.buddybuilder;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelPlayer;
import net.minecraft.client.renderer.entity.RenderLivingBase;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Draws a friend with a player body. Skins live in config/buddybuilder/skins/NAME.png:
 * put any PNG there, or type a Minecraft username and it is downloaded ONCE and kept, so it never changes.
 */
public class RenderBuddy extends RenderLivingBase<EntityBuddy> {
    private static final ResourceLocation ALEX = new ResourceLocation("textures/entity/alex.png");
    private static final Map<String, ResourceLocation> loaded = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> slim = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> fetching = new ConcurrentHashMap<>();
    private final ModelPlayer wide = new ModelPlayer(0.0F, false), thin = new ModelPlayer(0.0F, true);

    public RenderBuddy(RenderManager rm) {
        super(rm, new ModelPlayer(0.0F, true), 0.5F);
    }

    public static File dir() {
        File d = new File(Minecraft.getMinecraft().mcDataDir, "config/buddybuilder/skins");
        d.mkdirs();
        return d;
    }

    private static String key(String skin) {
        return skin.toLowerCase().replaceAll("\\.png$", "").replaceAll("[^a-z0-9_]", "");
    }

    @Override
    public void doRender(EntityBuddy e, double x, double y, double z, float yaw, float pt) {
        String k = key(e.getSkin());
        mainModel = slim.getOrDefault(k, true) ? thin : wide;
        super.doRender(e, x, y, z, yaw, pt);
    }

    @Override
    protected ResourceLocation getEntityTexture(EntityBuddy e) {
        String k = key(e.getSkin());
        if (k.isEmpty()) return ALEX;
        ResourceLocation rl = loaded.get(k);
        if (rl != null) return rl;
        File png = new File(dir(), k + ".png");
        if (png.isFile()) {
            try {
                BufferedImage img = ImageIO.read(png);
                slim.put(k, new File(dir(), k + ".slim").isFile());
                rl = Minecraft.getMinecraft().getTextureManager().getDynamicTextureLocation("buddy_" + k, new DynamicTexture(img));
                loaded.put(k, rl);
                return rl;
            } catch (Exception ignored) {
                return ALEX;
            }
        }
        if (fetching.putIfAbsent(k, true) == null) {
            Thread t = new Thread(() -> download(k), "BuddySkin");
            t.setDaemon(true);
            t.start();
        }
        return ALEX;
    }

    /** Username -> Mojang skin, saved to disk so it stays the same forever. */
    private static void download(String name) {
        try {
            JsonObject prof = read("https://api.mojang.com/users/profiles/minecraft/" + name);
            JsonObject ses = read("https://sessionserver.mojang.com/session/minecraft/profile/" + prof.get("id").getAsString());
            String b64 = ses.getAsJsonArray("properties").get(0).getAsJsonObject().get("value").getAsString();
            JsonObject skin = new JsonParser().parse(new String(Base64.getDecoder().decode(b64), StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonObject("textures").getAsJsonObject("SKIN");
            BufferedImage img = ImageIO.read(new URL(skin.get("url").getAsString()));
            boolean isSlim = skin.has("metadata") && "slim".equals(skin.getAsJsonObject("metadata").get("model").getAsString());
            if (isSlim) new File(dir(), name + ".slim").createNewFile();
            ImageIO.write(img, "png", new File(dir(), name + ".png"));
            Brain.note("§7Got the skin for " + name + " and saved it.");
        } catch (Exception e) {
            Brain.note("§cCouldn't find a skin called " + name + " - put " + name + ".png in config/buddybuilder/skins");
        }
    }

    private static JsonObject read(String url) throws Exception {
        try (InputStreamReader r = new InputStreamReader(new URL(url).openStream(), StandardCharsets.UTF_8)) {
            return new JsonParser().parse(r).getAsJsonObject();
        }
    }
}
