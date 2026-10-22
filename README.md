<p align="center">
  <img src="assets/m3-hero-light.svg" alt="Orion Store 简体中文汉化版" width="80%">
</p>

<h1 align="center">Orion Store 简体中文汉化版</h1>

<p align="center">
  基于 <a href="https://github.com/RookieEnough/Orion-Store">RookieEnough/Orion-Store</a> v2.2.5 的<strong>完整简体中文本地化</strong>版本。<br>
  界面、引导、设置、通知、对话框全部汉化，默认语言即简体中文，无需任何设置。
</p>

<p align="center">
  <a href="#-下载"><img src="https://img.shields.io/badge/下载-最新_APK-7478F8?style=for-the-badge&logo=android&logoColor=white" alt="Download"></a>
  <a href="https://github.com/RookieEnough/Orion-Store/releases"><img src="https://img.shields.io/github/v/release/RookieEnough/Orion-Store?style=for-the-badge&logo=github&label=上游版本&color=A3E635" alt="Upstream"></a>
  <a href="https://www.gnu.org/licenses/gpl-3.0"><img src="https://img.shields.io/badge/License-GPL--3.0-blue?style=for-the-badge&logo=gnu" alt="GPL-3.0"></a>
</p>

---

## 📖 关于本仓库

本仓库是 [Orion Store](https://github.com/RookieEnough/Orion-Store) 的非官方简体中文汉化项目。Orion Store 是一个完全开源、无广告、无跟踪器的安卓应用商店，聚焦开源应用与 ReVanced / Morphe 修改版应用的分发。

本汉化版在上游 v2.2.5 的基础上，对应用内所有用户可见文本进行了完整的简体中文本地化，包括：

- **全部界面文案**：底部导航（应用 / 电脑 / 电视 / 我的应用 / 开发者）、顶栏、搜索框、排序与筛选
- **应用详情页**：版本、大小、开发者、包名、来源与安全性、版本渠道（稳定版 / 测试版 / 内测版 / 夜间版）、架构选择
- **下载与安装**：下载队列、全部更新、静默安装、无线调试配对引导（分步中文提示）
- **系统通知**：下载进度、下载完成、更新就绪、配对向导通知、自动更新报告
- **设置中心**：八个板块全部汉化（下载、外观、网络、存储、Orion 特性、备份、界面、开发者）
- **守护者与哨兵**：权限管家、APK 外科医生、系统精简、快速/深度扫描、网络哨兵的全部说明
- **首次引导与彩蛋**：新界面教程、任务引导、常见问题（FAQ 全文翻译）、Field Guide 使用指南全部章节
- **游戏化系统**：飞行员卡、经验值等级、徽章档案、《为代码加油》任务剧情（全部 51 张故事卡）、恐龙跑酷
- **AI 助手**：Orion AI 界面与设置汉化，并让 AI 默认使用简体中文回答

> 应用目录（应用名称、简介、截图等）来自上游远程数据源，这部分内容保持原样，不受汉化影响。

## 📥 下载

前往 [Releases](../../releases) 页面下载最新签名的 `Orion-Store-x.x.x-zh-CN.apk`，安装即可，界面直接就是中文。

- 最低支持：Android 7.0（API 24）
- 支持架构：arm64-v8a / armeabi-v7a
- 支持设备：手机、平板、Android TV

## 🔧 构建说明

```bash
git clone https://github.com/<你的用户名>/Orion-Store-ZH.git
cd Orion-Store-ZH
./gradlew :app:assembleRelease
```

- 需要 JDK 21 与 Android SDK（compileSdk 37）
- 未配置签名时 Release 包自动使用 debug 签名；如需自有签名，参照 CI 工作流提供 `keystore.properties`

## ⚖️ 授权与致谢

- 原项目：**[RookieEnough/Orion-Store](https://github.com/RookieEnough/Orion-Store)** — 由 [RookieZ](https://github.com/RookieEnough) 开发与维护
- 原项目采用 **[GPL-3.0](LICENSE)** 授权，本汉化版同样以 GPL-3.0 开源
- **修改声明**：本仓库仅修改了界面文本资源（Kotlin 源码中的字符串字面量），未改动任何业务逻辑、网络行为或数据收集行为；所有修改均可在 Commit 历史中审查
- 如果你觉得 Orion Store 好用，请去给[原项目](https://github.com/RookieEnough/Orion-Store)点个 Star，或通过原项目的 [Ko-fi](https://ko-fi.com/rookie_z) 支持作者

## 🙏 免责声明

本项目与上游作者无隶属关系。汉化版仅供参考学习使用，因使用本仓库构建的应用所产生的任何问题由使用者自行承担。请以[上游官方发布](https://github.com/RookieEnough/Orion-Store/releases)为准获取最新功能与安全更新。
