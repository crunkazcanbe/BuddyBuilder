package com.dogpound.buddybuilder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Keyboard;

/** Ways to open the menu: the J key, a button in the inventory, a button in the pause menu. */
public class ClientHooks {
    private static final int BUTTON_ID = 0x42554459;
    private static final KeyBinding OPEN = new KeyBinding("Open Buddy Builder", Keyboard.KEY_F10, "Buddy Builder"); // J is RealmCoin's map; F10 is free in her pack

    public static void preInit() {
        RenderingRegistry.registerEntityRenderingHandler(EntityBuddy.class, RenderBuddy::new);
    }

    public static void register() {
        ClientRegistry.registerKeyBinding(OPEN);
        MinecraftForge.EVENT_BUS.register(new ClientHooks());
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent e) {
        Minecraft mc = Minecraft.getMinecraft();
        if (e.phase == TickEvent.Phase.END && mc.currentScreen == null && mc.player != null && OPEN.isPressed())
            mc.displayGuiScreen(new GuiBuddy());
    }

    @SubscribeEvent
    public void onGuiInit(GuiScreenEvent.InitGuiEvent.Post e) {
        if (e.getGui() instanceof GuiInventory) {
            GuiContainer g = (GuiContainer) e.getGui();
            // icon-sized and above the hotbar row, so PridePatches' side dock files it with the other buttons (her ask)
            e.getButtonList().add(new GuiBuddy.FancyButton(BUTTON_ID, g.getGuiLeft() - 20, g.getGuiTop() + 100, 18, 18, "B", 0xC2185B));
        } else if (e.getGui() instanceof GuiIngameMenu) {
            e.getButtonList().add(new GuiBuddy.FancyButton(BUTTON_ID, 6, 6, 90, 20, "✦ Buddy Builder", 0xC2185B));
        }
    }

    @SubscribeEvent
    public void onButton(GuiScreenEvent.ActionPerformedEvent.Pre e) {
        GuiButton b = e.getButton();
        if (b.id == BUTTON_ID) {
            Minecraft.getMinecraft().displayGuiScreen(new GuiBuddy());
            e.setCanceled(true);
        }
    }

    /** A small line in the corner while Buddy is thinking or building and the menu is closed. */
    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Text e) {
        if (Brain.running() || Builder.busy())
            e.getLeft().add("§d✦ Buddy: §f" + Brain.status + (Builder.busy() ? " " + Builder.placed + "/" + Builder.total : ""));
    }
}
