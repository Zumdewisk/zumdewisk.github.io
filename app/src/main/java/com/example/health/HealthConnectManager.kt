package com.example.health

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Energy
import com.example.data.model.WorkoutLog
import com.example.data.model.WorkoutType
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.math.roundToInt

private const val TAG = "HealthConnectManager"
private const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"

enum class HealthConnectAvailability {
    AVAILABLE,
    UPDATE_REQUIRED,
    NOT_SUPPORTED
}

data class HealthConnectWorkoutSummary(
    val id: String,
    val title: String,
    val exerciseTypeName: String,
    val durationMinutes: Int,
    val caloriesBurned: Int,
    val startTimeMillis: Long,
    val isFromNaringAi: Boolean
)

data class HealthConnectDailyData(
    val date: String,
    val steps: Long = 0L,
    val activeCaloriesBurned: Int = 0,
    val workouts: List<HealthConnectWorkoutSummary> = emptyList(),
    val sleepMinutes: Int = 0,
    val sleepQualityNote: String = "",
    val isSynced: Boolean = false,
    val errorMessage: String? = null
)

data class DailyActivityEnergySummary(
    val steps: Long,
    val stepEstimatedCalories: Int,
    val healthConnectActiveCalories: Int,
    val manualWorkoutCalories: Int,
    val totalBurnedCalories: Int,
    val baseCalorieGoal: Int,
    val adjustedCalorieGoal: Int
)

@androidx.annotation.RequiresApi(Build.VERSION_CODES.O)
class HealthConnectManager(private val context: Context) {

    private val appContext = context.applicationContext

    val readPermissions: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class)
    )

    val allPermissions: Set<String> = readPermissions + setOf(
        HealthPermission.getWritePermission(ExerciseSessionRecord::class),
        HealthPermission.getWritePermission(ActiveCaloriesBurnedRecord::class)
    )

    fun checkAvailability(): HealthConnectAvailability {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return HealthConnectAvailability.NOT_SUPPORTED
        }
        return try {
            when (HealthConnectClient.getSdkStatus(appContext, HEALTH_CONNECT_PACKAGE)) {
                HealthConnectClient.SDK_AVAILABLE -> HealthConnectAvailability.AVAILABLE
                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthConnectAvailability.UPDATE_REQUIRED
                else -> HealthConnectAvailability.NOT_SUPPORTED
            }
        } catch (e: Exception) {
            Log.w(TAG, "Health Connect availability check failed: ${e.message}")
            HealthConnectAvailability.NOT_SUPPORTED
        }
    }

    fun getClientOrNull(): HealthConnectClient? {
        if (checkAvailability() != HealthConnectAvailability.AVAILABLE) return null
        return try {
            HealthConnectClient.getOrCreate(appContext)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create HealthConnectClient", e)
            null
        }
    }

    fun createRequestPermissionsContract(): ActivityResultContract<Set<String>, Set<String>> {
        return PermissionController.createRequestPermissionResultContract()
    }

    suspend fun getGrantedPermissions(): Set<String> {
        val client = getClientOrNull() ?: return emptySet()
        return try {
            client.permissionController.getGrantedPermissions()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to query granted permissions", e)
            emptySet()
        }
    }

    suspend fun hasReadPermissions(): Boolean {
        val granted = getGrantedPermissions()
        return granted.containsAll(readPermissions)
    }

    suspend fun hasWritePermissions(): Boolean {
        val granted = getGrantedPermissions()
        return granted.containsAll(allPermissions)
    }

    fun openHealthConnectSettingsOrInstall(activityContext: Context) {
        try {
            val availability = checkAvailability()
            if (availability == HealthConnectAvailability.UPDATE_REQUIRED) {
                val uriString =
                    "market://details?id=$HEALTH_CONNECT_PACKAGE&url=healthconnect%3A%2F%2Fonboarding"
                val storeIntent = Intent(Intent.ACTION_VIEW).apply {
                    setPackage("com.android.vending")
                    data = Uri.parse(uriString)
                    putExtra("overlay", true)
                    putExtra("callerId", activityContext.packageName)
                }
                activityContext.startActivity(storeIntent)
                return
            }

            val hcIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                Intent("android.health.connect.action.HEALTH_HOME_SETTINGS")
            } else {
                Intent("androidx.health.ACTION_HEALTH_CONNECT_SETTINGS")
            }
            activityContext.startActivity(hcIntent)
        } catch (e: Exception) {
            Log.w(TAG, "Could not launch Health Connect settings directly: ${e.message}")
            try {
                val webIntent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=$HEALTH_CONNECT_PACKAGE")
                )
                activityContext.startActivity(webIntent)
            } catch (_: Exception) {
                // Ignore if no browser/Play Store on emulator
            }
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.O)
    suspend fun fetchDailyData(date: LocalDate): HealthConnectDailyData {
        val dateStr = date.toString()
        val client = getClientOrNull()
            ?: return HealthConnectDailyData(
                date = dateStr,
                isSynced = false,
                errorMessage = "Health Connect är inte tillgängligt på den här enheten."
            )

        return try {
            val granted = client.permissionController.getGrantedPermissions()
            val canReadSteps = granted.contains(HealthPermission.getReadPermission(StepsRecord::class))
            val canReadActiveCals =
                granted.contains(HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class))
            val canReadExercise =
                granted.contains(HealthPermission.getReadPermission(ExerciseSessionRecord::class))
            val canReadSleep =
                granted.contains(HealthPermission.getReadPermission(SleepSessionRecord::class))

            if (!canReadSteps && !canReadActiveCals && !canReadExercise && !canReadSleep) {
                return HealthConnectDailyData(
                    date = dateStr,
                    isSynced = false,
                    errorMessage = "Behörighet till Health Connect saknas."
                )
            }

            val zone = ZoneId.systemDefault()
            val startOfDay = date.atStartOfDay(zone).toInstant()
            val endOfDay = date.plusDays(1).atStartOfDay(zone).toInstant()
            val timeRange = TimeRangeFilter.between(startOfDay, endOfDay)

            val metrics = buildSet {
                if (canReadSteps) add(StepsRecord.COUNT_TOTAL)
                if (canReadActiveCals) add(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)
            }

            var stepsTotal = 0L
            var activeKcalTotal = 0

            if (metrics.isNotEmpty()) {
                val aggregated = client.aggregate(
                    AggregateRequest(
                        metrics = metrics,
                        timeRangeFilter = timeRange
                    )
                )
                if (canReadSteps) {
                    stepsTotal = aggregated[StepsRecord.COUNT_TOTAL] ?: 0L
                }
                if (canReadActiveCals) {
                    activeKcalTotal = (aggregated[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories ?: 0.0)
                        .roundToInt()
                        .coerceAtLeast(0)
                }
            }

            val sessions = if (canReadExercise) {
                val records = client.readRecords(
                    ReadRecordsRequest(
                        recordType = ExerciseSessionRecord::class,
                        timeRangeFilter = timeRange
                    )
                ).records

                records.map { session ->
                    val durationMinutes = Duration.between(session.startTime, session.endTime)
                        .toMinutes()
                        .toInt()
                        .coerceAtLeast(1)

                    val sessionCalories = if (canReadActiveCals) {
                        try {
                            val sessionAgg = client.aggregate(
                                AggregateRequest(
                                    metrics = setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL),
                                    timeRangeFilter = TimeRangeFilter.between(session.startTime, session.endTime)
                                )
                            )
                            (sessionAgg[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories ?: 0.0)
                                .roundToInt()
                                .coerceAtLeast(0)
                        } catch (_: Exception) {
                            0
                        }
                    } else {
                        0
                    }

                    val typeName = WorkoutType.swedishNameForHealthConnectType(session.exerciseType)
                    val displayTitle = session.title?.takeIf { it.isNotBlank() } ?: typeName

                    HealthConnectWorkoutSummary(
                        id = session.metadata.id,
                        title = displayTitle,
                        exerciseTypeName = typeName,
                        durationMinutes = durationMinutes,
                        caloriesBurned = sessionCalories,
                        startTimeMillis = session.startTime.toEpochMilli(),
                        isFromNaringAi = session.metadata.dataOrigin.packageName == appContext.packageName
                    )
                }.sortedByDescending { it.startTimeMillis }
            } else {
                emptyList()
            }

            var sleepMinutesTotal = 0
            var sleepNote = ""
            if (canReadSleep) {
                try {
                    val sleepRecords = client.readRecords(
                        ReadRecordsRequest(
                            recordType = SleepSessionRecord::class,
                            timeRangeFilter = timeRange
                        )
                    ).records

                    sleepMinutesTotal = sleepRecords.sumOf { session ->
                        Duration.between(session.startTime, session.endTime).toMinutes().toInt()
                    }.coerceAtLeast(0)

                    sleepNote = when {
                        sleepMinutesTotal >= 480 -> "God sömn (>8h)"
                        sleepMinutesTotal >= 360 -> "Tillräcklig sömn (6-8h)"
                        sleepMinutesTotal > 0 -> "Kort sömn (<6h) - aktiv vila rekommenderas"
                        else -> "Ingen sömn registrerad"
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to read sleep session records", e)
                }
            }

            HealthConnectDailyData(
                date = dateStr,
                steps = stepsTotal,
                activeCaloriesBurned = activeKcalTotal,
                workouts = sessions,
                sleepMinutes = sleepMinutesTotal,
                sleepQualityNote = sleepNote,
                isSynced = true,
                errorMessage = null
            )
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException reading Health Connect data", e)
            HealthConnectDailyData(
                date = dateStr,
                isSynced = false,
                errorMessage = "Behörighet nekad i Health Connect."
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching Health Connect data", e)
            HealthConnectDailyData(
                date = dateStr,
                isSynced = false,
                errorMessage = e.localizedMessage ?: "Kunde inte hämta data från Health Connect."
            )
        }
    }

    suspend fun writeWorkoutSession(
        clientRecordId: String,
        workoutType: WorkoutType,
        title: String,
        durationMinutes: Int,
        caloriesBurned: Int,
        date: LocalDate,
        notes: String
    ): Result<Unit> {
        val client = getClientOrNull()
            ?: return Result.failure(IllegalStateException("Health Connect är inte tillgängligt."))

        return try {
            val granted = client.permissionController.getGrantedPermissions()
            val canWriteExercise =
                granted.contains(HealthPermission.getWritePermission(ExerciseSessionRecord::class))
            if (!canWriteExercise) {
                return Result.failure(SecurityException("Skrivbehörighet för träning saknas i Health Connect."))
            }

            val zone = ZoneId.systemDefault()
            val nowDate = LocalDate.now(zone)
            val endTime = if (date == nowDate) {
                java.time.Instant.now()
            } else {
                date.atTime(17, 0).atZone(zone).toInstant()
            }
            val safeDuration = durationMinutes.coerceIn(1, 600).toLong()
            val startTime = endTime.minus(Duration.ofMinutes(safeDuration))
            val startOffset: ZoneOffset = zone.rules.getOffset(startTime)
            val endOffset: ZoneOffset = zone.rules.getOffset(endTime)

            val records = mutableListOf<androidx.health.connect.client.records.Record>()

            records += ExerciseSessionRecord(
                startTime = startTime,
                startZoneOffset = startOffset,
                endTime = endTime,
                endZoneOffset = endOffset,
                exerciseType = workoutType.healthConnectExerciseType,
                title = title.ifBlank { workoutType.displayName },
                notes = notes.takeIf { it.isNotBlank() },
                metadata = Metadata.manualEntry(
                    clientRecordId = "naringai_exercise_$clientRecordId"
                )
            )

            val canWriteCalories =
                granted.contains(HealthPermission.getWritePermission(ActiveCaloriesBurnedRecord::class))
            if (canWriteCalories && caloriesBurned > 0) {
                records += ActiveCaloriesBurnedRecord(
                    startTime = startTime,
                    startZoneOffset = startOffset,
                    endTime = endTime,
                    endZoneOffset = endOffset,
                    energy = Energy.kilocalories(caloriesBurned.toDouble()),
                    metadata = Metadata.manualEntry(
                        clientRecordId = "naringai_kcal_$clientRecordId"
                    )
                )
            }

            client.insertRecords(records)
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error writing workout session to Health Connect", e)
            Result.failure(e)
        }
    }

    companion object {
        /**
         * Estimates step-based active calories when Health Connect does not provide
         * aggregated ActiveCaloriesBurnedRecord data directly.
         */
        fun estimateStepCalories(steps: Long, weightKg: Double): Int {
            if (steps <= 0L) return 0
            val safeWeight = weightKg.coerceIn(35.0, 250.0)
            // ~0.04 kcal per step at 70 kg
            return (steps * 0.04 * (safeWeight / 70.0)).roundToInt().coerceAtLeast(0)
        }

        /**
         * Combines Health Connect activity data and manual workout logs without double counting.
         */
        fun calculateDailyActivityEnergy(
            baseCalorieGoal: Int,
            weightKg: Double,
            healthConnectData: HealthConnectDailyData?,
            manualWorkouts: List<WorkoutLog>
        ): DailyActivityEnergySummary {
            val steps = healthConnectData?.steps ?: 0L
            val stepEstimatedKcal = estimateStepCalories(steps, weightKg)
            val hcActiveKcal = healthConnectData?.activeCaloriesBurned ?: 0

            // When Health Connect has active calories, it already covers steps and synced workouts.
            val hcContributionKcal = if (hcActiveKcal > 0) hcActiveKcal else stepEstimatedKcal

            // Only count manual workouts not already accounted for by Health Connect active calories
            val manualUnsyncedKcal = if (hcActiveKcal > 0) {
                manualWorkouts.filter { !it.syncedToHealthConnect }.sumOf { it.caloriesBurned }
            } else {
                manualWorkouts.sumOf { it.caloriesBurned }
            }

            val totalBurned = (hcContributionKcal + manualUnsyncedKcal).coerceAtLeast(0)
            val adjustedGoal = (baseCalorieGoal + totalBurned).coerceIn(500, 12000)

            return DailyActivityEnergySummary(
                steps = steps,
                stepEstimatedCalories = stepEstimatedKcal,
                healthConnectActiveCalories = hcActiveKcal,
                manualWorkoutCalories = manualUnsyncedKcal,
                totalBurnedCalories = totalBurned,
                baseCalorieGoal = baseCalorieGoal,
                adjustedCalorieGoal = adjustedGoal
            )
        }
    }
}
