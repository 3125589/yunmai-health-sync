package com.yunmai.healthsync

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header

/**
 * 云麦体重 API 接口
 */
interface YunmaiApi {
    @GET("/api/weight/latest")
    suspend fun getLatestWeight(
        @Header("Authorization") auth: String
    ): WeightResponse

    companion object {
        private const val BASE_URL = "https://vps.wangdeye.shop:8895"
        const val TOKEN = "Bearer yunmai_weight_2026"

        fun create(): YunmaiApi {
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(YunmaiApi::class.java)
        }
    }
}

/**
 * API 响应结构
 */
data class WeightResponse(
    val success: Boolean,
    val data: WeightData?
)

/**
 * 体重数据结构
 */
data class WeightData(
    val datetime: String,
    val weight: Double,
    val bmi: Double?,
    val fat: Double?,
    val muscle: Double?,
    val water: Double?
)