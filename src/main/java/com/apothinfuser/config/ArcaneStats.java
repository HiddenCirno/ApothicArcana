package com.apothinfuser.config;

import dev.shadowsoffire.apotheosis.ench.table.ApothEnchantmentMenu.TableStats;
import net.minecraft.network.FriendlyByteBuf;

/**
 * 奥术附魔台的三维状态：书架提供的上限 + 玩家拨定的当前值。
 * <p>
 * <b>与终极奥术附魔台那边的区别：</b>那边是"扛着一整套灌注台配置"，还要兼容灌注台自己的
 * 四维语义与黑名单；这里只要三个数，因为奥术附魔台完全跑在神化附魔台自己的机制上——
 * 三维是它唯一需要接管的东西。
 * <p>
 * <b>{@code tuned} 的含义（重要）：</b>没被玩家拨过时，当前值每次都跟随上限走，
 * 于是"开箱即等于神化附魔台"；一旦玩家拨过，就固定住玩家的选择并跨会话记忆。
 * 若不做这个区分，全新台子的三维会是 0，反而比它所附属的神化附魔台更弱。
 */
public class ArcaneStats {

    private float maxEterna;
    private float maxQuanta;
    private float maxArcana;

    /**
     * 校准。玩家不可调，纯粹跟随书架。
     * <p>
     * <b>但它必须随同步包一起发给客户端。</b>神化把校准显示在「量子化」属性条的 tooltip 里，
     * 并参与左侧那块"量子化增益/削减"面板的计算（{@code -quanta + quanta * rectification / 100}）。
     * 客户端没有方块实体、也扫不到书架，不发过去它就永远是 0，表现为"读不到校准"。
     */
    private float rectification;

    private float selectedEterna;
    private float selectedQuanta;
    private float selectedArcana;

    /** 玩家是否亲手动过滑块 */
    private boolean tuned;

    // ------------------------------------------------------------------
    // 上限
    // ------------------------------------------------------------------

    /**
     * 记下书架提供的上限，并把当前值收敛进新范围。
     * <p>
     * 未拨动过时直接顶到上限（等价于神化附魔台）；拨动过则只做夹取——
     * 挪走书架会让上限变小，此时必须把当前值压回来，否则滑块会停在条外。
     */
    public void updateFromGathered(TableStats gathered) {
        this.maxEterna = Math.max(0.0F, gathered.eterna());
        this.maxQuanta = Math.max(0.0F, gathered.quanta());
        this.maxArcana = Math.max(0.0F, gathered.arcana());
        this.rectification = Math.max(0.0F, gathered.rectification());

        if (this.tuned) {
            this.selectedEterna = StatMath.snap(this.selectedEterna, this.maxEterna);
            this.selectedQuanta = StatMath.snap(this.selectedQuanta, this.maxQuanta);
            this.selectedArcana = StatMath.snap(this.selectedArcana, this.maxArcana);
        } else {
            this.selectedEterna = this.maxEterna;
            this.selectedQuanta = this.maxQuanta;
            this.selectedArcana = this.maxArcana;
        }
    }

    public float getMax(TableStat stat) {
        return switch (stat) {
            case ETERNA -> this.maxEterna;
            case QUANTA -> this.maxQuanta;
            case ARCANA -> this.maxArcana;
        };
    }

    // ------------------------------------------------------------------
    // 当前值
    // ------------------------------------------------------------------

    public float getSelected(TableStat stat) {
        return StatMath.snap(this.selected(stat), this.getMax(stat));
    }

    /** 玩家拨动。会置上 {@link #tuned}，此后当前值不再自动跟随上限。 */
    public void setSelected(TableStat stat, float value) {
        float snapped = StatMath.snap(value, this.getMax(stat));
        switch (stat) {
            case ETERNA -> this.selectedEterna = snapped;
            case QUANTA -> this.selectedQuanta = snapped;
            case ARCANA -> this.selectedArcana = snapped;
        }
        this.tuned = true;
    }

    /** 未经夹取的存储值，仅供写回方块实体——上限临时变小时也把玩家的设置留住 */
    public float rawSelected(TableStat stat) {
        return this.selected(stat);
    }

    /**
     * 从方块实体恢复上次的拨定值。
     * <p>
     * <b>刻意不夹取</b>：调用时上限还没采集（是 0），夹取会把恢复出来的值全压成 0。
     * 稍后第一次 {@link #updateFromGathered} 会统一收敛一次。
     */
    public void restore(float eterna, float quanta, float arcana) {
        this.selectedEterna = Math.max(0.0F, eterna);
        this.selectedQuanta = Math.max(0.0F, quanta);
        this.selectedArcana = Math.max(0.0F, arcana);
        this.tuned = true;
    }

    public boolean isTuned() {
        return this.tuned;
    }

    private float selected(TableStat stat) {
        return switch (stat) {
            case ETERNA -> this.selectedEterna;
            case QUANTA -> this.selectedQuanta;
            case ARCANA -> this.selectedArcana;
        };
    }

    // ------------------------------------------------------------------
    // 与神化 TableStats 的衔接
    // ------------------------------------------------------------------

    /**
     * 把采集到的书架统计换成"当前值"版本。
     * <p>
     * 除三维外的字段（校准、线索数、附魔黑名单、宝藏解锁）必须原样保留——
     * 它们同样来自书架扫描，与本模组接管的那三个维度无关。
     */
    public TableStats applyTo(TableStats meta) {
        return new TableStats(
                this.getSelected(TableStat.ETERNA),
                this.getSelected(TableStat.QUANTA),
                this.getSelected(TableStat.ARCANA),
                // 用我们自己存的那份，而不是 meta 的：客户端那份 meta 是 INVALID，
                // 若从这里取就会把校准抹成 0（这正是"读不到校准"的原因）。
                this.rectification,
                meta.clues(),
                meta.blacklist(),
                meta.treasure());
    }

    /** 校准只读展示，没有 setter */
    public float getRectification() {
        return this.rectification;
    }

    /**
     * 从服务端同步过来的值覆盖本地。
     * <p>
     * 客户端用：客户端没有方块实体、也扫不到书架，上限与当前值全靠服务端下发。
     */
    public void copyFrom(ArcaneStats other) {
        this.maxEterna = other.maxEterna;
        this.maxQuanta = other.maxQuanta;
        this.maxArcana = other.maxArcana;
        this.rectification = other.rectification;
        this.selectedEterna = other.selectedEterna;
        this.selectedQuanta = other.selectedQuanta;
        this.selectedArcana = other.selectedArcana;
        this.tuned = other.tuned;
    }

    // ------------------------------------------------------------------
    // 网络
    // ------------------------------------------------------------------

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(StatMath.toWire(this.maxEterna));
        buf.writeVarInt(StatMath.toWire(this.maxQuanta));
        buf.writeVarInt(StatMath.toWire(this.maxArcana));
        buf.writeVarInt(StatMath.toWire(this.rectification));
        buf.writeVarInt(StatMath.toWire(this.selectedEterna));
        buf.writeVarInt(StatMath.toWire(this.selectedQuanta));
        buf.writeVarInt(StatMath.toWire(this.selectedArcana));
        buf.writeBoolean(this.tuned);
    }

    public static ArcaneStats read(FriendlyByteBuf buf) {
        ArcaneStats stats = new ArcaneStats();
        stats.maxEterna = StatMath.fromWire(buf.readVarInt());
        stats.maxQuanta = StatMath.fromWire(buf.readVarInt());
        stats.maxArcana = StatMath.fromWire(buf.readVarInt());
        stats.rectification = StatMath.fromWire(buf.readVarInt());
        stats.selectedEterna = StatMath.fromWire(buf.readVarInt());
        stats.selectedQuanta = StatMath.fromWire(buf.readVarInt());
        stats.selectedArcana = StatMath.fromWire(buf.readVarInt());
        stats.tuned = buf.readBoolean();
        return stats;
    }
}
