package com.xunyyds.kelaile.data

import java.net.IDN

/**
 * 更新包选取与下载地址构造。
 *
 * 所有规则集中在这里，UI 与网络层只调用本对象，避免散落在各处难以核对。
 */
object UpdateSource {

    const val API_LATEST = "https://api.github.com/repos/xunyyds/kelaile/releases/latest"

    const val RELEASE_PAGE = "https://github.com/xunyyds/kelaile/releases/latest"

    /** 直连失败后按顺序尝试的镜像主机。 */
    val MIRRORS: List<String> = listOf(
        "gh.07150721.xyz",
        "ghpxy.hwinzniej.top",
        "ghf.无名氏.top",
        "gh.chjina.com",
        "js.jiangss.shop",
        "ghproxy.icu",
    )

    /**
     * 从 assets 里选安装包。
     *
     * 规则（按优先级）：`.apk` 优先；同为 apk 时优先 arm64 架构包，其次与当前 ABI 无关的通用包；
     * 仍未决时取文件名排序靠前的一个，保证同一份 Release 每次选到同一个包。
     */
    fun pickAsset(assets: List<ReleaseAsset>): ReleaseAsset? {
        if (assets.isEmpty()) return null
        val apks = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
        val pool = apks.ifEmpty { assets }
        val arm = pool.filter { it.name.contains("arm64", ignoreCase = true) }
        val preferred = arm.ifEmpty { pool }
        return preferred.sortedWith(compareBy({ -it.sizeBytes }, { it.name })).firstOrNull()
    }

    /** 直连下载地址。优先用接口给出的 browser_download_url，缺失时按 tag/asset 拼。 */
    fun directUrl(info: ReleaseInfo, asset: ReleaseAsset): String {
        val url = asset.downloadUrl.trim()
        if (url.startsWith("https://") || url.startsWith("http://")) return url
        return "https://github.com/xunyyds/kelaile/releases/download/${info.tag}/${asset.name}"
    }

    /** 镜像地址：`https://镜像主机/ + 完整 GitHub 下载地址`。主机名做 IDN 归一，兼容中文域名。 */
    fun mirrorUrl(host: String, directUrl: String): String {
        val ascii = runCatching { IDN.toASCII(host.trim()) }.getOrElse { host.trim() }
        require(ascii.isNotBlank()) { "镜像主机为空" }
        return "https://$ascii/$directUrl"
    }

    /** 直连 + 全部镜像，按尝试顺序排列。 */
    fun downloadCandidates(info: ReleaseInfo, asset: ReleaseAsset): List<String> {
        val direct = directUrl(info, asset)
        return listOf(direct) + MIRRORS.map { mirrorUrl(it, direct) }
    }
}
