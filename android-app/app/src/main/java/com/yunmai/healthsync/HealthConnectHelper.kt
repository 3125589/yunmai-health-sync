package com.yunmai.healthsync

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Percentage
import android.util.Log
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Health Connect 操作管理类
 * 使用 Jetpack Health Connect Client
 */
class HealthConnectHelper(private val context: Context) {

    private val healthConnectClient: HealthConnectClient = 
        HealthConnectClient.getOrCreate(context)

    /**
     * 写入体重数据
     */
    suspend fun writeWeight(weight: Double, datetime: String): Boolean {
        try {
            val instant = parseDatetime(datetime)
            
            val weightRecord = WeightRecord(
                time = instant,
                weight = Mass.kilograms(weight)
            )

            healthConnectClient.insertRecords(listOf(weightRecord))
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
            
            val bodyFatRecord = BodyFatRecord(
                time = instant,
                percentage = Percentage(fat)
            )

            healthConnectClient.insertRecords(listOf(bodyFatRecord))
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