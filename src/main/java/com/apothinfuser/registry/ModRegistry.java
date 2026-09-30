package com.apothinfuser.registry;

import com.apothinfuser.ApothInfuser;
import com.apothinfuser.world.inventory.ArcaneEnchantmentMenu;
import com.apothinfuser.world.inventory.UltimateInfuserMenus;
import com.apothinfuser.world.level.block.ArcaneEnchantingTableBlock;
import com.apothinfuser.world.level.block.UltimateInfuserBlock;
import com.apothinfuser.world.level.block.entity.ArcaneEnchantTile;
import com.apothinfuser.world.level.block.entity.UltimateInfuserBlockEntity;
import fuzs.enchantinginfuser.world.inventory.InfuserMenu;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.EnchantmentMenu;
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

public final class ModRegistry {

    private ModRegistry() {
    }

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, ApothInfuser.MOD_ID);
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, ApothInfuser.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ApothInfuser.MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, ApothInfuser.MOD_ID);

    // ------------------------------------------------------------------
    // 终极奥术附魔台（上位台子）
    //
    // 注册名已从 ultimate_infuser 统一改为 ultimate_arcane_enchanting_table，
    // 与下位的 arcane_enchanting_table 成对。注意 Java 类名仍是 UltimateInfuser* ——
    // 那是内部实现命名，不影响玩家看到的一切，改名收益低而改动面大。
    // ------------------------------------------------------------------

    public static final RegistryObject<UltimateInfuserBlock> ULTIMATE_ARCANE_ENCHANTING_TABLE =
            BLOCKS.register("ultimate_arcane_enchanting_table",
                    () -> new UltimateInfuserBlock(BlockBehaviour.Properties.copy(Blocks.ENCHANTING_TABLE)));

    public static final RegistryObject<Item> ULTIMATE_ARCANE_ENCHANTING_TABLE_ITEM =
            ITEMS.register("ultimate_arcane_enchanting_table",
                    () -> new BlockItem(ULTIMATE_ARCANE_ENCHANTING_TABLE.get(), new Item.Properties()));

    public static final RegistryObject<BlockEntityType<UltimateInfuserBlockEntity>> ULTIMATE_ARCANE_ENCHANTING_TABLE_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("ultimate_arcane_enchanting_table", () -> BlockEntityType.Builder
                    .of(UltimateInfuserBlockEntity::new, ULTIMATE_ARCANE_ENCHANTING_TABLE.get())
                    .build(null));

    /**
     * 注意用的是我们自己的 MenuType，而不是灌注台的 {@code ADVANCED_INFUSING_MENU_TYPE}：
     * 客户端靠 MenuType 决定打开哪个 Screen，而 {@code MenuScreens.register} 对同一类型重复注册会抛异常，
     * 无法覆盖灌注台已有的注册。自有类型让 {@code UltimateArcaneEnchantingScreen} 能被正确关联。
     */
    public static final RegistryObject<MenuType<InfuserMenu>> ULTIMATE_ARCANE_ENCHANTING_MENU =
            MENUS.register("ultimate_arcane_enchanting", () -> new MenuType<>(UltimateInfuserMenus::createClientMenu,
                    FeatureFlags.DEFAULT_FLAGS));

    // ------------------------------------------------------------------
    // 奥术附魔台（下位台子）
    //
    // 跑在神化附魔台自己的机制上，因此只需要方块 + 方块实体 + 菜单三样，
    // 不像终极台子那样还要一整套 config / 注入。
    // ------------------------------------------------------------------

    public static final RegistryObject<ArcaneEnchantingTableBlock> ARCANE_ENCHANTING_TABLE =
            BLOCKS.register("arcane_enchanting_table", ArcaneEnchantingTableBlock::new);

    public static final RegistryObject<Item> ARCANE_ENCHANTING_TABLE_ITEM =
            ITEMS.register("arcane_enchanting_table", () -> new BlockItem(ARCANE_ENCHANTING_TABLE.get(), new Item.Properties()));

    /**
     * 必须自建类型，蹭不了 {@code BlockEntityType.ENCHANTING_TABLE}——
     * 神化把它的 {@code validBlocks} 写死成了 {@code ImmutableSet.of(Blocks.ENCHANTING_TABLE)}，
     * 我们的方块加不进去，且方块实体的 {@code getType()} 也不能返回别人的类型。
     */
    public static final RegistryObject<BlockEntityType<ArcaneEnchantTile>> ARCANE_ENCHANTING_TABLE_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("arcane_enchanting_table", () -> BlockEntityType.Builder
                    .of(ArcaneEnchantTile::new, ARCANE_ENCHANTING_TABLE.get())
                    .build(null));

    /**
     * 必须注册自己的类型：客户端的界面是<b>按菜单类型 id</b> 查表打开的，
     * 若沿用父类的 {@code getType()}，玩家看到的会是神化的界面，我们的可拖动属性条根本不出现。
     * <p>
     * <b>泛型参数为什么是 {@code EnchantmentMenu} 而不是 {@code ArcaneEnchantmentMenu}：</b>
     * {@code MenuScreens.register} 要求 {@code U extends Screen & MenuAccess<M>}，而
     * {@code ApothEnchantScreen} 经由 {@code AbstractContainerScreen<EnchantmentMenu>} 实现的
     * {@code MenuAccess<EnchantmentMenu>} 把 {@code M} 钉死成了 {@code EnchantmentMenu}——
     * 泛型不变，{@code MenuAccess<ArcaneEnchantmentMenu>} 并不成立。于是
     * {@code MenuType<ArcaneEnchantmentMenu>} 会与它冲突，编译期就报推断矛盾。
     * <p>
     * 神化自己也是这么绕的（源码里那句 "menu type is weak due to weird generic stuff
     * regarding screen registration" 说的就是这件事），所以照做：类型放宽到
     * {@code EnchantmentMenu}，界面构造器里再自行强转回具体类。
     */
    public static final RegistryObject<MenuType<EnchantmentMenu>> ARCANE_ENCHANTING_MENU =
            MENUS.register("arcane_enchanting", () -> new MenuType<>(ArcaneEnchantmentMenu::new,
                    FeatureFlags.DEFAULT_FLAGS));

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
        modBus.addListener(ModRegistry::onBuildCreativeTabContents);
    }

    /**
     * 放进「功能方块」页，和附魔灌注台自己的两个台子同一页（它用的是
     * PuzzlesLib 的 {@code registerBuildListener(CreativeModeTabs.FUNCTIONAL_BLOCKS, ...)}，
     * 这里用等价的 Forge 事件）。
     */
    private static void onBuildCreativeTabContents(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(ARCANE_ENCHANTING_TABLE_ITEM.get());
            event.accept(ULTIMATE_ARCANE_ENCHANTING_TABLE_ITEM.get());
        }
    }
}
