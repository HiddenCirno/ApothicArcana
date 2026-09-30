package com.apothinfuser.world.level.block.entity;

import com.apothinfuser.registry.ModRegistry;
import fuzs.enchantinginfuser.world.level.block.entity.ForgeInfuserBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 直接继承灌注台的方块实体，白拿单槽容器（{@code WorldlyContainer}）与
 * Forge 的 {@code IItemHandler} capability。
 * <p>
 * 唯一必须覆写的是 {@link #getType()}——{@code InfuserBlockEntity} 把它<b>硬编码</b>成了
 * 灌注台自己的 {@code INFUSER_BLOCK_ENTITY_TYPE}，若不改写，本方块实体会自称是别人的类型。
 * <p>
 * 另外承担三个滑块的持久化：滑块位置是<b>台子</b>的属性（物理上摆在那儿的装置的当前设置），
 * 而不是某个玩家或某次会话的属性，所以存在方块实体里、随世界存档保存。
 */
public class UltimateInfuserBlockEntity extends ForgeInfuserBlockEntity {

    private static final String TAG_ETERNA = "SelectedEterna";
    private static final String TAG_QUANTA = "SelectedQuanta";
    private static final String TAG_ARCANA = "SelectedArcana";

    private float selectedEterna;
    private float selectedQuanta;
    private float selectedArcana;

    public UltimateInfuserBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);
    }

    @Override
    public BlockEntityType<?> getType() {
        return ModRegistry.ULTIMATE_ARCANE_ENCHANTING_TABLE_BLOCK_ENTITY.get();
    }

    public float getSelectedEterna() {
        return this.selectedEterna;
    }

    public float getSelectedQuanta() {
        return this.selectedQuanta;
    }

    public float getSelectedArcana() {
        return this.selectedArcana;
    }

    public void setSelectedStats(float eterna, float quanta, float arcana) {
        if (this.selectedEterna == eterna && this.selectedQuanta == quanta && this.selectedArcana == arcana) {
            return;
        }
        this.selectedEterna = eterna;
        this.selectedQuanta = quanta;
        this.selectedArcana = arcana;
        this.setChanged();
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.selectedEterna = tag.getFloat(TAG_ETERNA);
        this.selectedQuanta = tag.getFloat(TAG_QUANTA);
        this.selectedArcana = tag.getFloat(TAG_ARCANA);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putFloat(TAG_ETERNA, this.selectedEterna);
        tag.putFloat(TAG_QUANTA, this.selectedQuanta);
        tag.putFloat(TAG_ARCANA, this.selectedArcana);
    }
}
