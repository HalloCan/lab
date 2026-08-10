# 晃晃我 — ShakeRandomGenerator

基于设备加速度传感器的摇晃随机数生成器，类似 VeraCrypt 鼠标熵收集机制。

## 原理

1. **差分采样** — 使用相邻采样点的加速度差分 (dx, dy, dz) 而非原始值，消除重力分量和设备姿态的固定偏移，只保留用户运动产生的随机性
2. **阈值过滤** — 当 `abs(dx)+abs(dy)+abs(dz)` 低于阈值时丢弃采样，避免静止或微抖引入低熵数据
3. **量化编码** — 将 float 差分值乘以 1000 取整（milli-g 单位），消除跨设备浮点精度差异
4. **熵池累积** — 每次有效采样产生 12 字节（3 轴 × 4 字节 Int），直接追加到熵池
5. **最终哈希** — 使用 SHA-512 对全部熵池 + 时间戳盐进行一次性压缩，取前 32 字节 (256-bit) 输出

> SHA 是确定性函数，对已有熵做哈希不会增加熵，只做压缩。熵的质量完全取决于用户摇晃行为的不可预测性。

## 可配置参数

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `targetBytes` | Int | 1024 | 目标收集字节数，越大需要摇晃次数越多 |
| `shakeThreshold` | Float | 3f | 差分变化量阈值 (m/s²)，越小越灵敏 |

## 使用方法

```kotlin
val generator = ShakeRandomGenerator(
    context = requireContext(),
    targetBytes = 1024,
    shakeThreshold = 3f,
    onProgress = { percent ->
        // 更新进度条
    },
    onComplete = { hexResult ->
        // 显示 256-bit 随机数 (64 位 hex)
    },
    onSensorData = { dx, dy, dz, rawX, rawY, rawZ ->
        // 可选：实时差分量化值 + 原始传感器值
    }
)

// 开始采集
generator.start()

// 运行中可更新阈值
generator.updateThreshold(5f)

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

## onSensorData 回调参数

| 参数 | 类型 | 说明 |
|------|------|------|
| `dx` | Int | X 轴差分量化值 (milli-g) |
| `dy` | Int | Y 轴差分量化值 (milli-g) |
| `dz` | Int | Z 轴差分量化值 (milli-g) |
| `rawX` | Float | X 轴原始传感器值 (m/s²) |
| `rawY` | Float | Y 轴原始传感器值 (m/s²) |
| `rawZ` | Float | Z 轴原始传感器值 (m/s²) |

## 注意事项

- 设备必须有加速度传感器，否则 `start()` 不会生效
- 输出为密码学级别随机数，但不建议用于生产环境的密钥生成
- `onSensorData` 回调在传感器线程，更新 UI 需确保在主线程
