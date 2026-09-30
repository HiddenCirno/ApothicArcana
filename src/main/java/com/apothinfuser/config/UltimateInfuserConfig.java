package com.apothinfuser.config;

import dev.shadowsoffire.apotheosis.ench.table.ApothEnchantmentMenu.TableStats;
import fuzs.enchantinginfuser.config.ServerConfig;
import net.minecraft.util.Mth;

/**
 * 本台子的每菜单状态载体。
 * <p>
 * 之所以继承 {@code InfuserConfig} 而不是另建一个类：{@code InfuserMenu} 把配置存在
 * {@code public final ServerConfig.InfuserConfig config} 字段里，我们把自己的子类实例传进去后，
 * 就能在整个 {@code InfuserMenu} 生命周期里通过 {@code config instanceof UltimateInfuserConfig}
 * 认出"这是我的台子"，同时把滑块/四维状态挂在同一个对象上——Mixin 里连一个 {@code @Unique}
 * 字段都不需要。
 * <p>
 * <b>必须是每个菜单独立一份实例。</b>若共用，{@code allowTreasureEnchantments} 与滑块值
 * 会在不同玩家的台子之间串扰。
 * <p>
 * <b>数值用 float。</b>神化的四维本来就是小数（单个深暗书架 eterna 2.5、量子化 5、阿卡那 5），
 * 取整会丢掉精度；而灌注配方 {@code EnchantingRecipe.matches} 吃的正是 float，
 * 且 {@code max_requirements} 允许很窄的窗口，0.25 的步进才卡得准。
 * 注意附魔等级算法本身是整数比较（{@code getCurrentPower()} 返回 int），
 * 且阈值间距约 2.5，所以小数精度只对"显示"与"灌注配方"有实际意义。
 */
public class UltimateInfuserConfig extends ServerConfig.InfuserConfig {

    /** 滑块最小步进。与奥术附魔台共用同一套规则，见 {@link StatMath}。 */
    public static final float STEP = StatMath.STEP;

    /** 服务端独有：完整四维统计（含附魔黑名单）。客户端始终是 INVALID。 */
    private TableStats stats = TableStats.INVALID;

    /** 两端都有：四维上限 */
    private float maxEterna;
    private float maxQuanta;
    private float maxArcana;
    private float maxRectification;
    private boolean treasureUnlocked;

    /** 两端都有：玩家通过滑块选择的当前值 */
    private float selectedEterna;
    private float selectedQuanta;
    private float selectedArcana;

    /** 服务端算出、同步给客户端的灌注产物；空表示当前三维没有命中任何灌注配方 */
    private net.minecraft.world.item.ItemStack infusionResult = net.minecraft.world.item.ItemStack.EMPTY;

    /** 灌注固定的经验等级消耗 */
    public static final int INFUSION_COST = 3;

    /**
     * 复刻灌注台「进阶高级附魔台」档位的默认覆写
     * （对应 {@code ServerConfig} 构造器里对 {@code advancedInfuser} 的那几行）。
     */
    public static UltimateInfuserConfig create() {
        UltimateInfuserConfig config = new UltimateInfuserConfig();
        config.allowRepairing = ServerConfig.AllowedRepairItems.TOOLS_AND_ARMOR;
        config.allowBooks = true;
        config.allowModifyingEnchantments = ServerConfig.ModifiableItems.ALL;
        config.costs.maximumCost = 20;
        config.types.allowAnvilEnchantments = true;
        return config;
    }

    // ---- 服务端：写入完整统计 ----

    public TableStats getStats() {
        return this.stats;
    }

    public void applyStats(TableStats stats) {
        this.stats = stats;
        this.maxEterna = Math.max(0.0F, stats.eterna());
        this.maxQuanta = Math.max(0.0F, stats.quanta());
        this.maxArcana = Math.max(0.0F, stats.arcana());
        this.maxRectification = Math.max(0.0F, stats.rectification());
        this.treasureUnlocked = stats.treasure();
        // 刻意不做破坏性夹取：上限可能因为临时取走物品、挪动书架而变小，
        // 若在此把存储值改小，等上限恢复后玩家的设置就永久丢了。
        // 夹取只发生在读取时（各 getSelectedX 会 snap + clamp）。
    }

    // ---- 客户端：接收同步值（走百分之一定点，避免浮点传输误差）----

    public void applySyncedState(int maxEterna, int maxQuanta, int maxArcana, int maxRectification,
            int selectedEterna, int selectedQuanta, int selectedArcana, boolean treasureUnlocked) {
        this.maxEterna = maxEterna / 100.0F;
        this.maxQuanta = maxQuanta / 100.0F;
        this.maxArcana = maxArcana / 100.0F;
        this.maxRectification = maxRectification / 100.0F;
        this.treasureUnlocked = treasureUnlocked;
        this.selectedEterna = selectedEterna / 100.0F;
        this.selectedQuanta = selectedQuanta / 100.0F;
        this.selectedArcana = selectedArcana / 100.0F;
    }

    public static int toWire(float value) {
        return StatMath.toWire(value);
    }

    // ---- 灌注配方 ----

    public net.minecraft.world.item.ItemStack getInfusionResult() {
        return this.infusionResult;
    }

    public void setInfusionResult(net.minecraft.world.item.ItemStack stack) {
        this.infusionResult = stack;
    }

    /** 当前三维是否命中了某个灌注配方 */
    public boolean hasInfusion() {
        return !this.infusionResult.isEmpty();
    }

    // ---- 四维上限 ----

    public float getMaxEterna() {
        return this.maxEterna;
    }

    public float getMaxQuanta() {
        return this.maxQuanta;
    }

    public float getMaxArcana() {
        return this.maxArcana;
    }

    /** 校准没有消费者（不参与附魔选择，也不被任何神化灌注配方使用），仅作只读展示 */
    public float getMaxRectification() {
        return this.maxRectification;
    }

    public boolean isTreasureUnlocked() {
        return this.treasureUnlocked;
    }

    // ---- 滑块当前值 ----

    public float getSelectedEterna() {
        return snap(this.selectedEterna, this.maxEterna);
    }

    public void setSelectedEterna(float value) {
        this.selectedEterna = snap(value, this.maxEterna);
    }

    public float getSelectedQuanta() {
        return snap(this.selectedQuanta, this.maxQuanta);
    }

    public void setSelectedQuanta(float value) {
        this.selectedQuanta = snap(value, this.maxQuanta);
    }

    public float getSelectedArcana() {
        return snap(this.selectedArcana, this.maxArcana);
    }

    public void setSelectedArcana(float value) {
        this.selectedArcana = snap(value, this.maxArcana);
    }

    /**
     * 从方块实体恢复上次的滑块值。
     * <p>
     * <b>刻意不做夹取</b>：调用时四维上限还没采集（是 0），夹取会把恢复出来的值全部压成 0。
     * 稍后 {@link #applyStats} 里会统一夹取一次。
     */
    public void restoreSelections(float eterna, float quanta, float arcana) {
        this.selectedEterna = Math.max(0.0F, eterna);
        this.selectedQuanta = Math.max(0.0F, quanta);
        this.selectedArcana = Math.max(0.0F, arcana);
    }

    /**
     * 未经上限夹取的原始存储值，仅供持久化使用。
     * <p>
     * 上限因临时取走物品/挪动书架而变小时，显示与计算都走夹取后的 getSelectedX，
     * 但写回方块实体的是这里的原始值——这样等上限恢复，玩家的设置还在。
     */
    public float getRawSelectedEterna() {
        return this.selectedEterna;
    }

    public float getRawSelectedQuanta() {
        return this.selectedQuanta;
    }

    public float getRawSelectedArcana() {
        return this.selectedArcana;
    }

    /**
     * 吸附到 0.25 步进并夹取。
     * <p>
     * 上限本身若是 0.25 的整数倍（神化数据都是，如 2.5 / 35 / 37.5），则上限可以被精确取到；
     * 万一不是，允许直接取到上限，免得永远差一截。
     */
    public static float snap(float value, float max) {
        return StatMath.snap(value, max);
    }
}
