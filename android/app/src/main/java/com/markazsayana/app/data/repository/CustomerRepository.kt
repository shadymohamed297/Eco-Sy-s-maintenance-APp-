package com.markazsayana.app.data.repository

import com.markazsayana.app.data.remote.ApiService
import com.markazsayana.app.data.remote.CustomerDetailResponse
import com.markazsayana.app.data.remote.CustomerDto
import com.markazsayana.app.data.remote.CustomerSummaryDto
import com.markazsayana.app.data.remote.UpdateCustomerRequest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CustomerRepository @Inject constructor(private val api: ApiService) {
    suspend fun list(search: String? = null): List<CustomerSummaryDto> =
        api.listCustomers(search = search, limit = 100).items

    suspend fun detail(id: Int): CustomerDetailResponse = api.customerDetail(id)

    suspend fun update(id: Int, name: String?, phone: String?, address: String?): CustomerDto =
        api.updateCustomer(id, UpdateCustomerRequest(name = name, phone = phone, address = address)).customer!!
}
