package com.yunmai.healthsync

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Percentage
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Health Connect 操作管理类
 * 使用 Health Connect SDK 1.1.0
 */
class HealthConnectHelper(context: Context) {

    private val healthConnectClient: HealthConnectClient = 
        HealthConnectClient.getOrCreate(context)

    /**
     * 写入体重数据
     */
    suspend fun writeWeight(weight: Double, datetime: String): Boolean {
        return try {
            val instant = parseDatetime(datetime)
            val zoneOffset = ZoneId.systemDefault().rules.getOffset(instant)
            
            // 创建 Metadata，使用 Device 类型
            val metadata = Metadata(
                recordingMethod = Metadata.RECORDING_METHOD_ACTIVELY_RECORDED,
                device = Device(type = Device.TYPE_UNKNOWN)
            )
            
            val weightRecord = WeightRecord(
                time = instant,
                zoneOffset = zoneOffset,
                weight = Mass.kilograms(weight),
                metadata = metadata
            )

            healthConnectClient.insertRecords(listOf(weightRecord))
            Log.d(TAG, "写入体重成功: ${weight}kg")
            true
        } catch (e: Exception) {
            Log.e(TAG, "写入体重失败: ${e.message}", e)
            false
        }
    }

    /**
     * 写入体脂率数据
     */
    suspend fun writeBodyFat(fat: Double, datetime: String): Boolean {
        return try {
            val instant = parseDatetime(datetime)
            val zoneOffset = ZoneId.systemDefault().rules.getOffset(instant)
            
            val metadata = Metadata(
                recordingMethod = Metadata.RECORDING_METHOD_ACTIVELY_RECORDED,
                device = Device(type = Device.TYPE_UNKNOWN)
            )
            
            val bodyFatRecord = BodyFatRecord(
                time = instant,
                zoneOffset = zoneOffset,
                percentage = Percentage(fat),
                metadata = metadata
            )

            healthConnectClient.insertRecords(listOf(bodyFatRecord))
            Log.d(TAG, "写入体脂成功: ${fat}%")
            true
        } catch (e: Exception) {
            Log.e(TAG, "写入体脂失败: ${e.message}", e)
            false
        }
    }

    /**
     * 写入所有健康数据
     */
    suspend fun writeAllData(data: WeightData): Boolean {
        val weightSuccess = writeWeight(data.weight, data.datetime)
        
        val fatSuccess = if (data.fat != null) {
            writeBodyFat(data.fat, data.datetime)
        } else true
        
        return weightSuccess && fatSuccess
    }

    /**
     * 解析日期时间字符串
     */
    private fun parseDatetime(datetime: String): Instant {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val localDateTime = LocalDateTime.parse(datetime, formatter)
        return localDateTime.atZone(ZoneId.systemDefault()).toInstant()
    }

    companion object {
        private const val TAG = "HealthConnectHelper"
    }
}