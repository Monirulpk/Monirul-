package com.example.domain

import android.content.Context
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ProjectFileNode(
    val name: String,
    val relativePath: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val depth: Int
)

class ProjectFileManager(private val context: Context) {

    val workspacesRoot: File
        get() = File(context.filesDir, "workspaces").apply { mkdirs() }

    val modelsRoot: File
        get() = File(context.filesDir, "models").apply { mkdirs() }

    val exportsRoot: File
        get() = File(context.filesDir, "exports").apply { mkdirs() }

    suspend fun initializeProjectStructure(
        projectId: String,
        projectName: String,
        packageName: String
    ): File = withContext(Dispatchers.IO) {
        val root = File(workspacesRoot, projectId).apply { mkdirs() }
        val pkgPath = packageName.replace('.', '/')

        val dirs = listOf(
            "app/src/main/java/$pkgPath/ui/screens",
            "app/src/main/java/$pkgPath/ui/theme",
            "app/src/main/java/$pkgPath/viewmodel",
            "app/src/main/java/$pkgPath/data",
            "app/src/main/res/values",
            "app/src/main/res/drawable",
            "app/src/test/java/$pkgPath",
            "pdfs",
            "reports",
            "outputs"
        )
        dirs.forEach { File(root, it).mkdirs() }

        writeFileSafe(
            root,
            "settings.gradle.kts",
            """
            pluginManagement {
                repositories {
                    google()
                    mavenCentral()
                    gradlePluginPortal()
                }
            }
            dependencyResolutionManagement {
                repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
                repositories {
                    google()
                    mavenCentral()
                }
            }
            rootProject.name = "${sanitizeForKotlinString(projectName)}"
            include(":app")
            """.trimIndent() + "\n"
        )

        writeFileSafe(
            root,
            "build.gradle.kts",
            """
            plugins {
                id("com.android.application") version "8.9.0" apply false
                id("org.jetbrains.kotlin.android") version "2.1.0" apply false
                id("org.jetbrains.kotlin.plugin.compose") version "2.1.0" apply false
            }
            """.trimIndent() + "\n"
        )

        writeFileSafe(
            root,
            "app/build.gradle.kts",
            """
            plugins {
                id("com.android.application")
                id("org.jetbrains.kotlin.android")
                id("org.jetbrains.kotlin.plugin.compose")
            }

            android {
                namespace = "$packageName"
                compileSdk = 35

                defaultConfig {
                    applicationId = "$packageName"
                    minSdk = 24
                    targetSdk = 35
                    versionCode = 1
                    versionName = "1.0"
                }

                buildFeatures {
                    compose = true
                }
            }

            dependencies {
                implementation(platform("androidx.compose:compose-bom:2025.02.00"))
                implementation("androidx.activity:activity-compose:1.10.0")
                implementation("androidx.compose.material3:material3")
                implementation("androidx.compose.ui:ui")
                implementation("androidx.compose.ui:ui-tooling-preview")
                implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
                testImplementation("junit:junit:4.13.2")
            }
            """.trimIndent() + "\n"
        )

        writeFileSafe(
            root,
            "app/src/main/AndroidManifest.xml",
            """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <application
                    android:allowBackup="true"
                    android:label="@string/app_name"
                    android:supportsRtl="true"
                    android:theme="@style/Theme.App">
                    <activity
                        android:name=".MainActivity"
                        android:exported="true">
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />
                            <category android:name="android.intent.category.LAUNCHER" />
                        </intent-filter>
                    </activity>
                </application>
            </manifest>
            """.trimIndent() + "\n"
        )

        writeFileSafe(
            root,
            "app/src/main/res/values/strings.xml",
            """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <string name="app_name">${escapeXml(projectName)}</string>
                <string name="home_title">Workspace Overview</string>
            </resources>
            """.trimIndent() + "\n"
        )

        writeFileSafe(
            root,
            "app/src/main/res/values/themes.xml",
            """
            <?xml version="1.0" encoding="utf-8"?>
            <resources>
                <style name="Theme.App" parent="android:Theme.Material.Light.NoActionBar" />
            </resources>
            """.trimIndent() + "\n"
        )

        writeFileSafe(
            root,
            "app/src/main/java/$pkgPath/ui/theme/Theme.kt",
            """
            package $packageName.ui.theme

            import androidx.compose.material3.MaterialTheme
            import androidx.compose.material3.darkColorScheme
            import androidx.compose.runtime.Composable

            private val AppDarkScheme = darkColorScheme()

            @Composable
            fun AppTheme(content: @Composable () -> Unit) {
                MaterialTheme(
                    colorScheme = AppDarkScheme,
                    content = content
                )
            }
            """.trimIndent() + "\n"
        )

        writeFileSafe(
            root,
            "app/src/main/java/$pkgPath/MainActivity.kt",
            """
            package $packageName

            import android.os.Bundle
            import androidx.activity.ComponentActivity
            import androidx.activity.compose.setContent
            import androidx.activity.enableEdgeToEdge
            import androidx.compose.foundation.layout.Column
            import androidx.compose.foundation.layout.fillMaxSize
            import androidx.compose.foundation.layout.padding
            import androidx.compose.material3.MaterialTheme
            import androidx.compose.material3.Scaffold
            import androidx.compose.material3.Text
            import androidx.compose.runtime.Composable
            import androidx.compose.ui.Modifier
            import androidx.compose.ui.unit.dp
            import $packageName.ui.theme.AppTheme

            class MainActivity : ComponentActivity() {
                override fun onCreate(savedInstanceState: Bundle?) {
                    super.onCreate(savedInstanceState)
                    enableEdgeToEdge()
                    setContent {
                        AppTheme {
                            AppRootScreen()
                        }
                    }
                }
            }

            @Composable
            fun AppRootScreen() {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "${sanitizeForKotlinString(projectName)}",
                            style = MaterialTheme.typography.headlineMedium
                        )
                    }
                }
            }
            """.trimIndent() + "\n"
        )

        root
    }

    suspend fun listProjectTree(projectRoot: File): List<ProjectFileNode> =
        withContext(Dispatchers.IO) {
            if (!projectRoot.exists()) return@withContext emptyList()
            val results = mutableListOf<ProjectFileNode>()
            fun walk(dir: File, depth: Int) {
                val children = dir.listFiles()?.sortedWith(
                    compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() }
                ) ?: return
                for (child in children) {
                    val rel = child.relativeTo(projectRoot).path.replace('\\', '/')
                    results.add(
                        ProjectFileNode(
                            name = child.name,
                            relativePath = rel,
                            isDirectory = child.isDirectory,
                            sizeBytes = if (child.isDirectory) 0L else child.length(),
                            depth = depth
                        )
                    )
                    if (child.isDirectory) {
                        walk(child, depth + 1)
                    }
                }
            }
            walk(projectRoot, 0)
            results
        }

    suspend fun readFileSafe(projectRoot: File, relativePath: String): String =
        withContext(Dispatchers.IO) {
            val target = resolveConfinedFile(projectRoot, relativePath)
            if (!target.exists() || target.isDirectory) {
                throw IOException("File not found: $relativePath")
            }
            target.readText(Charsets.UTF_8)
        }

    suspend fun writeFileSafe(
        projectRoot: File,
        relativePath: String,
        content: String
    ): File = withContext(Dispatchers.IO) {
        val target = resolveConfinedFile(projectRoot, relativePath)
        target.parentFile?.mkdirs()
        target.writeText(content, Charsets.UTF_8)
        target
    }

    suspend fun createDirectorySafe(projectRoot: File, relativePath: String): File =
        withContext(Dispatchers.IO) {
            val target = resolveConfinedFile(projectRoot, relativePath)
            if (!target.exists() && !target.mkdirs()) {
                throw IOException("Could not create folder: $relativePath")
            }
            target
        }

    suspend fun deleteSafe(projectRoot: File, relativePath: String): Boolean =
        withContext(Dispatchers.IO) {
            val target = resolveConfinedFile(projectRoot, relativePath)
            if (target.canonicalPath == projectRoot.canonicalPath) {
                throw SecurityException("Cannot delete workspace root")
            }
            target.deleteRecursively()
        }

    suspend fun deleteWorkspace(projectId: String) = withContext(Dispatchers.IO) {
        File(workspacesRoot, projectId).deleteRecursively()
    }

    fun resolveConfinedFile(projectRoot: File, relativePath: String): File {
        val clean = relativePath.trimStart('/', '\\')
        val candidate = File(projectRoot, clean)
        val rootCanon = projectRoot.canonicalPath
        val candCanon = candidate.canonicalPath
        if (candCanon != rootCanon && !candCanon.startsWith(rootCanon + File.separator)) {
            throw SecurityException("Path escapes authorized workspace: $relativePath")
        }
        return candidate
    }

    private fun sanitizeForKotlinString(input: String): String =
        input.replace("\"", "\\\"").replace("\n", " ")

    private fun escapeXml(input: String): String =
        input.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
}
