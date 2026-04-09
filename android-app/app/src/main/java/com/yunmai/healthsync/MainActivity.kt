package com.yunmai.healthsync

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
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
    private lateinit var btnGrantPermission: Button
    private var healthConnectClient: HealthConnectClient? = null

    // Health Connect 权限
    private val PERMISSIONS = setOf(
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getWritePermission(WeightRecord::class),
        HealthPermission.getReadPermission(BodyFatRecord::class),
        HealthPermission.getWritePermission(BodyFatRecord::class)
    )

    // 权限请求 launcher - 使用官方推荐方式
    private val requestPermissions = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { grantedPermissions ->
        Log.d(TAG, "授权结果: $grantedPermissions")
        
        if (grantedPermissions.containsAll(PERMISSIONS)) {
            tvStatus.text = "✅ 已授权"
            Toast.makeText(this, "权限已授权", Toast.LENGTH_SHORT).show()
        } else {
            tvStatus.text = "⚠️ 权限不完整"
            Toast.makeText(this, "请授权所有权限", Toast.LENGTH_LONG).show()
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
        btnGrantPermission = findViewById(R.id.btnGrantPermission)

        btnSyncNow.setOnClickListener {
            syncNow()
        }

        btnSetupSchedule.setOnClickListener {
            setupDailySchedule()
        }

        btnGrantPermission.setOnClickListener {
            requestPermissions.launch(PERMISSIONS)
        }
    }

    /**
     * 初始化 Health Connect
     */
    private fun initHealthConnect() {
        tvStatus.text = "正在初始化 Health Connect..."
        
        lifecycleScope.launch {
            try {
                // 检查 Health Connect 状态
                val status = HealthConnectClient.getSdkStatus(this@MainActivity)
                
                Log.d(TAG, "Health Connect SDK 状态: $status")
                
                when (status) {
                    HealthConnectClient.SDK_UNAVAILABLE -> {
                        tvStatus.text = "❌ Health Connect 不可用"
                        Toast.makeText(this@MainActivity, "请安装 Health Connect", Toast.LENGTH_LONG).show()
                        return@launch
                    }
                    HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                        tvStatus.text = "⚠️ 请更新 Health Connect"
                        return@launch
                    }
                }
                
                // 创建 Health Connect Client
                healthConnectClient = HealthConnectClient.getOrCreate(this@MainActivity)
                
                Log.d(TAG, "Health Connect Client 创建成功")
                
                // 检查已授权的权限
                val granted = healthConnectClient!!.permissionController.getGrantedPermissions()
                Log.d(TAG, "已授权权限: $granted")
                
                if (!granted.containsAll(PERMISSIONS)) {
                    tvStatus.text = "请点击「授权」按钮"
                } else {
                    tvStatus.text = "✅ 准备就绪"
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "初始化失败", e)
                tvStatus.text = "❌ 初始化失败: ${e.message}"
            }
        }
    }

    /**
     * 立即同步
     */
    private fun syncNow() {
        val client = healthConnectClient
        if (client == null) {
            Toast.makeText(this, "Health Connect 未初始化", Toast.LENGTH_SHORT).show()
            return
        }
        
        tvStatus.text = "正在同步..."
        
        lifecycleScope.launch {
            try {
                // 检查权限
                val granted = client.permissionController.getGrantedPermissions()
                if (!granted.containsAll(PERMISSIONS)) {
                    tvStatus.text = "❌ 缺少权限"
                    requestPermissions.launch(PERMISSIONS)
                    return@launch
                }
                
                // 获取数据
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
                        Toast.makeText(this@MainActivity, "写入失败，请检查权限", Toast.LENGTH_SHORT).show()
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