#!/usr/bin/env python3
"""
同步云麦体重数据到 Health Connect
通过 Tasker HTTP Request 触发手机写入
"""

import json
import os
import requests
from datetime import datetime

# 配置
DATA_DIR = "/root/.openclaw/workspace/memory/yunmai-data"
CSV_FILE = f"{DATA_DIR}/weight_data.csv"
STATE_FILE = f"{DATA_DIR}/sync_state.json"

# Tasker HTTP Server 配置（需要在手机上设置）
# 可以使用 Tasker 的 "HTTP Request" 事件触发
# 或者使用第三方服务如 Tasker Net

# 方案 1: 直接 HTTP POST 到手机（需要手机公网可访问或内网穿透）
# TASKER_HTTP_URL = "http://手机IP:端口/health-connect"

# 方案 2: 通过推送服务（如 Pushover, Telegram Bot, 企业微信等）
# 这里我们先生成数据文件，然后通过多种方式推送


def load_latest_weight():
    """读取最新体重数据"""
    import csv
    
    if not os.path.exists(CSV_FILE):
        return None
    
    with open(CSV_FILE, 'r', encoding='utf-8') as f:
        reader = csv.DictReader(f)
        rows = list(reader)
        if rows:
            return rows[-1]  # 返回最后一条（最新的）
    return None


def format_for_health_connect(weight_data):
    """格式化为 Health Connect 兼容格式"""
    return {
        "type": "weight",
        "value": float(weight_data.get("weight", 0)),
        "unit": "kilograms",
        "timestamp": weight_data.get("createTime"),
        "metadata": {
            "body_fat_percentage": float(weight_data.get("fat", 0)) if weight_data.get("fat") else None,
            "bmi": float(weight_data.get("bmi", 0)) if weight_data.get("bmi") else None,
            "muscle_percentage": float(weight_data.get("muscle", 0)) if weight_data.get("muscle") else None,
            "body_water_percentage": float(weight_data.get("water", 0)) if weight_data.get("water") else None,
            "protein_percentage": float(weight_data.get("protein", 0)) if weight_data.get("protein") else None,
            "visceral_fat_level": int(weight_data.get("visFat", 0)) if weight_data.get("visFat") else None,
            "basal_metabolic_rate": float(weight_data.get("bmr", 0)) if weight_data.get("bmr") else None,
            "body_age": int(weight_data.get("somaAge", 0)) if weight_data.get("somaAge") else None
        }
    }


def generate_tasker_payload():
    """生成 Tasker 兼容的 JSON 数据"""
    latest = load_latest_weight()
    if not latest:
        print("❌ 没有体重数据")
        return None
    
    health_data = format_for_health_connect(latest)
    
    # 添加 Tasker 识别字段
    payload = {
        "task": "health_connect_weight",
        "data": health_data
    }
    
    return payload


def save_for_tasker():
    """保存数据文件供 Tasker 读取"""
    payload = generate_tasker_payload()
    if not payload:
        return False
    
    # 保存为 JSON 文件
    output_file = f"{DATA_DIR}/tasker_payload.json"
    with open(output_file, 'w', encoding='utf-8') as f:
        json.dump(payload, f, ensure_ascii=False, indent=2)
    
    print(f"✅ Tasker 数据已保存: {output_file}")
    
    # 同时生成一个简化的文本格式（方便短信/通知推送）
    data = payload["data"]
    summary = (
        f"⚖️ 体重: {data['value']} kg\n"
        f"📊 体脂: {data['metadata']['body_fat_percentage']}%\n"
        f"💪 肌肉: {data['metadata']['muscle_percentage']}%\n"
        f"💧 水分: {data['metadata']['body_water_percentage']}%\n"
        f"🔥 内脏脂肪: {data['metadata']['visceral_fat_level']}\n"
        f"⏰ 时间: {data['timestamp']}"
    )
    
    summary_file = f"{DATA_DIR}/weight_summary.txt"
    with open(summary_file, 'w', encoding='utf-8') as f:
        f.write(summary)
    
    print(f"✅ 摘要已保存: {summary_file}")
    print(f"\n{summary}")
    
    return True


def push_via_syncthing():
    """通过 Syncthing 同步到手机（如果配置了）"""
    # 检查 Syncthing 是否配置
    # 手机上的 Syncthing 可以监控文件夹变化
    # 当文件变化时，Tasker 可以读取并写入 Health Connect
    pass


def main():
    print(f"[{datetime.now()}] 生成 Health Connect 同步数据...")
    save_for_tasker()


if __name__ == "__main__":
    main()