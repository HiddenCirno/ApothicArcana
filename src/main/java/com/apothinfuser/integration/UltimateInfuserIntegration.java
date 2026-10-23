package com.apothinfuser.integration;

import com.apothinfuser.ApothInfuser;
import com.apothinfuser.mixin.InfuserMenuInvoker;
import com.apothinfuser.network.UltimateInfuserNetwork;
import com.apothinfuser.world.inventory.UltimateInfuserMenus;
import com.apothinfuser.world.level.block.UltimateInfuserBlock;
import com.apothinfuser.world.level.block.entity.UltimateInfuserBlockEntity;
import fuzs.enchantinginfuser.world.inventory.InfuserMenu;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Enchanting Infuser 联动层（<b>可选依赖</b>）。
 *
 * <p><b>为什么整层单独拆出来：</b>本类引用的每一个类型——{@code InfuserMenu}、
 * {@code ServerConfig.InfuserConfig}、{@code ForgeInfuserBlockEntity}——都来自灌注台。
 * 只要这些类型出现在<b>会无条件加载</b>的类里（比如 {@code ModRegistry} 的静态字段），
 * 没装灌注台的玩家就会在模组构造阶段撞上 {@code NoClassDefFoundError}，整个模组都加载不了。
 * <p>
 * 所以：类加载是惰性的——只要 {@link #register(IEventBus)} 只在
 * {@code ModList.isLoaded("enchantinginfuser")} 为真时调用，本类及其引用的所有类型
 * 在没装灌注台的环境里<b>永远不会被加载</b>。注册表本身也用独立的
 * {@link DeferredRegister}（同一个 modId），与 {@code ModRegistry} 互不牵连。
 * <p>
 * <b>注册名必须保持不变</b>：{@code ultimate_arcane_enchanting_table} 与
 * {@code ultimate_arcane_enchanting}——老存档里存的是这两个 id。
 */
public final class UltimateInfuserIntegration {

    private UltimateInfuserIntegration() {
    }

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, ApothInfuser.MOD_ID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, ApothInfuser.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ApothInfuser.MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, ApothInfuser.MOD_ID);

    public static final RegistryObject<UltimateInfuserBlock> ULTIMATE_ARCANE_ENCHANTING_TABLE =
            BLOCKS.register("ultimate_arcane_enchanting_table",
                    () -> new UltimateInfuserBlock(BlockBehaviour.Properties.copy(Blocks.ENCHANTING_TABLE)));

    public static final RegistryObject<Item> ULTIMATE_ARCANE_ENCHANTING_TABLE_ITEM =
            ITEMS.register("ultimate_arcane_enchanting_table",
                    () -> new BlockItem(ULTIMATE_ARCANE_ENCHANTING_TABLE.get(), new Item.Properties()));

    /**
     * 自建类型：{@code ForgeInfuserBlockEntity} 把 {@code getType()} 写死成灌注台自己的类型，
     * 蹭不了；方块实体的 {@code getType()} 也不能返回别人的类型。
     */
    public static final RegistryObject<BlockEntityType<UltimateInfuserBlockEntity>> ULTIMATE_ARCANE_ENCHANTING_TABLE_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("ultimate_arcane_enchanting_table", () -> BlockEntityType.Builder
                    .of(UltimateInfuserBlockEntity::new, ULTIMATE_ARCANE_ENCHANTING_TABLE.get())
                    .build(null));

    /**
     * 自有 MenuType：客户端按类型决定打开哪个 Screen。灌注台对同一类型重复注册会抛异常，
     * 所以不能沿用它的 {@code ADVANCED_INFUSING_MENU_TYPE}。
     */
    public static final RegistryObject<MenuType<InfuserMenu>> ULTIMATE_ARCANE_ENCHANTING_MENU =
            MENUS.register("ultimate_arcane_enchanting", () -> new MenuType<>(
                    UltimateInfuserMenus::createClientMenu, FeatureFlags.DEFAULT_FLAGS));

    /**
     * 只有确认灌注台存在时才会被调用。
     * <p>
     * 顺序：先挂注册表（方块/物品/方块实体/菜单），再注册网络包，最后做 Mixin 自检。
     * 自检放在最后，失败时直接抛异常——与解耦前一样，装了灌注台却补丁没应用时，
     * 宁可启动即失败，也不要在游戏里打开一张行为诡异的台子。
     */
    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        modBus.addListener(UltimateInfuserIntegration::onBuildCreativeTabContents);

        UltimateInfuserNetwork.register();
        verifyMixinsApplied();
    }

    /** 与两张奥术台一起放在「功能方块」页。 */
    private static void onBuildCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(ULTIMATE_ARCANE_ENCHANTING_TABLE_ITEM.get());
        }
    }

    /**
     * 启动期硬自检：确认行为补丁真的被应用了。
     * <p>
     * 判据是决定性的：接口型 Mixin 一旦应用，目标类就会实现该接口。
     * 用 {@code isAssignableFrom} 而不是 {@code instanceof}，因为这里只有 Class 对象。
     */
    private static void verifyMixinsApplied() {
        if (InfuserMenuInvoker.class.isAssignableFrom(InfuserMenu.class)) return;

        String message = """
                本模组的 Enchanting Infuser 联动 Mixin 未能应用到 %s。
                这通常由以下原因造成：
                  1. 另一个模组覆盖或移除了 InfuserMenu，导致目标类不匹配；
                  2. Enchanting Infuser 的版本不在 mods.toml 声明的区间内，构造器签名已变更；
                  3. Mixin 框架本身被其他模组干扰。
                由于终极奥术附魔台的全部行为都依赖这些补丁，继续加载只会产生难以归因的错误，故在此终止。
                """.formatted(InfuserMenu.class.getName());

        ApothInfuser.LOGGER.error(message);
        throw new IllegalStateException("终极奥术附魔台：Enchanting Infuser 联动 Mixin 未应用，详见上方日志");
    }
}
