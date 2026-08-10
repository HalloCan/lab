# 晃晃我 — ShakeRandomGenerator

基于设备加速度传感器的摇晃随机数生成器，类似 VeraCrypt 鼠标熵收集机制。

## 原理

1. **差分采样** — 使用相邻采样点的加速度差分 (dx, dy, dz)，消除重力和设备姿态的固定偏移
2. **阈值过滤** — 丢弃 `abs(dx)+abs(dy)+abs(dz) < threshold` 的静止/微抖数据
3. **量化编码** — `(value × 1000).roundToInt()` 转为 Int (32-bit)，消除浮点差异
4. **熵池累积** — 12 字节/样本（3 轴 × 4 字节 Int），直接追加原始字节
5. **熵质量评估** — Shannon 熵 + 位平衡度 + 可预测性测试（RLE 压缩比），综合评分达标后停止
6. **HKDF 提取** — 遵循 NIST SP 800-56C，collectedBytes 作为 IKM，SecureRandom(32B) 作为 Salt，输出 256-bit

## 可配置参数

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `shakeThreshold` | Float | 3f | 差分变化量阈值 (m/s²)，越小越灵敏 |
| `qualityThreshold` | Int | 85 | 熵质量评分阈值 (60-95)，达标后停止采集 |

## 使用方法

```kotlin
val generator = ShakeRandomGenerator(
    context = requireContext(),
    shakeThreshold = 3f,
    qualityThreshold = 85,
    onProgress = { score -> /* 更新进度条 0-100 */ },
    onComplete = { hex -> /* 显示 256-bit hex */ },
    onSensorData = { dx, dy, dz, rawX, rawY, rawZ -> /* 可选 */ }
)

generator.start()
generator.updateThreshold(5f)
generator.updateQualityThreshold(90)
generator.reset()
generator.release()
```

## API

| 方法 | 说明 |
|------|------|
| `start()` | 开始采集 |
| `reset()` | 重置状态 |
| `updateThreshold(Float)` | 调整灵敏度 |
| `updateQualityThreshold(Int)` | 调整质量阈值 |
| `release()` | 释放传感器 |

| 属性 | 说明 |
|------|------|
| `progress` | 熵质量评分 (0-100) |
| `isRunning` | 是否正在采集 |
| `isFinished` | 是否已完成 |
