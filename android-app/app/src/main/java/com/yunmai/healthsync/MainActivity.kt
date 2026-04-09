package com.yunmai.healthsync

import android.app.Activity
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

    // Health Connect 权限
    private val PERMISSIONS = setOf(
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getWritePermission(WeightRecord::class),
        HealthPermission.getReadPermission(BodyFatRecord::class),
        HealthPermission.getWritePermission(BodyFatRecord::class)
    )

    // 权限请求码
    private val PERMISSION_REQUEST_CODE = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        
        // 关键：立即初始化并请求 Health Connect 权限
        lifecycleScope.launch {
            initHealthConnectAndRequestPermission()
        }
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
            requestHealthConnectPermission()
        }
    }

    /**
     * 初始化 Health Connect 并请求权限
     */
    private suspend fun initHealthConnectAndRequestPermission() {
        tvStatus.text = "正在初始化 Health Connect..."
        
        try {
            // 获取 Health Connect Client - 这会注册应用到 Health Connect
            healthConnectClient = HealthConnectClient.getOrCreate(this@MainActivity)
            
            Log.d(TAG, "Health Connect Client 创建成功")
            
            // 检查已授权的权限
            val grantedPermissions = healthConnectClient!!.permissionController.getGrantedPermissions()
            
            Log.d(TAG, "已授权权限: $grantedPermissions")
            
            if (!grantedPermissions.containsAll(PERMISSIONS)) {
                tvStatus.text = "需要授权健康权限"
                
                // 自动请求权限
                requestHealthConnectPermission()
            } else {
                tvStatus.text = "准备就绪"
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Health Connect 初始化失败", e)
            tvStatus.text = "初始化失败: ${e.message}"
            
            // 可能是 Health Connect 未安装
            if (e.message?.contains("not available") == true || 
                e.message?.contains("not installed") == true) {
                Toast.makeText(this, "请先安装 Health Connect", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * 请求 Health Connect 权限
     */
    private fun requestHealthConnectPermission() {
        lifecycleScope.launch {
            try {
                val client = healthConnectClient
                if (client == null) {
                    Toast.makeText(this@MainActivity, "请先初始化 Health Connect", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                
                // 使用 Intent 方式请求权限
                val requestPermissionIntent = Intent("androidx.health.ACTION_REQUEST_PERMISSIONS")
                    .putExtra("androidx.health.extra.PERMISSIONS", PERMISSIONS.toTypedArray())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                
                startActivityForResult(requestPermissionIntent, PERMISSION_REQUEST_CODE)
                
            } catch (e: Exception) {
                Log.e(TAG, "请求权限失败", e)
                // 如果 Intent 方式失败，尝试打开系统设置
                openHealthConnectAppSettings()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (resultCode == Activity.RESULT_OK) {
                // 检查权限状态
                lifecycleScope.launch {
                    checkPermissionStatus()
                }
            } else {
                tvStatus.text = "权限请求被取消"
            }
            
            // 重新检查权限状态
            lifecycleScope.launch {
                checkPermissionStatus()
            }
        }
    }

    /**
     * 检查权限状态
     */
    private suspend fun checkPermissionStatus() {
        try {
            val client = healthConnectClient ?: return
            val granted = client.permissionController.getGrantedPermissions()
            
            Log.d(TAG, "当前已授权权限: $granted")
            
            if (granted.containsAll(PERMISSIONS)) {
                tvStatus.text = "准备就绪"
            } else {
                val missing = PERMISSIONS - granted
                tvStatus.text = "缺少权限: ${missing.size} 项"
            }
        } catch (e: Exception) {
            Log.e(TAG, "检查权限失败", e)
        }
    }

    /**
     * 打开 Health Connect 应用设置
     */
    private fun openHealthConnectAppSettings() {
        try {
            // 方法1: 打开 Health Connect 应用详情
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:com.google.android.apps.healthdata")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            Toast.makeText(this, "请在 Health Connect 中授权「云麦体重同步」", Toast.LENGTH_LONG).show()
            
        } catch (e: Exception) {
            // 方法2: 打开应用列表
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_SETTINGS)
                startActivity(intent)
            } catch (e2: Exception) {
                Toast.makeText(this, "请手动打开 Health Connect 授权", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * 立即同步
     */
    private fun syncNow() {
        if (healthConnectClient == null) {
            Toast.makeText(this, "Health Connect 未初始化，请重启应用", Toast.LENGTH_SHORT).show()
            return
        }
        
        tvStatus.text = "正在同步..."
        
        lifecycleScope.launch {
            try {
                // 先检查权限
                val granted = healthConnectClient!!.permissionController.getGrantedPermissions()
                if (!granted.containsAll(PERMISSIONS)) {
                    tvStatus.text = "缺少权限"
                    requestHealthConnectPermission()
                    return@launch
                }
                
                val api = YunmaiApi.create()
                val response = api.getLatestWeight(YunmaiApi.TOKEN)

                if (response.success && response.data != null) {
                    val data = response.data
                    
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
                
                if (e.message?.contains("permission") == true || 
                    e.message?.contains("SecurityException") == true) {
                    requestHealthConnectPermission()
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