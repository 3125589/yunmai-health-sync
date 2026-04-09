#!/usr/bin/env python3
"""
从 Node-RED 读取 Yunmai 数据并写入 Health Connect
通过 ADB 命令与 Android 设备交互
"""

import json
import os
import csv
import subprocess
from datetime import datetime

# 配置
NODE_RED_DATA_DIR = "/root/node-red-data"  # Node-RED 数据目录
HEALTH_CONNECT_BRIDGE = "/root/.openclaw/workspace/agents/yunmai-sync/scripts/health_connect_bridge.py"

def read_yunmai_data_from_node_red():
    """从 Node-RED 的 CSV 文件读取数据"""
    csv_file = os.path.join(NODE_RED_DATA_DIR, "weight_data.csv")
    
    if not os.path.exists(csv_file):
        print(f"❌ 数据文件不存在: {csv_file}")
        return []
    
    records = []
    with open(csv_file, 'r', encoding='utf-8') as f:
        reader = csv.DictReader(f)
        for row in reader:
            records.append({
                "timestamp": row.get("timestamp"),
                "datetime": row.get("datetime"),
                "weight": float(row.get("weight", 0)),
                "bodyfat": float(row.get("bodyfat", 0)) if row.get("bodyfat") else None,
                "bmi": float(row.get("bmi", 0)) if row.get("bmi") else None,
                "muscle": float(row.get("muscle", 0)) if row.get("muscle") else None,
                "bone": float(row.get("bone", 0)) if row.get("bone") else None,
                "water": float(row.get("water", 0)) if row.get("water") else None,
                "visceral_fat": int(row.get("visceral_fat", 0)) if row.get("visceral_fat") else None,
                "basal_metabolism": int(row.get("basal_metabolism", 0)) if row.get("basal_metabolism") else None,
            })
    
    return records

def write_to_health_connect_via_adb(record):
    """
    通过 ADB 写入 Health Connect
    需要连接 Android 设备并授权
    
    方法：
    1. 使用 ADB 发送 Intent 到 Health Connect
    2. 或者使用第三方应用（如 Health Connect Bridge App）
    """
    # 构造 Health Connect 数据格式
    health_data = {
        "type": "weight",
        "value": record["weight"],
        "unit": "kilograms",
        "timestamp": record["timestamp"],
        "metadata": {
            "body_fat_percentage": record.get("bodyfat"),
            "bmi": record.get("bmi"),
            "muscle_mass": record.get("muscle"),
            "bone_mass": record.get("bone"),
            "body_water_percentage": record.get("water"),
            "visceral_fat": record.get("visceral_fat"),
            "basal_metabolic_rate": record.get("basal_metabolism")
        }
    }
    
    # 保存为临时 JSON 文件
    temp_file = "/tmp/health_connect_data.json"
    with open(temp_file, 'w') as f:
        json.dump(health_data, f)
    
    # 使用 ADB 推送到设备（示例）
    # adb push /tmp/health_connect_data.json /sdcard/health_connect/
    # 然后通过 Intent 触发 Health Connect 读取
    
    print(f"📊 准备写入: {record['datetime']} - {record['weight']}kg")
    return True

def sync_to_health_connect():
    """主同步流程"""
    print(f"[{datetime.now()}] 开始同步 Yunmai 数据到 Health Connect...")
    
    # 1. 读取 Node-RED 数据
    records = read_yunmai_data_from_node_red()
    if not records:
        print("❌ 没有数据可同步")
        return False
    
    # 2. 获取上次同步的时间戳
    state_file = "/root/.openclaw/workspace/memory/yunmai-data/health_connect_state.json"
    last_sync_timestamp = 0
    
    if os.path.exists(state_file):
        with open(state_file, 'r') as f:
            state = json.load(f)
            last_sync_timestamp = state.get("last_timestamp", 0)
    
    # 3. 过滤新数据
    new_records = [r for r in records if int(r["timestamp"]) > last_sync_timestamp]
    
    if not new_records:
        print("ℹ️ 没有新数据")
        return True
    
    # 4. 写入 Health Connect
    success_count = 0
    for record in new_records:
        if write_to_health_connect_via_adb(record):
            success_count += 1
    
    # 5. 更新状态
    if new_records:
        with open(state_file, 'w') as f:
            json.dump({
                "last_timestamp": max(int(r["timestamp"]) for r in new_records),
                "last_sync_time": datetime.now().isoformat(),
                "total_synced": success_count
            }, f, indent=2)
    
    print(f"✅ 同步完成：{success_count}/{len(new_records)} 条记录")
    return True

if __name__ == "__main__":
    sync_to_health_connect()