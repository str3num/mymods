package com.guoqiang.clockworkblock.client;

import com.guoqiang.clockworkblock.content.ShieldBlockMenu;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class ShieldBlockScreen extends AbstractContainerScreen<ShieldBlockMenu> {

    private static final ResourceLocation BACKGROUND =
        ResourceLocation.withDefaultNamespace("textures/gui/demo_background.png");
    private static final int BG_WIDTH = 176;
    private static final int BG_HEIGHT = 100;

    private Button phiMinus;
    private Button phiPlus;
    private Button flowMinus;
    private Button flowPlus;

    private int phi;
    private int flow;

    public ShieldBlockScreen(ShieldBlockMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.phi = menu.getPhi();
        this.flow = menu.getFlow();
    }

    @Override
    protected void init() {
        this.imageWidth = BG_WIDTH;
        this.imageHeight = BG_HEIGHT;
        super.init();

        int x = getGuiLeft();
        int y = getGuiTop();

        phiMinus = Button.builder(Component.literal("-"), btn -> {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 0);
            phi = Math.max(45, phi - 15);
        }).pos(x + 100, y + 30).size(20, 20).build();

        phiPlus = Button.builder(Component.literal("+"), btn -> {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 1);
            phi = Math.min(145, phi + 15);
        }).pos(x + 150, y + 30).size(20, 20).build();

        flowMinus = Button.builder(Component.literal("-"), btn -> {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 2);
            flow = Math.max(1, flow - 2);
        }).pos(x + 100, y + 60).size(20, 20).build();

        flowPlus = Button.builder(Component.literal("+"), btn -> {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 3);
            flow = Math.min(32, flow + 2);
        }).pos(x + 150, y + 60).size(20, 20).build();

        addRenderableWidget(phiMinus);
        addRenderableWidget(phiPlus);
        addRenderableWidget(flowMinus);
        addRenderableWidget(flowPlus);
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

        int x = getGuiLeft();
        int y = getGuiTop();

        Component title = Component.translatable("block.clockworkblock.shield_block");
        graphics.drawString(font, title, x + 8, y + 5, 0xFFFFFF, false);

        Component phiLabel = Component.translatable("message.clockworkblock.shield_phi", phi);
        graphics.drawString(font, phiLabel, x + 8, y + 34, 0xA0A0A0, false);

        Component flowLabel = Component.translatable("message.clockworkblock.shield_flow", flow);
        graphics.drawString(font, flowLabel, x + 8, y + 64, 0xA0A0A0, false);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (menu.getPhi() != phi) {
            phi = menu.getPhi();
        }
        if (menu.getFlow() != flow) {
            flow = menu.getFlow();
        }
    }
}
