package com.dogpound.buddybuilder;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.common.registry.EntityRegistry;
import net.minecraft.util.ResourceLocation;

@Mod(modid = "buddybuilder", name = "Buddy Builder", version = "1.0.0", acceptableRemoteVersions = "*")
public class BuddyBuilder {
    @Mod.Instance("buddybuilder")
    public static BuddyBuilder instance;
    public static Configuration cfg;
    public static String endpoint, modelCpu, modelGpu;
    public static boolean useGpu;
    public static int blocksPerTick;

    @Mod.EventHandler
    public void pre(FMLPreInitializationEvent e) {
        cfg = new Configuration(e.getSuggestedConfigurationFile());
        endpoint = cfg.getString("endpoint", "ai", "http://127.0.0.1:8096/v1/chat/completions", "OpenAI-style chat URL (a local OpenAI-style server");
        modelCpu = cfg.getString("modelCpu", "ai", "buddy-cpu", "Model name used when the menu is set to CPU");
        modelGpu = cfg.getString("modelGpu", "ai", "buddy-gpu", "Model name used when the menu is set to GPU");
        useGpu = cfg.getBoolean("useGpu", "ai", true, "Use the GPU brain (fast). Set false on PCs without a graphics card to use the CPU brain");
        blocksPerTick = cfg.getInt("blocksPerTick", "build", 200, 1, 5000, "Blocks placed per game tick");
        cfg.save();
        EntityRegistry.registerModEntity(new ResourceLocation("buddybuilder", "buddy"), EntityBuddy.class, "buddybuilder.buddy", 0, this, 80, 3, true);
        if (FMLCommonHandler.instance().getSide() == Side.CLIENT) ClientHooks.preInit();
    }

    public static void saveGpu(boolean gpu) {
        useGpu = gpu;
        cfg.get("ai", "useGpu", false).set(gpu);
        cfg.save();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent e) {
        MinecraftForge.EVENT_BUS.register(new Builder());
        if (FMLCommonHandler.instance().getSide() == Side.CLIENT) ClientHooks.register();
    }
}
