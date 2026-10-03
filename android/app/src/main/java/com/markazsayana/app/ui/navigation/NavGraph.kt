package com.markazsayana.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.markazsayana.app.ui.components.ComingSoonScreen
import com.markazsayana.app.ui.components.FullScreenLoading
import com.markazsayana.app.ui.customers.CustomerDetailScreen
import com.markazsayana.app.ui.customers.CustomersListScreen
import com.markazsayana.app.ui.login.LoginScreen
import com.markazsayana.app.ui.manager.EmployeesScreen
import com.markazsayana.app.ui.manager.InventoryScreen
import com.markazsayana.app.ui.manager.ManagerDashboardScreen
import com.markazsayana.app.ui.manager.TechniciansRosterScreen
import com.markazsayana.app.ui.reception.AssignTechnicianScreen
import com.markazsayana.app.ui.reception.NewRequestScreen
import com.markazsayana.app.ui.reception.ReceptionDashboardScreen
import com.markazsayana.app.ui.reports.ReportsScreen
import com.markazsayana.app.ui.technician.InvoiceSignoffScreen
import com.markazsayana.app.ui.technician.MyPerformanceScreen
import com.markazsayana.app.ui.technician.TechnicianTasksScreen
import com.markazsayana.app.ui.technician.WorkOrderExecutionScreen
import com.markazsayana.app.ui.workorders.WorkOrderFilter
import com.markazsayana.app.ui.workorders.WorkOrdersListScreen

@Composable
fun MarkazSayanaNavHost(navController: NavHostController) {
    // Global session watcher: if a 401 clears the token while the user is mid-flow on an
    // authenticated screen, drop them back to login instead of leaving every screen stuck
    // showing a generic load error forever.
    val currentUserViewModel: CurrentUserViewModel = hiltViewModel()
    val isLoggedIn by currentUserViewModel.isLoggedIn.collectAsState()
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    var wasLoggedIn by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(isLoggedIn) {
        val route = currentBackStackEntry?.destination?.route
        if (wasLoggedIn == true && isLoggedIn == false && route != Routes.LOGIN && route != Routes.SPLASH) {
            navController.navigateAndClearBackstack(Routes.LOGIN)
        }
        wasLoggedIn = isLoggedIn
    }

    NavHost(navController = navController, startDestination = Routes.SPLASH) {

        composable(Routes.SPLASH) {
            val viewModel: SplashViewModel = hiltViewModel()
            val destination by viewModel.destination.collectAsState()
            LaunchedEffect(destination) {
                when (val d = destination) {
                    is SessionDestination.Checking -> Unit
                    is SessionDestination.NeedsLogin -> navController.navigateAndClearBackstack(Routes.LOGIN)
                    is SessionDestination.LoggedIn -> navController.navigateAndClearBackstack(homeRouteForRole(d.role))
                }
            }
            FullScreenLoading()
        }

        composable(Routes.LOGIN) {
            LoginScreen(onLoggedIn = { user -> navController.navigateAndClearBackstack(homeRouteForRole(user.role)) })
        }

        // ── Reception ────────────────────────────────────────────────
        composable(Routes.RECEPTION_DASHBOARD) {
            ReceptionDashboardScreen(
                onNewRequest = { navController.navigate(Routes.NEW_REQUEST) },
                onNavTab = { route -> navController.navigateTab(route) },
                onOpenApprovals = { navController.navigate(Routes.PENDING_APPROVALS) },
            )
        }
        composable(Routes.NEW_REQUEST) {
            NewRequestScreen(
                onBack = { navController.popBackStack() },
                onNext = { workOrderId -> navController.navigate(Routes.assignTechnician(workOrderId)) },
            )
        }
        composable(
            route = Routes.ASSIGN_TECHNICIAN,
            arguments = listOf(navArgument("workOrderId") { type = NavType.StringType }),
        ) {
            AssignTechnicianScreen(
                onBack = { navController.popBackStack() },
                onConfirmed = { navController.navigateAndClearBackstack(Routes.RECEPTION_DASHBOARD) },
            )
        }

        // ── Technician ───────────────────────────────────────────────
        composable(Routes.TECHNICIAN_TASKS) {
            TechnicianTasksScreen(
                onOpenWorkOrder = { workOrderId -> navController.navigate(Routes.workOrderExecution(workOrderId)) },
                onNavTab = { route -> navController.navigateTab(route) },
            )
        }
        composable(
            route = Routes.WORK_ORDER_EXECUTION,
            arguments = listOf(navArgument("workOrderId") { type = NavType.StringType }),
        ) {
            WorkOrderExecutionScreen(
                onBack = { navController.popBackStack() },
                onFinish = { workOrderId -> navController.navigate(Routes.invoiceSignoff(workOrderId)) },
            )
        }
        composable(
            route = Routes.INVOICE_SIGNOFF,
            arguments = listOf(navArgument("workOrderId") { type = NavType.StringType }),
        ) {
            InvoiceSignoffScreen(
                onBack = { navController.popBackStack() },
                onClosed = { navController.navigateAndClearBackstack(Routes.TECHNICIAN_TASKS) },
            )
        }

        composable(Routes.MY_PERFORMANCE) {
            MyPerformanceScreen(onNavTab = { route -> navController.navigateTab(route) })
        }

        // ── Manager ──────────────────────────────────────────────────
        composable(Routes.MANAGER_DASHBOARD) {
            ManagerDashboardScreen(
                onNavTab = { route -> navController.navigateTab(route) },
                onManageEmployees = { navController.navigate(Routes.EMPLOYEES) },
            )
        }

        composable(Routes.EMPLOYEES) {
            EmployeesScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.TECHNICIANS_ROSTER) {
            TechniciansRosterScreen(onNavTab = { route -> navController.navigateTab(route) })
        }

        composable(Routes.REPORTS) {
            ReportsScreen(onNavTab = { route -> navController.navigateTab(route) })
        }

        composable(Routes.INVENTORY) {
            val currentUserViewModel: CurrentUserViewModel = hiltViewModel()
            val user by currentUserViewModel.user.collectAsState()
            InventoryScreen(
                role = user?.role ?: "reception",
                onNavTab = { route -> navController.navigateTab(route) },
            )
        }

        // ── Shared (reception) ──────────────────────────────────────
        composable(Routes.WORK_ORDERS_LIST) {
            WorkOrdersListScreen(onNavTab = { route -> navController.navigateTab(route) })
        }

        composable(Routes.PENDING_APPROVALS) {
            WorkOrdersListScreen(
                onNavTab = { route -> navController.navigateTab(route) },
                initialFilter = WorkOrderFilter.AWAITING_APPROVAL,
            )
        }

        composable(Routes.CUSTOMERS_LIST) {
            CustomersListScreen(
                onOpenCustomer = { customerId -> navController.navigate(Routes.customerDetail(customerId)) },
                onNavTab = { route -> navController.navigateTab(route) },
            )
        }

        composable(
            route = Routes.CUSTOMER_DETAIL,
            arguments = listOf(navArgument("customerId") { type = NavType.StringType }),
        ) {
            CustomerDetailScreen(onBack = { navController.popBackStack() })
        }

        composable(
            route = Routes.COMING_SOON,
            arguments = listOf(navArgument("title") { type = NavType.StringType }),
        ) { backStackEntry ->
            ComingSoonScreen(title = backStackEntry.arguments?.getString("title") ?: "")
        }
    }
}

private fun homeRouteForRole(role: String): String = when (role) {
    "reception" -> Routes.RECEPTION_DASHBOARD
    "technician" -> Routes.TECHNICIAN_TASKS
    "manager" -> Routes.MANAGER_DASHBOARD
    else -> Routes.LOGIN
}

private fun NavHostController.navigateAndClearBackstack(route: String) {
    navigate(route) {
        popUpTo(graph.id) { inclusive = true }
        launchSingleTop = true
    }
}

/** Bottom-nav tab taps: swap the current tab without stacking duplicate destinations. */
private fun NavHostController.navigateTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
