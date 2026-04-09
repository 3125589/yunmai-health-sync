#!/usr/bin/env python3
"""
云麦体重数据 API - 最小版本
"""

from http.server import HTTPServer, BaseHTTPRequestHandler
import json
import csv
import os
from datetime import datetime

DATA_DIR = "/root/.openclaw/workspace/memory/yunmai-data"
CSV_FILE = f"{DATA_DIR}/weight_data.csv"
API_TOKEN = "yunmai_weight_2026"
PORT = 8898

class Handler(BaseHTTPRequestHandler):
    def do_OPTIONS(self):
        self.send_response(200)
        self.send_header('Access-Control-Allow-Origin', '*')
        self.send_header('Access-Control-Allow-Headers', 'Authorization')
        self.end_headers()
    
    def do_GET(self):
        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Access-Control-Allow-Origin', '*')
        
        if self.headers.get('Authorization', '') != f'Bearer {API_TOKEN}':
            self.end_headers()
            self.wfile.write(json.dumps({"error": "Unauthorized"}).encode())
            return
        
        self.end_headers()
        
        if self.path == '/api/weight/latest' and os.path.exists(CSV_FILE):
            with open(CSV_FILE, 'r', encoding='utf-8') as f:
                rows = list(csv.DictReader(f))
            if rows:
                latest = rows[-1]
                response = {
                    "success": True,
                    "data": {
                        "datetime": latest.get("datetime", ""),
                        "weight": float(latest.get("weight", 0)),
                        "bmi": float(latest.get("bmi", 0)) if latest.get("bmi") else None,
                        "fat": float(latest.get("fat", 0)) if latest.get("fat") else None,
                        "muscle": float(latest.get("muscle", 0)) if latest.get("muscle") else None,
                        "water": float(latest.get("water", 0)) if latest.get("water") else None
                    }
                }
                self.wfile.write(json.dumps(response).encode())
                return
        
        self.wfile.write(json.dumps({"error": "No data"}).encode())
    
    def log_message(self, format, *args):
        print(f"[{datetime.now()}] {args[0]}")

if __name__ == '__main__':
    server = HTTPServer(('0.0.0.0', PORT), Handler)
    print(f"✅ API 运行在端口 {PORT}")
    server.serve_forever()