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
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.PermissionController
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
    private lateinit var btnOpenHealthConnect: Button
    
    private var healthConnectClient: HealthConnectClient? = null

    // Health Connect 权限
    private val permissions = setOf(
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getWritePermission(WeightRecord::class),
        HealthPermission.getReadPermission(BodyFatRecord::class),
        HealthPermission.getWritePermission(BodyFatRecord::class)
    )

    // 权限请求 (使用标准 API)
    private val requestPermissionLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        Log.d(TAG, "授权结果: $granted")
        Log.d(TAG, "请求的权限: $permissions")
        Log.d(TAG, "是否全部授权: ${granted.containsAll(permissions)}")
        if (granted.containsAll(permissions)) {
            tvStatus.text = "✅ 权限已授权"
            Toast.makeText(this, "权限授权成功！", Toast.LENGTH_SHORT).show()
        } else {
            val missing = permissions - granted
            tvStatus.text = "⚠️ 缺少权限: ${missing.size}"
            Log.w(TAG, "缺少权限: $missing")
            // Android 16 可能需要用户手动到 Health Connect 设置中授权
            Toast.makeText(this, "请到 Health Connect 设置中手动授权", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
    }

    private fun initViews() {
        tvStatus = findViewById(R.id.tvStatus)
        tvLastSync = findViewById(R.id.tvLastSync)
        btnSyncNow = findViewById(R.id.btnSyncNow)
        btnSetupSchedule = findViewById(R.id.btnSetupSchedule)
        btnGrantPermission = findViewById(R.id.btnGrantPermission)
        btnOpenHealthConnect = findViewById(R.id.btnOpenHealthConnect)

        btnSyncNow.setOnClickListener { syncNow() }
        btnSetupSchedule.setOnClickListener { setupDailySchedule() }
        btnGrantPermission.setOnClickListener { requestPermissions() }
        btnOpenHealthConnect.setOnClickListener { openHealthConnectSettings() }
    }

    override fun onResume() {
        super.onResume()
        // 每次恢复时检查 Health Connect 状态
        checkHealthConnect()
    }

    private fun checkHealthConnect() {
        lifecycleScope.launch {
            try {
                // 检查 Health Connect 状态
                val status = HealthConnectClient.getSdkStatus(this@MainActivity)
                
                when (status) {
                    HealthConnectClient.SDK_UNAVAILABLE -> {
                        tvStatus.text = "❌ Health Connect 不可用"
                        return@launch
                    }
                    HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                        tvStatus.text = "⚠️ 请更新 Health Connect"
                        return@launch
                    }
                }

                // 创建或获取 HealthConnectClient
                healthConnectClient = HealthConnectClient.getOrCreate(this@MainActivity)
                
                // 检查已授权的权限
                val granted = healthConnectClient!!.permissionController.getGrantedPermissions()
                
                if (granted.containsAll(permissions)) {
                    tvStatus.text = "✅ 准备就绪"
                } else {
                    val missing = permissions - granted
                    tvStatus.text = "⚠️ 缺少 ${missing.size} 项权限"
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "检查 Health Connect 失败", e)
                tvStatus.text = "❌ 错误: ${e.message}"
            }
        }
    }

    private fun requestPermissions() {
        lifecycleScope.launch {
            try {
                // 确保 HealthConnectClient 已初始化
                if (healthConnectClient == null) {
                    healthConnectClient = HealthConnectClient.getOrCreate(this@MainActivity)
                }
                
                // 启动权限请求
                requestPermissionLauncher.launch(permissions)
                
            } catch (e: Exception) {
                Log.e(TAG, "请求权限失败", e)
                Toast.makeText(this@MainActivity, "请求权限失败: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * 打开 Health Connect 应用权限设置页面
     * Android 16 上可能需要手动授权
     */
    private fun openHealthConnectSettings() {
        try {
            // 打开 Health Connect 应用权限页面
            val intent = Intent(Intent.ACTION_VIEW_PERMISSION_USAGE)
            intent.setPackage("com.google.android.apps.healthdata")
            intent.addCategory(Intent.CATEGORY_HEALTH_PERMISSIONS)
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "无法打开 Health Connect 设置", e)
            // 如果上面的 Intent 失败，尝试打开 Health Connect 主页面
            try {
                val fallbackIntent = Intent()
                fallbackIntent.setPackage("com.google.android.apps.healthdata")
                startActivity(fallbackIntent)
            } catch (e2: Exception) {
                Toast.makeText(this, "请手动打开 Health Connect 设置", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun syncNow() {
        if (healthConnectClient == null) {
            Toast.makeText(this, "Health Connect 未初始化", Toast.LENGTH_SHORT).show()
            return
        }
        
        tvStatus.text = "正在同步..."
        
        lifecycleScope.launch {
            try {
                // 检查权限
                val granted = healthConnectClient!!.permissionController.getGrantedPermissions()
                if (!granted.containsAll(permissions)) {
                    tvStatus.text = "❌ 缺少权限，请先授权"
                    requestPermissions()
                    return@launch
                }
                
                // 获取数据
                val api = YunmaiApi.create()
                val response = api.getLatestWeight(YunmaiApi.TOKEN)

                if (response.success && response.data != null) {
                    val data = response.data
                    
                    val helper = HealthConnectHelper(this@MainActivity)
                    val success = helper.writeAllData(data)

                    if (success) {
                        tvStatus.text = "✅ 同步成功"
                        tvLastSync.text = "体重: ${data.weight}kg | 体脂: ${data.fat}%"
                        Toast.makeText(this@MainActivity, "数据已写入 Health Connect", Toast.LENGTH_SHORT).show()
                    } else {
                        tvStatus.text = "❌ 写入失败"
                    }
                } else {
                    tvStatus.text = "❌ 获取数据失败"
                }
            } catch (e: Exception) {
                tvStatus.text = "❌ 错误: ${e.message}"
                Log.e(TAG, "同步错误", e)
            }
        }
    }

    private fun setupDailySchedule() {
        val dailyWork = PeriodicWorkRequestBuilder<WeightSyncWorker>(24, TimeUnit.HOURS).build()
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