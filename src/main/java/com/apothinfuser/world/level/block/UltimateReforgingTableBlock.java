package com.apothinfuser.world.level.block;

import com.apothinfuser.world.level.block.entity.UltimateReforgingTableTile;
import dev.shadowsoffire.apotheosis.adventure.affix.reforging.ReforgingTableBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 极限重铸台。
 * <p>
 * <b>原版重铸台的等级上限是写死的方块常量</b>：{@code ReforgingTableBlock(properties, maxRarity)}
 * 把它存成 int，{@code ReforgingTableTile.isValidRarityMat} 再要求
 * {@code getMaxRarity().isAtLeast(材料稀有度)}。神化只注册了 rare(2) 与 mythic(4) 两档，
 * 所以正常情况下远古材料根本放不进去。这里注册第三档 {@code maxRarity = 5}（ancient）。
 * <p>
 * 光有台子还不够：{@code isValidRarityMat} 同时要求 {@code getRecipeFor(rarity) != null}。
 * 对应的重铸配方在我们的数据包里（{@code data/apothinfuser/recipes/reforging/ancient.json}），
 * 两个条件缺一不可。
 * <p>
 * 外观按需求直接复用奥术附魔台的模型与贴图（{@code minecraft:block/enchanting_table} 父模型），
 * 方块高度与神化的重铸台一致（视觉上占 12 像素），碰撞箱沿用父类。
 */
public class UltimateReforgingTableBlock extends ReforgingTableBlock {

    /** 稀有度 ordinal：common 0 / uncommon 1 / rare 2 / epic 3 / mythic 4 / ancient 5。 */
    private static final int ANCIENT_ORDINAL = 5;

    public UltimateReforgingTableBlock(BlockBehaviour.Properties properties) {
        super(properties, ANCIENT_ORDINAL);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new UltimateReforgingTableTile(pos, state);
    }

    /**
     * 父类的 tooltip 只在 {@code maxRarity < 最高稀有度} 时才追加第二行。
     * 我们的上限就是最高档，那个条件恒假，所以这里自己补上——否则玩家看不出
     * 这张台子和普通重铸台的区别。
     */
    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip,
            TooltipFlag flag) {
        tooltip.add(Component.translatable("block.apotheosis.reforging_table.desc").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("block.apotheosis.reforging_table.desc2", this.getMaxRarity().toComponent())
                .withStyle(ChatFormatting.GRAY));
    }
}
