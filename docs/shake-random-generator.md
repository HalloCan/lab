# 晃晃我 — ShakeRandomGenerator

基于设备加速度传感器的摇晃随机数生成器。

## 原理

1. 监听加速度传感器的 X/Y/Z 数据
2. 检测到超过阈值的加速度变化时，判定为一次有效摇晃
3. 将传感器数据 + 三重时间戳（sensorTime、wallTime、nanoTime）经 SHA-256 哈希，产生 32 字节随机数据
4. 累积到目标字节数后，将全部收集数据 + 最终时间戳二次哈希，输出前 16 字节的 hex 字符串

## 可配置参数

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `targetBytes` | Int | 1024 | 目标收集字节数，越大需要摇晃次数越多 |
| `shakeThreshold` | Float | 20f | 加速度变化阈值，越小越灵敏 |

## 使用方法

```kotlin
val generator = ShakeRandomGenerator(
    context = requireContext(),
    targetBytes = 1024,
    shakeThreshold = 20f,
    onProgress = { percent ->
        // 更新进度条
    },
    onComplete = { hexResult ->
        // 显示随机数结果
    },
    onSensorData = { x, y, z ->
        // 可选：显示实时传感器数据
    }
)

// 开始采集
generator.start()

// 运行中可更新阈值
generator.updateThreshold(15f)

// 重置，可重新开始
generator.reset()

// 页面销毁时释放
generator.release()
```

## API

| 方法 | 说明 |
|------|------|
| `start()` | 开始采集摇晃数据，重复调用无效 |
| `reset()` | 重置状态，可重新开始 |
| `updateThreshold(Float)` | 运行中动态调整灵敏度 |
| `release()` | 释放传感器监听，onDestroy 时调用 |

| 属性 | 说明 |
|------|------|
| `progress` | 当前进度百分比 (0-100) |
| `isRunning` | 是否正在采集 |
| `isFinished` | 是否已完成 |

## 注意事项

- 设备必须有加速度传感器，否则 `start()` 不会生效
- 输出为密码学级别随机数，但不建议用于生产环境的密钥生成
- `onSensorData` 回调在传感器线程，更新 UI 需确保在主线程
