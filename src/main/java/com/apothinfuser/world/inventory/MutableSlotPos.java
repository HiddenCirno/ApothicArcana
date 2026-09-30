package com.apothinfuser.world.inventory;

import net.minecraft.world.inventory.Slot;

/**
 * 让槽位坐标可改。
 * <p>
 * <b>为什么需要它：</b>原版 {@code Slot} 把坐标声明成了 {@code public final int x/y}——
 * 编译期就是常量，拿到槽位对象后<b>无法</b>再摆位置。而 {@code InfuserMenu} 的构造器里
 * 把 42 个槽位的位置全写死了（附魔槽、装备栏分左右两列、背包、快捷栏、盾牌），
 * 我们既要沿用它的槽位布局逻辑，又要把它们摆到我们自己设计的界面上。
 * <p>
 * <b>为什么不用 AccessTransformer：</b>AT 只在运行时生效，javac 编译时看到的仍是未打补丁的
 * jar，{@code slot.x = ...} 直接编译不过（这个坑在项目里已经踩过一次）。
 * <p>
 * <b>为什么不用 {@code @ModifyArgs} 改 {@code addSlot} 调用：</b>42 次调用散落在构造器和
 * {@code initCommon} 的循环里，靠 ordinal 定位极其易碎。改为「构造完再统一摆位置」，
 * 只需要一处 Mixin，且与 {@code InfuserMenu} 的内部结构解耦。
 * <p>
 * 实现见 {@code com.apothinfuser.mixin.SlotMixin}：用 {@code @Mutable @Shadow} 去掉 final 修饰符，
 * 再把写入能力通过本接口暴露出来（直接写 {@code slot.x} 会在 javac 处被 final 拦住）。
 */
public interface MutableSlotPos {

    /** 把槽位摆到指定坐标（相对界面左上角） */
    void apothinfuser$setPos(int x, int y);

    static void move(Slot slot, int x, int y) {
        ((MutableSlotPos) slot).apothinfuser$setPos(x, y);
    }
}
