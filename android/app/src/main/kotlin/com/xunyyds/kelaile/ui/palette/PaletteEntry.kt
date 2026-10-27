package com.xunyyds.kelaile.ui.palette

import com.xunyyds.kelaile.data.AppData
import com.xunyyds.kelaile.data.CourseSession

enum class PaletteKind(val label: String) {
    Course("课程"),
    Setting("设置"),
    Action("动作"),
}

val paletteKindOrder: List<PaletteKind> = listOf(PaletteKind.Course, PaletteKind.Setting, PaletteKind.Action)

data class PaletteEntry(
    val id: String,
    val kind: PaletteKind,
    val title: String,
    val subtitle: String = "",
    val keywords: String = "",
    val payload: String = "",
)

object PaletteActions {
    const val NEW_COURSE = "new-course"
    const val EDIT_PERIODS = "edit-periods"
    const val BROWSER_IMPORT = "browser-import"
    const val IMPORT_FILE = "import-file"
    const val EXPORT_FILE = "export-file"
    const val TOGGLE_THEME = "toggle-theme"
    const val OPEN_SETTINGS = "open-settings"
}

private data class SettingSeed(val title: String, val route: String, val path: String, val keywords: String = "")

private val paletteSettings: List<SettingSeed> = listOf(
    SettingSeed("外观", "appearance", "设置 · 外观", "主题 莫奈 底栏 布局"),
    SettingSeed("明暗模式", "appearance", "外观", "深色 浅色 跟随系统 黑暗 夜间"),
    SettingSeed("主题颜色", "palette", "外观 · 主题颜色", "莫奈 动态色 壁纸 预设 取色"),
    SettingSeed("从系统壁纸取色", "palette", "外观 · 主题颜色", "莫奈 动态色"),
    SettingSeed("底栏样式", "appearance", "外观", "默认 悬浮 液态玻璃 导航"),
    SettingSeed("课程表外观", "grid", "外观 · 课程表外观", "背景 颜色 网格线 边框"),
    SettingSeed("课程表背景", "grid", "外观 · 课程表外观", "背景图 自定义 图片"),
    SettingSeed("表格颜色", "grid", "外观 · 课程表外观", "表头 单元格 底色"),
    SettingSeed("表格文字颜色", "grid", "外观 · 课程表外观", "文字 颜色 字色"),
    SettingSeed("格子边框颜色", "grid", "外观 · 课程表外观", "边框 网格线 颜色"),
    SettingSeed("网格线样式", "grid", "外观 · 课程表外观", "虚线 透明线 无样式 网格"),
    SettingSeed("显示设置", "display", "设置 · 显示设置", "格内 老师 @ 对齐"),
    SettingSeed("地点前显示 @", "display", "显示设置", "地点 @ 前缀"),
    SettingSeed("格子内显示老师", "display", "显示设置", "老师 教师 姓名"),
    SettingSeed("竖直对齐", "display", "显示设置", "格内 居中 靠上 靠下"),
    SettingSeed("显示布局", "layout", "外观 · 显示布局", "字体 密度"),
    SettingSeed("字体大小", "layout", "外观 · 显示布局", "文字 缩放"),
    SettingSeed("课表密度", "layout", "外观 · 显示布局", "显示 间距"),
    SettingSeed("动效设置", "motion", "外观 · 动效设置", "动画 速度 转场"),
    SettingSeed("动画速度", "motion", "外观 · 动效设置", "快慢 动效"),
    SettingSeed("减少动画", "motion", "外观 · 动效设置", "动效 无障碍 位移 缩放"),
    SettingSeed("转场风格", "motion", "外观 · 动效设置", "动画 标准 共享轴 淡入淡出"),
    SettingSeed("学期与课表", "semester", "设置 · 学期与课表", "学校 日期 时间"),
    SettingSeed("学校名称", "semester", "学期与课表", "学校 导入"),
    SettingSeed("学期日期", "calendar", "学期与课表 · 学期日期"),
    SettingSeed("学期开始", "calendar", "学期与课表 · 学期日期", "开学 日期"),
    SettingSeed("学期结束", "calendar", "学期与课表 · 学期日期", "放假 日期"),
    SettingSeed("周次与星期", "week", "学期与课表 · 周次与星期"),
    SettingSeed("总周数", "week", "学期与课表 · 周次与星期", "周次 星期"),
    SettingSeed("当前周", "week", "学期与课表 · 周次与星期", "周次 星期"),
    SettingSeed("周起始日", "week", "学期与课表 · 周次与星期", "周次 星期"),
    SettingSeed("显示开始星期", "week", "学期与课表 · 周次与星期", "周次 星期"),
    SettingSeed("显示结束星期", "week", "学期与课表 · 周次与星期", "周次 星期"),
    SettingSeed("节次时间", "semester", "学期与课表", "上课 下课 时间"),
    SettingSeed("课程", "courses", "设置 · 课程", "管理"),
    SettingSeed("新增课程", "courses", "课程", "添加"),
    SettingSeed("默认课程颜色", "courses", "课程", "颜色 色板"),
    SettingSeed("上课提醒", "reminders", "设置 · 上课提醒", "通知 权限 闹钟"),
    SettingSeed("提前分钟", "reminders", "上课提醒", "通知 提前时间"),
    SettingSeed("数据与备份", "data", "设置 · 数据与备份", "数据 恢复 备份 导入 导出"),
    SettingSeed("应用数据备份", "data", "数据与备份", "全量 备份 JSON 设置 备注"),
    SettingSeed("应用数据恢复", "data", "数据与备份", "全量 恢复 JSON 覆盖"),
    SettingSeed("课表导入", "data", "数据与备份", "Excel xlsx 表格 导入"),
    SettingSeed("课表导出", "data", "数据与备份", "Excel xlsx 表格 导出"),
    SettingSeed("教务网址", "data", "数据与备份", "学校 链接 URL"),
    SettingSeed("浏览器导入课表", "data", "数据与备份", "教务 自动识别 桌面模式"),
    SettingSeed("关于与更新", "about", "设置 · 关于", "版本 系统 开源 GitHub"),
    SettingSeed("检测更新", "about", "关于与更新", "更新 版本 Release GitHub"),
    SettingSeed("开源许可", "about", "关于与更新", "许可 License 开源"),
    SettingSeed("分享应用", "about", "关于与更新", "APK 安装包 分享"),
)

val paletteActions: List<PaletteEntry> = listOf(
    PaletteEntry(
        id = "action:" + PaletteActions.NEW_COURSE, kind = PaletteKind.Action, title = "新增课程",
        subtitle = "创建一门课程并排进课表", keywords = "添加 创建 新建 排课", payload = PaletteActions.NEW_COURSE,
    ),
    PaletteEntry(
        id = "action:" + PaletteActions.BROWSER_IMPORT, kind = PaletteKind.Action, title = "打开浏览器导入",
        subtitle = "桌面模式 · 自动识别学校", keywords = "教务 网页 抓取 登录", payload = PaletteActions.BROWSER_IMPORT,
    ),
    PaletteEntry(
        id = "action:" + PaletteActions.TOGGLE_THEME, kind = PaletteKind.Action, title = "切换明暗模式",
        subtitle = "跟随系统 → 深色 → 浅色", keywords = "主题 夜间 白天 黑暗", payload = PaletteActions.TOGGLE_THEME,
    ),
    PaletteEntry(
        id = "action:" + PaletteActions.EXPORT_FILE, kind = PaletteKind.Action, title = "导出课表文件",
        subtitle = "备份课程、学校与设置", keywords = "保存 JSON 备份 分享", payload = PaletteActions.EXPORT_FILE,
    ),
    PaletteEntry(
        id = "action:" + PaletteActions.IMPORT_FILE, kind = PaletteKind.Action, title = "导入课表文件",
        subtitle = "从 Excel 表格导入课表", keywords = "恢复 Excel xlsx 导入", payload = PaletteActions.IMPORT_FILE,
    ),
    PaletteEntry(
        id = "action:" + PaletteActions.EDIT_PERIODS, kind = PaletteKind.Action, title = "编辑节次时间",
        subtitle = "调整每节课的上课与下课时间", keywords = "作息 时间表", payload = PaletteActions.EDIT_PERIODS,
    ),
    PaletteEntry(
        id = "action:" + PaletteActions.OPEN_SETTINGS, kind = PaletteKind.Action, title = "打开设置",
        subtitle = "进入全部设置项", keywords = "偏好 配置", payload = PaletteActions.OPEN_SETTINGS,
    ),
)

private val paletteWeekdays = listOf("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")

private fun courseSubtitle(course: CourseSession): String {
    val parts = mutableListOf<String>()
    paletteWeekdays.getOrNull(course.weekday - 1)?.let { parts += it }
    parts += "第 ${course.startPeriod}—${course.endPeriod} 节"
    course.teacher.takeIf { it.isNotBlank() }?.let { parts += it }
    course.location.takeIf { it.isNotBlank() }?.let { parts += it }
    return parts.joinToString(" · ")
}

fun courseEntries(courses: List<CourseSession>): List<PaletteEntry> = courses.map { course ->
    PaletteEntry(
        id = "course:${course.id}",
        kind = PaletteKind.Course,
        title = course.name,
        subtitle = courseSubtitle(course),
        keywords = "${course.teacher} ${course.location} 课程 课表",
        payload = course.id,
    )
}

fun settingEntries(): List<PaletteEntry> = paletteSettings.map { seed ->
    PaletteEntry(
        id = "setting:${seed.title}",
        kind = PaletteKind.Setting,
        title = seed.title,
        subtitle = seed.path,
        keywords = seed.keywords,
        payload = seed.route,
    )
}

fun paletteEntries(data: AppData): List<PaletteEntry> =
    courseEntries(data.courses) + settingEntries() + paletteActions
