package com.apothinfuser.world.level.block;

import com.apothinfuser.registry.ModRegistry;
import com.apothinfuser.world.inventory.ArcaneEnchantmentMenu;
import com.apothinfuser.world.level.block.entity.ArcaneEnchantTile;
import dev.shadowsoffire.apotheosis.ench.table.ApothEnchantBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.EnchantmentTableBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 奥术附魔台（下位台子）。
 * <p>
 * <b>为什么是新方块，而不是改造原版附魔台：</b>神化已经用 Placebo 的覆盖机制把
 * {@code Blocks.ENCHANTING_TABLE} 整个换成了它自己的 {@code ApothEnchantBlock}——
 * 游戏里的"原版附魔台"其实一直是神化的台子。两个模组同时抢同一个方块对象，
 * 结果取决于装载顺序，是必崩的写法。所以本台子自立门户，外观沿用原版附魔台。
 * <p>
 * <b>为什么可以放心继承 {@code ApothEnchantBlock}：</b>它的 {@code getStateDefinition()}
 * 对 {@code container} 字段有 null 兜底（那是 Placebo 覆盖机制专用的），我们不走覆盖注册，
 * 该字段保持 null，会正常回落到父类实现。继承之后白拿书架粒子（{@code animateTick}）、
 * 破坏时掉落青金石槽内容（{@code onRemove}）以及方块属性，只覆写两处。
 */
public class ArcaneEnchantingTableBlock extends ApothEnchantBlock {

    @Override
    public MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof ArcaneEnchantTile tile) {
            return new SimpleMenuProvider(
                    (id, inventory, player) -> new ArcaneEnchantmentMenu(id, inventory,
                            ContainerLevelAccess.create(level, pos), tile),
                    tile.getDisplayName());
        }
        return null;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ArcaneEnchantTile(pos, state);
    }

    /**
     * 书本动画的 ticker。
     * <p>
     * <b>必须覆写。</b>原版 {@code EnchantmentTableBlock.getTicker} 把它绑定在
     * {@code BlockEntityType.ENCHANTING_TABLE} 上（内部那句 {@code createTickerHelper} 会比对类型），
     * 而本台子用的是自己的方块实体类型，比对必然失败，结果是上方那本书<b>不翻页也不悬浮</b>——
     * 画面看起来像卡住了，却不报任何错。
     * <p>
     * 只在客户端需要：服务端不渲染书。
     */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, ModRegistry.ARCANE_ENCHANTING_TABLE_BLOCK_ENTITY.get(),
                        EnchantmentTableBlockEntity::bookAnimationTick)
                : null;
    }

    /**
     * 物品栏里的说明文字。
     * <p>
     * 写在<b>方块</b>上而不是自建一个 {@code BlockItem} 子类：{@code BlockItem.appendHoverText}
     * 本来就会转发给 {@code getBlock().appendHoverText(...)}，所以覆写这里就够了，
     * 注册表那边一个字符都不用动。灌注台的 {@code InfuserBlock} 与神化那几台机器也都是这么做的。
     * <p>
     * 灰色是这类"风味描述"的惯例（神化的 {@code info.apotheosis.*} 那一整批都是 GRAY）；
     * 灌注台反而是不加样式的白字。这里跟神化走。
     */
    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("block.apothinfuser.arcane_enchanting_table.desc")
                .withStyle(ChatFormatting.GRAY));
    }
}
