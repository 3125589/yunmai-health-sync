# Yunmai Sync Agent

云麦好轻体脂秤数据同步 Agent

## 功能

- ✅ 自动登录 Yunmai API
- ✅ 定时同步体重数据
- ✅ CSV 格式导出
- ✅ 数据去重
- ✅ 支持多项身体指标（体重、体脂、BMI、肌肉量、骨量等）

## 使用方法

### 1. 手动同步

```bash
cd /root/.openclaw/workspace/agents/yunmai-sync/scripts
python3 sync.py
```

### 2. 同步最近 N 天

```bash
python3 sync.py 30  # 同步最近30天
```

### 3. 查看最新数据

```bash
python3 sync.py latest
```

## 数据存储

- 数据文件：`/root/.openclaw/workspace/memory/yunmai-data/weight_data.csv`
- 状态文件：`/root/.openclaw/workspace/memory/yunmai-data/sync_state.json`

## 定时同步（Cron）

建议每小时同步一次：

```bash
# 添加到 crontab
0 * * * * cd /root/.openclaw/workspace/agents/yunmai-sync/scripts && python3 sync.py >> /var/log/yunmai-sync.log 2>&1
```

## 数据字段

| 字段 | 说明 | 单位 |
|------|------|------|
| weight | 体重 | kg |
| bodyfat | 体脂率 | % |
| bmi | BMI | - |
| muscle | 肌肉量 | kg |
| bone | 骨量 | kg |
| water | 水分 | % |
| visceral_fat | 内脏脂肪 | 等级 |
| basal_metabolism | 基础代谢 | kcal |
| protein | 蛋白质 | % |
| body_age | 身体年龄 | 岁 |

## 后续扩展

- [ ] 同步到 Zepp App
- [ ] 同步到 Apple Health / Google Fit
- [ ] 数据可视化图表
- [ ] 体重变化趋势分析