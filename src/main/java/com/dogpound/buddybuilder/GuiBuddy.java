package com.dogpound.buddybuilder;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.util.ArrayList;
import java.util.List;

/** The Buddy Builder control panel. */
public class GuiBuddy extends GuiScreen {
    private static final String[] STYLES = {"modern", "medieval", "futuristic", "cozy", "fancy", "industrial"};
    private static final String[] SIZES = {"small", "medium", "huge"};
    private static final String[] SPEED_NAMES = {"Slow", "Normal", "Fast", "Insane"};
    private static final int[] SPEEDS = {20, 200, 1000, 5000};
    private static final String[][] PRESETS = {
        {"Road", "build a %LEN% block long road going %FACING% from me in %STYLE% style, with sidewalks and street lamps"},
        {"Avenue", "build a wide %STYLE% avenue %LEN% blocks long going %FACING% from me, with fancy sidewalks and lots of street lamps"},
        {"City", "build a %SIZE% %STYLE% city around me with a road grid, sidewalks, street lamps and colourful buildings, all modded blocks"},
        {"Tower", "build a %SIZE% %STYLE% skyscraper right next to me with nice windows"},
        {"House", "build a %SIZE% %STYLE% house next to me"},
        {"Shops", "build a row of %SIZE% %STYLE% shops along a road next to me, each a different colour"},
    };
    private static int style, size = 1, speed = 1, scroll;
    private static String draft = "";

    private GuiTextField input;
    private PrideFrame f;
    private int logX, logY, logW, logH, presetY, settingsY;
    private FancyButton brainBtn, styleBtn, sizeBtn, speedBtn, talkBtn;

    @Override
    public void initGui() {
        Keyboard.enableRepeatEvents(true);
        f = PrideFrame.fit(width, height);
        buttonList.clear();
        int lx = f.cx, y = f.cy + 10, bw = 52;
        buttonList.add(talkBtn = new FancyButton(10, lx, y, 108, 16, "", 0xC2185B)); y += 18;
        buttonList.add(new FancyButton(11, lx, y, bw, 16, "New friend", 0xAD1457));
        buttonList.add(new FancyButton(12, lx + bw + 4, y, bw, 16, "Skin", 0xAD1457)); y += 18;
        buttonList.add(new FancyButton(13, lx, y, bw, 16, "Follow", 0x6C3483));
        buttonList.add(new FancyButton(14, lx + bw + 4, y, bw, 16, "Stay", 0x6C3483)); y += 18 + 11;
        presetY = y - 2;
        for (int i = 0; i < PRESETS.length; i++)
            buttonList.add(new FancyButton(100 + i, lx + (i % 2) * (bw + 4), y + (i / 2) * 18, bw, 16, PRESETS[i][0], 0x3A7BD5));
        y += 3 * 18 + 11;
        settingsY = y - 9;
        buttonList.add(styleBtn = new FancyButton(1, lx, y, 108, 16, "", 0x8E44AD)); y += 18;
        buttonList.add(sizeBtn = new FancyButton(2, lx, y, 108, 16, "", 0x8E44AD)); y += 18;
        buttonList.add(speedBtn = new FancyButton(3, lx, y, 108, 16, "", 0x16A085)); y += 18;
        buttonList.add(brainBtn = new FancyButton(4, lx, y, 108, 16, "", 0)); y += 18;
        int by = f.cy + f.ch - 36;
        logX = f.cx + 118; logY = f.cy + 10; logW = f.cw - 118; logH = by - 8 - logY;
        input = new GuiTextField(0, fontRenderer, logX + 1, by + 1, logW - 58, 14);
        input.setMaxStringLength(300);
        input.setText(draft);
        input.setFocused(true);
        buttonList.add(new FancyButton(5, logX + logW - 54, by, 54, 16, "Build!", 0x27AE60));
        by += 20;
        int w5 = (logW - 16) / 5;
        buttonList.add(new FancyButton(6, logX, by, w5, 16, "Stop", 0xC0392B));
        buttonList.add(new FancyButton(7, logX + (w5 + 4), by, w5, 16, "Undo", 0xD68910));
        buttonList.add(new FancyButton(8, logX + (w5 + 4) * 2, by, w5, 16, "Come here", 0xC2185B));
        buttonList.add(new FancyButton(15, logX + (w5 + 4) * 3, by, w5, 16, "Claude's plan", 0xD35400));
        buttonList.add(new FancyButton(9, logX + (w5 + 4) * 4, by, w5, 16, "Clear log", 0x5D6D7E));
        refresh();
    }

    private void refresh() {
        styleBtn.displayString = "Style: " + cap(STYLES[style]);
        sizeBtn.displayString = "Size: " + cap(SIZES[size]);
        speedBtn.displayString = "Speed: " + SPEED_NAMES[speed];
        brainBtn.displayString = "Brain: " + (BuddyBuilder.useGpu ? "GPU (fast)" : "CPU (quiet)");
        brainBtn.color = BuddyBuilder.useGpu ? 0xE67E22 : 0x2E86C1;
        EntityBuddy f = selected();
        if (f == null) Brain.target = null;
        talkBtn.displayString = "Talk to: " + (f == null ? "Me" : f.getName());
        BuddyBuilder.blocksPerTick = SPEEDS[speed];
    }

    /** Friends in the loaded world, so the Talk-to button can cycle through them. */
    private List<EntityBuddy> friends() {
        List<EntityBuddy> out = new ArrayList<>();
        for (net.minecraft.entity.Entity e : mc.world.loadedEntityList) if (e instanceof EntityBuddy) out.add((EntityBuddy) e);
        out.sort((a, b) -> Double.compare(a.getDistanceSq(mc.player), b.getDistanceSq(mc.player)));
        return out;
    }

    private EntityBuddy selected() {
        for (EntityBuddy f : friends()) if (f.getUniqueID().equals(Brain.target)) return f;
        return null;
    }

    private static String cap(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    @Override
    protected void actionPerformed(GuiButton b) {
        switch (b.id) {
            case 1: style = (style + 1) % STYLES.length; break;
            case 2: size = (size + 1) % SIZES.length; break;
            case 3: speed = (speed + 1) % SPEEDS.length; break;
            case 4: BuddyBuilder.saveGpu(!BuddyBuilder.useGpu); Brain.log.add("§7Brain switched to " + (BuddyBuilder.useGpu ? "GPU" : "CPU")); break;
            case 5: send(input.getText()); break;
            case 6: Brain.stop(); break;
            case 7: Brain.undo(); break;
            case 8: Brain.buddyHere(); break;
            case 9: Brain.log.clear(); scroll = 0; break;
            case 10: { // cycle Me -> friend 1 -> friend 2 -> ... -> Me
                List<EntityBuddy> fs = friends();
                EntityBuddy cur = selected();
                int i = cur == null ? -1 : fs.indexOf(cur);
                Brain.target = i + 1 < fs.size() ? fs.get(i + 1).getUniqueID() : null;
                break;
            }
            case 11: {
                String name = input.getText().trim().isEmpty() ? "Buddy" : input.getText().trim();
                if (name.length() > 24) name = name.substring(0, 24);
                Brain.newFriend(name);
                input.setText("");
                break;
            }
            case 12:
                if (selected() == null || input.getText().trim().isEmpty())
                    Brain.note("§eType a Minecraft username (or the name of a PNG in config/buddybuilder/skins) in the box, pick a friend with Talk to, then press Skin.");
                else { Brain.setSkin(input.getText().trim()); input.setText(""); }
                break;
            case 13: case 14:
                if (selected() == null) Brain.note("§ePick a friend with Talk to first.");
                else Brain.setMode(b.id == 13 ? "follow" : "stay");
                break;
            case 15:
                send("Read the notes Claude left and carry on with the work exactly where Claude left off. Say what you are doing first.");
                break;
            default:
                if (b.id >= 100) send(PRESETS[b.id - 100][1]
                    .replace("%STYLE%", STYLES[style]).replace("%SIZE%", SIZES[size])
                    .replace("%LEN%", String.valueOf(new int[]{20, 40, 80}[size]))
                    .replace("%FACING%", mc.player.getHorizontalFacing().getName()));
        }
        refresh();
    }

    private void send(String text) {
        if (Brain.running()) { Brain.log.add("§eStill working on the last order - press Stop first."); return; }
        Brain.order(text);
        input.setText("");
        scroll = 0;
    }

    @Override
    protected void keyTyped(char c, int key) throws java.io.IOException {
        if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) { send(input.getText()); return; }
        if (input.textboxKeyTyped(c, key)) return;
        super.keyTyped(c, key);
    }

    @Override
    protected void mouseClicked(int x, int y, int button) throws java.io.IOException {
        super.mouseClicked(x, y, button);
        input.mouseClicked(x, y, button);
    }

    @Override
    public void handleMouseInput() throws java.io.IOException {
        super.handleMouseInput();
        int d = Mouse.getEventDWheel();
        if (d != 0) scroll = Math.max(0, scroll + (d > 0 ? 3 : -3));
    }

    @Override
    public void updateScreen() {
        input.updateCursorCounter();
    }

    @Override
    public void onGuiClosed() {
        draft = input.getText();
        Keyboard.enableRepeatEvents(false);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false; // keep building while the menu is open
    }

    @Override
    public void drawScreen(int mx, int my, float pt) {
        long t = Minecraft.getSystemTime();
        String st = Brain.status + (Builder.busy() ? "  " + Builder.placed + "/" + Builder.total : "");
        f.draw(this, "BUDDY BUILDER", st);
        // status dot, just left of the status text on the title bar
        boolean busy = Brain.running() || Builder.busy();
        int dot = busy ? ((t / 250) % 2 == 0 ? 0xFFFFD700 : 0xFFFF8C00) : 0xFF2ECC71;
        int sw = fontRenderer.getStringWidth(st);
        drawRect(f.x + f.w - 10 - sw - 10, f.y + 12, f.x + f.w - 10 - sw - 4, f.y + 18, dot);
        // left column labels
        EntityBuddy sel = selected();
        drawString(fontRenderer, "§lFRIENDS" + (sel == null ? "" : "  §d❤ §7" + sel.friendTitle()), f.cx, f.cy, PrideFrame.BLUE);
        drawString(fontRenderer, "§lQUICK BUILDS", f.cx, presetY - 9, PrideFrame.BLUE);
        drawString(fontRenderer, "§lSETTINGS", f.cx, settingsY, PrideFrame.BLUE);
        // log panel (scrolls with the mouse wheel)
        drawString(fontRenderer, "§lWHAT BUDDY IS DOING", logX, f.cy, PrideFrame.BLUE);
        PrideFrame.card(logX, logY, logW, logH, PrideFrame.PINK);
        List<String> lines = new ArrayList<>();
        for (String l : Brain.log) lines.addAll(fontRenderer.listFormattedStringToWidth(l, logW - 12));
        if (lines.isEmpty()) lines.add("§8Type an order below or press a Quick Build. Try: \"build a pretty modern city\"");
        int rows = (logH - 4) / 10;
        scroll = Math.min(scroll, Math.max(0, lines.size() - rows));
        int start = Math.max(0, lines.size() - rows - scroll);
        PrideFrame.clip(logX, logY, logW, logH);
        for (int i = 0; i < rows && start + i < lines.size(); i++)
            drawString(fontRenderer, lines.get(start + i), logX + 5, logY + 3 + i * 10, 0xFFFFFF);
        PrideFrame.unclip();
        PrideFrame.scrollbar(logX + logW - 4, logY + 2, logH - 4, start, rows, lines.size());
        // progress bar
        int barY = logY + logH + 2;
        drawRect(logX, barY, logX + logW, barY + 4, 0xFF222222);
        if (Builder.total > 0) {
            int fill = (int) (logW * Math.min(1.0, Builder.placed / (double) Builder.total));
            drawGradientRect(logX, barY, logX + fill, barY + 4, PrideFrame.BLUE, PrideFrame.PINK);
        }
        drawRect(logX, input.y - 1, logX + logW - 58, input.y + 15, PrideFrame.PINK);
        input.drawTextBox();
        super.drawScreen(mx, my, pt);
    }

    /** Flat coloured button that lights up under the mouse. */
    static class FancyButton extends GuiButton {
        int color;

        FancyButton(int id, int x, int y, int w, int h, String text, int color) {
            super(id, x, y, w, h, text);
            this.color = color;
        }

        @Override
        public void drawButton(Minecraft mc, int mx, int my, float pt) {
            if (!visible) return;
            // house style: flat PrideFrame button, keeping each button's own colour (0 = the plain house button)
            hovered = PrideFrame.button(x, y, width, height, displayString, color == 0 ? PrideFrame.BUTTON : 0xFF000000 | color, mx, my);
        }
    }
}
