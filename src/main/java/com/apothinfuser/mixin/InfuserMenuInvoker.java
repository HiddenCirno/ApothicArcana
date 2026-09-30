package com.apothinfuser.mixin;

import fuzs.enchantinginfuser.config.ServerConfig;
import fuzs.enchantinginfuser.world.inventory.InfuserMenu;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * 打开 {@code InfuserMenu} 里两个外部够不着的私有成员。
 * <p>
 * 其一是构造器：{@code InfuserMenu} 的两个构造器都是 private，而灌注台全程没有 Mixin 也没有
 * AccessTransformer，外部无法合法继承。用 {@code @Invoker("<init>")} 打开那个
 * 六参数构造器，让 {@code UltimateInfuserMenu} 能够继承它。
 * <p>
 * 之所以选 {@code @Invoker} 而不是我们自己的 accesstransformer.cfg：不用额外维护 AT 文件，
 * 所有补丁集中在本包内；且 {@code @Invoker} 是 Mixin 官方支持的私有构造器访问方式。
 * <p>
 * 参数顺序必须与 {@code InfuserMenu} 的私有构造器完全一致：
 * {@code (MenuType<?>, int, Inventory, Container, ContainerLevelAccess, ServerConfig.InfuserConfig)}
 */
@Mixin(InfuserMenu.class)
public interface InfuserMenuInvoker {

    @Invoker("<init>")
    static InfuserMenu apothinfuser$create(MenuType<?> menuType, int id, Inventory inventory,
            Container container, ContainerLevelAccess levelAccess, ServerConfig.InfuserConfig config) {
        throw new AssertionError("@Invoker 未被应用，InfuserMenu 构造器签名可能已变更");
    }

    /**
     * 读出当前的等级成本，让 {@code InfuserMenuMixin} 能在「降级退款」那条分支上
     * 用同一条曲线算出应退的经验点。
     * <p>
     * <b>为什么敢在别处重算一次：</b>{@code calculateEnchantCost()} 是纯函数——
     * 只读 {@code enchantments} / {@code enchantingBaseCost} 两个字段，唯一的副作用
     * {@code markChanged()} 仅根据那两个字段重算一个布尔。而退款分支发生的时机在
     * {@code enchantSlots.setItem(...)} <b>之前</b>，两个字段都还没变，
     * 所以重算结果与 {@code clickEnchantButton} 当时存进局部变量的那个 {@code cost} 逐位相同。
     */
    @Invoker("calculateEnchantCost")
    int apothinfuser$calculateEnchantCost();
}
