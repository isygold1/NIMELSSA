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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nimelssa.vault.data.AuthMode
import com.nimelssa.vault.data.Course
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
import com.nimelssa.vault.ui.screens.WorkspaceScreen
import com.nimelssa.vault.ui.theme.NIMELSSATheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Check for existing Firebase Auth session on startup
        UserSession.checkExistingSession()
        setContent {
            NIMELSSATheme {
                MainApp()
            }
        }
    }
}

object Routes {
    const val AUTH = "auth"
    const val WORKSPACE = "workspace"
    const val PROPOSE = "propose"
    const val ADMIN = "admin"
    const val VIEWER = "viewer/{courseCode}"
    const val PROFILE = "profile"
    const val CBT = "cbt"
    const val EMAIL_CHANGE = "email_change"

    fun viewerRoute(courseCode: String) = "viewer/$courseCode"
}

@Composable
fun MainApp() {
    val userState by UserSession.state.collectAsState()
    val navController = rememberNavController()

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

    val selectedTab = when {
        currentRoute == Routes.WORKSPACE -> BottomNavTab.WORKSPACE
        currentRoute == Routes.PROPOSE -> BottomNavTab.PROPOSE
        currentRoute == Routes.ADMIN -> BottomNavTab.ADMIN
        else -> BottomNavTab.WORKSPACE
    }

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
                    onLogout = {
                        scope.launch { drawerState.close() }
                        UserSession.signOut()
                    }
                )
            }
        }
    ) {
        Scaffold(
            bottomBar = {
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
                        onOpenViewer = { course ->
                            navController.navigate(Routes.viewerRoute(course.code))
                        }
                    )
                }

                composable(Routes.PROPOSE) {
                    ProposeScreen(
                        onProposed = {
                            navController.navigate(Routes.ADMIN) {
                                popUpTo(Routes.WORKSPACE) { saveState = true }
                            }
                        }
                    )
                }

                composable(Routes.ADMIN) {
                    AdminScreen(repLevel = userState.repLevel)
                }

                composable(
                    route = Routes.VIEWER,
                    arguments = listOf(navArgument("courseCode") { type = NavType.StringType })
                ) { backStackEntry ->
                    val courseCode = backStackEntry.arguments?.getString("courseCode") ?: ""
                    val course = CourseRepository.findCourse(courseCode)
                    DocumentViewerScreen(
                        course = course,
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
            }
        }
    }
}
