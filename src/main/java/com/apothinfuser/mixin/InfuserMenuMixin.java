package com.apothinfuser.mixin;

import com.apothinfuser.ApothInfuser;
import com.apothinfuser.config.UltimateInfuserConfig;
import com.apothinfuser.network.UltimateInfuserNetwork;
import com.apothinfuser.world.inventory.UltimateInfuserMenuAccess;
import com.apothinfuser.world.level.block.entity.UltimateInfuserBlockEntity;
import com.mojang.datafixers.util.Pair;
import dev.shadowsoffire.apotheosis.ench.Ench;
import dev.shadowsoffire.apotheosis.ench.table.ApothEnchantmentMenu;
import dev.shadowsoffire.apotheosis.ench.table.ApothEnchantmentMenu.TableStats;
import dev.shadowsoffire.apotheosis.ench.table.EnchantingRecipe;
import dev.shadowsoffire.apotheosis.util.ApothMiscUtil;
import dev.shadowsoffire.placebo.util.EnchantmentUtils;
import fuzs.enchantinginfuser.config.ServerConfig;
import fuzs.enchantinginfuser.world.inventory.InfuserMenu;
import net.minecraft.advancements.Advancement;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 本附属的全部行为补丁。所有入口都以 {@code config instanceof UltimateInfuserConfig} 门控，
 * 普通灌注台传的是它自己的 {@code InfuserConfig}，因此完全不受影响。
 * <p>
 * <b>为什么是"注入"而不是"继承"：</b>{@code InfuserMenu} 的两个构造器都是 private，且
 * Enchanting Infuser 本身没有 Mixin/AT。AccessTransformer 也救不了——AT 只在运行时生效，
 * javac 编译时看到的仍是未打补丁的 jar，{@code super(...)} 直接编译不过（已实测）。
 * 因此改为：用 {@code @Invoker} 构造带我们自有 MenuType 与 config 的 {@code InfuserMenu} 实例，
 * 再用本 Mixin 覆写行为。
 * <p>
 * <b>全部入口都以 {@code config instanceof UltimateInfuserConfig} 门控</b>——
 * 普通灌注台传的是它自己的 {@code InfuserConfig}，因此完全不受影响。
 * <p>
 * <b>灌注的呈现方式</b>：不另做预览界面，而是在附魔列表里注入神化的伪造附魔
 * {@code apotheosis:infusion}（{@code InertEnchantment}），上限锁 1 级——这与神化原版附魔台
 * 的做法一致（它的第三个槽位就是塞入同一个假附魔）。这样列表的图标、tooltip、+/- 按钮、
 * 搜索全部照常复用，不需要任何新控件。
 */
@Mixin(InfuserMenu.class)
public abstract class InfuserMenuMixin implements UltimateInfuserMenuAccess {

    @Shadow @Final public ServerConfig.InfuserConfig config;

    @Shadow @Final private Container enchantSlots;

    @Shadow @Final private ContainerLevelAccess levelAccess;

    @Shadow @Final private Player player;

    @Shadow public abstract Map<Enchantment, Integer> getValidEnchantments();

    @Shadow public abstract void setAndSyncEnchantments(Map<Enchantment, Integer> enchantmentsToLevel);

    /**
     * {@code containerId} 是 {@code AbstractContainerMenu} 的 public 字段。Mixin 类并不继承目标类，
     * 用显式转换取值比 {@code @Shadow} 一个继承来的字段更稳妥。
     */
    @Unique
    private int apothinfuser$containerId() {
        return ((AbstractContainerMenu) (Object) this).containerId;
    }

    // ------------------------------------------------------------------
    // 核心：采集四维 + 匹配灌注配方 + 同步给客户端
    // ------------------------------------------------------------------

    @Unique
    private void apothinfuser$refreshFromWorld(Level level, BlockPos pos) {
        if (!(this.config instanceof UltimateInfuserConfig config)) return;

        ItemStack stack = this.enchantSlots.getItem(0);
        // 槽位为空时以 itemEnch = 0 采集：书架提供的三维照常生效，玩家首次打开
        // （槽里本来就没东西）也能看到并调整；物品贡献的基础值则正确地不参与。
        TableStats stats = ApothEnchantmentMenu.gatherStats(level, pos,
                stack.isEmpty() ? 0 : stack.getEnchantmentValue());
        config.applyStats(stats);
        // 灌注台把"宝藏魔咒是否可用"做成了全局配置开关（默认 false），从不查询周围书架。
        // 奥术宝藏深暗书架通过 IEnchantingBlock#allowsTreasure → TableStats.treasure() 传上来。
        config.types.allowTreasureEnchantments = stats.treasure();

        config.setInfusionResult(apothinfuser$matchInfusion(level, stack, config));

        UltimateInfuserNetwork.sendTableState(this.player, this.apothinfuser$containerId(), config);
    }

    /**
     * 用当前三维去匹配灌注配方。
     * <p>
     * 这是本台子相对神化原版附魔台的结构性优势：原版是在第三个槽位"抽卡"
     * （{@code getEnchantmentList} 里 {@code if (enchantSlot == 2 && match != null)} 塞入假附魔），
     * 玩家只能碰运气；这里三维由玩家直接拨定，可以精确卡进 {@code max_requirements} 的窗口
     * （例如胡萝卜要求位阶正好 10、量子化 10~30）。
     */
    @Unique
    private static ItemStack apothinfuser$matchInfusion(Level level, ItemStack input, UltimateInfuserConfig config) {
        if (input.isEmpty()) return ItemStack.EMPTY;
        float eterna = config.getSelectedEterna();
        float quanta = config.getSelectedQuanta();
        float arcana = config.getSelectedArcana();
        EnchantingRecipe match = EnchantingRecipe.findMatch(level, input, eterna, quanta, arcana);
        return match == null ? ItemStack.EMPTY : match.assemble(input, eterna, quanta, arcana);
    }

    /**
     * 把可用附魔列表同步成「当前状态应有的样子」：套用黑名单 + 按需注入/移除伪造附魔。
     * <p>
     * 两个来源都必须在<b>灌注台原有 slotsChanged 跑完之后</b>再处理：
     * 原逻辑会用 {@code setAndSyncEnchantments} 覆盖整个 map，在它之前动是白费功夫。
     */
    @Unique
    private void apothinfuser$syncEnchantmentEntries(UltimateInfuserConfig config) {
        Map<Enchantment, Integer> current = this.getValidEnchantments();
        Map<Enchantment, Integer> updated = new LinkedHashMap<>(current);

        // 黑名单来自水之过滤海洋书架插入的单附魔书（灌注台的过滤器没有这个参数）
        Set<Enchantment> blacklist = config.getStats().blacklist();
        if (!blacklist.isEmpty()) {
            updated.keySet().removeAll(blacklist);
        }

        // 命中灌注配方时注入伪造附魔；物品已换成产物后它自然消失。
        //
        // 这里刻意<b>不</b>要求 updated 非空：灌注配方的目标物品（胡萝卜、龙息、药水……）
        // 大多本来就不接受任何魔咒，getValidEnchantments() 对它们返回空 map——
        // 若以"列表非空"为前提，玩家在游戏里就永远看不到这条灌注选项。
        // hasInfusion() 本身已经保证槽里有个能匹配配方的物品，足够作为依据。
        Enchantment infusion = Ench.Enchantments.INFUSION.get();
        if (config.hasInfusion()) {
            updated.put(infusion, 1);
        } else {
            updated.remove(infusion);
        }

        if (!updated.equals(current)) {
            this.setAndSyncEnchantments(updated);
        }
    }

    // ------------------------------------------------------------------
    // UltimateInfuserMenuAccess 实现
    // ------------------------------------------------------------------

    /**
     * 把当前滑块值写回方块实体。{@code enchantSlots} 对本模组的台子来说就是
     * {@code UltimateInfuserBlockEntity}，因此这里能直接拿到。
     * <p>
     * 用 {@code getRawSelectedX} 而非 {@code getSelectedX}：上限因临时取走物品/挪动书架而变小时，
     * 原始值要保留下来，等上限恢复玩家的设置还在。
     */
    @Override
    public void apothinfuser$persistSliderValues() {
        if (this.enchantSlots instanceof UltimateInfuserBlockEntity blockEntity
                && this.config instanceof UltimateInfuserConfig config) {
            blockEntity.setSelectedStats(config.getRawSelectedEterna(), config.getRawSelectedQuanta(),
                    config.getRawSelectedArcana());
        }
    }

    /**
     * 滑块变化后重新匹配。三维决定配方窗口，拨动滑块就可能进出窗口，
     * 因此这里既要更新灌注产物，也要同步列表里那条伪造附魔。
     */
    @Override
    public void apothinfuser$refreshInfusion() {
        this.levelAccess.execute((level, pos) -> {
            this.apothinfuser$refreshFromWorld(level, pos);
            if (this.config instanceof UltimateInfuserConfig config) {
                this.apothinfuser$syncEnchantmentEntries(config);
            }
        });
    }

    /**
     * 真正执行灌注。服务端<b>重新匹配</b>而不是信任客户端——客户端无法伪造配方白拿产物。
     */
    @Override
    public boolean apothinfuser$performInfusion(ServerPlayer player) {
        if (!(this.config instanceof UltimateInfuserConfig config)) return false;
        AtomicBoolean performed = new AtomicBoolean(false);
        this.levelAccess.execute((level, pos) -> {
            ItemStack input = this.enchantSlots.getItem(0);
            ItemStack result = apothinfuser$matchInfusion(level, input, config);
            if (result.isEmpty()) return;
            if (!player.getAbilities().instabuild) {
                if (player.experienceLevel < UltimateInfuserConfig.INFUSION_COST) return;
                player.giveExperienceLevels(-UltimateInfuserConfig.INFUSION_COST);
            }
            this.enchantSlots.setItem(0, result);
            this.enchantSlots.setChanged();
            apothinfuser$awardEternalEnd(player);
            performed.set(true);
            // 输入已换成产物，配方不再匹配，刷新一次让那条伪造附魔消失
            this.apothinfuser$refreshFromWorld(level, pos);
            this.apothinfuser$syncEnchantmentEntries(config);
        });
        return performed.get();
    }

    /**
     * 授予隐藏进度「永恒尽头」。
     * <p>
     * <b>为什么直接发而不是写一个自定义触发器：</b>灌注是<b>我们自己代码里的一步</b>，
     * 不像"合成出某个物品"那样需要监听一个通用游戏事件。自定义 {@code CriterionTrigger}
     * 要注册、要写 Codec、还要往 JSON 里配对参数，而这里一行 {@code award} 就能表达完。
     * 配方 JSON 里那条 criteria 因此写成 {@code minecraft:impossible}——它永远不会被游戏自己触发，
     * 只作为 {@code award} 的"槽位名"存在，这是这种做法约定俗成的写法。
     * <p>
     * 进度没加载出来时（比如神化附魔模块被关掉，那条 {@code conditions} 让整条链都不存在）
     * {@code getAdvancement} 返回 null，静默跳过即可——不能因为一条进度没了一并崩掉灌注本身。
     */
    @Unique
    private static void apothinfuser$awardEternalEnd(ServerPlayer player) {
        ResourceLocation id = ResourceLocation.tryBuild(ApothInfuser.MOD_ID, "enchanting/eternal_end");
        if (id == null) return;
        Advancement advancement = player.server.getAdvancements().getAdvancement(id);
        if (advancement != null) {
            player.getAdvancements().award(advancement, "infuse");
        }
    }

    // ------------------------------------------------------------------
    // 注入
    // ------------------------------------------------------------------

    /**
     * 功率上限由「周围书架提供的位阶」决定，而不是灌注台那个硬编码的 50
     * （神化 provider 的 {@code getMaximumEnchantPower()} 恒为 50，与书架无关）。
     */
    @Inject(method = "getMaxPower", at = @At("HEAD"), cancellable = true)
    private void apothinfuser$maxPowerFromShelves(CallbackInfoReturnable<Integer> cir) {
        if (this.config instanceof UltimateInfuserConfig config) {
            cir.setReturnValue((int) config.getMaxEterna());
        }
    }

    /**
     * 当前功率由位阶滑块决定。原实现读的是 {@code enchantingPower} DataSlot
     * （由书架扫描算出），我们整条替换掉，因此不需要碰那个私有字段。
     */
    @Inject(method = "getCurrentPower", at = @At("HEAD"), cancellable = true)
    private void apothinfuser$currentPowerFromSlider(CallbackInfoReturnable<Integer> cir) {
        if (this.config instanceof UltimateInfuserConfig config) {
            cir.setReturnValue((int) config.getSelectedEterna());
        }
    }

    /**
     * 伪造附魔的等级上限锁死为 1。
     * <p>
     * 不锁的话它会走稀有度门槛（{@code InertEnchantment} 是 VERY_RARE，需要 0.6×位阶），
     * 位阶不够时 {@code maxLevel} 算出来是 0——而列表条目用 {@code maxLevel == 0} 判定"未解锁"，
     * 会把它显示成乱码名字且完全不可操作。
     */
    @Inject(method = "getMaxLevel", at = @At("HEAD"), cancellable = true)
    private void apothinfuser$infusionMaxLevel(Enchantment enchantment,
            CallbackInfoReturnable<Pair<OptionalInt, Integer>> cir) {
        if (!(this.config instanceof UltimateInfuserConfig)) return;
        if (enchantment == Ench.Enchantments.INFUSION.get()) {
            cir.setReturnValue(Pair.of(OptionalInt.empty(), 1));
        }
    }

    /**
     * 灌注命中时点亮底部那颗按钮。
     * <p>
     * 原实现要求 {@code enchantmentsChanged} 为真（当前列表 ≠ 原始列表）。而我们把伪造附魔塞进列表用的是
     * {@code setAndSyncEnchantments}，它会把 {@code originalEnchantments} 一起改成注入后的列表——
     * 于是"变更"被抹平，按钮永远不亮。这里直接接管判定，不再依赖那套变更跟踪。
     * <p>
     * 保留经验等级判断，否则按钮上的成本数字会一直是绿的（红/绿由 {@code canEnchant} 决定）。
     */
    @Inject(method = "canEnchant", at = @At("HEAD"), cancellable = true)
    private void apothinfuser$canEnchantInfusion(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (this.config instanceof UltimateInfuserConfig config && config.hasInfusion()) {
            cir.setReturnValue(player.experienceLevel >= UltimateInfuserConfig.INFUSION_COST
                    || player.getAbilities().instabuild);
        }
    }

    /**
     * 灌注的成本是固定值，必须接管读数。
     * <p>
     * 不接管的话 {@code calculateEnchantCost()} 会拿"当前列表的缩放成本 − 注入后列表的基准成本"当结果：
     * 原样时得 0，玩家把伪造附魔按到 0 级时得<b>负数</b>——而负数在灌注台里意味着"剥除魔咒、返还经验"，
     * 于是按钮文字变成"获得经验值"、点下去真的发经验球。这曾经是实际现象。
     */
    @Inject(method = "getEnchantCost", at = @At("HEAD"), cancellable = true)
    private void apothinfuser$infusionCost(CallbackInfoReturnable<Integer> cir) {
        if (this.config instanceof UltimateInfuserConfig config && config.hasInfusion()) {
            cir.setReturnValue(UltimateInfuserConfig.INFUSION_COST);
        }
    }

    /**
     * 锁死伪造附魔的等级，让列表里的 +/- 按钮对它无效。
     * <p>
     * 它的"等级"没有任何含义（永远是 1），但灌注台会把它当成可调项。玩家一旦能改动它，
     * 就会触发上面 {@code getEnchantCost} 描述的那条负数分支——症状是按钮忽然变成"获得经验值"。
     * 这里返回当前等级，表现为"按了没反应"。
     */
    @Inject(method = "clickEnchantmentLevelButton", at = @At("HEAD"), cancellable = true)
    private void apothinfuser$lockInfusionLevel(Player player, Enchantment enchantment, boolean increase,
            CallbackInfoReturnable<Integer> cir) {
        if (!(this.config instanceof UltimateInfuserConfig)) return;
        if (enchantment == Ench.Enchantments.INFUSION.get()) {
            cir.setReturnValue(this.getValidEnchantments().getOrDefault(enchantment, 1));
        }
    }

    /**
     * 把「直接扣等级」换成神化的「按经验点结算」。
     * <p>
     * <b>为什么要改：</b>原版 {@code giveExperienceLevels(-N)} 扣的是<b>顶部 N 级各自的边际成本之和</b>——
     * 30 级掉 5 级要一千多点，5 级掉 5 级只要八十几点。同一个"5 级"的代价差一个数量级，
     * 于是玩家在高等级时被狠宰。
     * <p>
     * 神化换了模型：{@code getExpCostForSlot(N, 0) = getExperienceForLevel(N) - 1}，
     * 也就是<b>只收"第 N 级本身所含的经验点"</b>。代价随等级线性增长，与当前等级无关。
     * <p>
     * <b>判定不变。</b>神化自己也是用等级判能不能付得起（{@code player.experienceLevel < cost}），
     * 只有实际扣费走经验点——所以按钮的亮灭逻辑不用动，改的只有这一处。
     * <p>
     * 用 {@code @Redirect} 而不是在 HEAD 拦截整个方法：这个方法里还有剥除魔咒返还经验、
     * 写回物品、发统计等等，全部要保留，只有"扣费"这一个调用点需要换。
     * <p>
     * {@code -levels} 是因为调用点传的是负数（{@code giveExperienceLevels(-cost)}）。
     * 剥除分支走的是 {@code ExperienceOrb.award}，不会经过这里。
     * <p>
     * <b>⚠️ 为什么 {@code method} 写的是 {@code lambda$clickEnchantButton$6} 这种乱码：</b>
     * 那个扣费调用在 {@code levelAccess.execute((level, pos) -> {...})} 的 lambda 里，
     * 而 lambda 会被编译成<b>独立的合成方法</b>，并不在 {@code clickEnchantButton} 本体中。
     * 最初按 {@code "clickEnchantButton"} 写，注入扫到 0 个匹配、
     * 直接让<b>整个模组加载失败</b>（{@code defaultRequire: 1} 下注入失败是致命的）。
     * <p>
     * 这个 {@code $6} / {@code $7} 是编译器按 lambda 出现顺序编号的，灌注台改那个类就会变。
     * 我们靠两点兜底：{@code mods.toml} 把版本锁在 {@code [8.0.3, 8.1)}，
     * 且注入失败是<b>响亮失败</b>而非静默失效——真变了会立刻在启动日志里炸出来。
     */
    @Redirect(method = "lambda$clickEnchantButton$6", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;giveExperienceLevels(I)V"))
    private void apothinfuser$chargePointsForEnchant(Player player, int levels) {
        if (this.config instanceof UltimateInfuserConfig) {
            EnchantmentUtils.chargeExperience(player, ApothMiscUtil.getExpCostForSlot(-levels, 0));
        } else {
            player.giveExperienceLevels(levels);
        }
    }

    /** 维修同理，见 {@link #apothinfuser$chargePointsForEnchant} */
    @Redirect(method = "lambda$clickRepairButton$7", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;giveExperienceLevels(I)V"))
    private void apothinfuser$chargePointsForRepair(Player player, int levels) {
        if (this.config instanceof UltimateInfuserConfig) {
            EnchantmentUtils.chargeExperience(player, ApothMiscUtil.getExpCostForSlot(-levels, 0));
        } else {
            player.giveExperienceLevels(levels);
        }
    }

    /**
     * 降级退款改走<b>与扣费同一条</b>曲线。
     * <p>
     * <b>返还经验本身是灌注台的设计，不是 bug</b>——把某条魔咒的等级调低就是"拆回去退钱"。
     * 问题只出在<b>数额怎么算</b>：{@code cost < 0} 时它走的是另一条分支，
     * 用 {@code calculateExperienceDelta()} 现算，而那个方法压根不是经验曲线——
     * 它把 provider 的 {@code getMinCost} 差值（"书架提供的功率差"，与经验无关的量纲）当成经验，
     * 末尾还叠了个 {@code random.nextInt(...)}，所以数额随机，且与扣费侧对不上。
     * <p>
     * 扣费侧我们已经换成神化的 {@code getExpCostForSlot(N, 0) = getExperienceForLevel(N) - 1}，
     * 于是两侧用了两套互不相干的量纲：同一次"降一级"，退的比收的还多，来回拨动就是净赚。
     * <p>
     * <b>修法：</b>退款额取 {@code getExpCostForSlot(-cost, 0)}——正是把同样的 {@code -cost}
     * 加上去时所收的那笔。扣多少退多少，来回拨动净收益为零，曲线与神化附魔台完全一致。
     * <p>
     * {@code cost} 从 lambda 的第 2 个参数拿不到（{@code @Redirect} 的处理器只能收到被调用点自身的实参），
     * 所以这里通过 {@link InfuserMenuInvoker#apothinfuser$calculateEnchantCost()} 重算一次；
     * 安全性论证见那个方法的注释。仍然撒经验球而不是直接加点，是为了保留原有的视觉反馈。
     * <p>
     * <b>界面上刻意不显示退款数额：</b>大额退款有五位数的经验点，{@code SpriteButton} 只有 19px 宽，
     * 画上去必然溢出。按钮因此沿用灌注台原本的写法——只画一个「+」，具体数值由浮出的经验球体现。
     */
    @Redirect(method = "lambda$clickEnchantButton$6", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/ExperienceOrb;award(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/phys/Vec3;I)V"))
    private void apothinfuser$refundWithApothCurve(ServerLevel level, Vec3 pos, int vanillaAmount) {
        if (!(this.config instanceof UltimateInfuserConfig)) {
            ExperienceOrb.award(level, pos, vanillaAmount);
            return;
        }
        int cost = ((InfuserMenuInvoker) (Object) this).apothinfuser$calculateEnchantCost();
        int points = cost < 0 ? ApothMiscUtil.getExpCostForSlot(-cost, 0) : 0;
        if (points > 0) {
            ExperienceOrb.award(level, pos, points);
        }
    }

    /** 在灌注台原有逻辑之前刷新四维与灌注匹配——它的可用附魔过滤依赖我们要改的 config 字段 */
    @Inject(method = "slotsChanged", at = @At("HEAD"))
    private void apothinfuser$refreshTableStats(Container container, CallbackInfo ci) {
        if (!(this.config instanceof UltimateInfuserConfig)) return;
        if (container != this.enchantSlots) return;
        this.levelAccess.execute(this::apothinfuser$refreshFromWorld);
    }

    /** 原逻辑跑完之后再套黑名单与伪造附魔（之前动会被它整个覆盖掉） */
    @Inject(method = "slotsChanged", at = @At("RETURN"))
    private void apothinfuser$afterSlotsChanged(Container container, CallbackInfo ci) {
        if (!(this.config instanceof UltimateInfuserConfig config)) return;
        if (container != this.enchantSlots) return;
        this.apothinfuser$syncEnchantmentEntries(config);
    }

    /**
     * 选中的是伪造附魔时改走灌注，并取消原附魔流程。
     * <p>
     * 两者是互斥的：灌注消耗输入产出新物品，附魔则是往原物品上写魔咒，同时做会互相破坏。
     * <p>
     * 客户端也会调用 {@code clickMenuButton}（返回 true 才发包），此时 player 不是
     * ServerPlayer，因此只返回 true 不执行；真正的执行发生在服务端收到按钮包之后。
     */
    @Inject(method = "clickEnchantButton", at = @At("HEAD"), cancellable = true)
    private void apothinfuser$infusionInsteadOfEnchant(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (!(this.config instanceof UltimateInfuserConfig)) return;
        if (this.getValidEnchantments().getOrDefault(Ench.Enchantments.INFUSION.get(), 0) <= 0) return;
        if (player instanceof ServerPlayer serverPlayer) {
            this.apothinfuser$performInfusion(serverPlayer);
        }
        cir.setReturnValue(true);
    }
}
