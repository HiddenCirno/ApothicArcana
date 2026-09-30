package com.apothinfuser.config;

/**
 * 玩家可以手动调节的三个维度。
 * <p>
 * 刻意不含校准（rectification）：它不参与附魔选择，也不被任何神化灌注配方使用
 * （{@code EnchantingRecipe.matches} 只读 eterna/quanta/arcana），因此只作只读展示。
 * <p>
 * <b>枚举顺序有实际含义</b>：网络包里按 {@code ordinal()} 传输，界面里也用它索引属性条的
 * 纵坐标与颜色表。调整顺序会同时破坏存档与界面，不要动。
 */
public enum TableStat {

    /**
     * 语言键沿用神化自己的。
     * <p>
     * 奥术附魔台跑在神化附魔台的界面上，左侧那三个属性名就是神化的 {@code renderLabels}
     * 用这些键画出来的。tooltip 必须用同一批键，否则同一个维度在同一个界面上会出现两种叫法。
     */
    ETERNA("gui.apotheosis.enchant.eterna"),
    QUANTA("gui.apotheosis.enchant.quanta"),
    ARCANA("gui.apotheosis.enchant.arcana");

    private static final TableStat[] VALUES = values();

    private final String translationKey;

    TableStat(String translationKey) {
        this.translationKey = translationKey;
    }

    public String translationKey() {
        return this.translationKey;
    }

    public static TableStat byOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : ETERNA;
    }
}
