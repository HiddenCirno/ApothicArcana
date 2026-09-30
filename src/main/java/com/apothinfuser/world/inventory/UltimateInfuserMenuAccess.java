package com.apothinfuser.world.inventory;

import net.minecraft.server.level.ServerPlayer;

/**
 * 由 {@code InfuserMenuMixin} 实现在 {@code InfuserMenu} 上。
 * <p>
 * 用途：把"需要方块/世界上下文"的操作留在 Mixin 内部完成——包处理器只拿得到
 * {@code InfuserMenu}，而容器、坐标、玩家在它内部都是 private。
 * <p>
 * <b>刻意不放在 {@code com.apothinfuser.mixin} 包。</b>Mixin 禁止从外部直接引用
 * mixin 包（{@code apothinfuser.mixins.json} 里声明的那个包）中的类，
 * 会抛 {@code IllegalClassLoadError: ... cannot be referenced directly}。
 * 带 {@code @Mixin} 注解的接口（如 {@code InfuserMenuInvoker}）是例外，因为 Mixin 会为它生成实体类。
 */
public interface UltimateInfuserMenuAccess {

    /** 把当前三个滑块值写回方块实体（若不是本模组的台子则什么也不做） */
    void apothinfuser$persistSliderValues();

    /**
     * 用当前滑块值重新匹配灌注配方并同步给客户端。
     * 滑块变化后必须调用——配方窗口由三维决定，拨动滑块就可能进出窗口。
     */
    void apothinfuser$refreshInfusion();

    /**
     * 真正执行灌注。服务端会<b>重新匹配</b>而不是信任客户端传来的结果，
     * 因此客户端无法伪造配方。成功返回 true。
     */
    boolean apothinfuser$performInfusion(ServerPlayer player);
}
