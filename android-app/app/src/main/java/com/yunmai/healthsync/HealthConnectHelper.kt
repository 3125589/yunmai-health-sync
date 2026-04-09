package com.yunmai.healthsync

import android.content.Context
import android.health.connect.HealthConnectManager
import android.health.connect.datatypes.WeightRecord
import android.health.connect.datatypes.BodyFatPercentageRecord
import android.health.connect.datatypes.units.Mass
import android.health.connect.datatypes.units.Percentage
import android.util.Log
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Health Connect 操作管理类
 */
class HealthConnectHelper(private val context: Context) {

    private val healthConnectManager: HealthConnectManager = 
        context.getSystemService(HealthConnectManager::class.java)

    /**
     * 写入体重数据
     */
    suspend fun writeWeight(weight: Double, datetime: String): Boolean {
        try {
            val instant = parseDatetime(datetime)
            val zoneOffset = ZoneId.systemDefault().rules.getOffset(instant)
            
            val weightRecord = WeightRecord(
                instant,
                zoneOffset,
                Mass.kilograms(weight)
            )

            healthConnectManager.insertRecords(listOf(weightRecord))
            Log.d("HealthConnect", "写入体重成功: ${weight}kg")
            return true
        } catch (e: Exception) {
            Log.e("HealthConnect", "写入体重失败: ${e.message}")
            return false
        }
    }

    /**
     * 写入体脂率数据
     */
    suspend fun writeBodyFat(fat: Double, datetime: String): Boolean {
        try {
            val instant = parseDatetime(datetime)
            val zoneOffset = ZoneId.systemDefault().rules.getOffset(instant)
            
            val bodyFatRecord = BodyFatPercentageRecord(
                instant,
                zoneOffset,
                Percentage(fat)
            )

            healthConnectManager.insertRecords(listOf(bodyFatRecord))
            Log.d("HealthConnect", "写入体脂成功: ${fat}%")
            return true
        } catch (e: Exception) {
            Log.e("HealthConnect", "写入体脂失败: ${e.message}")
            return false
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
}