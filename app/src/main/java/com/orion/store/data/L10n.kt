package com.orion.store.data

/**
 * 简体中文显示映射层（Orion Store 中文版）。
 *
 * 目录数据（apps.json）里的分类名、更新频道名、排序名既是显示文案又是逻辑键：
 * 直接替换会破坏过滤、版本解析与持久化状态，因此这里保留英文键、仅在 UI 渲染时映射为中文。
 * 未收录的值原样返回，保证远程目录新增分类时界面不会出现空白。
 */

private val CAT_ZH: Map<String, String> = mapOf(
    "All" to "全部",
    "Activator" to "激活器",
    "Automation" to "自动化",
    "Browser" to "浏览器",
    "Creative/Design" to "创意/设计",
    "Creative/Multimedia" to "创意/多媒体",
    "Customization" to "个性化",
    "Customization/Utility" to "个性化/工具",
    "Developer/Utility" to "开发/工具",
    "Development" to "开发",
    "Development/Utility" to "开发/工具",
    "Educational" to "教育",
    "Emulator" to "模拟器",
    "Entertainment" to "娱乐",
    "Gaming/Utility" to "游戏/工具",
    "Graphics" to "图形图像",
    "Health" to "健康",
    "Health & Fitness" to "健康与健身",
    "Media" to "影音媒体",
    "Multimedia" to "多媒体",
    "Multimedia/Creative" to "多媒体/创意",
    "Multimedia/Utility" to "多媒体/工具",
    "Music" to "音乐",
    "Novel Reader" to "小说阅读",
    "Photography" to "摄影",
    "Privacy" to "隐私安全",
    "Productivity" to "效率办公",
    "Productivity/Communications" to "效率/通讯",
    "Productivity/Utility" to "效率/工具",
    "Reading" to "阅读",
    "Security" to "安全",
    "Security/Utility" to "安全/工具",
    "Social" to "社交",
    "Sports" to "体育",
    "System" to "系统",
    "System Mod" to "系统修改",
    "System Monitoring" to "系统监控",
    "System Monitoring/Optimization" to "系统监控/优化",
    "System Monitoring/Utility" to "系统监控/工具",
    "System Optimization" to "系统优化",
    "System Utilities" to "系统工具",
    "System/File Management" to "系统/文件管理",
    "Utility" to "实用工具",
    "Utility/Automation" to "工具/自动化",
    "Utility/Data Recovery" to "工具/数据恢复",
    "Utility/Installer" to "工具/安装器",
    "Utility/Networking" to "工具/网络",
    "Video" to "视频",
    "Video/Streaming" to "视频/流媒体",
    "Wallpaper" to "壁纸",
    "health" to "健康",
    "utility" to "实用工具",
    // 预设分类卡片（Logic.kt PRESETS 的 label）
    "Movies" to "影视",
    "Anime" to "动漫",
    "Developer" to "开发",
    "Utilities" to "实用工具",
)

/** 分类名显示文本：已知分类映射为中文，未知分类原样返回。 */
fun catLabel(c: String?): String {
    val raw = c?.trim().orEmpty()
    if (raw.isEmpty()) return "全部"
    return CAT_ZH[raw] ?: CAT_ZH[raw.lowercase()] ?: raw
}

private val STREAM_ZH: Map<String, String> = mapOf(
    "Stable" to "稳定版",
    "Beta" to "测试版",
    "Alpha" to "内测版",
    "Nightly" to "每夜版",
)

/** 更新频道名显示文本（键保持英文不变）。 */
fun streamLabel(s: String?): String {
    val raw = s?.trim().orEmpty()
    if (raw.isEmpty()) return "稳定版"
    return STREAM_ZH[raw] ?: raw.replaceFirstChar { it.uppercase() }.let { STREAM_ZH[it] ?: raw }
}

/** 平台名显示文本（Tab/Platform 的枚举名或 JSON 值）。 */
fun platformLabel(p: String?): String = when (p?.trim()?.lowercase()) {
    "android" -> "安卓"
    "pc", "windows" -> "电脑"
    "tv" -> "电视"
    else -> p.orEmpty()
}

/** 版本/大小兜底值的显示文本（数据键保持英文）。 */
fun versionLabel(v: String?): String = when (v?.trim()) {
    "Latest" -> "最新"
    "Varies" -> "视设备而定"
    "View on GitHub" -> "在 GitHub 上查看"
    "Unknown" -> "未知"
    else -> v.orEmpty()
}

/** 徽章键的显示文本。 */
private val BADGE_ZH: Map<String, String> = mapOf(
    "Legend" to "传奇", "Guardian" to "守护者", "Void Walker" to "虚空行者", "Supernova" to "超新星",
    "Backer" to "赞助者", "Dev Mode" to "开发者模式", "Dino Rookie" to "恐龙新兵", "Dino Master" to "恐龙大师",
    "Dino Legend" to "恐龙传奇", "Merge Tamer" to "合并驯手", "Spaghetti Slayer" to "面条杀手",
    "Sudo Sovereign" to "Sudo 君主", "Bronze Wings" to "青铜之翼", "Silver Wings" to "白银之翼", "Gold Wings" to "黄金之翼",
    "Cosmic Guardian" to "宇宙守护者", "Entropy" to "熵",
)

fun badgeLabel(b: String?): String = b?.let { BADGE_ZH[it] ?: it }.orEmpty()

/** 精简风险/扫描状态的显示文本。 */
fun riskLabel(r: String?): String = when (r?.trim()?.uppercase()) {
    "RECOMMENDED" -> "推荐移除"
    "ADVANCED" -> "进阶"
    "EXPERT" -> "专家"
    "UNSAFE" -> "危险"
    "UNTESTED" -> "未测试"
    "CRITICAL" -> "高危"
    "WARNING" -> "警告"
    "SAFE" -> "安全"
    "UNKNOWN" -> "未知"
    else -> r.orEmpty()
}

/** VirusTotal 引擎判定的显示文本。 */
fun vtCatLabel(c: String?): String = when (c?.trim()?.lowercase()) {
    "malicious" -> "恶意"
    "suspicious" -> "可疑"
    "undetected" -> "未检测"
    "harmless" -> "安全"
    "timeout" -> "超时"
    else -> c.orEmpty()
}

/** 主题值的显示文本。 */
fun themeLabel(t: String?): String = when (t?.trim()?.lowercase()) {
    "light" -> "浅色"
    "dusk" -> "暮色"
    "dark" -> "深色"
    "oled" -> "纯黑"
    else -> t.orEmpty()
}

/** 排行榜职业的显示文本。 */
fun classLabel(c: String?): String = when (c?.trim()) {
    "Warrior" -> "武士"
    "Scribe" -> "学者"
    "Hybrid" -> "混合"
    else -> c.orEmpty()
}
