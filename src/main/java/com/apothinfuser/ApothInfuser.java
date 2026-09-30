package com.apothinfuser;

import com.apothinfuser.mixin.InfuserMenuInvoker;
import com.apothinfuser.network.ArcaneNetwork;
import com.apothinfuser.network.UltimateInfuserNetwork;
import com.apothinfuser.registry.ModRegistry;
import fuzs.enchantinginfuser.world.inventory.InfuserMenu;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Mod(ApothInfuser.MOD_ID)
public class ApothInfuser {

    public static final String MOD_ID = "apothinfuser";
    public static final Logger LOGGER = LoggerFactory.getLogger("ApothicInfuser");

    public ApothInfuser(FMLJavaModLoadingContext context) {
        IEventBus modBus = context.getModEventBus();
        ModRegistry.register(modBus);
        UltimateInfuserNetwork.register();
        ArcaneNetwork.register();
        verifyMixinsApplied();
        // 唯一一条常态日志。作用不是"打印进度"，而是给出一个正向信号：
        // 上面的校验只在失败时出声，没有这一行就无法从日志区分
        // "模组正常加载" 和 "模组压根没被加载"——而这正是排查玩家反馈时最先要确认的事。
        LOGGER.info("已加载：奥术附魔台、终极奥术附魔台（行为补丁校验通过）");
    }

    /**
     * 启动期硬自检：确认行为补丁真的被应用了。
     * <p>
     * <b>为什么必须显式检查：</b>本模组对 {@code InfuserMenu} 的全部改造都靠 Mixin 完成，
     * 而 Mixin 失效是<b>完全静默</b>的——配置没加载、目标类签名变了、被别的模组挤掉，
     * 全都不会报错，只会表现为"台子打开后行为诡异"甚至空指针。
     * <p>
     * 判据是决定性的：接口型 Mixin 一旦应用，目标类就会实现该接口。
     * 用 {@code isAssignableFrom} 而不是 {@code instanceof}，因为这里只有 Class 对象。
     * <p>
     * 宁可在启动时明确失败，也不要让玩家在游戏里撞上一个无法归因的崩溃。
     */
    private static void verifyMixinsApplied() {
        if (InfuserMenuInvoker.class.isAssignableFrom(InfuserMenu.class)) return;

        String message = """
                本模组的核心 Mixin 未能应用到 %s。
                这通常由以下原因造成：
                  1. 另一个模组覆盖或移除了 InfuserMenu，导致目标类不匹配；
                  2. Enchanting Infuser 的版本不在 %s 声明的区间内，构造器签名已变更；
                  3. Mixin 框架本身被其他模组干扰。
                由于终极奥术附魔台的全部行为都依赖这些补丁，继续加载只会产生难以归因的错误，故在此终止。
                """.formatted(InfuserMenu.class.getName(), ApothInfuser.MOD_ID);

        LOGGER.error(message);
        throw new IllegalStateException("终极奥术附魔台：核心 Mixin 未应用，详见上方日志");
    }
}
