package com.example.ridevault

import org.junit.Assert.assertEquals
import org.junit.Test

class FormattingTest {
    @Test
    fun normalizeDownloadRelativePath_ensuresTrailingSlash() {
        assertEquals(
            "Download/RideVault/Courses/",
            normalizeDownloadRelativePath("Download/RideVault/Courses")
        )
        assertEquals(
            "Download/RideVault/Courses/",
            normalizeDownloadRelativePath("Download/RideVault/Courses/")
        )
    }

    @Test
    fun downloadRelativePathQueryValues_includesBothPathForms() {
        assertEquals(
            listOf(
                "Download/RideVault/Courses/",
                "Download/RideVault/Courses"
            ),
            downloadRelativePathQueryValues("Download/RideVault/Courses/")
        )
    }

    @Test
    fun normalizeCourseFitFilename_usesExactlyOneFitExtension() {
        assertEquals("Course.fit", normalizeCourseFitFilename("Course.fit"))
        assertEquals("Course.fit", normalizeCourseFitFilename("Course.FIT"))
        assertEquals("Course.fit", normalizeCourseFitFilename("Course.fit.fit"))
        assertEquals("Course.fit", normalizeCourseFitFilename("Course.FIT.fit"))
        assertEquals("Course.fit", normalizeCourseFitFilename("Course.fit.FIT"))
    }
}
