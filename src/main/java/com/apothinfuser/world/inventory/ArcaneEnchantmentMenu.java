package com.apothinfuser.world.inventory;

import com.apothinfuser.config.ArcaneStats;
import com.apothinfuser.config.TableStat;
import com.apothinfuser.network.ArcaneNetwork;
import com.apothinfuser.registry.ModRegistry;
import com.apothinfuser.world.level.block.entity.ArcaneEnchantTile;
import dev.shadowsoffire.apotheosis.ench.table.ApothEnchantmentMenu;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;

/**
 * 奥术附魔台的菜单。
 * <p>
 * <b>本模组对神化附魔台的全部改造，就集中在本类的 {@link #gatherStats()} 一个方法里。</b>
 * 不需要任何 Mixin，因为 {@code ApothEnchantmentMenu} 本来就为继承留好了口子：
 * {@code stats} 字段是 {@code protected}、{@code gatherStats()} 是 public 且可覆写、
 * 构造器全部 public。
 * <p>
 * 之所以只改这一处就够：{@code slotsChanged()} 的<b>第一步</b>就是 {@code gatherStats()}，
 * 之后才拿 {@code this.stats} 去算三个附魔位的经验成本、线索数、以及可用魔咒列表。
 * 所以在这里把"书架采集值"换成"玩家拨定值"，下游全部自动随之改变——
 * 包括第三个槽位的灌注判定（{@code EnchantingRecipe.findMatch} 读的也是从这里出去的三个数）。
 */
public class ArcaneEnchantmentMenu extends ApothEnchantmentMenu {

    /**
     * 我们自己的访问器副本。
     * <p>
     * 不能直接用 {@code this.access}：那是原版 {@code EnchantmentMenu} 的私有字段，
     * 神化能在自己包里访问是因为它有 AccessTransformer，我们作为外部子类拿不到。
     * 构造时把同一个对象存一份即可。
     */
    private final ContainerLevelAccess arcaneAccess;

    /** 服务端非空。客户端的菜单由 MenuType 工厂造出，拿不到方块实体。 */
    private final ArcaneEnchantTile tile;

    private final ArcaneStats arcane = new ArcaneStats();

    /** 客户端构造器：MenuType 的工厂会调它 */
    public ArcaneEnchantmentMenu(int id, Inventory inventory) {
        super(id, inventory);
        this.arcaneAccess = ContainerLevelAccess.NULL;
        this.tile = null;
    }

    /** 服务端构造器 */
    public ArcaneEnchantmentMenu(int id, Inventory inventory, ContainerLevelAccess access, ArcaneEnchantTile tile) {
        super(id, inventory, access, tile);
        this.arcaneAccess = access;
        this.tile = tile;
        if (tile.isTuned()) {
            // 此刻上限还没采集（是 0），所以走不夹取的 restore；随后的 gatherStats 会统一收敛
            this.arcane.restore(tile.getSelected(TableStat.ETERNA),
                    tile.getSelected(TableStat.QUANTA), tile.getSelected(TableStat.ARCANA));
        }
    }

    /**
     * 关键覆写：必须换成我们自己的菜单类型。
     * <p>
     * 客户端的界面是<b>按菜单类型 id</b> 查表打开的。若沿用神化的类型，玩家会看到神化的界面，
     * 我们的滑块将完全不可见。原方法不是 final，可以直接覆写。
     */
    @Override
    public MenuType<?> getType() {
        return ModRegistry.ARCANE_ENCHANTING_MENU.get();
    }

    public ArcaneStats getArcane() {
        return this.arcane;
    }

    /**
     * 必须覆写，否则界面会<b>一打开就被服务端关掉</b>。
     * <p>
     * 原版 {@code EnchantmentMenu} 的实现是（字节码核对过）：
     * <pre>return stillValid(this.access, player, Blocks.ENCHANTING_TABLE);</pre>
     * 它写死了原版附魔台那一个方块对象。神化不需要管这件事，因为它的台子
     * <b>就是</b> {@code Blocks.ENCHANTING_TABLE} 本身（被 Placebo 覆盖过）；而我们的方块是另一个对象，
     * 这句判定必然为假。
     * <p>
     * 服务端每 tick 都会用 {@code stillValid} 检查玩家是否还站在正确的方块旁，
     * 一旦为假立刻下发关闭界面的包——表现就是"打开后瞬间自动关闭"，且不产生任何报错。
     */
    @Override
    public boolean stillValid(Player player) {
        return stillValid(this.arcaneAccess, player, ModRegistry.ARCANE_ENCHANTING_TABLE.get());
    }

    /**
     * 采集三维——本模组唯一的行为接管点。
     * <p>
     * <b>刻意不调用 {@code super.gatherStats()}</b>：那一版会先把书架原始值发给客户端，
     * 而我们紧接着还要发一份拨定后的值。两个包走的是<b>不同的网络通道</b>（神化自己的 vs 我们的），
     * 跨通道不保证到达顺序，客户端可能被原始值覆盖回去，表现为"三条属性条跳回书架满值"。
     * 这里直接自己做完整流程，每个 tick 只发一个权威包。
     */
    @Override
    public void gatherStats() {
        this.arcaneAccess.execute((level, pos) -> {
            var gathered = gatherStats(level, pos, this.getSlot(0).getItem().getEnchantmentValue());
            this.arcane.updateFromGathered(gathered);
            this.stats = this.arcane.applyTo(gathered);
            ArcaneNetwork.sendStats(this.player, this.containerId, this.arcane);
        });
    }

    /**
     * 服务端：玩家拨动了某一条。
     * <p>
     * 拨完必须整条重算，不能只改数字：位阶决定三个附魔位的经验成本与可选等级上限，
     * 量子化/阿卡那决定魔咒列表与第三个槽位能否灌注。{@code slotsChanged} 正是那套流程，
     * 且它开头会再次调用 {@link #gatherStats()}，顺带把新值同步给客户端。
     */
    public void applySelection(TableStat stat, float value) {
        this.arcane.setSelected(stat, value);
        if (this.tile != null) {
            this.tile.setSelected(stat, this.arcane.rawSelected(stat));
        }
        this.slotsChanged(this.getSlot(0).container);
    }

    /** 客户端：装上服务端下发的权威值。后续由 {@code ApothEnchantScreen.containerTick} 平滑插值。 */
    public void applyClientStats(ArcaneStats incoming) {
        this.arcane.copyFrom(incoming);
        this.stats = this.arcane.applyTo(this.stats);
    }

    /**
     * 客户端拖动中的本地即时反馈。
     * <p>
     * 不等服务端回包（一个往返至少一帧，快速拖动时会明显滞后于鼠标）就先把条画到新位置；
     * 随后服务端下发的权威值会把这里的结果覆盖掉，因此不担心本地越界——
     * 越界的值在这一步也已经被 {@code setSelected} 夹到上限了。
     */
    public void applyLocalSelection(TableStat stat, float value) {
        this.arcane.setSelected(stat, value);
        this.stats = this.arcane.applyTo(this.stats);
    }
}
