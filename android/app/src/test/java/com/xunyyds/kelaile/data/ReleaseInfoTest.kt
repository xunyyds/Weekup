package com.xunyyds.kelaile.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseInfoTest {

    private val json = """
        {
          "tag_name": "v2.6.0",
          "name": "课来了 2.6.0",
          "body": "- 新增更新检测\n- 设置页分组",
          "published_at": "2026-03-01T10:00:00Z",
          "draft": false,
          "prerelease": false,
          "assets": [
            {"name": "kelaile-arm64-v8a.apk", "size": 18000000,
             "browser_download_url": "https://github.com/xunyyds/kelaile/releases/download/v2.6.0/kelaile-arm64-v8a.apk"},
            {"name": "checksums.txt", "size": 500,
             "browser_download_url": "https://github.com/xunyyds/kelaile/releases/download/v2.6.0/checksums.txt"}
          ]
        }
    """.trimIndent()

    @Test
    fun parse_readsAllFields() {
        val info = ReleaseInfo.parse(json)
        assertEquals("v2.6.0", info.tag)
        assertEquals("2.6.0", info.version)
        assertEquals("课来了 2.6.0", info.displayName)
        assertEquals("2026-03-01T10:00:00Z", info.publishedAt)
        assertTrue(info.body.contains("新增更新检测"))
        assertEquals(2, info.assets.size)
        assertEquals(18_000_000L, info.assets.first().sizeBytes)
    }

    @Test
    fun parse_rejectsDraft() {
        val draft = json.replace("\"draft\": false", "\"draft\": true")
        val error = runCatching { ReleaseInfo.parse(draft) }.exceptionOrNull()
        assertTrue(error is IllegalStateException || error is IllegalArgumentException)
    }

    @Test
    fun parse_rejectsPrerelease() {
        val pre = json.replace("\"prerelease\": false", "\"prerelease\": true")
        assertTrue(runCatching { ReleaseInfo.parse(pre) }.isFailure)
    }

    @Test
    fun parse_rejectsMissingTag() {
        val missing = json.replace("\"v2.6.0\"", "\"\"")
        assertTrue(runCatching { ReleaseInfo.parse(missing) }.isFailure)
    }

    @Test
    fun parse_rejectsNonVersionTag() {
        val weird = json.replace("\"v2.6.0\"", "\"nightly\"")
        assertTrue(runCatching { ReleaseInfo.parse(weird) }.isFailure)
    }

    @Test
    fun parse_toleratesMissingOptionalFields() {
        val minimal = """{"tag_name":"v2.6.0"}"""
        val info = ReleaseInfo.parse(minimal)
        assertEquals("v2.6.0", info.tag)
        assertTrue(info.assets.isEmpty())
        assertEquals("", info.body)
        assertEquals("v2.6.0", info.displayName)
    }

    @Test
    fun parse_skipsAssetsWithoutUrl() {
        val partial = """
            {"tag_name":"v2.6.0","assets":[{"name":"a.apk"},{"name":"b.apk","browser_download_url":"https://x/y"}]}
        """.trimIndent()
        val info = ReleaseInfo.parse(partial)
        assertEquals(1, info.assets.size)
        assertEquals("b.apk", info.assets.single().name)
    }

    @Test
    fun version_stripsVPrefix() {
        val info = ReleaseInfo.parse(json)
        assertFalse(info.version.startsWith("v"))
    }
}
