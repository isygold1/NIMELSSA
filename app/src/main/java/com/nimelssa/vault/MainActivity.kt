package com.nimelssa.vault

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nimelssa.vault.data.AuthMode
import com.nimelssa.vault.data.CourseRepository
import com.nimelssa.vault.data.UserRole
import com.nimelssa.vault.data.UserSession
import com.nimelssa.vault.ui.components.BottomNavBar
import com.nimelssa.vault.ui.components.BottomNavTab
import com.nimelssa.vault.ui.components.DrawerContent
import com.nimelssa.vault.ui.screens.AdminScreen
import com.nimelssa.vault.ui.screens.AuthScreen
import com.nimelssa.vault.ui.screens.CbtExamScreen
import com.nimelssa.vault.ui.screens.DocumentViewerScreen
import com.nimelssa.vault.ui.screens.EmailChangeScreen
import com.nimelssa.vault.ui.screens.ProfileScreen
import com.nimelssa.vault.ui.screens.ProposeScreen
import com.nimelssa.vault.ui.screens.ReportScreen
import com.nimelssa.vault.ui.screens.ReportsDashboardScreen
import com.nimelssa.vault.ui.screens.SettingsScreen
import com.nimelssa.vault.ui.screens.WorkspaceScreen
import com.nimelssa.vault.ui.theme.NIMELSSATheme
import com.nimelssa.vault.ui.theme.OrientationManager
import com.nimelssa.vault.ui.theme.ThemeManager
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Restore persisted theme preference before composing
        ThemeManager.init(applicationContext)
        // Restore persisted PDF orientation preference
        OrientationManager.init(applicationContext)
        // Check for existing Firebase Auth session on startup
        UserSession.checkExistingSession()
        setContent {
            NIMELSSATheme {
                MainApp()
            }
        }
    }
}

/**
 * Top-level Scaffold + NavHost.  Extracted from MainApp so that on the viewer
 * route it can be rendered *without* a ModalNavigationDrawer parent — the
 * drawer's gesture detector was intercepting pinch/pan events.
 */
@Composable
private fun VaultScaffold(
    navController: androidx.navigation.NavHostController,
    userState: com.nimelssa.vault.data.UserState,
    drawerState: androidx.compose.material3.DrawerState,
    scope: kotlinx.coroutines.CoroutineScope,
    selectedTab: com.nimelssa.vault.ui.components.BottomNavTab,
    showBottomBar: Boolean,
) {
    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                BottomNavBar(
                    selectedTab = selectedTab,
                    onTabSelected = { tab ->
                        val route = when (tab) {
                            BottomNavTab.WORKSPACE -> Routes.WORKSPACE
                            BottomNavTab.PROPOSE -> Routes.PROPOSE
                            BottomNavTab.ADMIN -> Routes.ADMIN
                        }
                        navController.navigate(route) {
                            popUpTo(Routes.WORKSPACE) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    showAdmin = userState.role == UserRole.ADMIN || userState.role == UserRole.REP
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.WORKSPACE,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            composable(Routes.WORKSPACE) {
                WorkspaceScreen(
                    onOpenViewer = { course, resourceType ->
                        navController.navigate(Routes.viewerRoute(course.code, resourceType, course.level))
                    },
                    onNavigateToPropose = {
                        navController.navigate(Routes.PROPOSE) {
                            popUpTo(Routes.WORKSPACE) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onOpenDrawer = {
                        scope.launch { drawerState.open() }
                    }
                )
            }

            composable(Routes.PROPOSE) {
                ProposeScreen(
                    onProposed = {
                        navController.navigate(Routes.WORKSPACE) {
                            popUpTo(Routes.WORKSPACE) { saveState = true }
                        }
                    },
                    onOpenDrawer = {
                        scope.launch { drawerState.open() }
                    }
                )
            }

            composable(Routes.ADMIN) {
                AdminScreen(
                    repLevel = userState.repLevel,
                    onPreview = { course, resourceType ->
                        navController.navigate(Routes.viewerRoute(course.code, resourceType, course.level))
                    },
                    onOpenDrawer = {
                        scope.launch { drawerState.open() }
                    }
                )
            }

            composable(
                route = Routes.VIEWER,
                arguments = listOf(
                    navArgument("courseCode") { type = NavType.StringType },
                    navArgument("resourceType") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("level") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { backStackEntry ->
                val courseCode = backStackEntry.arguments?.getString("courseCode") ?: ""
                val resourceType = backStackEntry.arguments?.getString("resourceType")
                val level = backStackEntry.arguments?.getString("level")
                DocumentViewerScreen(
                    courseCode = courseCode,
                    initialResourceType = resourceType,
                    levelHint = level,
                    onClose = { navController.popBackStack() }
                )
            }

            composable(Routes.PROFILE) {
                ProfileScreen(
                    onClose = { navController.popBackStack() }
                )
            }

            composable(Routes.CBT) {
                CbtExamScreen(
                    onClose = { navController.popBackStack() }
                )
            }

            composable(Routes.EMAIL_CHANGE) {
                EmailChangeScreen(
                    onClose = { navController.popBackStack() }
                )
            }

            composable(Routes.REPORT) {
                ReportScreen(
                    onClose = { navController.popBackStack() }
                )
            }

            composable(Routes.REPORTS_DASHBOARD) {
                ReportsDashboardScreen(
                    onClose = { navController.popBackStack() }
                )
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onClose = { navController.popBackStack() }
                )
            }
        }
    }
}

object Routes {
    const val AUTH = "auth"
    const val WORKSPACE = "workspace"
    const val PROPOSE = "propose"
    const val ADMIN = "admin"
    const val VIEWER = "viewer/{courseCode}?resourceType={resourceType}&level={level}"
    const val PROFILE = "profile"
    const val CBT = "cbt"
    const val EMAIL_CHANGE = "email_change"
    const val REPORT = "report"
    const val REPORTS_DASHBOARD = "reports_dashboard"
    const val SETTINGS = "settings"

    fun viewerRoute(courseCode: String, resourceType: String? = null, level: String? = null) =
        buildString {
            append("viewer/").append(courseCode)
            if (resourceType != null) append("?resourceType=").append(resourceType)
            if (level != null) append(if (resourceType != null) "&" else "?").append("level=").append(level)
        }
}

@Composable
fun MainApp() {
    val userState by UserSession.state.collectAsState()
    val navController = rememberNavController()

    // ── Save last route across config changes (orientation rotation) ──
    var savedRoute by rememberSaveable { mutableStateOf<String?>(null) }

    // ── Loading screen (initial session check in progress) ──
    if (!userState.isLoggedIn && userState.isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    // ── Auth screen (not logged in) ──
    if (!userState.isLoggedIn) {
        AuthScreen(
            authMode = userState.authMode,
            isLoading = userState.isLoading,
            errorMessage = userState.errorMessage,
            onToggleMode = { UserSession.setAuthMode(it) },
            onLogin = { email, password ->
                UserSession.signIn(email, password)
            },
            onSignup = { name, email, password, role, repLevel ->
                UserSession.signUp(name, email, password, role, repLevel)
            },
            onForgotPassword = { email ->
                UserSession.sendPasswordReset(email)
            },
            onClearError = { UserSession.clearError() }
        )
        return
    }

    // ── Main app with drawer + bottom nav ──
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route

    // ── Restore viewer route after config change (orientation rotation) ──
    val wasViewer = savedRoute?.startsWith("viewer/") == true
    LaunchedEffect(wasViewer, currentRoute) {
        if (wasViewer && savedRoute != null && currentRoute != savedRoute) {
            navController.navigate(savedRoute!!)
            savedRoute = null
        }
    }
    // Track current route for restore
    LaunchedEffect(currentRoute) {
        if (currentRoute != null) savedRoute = currentRoute
    }

    val selectedTab = when {
        currentRoute == Routes.WORKSPACE -> BottomNavTab.WORKSPACE
        currentRoute == Routes.PROPOSE -> BottomNavTab.PROPOSE
        currentRoute == Routes.ADMIN -> BottomNavTab.ADMIN
        else -> BottomNavTab.WORKSPACE
    }

    val isViewer = currentRoute?.startsWith("viewer/") == true

    // On viewer routes, render the scaffold directly — no drawer gesture
    // detector above it to steal pinch/pan events.
    if (isViewer) {
        VaultScaffold(
            navController = navController,
            userState = userState,
            drawerState = drawerState,
            scope = scope,
            selectedTab = selectedTab,
            showBottomBar = false
        )
    } else {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet {
                    DrawerContent(
                        user = userState,
                        onNavigateToCbt = {
                            scope.launch { drawerState.close() }
                            navController.navigate(Routes.CBT)
                        },
                        onNavigateToProfile = {
                            scope.launch { drawerState.close() }
                            navController.navigate(Routes.PROFILE)
                        },
                        onNavigateToEmailMod = {
                            scope.launch { drawerState.close() }
                            navController.navigate(Routes.EMAIL_CHANGE)
                        },
                        onNavigateToReport = {
                            scope.launch { drawerState.close() }
                            navController.navigate(Routes.REPORT)
                        },
                        onNavigateToReportsDashboard = {
                            scope.launch { drawerState.close() }
                            navController.navigate(Routes.REPORTS_DASHBOARD)
                        },
                        onNavigateToSettings = {
                            scope.launch { drawerState.close() }
                            navController.navigate(Routes.SETTINGS)
                        },
                        onLogout = {
                            scope.launch { drawerState.close() }
                            UserSession.signOut()
                        }
                    )
                }
            }
        ) {
            VaultScaffold(
                navController = navController,
                userState = userState,
                drawerState = drawerState,
                scope = scope,
                selectedTab = selectedTab,
                showBottomBar = true
            )
        }
    }
}
