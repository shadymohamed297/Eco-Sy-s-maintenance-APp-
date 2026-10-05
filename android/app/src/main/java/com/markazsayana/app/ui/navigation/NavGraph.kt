package com.markazsayana.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
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
import com.markazsayana.app.ui.manager.TechnicianDetailScreen
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
import com.markazsayana.app.ui.workorders.WorkOrderDetailScreen
import com.markazsayana.app.ui.workorders.WorkOrderInvoiceScreen

@Composable
fun MarkazSayanaNavHost(navController: NavHostController) {
    // Global session watcher: if a 401 clears the token while the user is mid-flow on an
    // authenticated screen, drop them back to login instead of leaving every screen stuck
    // showing a generic load error forever.
    val currentUserViewModel: CurrentUserViewModel = hiltViewModel()
    val isLoggedIn by currentUserViewModel.isLoggedIn.collectAsState()
    val signedInUser by currentUserViewModel.user.collectAsState()
    val role = signedInUser?.role ?: "reception"
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
                onNavTab = { route -> navController.navigateTab(route, role) },
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
        ) { entry ->
            AssignTechnicianScreen(
                onBack = { navController.popBackStack() },
                onConfirmed = {
                    val id = entry.arguments?.getString("workOrderId")?.toIntOrNull()
                    if (id != null) {
                        val detailRoute = Routes.workOrderDetail(id)
                        if (!navController.popBackStack(detailRoute, false)) {
                            navController.navigate(detailRoute) {
                                popUpTo(homeRouteForRole(role))
                                launchSingleTop = true
                            }
                        }
                    } else navController.navigateAndClearBackstack(homeRouteForRole(role))
                },
            )
        }

        // ── Technician ───────────────────────────────────────────────
        composable(Routes.TECHNICIAN_TASKS) {
            TechnicianTasksScreen(
                onOpenWorkOrder = { workOrderId -> navController.navigate(Routes.workOrderExecution(workOrderId)) },
                onNavTab = { route -> navController.navigateTab(route, role) },
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
            MyPerformanceScreen(
                onNavTab = { route -> navController.navigateTab(route, role) },
                onOpenWorkOrder = { id -> navController.navigate(Routes.workOrderExecution(id)) },
            )
        }

        // ── Manager ──────────────────────────────────────────────────
        composable(Routes.MANAGER_DASHBOARD) {
            ManagerDashboardScreen(
                onNavTab = { route -> navController.navigateTab(route, role) },
                onManageEmployees = { navController.navigate(Routes.EMPLOYEES) },
            )
        }

        composable(Routes.EMPLOYEES) {
            EmployeesScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.TECHNICIANS_ROSTER) {
            TechniciansRosterScreen(
                onNavTab = { route -> navController.navigateTab(route, role) },
                onOpenTechnician = { id -> navController.navigate(Routes.technicianDetail(id)) },
            )
        }

        composable(Routes.TECHNICIAN_DETAIL, arguments = listOf(navArgument("technicianId") { type = NavType.StringType })) {
            TechnicianDetailScreen(
                onBack = { navController.popBackStack() },
                onOpenWorkOrder = { id -> navController.navigate(Routes.workOrderDetail(id)) },
            )
        }

        composable(Routes.REPORTS) {
            ReportsScreen(onNavTab = { route -> navController.navigateTab(route, role) })
        }

        composable(Routes.INVENTORY) {
            InventoryScreen(
                role = role,
                onNavTab = { route -> navController.navigateTab(route, role) },
            )
        }

        // ── Shared management (manager / reception) ─────────────────
        composable(Routes.WORK_ORDERS_LIST) {
            WorkOrdersListScreen(
                role = role,
                onNavTab = { route -> navController.navigateTab(route, role) },
                onOpenWorkOrder = { id -> navController.navigate(Routes.workOrderDetail(id)) },
                onNewRequest = { navController.navigate(Routes.NEW_REQUEST) },
            )
        }

        composable(Routes.WORK_ORDER_DETAIL, arguments = listOf(navArgument("workOrderId") { type = NavType.StringType })) {
            WorkOrderDetailScreen(
                role = role,
                onBack = { navController.popBackStack() },
                onAssign = { id -> navController.navigate(Routes.assignTechnician(id)) },
                onInvoice = { id -> navController.navigate(Routes.workOrderInvoice(id)) },
                onCustomer = { id -> navController.navigate(Routes.customerDetail(id)) },
            )
        }

        composable(Routes.WORK_ORDER_INVOICE, arguments = listOf(navArgument("workOrderId") { type = NavType.StringType })) {
            WorkOrderInvoiceScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.PENDING_APPROVALS) {
            WorkOrdersListScreen(
                role = role,
                onNavTab = { route -> navController.navigateTab(route, role) },
                onOpenWorkOrder = { id -> navController.navigate(Routes.workOrderDetail(id)) },
                onNewRequest = { navController.navigate(Routes.NEW_REQUEST) },
                initialFilter = WorkOrderFilter.AWAITING_APPROVAL,
            )
        }

        composable(Routes.CUSTOMERS_LIST) {
            CustomersListScreen(
                role = role,
                onOpenCustomer = { customerId -> navController.navigate(Routes.customerDetail(customerId)) },
                onNavTab = { route -> navController.navigateTab(route, role) },
            )
        }

        composable(
            route = Routes.CUSTOMER_DETAIL,
            arguments = listOf(navArgument("customerId") { type = NavType.StringType }),
        ) {
            CustomerDetailScreen(
                onBack = { navController.popBackStack() },
                onOpenWorkOrder = { id -> navController.navigate(Routes.workOrderDetail(id)) },
            )
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
private fun NavHostController.navigateTab(route: String, role: String) {
    navigate(route) {
        popUpTo(homeRouteForRole(role)) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
