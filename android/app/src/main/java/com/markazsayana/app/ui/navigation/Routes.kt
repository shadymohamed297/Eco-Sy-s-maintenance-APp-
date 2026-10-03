package com.markazsayana.app.ui.navigation

object Routes {
    const val SPLASH = "splash"
    const val LOGIN = "login"

    const val RECEPTION_DASHBOARD = "reception/dashboard"
    const val NEW_REQUEST = "reception/new_request"
    const val ASSIGN_TECHNICIAN = "reception/assign/{workOrderId}"
    fun assignTechnician(workOrderId: Int) = "reception/assign/$workOrderId"

    const val TECHNICIAN_TASKS = "technician/tasks"
    const val WORK_ORDER_EXECUTION = "technician/work_order/{workOrderId}"
    fun workOrderExecution(workOrderId: Int) = "technician/work_order/$workOrderId"
    const val INVOICE_SIGNOFF = "technician/invoice/{workOrderId}"
    fun invoiceSignoff(workOrderId: Int) = "technician/invoice/$workOrderId"

    const val MANAGER_DASHBOARD = "manager/dashboard"
    const val EMPLOYEES = "manager/employees"
    const val INVENTORY = "inventory"

    const val WORK_ORDERS_LIST = "work-orders"
    const val PENDING_APPROVALS = "work-orders/awaiting-approval"
    const val CUSTOMERS_LIST = "customers"
    const val CUSTOMER_DETAIL = "customers/{customerId}"
    fun customerDetail(customerId: Int) = "customers/$customerId"
    const val TECHNICIANS_ROSTER = "manager/technicians"
    const val REPORTS = "manager/reports"
    const val MY_PERFORMANCE = "technician/performance"

    const val COMING_SOON = "coming_soon/{title}"
    fun comingSoon(title: String) = "coming_soon/$title"
}
