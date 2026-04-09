# Yunmai Health Sync - Android App

## 概述

这是一个简单的 Android 后台服务 App，自动从 OpenClaw API 获取云麦体重数据并写入 Health Connect。

## 技术栈

- Kotlin
- WorkManager（后台定时任务）
- Health Connect SDK
- Retrofit（HTTP 请求）

---

## 核心文件

### 1. AndroidManifest.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.yunmai.healthsync">

    <!-- Health Connect 权限 -->
    <uses-permission android:name="android.permission.health.READ_WEIGHT"/>
    <uses-permission android:name="android.permission.health.WRITE_WEIGHT"/>
    <uses-permission android:name="android.permission.health.READ_BODY_FAT"/>
    <uses-permission android:name="android.permission.health.WRITE_BODY_FAT"/>

    <!-- 网络权限 -->
    <uses-permission android:name="android.permission.INTERNET"/>
    
    <!-- 后台任务权限 -->
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED"/>

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="云麦体重同步"
        android:theme="@style/Theme.YunmaiHealthSync">

        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN"/>
                <category android:name="android.intent.category.LAUNCHER"/>
            </intent-filter>
        </activity>

        <!-- Health Connect 权限声明 -->
        <activity
            android:name=".HealthConnectPermissionActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="androidx.health.ACTION_REQUEST_PERMISSIONS"/>
            </intent-filter>
        </activity>
    </application>
</manifest>
```

### 2. build.gradle (app level)

```gradle
plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'
}

android {
    namespace 'com.yunmai.healthsync'
    compileSdk 34

    defaultConfig {
        applicationId "com.yunmai.healthsync"
        minSdk 26
        targetSdk 34
        versionCode 1
        versionName "1.0"
    }

    buildTypes {
        release {
            minifyEnabled false
        }
    }
}

dependencies {
    // Health Connect SDK
    implementation 'androidx.health.connect:connect-client:1.1.0-alpha07'

    // WorkManager（后台任务）
    implementation 'androidx.work:work-runtime-ktx:2.9.0'

    // Retrofit（HTTP 请求）
    implementation 'com.squareup.retrofit2:retrofit:2.9.0'
    implementation 'com.squareup.retrofit2:converter-gson:2.9.0'

    // Coroutines
    implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'

    // UI
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'androidx.lifecycle:lifecycle-runtime-ktx:2.7.0'
}
```

### 3. ApiClient.kt

```kotlin
package com.yunmai.healthsync

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header

interface YunmaiApi {
    @GET("/api/weight/latest")
    suspend fun getLatestWeight(
        @Header("Authorization") auth: String
    ): WeightResponse

    companion object {
        private const val BASE_URL = "https://vps.wangdeye.shop:8895"
        private const val TOKEN = "Bearer yunmai_weight_2026"

        fun create(): YunmaiApi {
            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(YunmaiApi::class.java)
        }
    }
}

data class WeightResponse(
    val success: Boolean,
    val data: WeightData?
)

data class WeightData(
    val datetime: String,
    val weight: Double,
    val bmi: Double?,
    val fat: Double?,
    val muscle: Double?,
    val water: Double?
)
```

### 4. HealthConnectManager.kt

```kotlin
package com.yunmai.healthsync

import android.content.Context
import android.health.connect.HealthConnectManager
import android.health.connect.datatypes.WeightRecord
import android.health.connect.datatypes.BodyFatPercentageRecord
import android.health.connect.datatypes.units.Mass
import android.health.connect.datatypes.units.Percentage
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class HealthConnectHelper(private val context: Context) {

    private val healthConnectManager = context.getSystemService(HealthConnectManager::class.java)

    /**
     * 写入体重数据到 Health Connect
     */
    suspend fun writeWeight(weight: Double, datetime: String): Boolean {
        try {
            val instant = parseDatetime(datetime)
            
            val weightRecord = WeightRecord(
                time = instant,
                weight = Mass.kilograms(weight)
            )

            val records = mutableListOf(weightRecord)
            
            healthConnectManager.insertRecords(records)
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    /**
     * 写入体脂率数据到 Health Connect
     */
    suspend fun writeBodyFat(fat: Double, datetime: String): Boolean {
        try {
            val instant = parseDatetime(datetime)
            
            val bodyFatRecord = BodyFatPercentageRecord(
                time = instant,
                percentage = Percentage(fat)
            )

            healthConnectManager.insertRecords(listOf(bodyFatRecord))
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    private fun parseDatetime(datetime: String): Instant {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        return ZonedDateTime.parse(datetime, formatter).toInstant()
    }
}
```

### 5. SyncWorker.kt（后台定时任务）

```kotlin
package com.yunmai.healthsync

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import kotlinx.coroutines.runBlocking

class WeightSyncWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        return runBlocking {
            try {
                Log.d("WeightSync", "开始同步体重数据...")

                // 1. 从 API 获取数据
                val api = YunmaiApi.create()
                val response = api.getLatestWeight("Bearer yunmai_weight_2026")

                if (!response.success || response.data == null) {
                    Log.e("WeightSync", "API 返回失败")
                    return Result.failure()
                }

                val data = response.data
                Log.d("WeightSync", "获取数据: 体重=${data.weight}kg, 体脂=${data.fat}%")

                // 2. 写入 Health Connect
                val healthHelper = HealthConnectHelper(applicationContext)
                
                val weightSuccess = healthHelper.writeWeight(data.weight, data.datetime)
                val fatSuccess = if (data.fat != null) {
                    healthHelper.writeBodyFat(data.fat, data.datetime)
                } else true

                if (weightSuccess && fatSuccess) {
                    Log.d("WeightSync", "✅ 同步成功!")
                    return Result.success()
                } else {
                    Log.e("WeightSync", "❌ 写入失败")
                    return Result.failure()
                }
            } catch (e: Exception) {
                Log.e("WeightSync", "同步异常: ${e.message}")
                return Result.failure()
            }
        }
    }

    companion object {
        const val WORK_NAME = "weight_sync_work"
    }
}
```

### 6. MainActivity.kt

```kotlin
package com.yunmai.healthsync

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 请求 Health Connect 权限
        requestHealthPermissions()

        // 启动后台定时任务（每天早上 8:30）
        scheduleSyncWork()
    }

    private fun requestHealthPermissions() {
        // Health Connect 权限请求逻辑
        // 用户首次使用需要手动授权
    }

    private fun scheduleSyncWork() {
        val syncWork = PeriodicWorkRequestBuilder<WeightSyncWorker>(
            24, TimeUnit.HOURS  // 每 24 小时执行一次
        ).build()

        WorkManager.getInstance(this).enqueue(syncWork)
    }
}
```

---

## 构建步骤

### 1. 创建项目

```bash
# 使用 Android Studio 创建新项目
# 选择 "Empty Activity" 模板
# 语言选择 Kotlin
# 最低 SDK 选择 26 (Android 8.0)
```

### 2. 配置依赖

将 `build.gradle` 内容复制到项目的 `app/build.gradle`

### 3. 添加代码文件

创建以上 6 个核心文件

### 4. 构建 APK

```bash
# 在 Android Studio 中
# Build → Build Bundle(s) / APK(s) → Build APK(s)
```

### 5. 安装到手机

- 通过 USB 安装
- 或生成签名 APK 分发

---

## 使用流程

1. **安装 App**
2. **首次运行** → 请求 Health Connect 权限
3. **授权后** → App 自动后台运行
4. **每天定时** → 自动从 API 获取数据并写入 Health Connect

---

## API 配置

| 项目 | 值 |
|------|-----|
| **URL** | `https://vps.wangdeye.shop:8895/api/weight/latest` |
| **Token** | `Bearer yunmai_weight_2026` |

---

## 注意事项

- 需要 Android 14+ (Health Connect 支持)
- 用户需要手动授权 Health Connect 权限
- Health Connect App 需要预先安装在手机上

---

*文档更新: 2026-04-09*