package com.apothinfuser.client;

import com.apothinfuser.ApothInfuser;
import com.apothinfuser.client.gui.ArcaneEnchantScreen;
import com.apothinfuser.client.gui.UltimateArcaneEnchantingScreen;
import com.apothinfuser.registry.ModRegistry;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * 客户端专属注册。
 * <p>
 * 单独一个类 + {@code value = Dist.CLIENT}：Forge 在服务端会跳过加载本类，
 * 因此 {@code Screen}/{@code MenuScreens} 这类客户端专属类型不会被服务端碰触
 * （否则会 NoClassDefFoundError）。
 * <p>
 * {@code MenuScreens.register} 必须在 {@code enqueueWork} 里执行——该事件是并行分发的。
 */
@Mod.EventBusSubscriber(modid = ApothInfuser.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ApothInfuserClient {

    private ApothInfuserClient() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(ModRegistry.ULTIMATE_ARCANE_ENCHANTING_MENU.get(), UltimateArcaneEnchantingScreen::new);
            MenuScreens.register(ModRegistry.ARCANE_ENCHANTING_MENU.get(), ArcaneEnchantScreen::new);
        });
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // 两个台子共用同一套书本渲染实现，只是书册贴图与"是否浮起待附魔物品"不同。
        // 自己实现的原因：原版渲染器的书册贴图是硬编码单例，且泛型参数只认原版方块实体类型。
        event.registerBlockEntityRenderer(ModRegistry.ARCANE_ENCHANTING_TABLE_BLOCK_ENTITY.get(),
                ArcaneTableRenderer::arcaneTable);
        event.registerBlockEntityRenderer(ModRegistry.ULTIMATE_ARCANE_ENCHANTING_TABLE_BLOCK_ENTITY.get(),
                ArcaneTableRenderer::ultimateTable);
    }
}
