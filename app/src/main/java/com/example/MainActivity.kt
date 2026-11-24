package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.navigation.HomeRoute
import com.example.ui.navigation.PdfUploadRoute
import com.example.ui.navigation.ProgressRoute
import com.example.ui.navigation.ProjectRoute
import com.example.ui.navigation.SettingsRoute
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.PdfUploadScreen
import com.example.ui.screens.ProgressScreen
import com.example.ui.screens.ProjectScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.AiDevStudioTheme
import com.example.viewmodel.StudioViewModel

private data class TopDestination(
    val route: Any,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            val viewModel: StudioViewModel = viewModel()
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            AiDevStudioTheme(themeMode = uiState.preferences.themeMode) {
                AiDevStudioAppShell(
                    widthSizeClass = windowSizeClass.widthSizeClass,
                    viewModel = viewModel
                )
            }
        }
    }
}

@Composable
private fun AiDevStudioAppShell(
    widthSizeClass: WindowWidthSizeClass,
    viewModel: StudioViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val destinations = remember {
        listOf(
            TopDestination(HomeRoute, R.string.nav_home, Icons.Filled.Home, Icons.Outlined.Home, "nav_home"),
            TopDestination(ProjectRoute, R.string.nav_project, Icons.Filled.Folder, Icons.Outlined.Folder, "nav_project"),
            TopDestination(PdfUploadRoute, R.string.nav_pdf, Icons.Filled.PictureAsPdf, Icons.Outlined.PictureAsPdf, "nav_pdf"),
            TopDestination(ProgressRoute, R.string.nav_progress, Icons.Filled.PlayCircle, Icons.Outlined.PlayCircle, "nav_progress"),
            TopDestination(SettingsRoute, R.string.nav_settings, Icons.Filled.Settings, Icons.Outlined.Settings, "nav_settings")
        )
    }

    LaunchedEffect(uiState.statusBanner) {
        val msg = uiState.statusBanner
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearStatusBanner()
        }
    }

    fun navigateTopLevel(route: Any) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val useRail = widthSizeClass != WindowWidthSizeClass.Compact
    val isExpanded = widthSizeClass == WindowWidthSizeClass.Expanded

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        bottomBar = {
            if (!useRail) {
                NavigationBar {
                    destinations.forEach { dest ->
                        val selected = currentDestination?.hasRoute(dest.route::class) == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navigateTopLevel(dest.route) },
                            icon = {
                                Icon(
                                    imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                    contentDescription = stringResource(dest.labelRes)
                                )
                            },
                            label = { Text(stringResource(dest.labelRes)) },
                            modifier = Modifier.testTag(dest.testTag)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (useRail) {
                NavigationRail {
                    destinations.forEach { dest ->
                        val selected = currentDestination?.hasRoute(dest.route::class) == true
                        NavigationRailItem(
                            selected = selected,
                            onClick = { navigateTopLevel(dest.route) },
                            icon = {
                                Icon(
                                    imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                    contentDescription = stringResource(dest.labelRes)
                                )
                            },
                            label = { Text(stringResource(dest.labelRes)) },
                            modifier = Modifier.testTag(dest.testTag)
                        )
                    }
                }
            }

            NavHost(
                navController = navController,
                startDestination = HomeRoute,
                modifier = Modifier.weight(1f)
            ) {
                composable<HomeRoute> {
                    HomeScreen(
                        uiState = uiState,
                        onCreateProject = viewModel::createProject,
                        onSelectProject = viewModel::selectProject,
                        onImportPdfUri = { uri -> viewModel.importPdf(uri, autoPlan = true) },
                        onRunPipeline = viewModel::runFull15TaskPipeline,
                        onAskAi = viewModel::askAssistant,
                        onNavigateToSettings = { navigateTopLevel(SettingsRoute) },
                        onNavigateToProgress = { navigateTopLevel(ProgressRoute) },
                        onNavigateToProject = { navigateTopLevel(ProjectRoute) }
                    )
                }
                composable<ProjectRoute> {
                    ProjectScreen(
                        uiState = uiState,
                        isExpandedScreen = isExpanded,
                        onOpenFile = viewModel::openFile,
                        onCloseFile = viewModel::closeOpenFile,
                        onUpdateEditor = viewModel::updateEditorContent,
                        onSaveFile = viewModel::saveOpenFile,
                        onCreateFile = viewModel::createNewFile,
                        onCreateFolder = viewModel::createNewFolder,
                        onDeletePath = viewModel::deleteProjectPath,
                        onCheckDuplicate = viewModel::checkDuplicateFeature,
                        onRunBuildAndFix = {
                            viewModel.runBuildAndFixOnly()
                            navigateTopLevel(ProgressRoute)
                        }
                    )
                }
                composable<PdfUploadRoute> {
                    PdfUploadScreen(
                        uiState = uiState,
                        onImportPdf = { uri -> viewModel.importPdf(uri, autoPlan = false) },
                        onSelectPdfPage = viewModel::selectPdfAndLoadPage,
                        onDeletePdf = viewModel::deletePdf,
                        onPlanTasksFromPdf = viewModel::planFromSelectedPdf,
                        onNavigateToProgress = { navigateTopLevel(ProgressRoute) }
                    )
                }
                composable<ProgressRoute> {
                    ProgressScreen(
                        uiState = uiState,
                        onRunFullPipeline = viewModel::runFull15TaskPipeline,
                        onRunBuildAndFix = viewModel::runBuildAndFixOnly,
                        onAddInstructionTasks = viewModel::addInstructionAsTasks,
                        onDeleteTask = viewModel::deleteTask,
                        onClearTasks = viewModel::clearTasks,
                        onCreateShareBundleIntent = viewModel::createShareBundleIntent,
                        onCreateShareReportIntent = viewModel::createShareReportIntent
                    )
                }
                composable<SettingsRoute> {
                    SettingsScreen(
                        uiState = uiState,
                        onSetBackend = viewModel::setBackendPreference,
                        onSetLanguagePolicy = viewModel::setLanguagePolicy,
                        onSetThemeMode = viewModel::setThemeMode,
                        onDownloadCatalogModel = viewModel::downloadCatalogModel,
                        onCancelDownload = viewModel::cancelDownload,
                        onImportLocalModel = viewModel::importLocalModel,
                        onLoadModel = viewModel::loadModel,
                        onUnloadModel = viewModel::unloadModel,
                        onDeleteModel = viewModel::deleteModel,
                        onDeleteActiveProject = viewModel::deleteProject
                    )
                }
            }
        }
    }
}
