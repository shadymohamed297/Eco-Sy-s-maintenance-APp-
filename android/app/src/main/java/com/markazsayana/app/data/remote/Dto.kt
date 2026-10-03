package com.markazsayana.app.data.remote

import kotlinx.serialization.Serializable

@Serializable
data class UserDto(
    val id: Int,
    val name: String,
    val email: String? = null,
    val phone: String? = null,
    val role: String,
    val branch: String? = null,
    val title: String? = null,
    val specialty: String? = null,
    val initials: String,
)

@Serializable
data class LoginRequest(val identifier: String, val password: String)

@Serializable
data class LoginResponse(val token: String, val user: UserDto)

@Serializable
data class MeResponse(val user: UserDto)

@Serializable
data class ChangePasswordRequest(val currentPassword: String, val newPassword: String)

// ── Employee management (manager) ───────────────────────────────────

@Serializable
data class EmployeeDto(
    val id: Int,
    val name: String,
    val initials: String,
    val email: String? = null,
    val phone: String? = null,
    val role: String,
    val branch: String? = null,
    val title: String? = null,
    val specialty: String? = null,
    val active: Boolean,
    val createdAt: String,
)

@Serializable
data class EmployeesListResponse(val employees: List<EmployeeDto>)

@Serializable
data class EmployeeResponse(val employee: EmployeeDto, val temporaryPassword: String? = null)

@Serializable
data class CreateEmployeeRequest(
    val name: String,
    val role: String,
    val email: String? = null,
    val phone: String? = null,
    val branch: String? = null,
    val title: String? = null,
    val specialty: String? = null,
)

@Serializable
data class UpdateEmployeeRequest(
    val name: String? = null,
    val role: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val branch: String? = null,
    val title: String? = null,
    val specialty: String? = null,
    val active: Boolean? = null,
    val resetPassword: Boolean? = null,
)

// ── Reception dashboard ─────────────────────────────────────────────

@Serializable
data class ReceptionDashboardResponse(
    val branch: String? = null,
    val counts: ReceptionCounts,
    val pendingApprovals: PendingApprovals,
    val todaysAppointments: List<AppointmentDto>,
)

@Serializable
data class ReceptionCounts(val new: Int, val inProgress: Int, val late: Int)

@Serializable
data class PendingApprovals(val count: Int, val oldestHours: Int, val items: List<ApprovalItemDto>)

@Serializable
data class ApprovalItemDto(
    val id: Int,
    val code: String,
    val issueDescription: String,
    val createdAt: String,
    val customerName: String,
    val deviceType: String,
)

@Serializable
data class AppointmentDto(
    val id: Int,
    val code: String,
    val issueDescription: String,
    val scheduledAt: String? = null,
    val status: String,
    val priority: String,
    val customerName: String,
    val branch: String? = null,
    val deviceType: String,
    val technicianName: String? = null,
)

// ── Manager dashboard ───────────────────────────────────────────────

@Serializable
data class ManagerDashboardResponse(
    val period: String,
    val kpis: ManagerKpis,
    val weeklyRevenue: List<WeeklyRevenuePoint>,
    val faultDistribution: List<FaultDistributionEntry>,
)

@Serializable
data class ManagerKpis(
    val completedCount: Int,
    val completedDeltaPct: Double? = null,
    val avgRepairDays: Double? = null,
    val reopenRatePct: Double,
    val avgRating: Double? = null,
    val ratingCount: Int,
)

@Serializable
data class WeeklyRevenuePoint(val weekStart: String, val revenue: Int)

@Serializable
data class FaultDistributionEntry(val deviceType: String, val total: Int, val pct: Double)

// ── Technicians ──────────────────────────────────────────────────────

@Serializable
data class TechniciansResponse(val technicians: List<TechnicianDto>)

@Serializable
data class TechnicianDto(
    val id: Int,
    val name: String,
    val initials: String,
    val specialty: String? = null,
    val distanceKm: Double? = null,
    val tasksToday: Int,
    val busyUntil: String? = null,
)

// ── Customers & devices ─────────────────────────────────────────────

@Serializable
data class CustomerDto(
    val id: Int,
    val name: String,
    val phone: String,
    val address: String? = null,
    val branch: String? = null,
    val devices: List<DeviceDto>? = null,
)

@Serializable
data class CustomerResponse(val customer: CustomerDto?)

@Serializable
data class CreateCustomerRequest(val name: String, val phone: String, val address: String?, val branch: String?)

@Serializable
data class DeviceDto(
    val id: Int,
    val customerId: Int,
    val deviceType: String,
    val brand: String? = null,
    val model: String? = null,
    val serialNumber: String? = null,
    val underWarranty: Boolean,
    val warrantyEnd: String? = null,
)

@Serializable
data class DeviceResponse(val device: DeviceDto)

@Serializable
data class CreateDeviceRequest(
    val customerId: Int,
    val deviceType: String,
    val brand: String?,
    val model: String?,
    val serialNumber: String?,
    val underWarranty: Boolean,
    val warrantyEnd: String?,
)

// ── Work orders ──────────────────────────────────────────────────────

@Serializable
data class WorkOrderResponse(val workOrder: WorkOrderDto)

@Serializable
data class WorkOrderListResponse(val items: List<WorkOrderDto>, val progress: TechnicianProgress)

@Serializable
data class TechnicianProgress(val completed: Int, val total: Int)

@Serializable
data class WorkOrderDto(
    val id: Int,
    val code: String,
    val status: String,
    val statusLabel: String,
    val priority: String,
    val issueDescription: String,
    val branch: String? = null,
    val source: String = "staff",
    val scheduledAt: String? = null,
    val createdAt: String,
    val startedAt: String? = null,
    val completedAt: String? = null,
    val closedAt: String? = null,
    val estimatedCost: Double? = null,
    val quoteNote: String? = null,
    val quotedAt: String? = null,
    val approvedAt: String? = null,
    val rejectReason: String? = null,
    val customer: CustomerRefDto,
    val device: DeviceRefDto,
    val technician: TechnicianRefDto? = null,
    val checklist: List<ChecklistItemDto>,
    val parts: List<PartUsedDto>,
    val invoice: InvoiceDto? = null,
)

@Serializable
data class CustomerRefDto(val id: Int, val name: String, val phone: String, val address: String? = null)

@Serializable
data class DeviceRefDto(
    val id: Int,
    val type: String,
    val brand: String? = null,
    val model: String? = null,
    val serialNumber: String? = null,
    val underWarranty: Boolean,
    val warrantyEnd: String? = null,
)

@Serializable
data class TechnicianRefDto(val id: Int, val name: String, val initials: String)

@Serializable
data class ChecklistItemDto(val id: Int, val label: String, val status: String, val note: String? = null, val orderIndex: Int)

@Serializable
data class PartUsedDto(val id: Int, val inventoryItemId: Int? = null, val name: String, val price: Double, val status: String)

@Serializable
data class InvoiceDto(
    val laborFee: Double,
    val warrantyDiscount: Double,
    val taxRate: Double,
    val paymentMethod: String? = null,
    val signatureName: String? = null,
    val signedAt: String? = null,
    val customerRating: Int? = null,
)

@Serializable
data class CreateWorkOrderRequest(
    val customerId: Int,
    val deviceId: Int,
    val issueDescription: String,
    val priority: String,
    val branch: String?,
)

@Serializable
data class AssignTechnicianRequest(val technicianId: Int, val scheduledAt: String)

@Serializable
data class ChecklistUpdateRequest(val status: String, val note: String? = null)

@Serializable
data class AddPartRequest(val name: String, val inventoryItemId: Int? = null, val status: String = "used")

@Serializable
data class InvoicePreviewResponse(
    val laborFee: Double,
    val partsTotal: Double,
    val warrantyDiscount: Double,
    val taxRate: Double,
    val subtotal: Double,
    val total: Double,
    val parts: List<PartUsedDto>,
)

@Serializable
data class SendQuoteRequest(val estimatedCost: Double, val note: String? = null)

@Serializable
data class RejectQuoteRequest(val reason: String? = null)

@Serializable
data class UpdateWorkOrderRequest(
    val issueDescription: String? = null,
    val priority: String? = null,
    val branch: String? = null,
)

@Serializable
data class UpdateCustomerRequest(
    val name: String? = null,
    val phone: String? = null,
    val address: String? = null,
    val branch: String? = null,
)

@Serializable
data class UpdateDeviceRequest(
    val deviceType: String? = null,
    val brand: String? = null,
    val model: String? = null,
    val serialNumber: String? = null,
    val underWarranty: Boolean? = null,
    val warrantyEnd: String? = null,
)

@Serializable
data class CloseWorkOrderRequest(
    val laborFee: Double,
    val warrantyDiscount: Double,
    val paymentMethod: String,
    val signatureName: String,
    val customerRating: Int? = null,
)

// ── Inventory ────────────────────────────────────────────────────────

@Serializable
data class InventoryResponse(
    val items: List<InventoryItemDto>,
    val pendingPurchaseOrders: Int,
    val consumptionThisMonth: Double,
)

@Serializable
data class InventoryItemDto(
    val id: Int,
    val name: String,
    val sku: String,
    val category: String? = null,
    val price: Double,
    val quantity: Int,
    val reorderThreshold: Int,
    val lowStock: Boolean,
    val reservedForCode: String? = null,
)

@Serializable
data class PurchaseOrderRequest(val quantity: Int = 1, val workOrderId: Int? = null)

// ── Work orders list (الطلبات) ──────────────────────────────────────

@Serializable
data class WorkOrdersListResponse(val total: Int, val limit: Int, val offset: Int, val items: List<WorkOrderSummaryDto>)

@Serializable
data class WorkOrderSummaryDto(
    val id: Int,
    val code: String,
    val status: String,
    val statusLabel: String,
    val priority: String,
    val issueDescription: String,
    val branch: String? = null,
    val source: String = "staff",
    val scheduledAt: String? = null,
    val createdAt: String,
    val closedAt: String? = null,
    val customerName: String,
    val deviceType: String,
    val technicianName: String? = null,
)

// ── Customers (العملاء) ─────────────────────────────────────────────

@Serializable
data class CustomersListResponse(val total: Int, val limit: Int, val offset: Int, val items: List<CustomerSummaryDto>)

@Serializable
data class CustomerSummaryDto(
    val id: Int,
    val name: String,
    val phone: String,
    val address: String? = null,
    val branch: String? = null,
    val deviceCount: Int,
    val workOrderCount: Int,
)

@Serializable
data class CustomerDetailResponse(
    val customer: CustomerDto,
    val devices: List<DeviceDto>,
    val workOrders: List<CustomerWorkOrderDto>,
)

@Serializable
data class CustomerWorkOrderDto(
    val id: Int,
    val code: String,
    val status: String,
    val priority: String,
    val issueDescription: String,
    val deviceType: String,
    val technicianName: String? = null,
    val createdAt: String,
    val closedAt: String? = null,
)

// ── Technicians roster & performance (الفنيون / أدائي) ──────────────

@Serializable
data class TechnicianRosterResponse(val technicians: List<TechnicianRosterDto>)

@Serializable
data class TechnicianRosterDto(
    val id: Int,
    val name: String,
    val initials: String,
    val branch: String? = null,
    val specialty: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val tasksToday: Int,
    val completedThisMonth: Int,
    val avgRating: Double? = null,
    val avgRepairDays: Double? = null,
)

@Serializable
data class TechnicianPerformanceResponse(
    val completedThisMonth: Int,
    val completedLast90Days: Int,
    val avgRating: Double? = null,
    val avgRepairDays: Double? = null,
    val reopenRatePct: Double,
    val recentWorkOrders: List<PerformanceWorkOrderDto>,
)

@Serializable
data class PerformanceWorkOrderDto(
    val id: Int,
    val code: String,
    val deviceType: String,
    val customerName: String,
    val closedAt: String? = null,
    val customerRating: Int? = null,
)

// ── Reports (التقارير) ───────────────────────────────────────────────

@Serializable
data class ReportsResponse(
    val period: String,
    val totals: ReportTotals,
    val byTechnician: List<ReportByTechnician>,
    val byBranch: List<ReportByBranch>,
    val byDeviceType: List<ReportByDeviceType>,
)

@Serializable
data class ReportTotals(val completedCount: Int, val totalRevenue: Int, val avgRepairDays: Double? = null)

@Serializable
data class ReportByTechnician(val id: Int, val name: String, val completedCount: Int, val revenue: Int, val avgRating: Double? = null)

@Serializable
data class ReportByBranch(val branch: String, val completedCount: Int, val revenue: Int)

@Serializable
data class ReportByDeviceType(val deviceType: String, val completedCount: Int, val revenue: Int)

@Serializable
data class ApiErrorResponse(val error: String)
