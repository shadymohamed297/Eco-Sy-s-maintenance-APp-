package com.markazsayana.app.data.repository

import com.markazsayana.app.data.remote.ApiService
import com.markazsayana.app.data.remote.ManagerDashboardResponse
import com.markazsayana.app.data.remote.ReceptionDashboardResponse
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DashboardRepository @Inject constructor(private val api: ApiService) {
    suspend fun reception(): ReceptionDashboardResponse = api.receptionDashboard()
    suspend fun manager(period: String = "month"): ManagerDashboardResponse = api.managerDashboard(period)
}
