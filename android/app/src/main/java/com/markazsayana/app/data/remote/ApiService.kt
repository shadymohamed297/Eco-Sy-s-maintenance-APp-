package com.markazsayana.app.data.remote

import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

interface ApiService {

    @POST("api/auth/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @GET("api/auth/me")
    suspend fun me(): MeResponse

    @POST("api/auth/change-password")
    suspend fun changePassword(@Body request: ChangePasswordRequest): Unit

    @GET("api/employees")
    suspend fun listEmployees(): EmployeesListResponse

    @POST("api/employees")
    suspend fun createEmployee(@Body request: CreateEmployeeRequest): EmployeeResponse

    @PATCH("api/employees/{id}")
    suspend fun updateEmployee(@Path("id") id: Int, @Body request: UpdateEmployeeRequest): EmployeeResponse

    @GET("api/dashboard/reception")
    suspend fun receptionDashboard(): ReceptionDashboardResponse

    @GET("api/dashboard/manager")
    suspend fun managerDashboard(@Query("period") period: String): ManagerDashboardResponse

    @GET("api/technicians")
    suspend fun technicians(): TechniciansResponse

    @GET("api/work-orders/customers")
    suspend fun findCustomer(@Query("phone") phone: String): CustomerResponse

    @POST("api/work-orders/customers")
    suspend fun createCustomer(@Body request: CreateCustomerRequest): CustomerResponse

    @POST("api/work-orders/devices")
    suspend fun createDevice(@Body request: CreateDeviceRequest): DeviceResponse

    @POST("api/work-orders")
    suspend fun createWorkOrder(@Body request: CreateWorkOrderRequest): WorkOrderResponse

    @PATCH("api/work-orders/{id}/assign")
    suspend fun assignTechnician(@Path("id") id: Int, @Body request: AssignTechnicianRequest): WorkOrderResponse

    @GET("api/work-orders/mine")
    suspend fun myWorkOrders(): WorkOrderListResponse

    @GET("api/work-orders/{id}")
    suspend fun workOrder(@Path("id") id: Int): WorkOrderResponse

    @POST("api/work-orders/{id}/start")
    suspend fun startWorkOrder(@Path("id") id: Int): WorkOrderResponse

    @POST("api/work-orders/{id}/pause")
    suspend fun pauseWorkOrder(@Path("id") id: Int): WorkOrderResponse

    @PATCH("api/work-orders/checklist-items/{itemId}")
    suspend fun updateChecklistItem(@Path("itemId") itemId: Int, @Body request: ChecklistUpdateRequest): WorkOrderResponse

    @POST("api/work-orders/{id}/parts")
    suspend fun addPart(@Path("id") id: Int, @Body request: AddPartRequest): WorkOrderResponse

    @GET("api/work-orders/{id}/invoice-preview")
    suspend fun invoicePreview(
        @Path("id") id: Int,
        @Query("laborFee") laborFee: Double,
        @Query("warrantyDiscount") warrantyDiscount: Double? = null,
    ): InvoicePreviewResponse

    @POST("api/work-orders/{id}/close")
    suspend fun closeWorkOrder(@Path("id") id: Int, @Body request: CloseWorkOrderRequest): WorkOrderResponse

    @PATCH("api/work-orders/{id}/quote")
    suspend fun sendQuote(@Path("id") id: Int, @Body request: SendQuoteRequest): WorkOrderResponse

    @POST("api/work-orders/{id}/approve")
    suspend fun approveWorkOrder(@Path("id") id: Int): WorkOrderResponse

    @POST("api/work-orders/{id}/reject")
    suspend fun rejectWorkOrder(@Path("id") id: Int, @Body request: RejectQuoteRequest): WorkOrderResponse

    @PATCH("api/work-orders/{id}")
    suspend fun updateWorkOrder(@Path("id") id: Int, @Body request: UpdateWorkOrderRequest): WorkOrderResponse

    @PATCH("api/work-orders/devices/{deviceId}")
    suspend fun updateDevice(@Path("deviceId") deviceId: Int, @Body request: UpdateDeviceRequest): DeviceResponse

    @PATCH("api/customers/{id}")
    suspend fun updateCustomer(@Path("id") id: Int, @Body request: UpdateCustomerRequest): CustomerResponse

    @GET("api/inventory")
    suspend fun inventory(@Query("filter") filter: String = "all"): InventoryResponse

    @POST("api/inventory/{id}/purchase-order")
    suspend fun createPurchaseOrder(@Path("id") id: Int, @Body request: PurchaseOrderRequest): Unit

    @GET("api/work-orders")
    suspend fun listWorkOrders(
        @Query("status") status: String? = null,
        @Query("branch") branch: String? = null,
        @Query("search") search: String? = null,
        @Query("limit") limit: Int? = null,
        @Query("offset") offset: Int? = null,
    ): WorkOrdersListResponse

    @GET("api/customers")
    suspend fun listCustomers(
        @Query("search") search: String? = null,
        @Query("limit") limit: Int? = null,
        @Query("offset") offset: Int? = null,
    ): CustomersListResponse

    @GET("api/customers/{id}")
    suspend fun customerDetail(@Path("id") id: Int): CustomerDetailResponse

    @GET("api/technicians/roster")
    suspend fun technicianRoster(): TechnicianRosterResponse

    @GET("api/technicians/me/performance")
    suspend fun myPerformance(): TechnicianPerformanceResponse

    @GET("api/reports")
    suspend fun reports(@Query("period") period: String = "month"): ReportsResponse

    @Streaming
    @GET("api/reports/export")
    suspend fun exportReportCsv(@Query("period") period: String = "month"): ResponseBody
}
