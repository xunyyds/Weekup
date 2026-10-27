package com.xunyyds.kelaile.data

import org.junit.Assert.assertEquals
import org.junit.Test

class GridStyleSettingsTest {

    @Test fun courseCellColorDefaultsToAuto() {
        assertEquals(GRID_COLOR_AUTO, AppSettings().courseCellColor)
    }

    @Test fun courseCellColorRoundTripsThroughBackup() {
        val data = AppData(settings = AppSettings(courseCellColor = 0xFF3366CCL))
        val restored = BackupCodec.decode(BackupCodec.encode(data))
        assertEquals(0xFF3366CCL, restored.settings.courseCellColor)
    }

    @Test fun courseCellColorAutoSurvivesBackup() {
        val data = AppData(settings = AppSettings(courseCellColor = GRID_COLOR_AUTO))
        val restored = BackupCodec.decode(BackupCodec.encode(data))
        assertEquals(GRID_COLOR_AUTO, restored.settings.courseCellColor)
    }

    @Test fun legacyBackupWithoutCourseCellColorFallsBackToAuto() {
        val legacy = """{"version":1,"settings":{"tableHeaderColor":-1},"courses":[],"colors":{}}"""
        val restored = BackupCodec.decode(legacy)
        assertEquals(GRID_COLOR_AUTO, restored.settings.courseCellColor)
    }

    @Test fun invalidCourseCellColorRejected() {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            AppSettings(courseCellColor = Long.MIN_VALUE)
        }
    }

    @Test fun everyGridStyleIsAccepted() {
        GRID_LINE_STYLES.forEach { style ->
            assertEquals(style, AppSettings(gridLineStyle = style).gridLineStyle)
        }
    }

    @Test fun unknownGridStyleRejected() {
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            AppSettings(gridLineStyle = "no-such-style")
        }
    }

    @Test fun noGridStyleIsOffered() {
        assertEquals(true, GRID_LINE_NONE in GRID_LINE_STYLES)
    }
}
