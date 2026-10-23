package com.apothinfuser;

import com.apothinfuser.integration.UltimateInfuserIntegration;
import com.apothinfuser.network.ArcaneNetwork;
import com.apothinfuser.registry.ModRegistry;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 神化：奥术秘法。
 *
 * <p><b>依赖分两类：</b>神化及其前置（Placebo、Apothic Attributes）是硬依赖；
 * <b>Enchanting Infuser 是可选联动</b>——装了才有终极奥术附魔台。
 * 解耦的边界落在 {@link UltimateInfuserIntegration}：它引用的全部灌注台类型
 * 都只在该类里出现，而该类只在上面的 {@code isLoaded} 分支里被首次触碰。
 * Java 的类加载是惰性的，因此没装灌注台时那些类型一个都不会被解析。
 */
@Mod(ApothInfuser.MOD_ID)
public class ApothInfuser {

    public static final String MOD_ID = "apothinfuser";
    public static final Logger LOGGER = LoggerFactory.getLogger("ApothicInfuser");

    public ApothInfuser(FMLJavaModLoadingContext context) {
        IEventBus modBus = context.getModEventBus();
        ModRegistry.register(modBus);
        ArcaneNetwork.register();

        if (ModList.get().isLoaded("enchantinginfuser")) {
            UltimateInfuserIntegration.register(modBus);
            LOGGER.info("已加载：奥术附魔台、终极奥术附魔台（Enchanting Infuser 联动已启用，行为补丁校验通过）");
        } else {
            LOGGER.info("已加载：奥术附魔台；未检测到 Enchanting Infuser，终极奥术附魔台与相关功能已跳过");
        }
    }
}
