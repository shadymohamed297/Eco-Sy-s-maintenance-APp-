package com.markazsayana.app.data.repository

import com.markazsayana.app.data.remote.ApiService
import com.markazsayana.app.data.remote.TechnicianPerformanceResponse
import com.markazsayana.app.data.remote.TechnicianRosterDto
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TechnicianRepository @Inject constructor(private val api: ApiService) {
    suspend fun roster(): List<TechnicianRosterDto> = api.technicianRoster().technicians
    suspend fun myPerformance(): TechnicianPerformanceResponse = api.myPerformance()
}
