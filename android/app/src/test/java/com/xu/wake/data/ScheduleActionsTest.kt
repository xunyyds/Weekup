package com.xu.wake.data

import com.xu.wake.domain.ScheduleActions
import org.junit.Assert.*
import org.junit.Test

class ScheduleActionsTest {
    private fun course()=CourseSession("test","数学",1,1,2,listOf(1,2))
    @Test fun courseMutationsPreserveUnrelatedSettings() {
        val before=AppData(settings=AppSettings(school="某大学"))
        val added=ScheduleActions.saveCourse(before,course(),0xff000000)
        assertEquals("某大学",added.settings.school)
        assertEquals(1,added.courses.size)
        assertTrue(before.courses.isEmpty())
        val deleted=ScheduleActions.deleteCourse(added,"test")
        assertTrue(deleted.courses.isEmpty());assertFalse(deleted.colors.containsKey("test"))
    }
    @Test fun schoolImportPreservesKnownSchoolAndRejectsEmpty() {
        val before=AppData(settings=AppSettings(school="某大学"))
        val imported=ScheduleActions.importSchool(before,SchoolImport(listOf(course())))
        assertEquals("某大学",imported.settings.school)
        assertThrows(IllegalArgumentException::class.java){ScheduleActions.importSchool(before,SchoolImport(emptyList()))}
    }
    @Test fun motionSettingsRoundTripAndLegacyDefaults() {
        val original=AppData(settings=AppSettings(motionSpeed=1.5,reduceMotion=true,transitionStyle="fade"))
        assertEquals(original,BackupCodec.decode(BackupCodec.encode(original)))
        val json=org.json.JSONObject(BackupCodec.encode(AppData()))
        json.getJSONObject("settings").apply { remove("motionSpeed");remove("reduceMotion");remove("transitionStyle") }
        val settings=BackupCodec.decode(json.toString()).settings
        assertEquals(1.0,settings.motionSpeed,0.0);assertFalse(settings.reduceMotion);assertEquals("shared",settings.transitionStyle)
        assertThrows(IllegalArgumentException::class.java){AppSettings(motionSpeed=Double.NaN)}
        assertThrows(IllegalArgumentException::class.java){AppSettings(transitionStyle="unknown")}
    }
}
