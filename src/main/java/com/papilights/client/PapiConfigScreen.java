package com.papilights.client;

import com.papilights.block.PapiLightBlockEntity;
import com.papilights.menu.PapiConfigMenu;
import com.papilights.network.SetPapiConfigPayload;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Locale;

/**
 * Configuration screen for a PAPI array. Reached by right-clicking any of the four units; it is
 * always bound to the array's leader, so all four open the same settings.
 *
 * <p>Drawn with primitives rather than a background texture, which keeps the mod free of GUI art
 * and lets it sit correctly in both the vanilla and any resource-pack theme.
 */
public class PapiConfigScreen extends AbstractContainerScreen<PapiConfigMenu> {

    private static final int PANEL_WIDTH = 256;
    private static final int PANEL_HEIGHT = 152;

    /** Left column (inputs) and right column (computed cut-off angles). */
    private static final int COL_LEFT = 12;
    private static final int COL_RIGHT = 132;

    private static final int COLOR_PANEL = 0xF0202124;
    private static final int COLOR_BORDER = 0xFF4A4E57;
    private static final int COLOR_LABEL = 0xFFD8D8D8;
    private static final int COLOR_MUTED = 0xFF9A9A9A;
    private static final int COLOR_WHITE_UNIT = 0xFFEDEAE5;
    private static final int COLOR_RED_UNIT = 0xFFB13937;

    private EditBox glideslopeBox;
    private EditBox spreadBox;

    public PapiConfigScreen(PapiConfigMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = PANEL_WIDTH;
        this.imageHeight = PANEL_HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        int x = leftPos + COL_LEFT;
        int y = topPos + 38;

        glideslopeBox = new EditBox(font, x, y, 100, 18,
                Component.translatable("gui.papilights.config.glideslope"));
        glideslopeBox.setMaxLength(8);
        glideslopeBox.setValue(format(menu.getInitialGlideslope()));
        addRenderableWidget(glideslopeBox);

        spreadBox = new EditBox(font, x, y + 36, 100, 18,
                Component.translatable("gui.papilights.config.spread"));
        spreadBox.setMaxLength(8);
        spreadBox.setValue(format(menu.getInitialSpread()));
        addRenderableWidget(spreadBox);

        addRenderableWidget(Button.builder(
                        Component.translatable("gui.papilights.config.reset"),
                        b -> {
                            glideslopeBox.setValue(format(PapiLightBlockEntity.DEFAULT_GLIDESLOPE_DEG));
                            spreadBox.setValue(format(
                                    PapiLightBlockEntity.defaultSpreadFor(menu.getUnitCount())));
                        })
                .bounds(leftPos + COL_LEFT, topPos + PANEL_HEIGHT - 28, 100, 20)
                .build());

        addRenderableWidget(Button.builder(
                        Component.translatable("gui.papilights.config.apply"),
                        b -> applyAndClose())
                .bounds(leftPos + PANEL_WIDTH - COL_LEFT - 100, topPos + PANEL_HEIGHT - 28, 100, 20)
                .build());

        setInitialFocus(glideslopeBox);
    }

    private void applyAndClose() {
        double glideslope = parse(glideslopeBox.getValue(), menu.getInitialGlideslope());
        double spread = parse(spreadBox.getValue(), menu.getInitialSpread());
        PacketDistributor.sendToServer(
                new SetPapiConfigPayload(menu.getLeaderPos(), glideslope, spread));
        onClose();
    }

    private static double parse(String raw, double fallback) {
        try {
            return Double.parseDouble(raw.trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.4f", value)
                .replaceAll("0+$", "")
                .replaceAll("\\.$", "");
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + PANEL_WIDTH, topPos + PANEL_HEIGHT, COLOR_PANEL);
        // Simple 1px frame.
        graphics.fill(leftPos, topPos, leftPos + PANEL_WIDTH, topPos + 1, COLOR_BORDER);
        graphics.fill(leftPos, topPos + PANEL_HEIGHT - 1, leftPos + PANEL_WIDTH, topPos + PANEL_HEIGHT, COLOR_BORDER);
        graphics.fill(leftPos, topPos, leftPos + 1, topPos + PANEL_HEIGHT, COLOR_BORDER);
        graphics.fill(leftPos + PANEL_WIDTH - 1, topPos, leftPos + PANEL_WIDTH, topPos + PANEL_HEIGHT, COLOR_BORDER);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, COL_LEFT, 10, COLOR_LABEL, false);

        graphics.drawString(font, Component.translatable("gui.papilights.config.glideslope"),
                COL_LEFT, 28, COLOR_MUTED, false);
        graphics.drawString(font, Component.translatable("gui.papilights.config.spread"),
                COL_LEFT, 64, COLOR_MUTED, false);

        double glideslope = parse(glideslopeBox.getValue(), menu.getInitialGlideslope());
        double spread = parse(spreadBox.getValue(), menu.getInitialSpread());
        int units = menu.getUnitCount();

        // Per-unit cut-off angles, listed in the order the pilot reads them: index 0 sits at
        // their right, so the steepest unit is printed first.
        graphics.drawString(font, Component.translatable("gui.papilights.config.units"),
                COL_RIGHT, 28, COLOR_MUTED, false);
        for (int i = units - 1; i >= 0; i--) {
            double angle = PapiLightBlockEntity.unitAngleDeg(glideslope, spread, i, units);
            int row = units - 1 - i;
            // On the nominal path the shallow half reads white and the steep half red.
            int color = angle <= glideslope ? COLOR_WHITE_UNIT : COLOR_RED_UNIT;
            graphics.drawString(font,
                    Component.literal(String.format(Locale.ROOT, "%d   %.3f\u00b0", i, angle)),
                    COL_RIGHT, 42 + row * 11, color, false);
        }

        graphics.drawString(font, Component.translatable("gui.papilights.config.hint1"),
                COL_LEFT, 100, COLOR_MUTED, false);
        graphics.drawString(font, Component.translatable("gui.papilights.config.hint2"),
                COL_LEFT, 112, COLOR_MUTED, false);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == InputConstants.KEY_ESCAPE) {
            onClose();
            return true;
        }
        if (keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER) {
            applyAndClose();
            return true;
        }
        if (keyCode == InputConstants.KEY_TAB) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        // While a box has focus, handle the key here rather than falling through to
        // AbstractContainerScreen, which would close the screen the moment you type the
        // inventory key into a number field.
        if (getFocused() instanceof EditBox box && box.canConsumeInput()) {
            box.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

}
