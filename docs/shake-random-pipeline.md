# ShakeRandomGenerator Pipeline 技术总结

> 供专家评审用。从用户按下「开始收集」到输出随机数的完整数据流。

---

## 1. 用户交互层 (WiggleFragment)

用户配置两个参数：
- **晃动灵敏度** `shakeThreshold` (1-50, 默认 3) — 控制差分变化量过滤阈值
- **质量阈值** `qualityThreshold` (60-95, 默认 85) — 控制熵质量达标分数

按下「开始收集」后，Fragment 创建 `ShakeRandomGenerator` 实例并调用 `start()`。

---

## 2. 传感器采集 (ShakeRandomGenerator.onSensorChanged)

```
TYPE_ACCELEROMETER, SENSOR_DELAY_GAME (~20ms/帧)
```

### 2.1 差分计算
```
dx = x[n] - x[n-1]
dy = y[n] - y[n-1]
dz = z[n] - z[n-1]
```
- 第一帧仅记录基准值，不入池
- 目的：消除重力分量和设备姿态的固定偏移

### 2.2 量化编码
```
qdx = (dx × 1000).roundToInt()   // Int (32-bit), 不截断
qdy = (dy × 1000).roundToInt()
qdz = (dz × 1000).roundToInt()
```
- milli-g 单位，32-bit Int 编码
- 允许高位全 0/FF，因为最终经过 HKDF 提取，输入空间基数才是安全核心

### 2.3 阈值过滤
```
if (|dx| + |dy| + |dz|) < shakeThreshold → 丢弃
```
- 静止或微抖时不入池
- `onSensorData` 回调每帧触发（无论是否丢弃），用于 UI 实时显示

---

## 3. 熵池累积

通过过滤的采样点以 **大端序 Int** 追加到熵池：
```
熵池 += [qdx×4, qdy×4, qdz×4]  // 12 字节/样本
```

同时喂入 `EntropyEstimator.feed(qdx, qdy, qdz)` 进行质量评估。

---

## 4. 熵质量评估 (EntropyEstimator)

每次 `feed()` 增量更新三个指标：

### 4.1 Shannon 熵 (权重 50%)
- 维护 256 桶直方图，统计每个字节值 (0x00-0xFF) 的出现次数
- `H = -Σ p(x) × log₂(p(x))`，理论最大 8.0 bits/byte
- `shannonRatio = H / 8.0`

### 4.2 位平衡度 (权重 20%)
- 维护 32 个 bit 位计数器（Int × 8 位 × 3 轴共享）
- 统计每个 bit 位上 1 的比例，理想值 50%
- `balanceScore = 1.0 - 2.0 × |bitBalance - 0.5|`

### 4.3 可预测性测试 (权重 30%)
- 环形缓冲区存储最近 256 字节原始数据
- 对缓冲区做 RLE（游程编码）压缩，计算压缩比
- 压缩比 ≈ 1.0 → 高度随机 → 评分 1.0
- 压缩比 >> 1.0 → 有规律（周期性摇晃）→ 评分趋近 0
- 目的：避免匀速来回摇晃等周期性运动提前触发停止

### 4.4 综合评分
```
entropyScore = shannonRatio × 50 + balanceScore × 20 + predictabilityScore × 30
```

### 4.5 最小采样数安全下限
- `minSampleBytes = 128`（约 11 个有效样本）
- 未达到时评分按比例返回，不触发完成

---

## 5. 停止条件

```
isReady (≥128 字节) && entropyScore ≥ qualityThreshold
```

达标后调用 `onCompleteInternal()`。

---

## 6. 最终密钥派生 (onCompleteInternal)

### 6.1 HKDF-HMAC-SHA512 (RFC 5869 / NIST SP 800-56C)

```
salt = SecureRandom(32B)          // 系统熵，每次独立生成，不存储
ikm  = collectedBytes             // 熵池原始字节
info = "ShakeRandomGenerator"     // 上下文绑定

PRK = HMAC-SHA512(salt, ikm)     // Extract 阶段
OKM = HMAC-SHA512(PRK, info||1)  // Expand 阶段，取前 32 字节
```

### 6.2 输出
```
result = hex(OKM[0..31])   // 256-bit，64 位 hex 字符串
```

---

## 7. 数据流总结

```
用户摇晃手机
    ↓
加速度传感器 (TYPE_ACCELEROMETER, ~50Hz)
    ↓
差分计算: dx/dy/dz = x[n]-x[n-1]  (消除重力/姿态偏移)
    ↓
量化: (value × 1000).roundToInt()  (消除浮点差异，32-bit Int)
    ↓
阈值过滤: |dx|+|dy|+|dz| ≥ threshold  (丢弃静止/微抖)
    ↓
熵池追加: [Int×3] = 12 字节/样本    (大端序，原始字节)
    ↓
并行: EntropyEstimator.feed()
    ├─ Shannon 熵 (256 桶直方图)          权重 50%
    ├─ 位平衡度 (32 位计数器)             权重 20%
    └─ 可预测性测试 (RLE 压缩比)          权重 30%
    ↓
停止判断: isReady && entropyScore ≥ qualityThreshold
    ↓
密钥派生: HKDF-HMAC-SHA512(ikm=池, salt=SecureRandom, info=上下文)
    ↓
输出: hex(OKM[0..31]) = 256-bit 随机数
```

---

## 8. 设计决策说明

| 决策 | 理由 |
|------|------|
| 差分而非原始值 | 消除重力 (9.8 m/s²) 和设备姿态的固定偏移 |
| Int 而非 Short 编码 | 不截断数据，保留完整精度；高位 0/FF 不影响安全性 |
| milli-g 量化 | 消除跨设备浮点精度差异 |
| 阈值过滤 | 静止时传感器噪声低且有规律，不适合作为熵源 |
| 不对单样本做 SHA | SHA 不增加熵，只做压缩；直接存原始字节让最终 HKDF 一次性处理 |
| HKDF 而非 SHA-512 | 遵循 NIST 800-56C，SecureRandom 做 salt，严格分离提取与扩展 |
| 可预测性测试 (RLE) | 检测周期性运动，避免匀速摇晃提前触发停止 |
| 熵质量评估停止 | 比固定数据量停止更合理 |
| context info 参数 | 防止跨用途密钥复用 |
