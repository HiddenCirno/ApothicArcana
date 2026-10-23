package com.apothinfuser.mixinplugin;

import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * 让 {@code apothinfuser.infuser.mixins.json} 只在装了 Enchanting Infuser 时生效。
 *
 * <p><b>为什么需要它：</b>那个配置里的两个 Mixin 目标都是灌注台的类。
 * 用 {@code required: false} 虽然能避免"目标不存在"时的启动崩溃，但 Mixin 仍会尝试
 * 解析目标类并打一串 {@code Error loading class} 警告；更重要的是，一旦以后有人把
 * {@code required} 改回 true，缺灌注台的环境就会直接启动失败。
 * <p>
 * 插件在应用前就返回 false，Mixin <b>完全不会去加载目标类</b>——这才是"可选联动"该有的样子。
 * <p>
 * 配置本身仍保留 {@code required: false} 作为兜底：万一插件判断失败（例如极早期
 * {@code ModList} 尚未就绪），最坏结果也只是跳过补丁，而不是让整个模组加载失败。
 * 真正的失败检测放在 {@code UltimateInfuserIntegration#verifyMixinsApplied()}：
 * 那只在"确认装了灌注台"的分支里执行，失败会响亮地抛异常。
 */
public class InfuserMixinPlugin implements IMixinConfigPlugin {

    private static boolean infuserPresent() {
        // 常规路径：模组列表已经就绪（绝大多数 mixin 应用都发生在这个阶段之后）。
        ModList modList = ModList.get();
        if (modList != null) {
            return modList.isLoaded("enchantinginfuser");
        }
        // 极早期兜底：ModList 还没建好，改用加载期的 ModFile 列表。
        var loadingModList = FMLLoader.getLoadingModList();
        return loadingModList != null && loadingModList.getModFileById("enchantinginfuser") != null;
    }

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return infuserPresent();
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
