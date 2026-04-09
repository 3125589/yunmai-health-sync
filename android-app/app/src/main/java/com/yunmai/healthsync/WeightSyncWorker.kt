package com.yunmai.healthsync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 后台定时同步任务
 */
class WeightSyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "开始同步体重数据...")

                // 1. 从 API 获取数据
                val api = YunmaiApi.create()
                val response = api.getLatestWeight(YunmaiApi.TOKEN)

                if (!response.success || response.data == null) {
                    Log.e(TAG, "API 返回失败")
                    return@withContext Result.failure()
                }

                val data = response.data
                Log.d(TAG, "获取数据: 体重=${data.weight}kg, 体脂=${data.fat}%")

                // 2. 写入 Health Connect
                val healthHelper = HealthConnectHelper(applicationContext)
                val success = healthHelper.writeAllData(data)

                if (success) {
                    Log.d(TAG, "✅ 同步成功!")
                    Result.success()
                } else {
                    Log.e(TAG, "❌ 写入 Health Connect 失败")
                    Result.failure()
                }

            } catch (e: Exception) {
                Log.e(TAG, "同步异常: ${e.message}", e)
                Result.failure()
            }
        }
    }

    companion object {
        const val TAG = "WeightSyncWorker"
        const val WORK_NAME = "weight_sync_daily"
    }
}