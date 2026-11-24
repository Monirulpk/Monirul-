package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.LanguagePolicy
import com.example.domain.AppLanguage
import com.example.domain.AutomaticBuildAndFixSystem
import com.example.domain.DuplicateFeatureChecker
import com.example.domain.LanguageHelper
import com.example.domain.ProjectFileManager
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExampleRobolectricTest {

    @Test
    fun `read app name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("AI Dev Studio", appName)
    }

    @Test
    fun `language helper enforces bengali and english replies`() {
        assertEquals(
            AppLanguage.BENGALI,
            LanguageHelper.resolveReplyLanguage("আমাকে একটি অ্যাপ বানিয়ে দাও", LanguagePolicy.AUTO_DETECT)
        )
        assertEquals(
            AppLanguage.ENGLISH,
            LanguageHelper.resolveReplyLanguage("Create a Compose screen", LanguagePolicy.AUTO_DETECT)
        )
    }

    @Test
    fun `workspace initialize, build check, auto fix, and duplicate check work`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fileManager = ProjectFileManager(context)
        val root = fileManager.initializeProjectStructure("test_proj", "SampleApp", "com.dev.sample")

        val buildSystem = AutomaticBuildAndFixSystem()
        val initialBuild = buildSystem.runBuildVerification(root)
        assertTrue(initialBuild.passed)

        // Inject a missing closing brace to verify Automatic Error Fixer
        val brokenPath = "app/src/main/java/com/dev/sample/Broken.kt"
        fileManager.writeFileSafe(root, brokenPath, "package com.dev.sample\n\nclass Broken {\n")
        val brokenBuild = buildSystem.runBuildVerification(root)
        assertFalse(brokenBuild.passed)

        val fixReport = buildSystem.autoFixAndRetry(root, "com.dev.sample", brokenBuild)
        assertEquals(1, fixReport.errorsFixed)
        assertTrue(fixReport.postFixReport.passed)

        // Duplicate checker detects AppRootScreen
        val checker = DuplicateFeatureChecker()
        val index = checker.indexWorkspace(root)
        val dup = checker.checkFeature("AppRootScreen", "AppRootScreen.kt", index)
        assertTrue(dup.isDuplicate)

        fileManager.deleteWorkspace("test_proj")
    }
}
