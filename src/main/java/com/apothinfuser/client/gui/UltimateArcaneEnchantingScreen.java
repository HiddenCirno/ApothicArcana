package com.apothinfuser.client.gui;

import com.apothinfuser.ApothInfuser;
import com.apothinfuser.config.StatMath;
import com.apothinfuser.config.TableStat;
import com.apothinfuser.config.UltimateInfuserConfig;
import com.apothinfuser.network.UltimateInfuserNetwork;
import com.apothinfuser.world.inventory.MutableSlotPos;
import dev.shadowsoffire.apotheosis.ench.Ench;
import dev.shadowsoffire.apotheosis.ench.table.ApothEnchantmentMenu;
import dev.shadowsoffire.apotheosis.ench.table.EnchantingStatRegistry;
import dev.shadowsoffire.apotheosis.util.ApothMiscUtil;
import dev.shadowsoffire.apotheosis.util.DrawsOnLeft;
import dev.shadowsoffire.placebo.util.EnchantmentUtils;
import fuzs.enchantinginfuser.EnchantingInfuser;
import fuzs.enchantinginfuser.api.EnchantingInfuserAPI;
import fuzs.enchantinginfuser.client.gui.screens.inventory.InfuserScreen;
import fuzs.enchantinginfuser.network.client.C2SAddEnchantLevelMessage;
import fuzs.enchantinginfuser.world.inventory.InfuserMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.EnchantmentNames;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 终极奥术附魔台的界面。
 * <p>
 * <b>本类不继承灌注台的 {@code InfuserScreen}。</b>那个界面的元素布局几乎全部写死在 private 成员里
 * （列表的 {@code ScrollingList}/{@code EnchantmentListEntry} 是 private 内部类、按钮坐标是
 * private static final、主贴图 {@code INFUSER_LOCATION} 也是 private static final 且指向灌注台自己的
 * All Rights Reserved 贴图）。我们要做的是全版式重排，与其用十几处 {@code @ModifyArgs}/
 * {@code @Overwrite} 把它拆光，不如自己实现。
 * <p>
 * 而且<b>服务端逻辑一行都不用碰</b>：状态、校验、成本计算全在 public 的 {@code InfuserMenu} 上，
 * 本类只负责表现与交互。
 * <p>
 * 界面尺寸 <b>199 × 196</b>，主贴图与全部控件精灵都在同一张 256×256 上
 * （面板占 {@code (0,0)-(198,195)}，其余区域是各类精灵）。
 */
public class UltimateArcaneEnchantingScreen extends AbstractContainerScreen<InfuserMenu> implements DrawsOnLeft {

    /** 主贴图；面板与所有精灵同图 */
    public static final ResourceLocation TEXTURE =
            ResourceLocation.tryBuild(ApothInfuser.MOD_ID, "textures/gui/ultimate_arcane_enchanting_table.png");

    // ------------------------------------------------------------------
    // 版式常量 —— 全部来自设计图的实测坐标（相对界面左上角）
    // ------------------------------------------------------------------

    private static final int SLOT_X = 9;
    private static final int SLOT_Y = 15;
    private static final int INV_X = 8;
    private static final int INV_Y = 112;
    private static final int HOTBAR_X = 8;
    private static final int HOTBAR_Y = 170;
    private static final int EQUIP_X = 174;
    private static final int EQUIP_Y = 94;
    private static final int SHIELD_X = 174;
    private static final int SHIELD_Y = 170;
    private static final int CELL = 18;

    /**
     * 槽位内缩量。
     * <p>
     * 设计图上标的是 18×18 的"格"（含边框），而 MC 的 {@code Slot.x/y} 指的是格内
     * <b>16×16 物品区</b>的左上角——四周各 1 像素的边框不算在内。
     * 所以设计坐标要往右下各偏 1 像素，物品才会落在格的正中。
     * <p>
     * 这个 1 是有意义的偏移，不是笔误，改之前先看这段注释。
     */
    private static final int SLOT_INSET = 1;

    // ---- 三条属性条（既是显示，也是滑块本体）----

    private static final TableStat[] STATS = {TableStat.ETERNA, TableStat.QUANTA, TableStat.ARCANA};
    private static final int BAR_X = 41;
    private static final int[] BAR_Y = {85, 94, 103};
    private static final int BAR_W = 120;
    private static final int BAR_H = 5;
    private static final int FILL_U = 120;
    private static final int[] FILL_V = {196, 201, 206};

    /**
     * 属性名标签的横坐标：紧贴左侧那颗彩色菱形图标（贴图里占 x=7~13）的右边一像素。
     * <p>
     * 右缘不写死，见 {@link #LABEL_MAX_W}。
     */
    private static final int LABEL_X = 14;

    /**
     * 标签可用的最大宽度：从 {@link #LABEL_X} 到属性条起点，27 像素。
     * <p>
     * <b>27 这个数是中文量出来的</b>——恰好三个全角字（「量子化」「阿卡那」占满，「位阶」18）。
     * 而英文的 {@code Eterna} 约 34 像素，硬画会直接压在属性条上。所以标签不是按固定字号画的，
     * 而是每帧量一次三条里最宽的那条，超宽就<b>整体等比缩小</b>到刚好装下（见
     * {@link #renderStatLabels}）。效果是中文维持原大小、英文自动缩一档，任何语言都不会出界。
     * <p>
     * 之所以不写死一个"小字号"对所有语言生效：那会让中文也跟着变小，而中文本来放得下。
     */
    private static final int LABEL_MAX_W = BAR_X - LABEL_X;

    /**
     * 属性条的视觉中心相对 {@code BAR_Y} 的偏移。
     * <p>
     * 贴图里菱形图标占 84~90、属性条占 85~89，两者中心都是 {@code BAR_Y + 2}（= 87）。
     * 标签按这个中心做垂直居中，与图标和条对齐。
     */
    private static final int BAR_CENTER_OFFSET = 2;

    /**
     * 三条标签的颜色，逐字抄自神化 {@code ApothEnchantScreen.renderLabels}：
     * {@code 0x3DB53D} / {@code 0xFC5454} / {@code 0xA800A8}。
     * <p>
     * 刻意不用 {@code ChatFormatting} 那三档：原版调色板的绿/红/暗紫都比这里暗一档，
     * 而界面底色是浅灰（{@code #C6C6C6}），暗色压不住。神化选的这三个亮一档的色值
     * 与游戏内其它地方显示这三个属性名时的颜色一致。
     */
    private static final int[] LABEL_COLORS = {0x3DB53D, 0xFC5454, 0xA800A8};

    /**
     * 拖动判定带的高度。
     * <p>
     * 属性条只有 5 像素高，按实际高度判定极难点中。相邻两条间距是 9 像素，
     * 因此让每条占用一个高 9 像素、向上偏 2 像素的带（83~91、92~100、101~109），
     * 既把可点高度翻倍，又<b>严格不与相邻条重叠</b>。
     */
    private static final int GRAB_BAND = 9;
    private static final int GRAB_OFFSET = 2;

    // ---- 附魔列表 ----

    private static final int LIST_X = 41;
    private static final int LIST_Y = 5;
    private static final int LIST_W = 120;
    private static final int ROW_H = 19;
    private static final int VISIBLE_ROWS = 4;

    /** 条目三态精灵的 v 坐标，依次为 正常 / 不可用 / 选中高亮 */
    private static final int ENTRY_V_NORMAL = 196;
    private static final int ENTRY_V_UNUSABLE = 215;
    private static final int ENTRY_V_ACTIVE = 234;

    /** 条目名在行内的垂直偏移（行高 19，字高 9，留上下各 5） */
    private static final int ENTRY_TEXT_Y = 6;

    /** tooltip 换行宽度，与灌注台取同一个值 */
    private static final int TOOLTIP_WIDTH = 175;

    /** 未解锁条目的名字颜色（与灌注台一致：偏灰的暗色） */
    private static final int COLOR_UNUSABLE = 6839882;

    // ---- 等级三角按钮 ----

    private static final int TRI_LEFT_X = 30;
    private static final int TRI_RIGHT_X = 165;
    private static final int TRI_ROW_0_Y = 9;

    /** 三角精灵：左列尖端朝左（降级），右列尖端朝右（升级）；三行自上而下 正常/不可用/悬浮高亮 */
    private static final int TRI_U_POINT_LEFT = 202;
    private static final int TRI_U_POINT_RIGHT = 213;
    private static final int TRI_V_NORMAL = 72;
    private static final int TRI_V_DISABLED = 87;
    private static final int TRI_V_HOVER = 102;

    // ---- 底部两个动作按钮 ----

    private static final int ACTION_SIZE = 19;
    private static final int APPLY_X = 8;
    private static final int APPLY_Y = 43;
    private static final int REPAIR_X = 8;
    private static final int REPAIR_Y = 62;

    /** 按钮精灵：左列 = 应用附魔，右列 = 维修物品；三行为 正常 / 不可用 / 悬浮高亮 */
    private static final int ACTION_U_APPLY = 199;
    private static final int ACTION_U_REPAIR = 218;
    private static final int ACTION_V_NORMAL = 0;
    private static final int ACTION_V_DISABLED = 19;
    private static final int ACTION_V_HOVER = 38;

    // ---- 滚动条 ----

    private static final int SCROLL_X = 176;
    private static final int SCROLL_Y = 5;
    private static final int SCROLL_W = 14;
    private static final int SCROLL_H = 76;

    /** 滚轮精灵：12×15，两态。注意这两个 u 的值是按实际贴图内容定的（见下方注释） */
    private static final int WHEEL_W = 12;
    private static final int WHEEL_H = 15;
    /** u=211 的那张才是"选中"态的画法；设计图里的颜色标注与最终贴图内容相反 */
    private static final int WHEEL_U_SELECTED = 211;
    private static final int WHEEL_U_IDLE = 199;
    private static final int WHEEL_V = 57;

    /** 滚轮行程：轨道高 − 滚轮高 − 上下各 1 像素内边距 */
    private static final int WHEEL_TRAVEL = SCROLL_H - WHEEL_H - 2;

    // ------------------------------------------------------------------
    // 状态
    // ------------------------------------------------------------------

    private TableStat dragging;
    private boolean scrolling;

    /** 列表快照。菜单里的 map 一变就整体重建，避免条目与菜单出现两套等级 */
    private List<EnchantmentEntry> entries = List.of();
    private Map<Enchantment, Integer> lastEnchantments = Map.of();

    /** 上次构建列表时的位阶；与魔咒表一起构成"要不要重建"的判据 */
    private int lastPower = Integer.MIN_VALUE;

    /** 第一条可见条目的下标 */
    private int scrollPosition;

    /**
     * 滚轮的<b>连续</b>位置，0~1。
     * <p>
     * 与 {@code scrollPosition}（整数行号）分开保存：滚轮按它平滑移动，列表按它取整吸附。
     * 若从 {@code scrollPosition} 反推滚轮位置，条目少时会退化成只有两三个落脚点。
     */
    private float scrollOffs;

    /** 每个可见行一对三角按钮；下标 = 行号 */
    private final SpriteButton[] decrButtons = new SpriteButton[VISIBLE_ROWS];
    private final SpriteButton[] incrButtons = new SpriteButton[VISIBLE_ROWS];

    private SpriteButton applyButton;
    private SpriteButton repairButton;

    /** 未解锁条目要显示乱码名，需要一个稳定的种子 */
    private final int enchantmentSeed = new Random().nextInt();

    public UltimateArcaneEnchantingScreen(InfuserMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 199;
        this.imageHeight = 196;
    }

    /** 本界面唯一的配置；不是我们的台子时返回 null，所有交互都会因此短路 */
    private UltimateInfuserConfig config() {
        return this.menu.config instanceof UltimateInfuserConfig config ? config : null;
    }

    /**
     * 属性条按"占<b>绝对上限</b>的比例"绘制（位阶 50、量子化 100、阿卡那 100），与神化附魔台一致。
     * <p>
     * 用绝对上限而不是书架上限，是为了让留白表达"还有成长空间"；副作用是书架不满配时条永远填不满，
     * 玩家拖到顶会看到条还有一截——这时靠 tooltip 里的具体数值说明上限在哪。
     */
    private static float absoluteMax(TableStat stat) {
        return stat == TableStat.ETERNA ? EnchantingStatRegistry.getAbsoluteMaxEterna() : 100.0F;
    }

    // ------------------------------------------------------------------
    // 生命周期
    // ------------------------------------------------------------------

    @Override
    protected void init() {
        super.init();
        this.layoutSlots();
        this.createTriangleButtons();
        this.createActionButtons();
        this.refreshEntries();
    }

    @Override
    public void containerTick() {
        super.containerTick();
        this.refreshEntries();
        this.updateTriangleButtons();
        this.updateActionButtons();
    }

    /**
     * 每个可见行造一对三角按钮。
     * <p>
     * 按钮位置固定在行上，它"指向哪条魔咒"由 {@code scrollPosition} 决定——
     * 所以回调里只捕获<b>行号</b>，实际条目在点击时现查（{@code onTriangle}）。
     * 若在这里就把条目捕获进去，滚动之后按钮会指向上一条。
     */
    private void createTriangleButtons() {
        for (int row = 0; row < VISIBLE_ROWS; row++) {
            final int r = row;
            int y = this.topPos + TRI_ROW_0_Y + row * ROW_H;
            this.decrButtons[row] = this.addRenderableWidget(new SpriteButton(
                    this.leftPos + TRI_LEFT_X, y, 7, 11, TEXTURE, TRI_U_POINT_LEFT,
                    TRI_V_NORMAL, TRI_V_DISABLED, TRI_V_HOVER, button -> this.onTriangle(r, false)));
            this.incrButtons[row] = this.addRenderableWidget(new SpriteButton(
                    this.leftPos + TRI_RIGHT_X, y, 7, 11, TEXTURE, TRI_U_POINT_RIGHT,
                    TRI_V_NORMAL, TRI_V_DISABLED, TRI_V_HOVER, button -> this.onTriangle(r, true)));
        }
    }

    /**
     * 底部两个动作按钮。
     * <p>
     * 点击走的是菜单的按钮协议：{@code clickMenuButton} 先在本地做一次校验并返回是否成立，
     * 成立才发 {@code handleInventoryButtonClick} 让服务端执行——与灌注台完全一致。
     * 灌注台那边点完还会清空搜索框，我们没有搜索框，所以省掉。
     */
    private void createActionButtons() {
        this.applyButton = this.addRenderableWidget(new SpriteButton(
                this.leftPos + APPLY_X, this.topPos + APPLY_Y, ACTION_SIZE, ACTION_SIZE, TEXTURE,
                ACTION_U_APPLY, ACTION_V_NORMAL, ACTION_V_DISABLED, ACTION_V_HOVER,
                button -> this.clickMenuButton(0)));
        this.repairButton = this.addRenderableWidget(new SpriteButton(
                this.leftPos + REPAIR_X, this.topPos + REPAIR_Y, ACTION_SIZE, ACTION_SIZE, TEXTURE,
                ACTION_U_REPAIR, ACTION_V_NORMAL, ACTION_V_DISABLED, ACTION_V_HOVER,
                button -> this.clickMenuButton(1)));
    }

    private void clickMenuButton(int buttonId) {
        if (this.menu.clickMenuButton(this.minecraft.player, buttonId)) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, buttonId);
        }
    }

    /**
     * 把 {@code InfuserMenu} 造出来的 42 个槽位摆到本界面设计的坐标上。
     * <p>
     * <b>按容器身份识别，而不是按索引。</b>槽位在菜单里的顺序取决于 {@code InfuserMenu}
     * 构造器的书写顺序，一旦灌注台调整顺序，任何硬编码索引都会静默错位。改为判断
     * "这个槽位挂在玩家物品栏上吗"，再按 {@code getContainerSlot()} 的<b>语义</b>分类——
     * 那几个数字（0-8 快捷栏、9-35 背包、36-39 护甲、40 副手）是原版 {@code Inventory} 的固定约定。
     * <p>
     * 装备栏"自上而下"的映射：原版护甲索引是 36=鞋、37=腿、38=胸、39=头，
     * 所以第 k 行（y 向下递增）对应索引 {@code 39 - k}。
     */
    private void layoutSlots() {
        Inventory playerInventory = this.minecraft.player.getInventory();
        for (Slot slot : this.menu.slots) {
            if (slot.container != playerInventory) {
                // 唯一的非玩家槽位就是附魔槽（灌注台不使用青金石槽）
                place(slot, SLOT_X, SLOT_Y);
                continue;
            }
            int index = slot.getContainerSlot();
            if (index >= 36 && index <= 39) {
                place(slot, EQUIP_X, EQUIP_Y + CELL * (39 - index));
            } else if (index == 40) {
                place(slot, SHIELD_X, SHIELD_Y);
            } else if (index < 9) {
                place(slot, HOTBAR_X + CELL * index, HOTBAR_Y);
            } else {
                int i = index - 9;
                place(slot, INV_X + CELL * (i % 9), INV_Y + CELL * (i / 9));
            }
        }
    }

    /**
     * 按<b>设计图坐标</b>摆放槽位，内部统一补上 {@link #SLOT_INSET}。
     * <p>
     * 所有槽位都经过这里，就不会出现"某几个槽忘了偏 1 像素"的散落式错误。
     */
    private static void place(Slot slot, int cellX, int cellY) {
        MutableSlotPos.move(slot, cellX + SLOT_INSET, cellY + SLOT_INSET);
    }

    // ------------------------------------------------------------------
    // 列表快照
    // ------------------------------------------------------------------

    /**
     * 菜单里的可用魔咒表一变，就整体重建列表。
     * <p>
     * 为什么要快照而不是每帧现算：冲突判定是 <b>O(n²)</b> 的（每条都要和所有已选中项比一遍
     * {@code isCompatibleWith}），四五十条魔咒每帧跑一遍上万次调用，会直接拖垮帧率。
     * <p>
     * 用 {@code equals} 比对 map 来判定"变了没有"——魔咒等级变化、物品换掉、位阶改动
     * 都会让这张表不同，因此这一个判据就够。
     */
    private void refreshEntries() {
        Map<Enchantment, Integer> current = this.validEnchantmentsOrNull();
        if (current == null) return;

        // 位阶也必须纳入变更判据：条目的 maxLevel（决定"未解锁/乱码名"）由菜单的
        // getCurrentPower() 驱动，而位阶变化时物品的可用魔咒表<b>不会变</b>——
        // 只看那张表就会漏掉"位阶调了但列表没跟着刷新"。
        int power = this.menu.getCurrentPower();
        if (current.equals(this.lastEnchantments) && power == this.lastPower) return;
        this.lastEnchantments = new LinkedHashMap<>(current);
        this.lastPower = power;

        List<EnchantmentEntry> fresh = new ArrayList<>(current.size());
        current.forEach((enchantment, level) -> {
            var maxLevelResult = this.menu.getMaxLevel(enchantment);
            fresh.add(new EnchantmentEntry(enchantment, level,
                    maxLevelResult.getSecond(), maxLevelResult.getFirst().orElse(-1), Set.of()));
        });

        // 与灌注台相同的排序：先按稀有度，再按显示名
        var provider = EnchantingInfuserAPI.getEnchantStatsProvider();
        fresh.sort(Comparator
                .comparingInt((EnchantmentEntry e) -> provider.getRarity(e.enchantment).ordinal())
                .thenComparing(e -> Component.translatable(e.enchantment.getDescriptionId()).getString()));

        // 冲突标注：只在"至少一方已选中"时才算，所以先挑出已选中的那些
        List<EnchantmentEntry> active = fresh.stream().filter(EnchantmentEntry::isActive).toList();
        List<EnchantmentEntry> marked = new ArrayList<>(fresh.size());
        for (EnchantmentEntry entry : fresh) {
            Set<Enchantment> conflicts = entry.isActive() ? Set.of()
                    : active.stream().filter(entry::isIncompatibleWith)
                            .map(e -> e.enchantment).collect(Collectors.toSet());
            marked.add(new EnchantmentEntry(entry.enchantment, entry.level,
                    entry.maxLevel, entry.requiredPower, conflicts));
        }

        this.entries = List.copyOf(marked);
        this.scrollTo(this.scrollOffs);
        this.updateTriangleButtons();
    }

    private int maxScroll() {
        return Math.max(0, this.entries.size() - VISIBLE_ROWS);
    }

    /**
     * 取菜单当前的可用魔咒表；<b>还没同步到就返回 null</b>。
     * <p>
     * <b>为什么必须挡这一层：</b>{@code InfuserMenu.enchantments} 在客户端是个尚未初始化的字段——
     * 要等服务端发来 {@code S2CCompatibleEnchantsMessage} 才被赋值，而界面是在
     * {@code ClientboundOpenScreenPacket} 里创建的，<b>早于</b>那个同步包。
     * 此时 {@code getValidEnchantments()} 内部会 {@code ImmutableMap.copyOf(null)} 抛 NPE。
     * <p>
     * 灌注台自己的界面从不在此刻碰这张表，所以它绕开了这个问题；我们靠轮询刷新
     * （因为拿不到它的同步回调），就必须自己处理这个中间态。而它的 API 没有暴露
     * "同步好了吗"这个信息，异常是唯一可用的信号——所以这里刻意只包住那一次调用，
     * 不让 try 块扩大到可能掩盖真正 bug 的范围。
     */
    private Map<Enchantment, Integer> validEnchantmentsOrNull() {
        try {
            return this.menu.getValidEnchantments();
        } catch (NullPointerException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------
    // 渲染
    // ------------------------------------------------------------------

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // 槽位底纹、按钮底、属性条底槽等静态元素全都画在这张面板贴图里，所以先贴一次整图
        graphics.blit(TEXTURE, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight);
        this.renderStatFills(graphics);
        this.renderEntries(graphics, mouseX, mouseY);
        this.renderScrollWheel(graphics);
    }

    /** 刷新两个动作按钮的可用性。灌注台的判据就这两句，逻辑全在菜单里 */
    private void updateActionButtons() {
        if (this.applyButton != null) {
            this.applyButton.active = this.menu.canEnchant(this.minecraft.player);
        }
        if (this.repairButton != null) {
            this.repairButton.active = this.menu.canRepair(this.minecraft.player);
        }
    }

    /**
     * 按钮左上角的经验成本数字。
     * <p>
     * 颜色即状态：可执行绿、不可执行红、负数黄（负数意味着这是"返还经验"而非消耗，
     * 灌注台的列表里把魔咒降到 0 级就会走到这条分支）。
     * <p>
     * 用 {@code drawString(..., true)} 带阴影，压在按钮贴图上才看得清。
     */
    private void renderActionButtonCosts(GuiGraphics graphics) {
        if (this.applyButton == null || this.repairButton == null) return;

        int enchantCost = this.menu.getEnchantCost();
        boolean canEnchant = this.menu.canEnchant(this.minecraft.player);
        if (canEnchant || enchantCost != 0) {
            int color = enchantCost < 0 ? ChatFormatting.YELLOW.getColor()
                    : (canEnchant ? ChatFormatting.GREEN.getColor() : ChatFormatting.RED.getColor());
            // 负数（降级退款）只画一个「+」，不画数额：大额退款有五位数的经验点，
            // 而这颗按钮只有 19px 宽，写上去必然溢出。具体多少由浮出的经验球体现。
            this.renderCostText(graphics, this.applyButton,
                    enchantCost < 0 ? "+" : String.valueOf(enchantCost), color);
        }

        int repairCost = this.menu.getRepairCost();
        if (repairCost != 0) {
            boolean canRepair = this.menu.canRepair(this.minecraft.player);
            this.renderCostText(graphics, this.repairButton, String.valueOf(repairCost),
                    canRepair ? ChatFormatting.GREEN.getColor() : ChatFormatting.RED.getColor());
        }
    }

    /**
     * 在按钮内绘制一个数字，照抄灌注台的 {@code renderReadableText}。
     * <p>
     * 两个细节都与直觉不同：
     * <ul>
     * <li><b>右对齐而非左上角</b>：{@code posX += 宽度 − 1 − 文字宽}，且垂直居中。
     * 灌注台那句 {@code 19 - 2 - font.width(text)} 是针对它 18 宽的按钮，换算过来
     * 就是"宽度 − 1 − 文字宽"。</li>
     * <li><b>四向描边而非阴影</b>：先上下左右各画一次黑色，再画彩色。按钮贴图颜色杂乱，
     * 只有一圈描边才压得住。</li>
     * </ul>
     */
    private void renderCostText(GuiGraphics graphics, SpriteButton button, String text, int color) {
        int x = button.getX() + button.getWidth() - 1 - this.font.width(text);
        int y = button.getY() + button.getHeight() / 2;
        graphics.drawString(this.font, text, x - 1, y, 0, false);
        graphics.drawString(this.font, text, x + 1, y, 0, false);
        graphics.drawString(this.font, text, x, y - 1, 0, false);
        graphics.drawString(this.font, text, x, y + 1, 0, false);
        graphics.drawString(this.font, text, x, y, color, false);
    }

    /** 三条属性条的前景：按 当前值/绝对上限 的比例，从精灵里裁切对应宽度叠上去 */
    private void renderStatFills(GuiGraphics graphics) {
        UltimateInfuserConfig config = this.config();
        if (config == null) return;

        for (int i = 0; i < STATS.length; i++) {
            int width = Math.round(selected(config, STATS[i]) / absoluteMax(STATS[i]) * BAR_W);
            if (width <= 0) continue;
            graphics.blit(TEXTURE, this.leftPos + BAR_X, this.topPos + BAR_Y[i],
                    FILL_U, FILL_V[i], Math.min(width, BAR_W), BAR_H);
        }
    }

    private void renderEntries(GuiGraphics graphics, int mouseX, int mouseY) {
        for (int row = 0; row < VISIBLE_ROWS; row++) {
            int index = this.scrollPosition + row;
            if (index >= this.entries.size()) break;
            EnchantmentEntry entry = this.entries.get(index);

            int x = this.leftPos + LIST_X;
            int y = this.topPos + LIST_Y + row * ROW_H;

            graphics.blit(TEXTURE, x, y, 0, entrySpriteV(entry), LIST_W, ROW_H);

            // 只有"真正未知"和"冲突"才变灰。位阶不足但物品上已有的魔咒照常显示——
            // 玩家得能看清自己附出了什么，否则调低位阶就等于把自己的成果变成一串乱码。
            int color = entry.isUnknown() || entry.isIncompatible() ? COLOR_UNUSABLE : 0xFFFFFF;
            graphics.drawCenteredString(this.font, this.entryName(entry), x + LIST_W / 2,
                    y + ENTRY_TEXT_Y, color);

            // 鼠标在条目本体上（不含两侧三角）时给出 tooltip
            if (mouseX >= x && mouseX < x + LIST_W && mouseY >= y && mouseY < y + ROW_H) {
                this.setEntryTooltip(entry);
            }
        }
    }

    /**
     * 条目 tooltip，三种情形互斥，与灌注台一致：
     * <ol>
     * <li><b>未解锁</b>——告诉玩家当前位阶与所需位阶，而不是干巴巴一句"够不着"</li>
     * <li><b>冲突</b>——列出与之冲突的已选中魔咒名字</li>
     * <li><b>正常</b>——魔咒名 + 等级区间 + 描述</li>
     * </ol>
     * 文案全部复用灌注台自己的语言键（含那两个 public static 常量），因此与它的界面逐字相同。
     */
    private void setEntryTooltip(EnchantmentEntry entry) {
        if (entry.isUnknown() || entry.isLocked()) {
            List<FormattedCharSequence> list = new ArrayList<>();
            list.add(Component.translatable(InfuserScreen.CURRENT_ENCHANTING_POWER_TRANSLATION_KEY,
                    InfuserScreen.ENCHANTING_POWER_COMPONENT,
                    Component.literal(String.valueOf(this.menu.getCurrentPower())).withStyle(ChatFormatting.RED),
                    Component.literal(String.valueOf(entry.requiredPower))).getVisualOrderText());
            // 两种情况要说的话不一样：
            //   未知（物品上还没有）→ "你需要更多的书架才能附魔"
            //   已锁（物品上已经有）→ "修改这个附魔的等级需要一个更多书架的附魔台"
            // 后者用灌注台自己的 lowPower2。那句文案正是为这个场景写的——它自己的列表
            // 会把这类条目整个过滤掉，所以那句键在它那儿是死代码，在我们这儿才真正用上。
            list.addAll(this.font.split(Component.translatable(entry.isLocked()
                    ? "gui.enchantinginfuser.tooltip.lowPower2"
                    : "gui.enchantinginfuser.tooltip.unknown_enchantment")
                    .withStyle(ChatFormatting.GRAY), TOOLTIP_WIDTH));
            this.setTooltipForNextRenderPass(list);
            return;
        }

        if (entry.isIncompatible()) {
            // 映射成 MutableComponent 而不是 Component：append 只存在于前者
            Component names = entry.incompatible.stream()
                    .map(e -> (MutableComponent) Component.translatable(e.getDescriptionId()))
                    .reduce((a, b) -> a.append(", ").append(b))
                    .orElse(Component.empty())
                    .withStyle(ChatFormatting.GRAY);
            this.setTooltipForNextRenderPass(this.font.split(
                    Component.translatable("gui.enchantinginfuser.tooltip.incompatible", names), TOOLTIP_WIDTH));
            return;
        }

        List<FormattedCharSequence> list = new ArrayList<>();
        String descId = entry.enchantment.getDescriptionId();
        if (Language.getInstance().has(descId + ".desc")) {
            list.addAll(this.font.split(
                    Component.translatable(descId + ".desc").withStyle(ChatFormatting.GRAY), TOOLTIP_WIDTH));
        } else if (Language.getInstance().has(descId + ".description")) {
            list.addAll(this.font.split(
                    Component.translatable(descId + ".description").withStyle(ChatFormatting.GRAY), TOOLTIP_WIDTH));
        }

        var provider = EnchantingInfuserAPI.getEnchantStatsProvider();
        MutableComponent levels = Component.translatable("enchantment.level." + provider.getMinLevel(entry.enchantment));
        if (provider.getMinLevel(entry.enchantment) != provider.getMaxLevel(entry.enchantment)) {
            levels.append("-").append(Component.translatable("enchantment.level." + provider.getMaxLevel(entry.enchantment)));
        }
        Component wrapped = Component.literal("(").append(levels).append(")").withStyle(ChatFormatting.GRAY);
        list.add(0, Component.translatable(descId).append(" ").append(wrapped).getVisualOrderText());
        this.setTooltipForNextRenderPass(list);
    }

    /**
     * 条目底纹：置灰优先于选中，与灌注台的 {@code getYImage()} 判定同构。
     * <p>
     * 但"位阶不足"要拆开看：{@link EnchantmentEntry#isUnknown()}（物品上还没有）置灰，
     * {@link EnchantmentEntry#isLocked()}（物品上已有）走正常的高亮——它确实是"已选中"的，
     * 只因为当前位阶够不到而改不动，那件事交给 tooltip 说。用 {@code isUnusable()}
     * 一刀切的话，玩家调低位阶就会看到自己所有的魔咒一起变灰，像是被清空了一样。
     */
    private static int entrySpriteV(EnchantmentEntry entry) {
        if (entry.isUnknown() || entry.isIncompatible()) return ENTRY_V_UNUSABLE;
        return entry.isActive() ? ENTRY_V_ACTIVE : ENTRY_V_NORMAL;
    }

    /**
     * 条目名。
     * <p>
     * 只有<b>物品上还没有、且当前位阶够不着</b>的魔咒才显示随机乱码名——用标准银河字母
     * 拼出的假词，让玩家知道"有这么个魔咒存在，但你现在够不着"，而不是干脆藏起来。
     * <p>
     * 物品上<b>已经有</b>的魔咒即使位阶不足也照常显示真名与等级（见
     * {@link EnchantmentEntry#isLocked()}）——那是玩家的成果，不该因为往下拨了滑块
     * 就变成认不出来的乱码。
     */
    private FormattedCharSequence entryName(EnchantmentEntry entry) {
        int maxWidth = (int) (LIST_W * 0.72F);
        if (entry.isUnknown()) {
            EnchantmentNames names = EnchantmentNames.getInstance();
            names.initSeed(this.enchantmentSeed + BuiltInRegistries.ENCHANTMENT.getId(entry.enchantment));
            FormattedText random = names.getRandomName(this.font, maxWidth);
            List<FormattedCharSequence> split = this.font.split(random, maxWidth);
            if (!split.isEmpty()) return split.get(0);
        }
        MutableComponent name = Component.translatable(entry.enchantment.getDescriptionId());
        if (entry.isActive()) {
            name.append(" ").append(Component.translatable("enchantment.level." + entry.level));
        }
        return name.getVisualOrderText();
    }

    /** 滚轮按连续的 {@code scrollOffs} 定位，因此条目少时也能平滑移动 */
    private void renderScrollWheel(GuiGraphics graphics) {
        if (this.maxScroll() <= 0) return;
        int x = this.leftPos + SCROLL_X + 1;
        int y = this.topPos + SCROLL_Y + 1 + Math.round(this.scrollOffs * WHEEL_TRAVEL);
        int u = this.scrolling ? WHEEL_U_SELECTED : WHEEL_U_IDLE;
        graphics.blit(TEXTURE, x, y, u, WHEEL_V, WHEEL_W, WHEEL_H);
    }

    /**
     * 画三条属性名，并<b>刻意丢掉</b>标题与"物品栏"字样。
     * <p>
     * 丢字是因为默认实现会往 (8,6) 和 (8, imageHeight-94) 写字，那两个位置在新版式里
     * 分别是附魔槽和背包区，会糊在一起。
     * <p>
     * 属性名则是从神化附魔台搬过来的：那边在 {@code renderLabels} 里写三条固定坐标的文字，
     * 我们重做版式时只搬了属性条本身、把标签漏了，于是三条彩色条没有任何文字说明。
     * 文字取自 {@link TableStat#translationKey()}——与三条属性条的 tooltip 共用同一批键，
     * 保证同一个维度在界面上只有一种叫法。
     * <p>
     * 这里是<b>界面相对坐标</b>：{@code renderLabels} 是在 {@code pose().translate(leftPos, topPos)}
     * 之内被调用的（可以对照 {@code AbstractContainerScreen.render} 的字节码，槽位循环与它之间
     * 夹着那次 translate），所以不用再自己加 leftPos/topPos——神化源码里那三个裸坐标也是同样道理。
     * 反过来说 {@code renderBg} 是在 translate <b>之前</b>调的，那边就必须自己加。
     * <p>
     * 具体画法（含宽度超限时等比缩小）见 {@link #renderStatLabels}。
     */
    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        this.renderStatLabels(graphics);
    }

    /**
     * 三条属性名，宽度自适应地画在菱形图标与属性条之间。
     * <p>
     * <b>为什么要缩放：</b>那一段只有 {@link #LABEL_MAX_W} 像素宽，是为中文量身量的
     * （「量子化」正好占满 27）。英文的 {@code Eterna} / {@code Quanta} / {@code Arcana}
     * 都是六个字母、约 34 像素，硬画会盖在属性条上。<b>改右对齐也救不了</b>——
     * 那会让中文的三字标签向左顶进菱形图标里。
     * <p>
     * 所以先量出三条里最宽的一条，超过可用宽度就整体等比缩到刚好装下：
     * 中文量出来正好 27，缩放系数为 1（字号完全不变）；英文约 34，缩到 0.79 一档。
     * <b>是"按语言整体缩"而不是"逐条缩"</b>——三条同一字号，否则中文界面里三个字大小不一，
     * 比出界更难看。
     * <p>
     * 每帧重算而不是缓存：语言可以在游戏里随时切换，缓存就要额外挂一个失效钩子，
     * 而三个短字符串排版的开销远小于维护那个钩子的心智成本（缓存失效是本项目最容易出的 bug 之一）。
     */
    private void renderStatLabels(GuiGraphics graphics) {
        Component[] labels = new Component[STATS.length];
        int widest = 0;
        for (int i = 0; i < STATS.length; i++) {
            labels[i] = Component.translatable(STATS[i].translationKey());
            widest = Math.max(widest, this.font.width(labels[i]));
        }
        if (widest == 0) return;
        float scale = widest > LABEL_MAX_W ? (float) LABEL_MAX_W / widest : 1.0F;

        for (int i = 0; i < STATS.length; i++) {
            // 缩放是以原点为中心的，所以先把原点挪到目标位置，画完再弹栈。
            // 纵向要跟着缩放走：字格高 lineHeight，中心固定在 BAR_Y + 2，字号变了中心不能跟着跑。
            int y = Math.round(BAR_Y[i] + BAR_CENTER_OFFSET - this.font.lineHeight * scale / 2.0F);
            graphics.pose().pushPose();
            graphics.pose().translate(LABEL_X, y, 0.0F);
            graphics.pose().scale(scale, scale, 1.0F);
            graphics.drawString(this.font, labels[i], 0, 0, LABEL_COLORS[i], false);
            graphics.pose().popPose();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        // 槽位物品的 tooltip 必须<b>自己调</b>：AbstractContainerScreen.render 并不负责它
        // （逐条核过字节码，那个方法里从头到尾没有 renderTooltip 调用，结尾是
        // popPose → enableDepthTest → return）。它只算出 hoveredSlot，画不画由子类决定——
        // 原版的 ContainerScreen / EnchantmentScreen 也都是在自己重写的 render 里显式调一次。
        // 漏掉这一步的症状就是：背包里的物品悬停上去什么都不显示，且不报任何错。
        // 位置放在 super.render 之后（hoveredSlot 那时才被算出来）、我们自己的 tooltip 之前，
        // 这样属性条与动作按钮的提示会盖在物品 tooltip 上层，不会被压住。
        this.renderTooltip(graphics, mouseX, mouseY);

        // 成本数字必须画在 super.render <b>之后</b>：按钮精灵是在那里绘制的，
        // 若放进 renderBg（更早）会被按钮整个盖住——数字存在但看不见。
        // 灌注台也是在 super.render 之后才调它的 renderEnchantButtonCost。
        this.renderActionButtonCosts(graphics);
        this.renderStatTooltips(graphics, mouseX, mouseY);
        this.renderActionButtonTooltips(graphics, mouseX, mouseY);
    }

    /**
     * 两个动作按钮的悬停提示。
     * <p>
     * <b>刻意不用 {@code button.isHovered()}：</b>那个方法会先判 {@code active}，
     * 按钮不可用时恒为 false——而"为什么不可用"恰恰是最需要提示的时候。
     * 灌注台源码里也留了同样的注释，这里按坐标自己判。
     */
    private void renderActionButtonTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        if (this.applyButton == null || this.repairButton == null) return;

        int enchantCost = this.menu.getEnchantCost();
        boolean canEnchant = this.menu.canEnchant(this.minecraft.player);
        if ((canEnchant || enchantCost != 0) && this.isOver(this.applyButton, mouseX, mouseY)) {
            List<Component> list = new ArrayList<>();
            if (canEnchant) {
                list.add(this.menu.getEnchantableStack().getHoverName().copy());
                list.add(Component.empty());
            }
            if (enchantCost < 0) {
                // 降级退款只留这一句，刻意不写换算行：退款额走 getExpCostForSlot(-cost, 0)
                // （见 InfuserMenuMixin#apothinfuser$refundWithApothCurve），大额退款是五位数，
                // 而这一栏其余条目的语境是"等级数"，混在一起只会误导。
                list.add(Component.translatable("gui.enchantinginfuser.tooltip.points")
                        .withStyle(ChatFormatting.YELLOW));
            } else if (enchantCost == 1) {
                list.add(Component.translatable("container.enchant.level.one").withStyle(ChatFormatting.GREEN));
            } else {
                list.add(Component.translatable("container.enchant.level.many", enchantCost)
                        .withStyle(ChatFormatting.GREEN));
                list.add(this.xpCostLine(enchantCost));
            }
            graphics.renderComponentTooltip(this.font, list, mouseX, mouseY);
            return;
        }

        int repairCost = this.menu.getRepairCost();
        if (repairCost != 0 && this.isOver(this.repairButton, mouseX, mouseY)) {
            ItemStack stack = this.menu.getEnchantableStack();
            List<Component> list = new ArrayList<>();
            list.add(stack.getHoverName().copy().withStyle(stack.getRarity().color));
            Component change = Component.translatable("gui.enchantinginfuser.tooltip.change",
                    stack.getMaxDamage() - stack.getDamageValue(), stack.getMaxDamage());
            list.add(Component.translatable("gui.enchantinginfuser.tooltip.durability", change)
                    .withStyle(ChatFormatting.YELLOW));
            list.add(Component.empty());
            list.add((repairCost == 1
                    ? Component.translatable("container.enchant.level.one")
                    : Component.translatable("container.enchant.level.many", repairCost))
                    .withStyle(ChatFormatting.GRAY));
            list.add(this.xpCostLine(repairCost));
            graphics.renderComponentTooltip(this.font, list, mouseX, mouseY);
        }
    }

    /**
     * 「实际消耗 X 点经验（约合 Y 级）」。
     * <p>
     * 必须显示，因为扣费模型与按钮上的等级数字<b>不是一回事</b>：按钮写"3 级"，
     * 实际扣的是 {@code getExperienceForLevel(3) - 1} 点经验——在低等级时两者接近，
     * 高等级时差得远。不写清玩家会以为系统吞了经验。
     * <p>
     * 文案与换算都走神化自己的键与工具（{@code info.apotheosis.xp_cost} /
     * {@code ApothMiscUtil} / {@code EnchantmentUtils}），所以数字与它的附魔台一致。
     */
    private Component xpCostLine(int levelCost) {
        int points = ApothMiscUtil.getExpCostForSlot(levelCost, 0);
        return Component.translatable("info.apotheosis.xp_cost",
                Component.literal(String.valueOf(points)).withStyle(ChatFormatting.GREEN),
                Component.literal(String.valueOf(EnchantmentUtils.getLevelForExperience(points)))
                        .withStyle(ChatFormatting.GREEN));
    }

    private boolean isOver(SpriteButton button, double mouseX, double mouseY) {
        return mouseX >= button.getX() && mouseX < button.getX() + button.getWidth()
                && mouseY >= button.getY() && mouseY < button.getY() + button.getHeight();
    }

    /**
     * 三维属性条的 tooltip + 左侧信息面板。
     * <p>
     * 逐条照搬神化附魔台的实现。左侧那两块面板（量子化的增益/削减、阿卡那的稀有度权重表）
     * 直接调用它的 {@link DrawsOnLeft#drawOnLeft} —— 本类 {@code implements DrawsOnLeft}，
     * 所以拿到的是神化自己的绘制代码，不是我重写的仿制品。
     * <p>
     * 文案全部走神化的语言键，因此与它的附魔台逐字一致。
     */
    private void renderStatTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        UltimateInfuserConfig config = this.config();
        if (config == null) return;
        TableStat hovered = this.hitBar(mouseX, mouseY);
        if (hovered == null) return;

        switch (hovered) {
            case ETERNA -> this.renderEternaTooltip(graphics, mouseX, mouseY, config);
            case QUANTA -> this.renderQuantaTooltip(graphics, mouseX, mouseY, config);
            case ARCANA -> this.renderArcanaTooltip(graphics, mouseX, mouseY, config);
        }
    }

    private void renderEternaTooltip(GuiGraphics graphics, int mouseX, int mouseY, UltimateInfuserConfig config) {
        List<Component> list = new ArrayList<>();
        list.add(Component.literal(eterna() + I18n.get("gui.apotheosis.enchant.eterna.desc")));
        list.add(Component.translatable("gui.apotheosis.enchant.eterna.desc2").withStyle(ChatFormatting.GRAY));
        float value = config.getSelectedEterna();
        if (value > 0.0F) {
            list.add(Component.literal(""));
            list.add(Component.literal(I18n.get("gui.apotheosis.enchant.eterna.desc3", f(value),
                    EnchantingStatRegistry.getAbsoluteMaxEterna())).withStyle(ChatFormatting.GRAY));
        }
        graphics.renderComponentTooltip(this.font, list, mouseX, mouseY);
    }

    private void renderQuantaTooltip(GuiGraphics graphics, int mouseX, int mouseY, UltimateInfuserConfig config) {
        float quanta = config.getSelectedQuanta();
        float rectification = config.getMaxRectification();

        List<Component> list = new ArrayList<>();
        list.add(Component.literal(quanta() + I18n.get("gui.apotheosis.enchant.quanta.desc")));
        list.add(Component.translatable("gui.apotheosis.enchant.quanta.desc2").withStyle(ChatFormatting.GRAY));
        list.add(Component.literal(rectification() + I18n.get("gui.apotheosis.enchant.quanta.desc3"))
                .withStyle(ChatFormatting.GRAY));
        if (quanta > 0.0F) {
            list.add(Component.literal(""));
            list.add(Component.literal(I18n.get("gui.apotheosis.enchant.quanta.desc4", f(quanta)))
                    .withStyle(ChatFormatting.GRAY));
            list.add(Component.literal(I18n.get("info.apotheosis.gui_rectification", f(rectification)))
                    .withStyle(ChatFormatting.YELLOW));
        }
        graphics.renderComponentTooltip(this.font, list, mouseX, mouseY);

        // 左侧面板：量子化带来的威力波动，被校准抵消多少
        if (quanta > 0.0F) {
            list.clear();
            list.add(Component.translatable("info.apotheosis.quanta_buff")
                    .withStyle(ChatFormatting.UNDERLINE, ChatFormatting.RED));
            list.add(Component.translatable("info.apotheosis.quanta_reduc",
                    f(-quanta + quanta * rectification / 100.0F)).withStyle(ChatFormatting.DARK_RED));
            list.add(Component.translatable("info.apotheosis.quanta_growth", f(quanta))
                    .withStyle(ChatFormatting.BLUE));
            this.drawOnLeft(graphics, list, this.getGuiTop() + 29);
        }
    }

    private void renderArcanaTooltip(GuiGraphics graphics, int mouseX, int mouseY, UltimateInfuserConfig config) {
        float arcana = config.getSelectedArcana();

        List<Component> list = new ArrayList<>();
        list.add(Component.literal(arcana() + I18n.get("gui.apotheosis.enchant.arcana.desc")));
        list.add(Component.translatable("gui.apotheosis.enchant.arcana.desc2").withStyle(ChatFormatting.GRAY));
        list.add(Component.translatable("gui.apotheosis.enchant.arcana.desc3").withStyle(ChatFormatting.GRAY));
        if (arcana > 0.0F) {
            // 物品自身的附魔能力会贡献一半的阿卡那，所以要把它从总量里刨出来展示
            float itemBonus = this.menu.getSlot(0).getItem().getEnchantmentValue() / 2.0F;
            list.add(Component.literal(""));
            list.add(Component.literal(I18n.get("gui.apotheosis.enchant.arcana.desc4", f(arcana - itemBonus)))
                    .withStyle(ChatFormatting.GRAY));
            list.add(Component.translatable("info.apotheosis.ench_bonus", f(itemBonus))
                    .withStyle(ChatFormatting.YELLOW));
            list.add(Component.literal(I18n.get("gui.apotheosis.enchant.arcana.desc5", f(arcana)))
                    .withStyle(ChatFormatting.GOLD));
        }
        graphics.renderComponentTooltip(this.font, list, mouseX, mouseY);

        // 左侧两块面板：本档位的加成说明 + 各稀有度的实际权重
        if (arcana > 0.0F) {
            ApothEnchantmentMenu.Arcana tier = ApothEnchantmentMenu.Arcana.getForThreshold(arcana);
            list.clear();
            list.add(Component.translatable("info.apotheosis.arcana_bonus")
                    .withStyle(ChatFormatting.UNDERLINE, ChatFormatting.DARK_PURPLE));
            if (tier != ApothEnchantmentMenu.Arcana.EMPTY) {
                list.add(Component.translatable("info.apotheosis.weights_changed").withStyle(ChatFormatting.BLUE));
            }
            int minEnchants = arcana > 75.0F ? 3 : arcana > 25.0F ? 2 : 0;
            if (minEnchants > 0) {
                list.add(Component.translatable("info.apotheosis.min_enchants", minEnchants)
                        .withStyle(ChatFormatting.BLUE));
            }
            this.drawOnLeft(graphics, list, this.getGuiTop() + 29);

            int offset = 20 + list.size() * this.font.lineHeight;
            int[] rarities = tier.getRarities();
            list.clear();
            list.add(Component.translatable("info.apotheosis.rel_weights")
                    .withStyle(ChatFormatting.UNDERLINE, ChatFormatting.YELLOW));
            list.add(Component.translatable("info.apotheosis.weight",
                    I18n.get("rarity.enchantment.common"), rarities[0]).withStyle(ChatFormatting.GRAY));
            list.add(Component.translatable("info.apotheosis.weight",
                    I18n.get("rarity.enchantment.uncommon"), rarities[1]).withStyle(ChatFormatting.GREEN));
            list.add(Component.translatable("info.apotheosis.weight",
                    I18n.get("rarity.enchantment.rare"), rarities[2]).withStyle(ChatFormatting.BLUE));
            list.add(Component.translatable("info.apotheosis.weight",
                    I18n.get("rarity.enchantment.very_rare"), rarities[3]).withStyle(ChatFormatting.GOLD));
            this.drawOnLeft(graphics, list, this.getGuiTop() + 29 + offset);
        }
    }

    // ---- 神化那几个 private static 格式化助手的等价实现 ----
    // 它们只有一两行，且是 private，调不到，只能照原样重写。

    private static String eterna() {
        return ChatFormatting.GREEN + I18n.get("gui.apotheosis.enchant.eterna") + ChatFormatting.RESET;
    }

    private static String quanta() {
        return ChatFormatting.RED + I18n.get("gui.apotheosis.enchant.quanta") + ChatFormatting.RESET;
    }

    private static String arcana() {
        return ChatFormatting.DARK_PURPLE + I18n.get("gui.apotheosis.enchant.arcana") + ChatFormatting.RESET;
    }

    private static String rectification() {
        return ChatFormatting.YELLOW + I18n.get("gui.apotheosis.enchant.rectification") + ChatFormatting.RESET;
    }

    /**
     * 两位小数。
     * <p>
     * <b>比神化原版多一个 {@code Locale.ROOT}。</b>它的实现是 {@code String.format("%.2f", v)}，
     * 没指定区域——在德语、法语这类用逗号作小数点的语言下会显示成 {@code 10,00}，
     * 与 Minecraft 自身的数字格式不一致。这里固定用 ROOT，保证永远是 {@code 10.00}。
     */
    private static String f(float value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    // ------------------------------------------------------------------
    // 鼠标交互
    // ------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (this.config() != null) {
                TableStat bar = this.hitBar(mouseX, mouseY);
                if (bar != null) {
                    this.dragging = bar;
                    this.dragTo(bar, mouseX);
                    return true;
                }
            }
            if (this.insideScrollbar(mouseX, mouseY)) {
                // 与灌注台一致：按下本身不滚动，一旦拖动才跳到鼠标位置
                this.scrolling = true;
                return true;
            }
        }
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
        if (this.scrolling) {
            this.scrollToMouse(mouseY);
            this.updateTriangleButtons();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.dragging != null || this.scrolling) {
            this.dragging = null;
            this.scrolling = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /**
     * 鼠标滚轮：一格 = 轨道的 1/(总数 − 可见数)，与灌注台一致。
     * <p>
     * 走 {@code scrollOffs} 而不是直接加减行号：让滚轮与拖动共用同一个位置来源，
     * 否则两条路径交替使用时会互相打架（滚完再拖，位置会跳）。
     */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int scroll = this.maxScroll();
        if (scroll <= 0) return super.mouseScrolled(mouseX, mouseY, delta);
        this.scrollTo(this.scrollOffs - (float) delta / scroll);
        this.updateTriangleButtons();
        return true;
    }

    private boolean insideScrollbar(double mouseX, double mouseY) {
        return mouseX >= this.leftPos + SCROLL_X && mouseX < this.leftPos + SCROLL_X + SCROLL_W
                && mouseY >= this.topPos + SCROLL_Y && mouseY < this.topPos + SCROLL_Y + SCROLL_H;
    }

    /**
     * 把 0~1 的滚轮位置换算成"第一条可见的条目下标"。
     * <p>
     * <b>为什么保留 float 的 {@code scrollOffs}，而不是从 {@code scrollPosition} 反推滚轮位置：</b>
     * 条目少的时候 {@code maxScroll} 很小（例如总共 5 条、可见 4 条 → maxScroll=1），
     * 从整数反推的话滚轮只有 2 个落脚点，拖起来一跳一跳。保留浮点位置让滚轮<b>连续移动</b>，
     * 列表按 {@code round} 吸附到整行——这正是灌注台的做法。
     */
    private void scrollTo(float offset) {
        this.scrollOffs = Mth.clamp(offset, 0.0F, 1.0F);
        int scroll = this.maxScroll();
        this.scrollPosition = scroll <= 0 ? 0 : Math.round(scroll * this.scrollOffs);
    }

    /**
     * 拖动滚动条：鼠标位置 → 0~1 的滚轮位置。
     * <p>
     * 扣掉上下各 1 像素内边距，再减去滚轮自身高度的一半——那样"把鼠标压在滚轮中心"时
     * 拖出来的位置正好等于它原位，手感才对得上。
     */
    private void scrollToMouse(double mouseY) {
        float travel = SCROLL_H - WHEEL_H - 2;
        float offset = (float) (mouseY - (this.topPos + SCROLL_Y + 1) - WHEEL_H / 2.0F) / travel;
        this.scrollTo(offset);
    }

    /** 第 {@code row} 行当前显示的是哪一条；超出范围返回 null */
    private EnchantmentEntry entryAt(int row) {
        int index = this.scrollPosition + row;
        return index < this.entries.size() ? this.entries.get(index) : null;
    }

    /** 点了第 {@code row} 行的某个三角 */
    private void onTriangle(int row, boolean increase) {
        EnchantmentEntry entry = this.entryAt(row);
        if (entry != null) {
            this.clickLevel(entry, increase);
        }
    }

    /**
     * 按当前显示的内容刷新 8 个三角按钮的显隐与可用性。
     * <p>
     * 显隐规则照搬灌注台：降级三角在等级为 0 时隐藏（没得降），升级三角在达到该魔咒的
     * <b>自身上限</b>时隐藏（升不动）。而"不可用"态对应更严的 <b>本物品上限</b>——
     * 位阶不够时按钮仍在，但按了无效。
     * <p>
     * 滚动或列表内容变化后都必须调用，否则按钮会停留在上一批条目的状态上。
     */
    private void updateTriangleButtons() {
        for (int row = 0; row < VISIBLE_ROWS; row++) {
            EnchantmentEntry entry = this.entryAt(row);
            SpriteButton decr = this.decrButtons[row];
            SpriteButton incr = this.incrButtons[row];

            // 灌注条目的"等级"是被锁死的（菜单侧 clickEnchantmentLevelButton 对它直接返回当前值），
            // 两个三角按了都没反应。这里必须<b>显式</b>摘掉，不能指望上面那两条通用规则：
            // 它 level 恒为 1，通用规则会算出「降级三角可见且可用」——于是界面上挂着一颗
            // 看起来能按、按下去什么都没发生的按钮。升级三角只是碰巧因为上限为 1 而被隐藏，
            // 依赖的是 provider 的返回值，同样不该赌。
            boolean levelLocked = entry != null && entry.enchantment == Ench.Enchantments.INFUSION.get();

            decr.visible = entry != null && entry.level > 0 && !levelLocked;
            decr.active = entry != null && entry.level - 1 < entry.maxLevel;

            incr.visible = entry != null && entry.level < entry.absoluteMaxLevel() && !levelLocked;
            incr.active = entry != null && entry.level < entry.maxLevel;
        }
    }

    /**
     * 调整某条魔咒的等级。
     * <p>
     * 与服务端一致的两步：先在<b>本地菜单</b>上调一次（{@code clickEnchantmentLevelButton} 会做越界
     * 与冲突校验并返回新等级，返回 {@code -1} 表示被拒），再发一个增量包让服务端照做。
     * 本地这步不能省——否则界面要等一个网络往返才动，连点时会明显卡顿。
     * <p>
     * 按住 Shift 连点，与灌注台一致。
     */
    private void clickLevel(EnchantmentEntry entry, boolean increase) {
        Enchantment current = entry.enchantment;
        do {
            int newLevel = this.menu.clickEnchantmentLevelButton(this.minecraft.player, current, increase);
            if (newLevel == -1) return;
            EnchantingInfuser.NETWORK.sendToServer(
                    new C2SAddEnchantLevelMessage(this.menu.containerId, current, increase));
            this.refreshEntries();

            EnchantmentEntry updated = this.findEntry(current);
            if (updated == null) return;
            boolean canRepeat = increase
                    ? updated.level < updated.maxLevel
                    : updated.level > 0 && updated.level - 1 < updated.maxLevel;
            if (!canRepeat) return;
        } while (Screen.hasShiftDown());
    }

    private EnchantmentEntry findEntry(Enchantment enchantment) {
        for (EnchantmentEntry entry : this.entries) {
            if (entry.enchantment == enchantment) return entry;
        }
        return null;
    }

    // ------------------------------------------------------------------
    // 拖动属性条
    // ------------------------------------------------------------------

    /** 鼠标位置 → 落在哪一条属性条的拖动带上，没有则 null */
    private TableStat hitBar(double mouseX, double mouseY) {
        double x = mouseX - (this.leftPos + BAR_X);
        if (x < 0 || x >= BAR_W) return null;
        for (int i = 0; i < STATS.length; i++) {
            double y = mouseY - (this.topPos + BAR_Y[i] - GRAB_OFFSET);
            if (y >= 0 && y < GRAB_BAND) return STATS[i];
        }
        return null;
    }

    private void dragTo(TableStat stat, double mouseX) {
        UltimateInfuserConfig config = this.config();
        if (config == null) return;
        float max = max(config, stat);
        if (max <= 0.0F) return;

        float ratio = Mth.clamp((float) (mouseX - (this.leftPos + BAR_X)) / BAR_W, 0.0F, 1.0F);
        // 先按绝对上限把鼠标位置还原成数值，再交给 setSelected 夹到书架上限并吸附 0.25
        float value = StatMath.snap(ratio * absoluteMax(stat), max);

        // 不等服务端回包就先本地生效，手感才跟手；随后服务端下发的权威值会覆盖它
        setSelected(config, stat, value);
        UltimateInfuserNetwork.sendSlider(this.menu.containerId, stat, value);
    }

    // ------------------------------------------------------------------
    // 配置读写（三处 switch 集中在这里，避免散落）
    // ------------------------------------------------------------------

    private static float selected(UltimateInfuserConfig config, TableStat stat) {
        return switch (stat) {
            case ETERNA -> config.getSelectedEterna();
            case QUANTA -> config.getSelectedQuanta();
            case ARCANA -> config.getSelectedArcana();
        };
    }

    private static void setSelected(UltimateInfuserConfig config, TableStat stat, float value) {
        switch (stat) {
            case ETERNA -> config.setSelectedEterna(value);
            case QUANTA -> config.setSelectedQuanta(value);
            case ARCANA -> config.setSelectedArcana(value);
        }
    }

    private static float max(UltimateInfuserConfig config, TableStat stat) {
        return switch (stat) {
            case ETERNA -> config.getMaxEterna();
            case QUANTA -> config.getMaxQuanta();
            case ARCANA -> config.getMaxArcana();
        };
    }
}
