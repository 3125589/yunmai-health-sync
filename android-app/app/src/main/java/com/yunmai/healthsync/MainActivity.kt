package com.yunmai.healthsync

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.lifecycle.lifecycleScope
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * 主界面
 */
class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvLastSync: TextView
    private lateinit var btnSyncNow: Button
    private lateinit var btnSetupSchedule: Button
    private lateinit var healthConnectClient: HealthConnectClient

    // Health Connect 权限
    private val permissions = setOf(
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getWritePermission(WeightRecord::class),
        HealthPermission.getReadPermission(BodyFatRecord::class),
        HealthPermission.getWritePermission(BodyFatRecord::class)
    )

    // 权限请求 launcher
    private val requestPermissionsActivity = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        if (granted.containsAll(permissions)) {
            Toast.makeText(this, "权限已授权", Toast.LENGTH_SHORT).show()
            tvStatus.text = "准备就绪"
        } else {
            Toast.makeText(this, "需要授权健康权限才能使用", Toast.LENGTH_LONG).show()
            tvStatus.text = "请授权健康权限"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        initHealthConnect()
    }

    private fun initViews() {
        tvStatus = findViewById(R.id.tvStatus)
        tvLastSync = findViewById(R.id.tvLastSync)
        btnSyncNow = findViewById(R.id.btnSyncNow)
        btnSetupSchedule = findViewById(R.id.btnSetupSchedule)

        btnSyncNow.setOnClickListener {
            syncNow()
        }

        btnSetupSchedule.setOnClickListener {
            setupDailySchedule()
        }
    }

    /**
     * 初始化 Health Connect
     */
    private fun initHealthConnect() {
        tvStatus.text = "正在初始化..."
        
        lifecycleScope.launch {
            try {
                // 检查 Health Connect 是否可用
                val availabilityStatus = HealthConnectClient.getSdkStatus(this@MainActivity)
                
                if (availabilityStatus == HealthConnectClient.SDK_UNAVAILABLE) {
                    tvStatus.text = "Health Connect 不可用"
                    Toast.makeText(this@MainActivity, "请先安装 Health Connect", Toast.LENGTH_LONG).show()
                    return@launch
                }
                
                if (availabilityStatus == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED) {
                    tvStatus.text = "请更新 Health Connect"
                    // 可以引导用户去更新
                    return@launch
                }

                // 获取 Health Connect Client
                healthConnectClient = HealthConnectClient.getOrCreate(this@MainActivity)

                // 检查权限
                val granted = healthConnectClient.permissionController.getGrantedPermissions()
                
                if (!granted.containsAll(permissions)) {
                    // 请求权限
                    tvStatus.text = "请授权健康权限"
                    requestPermissionsActivity.launch(permissions)
                } else {
                    tvStatus.text = "准备就绪"
                }
            } catch (e: Exception) {
                Log.e(TAG, "初始化失败: ${e.message}", e)
                tvStatus.text = "初始化失败: ${e.message}"
            }
        }
    }

    /**
     * 立即同步
     */
    private fun syncNow() {
        tvStatus.text = "正在同步..."
        
        lifecycleScope.launch {
            try {
                val api = YunmaiApi.create()
                val response = api.getLatestWeight(YunmaiApi.TOKEN)

                if (response.success && response.data != null) {
                    val data = response.data
                    
                    // 写入 Health Connect
                    val healthHelper = HealthConnectHelper(this@MainActivity)
                    val success = healthHelper.writeAllData(data)

                    if (success) {
                        tvStatus.text = "✅ 同步成功"
                        tvLastSync.text = "体重: ${data.weight}kg | 体脂: ${data.fat}%"
                        Toast.makeText(this@MainActivity, "数据已写入 Health Connect", Toast.LENGTH_SHORT).show()
                    } else {
                        tvStatus.text = "❌ 写入失败"
                        Toast.makeText(this@MainActivity, "请检查 Health Connect 权限", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    tvStatus.text = "❌ API 失败"
                }
            } catch (e: Exception) {
                tvStatus.text = "❌ 错误: ${e.message}"
                Log.e(TAG, "同步错误", e)
            }
        }
    }

    /**
     * 设置定时任务
     */
    private fun setupDailySchedule() {
        val dailyWork = PeriodicWorkRequestBuilder<WeightSyncWorker>(
            24, TimeUnit.HOURS
        ).build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            WeightSyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            dailyWork
        )

        Toast.makeText(this, "已设置每天自动同步", Toast.LENGTH_SHORT).show()
        tvStatus.text = "定时任务已启动"
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}