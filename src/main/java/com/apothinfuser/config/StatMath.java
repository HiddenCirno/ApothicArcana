package com.apothinfuser.config;

import net.minecraft.util.Mth;

/**
 * 三维数值的统一规则。两个台子（奥术附魔台 / 终极奥术附魔台）必须用同一套，
 * 否则同一个数字在两个界面上会显示成不同的值。
 * <p>
 * <b>为什么步进是 0.25：</b>神化的三维本来就是小数（单个深暗书架 eterna 2.5、量子化 5、阿卡那 5），
 * 取整会丢精度；而灌注配方 {@code EnchantingRecipe.matches} 吃的正是 float，且
 * {@code max_requirements} 允许很窄的窗口（龙息的量子化窗口只有 15~25），0.25 的步进才卡得准。
 * <p>
 * <b>为什么走定点整数发网络包：</b>float 跨平台没有位级一致性的保证，而这里只需要两位小数，
 * 乘 100 取整成 VarInt 既精确又省字节。
 */
public final class StatMath {

    /** 最小步进。神化数据里出现的所有三维值都是它的整数倍。 */
    public static final float STEP = 0.25F;

    private StatMath() {
    }

    /**
     * 吸附到 {@link #STEP} 步进并夹取到 {@code [0, max]}。
     * <p>
     * 上限本身若不是 0.25 的整数倍，允许直接取到上限——否则玩家永远够不着那个最大值，
     * 而很多灌注配方的 {@code max_requirements} 恰好卡在上限上（例如胡萝卜要求位阶正好 10）。
     */
    public static float snap(float value, float max) {
        float limit = Math.max(0.0F, max);
        float clamped = Mth.clamp(value, 0.0F, limit);
        if (clamped >= limit) return limit;
        return Math.round(clamped / STEP) * STEP;
    }

    /** 浮点值 → 百分之一定点整数（网络传输用） */
    public static int toWire(float value) {
        return Math.round(value * 100.0F);
    }

    /** 百分之一定点整数 → 浮点值 */
    public static float fromWire(int wire) {
        return wire / 100.0F;
    }
}
