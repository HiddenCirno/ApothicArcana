package com.apothinfuser.registry;

import com.apothinfuser.ApothInfuser;
import com.apothinfuser.world.inventory.ArcaneEnchantmentMenu;
import com.apothinfuser.world.level.block.ArcaneEnchantingTableBlock;
import com.apothinfuser.world.level.block.UltimateReforgingTableBlock;
import com.apothinfuser.world.level.block.entity.ArcaneEnchantTile;
import com.apothinfuser.world.level.block.entity.UltimateReforgingTableTile;
import com.google.common.collect.ImmutableSet;
import dev.shadowsoffire.apotheosis.adventure.Adventure;
import dev.shadowsoffire.apotheosis.adventure.affix.reforging.ReforgingTableTile;
import dev.shadowsoffire.apotheosis.util.TooltipItem;
import dev.shadowsoffire.placebo.block_entity.TickingBlockEntityType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
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
    // 终极奥术附魔台（上位台子）—— 已整体移到可选联动层
    //
    // 它依赖 Enchanting Infuser 的 Menu / Config / BlockEntity，注册时必须保证
    // 没装灌注台的环境不会加载那些类型，因此不能放在本类（本类会在构造期无条件初始化）。
    // 见 com.apothinfuser.integration.UltimateInfuserIntegration。
    // ------------------------------------------------------------------

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

    // ------------------------------------------------------------------
    // 极限重铸台
    //
    // 神化的重铸台把等级上限写成方块常量：简易 2（rare）、普通 4（mythic）。
    // 这一档给 5（ancient），配合数据包里的 reforge/ancient 配方，远古材料才能放进去。
    // 外观直接复用奥术附魔台的贴图与模型（父模型仍是原版 enchanting_table）。
    // ------------------------------------------------------------------

    public static final RegistryObject<UltimateReforgingTableBlock> ULTIMATE_REFORGING_TABLE =
            BLOCKS.register("ultimate_reforging_table",
                    () -> new UltimateReforgingTableBlock(BlockBehaviour.Properties.of()
                            .requiresCorrectToolForDrops().strength(4.0F, 1000.0F)));

    public static final RegistryObject<Item> ULTIMATE_REFORGING_TABLE_ITEM =
            ITEMS.register("ultimate_reforging_table",
                    () -> new BlockItem(ULTIMATE_REFORGING_TABLE.get(), new Item.Properties().rarity(Rarity.EPIC)));

    /**
     * 必须是 Placebo 的 {@link TickingBlockEntityType}，不能是原版 {@code BlockEntityType.Builder}。
     * <p>
     * 重铸台方块继承自 {@code ReforgingTableBlock implements TickingEntityBlock}，而
     * {@code TickingEntityBlock.getTicker} 会检查方块实体类型是不是 {@code TickingBlockEntityType}：
     * 不是的话它只打一行错误日志并返回 {@code null}——<b>不崩，但方块实体永远不 tick</b>。
     * 症状正是"锤子不动"：{@code ReforgingTableTile.clientTick} 负责推进 {@code time/step1}，
     * 世界里的锤子动画完全依赖这两个字段。
     * <p>
     * 泛型放宽到 {@link ReforgingTableTile}：这样客户端可以直接复用神化的
     * {@code ReforgingTableTileRenderer}（它实现的是 {@code BlockEntityRenderer<ReforgingTableTile>}，
     * 泛型不变，不能直接接受更具体的子类）。工厂仍然返回我们的子类。
     */
    public static final RegistryObject<BlockEntityType<ReforgingTableTile>> ULTIMATE_REFORGING_TABLE_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("ultimate_reforging_table", () -> new TickingBlockEntityType<>(
                    UltimateReforgingTableTile::new,
                    ImmutableSet.of(ULTIMATE_REFORGING_TABLE.get()),
                    true, false));

    // ------------------------------------------------------------------
    // 创造模式镶孔符石
    //
    // 不需要任何 Java 行为：神化的 apotheosis:add_sockets 是数据驱动的锻造配方，
    // 孔数上限就是配方 JSON 里的 max_sockets。所以我们只提供物品 + 一份配方
    // （data/apothinfuser/recipes/creative_add_sockets.json，max_sockets = int 上限）。
    //
    // 走的是标准锻造台流程（模板槽留空、装备放 base、符石放 addition），
    // 因此和神化自己的符石一样：取走产物时符石会被消耗 1 个。
    // ------------------------------------------------------------------

    public static final RegistryObject<Item> CREATIVE_SIGIL_OF_SOCKETING =
            ITEMS.register("creative_sigil_of_socketing",
                    () -> new TooltipItem(new Item.Properties().rarity(Rarity.EPIC)));

    /** 神化的冒险创造页；物品放这里，和符石本尊同一页。 */
    private static final ResourceKey<CreativeModeTab> APOTHEOSIS_ADVENTURE_TAB =
            ResourceKey.create(Registries.CREATIVE_MODE_TAB, new ResourceLocation("apotheosis", "adventure"));

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
            // 终极奥术附魔台由联动层自己往这一页加（仅在装了 Enchanting Infuser 时）。
        }
        // 冒险页：极限重铸台与神化的两张重铸台放一起，创造符石也在这里。
        // 冒险模块被关掉时这个页签根本不会创建，事件不会以那个 key 触发，自然什么也不做。
        if (event.getTabKey().equals(APOTHEOSIS_ADVENTURE_TAB)) {
            // 神化只把 common~mythic 的珍宝材料塞进了这一页，远古材料是有意排除的
            // （weight 0、正常不可得）。这里按需求补上，让创造模式可以直接拿到「无限之体现」。
            event.accept(Adventure.Items.ANCIENT_MATERIAL.get());
            event.accept(ULTIMATE_REFORGING_TABLE_ITEM.get());
            event.accept(CREATIVE_SIGIL_OF_SOCKETING.get());
        }
    }
}
