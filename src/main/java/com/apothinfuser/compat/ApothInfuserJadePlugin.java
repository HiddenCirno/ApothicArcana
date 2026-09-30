package com.apothinfuser.compat;

import com.apothinfuser.ApothInfuser;
import com.apothinfuser.world.level.block.ArcaneEnchantingTableBlock;
import com.apothinfuser.world.level.block.UltimateInfuserBlock;
import dev.shadowsoffire.apotheosis.util.CommonTooltipUtil;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.Identifiers;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

/**
 * 让两张奥术台在 Jade 的方块信息里显示四维数值。
 * <p>
 * <b>为什么必须由我们自己注册：</b>神化那个附魔台能显示，不是 Jade 主动兼容的，
 * 是<b>神化自己写了一个 Jade 插件</b>（{@code dev.shadowsoffire.apotheosis.ench.compat.EnchHwylaPlugin}，
 * 带 {@code @WailaPlugin} 注解）。Jade 本体不认识 apotheosis 这个名字空间。
 * <p>
 * 而神化那个插件里，出四维数值的那一句是被这样框住的：
 * <pre>
 *   if (accessor.getBlock() == Blocks.ENCHANTING_TABLE) {   // ← 方块对象的身份比较
 *       CommonTooltipUtil.appendTableStats(...);
 *       tooltip.remove(Identifiers.MC_TOTAL_ENCHANTMENT_POWER);
 *   }
 * </pre>
 * 用的是 {@code ==}——只认原版那个<b>唯一实例</b>，方块继承自 {@code EnchantmentTableBlock}
 * 也没用。我们的两个方块是另外两个对象，所以那个分支永远不进，四维自然不显示。
 * <p>
 * <b>所以本类做的事就是替神化把那个 if 补上</b>：对"是奥术台"这件事自己下一个判断，
 * 然后调用同一个 {@code appendTableStats}。显示出来的五行与神化附魔台<b>逐字相同</b>
 * （位阶 / 量子化 / 阿卡那 / 校准 / 线索），连翻译都共用神化自己的 {@code info.apotheosis.*}
 * 键——因为这些行本来就是神化生成的，不是我们仿的。
 *
 * <h2>为什么可以放心复用 {@code CommonTooltipUtil}</h2>
 * 它是 public 类的 public static 方法，是神化自己 compat 层在用的入口。我们整个工程
 * 的既有原则就是「运行时链接 + 公开 API，不复制代码」，这里同理：自己再写一遍那五个
 * {@code Component.translatable} 加 {@code Math.min(100, ...)} 的夹取，
 * 只会得到一份会和神化漂移的副本。
 * <p>
 * 代价要说清楚：这是一条指向神化<b>内部工具类</b>的链接（它在 {@code util} 包下，
 * 不在 {@code ench.api} 下），神化若重命名它，我们会得到一个 {@code NoSuchMethodError}。
 * 接受这个风险的前提是：1.20.1 的神化已经封盘（7.4.8 之后不再动），
 * 且真出问题时症状明确——只在这两张台子上、只在装了 Jade 时、只在悬停的那一瞬间。
 *
 * <h2>不装 Jade 时会怎样</h2>
 * 什么都不会发生。Jade 靠 Forge 的 {@code ModFileScanData} 扫 {@code @WailaPlugin} 注解
 * 来发现插件（拿 ASM 的 {@code Type.getClassName()} 比字符串，<b>不加载类</b>），
 * Jade 缺席时这段扫描压根不执行，唯一引用本类的入口就消失了，
 * 于是本类永远不会被类加载。{@code mods.toml} 里也<b>没有</b>声明 Jade，玩家不装照样玩。
 */
@WailaPlugin
public class ApothInfuserJadePlugin implements IWailaPlugin, IBlockComponentProvider {

    /**
     * 只注册客户端组件，不注册服务端数据提供器。
     * <p>
     * 四维是当场从<b>周围方块</b>算出来的（扫 {@code EnchantmentTableBlock.BOOKSHELF_OFFSETS}），
     * 不需要服务端额外告诉我们什么——书架在客户端同样加载着。神化自己也是这么做的，
     * 它的 {@code appendTableStats} 就写在客户端侧的 {@code appendTooltip} 里。
     * <p>
     * 那条日志与主类里那条同理，是<b>正向信号</b>而非进度打印：
     * 插件没被 Jade 认到（注解没被扫到、uid 撞了、类加载失败）是<b>完全静默</b>的，
     * 表现只是"台子上不显示四维"，与"玩家没装 Jade"无法区分。
     * 有了这一行，玩家反馈时一眼就能定位。只在客户端、只在装 Jade 时出现。
     */
    @Override
    public void registerClient(IWailaClientRegistration reg) {
        reg.registerBlockComponent(this, ArcaneEnchantingTableBlock.class);
        reg.registerBlockComponent(this, UltimateInfuserBlock.class);
        ApothInfuser.LOGGER.info("已接入 Jade：奥术附魔台与终极奥术附魔台将显示四维数值");
    }

    /** 没有需要在服务端采集的数据，留空。 */
    @Override
    public void register(IWailaCommonRegistration reg) {}

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        // Jade 原版对 EnchantmentTableBlock 的向下转型会给出一行「附魔之力: N」，
        // 那是原版书架功率；而下面五行里的位阶是神化口径的同一个量，两行并排是重复的。
        // 神化对原版附魔台也做了同样的删除，这里保持一致。
        //
        // 之所以删得掉：该 provider（TotalEnchantmentPowerProvider）的优先级是 -400，
        // 本 provider 是 1150，Jade 按优先级升序渲染，我们稳在它之后。
        tooltip.remove(Identifiers.MC_TOTAL_ENCHANTMENT_POWER);

        // 神化这个方法是方块无关的：它只按 BOOKSHELF_OFFSETS 扫传入坐标周围的书架，
        // 不检查那个坐标上是什么方块。所以直接用在我们两张台子上完全成立。
        // 这里拿到的是「书架供给的上限」，也正是两张台子滑块的上界。
        CommonTooltipUtil.appendTableStats(accessor.getLevel(), accessor.getPosition(), tooltip::add);
    }

    @Override
    public ResourceLocation getUid() {
        // 本工程统一用 tryBuild（new ResourceLocation(String,String) 已被标记移除）
        return ResourceLocation.tryBuild(ApothInfuser.MOD_ID, "table_stats");
    }

    /**
     * 与神化的 {@code EnchHwylaPlugin} 取同一个值，理由也一样：排到物品展示之后。
     * 唯一硬性要求是必须大于 -400（原版附魔之力那一条），否则上面那行 {@code remove} 会落空。
     */
    @Override
    public int getDefaultPriority() {
        return 1150;
    }
}
