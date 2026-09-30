package com.apothinfuser.world.level.block;

import com.apothinfuser.registry.ModRegistry;
import com.apothinfuser.world.inventory.UltimateInfuserMenus;
import com.apothinfuser.world.level.block.entity.UltimateInfuserBlockEntity;
import fuzs.enchantinginfuser.world.level.block.InfuserBlock;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EnchantmentTableBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.EnchantmentTableBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 神化：终极灌注——终极附魔台方块。
 * <p>
 * 继承 {@link EnchantmentTableBlock} 而不是灌注台的 {@code InfuserBlock}：后者的构造器强制要求
 * 那个只有 {@code NORMAL / ADVANCED} 两个值的封闭枚举，并且内部到处是硬 {@code switch}。
 * 继承 {@code EnchantmentTableBlock} 可以直接复用 {@code BOOKSHELF_OFFSETS}，
 * 与神化 {@code ApothEnchantmentMenu.gatherStats} 的扫描半径天然对齐。
 */
public class UltimateInfuserBlock extends EnchantmentTableBlock {

    public UltimateInfuserBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new UltimateInfuserBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        // 客户端需要漂浮的书本动画
        return level.isClientSide
                ? createTickerHelper(type, ModRegistry.ULTIMATE_ARCANE_ENCHANTING_TABLE_BLOCK_ENTITY.get(),
                        EnchantmentTableBlockEntity::bookAnimationTick)
                : null;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hitResult) {
        if (level.getBlockEntity(pos) instanceof UltimateInfuserBlockEntity blockEntity) {
            if (!level.isClientSide) {
                player.openMenu(state.getMenuProvider(level, pos));
                // 物品可能还留在背包槽位里，刷新一次附魔按钮才会出现
                player.containerMenu.slotsChanged(blockEntity);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    @Nullable
    @Override
    public MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof UltimateInfuserBlockEntity blockEntity) {
            Component title = blockEntity.getDisplayName();
            return new SimpleMenuProvider((id, inventory, player) -> blockEntity.canOpen(player)
                    ? UltimateInfuserMenus.createServerMenu(id, inventory, blockEntity,
                            ContainerLevelAccess.create(level, pos))
                    : null, title);
        }
        return null;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof UltimateInfuserBlockEntity blockEntity) {
            Containers.dropContents(level, pos, blockEntity);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof UltimateInfuserBlockEntity blockEntity
                && !blockEntity.getItem(0).isEmpty() ? 15 : 0;
    }

    /** 物品栏里的说明文字，见 {@link ArcaneEnchantingTableBlock#appendHoverText} 的注释 */
    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("block.apothinfuser.ultimate_arcane_enchanting_table.desc")
                .withStyle(ChatFormatting.GRAY));
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        super.animateTick(state, level, pos, random);
        for (BlockPos offset : BOOKSHELF_OFFSETS) {
            // 直接复用灌注台的书架判定（public static），保证与统计来源一致
            if (random.nextInt(16) == 0 && InfuserBlock.isValidBookShelf(level, pos, offset)) {
                level.addParticle(ParticleTypes.ENCHANT,
                        pos.getX() + 0.5, pos.getY() + 2.0, pos.getZ() + 0.5,
                        (offset.getX() + random.nextFloat()) - 0.5,
                        offset.getY() - random.nextFloat() - 1.0F,
                        (offset.getZ() + random.nextFloat()) - 0.5);
            }
        }
    }
}
