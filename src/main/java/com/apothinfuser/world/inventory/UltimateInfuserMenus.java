package com.apothinfuser.world.inventory;

import com.apothinfuser.config.UltimateInfuserConfig;
import com.apothinfuser.mixin.InfuserMenuInvoker;
import com.apothinfuser.registry.ModRegistry;
import com.apothinfuser.world.level.block.entity.UltimateInfuserBlockEntity;
import fuzs.enchantinginfuser.world.inventory.InfuserMenu;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;

/**
 * 台子菜单的两个构造入口。
 * <p>
 * {@code InfuserMenu} 的构造器是 private，通过 {@link InfuserMenuInvoker} 调用。
 * 客户端路径复用六参数构造器，传 {@code new SimpleContainer(1)} 与
 * {@code ContainerLevelAccess.NULL}——与灌注台自带的四参数私有构造器行为完全一致。
 */
public final class UltimateInfuserMenus {

    private UltimateInfuserMenus() {
    }

    /** 服务端：方块被打开时创建，带真实容器与世界坐标 */
    public static InfuserMenu createServerMenu(int id, Inventory inventory, Container container,
            ContainerLevelAccess levelAccess) {
        UltimateInfuserConfig config = UltimateInfuserConfig.create();
        // 恢复上次的滑块位置（存在方块实体里，随存档保存）
        if (container instanceof UltimateInfuserBlockEntity blockEntity) {
            config.restoreSelections(blockEntity.getSelectedEterna(), blockEntity.getSelectedQuanta(),
                    blockEntity.getSelectedArcana());
        }
        return InfuserMenuInvoker.apothinfuser$create(ModRegistry.ULTIMATE_ARCANE_ENCHANTING_MENU.get(), id, inventory,
                container, levelAccess, config);
    }

    /** 客户端：由我们的 MenuType 工厂调用，客户端无容器也无世界访问权 */
    public static InfuserMenu createClientMenu(int id, Inventory inventory) {
        return InfuserMenuInvoker.apothinfuser$create(ModRegistry.ULTIMATE_ARCANE_ENCHANTING_MENU.get(), id, inventory,
                new SimpleContainer(1), ContainerLevelAccess.NULL, UltimateInfuserConfig.create());
    }
}
