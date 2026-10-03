package com.markazsayana.app.data.repository

import com.markazsayana.app.data.remote.ApiService
import com.markazsayana.app.data.remote.CreateEmployeeRequest
import com.markazsayana.app.data.remote.EmployeeDto
import com.markazsayana.app.data.remote.EmployeeResponse
import com.markazsayana.app.data.remote.UpdateEmployeeRequest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EmployeeRepository @Inject constructor(private val api: ApiService) {

    suspend fun list(): List<EmployeeDto> = api.listEmployees().employees

    suspend fun create(
        name: String,
        role: String,
        email: String?,
        phone: String?,
        branch: String?,
        title: String?,
        specialty: String?,
    ): EmployeeResponse = api.createEmployee(
        CreateEmployeeRequest(name, role, email, phone, branch, title, specialty)
    )

    suspend fun setActive(id: Int, active: Boolean): EmployeeDto =
        api.updateEmployee(id, UpdateEmployeeRequest(active = active)).employee

    suspend fun resetPassword(id: Int): EmployeeResponse =
        api.updateEmployee(id, UpdateEmployeeRequest(resetPassword = true))

    suspend fun update(
        id: Int,
        name: String? = null,
        role: String? = null,
        email: String? = null,
        phone: String? = null,
        branch: String? = null,
        title: String? = null,
        specialty: String? = null,
    ): EmployeeDto = api.updateEmployee(
        id,
        UpdateEmployeeRequest(name = name, role = role, email = email, phone = phone, branch = branch, title = title, specialty = specialty)
    ).employee
}
