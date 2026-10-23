package com.xunyyds.kelaile.data

import java.net.IDN
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateSourceTest {

    private fun info(vararg assets: ReleaseAsset) = ReleaseInfo(
        tag = "v2.6.0",
        name = "课来了 2.6.0",
        body = "- 新增更新检测",
        publishedAt = "2026-03-01T10:00:00Z",
        assets = assets.toList(),
    )

    private fun asset(name: String, size: Long = 10_000_000L) =
        ReleaseAsset(name, size, "https://github.com/xunyyds/kelaile/releases/download/v2.6.0/$name")

    @Test
    fun pickAsset_prefersApk() {
        val picked = UpdateSource.pickAsset(listOf(asset("checksums.txt"), asset("kelaile-2.6.0.apk")))
        assertEquals("kelaile-2.6.0.apk", picked?.name)
    }

    @Test
    fun pickAsset_prefersArm64Apk() {
        val picked = UpdateSource.pickAsset(
            listOf(
                asset("kelaile-x86_64.apk", 9_000_000L),
                asset("kelaile-arm64-v8a.apk", 8_000_000L),
            ),
        )
        assertEquals("kelaile-arm64-v8a.apk", picked?.name)
    }

    @Test
    fun pickAsset_fallsBackToAnyAssetWhenNoApk() {
        val picked = UpdateSource.pickAsset(listOf(asset("kelaile-2.6.0.apk.txt", 1L)))
        assertEquals("kelaile-2.6.0.apk.txt", picked?.name)
    }

    @Test
    fun pickAsset_emptyReturnsNull() {
        assertNull(UpdateSource.pickAsset(emptyList()))
    }

    @Test
    fun pickAsset_isDeterministicForEqualSizes() {
        val first = UpdateSource.pickAsset(listOf(asset("b.apk", 5L), asset("a.apk", 5L)))
        val second = UpdateSource.pickAsset(listOf(asset("a.apk", 5L), asset("b.apk", 5L)))
        assertEquals(first?.name, second?.name)
    }

    @Test
    fun mirrorUrl_prefixesFullGithubUrl() {
        val direct = "https://github.com/xunyyds/kelaile/releases/download/v2.6.0/kelaile.apk"
        assertEquals(
            "https://gh.07150721.xyz/$direct",
            UpdateSource.mirrorUrl("gh.07150721.xyz", direct),
        )
    }

    @Test
    fun mirrorUrl_appliesIdnToAscii() {
        val direct = "https://github.com/o/r/releases/download/v1/a.apk"
        val url = UpdateSource.mirrorUrl("ghf.无名氏.top", direct)
        val host = url.removePrefix("https://").substringBefore('/')
        assertEquals(IDN.toASCII("ghf.无名氏.top"), host)
        assertTrue(url.endsWith("/$direct"))
    }

    @Test
    fun downloadCandidates_ordersDirectThenMirrors() {
        val asset = asset("kelaile.apk")
        val candidates = UpdateSource.downloadCandidates(info(asset), asset)
        assertEquals(1 + UpdateSource.MIRRORS.size, candidates.size)
        assertEquals(asset.downloadUrl, candidates.first())
        assertTrue(candidates[1].startsWith("https://${IDN.toASCII("gh.07150721.xyz")}/"))
    }

    @Test
    fun directUrl_fallsBackToConstructedPath() {
        val missing = ReleaseAsset("kelaile.apk", 1L, "")
        assertEquals(
            "https://github.com/xunyyds/kelaile/releases/download/v2.6.0/kelaile.apk",
            UpdateSource.directUrl(info(missing), missing),
        )
    }

    @Test
    fun mirrors_containsExpectedHostsInOrder() {
        assertEquals(
            listOf(
                "gh.07150721.xyz",
                "ghpxy.hwinzniej.top",
                "ghf.无名氏.top",
                "gh.chjina.com",
                "js.jiangss.shop",
                "ghproxy.icu",
            ),
            UpdateSource.MIRRORS,
        )
    }
}
