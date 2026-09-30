package com.apothinfuser.mixin;

import com.apothinfuser.world.inventory.MutableSlotPos;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 去掉 {@code Slot.x/y} 的 final 修饰符，让槽位坐标可以在构造之后重排。
 * <p>
 * {@code @Mutable} 是必须的：{@code AccessorGeneratorFieldSetter} 生成写入代码前会检查
 * {@code isDecoratedMutable()}，没有它就直接报
 * {@code "... for final field ... is not @Mutable"}。加上它才允许写 final 字段。
 * <p>
 * 本 Mixin <b>不改变任何原有行为</b>：只是把两个字段从"只读"变成"可写"，
 * 没有任何代码会去写它们，除非显式调用 {@code MutableSlotPos}。
 * 因此灌注台自己的界面完全不受影响。
 * <p>
 * <b>⚠️ 为什么是 {@code @Accessor} 而不是 {@code @Mutable @Shadow}（这里翻过车）：</b>
 * 两种写法在 dev 里都正常，但<b>发布版只认前者</b>。
 * <p>
 * {@code @Shadow} 成员在运行时由 {@code MixinPreProcessorStandard.attachFields} 处理，
 * 它走的是 {@code MixinTargetContext.findRemappedField} → 环境的 {@code RemapperChain}——
 * <b>而 refmap 压根不在那条链上</b>（整包扫过，实现 {@code mapFieldName} 的只有
 * {@code RemapperAdapter} / {@code RemapperChain} / {@code MixinTargetContext}）。
 * 于是生产环境里 {@code @Shadow int x} 只能靠**名字直接命中**目标字段，
 * 而目标字段在那儿叫 {@code f_40220_} —— 找不到，整个模组在启动时崩掉
 * （{@code @Shadow field x was not located ... No refMap loaded. / Using refmap ...}）。
 * dev 之所以看不出问题：那边的字段本来就叫 {@code x}，直接命中，refmap 一次都没被查过。
 * <p>
 * {@code @Accessor} 走的则是 {@code InvokerInfo.getTargetName}：
 * <pre>
 *   mixin.getReferenceMapper().remap(mixin.getClassRef(), specifiedName)
 * </code></pre>
 * ——类名用**混入类**、名字用注解括号里那个，正好是 refmap 的两个键。
 * 也就是说 {@code @Accessor} 是这条链上**唯一会查 refmap** 的写法，
 * 神化自己的 {@code ThrownTridentMixin} 取 {@code tridentItem} 用的也是它。
 */
@Mixin(Slot.class)
public abstract class SlotMixin implements MutableSlotPos {

    @Mutable
    @Accessor("x")
    public abstract void apothinfuser$setX(int x);

    @Mutable
    @Accessor("y")
    public abstract void apothinfuser$setY(int y);

    @Override
    public void apothinfuser$setPos(int x, int y) {
        this.apothinfuser$setX(x);
        this.apothinfuser$setY(y);
    }
}
