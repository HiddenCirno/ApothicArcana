package com.apothinfuser.client;

import com.apothinfuser.ApothInfuser;
import com.apothinfuser.client.gui.ArcaneEnchantScreen;
import com.apothinfuser.client.gui.UltimateArcaneEnchantingScreen;
import com.apothinfuser.integration.UltimateInfuserIntegration;
import com.apothinfuser.registry.ModRegistry;
import dev.shadowsoffire.apotheosis.adventure.affix.reforging.ReforgingTableTileRenderer;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
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
            MenuScreens.register(ModRegistry.ARCANE_ENCHANTING_MENU.get(), ArcaneEnchantScreen::new);
            // 终极台是可选联动：没有灌注台时既没有它的 MenuType，也不该加载它的 Screen 类。
            if (ModList.get().isLoaded("enchantinginfuser")) {
                MenuScreens.register(UltimateInfuserIntegration.ULTIMATE_ARCANE_ENCHANTING_MENU.get(),
                        UltimateArcaneEnchantingScreen::new);
            }
        });
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // 两个台子共用同一套书本渲染实现，只是书册贴图与"是否浮起待附魔物品"不同。
        // 自己实现的原因：原版渲染器的书册贴图是硬编码单例，且泛型参数只认原版方块实体类型。
        event.registerBlockEntityRenderer(ModRegistry.ARCANE_ENCHANTING_TABLE_BLOCK_ENTITY.get(),
                ArcaneTableRenderer::arcaneTable);
        if (ModList.get().isLoaded("enchantinginfuser")) {
            event.registerBlockEntityRenderer(
                    UltimateInfuserIntegration.ULTIMATE_ARCANE_ENCHANTING_TABLE_BLOCK_ENTITY.get(),
                    ArcaneTableRenderer::ultimateTable);
        }
        // 极限重铸台：世界里的锤子动画由神化的渲染器画，动画进度来自
        // ReforgingTableTile.clientTick 推的 time/step1（见 ModRegistry 里 BE 类型的注释）。
        // 我们的方块实体类型因此声明为 BlockEntityType<ReforgingTableTile>，泛型正好匹配。
        event.registerBlockEntityRenderer(ModRegistry.ULTIMATE_REFORGING_TABLE_BLOCK_ENTITY.get(),
                context -> new ReforgingTableTileRenderer());
    }
}
