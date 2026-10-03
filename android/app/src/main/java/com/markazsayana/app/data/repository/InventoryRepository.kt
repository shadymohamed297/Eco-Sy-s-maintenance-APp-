package com.markazsayana.app.data.repository

import com.markazsayana.app.data.remote.ApiService
import com.markazsayana.app.data.remote.InventoryResponse
import com.markazsayana.app.data.remote.PurchaseOrderRequest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InventoryRepository @Inject constructor(private val api: ApiService) {
    suspend fun items(filter: String = "all"): InventoryResponse = api.inventory(filter)
    suspend fun createPurchaseOrder(itemId: Int, quantity: Int = 1, workOrderId: Int? = null) =
        api.createPurchaseOrder(itemId, PurchaseOrderRequest(quantity, workOrderId))
}
