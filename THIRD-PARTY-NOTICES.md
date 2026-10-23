# 第三方声明 / Third-Party Notices

本项目在**运行时**依赖以下模组，但发布 jar **不打包**它们的代码或素材
（`asset/` 下只有 `apothinfuser:` 自己的资源；构建也未启用 Jar-in-Jar）。
各依赖的许可以其自身发布物为准。

---

## Apotheosis

本项目部分视觉素材以 Apotheosis 的素材为基础修改。
Apotheosis 的代码如下（含素材例外条款），全文照录：

```
MIT License

Copyright (c) 2023 Brennan Ward

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.

The following assets in src/main/resources/assets/textures/
* All files in /items/vial/
* The file /items/rune_base.png
* The file /items/sigil_of_enhancement.png
* The file /items/sigil_of_socketing.png
* The file /items/book.png
are Copyright (c) malcomriley, used under the terms of CC-BY-4.0
https://github.com/malcolmriley/unused-textures/blob/master/LICENSE
```

---

## malcomriley — 镶孔符石原素材

`assets/apothinfuser/textures/item/creative_sigil_of_socketing.png` 由
Apotheosis 的 `items/sigils/socketing.png` 修改而来。

- 原素材：Copyright (c) malcomriley
- 许可：CC-BY-4.0 — https://creativecommons.org/licenses/by/4.0/
- 修改：重新绘制 / 调色，用作「创造模式镶孔符石」

该文件按 **CC-BY-4.0** 分发，不适用本项目的 CC BY-NC-SA 3.0。

---

## Enchanting Infuser（可选联动）

- 代码：MPL-2.0（文件级 copyleft）
- 素材：All Rights Reserved

本项目不复制其代码，也不打包其素材；仅在检测到该模组时通过公开 API
与 Mixin 进行运行时联动。若未安装，本模组的其余功能不受影响。

---

## Placebo / Apothic Attributes (attributeslib)

- Placebo：MIT
- Apothic Attributes：见其发布物

均为 Apotheosis 的运行时前置，本项目未打包。

## PuzzlesLib

Enchanting Infuser 的运行时前置；本项目不直接引用其代码，
也未在 `mods.toml` 中声明依赖。

---

## Minecraft / Minecraft Forge

原版游戏与 Forge 的代码、素材版权归 Mojang Studios / Microsoft 及
Forge 开发团队所有；本项目未再分发它们。
