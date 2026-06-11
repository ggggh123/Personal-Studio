# 专业排名百分位估计 设计

日期：2026-06-11
状态：已获批，待写实现计划

## 背景与目标

成绩页已有「估计平均绩」（`PeerGpaEstimator` 用各课最高分+学习人数估分数 σ，二阶 Jensen 修正得班级平均绩点）。本功能再加一个：**估计学生在本专业的整体排名百分位**——教务不直接给整体排名（整体专业排名只在图片版成绩单里），但 cjfx 详情给了**每门课的专业位次**「本人成绩在专业中占 p%」。用这些逐课位次聚合出整体专业百分位。

**已确认的关键事实**：
- 百分位方向：「在…中占 p%」= **前 p%**，小=好（代码现有理解正确，`BuildGradeSummaryUseCase` 与 AI prompt 一致）。
- 群体：**专业内**（保研/评奖最相关）。
- 呈现：**点估计 + 区间**（诚实反映不确定性）。
- 统计参数由实现方（AI）决定，采用下述稳健保守选择。

## 方案（已选 A：逐课位次聚合，z 空间加权合成）

不用「直接平均百分比」（数学上错）；在 z 空间合成（Stouffer 类标准做法），点估计取保守的一致能力模型。

## 数学模型

记号：课程 i，学分 wᵢ，专业百分位 pᵢ∈(0,100)（来自 `majorRankText`），专业人数 Nᵢ（来自 `majorSize`，可缺）。只纳入有 pᵢ 的课。

### 第 1 步：百分位 → z 分数
```
击败比例 bᵢ = 1 − pᵢ/100
zᵢ = Φ⁻¹(clamp(bᵢ))         # 越大越强
```
连续性校正避免 Φ⁻¹(0)/Φ⁻¹(1)=±∞：
```
clamp(bᵢ) ∈ [1/(2Nᵢ), 1 − 1/(2Nᵢ)]      # Nᵢ 可用时
否则 ∈ [0.005, 0.995]                     # ε 兜底
```

### 第 2 步：点估计（ρ=1 一致能力模型）
```
z* = Σ(wᵢ·zᵢ) / Σwᵢ                      # 学分加权 z 均值
P* = 100·(1 − Φ(z*))                      # 「前 P*%」
```
取舍理由：整体 GPA 排名按平均表现比较，ρ=1 等价「整体位次 ≈ 一贯水平」，保守不夸大。ρ=0（Stouffer 独立求和）会把"门门 +1σ"算成前 0.08%，明显失真，故不取。

### 第 3 步：区间（两个不确定性来源）
**a) 各课分歧（加权标准误）**
```
s²    = Σ wᵢ(zᵢ − z*)² / Σwᵢ
N_eff = (Σwᵢ)² / Σwᵢ²                     # Kish 有效样本量
SE    = sqrt(s² / N_eff)
```
**b) 相关性敏感度**（ρ 无法从数据反推，用结构带表达）
```
z(ρ) = Σwᵢzᵢ / sqrt[(1−ρ)Σwᵢ² + ρ(Σwᵢ)²]   # z(1)=z*
取 ρ_min = 0.5 得「更极端」一侧 z(0.5)
```
**合成端点**（对强/弱学生用 sign(z*) 对称处理）：
```
zLess = z*    − sign(z*)·SE               # 往 50% 收(更保守)
zMore = z(0.5) + sign(z*)·SE              # 往两端推(更极端)
端点百分位 = 100·(1 − Φ(z))
loPercent = min(P(zLess), P(zMore))
hiPercent = max(P(zLess), P(zMore))
```
sign(0) 取 +1。输出形如 `专业 约前 12%（7%–18%）`。

### 可调常数（实现方已定，集中此处便于日后校准）
- `RHO_MIN = 0.5`（相关性结构带下界）
- `SE_K = 1.0`（标准误倍数，≈68% 带）
- `EPS = 0.005`（无专业人数时的连续性夹值）
- `MIN_COURSES = 2`（少于此不出估计）

## 工程结构

### 新增 `core/util/RankPercentileEstimator.kt`（纯 object）
```
data class RankPercentileEstimate(
    val pointPercent: Double,   // 前 X%
    val loPercent: Double,      // 区间下界(更好)
    val hiPercent: Double,      // 区间上界(更差)
    val basisCount: Int,        // 参与估计的课程数
)

object RankPercentileEstimator {
    // courses: 各课 (credit, majorPercentile p∈(0,100), majorSize?)
    fun estimate(courses: List<Triple<Double, Double, Int?>>): RankPercentileEstimate?
}
```
- 少于 `MIN_COURSES` 门有效课 → 返回 null。
- 复用 `PeerGpaEstimator.invNormalCdf`（Φ⁻¹）；新增 `PeerGpaEstimator.normalCdf(z)`（Φ，A&S 26.2.17 有理逼近，精度~7.5e-8）。
- 三个百分位（point/lo/hi）各夹回 **[1.0, 99.0]**，配合整数显示保证落在「前 1%」～「前 99%」，不出现「前 0%/100%」。

### 给 `PeerGpaEstimator` 加 `normalCdf(z: Double): Double`
正逆 CDF 放一处。

### 模型：`GradeModels.kt`
- `RankPercentileEstimate` 数据类（如上，或就近放 model 包）。
- `GradeBook` 增 `val overallMajorRankEst: RankPercentileEstimate? = null`。

### `ComputeGpaUseCase`
- 新增私有 helper 解析 `majorRankText`（原文如 `"63%"`）→ Double：取首个数字（含小数），解析不出返回 null。
- 收集所有有专业位次的课 `(credit, p, majorSize)` → `RankPercentileEstimator.estimate(...)` → 填 `GradeBook.overallMajorRankEst`。
- 仅在 `invoke` 末尾构造 GradeBook 时加这一项，不动现有 GPA/peer 逻辑。

## 数据流 + 降级

- **不需要重新同步**：估计只依赖 `majorRankText`（F1 老字段，已在库里）。`majorSize` 是 #18 新增、仅用于连续性校正，缺则 ε 兜底——所以旧数据也能算（精度略低）。
- 课程无 `majorRankText` 或解析失败 → 跳过。
- 有效课 < 2 → `overallMajorRankEst = null` → UI 不显示该行。
- 数值边界：clamp + 结果夹 [0.1, 99.9]。

## 展示

- `GpaOverviewCard` 在「估计平均绩」行下新增一行（仅当 `rankEst != null` 且 `filtering == false`）：
  ```
  专业排名  约前 12%（7%–18%）
  ```
  - 颜色 Amber（与 peer 行的 FoamMute 区分，点明这是关键估计）。
  - 文案固定 `专业排名  约前 {point}%（{lo}%–{hi}%）`，三个数均**四舍五入到整数**（估计器已夹 [1,99]，故恒为 1–99 的整数，不会出现「前 0%」或假精确的小数）。
  - 「约」+ 区间自带「这是估计」语义，无需额外说明文字。
  - 筛选选中子集时隐藏（子集排名无意义）。
- `GradeBook.overallMajorRankEst` 经 `GradesViewModel` 传入卡片（新增可空参数 `rankEst`，默认 null，向后兼容）。

## 测试

### `RankPercentileEstimatorTest`（纯函数，JUnit）
- 全 p=50（3 门等学分）→ point≈50，lo≤point≤hi。
- 强且一致（p=10/12/15 等学分）→ point<15，且 point 落在合理小值；lo≤point≤hi。
- 方向单调：同结构下 p 更小 → point 更小。
- 降级：0 门、1 门 → null。
- 区间序不变量：任意输入 lo≤point≤hi、均∈[0.1,99.9]。
- 连续性夹：含 p=0（满分课）不产生 NaN/∞，z 有限。
- 学分加权：高学分高位次课对 point 拉动更大（构造对照）。

### `PeerGpaEstimatorTest`（已存在则补）
- `normalCdf`：Φ(0)=0.5、Φ(1.96)≈0.975（容差 1e-3）、Φ(−z)=1−Φ(z) 对称。
- `normalCdf(invNormalCdf(p))≈p` 往返（容差 ~1e-3）。

### `ComputeGpaUseCaseTest`（已存在则补）
- entries 含可解析 `majorRankText`（≥2 门）→ `book.overallMajorRankEst != null` 且 pointPercent 合理。
- 无 `majorRankText` → `overallMajorRankEst == null`。

UI 纯展示，逻辑已被纯函数测试覆盖，不强制 UI 单测。

## 影响面

新增 2 文件（`RankPercentileEstimator.kt` + 测试），改 `PeerGpaEstimator`（+normalCdf）、`GradeModels`（+模型/字段）、`ComputeGpaUseCase`（+解析+填充）、`GpaOverviewCard`（+一行）、`GradesViewModel`（传参）。无网络/DB schema 改动，低风险，不需重新同步。
