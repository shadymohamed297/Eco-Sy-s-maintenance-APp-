package com.markazsayana.app.data.repository

import com.markazsayana.app.data.remote.AddPartRequest
import com.markazsayana.app.data.remote.ApiService
import com.markazsayana.app.data.remote.AssignTechnicianRequest
import com.markazsayana.app.data.remote.ChecklistUpdateRequest
import com.markazsayana.app.data.remote.CloseWorkOrderRequest
import com.markazsayana.app.data.remote.CreateCustomerRequest
import com.markazsayana.app.data.remote.CreateDeviceRequest
import com.markazsayana.app.data.remote.CreateWorkOrderRequest
import com.markazsayana.app.data.remote.CustomerDto
import com.markazsayana.app.data.remote.DeviceDto
import com.markazsayana.app.data.remote.InvoicePreviewResponse
import com.markazsayana.app.data.remote.RejectQuoteRequest
import com.markazsayana.app.data.remote.SendQuoteRequest
import com.markazsayana.app.data.remote.TechnicianDto
import com.markazsayana.app.data.remote.UpdateDeviceRequest
import com.markazsayana.app.data.remote.UpdateWorkOrderRequest
import com.markazsayana.app.data.remote.WorkOrderDto
import com.markazsayana.app.data.remote.WorkOrderListResponse
import com.markazsayana.app.data.remote.WorkOrderSummaryDto
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorkOrderRepository @Inject constructor(private val api: ApiService) {

    suspend fun listWorkOrders(status: String? = null, search: String? = null): List<WorkOrderSummaryDto> =
        api.listWorkOrders(status = status, search = search, limit = 100).items

    suspend fun findCustomerByPhone(phone: String): CustomerDto? = api.findCustomer(phone).customer

    suspend fun createCustomer(name: String, phone: String, address: String?, branch: String?): CustomerDto =
        api.createCustomer(CreateCustomerRequest(name, phone, address, branch)).customer!!

    suspend fun createDevice(
        customerId: Int,
        deviceType: String,
        brand: String?,
        model: String?,
        serialNumber: String?,
        underWarranty: Boolean,
        warrantyEnd: String?,
    ): DeviceDto = api.createDevice(
        CreateDeviceRequest(customerId, deviceType, brand, model, serialNumber, underWarranty, warrantyEnd)
    ).device

    suspend fun createWorkOrder(
        customerId: Int,
        deviceId: Int,
        issueDescription: String,
        priority: String,
        branch: String?,
    ): WorkOrderDto = api.createWorkOrder(
        CreateWorkOrderRequest(customerId, deviceId, issueDescription, priority, branch)
    ).workOrder

    suspend fun availableTechnicians(): List<TechnicianDto> = api.technicians().technicians

    suspend fun assignTechnician(workOrderId: Int, technicianId: Int, scheduledAtIso: String): WorkOrderDto =
        api.assignTechnician(workOrderId, AssignTechnicianRequest(technicianId, scheduledAtIso)).workOrder

    suspend fun myWorkOrders(): WorkOrderListResponse = api.myWorkOrders()

    suspend fun workOrder(id: Int): WorkOrderDto = api.workOrder(id).workOrder

    suspend fun startWorkOrder(id: Int): WorkOrderDto = api.startWorkOrder(id).workOrder

    suspend fun pauseWorkOrder(id: Int): WorkOrderDto = api.pauseWorkOrder(id).workOrder

    suspend fun updateChecklistItem(itemId: Int, status: String, note: String? = null): WorkOrderDto =
        api.updateChecklistItem(itemId, ChecklistUpdateRequest(status, note)).workOrder

    suspend fun addPart(workOrderId: Int, name: String, inventoryItemId: Int?, status: String): WorkOrderDto =
        api.addPart(workOrderId, AddPartRequest(name, inventoryItemId, status)).workOrder

    suspend fun invoicePreview(workOrderId: Int, laborFee: Double, warrantyDiscount: Double?): InvoicePreviewResponse =
        api.invoicePreview(workOrderId, laborFee, warrantyDiscount)

    suspend fun closeWorkOrder(
        workOrderId: Int,
        laborFee: Double,
        warrantyDiscount: Double,
        paymentMethod: String,
        signatureName: String,
        customerRating: Int?,
    ): WorkOrderDto = api.closeWorkOrder(
        workOrderId,
        CloseWorkOrderRequest(laborFee, warrantyDiscount, paymentMethod, signatureName, customerRating)
    ).workOrder

    suspend fun sendQuote(workOrderId: Int, estimatedCost: Double, note: String?): WorkOrderDto =
        api.sendQuote(workOrderId, SendQuoteRequest(estimatedCost, note)).workOrder

    suspend fun approve(workOrderId: Int): WorkOrderDto = api.approveWorkOrder(workOrderId).workOrder

    suspend fun reject(workOrderId: Int, reason: String?): WorkOrderDto =
        api.rejectWorkOrder(workOrderId, RejectQuoteRequest(reason)).workOrder

    suspend fun updateWorkOrder(workOrderId: Int, issueDescription: String?, priority: String?): WorkOrderDto =
        api.updateWorkOrder(workOrderId, UpdateWorkOrderRequest(issueDescription = issueDescription, priority = priority)).workOrder

    suspend fun updateDevice(
        deviceId: Int,
        brand: String?,
        model: String?,
        serialNumber: String?,
        underWarranty: Boolean?,
    ): DeviceDto = api.updateDevice(
        deviceId,
        UpdateDeviceRequest(brand = brand, model = model, serialNumber = serialNumber, underWarranty = underWarranty)
    ).device
}
