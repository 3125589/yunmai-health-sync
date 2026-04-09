#!/usr/bin/env python3
"""
Yunmai 好轻 API 客户端
基于 Node-RED 方案逆向实现
"""

import requests
import json
import hashlib
import time
import urllib3
from datetime import datetime, timedelta

# 禁用 SSL 警告（Yunmai API 证书有问题）
urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

# 配置
BASE_URL = "https://api.yunmai.com"
APP_KEY = "5aQ9DRs9Z"
APP_VERSION = "YUNMAI_3.0"

class YunmaiAPI:
    def __init__(self, account, encrypted_password):
        self.account = account
        self.password = encrypted_password
        self.user_id = None
        self.refresh_token = None
        self.access_token = None
        self.session = requests.Session()
        self.session.headers.update({
            "User-Agent": "Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X)",
            "Content-Type": "application/json",
            "Accept": "application/json"
        })
        # 禁用 SSL 验证（Yunmai API 证书问题）
        self.session.verify = False
    
    def _generate_sign(self, params):
        """生成 API 签名"""
        # 按 key 排序并拼接
        sorted_params = sorted(params.items())
        sign_str = "".join([f"{k}{v}" for k, v in sorted_params])
        sign_str += APP_KEY
        return hashlib.md5(sign_str.encode()).hexdigest().upper()
    
    def login(self):
        """登录获取 userId 和 refreshToken"""
        params = {
            "account": self.account,
            "password": self.password,
            "appKey": APP_KEY,
            "appVersion": APP_VERSION,
            "time": str(int(time.time()))
        }
        params["sign"] = self._generate_sign(params)
        
        url = f"{BASE_URL}/account/login.json"
        response = self.session.post(url, data=json.dumps(params))
        data = response.json()
        
        if data.get("code") == 200:
            self.user_id = data["data"]["userId"]
            self.refresh_token = data["data"]["refreshToken"]
            self.access_token = data["data"]["accessToken"]
            print(f"✅ 登录成功，userId: {self.user_id}")
            return True
        else:
            print(f"❌ 登录失败: {data}")
            return False
    
    def refresh_access_token(self):
        """刷新 accessToken"""
        if not self.refresh_token:
            print("❌ 没有 refreshToken，请先登录")
            return False
        
        params = {
            "userId": self.user_id,
            "refreshToken": self.refresh_token,
            "appKey": APP_KEY,
            "appVersion": APP_VERSION,
            "time": str(int(time.time()))
        }
        params["sign"] = self._generate_sign(params)
        
        url = f"{BASE_URL}/account/refresh_token.json"
        response = self.session.post(url, data=json.dumps(params))
        data = response.json()
        
        if data.get("code") == 200:
            self.access_token = data["data"]["accessToken"]
            print("✅ Token 刷新成功")
            return True
        else:
            print(f"❌ Token 刷新失败: {data}")
            return False
    
    def get_weight_list(self, start_time=None, end_time=None, limit=100):
        """获取体重数据列表"""
        if not self.access_token:
            print("❌ 未登录")
            return []
        
        if not start_time:
            start_time = int((datetime.now() - timedelta(days=30)).timestamp())
        if not end_time:
            end_time = int(time.time())
        
        params = {
            "userId": self.user_id,
            "accessToken": self.access_token,
            "appKey": APP_KEY,
            "appVersion": APP_VERSION,
            "time": str(int(time.time())),
            "startTime": str(start_time),
            "endTime": str(end_time),
            "limit": str(limit)
        }
        params["sign"] = self._generate_sign(params)
        
        url = f"{BASE_URL}/weight/list.json"
        response = self.session.post(url, data=json.dumps(params))
        data = response.json()
        
        if data.get("code") == 200:
            weights = data.get("data", {}).get("list", [])
            print(f"✅ 获取到 {len(weights)} 条体重记录")
            return weights
        else:
            print(f"❌ 获取体重数据失败: {data}")
            return []
    
    def parse_weight_data(self, weight_list):
        """解析体重数据为结构化格式"""
        records = []
        for item in weight_list:
            record = {
                "timestamp": item.get("time"),
                "datetime": datetime.fromtimestamp(item.get("time", 0)).strftime("%Y-%m-%d %H:%M:%S"),
                "weight": round(item.get("weight", 0) / 1000, 2),  # 转换为 kg
                "bmi": item.get("bmi"),
                "bodyfat": item.get("bodyfat"),  # 体脂率 %
                "muscle": item.get("muscle"),  # 肌肉量 kg
                "bone": item.get("bone"),  # 骨量 kg
                "water": item.get("water"),  # 水分 %
                "visceral_fat": item.get("visceralFat"),  # 内脏脂肪等级
                "basal_metabolism": item.get("basalMetabolism"),  # 基础代谢 kcal
                "protein": item.get("protein"),  # 蛋白质 %
                "body_age": item.get("bodyAge"),  # 身体年龄
                "scale_name": item.get("scaleName", "")  # 设备名称
            }
            records.append(record)
        return records

if __name__ == "__main__":
    # 测试
    account = "15810200327"
    password = "MoviQSg+Goi8hRLaWmeMXJV4iOEXtrZ2UcjMYUt831tMdzlMiJpTJix5skXVllSzopTsfDMHKrjXhk0Dd/9LLg=="
    
    api = YunmaiAPI(account, password)
    if api.login():
        weights = api.get_weight_list(limit=10)
        records = api.parse_weight_data(weights)
        for r in records[:3]:
            print(f"{r['datetime']}: {r['weight']}kg, 体脂{r['bodyfat']}%")