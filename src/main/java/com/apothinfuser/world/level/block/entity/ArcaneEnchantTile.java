package com.apothinfuser.world.level.block.entity;

import com.apothinfuser.config.TableStat;
import com.apothinfuser.registry.ModRegistry;
import dev.shadowsoffire.apotheosis.ench.table.ApothEnchantTile;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 奥术附魔台的方块实体。
 * <p>
 * 继承 {@code ApothEnchantTile} 即可白拿青金石槽（{@code ItemStackHandler}）与 Forge 的
 * {@code IItemHandler} capability——神化原版附魔台用的就是它，因此本台子的槽位行为与之一致。
 * <p>
 * 本类只多负责一件事：把玩家拨定的三维存进存档。存在方块实体而不是玩家身上，是因为
 * "这张台子当前被调成了多少"是<b>台子</b>的属性——换个玩家来用，看到的应该是同一组设置。
 */
public class ArcaneEnchantTile extends ApothEnchantTile {

    private static final String TAG_ETERNA = "ArcaneEterna";
    private static final String TAG_QUANTA = "ArcaneQuanta";
    private static final String TAG_ARCANA = "ArcaneArcana";
    /**
     * 是否被玩家调过。
     * <p>
     * 必须存：未调过的台子每次采集上限都把当前值顶到上限（等于神化原版附魔台），
     * 调过之后才固定住玩家的选择。少了这个标志位，重进世界后无法区分
     * "玩家把三维调成了 0" 和 "玩家从没调过"，会把全新台子误当成已被调成 0。
     */
    private static final String TAG_TUNED = "ArcaneTuned";

    private float selectedEterna;
    private float selectedQuanta;
    private float selectedArcana;
    private boolean tuned;

    public ArcaneEnchantTile(BlockPos pos, BlockState state) {
        super(pos, state);
    }

    /**
     * {@code ApothEnchantTile} 没有覆写 {@code getType()}，会沿用到
     * {@code EnchantmentTableType.ENCHANTING_TABLE}——那样本方块实体会自称是别人的类型。
     */
    @Override
    public BlockEntityType<?> getType() {
        return ModRegistry.ARCANE_ENCHANTING_TABLE_BLOCK_ENTITY.get();
    }

    public boolean isTuned() {
        return this.tuned;
    }

    public float getSelected(TableStat stat) {
        return switch (stat) {
            case ETERNA -> this.selectedEterna;
            case QUANTA -> this.selectedQuanta;
            case ARCANA -> this.selectedArcana;
        };
    }

    public void setSelected(TableStat stat, float value) {
        switch (stat) {
            case ETERNA -> this.selectedEterna = value;
            case QUANTA -> this.selectedQuanta = value;
            case ARCANA -> this.selectedArcana = value;
        }
        this.tuned = true;
        this.setChanged();
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.selectedEterna = tag.getFloat(TAG_ETERNA);
        this.selectedQuanta = tag.getFloat(TAG_QUANTA);
        this.selectedArcana = tag.getFloat(TAG_ARCANA);
        this.tuned = tag.getBoolean(TAG_TUNED);
    }

    // ApothEnchantTile 把这两个方法声明成了 public（原版 BlockEntity 是 protected），
    // 因此这里不能按常规写 protected——那会收窄访问权限，直接编译不过。
    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putFloat(TAG_ETERNA, this.selectedEterna);
        tag.putFloat(TAG_QUANTA, this.selectedQuanta);
        tag.putFloat(TAG_ARCANA, this.selectedArcana);
        tag.putBoolean(TAG_TUNED, this.tuned);
    }
}
