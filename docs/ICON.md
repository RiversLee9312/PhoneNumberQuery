# 应用图标

使用内置 GPT Image 图像生成工具（imagegen 技能）生成透明 PNG，并复制到 `app/src/main/res/drawable-nodpi/launcher_mark.png`。项目引用工作区内的副本，不依赖工具默认输出目录。

最终生成提示词：

> Use case: logo-brand. Asset type: production Android adaptive app icon foreground for a Chinese offline phone-number region lookup app named 号码归属地. Generate one refined, very simple Material 3 Expressive symbol: a single bold rounded location pin silhouette whose negative-space opening subtly reads as a miniature rounded telephone handset. Cohesive rounded geometric form, optical balance, premium restraint, flat solid dark forest green, crisp smooth edges. Transparent background, centered on square canvas. No text, no lettering, no digits, no border, no drop shadows, no gradients, no 3D, no scenery, no app tile, no mockup. The whole mark occupies approximately 54% of the square canvas width and height with generous transparent margin so it survives Android adaptive icon masks. It must remain legible as a single-color Android themed icon at 24px. Output only the finished symbol asset.

Android 26 起的 Adaptive Icon 将图案作为透明前景，背景独立使用浅绿色。Android 33 起显式提供 monochrome 图层，使用同一图案的透明度轮廓，交由启动器施加主题颜色。图案没有自行绘制外部启动器遮罩。

PNG 为 1254 × 1254 RGBA。主要可见图案（alpha > 64）的最大半径在 108dp 图层中约为 32.87dp，位于官方 66dp 直径安全圆内。主题图标存在性、透明背景和可见前景另通过真机测试验证。

界面图标来自 Google Fonts Material Symbols Rounded 官方仓库，保留 Apache 2.0 许可证；项目只修改 Vector Drawable 的主题属性命名空间，以用于原生 Compose 工程。
