package com.xunyyds.kelaile.data

/**
 * 版本号与强制更新规则。
 *
 * 「正式版本」指 GitHub latest 接口返回的正式 Release，本应用只以该接口的结果判定是否有新版本，
 * 因此这里只需回答两个问题：谁更新，以及当前安装版本距离「被跳过」的版本落后了几个正式版本。
 */
object VersionRules {

    /**
     * 强制更新阈值：落后达到 3 个正式版本即必须更新。
     *
     * 两条触发路径都在这里定义，避免散落在 UI 里：
     * 1. 最新正式版与当前安装版本相差 >= 3 个正式版本；
     * 2. 自 skippedVersion 起已错过 >= 3 个正式版本。
     */
    const val FORCE_AFTER = 3

    /** 从 tag 或版本名里解析出数字段。`v2.5.3`、`2.5.3`、`release-2.5.3`、`V2.5.3-beta` 都取 `2.5.3`。 */
    fun segments(raw: String?): List<Int> {
        if (raw.isNullOrBlank()) return emptyList()
        val match = Regex("\\d+(?:\\.\\d+)*").find(raw.trim()) ?: return emptyList()
        return match.value.split('.').mapNotNull { it.toIntOrNull() }
    }

    /** 规范化版本标签：优先保留数字段，解析不出时退回原始文本（截断），便于稳定比较与存储。 */
    fun normalize(raw: String?): String {
        val parts = segments(raw)
        return if (parts.isEmpty()) raw.orEmpty().trim().take(SKIPPED_VERSION_MAX) else parts.joinToString(".")
    }

    /**
     * 逐段比较两个版本。缺失的段按 0 补齐，因此 `2.5` 与 `2.5.0` 相等。
     * 返回正数表示 [left] 更新，负数表示 [right] 更新，0 表示相同或无法比较。
     */
    fun compare(left: String?, right: String?): Int {
        val a = segments(left)
        val b = segments(right)
        if (a.isEmpty() || b.isEmpty()) return 0
        val size = maxOf(a.size, b.size)
        for (i in 0 until size) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return if (x > y) 1 else -1
        }
        return 0
    }

    /** [candidate] 是否比 [current] 更新。无法解析时视为不更新，避免误弹窗。 */
    fun isNewer(candidate: String?, current: String?): Boolean = compare(candidate, current) > 0

    /**
     * 以 major 段为骨架，两个版本之间相隔了多少个「正式版本」。
     *
     * GitHub latest 只暴露最新一个正式版，无法枚举中间版本，因此用版本号的递增跨度近似计数：
     * - major 相同：取 minor 的差值（`2.5.3` → `2.8.0` 计 3 个正式版本）；
     * - major 不同：按每 100 个 minor 折一个 major 的保守估计（`2.5.3` → `3.0.0` 计 95 个），
     *   这样跨大版本一定会触发强制更新，而不是因为无法枚举而永远不强制。
     * patch 段不参与计数：同一 minor 内的补丁版本不足以构成「错过一个正式版本」。
     */
    fun releaseGap(from: String?, to: String?): Int {
        val a = segments(from)
        val b = segments(to)
        if (a.isEmpty() || b.isEmpty()) return 0
        if (compare(from, to) >= 0) return 0
        val majorA = a.getOrElse(0) { 0 }
        val minorA = a.getOrElse(1) { 0 }
        val majorB = b.getOrElse(0) { 0 }
        val minorB = b.getOrElse(1) { 0 }
        if (majorA == majorB) return (minorB - minorA).coerceAtLeast(0)
        return (majorB - majorA) * 100 + (minorB - minorA)
    }

    data class Decision(
        /** 是否展示更新弹窗。 */
        val show: Boolean,
        /** 是否强制：隐藏「此版本不再提示」，不可跳过。 */
        val forced: Boolean,
        /** 给用户看的判定原因，便于诊断为什么必须更新。 */
        val reason: String,
    )

    /**
     * 决定某次检查结果是否弹窗、以及是否强制。
     *
     * @param latest 最新正式版本标签；为空表示检查失败或没有版本信息，不弹窗。
     * @param current 当前安装版本。
     * @param skipped 用户上次选择「此版本不再提示」的版本。
     * @param manual 是否为用户手动点击「检测更新」。手动检测忽略跳过记录，总是展示结果。
     */
    fun decide(latest: String?, current: String?, skipped: String?, manual: Boolean): Decision {
        if (latest.isNullOrBlank()) return Decision(show = false, forced = false, reason = "没有可用的版本信息")
        if (!isNewer(latest, current)) return Decision(show = false, forced = false, reason = "已是最新版本")

        val behind = releaseGap(current, latest)
        val skippedGap = if (skipped.isNullOrBlank()) 0 else releaseGap(skipped, latest)

        val forcedByLatest = behind >= FORCE_AFTER
        val forcedBySkip = skippedGap >= FORCE_AFTER
        if (forcedByLatest || forcedBySkip) {
            return Decision(
                show = true,
                forced = true,
                reason = if (forcedByLatest) {
                    "最新正式版比当前版本新 $behind 个正式版本（达到 $FORCE_AFTER 个即强制更新）"
                } else {
                    "自跳过版本起已错过 $skippedGap 个正式版本（达到 $FORCE_AFTER 个即强制更新）"
                },
            )
        }

        val skippedThis = !skipped.isNullOrBlank() && compare(skipped, latest) == 0
        if (skippedThis && !manual) {
            return Decision(show = false, forced = false, reason = "该版本已被「此版本不再提示」跳过")
        }
        return Decision(show = true, forced = false, reason = "发现新版本")
    }
}
