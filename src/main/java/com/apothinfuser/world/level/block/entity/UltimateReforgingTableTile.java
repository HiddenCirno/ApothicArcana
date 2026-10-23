package com.apothinfuser.world.level.block.entity;

import com.apothinfuser.registry.ModRegistry;
import dev.shadowsoffire.apotheosis.adventure.affix.reforging.ReforgingTableTile;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 极限重铸台的方块实体。
 * <p>
 * <b>为什么必须覆写 {@code getType()}</b>：{@link ReforgingTableTile} 的构造器把类型写死成
 * 神化自己的 {@code Apoth.Tiles.REFORGING_TABLE}，而那个类型的 {@code validBlocks} 是
 * 不可变集合、只含神化自己的两张台子。若照搬，我们的方块会以一个"不包含自己"的类型存档：
 * 方块实体本身能跑（原版加载时不做 validBlocks 校验），但注册表里那个新类型永远用不上，
 * 而且语义上是错的。
 * <p>
 * {@code BlockEntity.getType()} 不是 final，所以这里直接改报我们自己的类型。内部字段仍是
 * 神化的类型，但除了 {@code getType()} 之外没有任何地方读它；存档写入的是
 * {@code getType()} 的注册名，读档时由我们类型的工厂重新构造本类，前后一致。
 * <p>
 * 其余行为（库存、重铸菜单、客户端动画）全部原样继承——上古重铸的"上限"不在这类里，
 * 而是由方块上的 {@code maxRarity = 5} 决定。
 */
public class UltimateReforgingTableTile extends ReforgingTableTile {

    public UltimateReforgingTableTile(BlockPos pos, BlockState state) {
        super(pos, state);
    }

    @Override
    public BlockEntityType<?> getType() {
        return ModRegistry.ULTIMATE_REFORGING_TABLE_BLOCK_ENTITY.get();
    }
}
