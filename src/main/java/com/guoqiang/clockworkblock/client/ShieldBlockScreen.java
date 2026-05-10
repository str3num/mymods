package com.guoqiang.clockworkblock.client;

import com.guoqiang.clockworkblock.content.ShieldBlockMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class ShieldBlockScreen extends AbstractContainerScreen<ShieldBlockMenu> {

    private static final ResourceLocation BACKGROUND =
        ResourceLocation.withDefaultNamespace("textures/gui/demo_background.png");
    private static final int BG_WIDTH = 176;
    private static final int BG_HEIGHT = 120;

    private ShieldSlider phiSlider;
    private ShieldSlider flowSlider;
    private ShieldSlider rangeSlider;

    private int phi;
    private int flow;
    private int range;

    public ShieldBlockScreen(ShieldBlockMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.phi = menu.getPhi();
        this.flow = menu.getFlow();
        this.range = menu.getRange();
    }

    @Override
    protected void init() {
        this.imageWidth = BG_WIDTH;
        this.imageHeight = BG_HEIGHT;
        super.init();

        int x = getGuiLeft();
        int y = getGuiTop();

        phiSlider = new ShieldSlider(x + 10, y + 25, 156, 20,
            "斥力角度：", 0, 45, 270, phi);

        flowSlider = new ShieldSlider(x + 10, y + 55, 156, 20,
            "斥力流量：", 1, 1, 32, flow);

        rangeSlider = new ShieldSlider(x + 10, y + 85, 156, 20,
            "斥力范围：", 2, 1, 32, range);

        addRenderableWidget(phiSlider);
        addRenderableWidget(flowSlider);
        addRenderableWidget(rangeSlider);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = getGuiLeft();
        int y = getGuiTop();

        graphics.fill(x, y, x + BG_WIDTH, y + BG_HEIGHT, 0xC0101010);
        graphics.fill(x, y, x + BG_WIDTH, y + 16, 0xFF3D3D3D);
        graphics.fill(x, y + BG_HEIGHT - 1, x + BG_WIDTH, y + BG_HEIGHT, 0xFF3D3D3D);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);

        graphics.drawString(font,
            Component.translatable("block.clockworkblock.shield_block"),
            getGuiLeft() + 8, getGuiTop() + 5, 0xFFFFFF, false);

        drawRangeLabels(graphics, phiSlider, 45, 270);
        drawRangeLabels(graphics, flowSlider, 1, 32);
        drawRangeLabels(graphics, rangeSlider, 1, 32);
    }

    private void drawRangeLabels(GuiGraphics graphics, ShieldSlider slider, int min, int max) {
        graphics.drawString(font, Component.literal(String.valueOf(min)),
            slider.getX() + 1, slider.getY() - 9, 0x808080);
        String maxStr = String.valueOf(max);
        graphics.drawString(font, Component.literal(maxStr),
            slider.getX() + slider.getWidth() - font.width(maxStr) - 1,
            slider.getY() - 9, 0x808080);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (menu.getPhi() != phi) {
            phi = menu.getPhi();
            phiSlider.setValue(phi);
        }
        if (menu.getFlow() != flow) {
            flow = menu.getFlow();
            flowSlider.setValue(flow);
        }
        if (menu.getRange() != range) {
            range = menu.getRange();
            rangeSlider.setValue(range);
        }
    }

    private class ShieldSlider extends AbstractSliderButton {
        private final String prefix;
        private final int paramIndex;
        private final int min;
        private final int max;

        ShieldSlider(int x, int y, int width, int height, String prefix,
                     int paramIndex, int min, int max, int current) {
            super(x, y, width, height,
                Component.literal(prefix + current),
                (current - min) / (double)(max - min));
            this.prefix = prefix;
            this.paramIndex = paramIndex;
            this.min = min;
            this.max = max;
        }

        void setValue(int val) {
            value = (val - min) / (double)(max - min);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            int val = (int) Math.round(min + value * (max - min));
            setMessage(Component.literal(prefix + val));
        }

        @Override
        protected void applyValue() {
            int val = (int) Math.round(min + value * (max - min));
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, (paramIndex << 8) | val);
        }
    }
}
