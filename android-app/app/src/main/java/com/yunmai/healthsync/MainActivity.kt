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
            openHealthConnectSettings()
        }
    }

    /**
     * 初始化 Health Connect
     */
    private fun initHealthConnect() {
        tvStatus.text = "正在初始化..."
        
        lifecycleScope.launch {
            try {
                // 获取 Health Connect Client
                healthConnectClient = HealthConnectClient.getOrCreate(this@MainActivity)
                tvStatus.text = "准备就绪"
                
            } catch (e: Exception) {
                Log.e(TAG, "初始化失败: ${e.message}", e)
                tvStatus.text = "请先安装 Health Connect"
            }
        }
    }

    /**
     * 打开 Health Connect 设置页面授权
     */
    private fun openHealthConnectSettings() {
        try {
            // 方法1: 打开 Health Connect 应用详情页
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:com.google.android.apps.healthdata")
            }
            startActivity(intent)
            Toast.makeText(this, "请在 Health Connect 中授权此应用", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            // 方法2: 打开所有应用列表
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_SETTINGS)
                startActivity(intent)
                Toast.makeText(this, "请找到 Health Connect 并授权", Toast.LENGTH_LONG).show()
            } catch (e2: Exception) {
                Toast.makeText(this, "请手动在系统设置中授权 Health Connect", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * 立即同步
     */
    private fun syncNow() {
        if (healthConnectClient == null) {
            Toast.makeText(this, "Health Connect 未初始化", Toast.LENGTH_SHORT).show()
            return
        }
        
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
                        // 提示用户授权
                        openHealthConnectSettings()
                    }
                } else {
                    tvStatus.text = "❌ API 失败"
                }
            } catch (e: Exception) {
                tvStatus.text = "❌ 错误: ${e.message}"
                Log.e(TAG, "同步错误", e)
                
                // 如果是权限问题，提示用户授权
                if (e.message?.contains("permission") == true || 
                    e.message?.contains("SecurityException") == true) {
                    openHealthConnectSettings()
                }
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