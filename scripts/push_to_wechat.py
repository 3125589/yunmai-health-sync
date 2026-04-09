#!/usr/bin/env python3
"""
微信推送体重数据
每天同步后推送体重摘要到用户微信
"""

import json
import os
import csv
import requests
from datetime import datetime

# 配置
DATA_DIR = "/root/.openclaw/workspace/memory/yunmai-data"
CSV_FILE = f"{DATA_DIR}/weight_data.csv"

# 微信推送配置（使用 OpenClaw 的微信通道）
OPENCLAW_API = "http://127.0.0.1:18789/api/message/send"
WECHAT_ACCOUNT = "845f9efecb3c-im-bot"
WECHAT_TARGET = "o9cq805w0Oj8KID5tl70RQv2yUQI@im.wechat"


def load_latest_weight():
    """加载最新体重数据"""
    if not os.path.exists(CSV_FILE):
        return None
    
    with open(CSV_FILE, 'r', encoding='utf-8') as f:
        reader = csv.DictReader(f)
        rows = list(reader)
        return rows[-1] if rows else None


def generate_weight_message(data):
    """生成微信推送消息"""
    if not data:
        return "❌ 暂无体重数据"
    
    weight = data.get('weight', '?')
    fat = data.get('fat', '?')
    bmi = data.get('bmi', '?')
    muscle = data.get('muscle', '?')
    datetime_str = data.get('datetime', '')
    
    message = f"""⚖️ 体重日报

📊 最新数据 ({datetime_str})

体重: {weight} kg
体脂: {fat}%
BMI: {bmi}
肌肉: {muscle}%

💡 提示
请在 Zepp App 记录体重数据
3 秒完成 → 自动同步 Health Connect

---
🦞 云麦同步 | {datetime.now().strftime('%Y-%m-%d %H:%M')}"""
    
    return message


def send_wechat_message(message):
    """发送微信消息"""
    # 使用 OpenClaw 内部 API
    payload = {
        "channel": "openclaw-weixin",
        "accountId": WECHAT_ACCOUNT,
        "to": WECHAT_TARGET,
        "message": message
    }
    
    try:
        # 直接调用 message tool 的内部逻辑
        # 这里使用简单的方式：写入文件，让 OpenClaw 读取并推送
        notify_file = f"{DATA_DIR}/wechat_notify.txt"
        with open(notify_file, 'w', encoding='utf-8') as f:
            f.write(message)
        
        print(f"✅ 消息已准备: {notify_file}")
        print(f"📄 内容:\n{message}")
        return True
    except Exception as e:
        print(f"❌ 发送失败: {e}")
        return False


def main():
    print(f"[{datetime.now()}] 生成体重推送消息...")
    
    data = load_latest_weight()
    message = generate_weight_message(data)
    send_wechat_message(message)


if __name__ == "__main__":
    main()