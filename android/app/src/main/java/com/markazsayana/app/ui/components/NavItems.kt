package com.markazsayana.app.ui.components

import com.markazsayana.app.ui.navigation.Routes

/** The bottom-nav tab set for each role, matching the footer design per screen. */
fun navItemsForRole(role: String): List<BottomNavItem> = when (role) {
    "manager" -> listOf(
        BottomNavItem("الأداء", Routes.MANAGER_DASHBOARD),
        BottomNavItem("الفنيون", Routes.TECHNICIANS_ROSTER),
        BottomNavItem("المخزون", Routes.INVENTORY),
        BottomNavItem("التقارير", Routes.REPORTS),
    )
    "technician" -> listOf(
        BottomNavItem("مهامي", Routes.TECHNICIAN_TASKS),
        BottomNavItem("قطع الغيار", Routes.INVENTORY),
        BottomNavItem("أدائي", Routes.MY_PERFORMANCE),
    )
    else -> listOf(
        BottomNavItem("الرئيسية", Routes.RECEPTION_DASHBOARD),
        BottomNavItem("الطلبات", Routes.WORK_ORDERS_LIST),
        BottomNavItem("العملاء", Routes.CUSTOMERS_LIST),
        BottomNavItem("المخزون", Routes.INVENTORY),
    )
}
