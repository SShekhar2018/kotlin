// File: kotlin-lib/src/main/java/com/example/hc/HealthCore.kt
@file:JvmName("HealthCore")

package com.example.hc

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.changes.Change
import androidx.health.connect.client.changes.GetChangesRequest
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.HeightRecord
import androidx.health.connect.client.aggregate.AggregationResult
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.permission.HealthPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant

object HealthCore {

    fun isAvailable(context: Context): Boolean {
        return HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
    }

    fun client(context: Context): HealthConnectClient =
        HealthConnectClient.getOrCreate(context)

    /** The default set of READ permissions you intend to request. Add/remove as needed. */
    fun defaultPermissions(): Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(HeightRecord::class)
    )

    /** Returns true if ALL defaultPermissions() are currently granted. */
    suspend fun hasAllPermissions(
        context: Context,
        permissions: Set<String> = defaultPermissions()
    ): Boolean = withContext(Dispatchers.IO) {
        val granted = client(context).permissionController.getGrantedPermissions()
        granted.containsAll(permissions)
    }

    /** Aggregated steps (avoid double count) for [start, end). */
    suspend fun getSteps(
        context: Context,
        start: Instant,
        end: Instant
    ): Long = withContext(Dispatchers.IO) {
        val result: AggregationResult = client(context).aggregate(
            AggregateRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(start, end)
            )
        )
        (result[StepsRecord.COUNT_TOTAL] ?: 0L)
    }

    /** Heart rate samples within range (capped by [limit]). */
    suspend fun getHeartRates(
        context: Context,
        start: Instant,
        end: Instant,
        limit: Int = 500
    ): List<HeartRateRecord.Sample> = withContext(Dispatchers.IO) {
        val records = client(context).readRecords(
            HeartRateRecord::class,
            TimeRangeFilter.between(start, end)
        )
        records.records.flatMap { it.samples }.sortedBy { it.time }.takeLast(limit)
    }

    /** Sleep sessions within range. */
    suspend fun getSleepSessions(
        context: Context,
        start: Instant,
        end: Instant
    ): List<SleepSessionRecord> = withContext(Dispatchers.IO) {
        client(context).readRecords(
            SleepSessionRecord::class,
            TimeRangeFilter.between(start, end)
        ).records
    }

    /** Total distance meters in range. */
    suspend fun getDistanceMeters(
        context: Context,
        start: Instant,
        end: Instant
    ): Double = withContext(Dispatchers.IO) {
        val result: AggregationResult = client(context).aggregate(
            AggregateRequest(
                metrics = setOf(DistanceRecord.DISTANCE_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(start, end)
            )
        )
        (result[DistanceRecord.DISTANCE_TOTAL]?.inMeters ?: 0.0)
    }

    /** Active calories burned (kcal) in range. */
    suspend fun getActiveCalories(
        context: Context,
        start: Instant,
        end: Instant
    ): Double = withContext(Dispatchers.IO) {
        val result: AggregationResult = client(context).aggregate(
            AggregateRequest(
                metrics = setOf(ActiveCaloriesBurnedRecord.ENERGY_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(start, end)
            )
        )
        (result[ActiveCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories ?: 0.0)
    }

    /** Latest body metrics (weight/height) recent-first. */
    suspend fun getLatestBodyMetrics(
        context: Context,
        start: Instant,
        end: Instant,
        maxPerType: Int = 3
    ): Map<String, List<String>> = withContext(Dispatchers.IO) {
        val weights = client(context).readRecords(
            WeightRecord::class, TimeRangeFilter.between(start, end)
        ).records.sortedByDescending { it.time }.take(maxPerType).map {
            "${it.weight.inKilograms} kg @ ${it.time}"
        }
        val heights = client(context).readRecords(
            HeightRecord::class, TimeRangeFilter.between(start, end)
        ).records.sortedByDescending { it.time }.take(maxPerType).map {
            "${it.height.inMeters} m @ ${it.time}"
        }
        mapOf("weights" to weights, "heights" to heights)
    }

    // ----- Change log tokens for incremental sync -----
    suspend fun getChangeToken(context: Context): String = withContext(Dispatchers.IO) {
        client(context).changesTokenStore.getChangeToken(
            setOf(
                StepsRecord::class,
                HeartRateRecord::class,
                SleepSessionRecord::class,
                DistanceRecord::class,
                ActiveCaloriesBurnedRecord::class
            )
        )
    }

    suspend fun getChangesAndNextToken(
        context: Context,
        token: String
    ): Pair<List<Change>, String?> = withContext(Dispatchers.IO) {
        val resp = client(context).getChanges(GetChangesRequest(token))
        Pair(resp.changes, resp.nextChangesToken)
    }
}
