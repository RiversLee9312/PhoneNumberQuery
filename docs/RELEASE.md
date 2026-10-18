# Release 1.0.0

日期：2026-10-18。

公开仓库：[RiversLee9312/PhoneNumberQuery](https://github.com/RiversLee9312/PhoneNumberQuery)。
发布页：[v1.0.0](https://github.com/RiversLee9312/PhoneNumberQuery/releases/tag/v1.0.0)。

## 签名与构建

- 包名：`com.sariv.numberlookup`。
- 最低版本 Android 8.0 / API 26，目标 API 36。
- Release 使用独立 RSA 4096 发布密钥，启用 R8 和资源压缩，未启用 debuggable。
- APK Signature Scheme v2 验证通过。
- 发布证书 SHA-256：`12e96f5ead7477a07f31d21105c4f1f191067f4392927da62fbd33d03be4575e`。
- 私钥与密码配置仅保存在发布者本机，未纳入 Git。仓库中仅保留无密钥的配置示例。

发布附件为 `NumberLookup-1.0.0-release.apk` 及对应 SHA-256 校验文件。
APK 文件哈希以 Release 附件为准，与证书指纹是不同的校验项。

此前调试版与正式版签名不同，首次安装正式版时需卸载调试版。后续正式版更新使用同一发布密钥。

## 验证

- 25 个 locale、57 个共享文案键的 XML、占位符与 locale config 校验通过。
- 8 项数据层单元测试通过，包括 245 个地区、489 个号码样例。
- 一加 11（PHB110，Android 16）上 12 项真机测试通过，包括新增的项目和第三方许可证页面导航、Apache-2.0 与 MIT 正文显示。
- Debug 与 Release 的 Lint 均为 0 个错误。
- 检查 Release APK 内全部 1,426 个 Google 号码、地理和运营商数据资源，与所选 Maven 上游 JAR 逐字节一致。
- 检查中国号段数据库与许可证资产均被完整打包，中国数据与原文件逐字节一致。

正式包另在一加 11 上验证中国与英国号码查询，以及项目和 MIT 许可证页面。公开发布后再次下载 APK 并校验 SHA-256。

## 开源信息

项目采用 Apache-2.0。中国号段数据库保留 MIT License；其余第三方组件、完整许可证和版权信息见 [THIRD_PARTY_NOTICES.md](../THIRD_PARTY_NOTICES.md)。应用内入口为“关于 → 开源许可证”。
