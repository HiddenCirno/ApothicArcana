package com.apothinfuser.client.gui;

import com.apothinfuser.config.StatMath;
import com.apothinfuser.config.TableStat;
import com.apothinfuser.network.ArcaneNetwork;
import com.apothinfuser.world.inventory.ArcaneEnchantmentMenu;
import dev.shadowsoffire.apotheosis.ench.table.ApothEnchantScreen;
import dev.shadowsoffire.apotheosis.ench.table.EnchantingStatRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.EnchantmentMenu;

/**
 * 奥术附魔台的界面。
 * <p>
 * <b>本类不画任何东西。</b>背景、三个附魔位、三条属性条、以及神化自带的那套悬停 tooltip
 * 与数值变化的平滑动画，全部原样由 {@code ApothEnchantScreen} 负责，逐像素与神化附魔台一致。
 * 本类只做一件神化没有的事：<b>把对那三条属性条的鼠标操作接管成拖动</b>。
 * <p>
 * <b>为什么不做任何自绘：</b>神化的属性条本身带插值动画——{@code containerTick} 让
 * {@code eterna/quanta/arcana} 三个字段平滑趋近 {@code menu.stats}。我们若另画一个
 * "当前值手柄"，它读的是即时值，而条画的是平滑值，动画期间两者必然错位。
 * 数值反馈交给神化自带的 tooltip 就足够了，不必也不该重复实现。
 */
public class ArcaneEnchantScreen extends ApothEnchantScreen {

    /** 三条属性条在神化 renderBg 里的几何位置（相对界面左上角） */
    private static final int BAR_X = 59;
    private static final int[] BAR_Y = {75, 85, 95};
    private static final int BAR_WIDTH = 110;

    /**
     * 可抓取纵向带宽。
     * <p>
     * 属性条只有 5 像素高，按实际高度判定极难点中。相邻两条的间距是 10 像素，
     * 因此让每条占用一个以自身为中心、高 10 像素的带（位阶 73~82、量子化 83~92、阿卡纳 93~102），
     * 既把可点高度翻倍，又<b>不会让相邻两条的判定区重叠</b>。
     * <p>
     * （曾经给每条上下各放宽 4 像素，总高 13 &gt; 间距 10，导致重叠 3 像素，
     * 在量子化上沿按下会误抓到它上方的位阶。）
     */
    private static final int GRAB_BAND = 10;
    private static final int GRAB_OFFSET = (GRAB_BAND - 5) / 2;

    private static final TableStat[] STATS = {TableStat.ETERNA, TableStat.QUANTA, TableStat.ARCANA};

    /** 正在拖动的那一条；null 表示没有拖动 */
    private TableStat dragging;

    /**
     * 我们自己那份具体类型的菜单引用。
     * <p>
     * 构造参数只能声明成 {@code EnchantmentMenu}：{@code MenuScreens.register} 要求
     * {@code U extends Screen & MenuAccess<M>}，而本类经由 {@code ApothEnchantScreen}
     * 继承的 {@code AbstractContainerScreen<EnchantmentMenu>} 把 {@code M} 钉死成了
     * {@code EnchantmentMenu}（泛型不变），所以注册表那边也只能把 MenuType 放宽到它。
     * 这里再转回具体类型。神化自己也是这么处理的。
     */
    private final ArcaneEnchantmentMenu arcaneMenu;

    public ArcaneEnchantScreen(EnchantmentMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.arcaneMenu = (ArcaneEnchantmentMenu) menu;
    }

    /**
     * 属性条是按"占绝对上限的比例"绘制的（神化的原设计，位阶按 50、量子化/阿卡那按 100），
     * 而不是按书架提供的上限。拖动位置必须与这个比例对齐，否则鼠标位置与条端对不上。
     * <p>
     * 副作用是：书架只提供 10 位阶时，条最多只填到 1/5 处、可拖区间也只在左边一小段。
     * 这是神化既有的视觉语言（留白表示还有成长空间），不做改动。
     */
    private static float absoluteMax(TableStat stat) {
        return stat == TableStat.ETERNA ? EnchantingStatRegistry.getAbsoluteMaxEterna() : 100.0F;
    }

    private int barLeft() {
        return this.leftPos + BAR_X;
    }

    private int bandTop(TableStat stat) {
        return this.topPos + BAR_Y[stat.ordinal()] - GRAB_OFFSET;
    }

    // ------------------------------------------------------------------
    // 拖动
    // ------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            TableStat hit = this.hitBar(mouseX, mouseY);
            if (hit != null) {
                this.dragging = hit;
                this.dragTo(hit, mouseX);
                return true;
            }
        }
        // 交回神化：它会处理三个附魔位与右上角的信息按钮
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        // 必须自己接管：AbstractContainerScreen 的 mouseDragged 是拿去做槽位拖拽的，
        // 不拦住的话拖动属性条会同时拖起一个物品堆
        if (this.dragging != null) {
            this.dragTo(this.dragging, mouseX);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.dragging != null) {
            this.dragging = null;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /** 鼠标位置 → 落在哪一条的抓取带上，没有则 null */
    private TableStat hitBar(double mouseX, double mouseY) {
        double x = mouseX - this.barLeft();
        if (x < 0 || x >= BAR_WIDTH) return null;
        for (TableStat stat : STATS) {
            double y = mouseY - this.bandTop(stat);
            if (y >= 0 && y < GRAB_BAND) return stat;
        }
        return null;
    }

    private void dragTo(TableStat stat, double mouseX) {
        ArcaneEnchantmentMenu menu = this.arcaneMenu;
        float max = menu.getArcane().getMax(stat);
        if (max <= 0.0F) return;

        float ratio = Mth.clamp((float) (mouseX - this.barLeft()) / BAR_WIDTH, 0.0F, 1.0F);
        // 先按绝对上限把鼠标位置还原成数值，再交给 setSelected 夹到书架上限并吸附 0.25
        float value = StatMath.snap(ratio * absoluteMax(stat), max);

        menu.applyLocalSelection(stat, value);
        ArcaneNetwork.sendSetStat(menu.containerId, stat, value);
    }
}
