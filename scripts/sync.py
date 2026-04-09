#!/usr/bin/env python3
"""
Yunmai 数据同步主脚本
- 获取最新体重数据
- 保存到本地 CSV
- 检测新数据
"""

import json
import os
import csv
from datetime import datetime, timedelta
from yunmai_api import YunmaiAPI

# 配置
CONFIG_FILE = "/root/.openclaw/workspace/agents/yunmai-sync/config/credentials.json"
DATA_DIR = "/root/.openclaw/workspace/memory/yunmai-data"
STATE_FILE = f"{DATA_DIR}/sync_state.json"

class YunmaiSync:
    def __init__(self):
        self.api = None
        self.config = self._load_config()
        self.state = self._load_state()
        
        # 确保数据目录存在
        os.makedirs(DATA_DIR, exist_ok=True)
    
    def _load_config(self):
        """加载配置"""
        if os.path.exists(CONFIG_FILE):
            with open(CONFIG_FILE, 'r') as f:
                return json.load(f)
        return {}
    
    def _load_state(self):
        """加载同步状态"""
        if os.path.exists(STATE_FILE):
            with open(STATE_FILE, 'r') as f:
                return json.load(f)
        return {
            "last_sync_time": None,
            "last_weight_time": 0,
            "total_records": 0
        }
    
    def _save_state(self):
        """保存同步状态"""
        with open(STATE_FILE, 'w') as f:
            json.dump(self.state, f, indent=2)
    
    def _save_to_csv(self, records, filename="weight_data.csv"):
        """保存数据到 CSV"""
        filepath = os.path.join(DATA_DIR, filename)
        
        # 检查文件是否存在
        file_exists = os.path.exists(filepath)
        
        with open(filepath, 'a', newline='', encoding='utf-8') as f:
            fieldnames = [
                "timestamp", "datetime", "weight", "bmi", "bodyfat",
                "muscle", "bone", "water", "visceral_fat",
                "basal_metabolism", "protein", "body_age", "scale_name"
            ]
            writer = csv.DictWriter(f, fieldnames=fieldnames)
            
            if not file_exists:
                writer.writeheader()
            
            for record in records:
                writer.writerow(record)
        
        return filepath
    
    def _load_existing_timestamps(self):
        """加载已存在的时间戳，用于去重"""
        filepath = os.path.join(DATA_DIR, "weight_data.csv")
        existing = set()
        
        if os.path.exists(filepath):
            with open(filepath, 'r', encoding='utf-8') as f:
                reader = csv.DictReader(f)
                for row in reader:
                    existing.add(row.get("timestamp"))
        
        return existing
    
    def sync(self, days=10):
        """同步最近 N 天的数据"""
        print(f"[{datetime.now()}] 开始同步 Yunmai 数据...")
        
        # 初始化 API
        account = self.config.get("account")
        password = self.config.get("password")
        
        if not account or not password:
            print("❌ 未配置账号密码")
            return False
        
        self.api = YunmaiAPI(account, password)
        
        # 登录
        if not self.api.login():
            return False
        
        # 计算时间范围
        end_time = int(time.time())
        start_time = int((datetime.now() - timedelta(days=days)).timestamp())
        
        # 获取数据
        weights = self.api.get_weight_list(start_time, end_time, limit=1000)
        if not weights:
            print("ℹ️ 没有新数据")
            return True
        
        # 解析数据
        records = self.api.parse_weight_data(weights)
        
        # 去重
        existing = self._load_existing_timestamps()
        new_records = [r for r in records if str(r["timestamp"]) not in existing]
        
        if new_records:
            # 保存到 CSV
            filepath = self._save_to_csv(new_records)
            
            # 更新状态
            self.state["last_sync_time"] = datetime.now().isoformat()
            self.state["last_weight_time"] = max(r["timestamp"] for r in new_records)
            self.state["total_records"] += len(new_records)
            self._save_state()
            
            print(f"✅ 同步完成：新增 {len(new_records)} 条记录")
            print(f"📁 数据保存至: {filepath}")
            
            # 显示最新记录
            latest = new_records[0]
            print(f"📊 最新记录: {latest['datetime']} - {latest['weight']}kg, 体脂{latest['bodyfat']}%")
            
            return True
        else:
            print("ℹ️ 无新数据（已去重）")
            return True
    
    def get_latest(self):
        """获取最新体重数据"""
        filepath = os.path.join(DATA_DIR, "weight_data.csv")
        if not os.path.exists(filepath):
            return None
        
        with open(filepath, 'r', encoding='utf-8') as f:
            reader = csv.DictReader(f)
            rows = list(reader)
            if rows:
                return rows[-1]
        return None

if __name__ == "__main__":
    import sys
    
    sync = YunmaiSync()
    
    if len(sys.argv) > 1 and sys.argv[1] == "latest":
        # 查看最新数据
        latest = sync.get_latest()
        if latest:
            print(f"最新体重: {latest['weight']}kg ({latest['datetime']})")
        else:
            print("暂无数据")
    else:
        # 执行同步
        days = int(sys.argv[1]) if len(sys.argv) > 1 else 10
        sync.sync(days)