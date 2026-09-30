package com.apothinfuser.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * 三态精灵按钮：贴图里纵向排开 正常 / 不可用 / 悬浮高亮 三帧，横向位置由构造参数指定。
 * <p>
 * <b>为什么做成真正的按钮控件，而不是在界面里自绘精灵：</b>MC 没有"三角形按钮"这种控件，
 * 但按钮的贴图<b>支持透明通道</b>——把三角画在 7×11 里、四角留透明就是一个按钮。
 * 做成控件之后，点击音效（{@code Button.onPress} 自动调 {@code playDownSound}）、
 * 悬停判定、禁用态、事件分发全部由 {@code AbstractWidget} 负责，不用自己补，
 * 也不会漏（第一版自绘精灵就漏了音效）。
 * <p>
 * <b>三态顺序</b>按本模组贴图的排布：正常 → 不可用 → 悬浮高亮。
 * 这与 MC 原生按钮贴图的顺序<b>不同</b>，所以不能直接用 {@link net.minecraft.client.gui.components.ImageButton}。
 * <p>
 * 本模组两类按钮（列表的等级三角、底部的应用/维修）贴图结构一致——一个 u 配三个 v——
 * 所以共用一个类，只在构造时传不同的取样坐标。
 */
public class SpriteButton extends Button {

    private final ResourceLocation texture;
    private final int spriteWidth;
    private final int spriteHeight;
    private final int u;
    private final int vNormal;
    private final int vDisabled;
    private final int vHover;

    public SpriteButton(int x, int y, int width, int height, ResourceLocation texture,
            int u, int vNormal, int vDisabled, int vHover, OnPress onPress) {
        super(x, y, width, height, Component.empty(), onPress, Button.DEFAULT_NARRATION);
        this.texture = texture;
        this.spriteWidth = width;
        this.spriteHeight = height;
        this.u = u;
        this.vNormal = vNormal;
        this.vDisabled = vDisabled;
        this.vHover = vHover;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 用 isHovered() 而不是 isHoveredOrFocused():点击后按钮会被设为「焦点控件」,
        // 而焦点不像悬停那样随鼠标移开而消失——用后者会导致点过的高亮永久不灭。
        int v = !this.active ? this.vDisabled : (this.isHovered() ? this.vHover : this.vNormal);
        graphics.blit(this.texture, this.getX(), this.getY(), this.u, v, this.spriteWidth, this.spriteHeight);
    }
}
