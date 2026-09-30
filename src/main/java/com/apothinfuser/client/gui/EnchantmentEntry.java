package com.apothinfuser.client.gui;

import fuzs.enchantinginfuser.api.EnchantingInfuserAPI;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.Set;

/**
 * 附魔列表里的一条。
 * <p>
 * 底纹判定与灌注台的 {@code EnchantmentListEntry.getYImage()} 同构：
 * <pre>
 *   置灰 = 与已选中的其他魔咒冲突，或位阶不足且物品上还没有这条魔咒
 *   高亮 = 已选中（等级 &gt; 0）
 *   正常 = 其余
 * </pre>
 * <p>
 * <b>与灌注台唯一的差别</b>：位阶不足<b>但物品上已经有</b>的魔咒（{@link #isLocked()}）
 * 我们照常显示名字与等级，只是不可操作；灌注台会把这类条目整个过滤掉。
 * 原因见 {@link #isLocked()} 的注释。
 * <p>
 * <b>本类不可变。</b>等级变化一律通过「菜单里的 map 变了 → 重建整个列表」来体现，
 * 与灌注台的做法一致。这样就不存在"条目里的 level 和菜单里的 level 不同步"这类脏状态——
 * 前者是后者的纯函数。
 */
final class EnchantmentEntry {

    final Enchantment enchantment;

    /** 当前选中的等级；0 表示未选中 */
    final int level;

    /**
     * 在<b>当前物品</b>上可达到的最高等级。
     * <p>
     * 注意这不是魔咒自身的等级上限，而是还要受<b>位阶</b>限制——位阶不够时这里是 0，
     * 条目会显示成乱码名且完全不可操作（这就是"未解锁"）。
     */
    final int maxLevel;

    /** 选中它所需的最低位阶；-1 表示无门槛。仅用于 tooltip 提示 */
    final int requiredPower;

    /**
     * 与哪些<b>已选中</b>的魔咒冲突。
     * <p>
     * 存集合而不是一个布尔：冲突的 tooltip 要把对方的<b>名字</b>列出来
     * （"该魔咒与锋利不兼容"），只留布尔就得在渲染时重算一遍 O(n) 的判定。
     */
    final Set<Enchantment> incompatible;

    EnchantmentEntry(Enchantment enchantment, int level, int maxLevel, int requiredPower,
            Set<Enchantment> incompatible) {
        this.enchantment = enchantment;
        this.level = Math.max(0, level);
        this.maxLevel = Math.max(0, maxLevel);
        this.requiredPower = requiredPower;
        this.incompatible = Set.copyOf(incompatible);
    }

    /** 已选中 */
    boolean isActive() {
        return this.level > 0;
    }

    /**
     * 位阶不够，且物品上<b>还没有</b>这条魔咒 —— 真正"未知"的那种。
     * <p>
     * 界面给它乱码名 + "你需要更多的书架才能附魔"，与灌注台一致。
     */
    boolean isUnknown() {
        return this.maxLevel == 0 && !this.isActive();
    }

    /**
     * 位阶不够，但物品上<b>已经有</b>这条魔咒 —— 名字照常显示，只是改不动。
     * <p>
     * <b>为什么要和 {@link #isUnknown()} 分开：</b>玩家把位阶滑到低于物品现有魔咒所需的高度时，
     * 那些魔咒会一起变成 {@code maxLevel == 0}。若按"未知"处理，玩家自己辛苦附出来的
     * 锋利 V 会变成一串乱码——既看不到自己有什么，也不知道为什么动不了。
     * 正确做法是照常显示名字与等级，只在 tooltip 里说明"修改它需要更高的位阶"
     * （灌注台的 {@code gui.enchantinginfuser.tooltip.lowPower2} 就是为这种情况写的）。
     * <p>
     * 这与灌注台自己的取舍不同——它直接按 {@code maxLevel > 0} 把这类条目<b>过滤掉</b>了。
     * 那对它的搜索框是合理的（搜不到的就不列），但对我们是纯粹的信息损失。
     */
    boolean isLocked() {
        return this.maxLevel == 0 && this.isActive();
    }

    /** 位阶不足，两种情形之和 */
    boolean isUnpowered() {
        return this.maxLevel == 0;
    }

    /** 是否与已选中的其他魔咒冲突 */
    boolean isIncompatible() {
        return !this.incompatible.isEmpty();
    }

    /**
     * 不可用 = 冲突 或 位阶不足。
     * <p>
     * 只用来判定"能不能操作"，<b>不</b>用来判定"要不要乱码/变灰"——后者见
     * {@link #isUnknown()} 与 {@link #isLocked()}。
     */
    boolean isUnusable() {
        return this.isIncompatible() || this.isUnpowered();
    }

    /**
     * 本条目与另一条是否冲突。
     * <p>
     * 只在<b>至少一方已选中</b>时才判——两条都未选中时它们互不影响，不该互相标红。
     */
    boolean isIncompatibleWith(EnchantmentEntry other) {
        if (other == this) return false;
        return (this.isActive() || other.isActive())
                && !EnchantingInfuserAPI.getEnchantStatsProvider()
                        .isCompatibleWith(this.enchantment, other.enchantment);
    }

    /** 该魔咒自身可达到的最高等级（与物品/位阶无关），决定升级三角是否还有意义 */
    int absoluteMaxLevel() {
        return EnchantingInfuserAPI.getEnchantStatsProvider().getMaxLevel(this.enchantment);
    }
}
