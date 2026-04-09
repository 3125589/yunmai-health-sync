#!/bin/bash
# Node-RED 部署脚本
# 用于获取 Yunmai 数据

echo "=== 部署 Node-RED ==="

# 1. 检查 Docker
if ! command -v docker &> /dev/null; then
    echo "❌ Docker 未安装，请先安装 Docker"
    exit 1
fi

# 2. 创建数据目录
mkdir -p /opt/node-red/data

# 3. 运行 Node-RED 容器
docker run -d \
  --name node-red \
  --restart always \
  -p 1880:1880 \
  -v /opt/node-red/data:/data \
  --user root \
  nodered/node-red:latest

echo "✅ Node-RED 已启动"
echo "📱 访问地址: http://localhost:1880"

# 4. 等待启动
echo "⏳ 等待 Node-RED 启动..."
sleep 10

# 5. 安装必要节点
echo "📦 安装必要节点..."
docker exec -u root node-red npm install \
  node-red-node-base64 \
  node-red-contrib-moment \
  node-red-contrib-spreadsheet-in

# 6. 重启 Node-RED
docker restart node-red

echo ""
echo "=== 后续步骤 ==="
echo "1. 访问 http://localhost:1880"
echo "2. 导入 Yunmai 流程（参考原文档）"
echo "3. 配置账号密码"
echo "4. 测试数据获取"