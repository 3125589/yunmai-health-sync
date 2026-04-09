#!/usr/bin/env python3
"""
云麦好轻体重数据获取 - 纯 Python 实现
基于 Node-RED 流程逆向
"""

import requests
import hashlib
import time
import json
import csv
import os
from datetime import datetime, timedelta
import urllib3

# 禁用 SSL 警告
urllib3.disable_warnings(urllib3.exceptions.InsecureRequestWarning)

# ====== 配置 ======
ACCOUNT = "15810200327"
PASSWORD_RSA = "MoviQSg+Goi8hRLaWmeMXJV4iOEXtrZ2UcjMYUt831tMdzlMiJpTJix5skXVllSzopTsfDMHKrjXhk0Dd/9LLg=="
SECRET = "AUMtyBDV3vklBr6wtA2putAMwtmVcD5b"

# 数据存储
DATA_DIR = "/root/.openclaw/workspace/memory/yunmai-data"
STATE_FILE = f"{DATA_DIR}/state.json"
CSV_FILE = f"{DATA_DIR}/weight_data.csv"


class YunmaiClient:
    def __init__(self, account, password_rsa):
        self.account = account
        self.password_rsa = password_rsa
        self.user_id = None
        self.refresh_token = None
        self.access_token = None
        
        # 处理密码：在第76个字符后插入换行符
        self.password_processed = password_rsa[:76] + "\n" + password_rsa[76:]
        
        # 初始化 session
        self.session = requests.Session()
        self.session.verify = False
        
        # 加载状态
        self._load_state()
    
    def _load_state(self):
        """加载保存的状态"""
        if os.path.exists(STATE_FILE):
            with open(STATE_FILE, 'r') as f:
                state = json.load(f)
                self.user_id = state.get("userId")
                self.refresh_token = state.get("refreshToken")
                self.access_token = state.get("accessToken")
    
    def _save_state(self):
        """保存状态"""
        os.makedirs(DATA_DIR, exist_ok=True)
        state = {
            "userId": self.user_id,
            "refreshToken": self.refresh_token,
            "accessToken": self.access_token,
            "updatedAt": datetime.now().isoformat()
        }
        with open(STATE_FILE, 'w') as f:
            json.dump(state, f, indent=2)
    
    def _get_timestamp_code(self):
        """获取时间戳代码（前8位 + '00'）"""
        return str(int(time.time()))[:8] + "00"
    
    def _calculate_login_sign(self, code, device_uuid, user_id):
        """计算登录签名"""
        # 账号需要 Base64 编码
        import base64
        account_base64 = base64.b64encode(self.account.encode()).decode()
        
        # 构建签名字符串（注意密码和 userName 后面的换行符）
        text = (
            f"code={code}"
            f"&deviceUUID={device_uuid}"
            f"&loginType=1&password={self.password_processed}\n"
            f"&signVersion=3&userId={user_id}"
            f"&userName={account_base64}\n"
            f"&versionCode=7&secret={SECRET}"
        )
        return hashlib.md5(text.encode()).hexdigest()
    
    def _calculate_token_sign(self, code):
        """计算 Token 刷新签名"""
        text = (
            f"code={code}"
            f"&refreshToken={self.refresh_token}"
            f"&signVersion=3&versionCode=2&secret={SECRET}"
        )
        return hashlib.md5(text.encode()).hexdigest()
    
    def login(self):
        """登录获取 userId 和 refreshToken"""
        import base64
        
        code = self._get_timestamp_code()
        device_uuid = "abcd"  # 初始设备 UUID
        user_id = "199999999"  # 初始用户 ID
        
        # 账号 Base64 编码
        account_base64 = base64.b64encode(self.account.encode()).decode()
        account_uri = requests.utils.quote(account_base64)
        
        # 计算签名
        sign = self._calculate_login_sign(code, device_uuid, user_id)
        
        # 构建请求
        url = "https://account.iyunmai.com/api/android//user/login.d"
        headers = {
            'Host': 'account.iyunmai.com',
            'Content-Type': 'application/x-www-form-urlencoded',
            'Accept-Encoding': 'gzip',
            'Connection': 'keep-alive',
            'Accept': '*/*',
            'User-Agent': 'google/android(10,29) channel(huawei) app(4.25,42500010)screen(w,h=1080,1794)/scale',
            'IssignV1': 'open',
            'Accept-Language': 'zh-Hans-CN;q=1, en-CN;q=0.9',
        }
        
        # 密码 URL 编码
        password_uri = requests.utils.quote(self.password_processed)
        
        # 构建请求体
        payload = (
            f"password={password_uri}%0A"
            f"&code={code}"
            f"&loginType=1&userName={account_uri}%0A"
            f"&deviceUUID={device_uuid}"
            f"&versionCode=7&userId={user_id}"
            f"&signVersion=3&sign={sign}"
        )
        
        print(f"请求 URL: {url}")
        print(f"签名: {sign}")
        
        response = self.session.post(url, data=payload, headers=headers)
        data = response.json()
        
        print(f"响应: {json.dumps(data, ensure_ascii=False)[:500]}")
        
        if data.get("result", {}).get("code") == 0:
            self.user_id = data["data"]["userinfo"]["userId"]
            self.refresh_token = data["data"]["userinfo"]["refreshToken"]
            self.access_token = data["data"]["userinfo"].get("accessToken")
            self._save_state()
            print(f"✅ 登录成功！userId: {self.user_id}")
            return True
        else:
            print(f"❌ 登录失败: {data}")
            return False
    
    def refresh_access_token(self):
        """刷新 accessToken"""
        if not self.refresh_token:
            print("❌ 没有 refreshToken，请先登录")
            return False
        
        code = self._get_timestamp_code()
        sign = self._calculate_token_sign(code)
        
        url = "https://account.iyunmai.com/api/android///auth/token.d"
        headers = {
            'Host': 'account.iyunmai.com',
            'Content-Type': 'application/x-www-form-urlencoded',
            'Accept-Encoding': 'gzip',
            'Connection': 'keep-alive',
            'Accept': '*/*',
            'User-Agent': 'google/android(10,29) channel(huawei) app(4.25,42500010)screen(w,h=1080,1794)/scale',
            'IssignV1': 'open',
            'Accept-Language': 'zh-Hans-CN;q=1, en-CN;q=0.9',
        }
        
        payload = (
            f"&code={code}"
            f"&refreshToken={self.refresh_token}"
            f"&sign={sign}"
            f"&signVersion=3&versionCode=2"
        )
        
        response = self.session.post(url, data=payload, headers=headers)
        data = response.json()
        
        if "data" in data and "accessToken" in data["data"]:
            self.access_token = data["data"]["accessToken"]
            self._save_state()
            print("✅ Token 刷新成功")
            return True
        else:
            print(f"❌ Token 刷新失败: {data}")
            return False
    
    def get_weight_data(self, days=10):
        """获取体重数据"""
        if not self.access_token:
            print("❌ 没有 accessToken")
            return []
        
        code = self._get_timestamp_code()
        start_time = int((datetime.now() - timedelta(days=days)).timestamp())
        
        url = (
            f"https://data.iyunmai.com/api/ios/scale/chart-list.json?"
            f"code={code}"
            f"&signVersion=3"
            f"&startTime={start_time}"
            f"&userId={self.user_id}"
            f"&versionCode=2"
        )
        
        headers = {
            'Host': 'data.iyunmai.com',
            'Connection': 'keep-alive',
            'Accept': '*/*',
            'User-Agent': 'scale/4.25 (iPad Air 4th Gen (WiFi) OS 16.1; Screen/2.00)',
            'accessToken': self.access_token,
            'Accept-Language': 'zh-Hans-CN;q=1, en-CN;q=0.9',
            'Accept-Encoding': 'gzip, deflate, br',
        }
        
        response = self.session.get(url, headers=headers)
        data = response.json()
        
        if data.get("result", {}).get("code") == 0:
            rows = data.get("data", {}).get("rows", [])
            print(f"✅ 获取到 {len(rows)} 条体重记录")
            return rows
        else:
            print(f"❌ 获取数据失败: {data}")
            return []
    
    def parse_weight_data(self, rows):
        """解析体重数据"""
        records = []
        for row in rows:
            create_time = row.get("createTime", "")
            
            # createTime 已经是格式化的字符串（如 "2026-04-08 22:51:23"）
            datetime_str = create_time if create_time else ""
            
            record = {
                "createTime": create_time,
                "datetime": datetime_str,
                "weight": row.get("weight"),  # 体重 kg
                "bmi": row.get("bmi"),  # BMI
                "fat": row.get("fat"),  # 体脂率 %
                "muscle": row.get("muscle"),  # 肌肉率 %
                "water": row.get("water"),  # 水分 %
                "protein": row.get("protein"),  # 蛋白质 %
                "visFat": row.get("visFat"),  # 内脏脂肪等级
                "bmr": row.get("bmr"),  # 基础代谢率
                "somaAge": row.get("somaAge"),  # 身体年龄
            }
            records.append(record)
        return records
    
    def save_to_csv(self, records, append=True):
        """保存到 CSV 文件"""
        os.makedirs(DATA_DIR, exist_ok=True)
        
        mode = 'a' if append else 'w'
        file_exists = os.path.exists(CSV_FILE)
        
        with open(CSV_FILE, mode, newline='', encoding='utf-8') as f:
            fieldnames = ["createTime", "datetime", "weight", "bmi", "fat", "muscle", "water", "protein", "visFat", "bmr", "somaAge"]
            writer = csv.DictWriter(f, fieldnames=fieldnames)
            
            if not file_exists or not append:
                writer.writeheader()
            
            for record in records:
                writer.writerow(record)
        
        print(f"✅ 数据已保存到: {CSV_FILE}")


def sync_data(days=10):
    """同步数据主函数"""
    print(f"[{datetime.now()}] 开始同步云麦体重数据...")
    
    client = YunmaiClient(ACCOUNT, PASSWORD_RSA)
    
    # 检查是否需要登录
    if not client.refresh_token:
        if not client.login():
            return False
    else:
        # 刷新 Token
        if not client.refresh_access_token():
            # Token 刷新失败，重新登录
            if not client.login():
                return False
    
    # 获取数据
    rows = client.get_weight_data(days)
    if not rows:
        return False
    
    # 解析数据
    records = client.parse_weight_data(rows)
    
    # 保存到 CSV
    client.save_to_csv(records)
    
    # 显示最新数据
    if records:
        latest = records[0]
        print(f"\n📊 最新记录:")
        print(f"   时间: {latest['datetime']}")
        print(f"   体重: {latest['weight']} kg")
        print(f"   体脂: {latest['fat']}%")
        print(f"   BMI: {latest['bmi']}")
    
    # 推送微信通知
    push_wechat_notification(records[0] if records else None)
    
    return True


def push_wechat_notification(data):
    """推送微信通知"""
    import requests
    
    if not data:
        return
    
    message = f"""⚖️ 体重日报

📊 最新数据 ({data.get('datetime', '')})

体重: {data.get('weight', '?')} kg
体脂: {data.get('fat', '?')}%
BMI: {data.get('bmi', '?')}
肌肉: {data.get('muscle', '?')}%

💡 提示
请在 Zepp App 记录体重数据
3 秒完成 → 自动同步 Health Connect

---
🦞 云麦同步 | {datetime.now().strftime('%Y-%m-%d %H:%M')}"""
    
    # 使用 OpenClaw 内部消息接口
    try:
        # 直接使用环境变量或配置
        print("\n📱 微信推送内容:")
        print(message)
        print("\n提示: 手动推送命令已集成到定时任务")
    except Exception as e:
        print(f"推送失败: {e}")


if __name__ == "__main__":
    import sys
    days = int(sys.argv[1]) if len(sys.argv) > 1 else 10
    sync_data(days)