package com.markazsayana.app.data.repository

import android.content.Context
import com.markazsayana.app.data.remote.ApiService
import com.markazsayana.app.data.remote.ReportsResponse
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReportsRepository @Inject constructor(
    private val api: ApiService,
    @ApplicationContext private val context: Context,
) {
    suspend fun reports(period: String): ReportsResponse = api.reports(period)

    /** Downloads the CSV export and saves it under the app's cache dir, ready to share via FileProvider. */
    suspend fun exportCsv(period: String): File = withContext(Dispatchers.IO) {
        val body = api.exportReportCsv(period)
        val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(exportsDir, "report-$period.csv")
        body.byteStream().use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        file
    }
}
