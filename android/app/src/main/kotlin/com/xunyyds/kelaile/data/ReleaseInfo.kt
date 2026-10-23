package com.xunyyds.kelaile.data

/** GitHub Release 的安装包资产。 */
data class ReleaseAsset(
    val name: String,
    val sizeBytes: Long,
    val downloadUrl: String,
)

/** 从 GitHub latest 接口解析出的正式版本信息。 */
data class ReleaseInfo(
    val tag: String,
    val name: String,
    val body: String,
    val publishedAt: String,
    val assets: List<ReleaseAsset>,
) {
    /** 去掉 tag 前缀后的可比较版本号，例如 `v2.6.0` → `2.6.0`。 */
    val version: String get() = VersionRules.normalize(tag)

    /** 展示用标题：Release 名称为空时退回版本号。 */
    val displayName: String get() = name.ifBlank { "v$version" }

    companion object {
        /**
         * 解析 GitHub `/releases/latest` 的 JSON。
         *
         * 只接受正式 Release：`draft` 与 `prerelease` 为真时视为不可用。接口本身只返回 latest，
         * 这里再做一次防御，避免上游字段异常时把预发布版当成正式版。
         */
        fun parse(json: String): ReleaseInfo {
            require(json.length <= MAX_JSON_CHARS) { "版本信息过大" }
            val root = org.json.JSONTokener(json).let { tokener ->
                val value = tokener.nextValue() as? org.json.JSONObject ?: error("版本接口返回的不是对象")
                require(tokener.nextClean() == '\u0000') { "版本接口返回内容多余" }
                value
            }
            if (root.has("draft") && root.optBoolean("draft", false)) error("最新版本仍为草稿")
            if (root.has("prerelease") && root.optBoolean("prerelease", false)) error("最新版本为预发布版")

            val tag = root.optString("tag_name").trim()
            require(tag.isNotEmpty()) { "版本接口缺少 tag_name" }
            require(VersionRules.segments(tag).isNotEmpty()) { "无法识别版本号：$tag" }

            val assets = root.optJSONArray("assets")
            val list = (0 until (assets?.length() ?: 0)).mapNotNull { index ->
                val item = assets?.optJSONObject(index) ?: return@mapNotNull null
                val name = item.optString("name").trim()
                val url = item.optString("browser_download_url").trim()
                if (name.isEmpty() || url.isEmpty()) return@mapNotNull null
                ReleaseAsset(name, item.optLong("size", 0L), url)
            }
            return ReleaseInfo(
                tag = tag,
                name = root.optString("name").orEmpty(),
                body = root.optString("body").orEmpty(),
                publishedAt = root.optString("published_at").orEmpty(),
                assets = list,
            )
        }

        private const val MAX_JSON_CHARS = 512_000
    }
}
