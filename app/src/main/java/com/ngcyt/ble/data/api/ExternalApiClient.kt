package com.ngcyt.ble.data.api

import com.ngcyt.ble.domain.model.ThreatAssessment
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentLinkedQueue

class ExternalApiClient(
    private val endpointUrl: String,
    private val apiKey: String,
    private val mode: ApiMode = ApiMode.ALERTS_ONLY,
    private val batchIntervalSeconds: Int = 30,
) {
    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
        }
    }

    private val pendingBatch = ConcurrentLinkedQueue<Map<String, Any?>>()
    private val failedQueue = ConcurrentLinkedQueue<Map<String, Any?>>()
    private var batchJob: Job? = null

    suspend fun sendAlert(assessment: ThreatAssessment): Boolean {
        if (mode == ApiMode.FULL_TELEMETRY) {
            pendingBatch.add(assessment.toMap())
            return true
        }
        return postWithRetry(assessment.toMap())
    }

    fun recordTelemetry(data: Map<String, Any?>) {
        if (mode == ApiMode.FULL_TELEMETRY) {
            pendingBatch.add(data)
        }
    }

    fun startBatching(scope: CoroutineScope) {
        if (mode != ApiMode.FULL_TELEMETRY) return
        batchJob = scope.launch {
            while (isActive) {
                delay(batchIntervalSeconds * 1000L)
                flushBatch()
            }
        }
    }

    fun stopBatching() {
        batchJob?.cancel()
    }

    suspend fun flushBatch() {
        if (pendingBatch.isEmpty()) return
        val batch = mutableListOf<Map<String, Any?>>()
        while (pendingBatch.isNotEmpty()) {
            pendingBatch.poll()?.let { batch.add(it) }
        }
        val payload = mapOf("events" to batch, "timestamp" to System.currentTimeMillis() / 1000.0)
        if (!postWithRetry(payload)) {
            batch.forEach { failedQueue.add(it) }
        }
    }

    suspend fun retryFailed() {
        val toRetry = mutableListOf<Map<String, Any?>>()
        while (failedQueue.isNotEmpty()) {
            failedQueue.poll()?.let { toRetry.add(it) }
        }
        if (toRetry.isNotEmpty()) {
            val payload = mapOf("events" to toRetry, "timestamp" to System.currentTimeMillis() / 1000.0, "is_retry" to true)
            if (!postWithRetry(payload)) {
                toRetry.forEach { failedQueue.add(it) }
            }
        }
    }

    private suspend fun postWithRetry(payload: Map<String, Any?>, maxRetries: Int = 3): Boolean {
        var delayMs = 1000L
        repeat(maxRetries) { attempt ->
            try {
                val response = client.post(endpointUrl) {
                    contentType(ContentType.Application.Json)
                    header("Authorization", "Bearer $apiKey")
                    setBody(payload)
                }
                if (response.status.isSuccess()) return true
            } catch (_: Exception) {}
            if (attempt < maxRetries - 1) {
                delay(delayMs)
                delayMs *= 2
            }
        }
        return false
    }

    fun getPendingCount(): Int = pendingBatch.size
    fun getFailedCount(): Int = failedQueue.size

    fun close() {
        stopBatching()
        client.close()
    }
}
