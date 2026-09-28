"""
指标异常检测引擎（业界标准方法实现）

对单个指标时间序列执行统计异常检测，输出异常时点、偏离得分、严重度与类型。

处理流程（标准化先分解后检测，即 S-H-ESD / STL 残差思路）:
    1. 结构识别：自相关法推断季节周期（时间缺口按真实时间对齐，不按下标压缩），
       用稳健分解（Theil–Sen 局部线性趋势 + 相位中位数季节）得到期望基线，
       并以季节强度门限判断是否存在真实季节结构
    2. 残差检测：存在季节结构时，对"实际值 − 基线"的残差序列执行点检测器；否则直接检测原序列
    3. 集成投票：多检测器命中数达到阈值（多数票）才判定为异常，降低单方法误报
    4. 位移确认：按变点检测（前后双窗中位数比较）确认水平位移起点，绕过投票阈值
    5. 分级分类：按偏离幅度与稳健得分定严重度，按方向定尖峰/凹陷，连续异常点合并为片段

检测方法（可单用，默认 auto 多方法投票集成）:
    mad     — 稳健修正 Z 分数（Iglewicz & Hoaglin），阈值 3.5
    zscore  — 经典 3σ Z 分数（要求序列干净、近似正态）
    iqr     — Tukey 箱线图围栏（1.5×IQR 离群，3.0×IQR 极值）
    moving  — 局部线性趋势残差（Theil–Sen 稳健拟合，Hampel 思路的变体）：
              以邻域稳健拟合值作为期望，趋势与边界处均不产生伪残差
    stl     — 时序分解路径：显式要求走"分解 + 残差检测"（无季节结构时退化为稳健 Z 分数）
    ewma    — EWMA 指数加权控制图（SPC），捕捉持续水平漂移（需显式指定，不参与默认集成）
    shift   — 水平位移确认（变点检测思路：前后双窗中位数比较，窗口按时间网格取）：对候选
              时点比较前后窗口的稳健水平，显著差异处定位位移起点；不单独选用，多方法集成时自动启用
    auto    — 默认：按样本量与季节结构自动选择检测器，多数投票判定

使用方式:
    python scripts/anomaly_detect.py '<JSON 载荷>'
    python scripts/anomaly_detect.py <JSON 文件路径>
    python scripts/anomaly_detect.py '<JSON 载荷>' --format=text
    echo '<JSON 载荷>' | python scripts/anomaly_detect.py

输入载荷（三种形态任选其一）:
    1. 直接给定序列:   {"metric": "...", "series": [{"time": "2025-06-01", "value": 120}, ...]}
    2. 透传查询响应:   {"metric": "...", "queryResult": {aloudata_metrics_query 原始响应}}
    3. 透传提取结果:   {"metric": "...", "columns": [...], "rows": [{列名: 值}, ...]}

    公共可选字段:
        granularity  时间粒度 DAY/WEEK/MONTH（不参与检测计算，仅用于"实测采样间隔与
                     声明粒度不符"的提示）
        method       auto|mad|zscore|iqr|moving|stl|ewma，默认 auto
        direction    both|up|down，默认 both
        timeField    时间字段名（缺省自动识别含 time/date 的列）
        valueField   数值字段名（缺省用 metric，其次取首个非时间数值列）
        params       方法参数，见下方默认值

输出: JSON（--format=text 时输出可读文本）
"""

import argparse
import json
import math
import os
import statistics
import sys
from datetime import datetime

# ==================== 方法标识与默认参数（常量集中定义，禁止散落魔法值） ====================

METHOD_AUTO = "auto"
METHOD_ENSEMBLE = "ensemble"
METHOD_MAD = "mad"
METHOD_ZSCORE = "zscore"
METHOD_IQR = "iqr"
METHOD_MOVING = "moving"
METHOD_STL = "stl"
METHOD_EWMA = "ewma"
METHOD_SHIFT = "shift"

SINGLE_METHODS = (METHOD_MAD, METHOD_ZSCORE, METHOD_IQR, METHOD_MOVING, METHOD_STL, METHOD_EWMA)
SUPPORTED_METHODS = (METHOD_AUTO, METHOD_ENSEMBLE) + SINGLE_METHODS

# 默认阈值与参数
DEFAULT_MAD_THRESHOLD = 3.5
DEFAULT_ZSCORE_THRESHOLD = 3.0
DEFAULT_IQR_K = 1.5
DEFAULT_IQR_EXTREME_K = 3.0
DEFAULT_MOVING_WINDOW = 7
DEFAULT_MOVING_THRESHOLD = 3.5
DEFAULT_EWMA_ALPHA = 0.3
DEFAULT_EWMA_L = 3.0

# 样本量与窗口下限
MIN_VALID_POINTS = 8              # 有效点下限，低于此值不做统计判定
MIN_POINTS_FOR_MOVING = 14        # 移动平均控制图所需最少点数
MIN_POINTS_PER_SEASON = 3         # STL 要求 n >= 3 × 周期
MIN_WINDOW_POINTS = 3             # 局部窗口（稳健线性拟合）所需最少参考点
MIN_POINTS_FOR_ACF = 14           # 季节性自动推断所需最少点数
MAX_SEASON_LAG = 12               # 自相关法最大候选周期
ACF_SEASONAL_THRESHOLD = 0.3      # 自相关峰的经验下限（短序列另按 2/√n 收紧）
ACF_SIGMA_FACTOR = 2.0            # 自相关显著性倍数：门限 = 该值 / √n（白噪声自相关波动幅度）
ACF_PEAK_RATIO = 0.9              # 取达到峰值 90% 的最小滞后，避免谐波
ACF_WINSORIZE_SIGMA = 3.0         # 差分序列稳健缩尾倍数（抑制异常点对自相关的污染）
SEASONAL_STRENGTH_THRESHOLD = 0.3  # 季节强度门限，低于该值不启用分解法（避免无季节性序列伪残差）
STRENGTH_TRIM_RATIO = 0.05        # 计算季节强度/效应量时按残差绝对值截尾的比例（抑制单个异常点）
SEASONAL_AMPLITUDE_SNR = 3.0      # 季节峰谷差至少达到残差波动（标准差）的该倍数才算真实季节结构
DECOMPOSE_REFINE_ROUNDS = 2       # 趋势/季节交替估计轮数（STL 内循环）：互相吸收对方估计误差
TREND_WINDOW_PERIODS = 2          # 分解时趋势窗覆盖的周期数（STL 要求 ≥1.5 周期）
# 退化保护门限：分解后残差尺度相对去趋势尺度的下限。真实业务指标必含噪声，
# 若残差被压到近乎为 0，说明序列是确定性周期重复，此时任何微小偏离都会被放大成
# 天文级得分（如 1.9% 偏离判 CRITICAL），故放弃分解、退回原序列检测。
STRENGTH_MODEL_MIN_RESIDUAL_RATIO = 0.03

# 统计常量
MAD_TO_SIGMA = 1.4826             # MAD → 标准差的一致性系数
IQR_TO_SIGMA = 1.349              # IQR → 标准差的一致性系数（正态分布下 Q3−Q1 ≈ 1.349σ）
MEAN_ABS_TO_SIGMA = 1.253314      # 平均绝对偏差 → 标准差的一致性系数
WINSOR_BOUND_SIGMA = 4.0          # 缩尾校正的界（MADσ 倍数）：缩尾后取 std 与 MADσ 之较大者
CONSTANT_TOLERANCE = 1e-12        # 判定"常量序列"的容差

# 严重度分级阈值
DEVIATION_RATIO_CRITICAL = 1.0    # 偏离幅度 ≥100%
DEVIATION_RATIO_MAJOR = 0.5       # 偏离幅度 ≥50%
MIN_DEVIATION_RATIO = 0.03        # 实际意义下限：统计显著但相对偏离不足 3% 的点不作为异常报告
SCORE_CRITICAL = 6.0
SCORE_MAJOR = 4.5
LEVEL_SHIFT_MIN_POINTS = 3        # 连续同向异常点达到该数量视为水平位移
SHIFT_WINDOW = 7                  # 位移确认的单侧比较窗口长度（双窗中位数比较，按时间网格取）
SHIFT_Z_THRESHOLD = 3.5           # 双窗水平比较的显著性门限（多点扫描下控制误报）
SHIFT_WINDOW_MISSING_TOLERANCE = 3  # 位移窗口允许的缺失网格槽位数（窗口最少 4 点；实测 2→3 在连缺 3 天场景召回 +13%，干净序列误报不变）
CORRELATED_VOTE_FLOOR = 4.5       # 仅由高相关的 mad/moving 凑票时的得分下限（≈ IQR 极值围栏强度）

# 严重度与类型枚举
SEVERITY_MINOR = "MINOR"
SEVERITY_MAJOR = "MAJOR"
SEVERITY_CRITICAL = "CRITICAL"
SEVERITY_ORDER = {SEVERITY_MINOR: 0, SEVERITY_MAJOR: 1, SEVERITY_CRITICAL: 2}

TYPE_SPIKE = "SPIKE"
TYPE_DIP = "DIP"
TYPE_LEVEL_SHIFT = "LEVEL_SHIFT"
TYPE_ZERO_VALUE = "ZERO_VALUE"

DIRECTION_BOTH = "both"
DIRECTION_UP = "up"
DIRECTION_DOWN = "down"

DEFAULT_MAX_ANOMALIES = 50
DEFAULT_GRANULARITY = "DAY"

# 粒度对应的期望采样间隔（秒），用于提示"声明的粒度与实测采样节奏不符"
# （典型场景：日粒度数据缺行近半，实测变成隔天采样，缺口计数看不出这一点）
GRANULARITY_SECONDS = {
    "DAY": 86400.0,
    "WEEK": 604800.0,
    "MONTH": 2629746.0,   # 平均月长 30.44 天
}
GRANULARITY_STEP_TOLERANCE = 0.25   # 实测间隔与声明粒度的相对偏差上限（超过即提示）

TIME_FIELD_KEYWORDS = ("metric_time", "time", "date", "日期", "时间")

_TIME_FORMATS = ("%Y-%m-%d %H:%M:%S", "%Y-%m-%d", "%Y-%m", "%Y/%m/%d", "%Y%m%d")


# ==================== 基础统计函数 ====================


def percentile(sorted_values, ratio):
    """线性插值分位数（与 numpy.percentile 的 linear 方式一致）"""
    if not sorted_values:
        return None
    if len(sorted_values) == 1:
        return sorted_values[0]
    position = (len(sorted_values) - 1) * ratio
    lower = int(math.floor(position))
    upper = int(math.ceil(position))
    if lower == upper:
        return sorted_values[lower]
    return sorted_values[lower] + (sorted_values[upper] - sorted_values[lower]) * (position - lower)


def median(values):
    """中位数"""
    return statistics.median(values)


def sample_stdev(values):
    """样本标准差（n-1 分母）"""
    if len(values) < 2:
        return 0.0
    return statistics.stdev(values)


def robust_scale(values):
    """稳健尺度估计（σ 一致估计）：MAD → IQR/1.349 → 平均绝对偏差×1.2533 → 样本标准差

    逐级退化而非直接跳到样本标准差：取值高度重复的序列（如整数化、四舍五入后的指标）
    会有超过半数取值与中位数完全相同，此时 MAD 恰为 0，若直接退到标准差，尺度会被
    少数极端点抬高几个量级，导致真正的异常反而检不出来。中间两级同样不受极端值主导。

    MAD 分支附加缩尾校正：分解残差存在"内挤+重尾"结构（季节相位中位数用同一样本
    拟合的过拟合效应），MAD 会被压低到真实噪声尺度的一半以下，得分被系统性放大
    （实测 MAD=1.85 而 std=5.79）。把取值缩尾到 ±WINSOR_BOUND_SIGMA×MADσ 后取
    样本标准差（Huber 稳健尺度的等价形式），既不被极端值抬升、又能反映内挤之外
    的真实散布，与 MADσ 取较大者作为校正后的尺度。
    """
    if not values:
        return 0.0
    center = median(values)
    mad = median([abs(v - center) for v in values])
    if mad > CONSTANT_TOLERANCE:
        mad_scale = MAD_TO_SIGMA * mad
        bound = WINSOR_BOUND_SIGMA * mad_scale
        winsorized = [max(-bound, min(bound, v)) for v in values]
        return max(mad_scale, sample_stdev(winsorized))
    ordered = sorted(values)
    iqr = percentile(ordered, 0.75) - percentile(ordered, 0.25)
    if iqr > CONSTANT_TOLERANCE:
        return iqr / IQR_TO_SIGMA
    mean_abs = sum(abs(v - center) for v in values) / len(values)
    if mean_abs > CONSTANT_TOLERANCE:
        return MEAN_ABS_TO_SIGMA * mean_abs
    return sample_stdev(values)


def mad_scale(values):
    """纯 MAD 尺度（不含缩尾校正）：用于差分序列的 σ 反推

    变点检测的差分序列中，阶跃只贡献 1~2 个离群差分点，正是需要 MAD 免疫的对象；
    若混入缩尾/标准差校正，阶跃差分的方差会撑大尺度、稀释位移 Z（实测被抬高 21%）。
    """
    if not values:
        return 0.0
    center = median(values)
    mad = median([abs(v - center) for v in values])
    if mad <= CONSTANT_TOLERANCE:
        return 0.0
    return MAD_TO_SIGMA * mad


def theil_sen_fit(points):
    """Theil–Sen 稳健线性回归：斜率为所有点对斜率的中位数，截距为残差中位数

    points 为 [(x, y)]。相比最小二乘，单个离群点不会带偏斜率，
    因此既能在趋势序列上给出正确期望，也能抗邻域异常点污染。
    点不足或横坐标无区分度时返回 (None, None)。
    """
    if len(points) < MIN_WINDOW_POINTS:
        return None, None
    slopes = []
    for i in range(len(points)):
        for j in range(i + 1, len(points)):
            delta_x = points[j][0] - points[i][0]
            if abs(delta_x) <= CONSTANT_TOLERANCE:
                continue
            slopes.append((points[j][1] - points[i][1]) / delta_x)
    if not slopes:
        return None, None
    slope = median(slopes)
    intercept = median([y - slope * x for x, y in points])
    return intercept, slope


def local_linear_center(values, index, half, grid=None):
    """邻域（不含自身）稳健线性拟合在 index 处的取值，作为该点的局部期望

    局部线性而非局部均值/中位数：趋势序列在窗口边缘用均值会产生系统偏差
    （序列末端被误判为尖峰），线性拟合可外推到窗口外，边界与趋势都不产生伪残差。
    拟合与求值都使用网格坐标（缺行处跳号），缺口不会压缩邻域的时间跨度、
    把缺口两侧的趋势斜率算陡。
    """
    def coord(j):
        return grid[j] if grid is not None else j

    points = [
        (coord(j), values[j])
        for j in range(max(0, index - half), min(len(values), index + half + 1))
        if j != index and values[j] is not None
    ]
    intercept, slope = theil_sen_fit(points)
    if intercept is None:
        return None
    return intercept + slope * coord(index)


def first_difference(values):
    """一阶差分（用于去趋势后做自相关分析）"""
    return [values[i + 1] - values[i] for i in range(len(values) - 1)]


def build_grid_positions(times):
    """把时间序列映射到均匀网格坐标，缺口跳号不压缩相位

    季节相位按"坐标 % period"分配的前提是采样等间隔；取数缺行（某天无记录）会让
    后续所有点的季节相位整体前移——周末高峰被对齐到工作日，分解基线失真、周末被误判。
    此处按真实时间把每个点映射到 (t − t0) / 步长的网格坐标（四舍五入用"半分向上"，
    round() 的银行家舍入会让恰好落在 .5 的累计偏移塌缩成重复坐标），缺行处坐标跳号，
    相位保持与日历对齐。

    步长优先取"占严格多数的间隔"（日/周粒度等日历对齐数据的真实采样节奏——间隔恒为
    整数天，任意缺口密度下多数间隔都是真实节奏，不像中位数会被多日缺口抬高）；无多数时
    （月粒度等天然不等长节奏）取中位间隔（对亚秒抖动稳健）。所选步长构建出的坐标不
    严格递增时依次退到其余候选重试，全部失败才放弃对齐、退回位置下标。

    返回 (grid, gap_count, aligned, step)：grid 为每点的网格坐标；gap_count 为缺失的
    网格槽位数（0 表示按检测到的节奏无缺口）；aligned 为 False 表示未能按真实时间
    对齐（时间不可解析、重复或缺口过密），grid 即位置下标；step 为采用的时间步长
    （秒），未对齐时为 None。
    """

    def grid_for(step):
        if step <= CONSTANT_TOLERANCE:
            return None
        grid = [math.floor((stamp - stamps[0]) / step + 0.5) for stamp in stamps]
        if any(g2 <= g1 for g1, g2 in zip(grid, grid[1:])):
            return None
        return grid

    fallback = (list(range(len(times))), 0, False, None)
    stamps = [_time_timestamp(t) for t in times]
    if not stamps or any(stamp is None for stamp in stamps):
        return fallback
    diffs = [b - a for a, b in zip(stamps, stamps[1:]) if b > a]
    if not diffs:
        return fallback
    median_step = median(diffs)
    counts = {}
    for d in diffs:
        counts[d] = counts.get(d, 0) + 1
    cadence = next((d for d in sorted(counts) if counts[d] > len(diffs) / 2.0), None)
    fine = [d for d in diffs if d >= median_step / 2.0] or diffs
    steps = []
    for step in (cadence, median_step, min(fine)):
        if step is not None and step > CONSTANT_TOLERANCE and step not in steps:
            steps.append(step)
    for step in steps:
        grid = grid_for(step)
        if grid is not None:
            return grid, (grid[-1] - grid[0] + 1) - len(grid), True, step
    return fallback


def autocorrelation_grid(entries, lag):
    """按网格距离 lag 配对的自相关系数（entries 为 [(grid_pos, value)]，已去均值）

    缺口处等滞后的点对在位置下标上不再等距，按下标配对会把不同滞后混进同一系数；
    按网格坐标配对后，滞后语义与等间隔序列一致。
    """
    denominator = sum(v * v for _, v in entries)
    if denominator <= CONSTANT_TOLERANCE:
        return 0.0
    by_pos = dict(entries)
    numerator = sum(v * by_pos[pos + lag] for pos, v in entries if pos + lag in by_pos)
    return numerator / denominator


def infer_seasonality_period(values, grid):
    """基于一阶差分序列的自相关峰推断季节周期；无显著周期返回 1

    差分只在网格相邻点对上计算（缺口两侧不差分，避免多日变化混入差分方差），
    自相关按网格距离配对，缺行不会让滞后错位。差分序列先做稳健缩尾（±3×MAD）：
    单个极端异常点会把差分的方差抬高几个量级，不缩尾会让真实季节峰淹没在异常点的伪相关里。
    """
    entries = [(grid[i], values[i]) for i in range(len(values)) if values[i] is not None]
    if len(entries) < MIN_POINTS_FOR_ACF:
        return 1
    diffs = [
        (g2 - 0.5, v2 - v1)
        for (g1, v1), (g2, v2) in zip(entries, entries[1:])
        if g2 - g1 == 1
    ]
    if len(diffs) < MIN_POINTS_FOR_ACF:
        return 1
    center = median([d for _, d in diffs])
    mad = median([abs(d - center) for _, d in diffs])
    if mad > CONSTANT_TOLERANCE:
        bound = MAD_TO_SIGMA * mad * ACF_WINSORIZE_SIGMA
        diffs = [(pos, max(center - bound, min(center + bound, d))) for pos, d in diffs]
    mean_diff = statistics.fmean([d for _, d in diffs])
    centered = [(pos, d - mean_diff) for pos, d in diffs]
    max_lag = min(MAX_SEASON_LAG, len(centered) // 2)
    if max_lag < 2:
        return 1
    scores = {lag: autocorrelation_grid(centered, lag) for lag in range(2, max_lag + 1)}
    best_lag = max(scores, key=lambda lag: scores[lag])
    best_score = scores[best_lag]
    # 显著性门限取 max(经验下限, 2/√n)：白噪声自相关的波动幅度约为 1/√n，
    # 固定门限在短序列上会把噪声峰值误判为周期（Bartlett 近似）
    threshold = max(ACF_SEASONAL_THRESHOLD, ACF_SIGMA_FACTOR / math.sqrt(len(centered)))
    if best_score < threshold:
        return 1
    # 取达到峰值 90% 的最小滞后，避免选中峰值谐波（如 7 的倍数）
    for lag in sorted(scores):
        if scores[lag] >= best_score * ACF_PEAK_RATIO:
            return lag
    return best_lag


# ==================== 检测器（每个检测器返回 {索引: {"score": 带符号得分, "expected": 期望值}}） ====================


def detect_mad(values, params):
    """稳健修正 Z 分数：|0.6745 × (x − median) / MAD| > 3.5，等价于 |x − median| / (1.4826×MAD)"""
    valid = [v for v in values if v is not None]
    if len(valid) < MIN_VALID_POINTS:
        return {}
    center = median(valid)
    scale = robust_scale(valid)
    if scale <= CONSTANT_TOLERANCE:
        return {}
    threshold = params["madThreshold"]
    hits = {}
    for index, value in enumerate(values):
        if value is None:
            continue
        score = (value - center) / scale
        if abs(score) > threshold:
            hits[index] = {"score": score, "expected": center}
    return hits


def detect_zscore(values, params):
    """经典 Z 分数：|(x − μ) / σ| > 3.0（对离群点敏感，样本需近似正态）"""
    valid = [v for v in values if v is not None]
    if len(valid) < MIN_VALID_POINTS:
        return {}
    mean = statistics.fmean(valid)
    std = sample_stdev(valid)
    if std <= CONSTANT_TOLERANCE:
        return {}
    threshold = params["zscoreThreshold"]
    hits = {}
    for index, value in enumerate(values):
        if value is None:
            continue
        score = (value - mean) / std
        if abs(score) > threshold:
            hits[index] = {"score": score, "expected": mean}
    return hits


def detect_iqr(values, params):
    """Tukey 围栏：超出 Q1−k×IQR / Q3+k×IQR 视为离群，超出 3×IQR 视为极值"""
    valid = sorted(v for v in values if v is not None)
    if len(valid) < MIN_VALID_POINTS:
        return {}
    q1 = percentile(valid, 0.25)
    q3 = percentile(valid, 0.75)
    iqr = q3 - q1
    if iqr <= CONSTANT_TOLERANCE:
        return {}
    k = params["iqrK"]
    extreme_k = params["iqrExtremeK"]
    lower = q1 - k * iqr
    upper = q3 + k * iqr
    extreme_lower = q1 - extreme_k * iqr
    extreme_upper = q3 + extreme_k * iqr
    center = median(valid)
    hits = {}
    for index, value in enumerate(values):
        if value is None:
            continue
        if lower <= value <= upper:
            continue
        score = (value - upper) / iqr if value > upper else (value - lower) / iqr
        hits[index] = {
            "score": score,
            "expected": center,
            "extreme": value < extreme_lower or value > extreme_upper,
        }
    return hits


def detect_moving(values, params):
    """滑动窗口检测：以邻域稳健线性拟合值（Theil–Sen，剔除当前点）作为期望，对残差做尺度判定

    用局部线性拟合而非局部均值/中位数：均值会让窗口内的尖峰抬高邻近点的期望（连带误报），
    中位数在序列末端会滞后于趋势（边界伪残差）；Theil–Sen 同时解决这两个问题。
    拟合使用网格坐标（params["grid"]），缺行不压缩邻域时间跨度。
    """
    window = params["window"]
    half = max(1, window // 2)
    grid = params.get("grid")
    expectations = {}
    residuals = {}
    for index in range(len(values)):
        if values[index] is None:
            continue
        local_center = local_linear_center(values, index, half, grid)
        if local_center is None:
            continue
        expectations[index] = local_center
        residuals[index] = values[index] - local_center
    if len(residuals) < MIN_VALID_POINTS:
        return {}
    scale = robust_scale(list(residuals.values()))
    if scale <= CONSTANT_TOLERANCE:
        return {}
    threshold = params["movingThreshold"]
    hits = {}
    for index, residual in residuals.items():
        score = residual / scale
        if abs(score) > threshold:
            hits[index] = {"score": score, "expected": expectations[index]}
    return hits


def decompose_structure(values, period, params):
    """稳健时序分解：趋势（Theil–Sen 局部线性拟合）+ 季节（相位中位数）→ 期望基线

    返回 {"baseline": [...], "trend": [...], "seasonal": {...}, "strength": float}；
    周期无效、样本不足或未通过三重门限时返回 None（表示无显著季节结构，应按原序列检测）。

    三重门限：季节强度 Fs = max(0, 1 − Var(残差)/Var(去趋势)) 达标；季节峰谷差显著大于
    残差波动；残差尺度相对原序列尺度不可忽略。计算方差前先按残差绝对值截尾剔除极值点，
    否则单个极端异常点会同时抬高残差方差，出现"越有异常越判定无季节性"的反向效果。
    """
    if period < 2:
        return None
    valid_count = sum(1 for v in values if v is not None)
    if valid_count < max(MIN_VALID_POINTS, MIN_POINTS_PER_SEASON * period):
        return None

    size = len(values)
    grid = params.get("grid")
    if grid is None or len(grid) != size:
        grid = list(range(size))
    # 趋势窗取 2 个周期（半窗 = 周期）：窗内若不足一个完整周期，线性拟合会被周期波动带偏，
    # 把季节信号误吸进趋势项，导致季节项与残差失真（经典 STL 同样要求趋势窗 ≥ 1.5 周期）
    half = max(1, TREND_WINDOW_PERIODS * period // 2)

    # 趋势与季节交替估计（STL 内循环思路）：先用原序列相位中位数初估季节并消去周期分量
    # 再拟合趋势——若直接对含周期波动的原序列做局部线性拟合，跨相位的点对（如工作日×周末）
    # 会产生 ±振幅/距离 的成对斜率，随窗口内相位分布不对称而整体抬高或压低趋势（实测
    # 25 振幅的周季节能让趋势虚高约 5%）。此后每轮用上一轮季节消去周期分量重拟合趋势、
    # 再由去趋势序列重估季节：趋势与季节的估计误差互相吸收直至稳定。只做一轮时，初估
    # 季节的相位误差（短周期序列每相位仅数个样本）会被趋势吸收并传导给正式季节项，
    # 序列末端尤其严重（实测边界趋势虚高 +9，残差呈内挤重尾，MAD 尺度被压低一半）。
    # 相位按网格坐标取模（缺行处跳号），保证季节相位始终与真实时间对齐。
    initial_phase = {}
    for index in range(size):
        if values[index] is not None:
            initial_phase.setdefault(grid[index] % period, []).append(values[index])
    if not initial_phase:
        return None
    initial_center = statistics.fmean([median(items) for items in initial_phase.values()])
    seasonal_est = {phase: median(items) - initial_center for phase, items in initial_phase.items()}

    trend = None
    detrended = None
    for _ in range(DECOMPOSE_REFINE_ROUNDS):
        adjusted = [
            None if values[index] is None else values[index] - seasonal_est.get(grid[index] % period, 0.0)
            for index in range(size)
        ]
        trend = [None] * size
        for index in range(size):
            trend[index] = local_linear_center(adjusted, index, half, grid)

        # 季节项：去趋势后按相位取中位数（中位数对单点污染不敏感，均值会让异常外溢到同相位点）
        phase_values = {}
        detrended = [None] * size
        for index in range(size):
            if values[index] is None or trend[index] is None:
                continue
            detrended[index] = values[index] - trend[index]
            phase_values.setdefault(grid[index] % period, []).append(detrended[index])
        if not phase_values:
            return None
        seasonal = {phase: median(items) for phase, items in phase_values.items()}
        seasonal_mean = sum(seasonal.values()) / len(seasonal)
        seasonal_est = {phase: value - seasonal_mean for phase, value in seasonal.items()}
    seasonal = seasonal_est

    residuals = {}
    baseline = [None] * size
    for index in range(size):
        if detrended[index] is None:
            continue
        baseline[index] = trend[index] + seasonal.get(grid[index] % period, 0.0)
        residuals[index] = values[index] - baseline[index]
    if len(residuals) < MIN_VALID_POINTS:
        return None

    residual_values = [residuals[index] for index in sorted(residuals)]
    # 强度与效应量都用"按残差大小截尾"后的同一批点计算，避免单个异常点主导两组方差
    kept = sorted(residuals, key=lambda index: abs(residuals[index]))
    trim = int(round(len(kept) * STRENGTH_TRIM_RATIO))
    if 0 < trim < len(kept) // 2:
        kept = kept[: len(kept) - trim]
    if len(kept) < MIN_VALID_POINTS:
        return None
    detrended_var = sample_stdev([detrended[i] for i in kept]) ** 2
    residual_var = sample_stdev([residuals[i] for i in kept]) ** 2
    if detrended_var <= CONSTANT_TOLERANCE:
        return None

    # 三重门限
    # 1) 季节强度：季节项解释了多少去趋势波动，过低说明没有季节可言
    # 2) 效应量：季节峰谷差必须显著大于残差波动，否则相位估计只是在"过拟合"随机噪声
    # 3) 退化保护：残差尺度相对原序列自身波动过小，说明分解模型已近乎完美拟合
    #    （序列是确定性周期重复，而非真实业务指标）。此时噪声被压到近 0，
    #    微小偏离也会被放大成天文级得分（曾出现偏离 1.9% 判 CRITICAL、得分 560），
    #    故放弃分解、退回原序列检测。
    #    参照物必须是原序列尺度：去趋势尺度同样会被压小，无法作为参照。
    strength = max(0.0, 1.0 - residual_var / detrended_var)
    if strength < SEASONAL_STRENGTH_THRESHOLD:
        return None
    seasonal_amplitude = max(seasonal.values()) - min(seasonal.values())
    noise_scale = math.sqrt(residual_var)
    if noise_scale > CONSTANT_TOLERANCE and seasonal_amplitude < SEASONAL_AMPLITUDE_SNR * noise_scale:
        return None
    series_scale = robust_scale([values[index] for index in sorted(residuals)])
    typical_residual = median([abs(item) for item in residual_values])
    if series_scale > CONSTANT_TOLERANCE and typical_residual < STRENGTH_MODEL_MIN_RESIDUAL_RATIO * series_scale:
        return None

    return {
        "baseline": baseline,
        "trend": trend,
        "seasonal": seasonal,
        "strength": strength,
    }


def detect_ewma(values, params):
    """EWMA 控制图：平滑值相对目标（中位数）的偏离超过 L×σ_z 即报警"""
    valid = [v for v in values if v is not None]
    if len(valid) < MIN_POINTS_FOR_MOVING:
        return {}
    alpha = params["ewmaAlpha"]
    target = median(valid)
    sigma = robust_scale(valid)
    if sigma <= CONSTANT_TOLERANCE:
        return {}
    sigma_z = sigma * math.sqrt(alpha / (2 - alpha))
    if sigma_z <= CONSTANT_TOLERANCE:
        return {}
    limit = params["ewmaL"]
    hits = {}
    smoothed = None
    for index, value in enumerate(values):
        if value is None:
            continue
        smoothed = value if smoothed is None else alpha * value + (1 - alpha) * smoothed
        score = (smoothed - target) / sigma_z
        if abs(score) > limit:
            hits[index] = {"score": score, "expected": smoothed}
    return hits


# 点检测器：全部按"被检序列"计算，传入残差序列时即为残差检测
DETECTORS = {
    METHOD_MAD: detect_mad,
    METHOD_ZSCORE: detect_zscore,
    METHOD_IQR: detect_iqr,
    METHOD_MOVING: detect_moving,
    METHOD_EWMA: detect_ewma,
}


# ==================== 方法编排与集成投票 ====================


def select_methods(requested, valid_count, seasonal):
    """按样本量与季节结构选择检测器：样本越少越保守，优先稳健方法

    集成默认不纳入 EWMA——EWMA 会把单个尖峰平滑后持续外溢，导致尖峰之后多个正常点
    被连带判为"水平位移"；它更适合显式指定用于检测持续漂移。

    stl 在本引擎中代表"时序分解路径"（分解由 decompose_structure 统一完成）：
    指定 stl 时同样跑残差检测器，无季节结构时退化为单方法稳健 Z 分数并给出提示。
    """
    if requested == METHOD_STL:
        return [METHOD_MAD, METHOD_IQR] if seasonal else [METHOD_MAD]
    if requested in SINGLE_METHODS:
        return [requested]
    methods = [METHOD_MAD, METHOD_IQR]
    if valid_count >= MIN_POINTS_FOR_MOVING:
        methods.append(METHOD_MOVING)
    return methods


def detect_shift_onsets(values, params):
    """水平位移起点确认（变点检测思路：前后双窗稳健水平比较，窗口按时间网格取）

    点检测器对水平位移天然只能命中起点与终点——位移中段已是新常态，局部期望随之平移；
    且位移点的偏差被全序列尺度稀释后常凑不齐集成投票。此处按变点检测的标准做法确认：
    对每个候选时点比较前后两个网格窗口的中位数，差异达到 SHIFT_Z_THRESHOLD 个合并标准误
    （√(π/2)·σ×√(1/m_前 + 1/m_后)，m 为窗口实际点数）即判定存在位移；同一处位移会令相邻多个候选时点
    同时超标，按 |Z| 最大者保留，并取候选窗内首个"离后窗水平更近"的点（水平归属切换点）
    定位真实起点。双窗中位数比较只依赖两侧局部水平，对全局去趋势的残余倾斜不敏感，比逐点游程更稳。

    网格语义：窗口按真实时间的网格坐标取（缺行槽位跳号，窗口跨越的日历跨度恒定）；
    窗口先按序列边界裁剪——边缘截断（界内槽位不足 SHIFT_WINDOW）不判定，与原版一致；
    完整窗口内允许至多 SHIFT_WINDOW_MISSING_TOLERANCE 个缺失槽位，此时标准误按实际
    点数放大（√(π/2)·σ×√(1/m_前 + 1/m_后)），满窗时与原版公式逐位一致。差分只在
    网格相邻点对上计算——跨缺口的差分含多日变化，会系统性抬高 σ、稀释位移 Z。
    起点回退沿占用点步进而非坐标减一，None 行不会中断定位（原按下标回退在 None 行上
    会取不到键）。

    返回 {起点行索引: {"score": 双窗比较 Z, "expected": 位移前水平}}
    """
    grid = params.get("grid")
    if grid is None or len(grid) != len(values):
        grid = list(range(len(values)))
    entries = sorted(
        (grid[index], index, value)
        for index, value in enumerate(values)
        if value is not None
    )
    if len(entries) < 2 * SHIFT_WINDOW + 1:
        return {}
    # 全局稳健去趋势：缓慢趋势会制造前后窗水平的系统性差异（假位移）。
    # Theil–Sen 对阶跃不敏感（斜率中位数由未受污染的点对主导），残余倾斜由双窗比较自然吸收。
    intercept, slope = theil_sen_fit([(coord, value) for coord, _, value in entries])
    if intercept is None:
        return {}
    coords = [coord for coord, _, _ in entries]
    rows = {coord: row for coord, row, _ in entries}
    place = {coord: position for position, coord in enumerate(coords)}
    by_coord = {coord: value - (intercept + slope * coord) for coord, _, value in entries}
    # σ 用一阶差分的纯 MAD 尺度反推（变点检测标准做法）：全序列残差的 MAD 会被
    # 阶跃与缓变趋势撑大（实测 -3σ×6 点的位移把尺度从 5 撑到 7.5，Z 被稀释三成），
    # 而差分只含噪声——阶跃仅贡献 1 个离群差分点，被中位数免疫（须用纯 MAD 而非
    # 带缩尾校正的 robust_scale，否则阶跃差分的方差会混入尺度，实测 σ 被抬高 21%）；
    # 相邻差分的方差为 2σ²。差分仅取网格相邻点对，缺口两侧不差分
    adjacent_diffs = [
        by_coord[c2] - by_coord[c1]
        for c1, c2 in zip(coords, coords[1:])
        if c2 - c1 == 1
    ]
    if len(adjacent_diffs) < MIN_VALID_POINTS:
        return {}
    diff_scale = mad_scale(adjacent_diffs)
    if diff_scale <= CONSTANT_TOLERANCE:
        return {}

    def window_levels(coord):
        before = [by_coord[c] for c in coords if coord - SHIFT_WINDOW <= c < coord]
        after = [by_coord[c] for c in coords if coord <= c < coord + SHIFT_WINDOW]
        return before, after

    candidates = []
    lo, hi = coords[0], coords[-1]
    for coord in coords:
        before, after = window_levels(coord)
        m_before, m_after = len(before), len(after)
        # 窗口的网格槽位数（按序列边界裁剪）：边缘截断（界内槽位不足 7 个）的窗口不
        # 判定，与原版一致；窗口完整落在界内时，缺失槽位（缺口）允许至多
        # SHIFT_WINDOW_MISSING_TOLERANCE 个，此时标准误按实际点数放大
        slots_before = min(SHIFT_WINDOW, coord - lo)
        slots_after = min(SHIFT_WINDOW, hi - coord + 1)
        if min(slots_before, slots_after) < SHIFT_WINDOW:
            continue
        if max(slots_before - m_before, slots_after - m_after) > SHIFT_WINDOW_MISSING_TOLERANCE:
            continue
        # 窗口中位数之差的标准误：m 点中位数的标准差 ≈ √(π/2)·σ/√m（中位数渐近效率
        # 2/π，与 MEAN_ABS_TO_SIGMA 同源），两窗取差 = √(π/2)·σ×√(1/m_前 + 1/m_后)；
        # σ 由相邻差分尺度反推（diff_scale ≈ σ×√2），故再除 √2。满窗时与原版公式
        # 逐位一致（diff_scale×√(π/2)/√7）
        if m_before == SHIFT_WINDOW and m_after == SHIFT_WINDOW:
            sigma_diff = diff_scale * MEAN_ABS_TO_SIGMA / math.sqrt(SHIFT_WINDOW)
        else:
            sigma_diff = (
                diff_scale
                * (MEAN_ABS_TO_SIGMA / math.sqrt(2.0))
                * math.sqrt(1.0 / m_before + 1.0 / m_after)
            )
        score = (median(after) - median(before)) / sigma_diff
        if abs(score) >= SHIFT_Z_THRESHOLD:
            candidates.append((coord, score))

    # 按 |Z| 降序贪心保留，与已接受起点相距不足一个窗口的候选视为同一位移并抑制
    candidates.sort(key=lambda item: -abs(item[1]))
    clusters = []
    for coord, score in candidates:
        if all(abs(coord - other) >= SHIFT_WINDOW for other, _ in clusters):
            clusters.append((coord, score))

    hits = {}
    for coord, score in clusters:
        before_level, after_level = window_levels(coord)
        before_level = median(before_level)
        after_level = median(after_level)
        # 起点定位：双窗统计量对起点存在 ±1~2 点的分辨率模糊（候选起点稍早时其后窗
        # 大部分已落在新水平上，得分几乎相同）。取候选窗内首个"离后窗水平更近"的点
        # 作为水平归属切换点；再向前扩展——若前一点按同样判据也属于新水平，则起点
        # 前移，直到遇到旧水平点为止（argmax 候选偏晚时把起点收回真实边界）
        onset = coord
        for position in range(place[coord], len(coords)):
            c = coords[position]
            if c >= coord + SHIFT_WINDOW:
                break
            if abs(by_coord[c] - after_level) < abs(by_coord[c] - before_level):
                onset = c
                break
        lower_bound = max(coord - SHIFT_WINDOW, coords[0])
        while place[onset] > 0:
            previous_coord = coords[place[onset] - 1]
            if previous_coord < lower_bound:
                break
            previous = by_coord[previous_coord]
            if abs(previous - after_level) >= abs(previous - before_level):
                break
            onset = previous_coord
        before_window = median(
            [by_coord[c] for c in coords if onset - SHIFT_WINDOW <= c < onset]
        )
        hits[rows[onset]] = {
            "score": score,
            "expected": intercept + slope * onset + before_window,
        }
    return hits


def resolve_period(payload, params, values, grid):
    """周期解析：显式指定优先，未指定时由自相关自动推断（样本不足则不推断）"""
    explicit = params.get("period")
    if explicit is not None:
        try:
            return int(explicit)
        except (TypeError, ValueError):
            pass
    return infer_seasonality_period(values, grid)


def build_params(payload):
    """合并默认参数与调用方覆盖参数（支持 params 子对象与顶层同名键两种写法）"""
    raw = payload.get("params") or {}
    if not isinstance(raw, dict):
        raw = {}

    def pick(key, default):
        if key in raw:
            return raw[key]
        if key in payload:
            return payload[key]
        return default

    return {
        "madThreshold": float(pick("threshold", pick("madThreshold", DEFAULT_MAD_THRESHOLD))),
        "zscoreThreshold": float(pick("zscoreThreshold", DEFAULT_ZSCORE_THRESHOLD)),
        "iqrK": float(pick("iqrK", DEFAULT_IQR_K)),
        "iqrExtremeK": float(pick("iqrExtremeK", DEFAULT_IQR_EXTREME_K)),
        "window": int(pick("window", DEFAULT_MOVING_WINDOW)),
        "movingThreshold": float(pick("movingThreshold", DEFAULT_MOVING_THRESHOLD)),
        "ewmaAlpha": float(pick("alpha", DEFAULT_EWMA_ALPHA)),
        "ewmaL": float(pick("l", pick("L", DEFAULT_EWMA_L))),
        "minDeviationRatio": float(pick("minDeviationRatio", MIN_DEVIATION_RATIO)),
        "period": pick("period", None),
        "maxAnomalies": int(pick("maxAnomalies", DEFAULT_MAX_ANOMALIES)),
    }


# ==================== 输入解析 ====================


def _to_float(value):
    """尽力把单元格值转为浮点数；非数值返回 None"""
    if value is None or isinstance(value, bool):
        return None
    if isinstance(value, (int, float)):
        return float(value)
    text = str(value).strip().replace(",", "")
    if not text:
        return None
    try:
        return float(text)
    except ValueError:
        return None


def _time_timestamp(time_text):
    """时间解析为数值（秒级时间戳或数值本身）；无法解析返回 None"""
    text = str(time_text).strip()
    for time_format in _TIME_FORMATS:
        try:
            return datetime.strptime(text, time_format).timestamp()
        except ValueError:
            continue
    return _to_float(text)


def _time_sort_key(time_text):
    """时间排序键：ISO/常见格式优先，数字次之，其余按字符串"""
    stamp = _time_timestamp(time_text)
    if stamp is not None:
        return (0, stamp, "")
    return (1, 0.0, str(time_text).strip())


def _pick_time_field(names, explicit):
    """时间字段识别：显式指定优先，否则取首个含 time/date 的列"""
    if explicit:
        return explicit
    for name in names:
        lowered = str(name).lower()
        if any(keyword in lowered for keyword in TIME_FIELD_KEYWORDS):
            return name
    return names[0] if names else None


def _pick_value_field(names, explicit, metric, rows, time_field):
    """数值字段识别：显式指定 > metric 同名 > 首个非时间且可数值化的列"""
    if explicit:
        return explicit
    if metric:
        for name in names:
            if name == metric:
                return name
    for name in names:
        if name == time_field:
            continue
        if any(_to_float(row.get(name)) is not None for row in rows):
            return name
    return names[-1] if names else None


def _series_from_rows(rows, time_field, value_field):
    """由行数据（字典列表）构造 (times, values)"""
    times = [row.get(time_field) for row in rows]
    values = [_to_float(row.get(value_field)) for row in rows]
    return times, values


def _series_from_query_result(response, payload):
    """从 aloudata_metrics_query 原始响应中提取 → (times, values, time_field, value_field)"""
    data = response.get("data") or {}
    table = data.get("table") or {}
    columns = table.get("columns") or {}
    metas = data.get("metas") or []
    names = [meta.get("name") for meta in metas] if metas else list(columns.keys())
    if not names:
        raise ValueError("查询响应中没有可用的列（data.metas / data.table.columns 均为空）")

    row_count = 0
    for name in names:
        column = columns.get(name) or []
        row_count = max(row_count, len(column))

    rows = []
    for index in range(row_count):
        row = {}
        for name in names:
            column = columns.get(name) or []
            if index >= len(column):
                continue
            cell = column[index]
            row[name] = cell.get("value") if isinstance(cell, dict) else cell
        rows.append(row)

    time_field = _pick_time_field(names, payload.get("timeField"))
    value_field = _pick_value_field(names, payload.get("valueField"), payload.get("metric"), rows, time_field)
    times, values = _series_from_rows(rows, time_field, value_field)
    return times, values, time_field, value_field


def normalize_series(payload):
    """统一输入形态 → (times, values, time_field, value_field)"""
    if isinstance(payload, list):
        payload = {"series": payload}

    if isinstance(payload.get("series"), list):
        items = payload["series"]
        time_field = payload.get("timeField", "time")
        value_field = payload.get("valueField", "value")
        times = [item.get(time_field) for item in items]
        values = [_to_float(item.get(value_field)) for item in items]
        return times, values, time_field, value_field

    if isinstance(payload.get("queryResult"), dict):
        return _series_from_query_result(payload["queryResult"], payload)

    data = payload.get("data")
    if isinstance(data, dict) and isinstance(data.get("table"), dict):
        return _series_from_query_result(payload, payload)

    if isinstance(payload.get("rows"), list):
        rows = payload["rows"]
        names = payload.get("columns") or (list(rows[0].keys()) if rows else [])
        time_field = _pick_time_field(names, payload.get("timeField"))
        value_field = _pick_value_field(names, payload.get("valueField"), payload.get("metric"), rows, time_field)
        times, values = _series_from_rows(rows, time_field, value_field)
        return times, values, time_field, value_field

    raise ValueError(
        "无法识别输入结构：请提供 series / queryResult / data.table / rows+columns 之一"
    )


def sort_by_time(times, values):
    """按时间升序排列，保证趋势与季节性方法有效"""
    order = sorted(range(len(times)), key=lambda i: _time_sort_key(times[i]))
    return [times[i] for i in order], [values[i] for i in order]


# ==================== 严重度、类型与异常片段 ====================


def grade_severity(score, deviation_ratio, extreme):
    """严重度分级：IQR 极值 / 偏离幅度 / 稳健得分三者取高"""
    if extreme or abs(score) >= SCORE_CRITICAL or deviation_ratio >= DEVIATION_RATIO_CRITICAL:
        return SEVERITY_CRITICAL
    if abs(score) >= SCORE_MAJOR or deviation_ratio >= DEVIATION_RATIO_MAJOR:
        return SEVERITY_MAJOR
    return SEVERITY_MINOR


def classify_type(value, score):
    """类型分类：零值 / 尖峰（向上） / 凹陷（向下）"""
    if value == 0:
        return TYPE_ZERO_VALUE
    return TYPE_SPIKE if score > 0 else TYPE_DIP


def calc_deviation_ratio(value, expected, baseline):
    """相对偏离幅度：|实际 − 期望| / max(|期望|, |基线|)；基线为 0 时按整体偏离处理"""
    denominator = max(abs(expected), abs(baseline))
    if denominator <= CONSTANT_TOLERANCE:
        return 1.0 if abs(value) > CONSTANT_TOLERANCE else 0.0
    return abs(value - expected) / denominator


def group_episodes(anomalies):
    """把连续的异常点合并为异常片段（告警去重），连续 ≥3 点判定为水平位移"""
    episodes = []
    current = []
    previous_index = None
    for item in anomalies:
        if previous_index is not None and item["index"] == previous_index + 1:
            current.append(item)
        else:
            if current:
                episodes.append(current)
            current = [item]
        previous_index = item["index"]
    if current:
        episodes.append(current)

    results = []
    for group in episodes:
        is_level_shift = len(group) >= LEVEL_SHIFT_MIN_POINTS
        if is_level_shift:
            for item in group:
                item["type"] = TYPE_LEVEL_SHIFT
        severity = max((item["severity"] for item in group), key=lambda s: SEVERITY_ORDER[s])
        if is_level_shift and SEVERITY_ORDER[severity] < SEVERITY_ORDER[SEVERITY_MAJOR]:
            severity = SEVERITY_MAJOR
        results.append(
            {
                "startTime": group[0]["time"],
                "endTime": group[-1]["time"],
                "length": len(group),
                "type": TYPE_LEVEL_SHIFT if is_level_shift else group[0]["type"],
                "severity": severity,
                "maxScore": round(max(abs(item["score"]) for item in group), 3),
                "meanDeviationRatio": round(sum(item["deviationRatio"] for item in group) / len(group), 4),
            }
        )
    return results


def describe_trend(valid_values):
    """整体走势提示：前后半段均值比较"""
    if len(valid_values) < 4:
        return "flat"
    middle = len(valid_values) // 2
    first_half = statistics.fmean(valid_values[:middle])
    second_half = statistics.fmean(valid_values[middle:])
    overall = statistics.fmean(valid_values)
    if abs(overall) <= CONSTANT_TOLERANCE:
        return "flat"
    change = (second_half - first_half) / abs(overall)
    if change > 0.05:
        return "up"
    if change < -0.05:
        return "down"
    return "flat"


# ==================== 主流程 ====================


def build_result(payload):
    """执行完整检测流程，返回结果字典"""
    times, values, time_field, value_field = normalize_series(payload)
    if not times:
        raise ValueError("输入序列为空")

    times, values = sort_by_time(times, values)
    metric = payload.get("metric") or value_field
    requested = str(payload.get("method") or METHOD_AUTO).lower()
    if requested not in SUPPORTED_METHODS:
        raise ValueError("不支持的 method: %s（可选 %s）" % (requested, ", ".join(SUPPORTED_METHODS)))
    direction = str(payload.get("direction") or DIRECTION_BOTH).lower()
    if direction not in (DIRECTION_BOTH, DIRECTION_UP, DIRECTION_DOWN):
        raise ValueError("不支持的 direction: %s（可选 both/up/down）" % direction)

    valid_values = [v for v in values if v is not None]
    missing_count = len(values) - len(valid_values)
    warnings = []
    if missing_count > 0:
        warnings.append("序列存在 %d 个缺失/非数值点，已跳过参与统计" % missing_count)
    grid, gap_count, aligned, step = build_grid_positions(times)
    if gap_count > 0:
        warnings.append(
            "时间轴存在 %d 个缺口（缺行或缺失日期），季节相位已按真实时间对齐，缺口处不参与差分"
            % gap_count
        )
    elif not aligned and _time_timestamp(times[0]) is not None:
        warnings.append("时间轴无法对齐到均匀网格（时间重复或缺口过密），季节相位按位置近似，周期判定可能偏差")
    # 实测采样间隔与声明粒度不符：缺口计数以"检测到的节奏"为基准，缺行近半时日粒度
    # 数据会被当成隔天采样而无缺口，此处按粒度换算直接点出，避免用户以为数据完整
    granularity = payload.get("granularity") or DEFAULT_GRANULARITY
    expected_step = GRANULARITY_SECONDS.get(str(granularity).upper())
    if aligned and step and expected_step:
        if abs(step - expected_step) > GRANULARITY_STEP_TOLERANCE * expected_step:
            warnings.append(
                "实测采样间隔约 %.2f 天，与声明的 %s 粒度不符（%.2f 天），可能存在大量缺行或数据实际为其他粒度；"
                "季节相位按实测间隔对齐"
                % (step / 86400.0, granularity, expected_step / 86400.0)
            )

    base = {
        "success": True,
        "metric": metric,
        "granularity": granularity,
        "timeField": time_field,
        "valueField": value_field,
        "seriesStats": {
            "points": len(values),
            "validPoints": len(valid_values),
            "missingPoints": missing_count,
            "start": times[0],
            "end": times[-1],
        },
    }

    # 样本量下限：不足时不做统计判定（避免小样本误报）
    if len(valid_values) < MIN_VALID_POINTS:
        warnings.append(
            "有效数据点 %d 个，低于统计检测下限 %d 个，无法判定异常；可扩大回看窗口后重试"
            % (len(valid_values), MIN_VALID_POINTS)
        )
        base.update(
            {
                "detectable": False,
                "config": {"method": requested, "methodsUsed": []},
                "summary": {
                    "anomalyCount": 0,
                    "episodeCount": 0,
                    "severityCounts": {},
                    "isStable": None,
                    "trendHint": "unknown",
                    "conclusion": "样本量不足，未执行异常检测。",
                },
                "anomalies": [],
                "episodes": [],
                "warnings": warnings,
            }
        )
        return base

    stats = {
        "min": round(min(valid_values), 6),
        "max": round(max(valid_values), 6),
        "mean": round(statistics.fmean(valid_values), 6),
        "median": round(median(valid_values), 6),
        "std": round(sample_stdev(valid_values), 6),
        "zeros": sum(1 for v in valid_values if v == 0),
    }
    base["seriesStats"].update(stats)

    # 常量序列：无波动，统计方法不可用
    if stats["max"] - stats["min"] <= CONSTANT_TOLERANCE:
        warnings.append("序列整段无波动（常量序列），统计方法无法判定异常，请按数据链路问题排查")
        base.update(
            {
                "detectable": False,
                "config": {"method": requested, "methodsUsed": []},
                "summary": {
                    "anomalyCount": 0,
                    "episodeCount": 0,
                    "severityCounts": {},
                    "isStable": None,
                    "trendHint": "flat",
                    "conclusion": "序列为常量，未执行异常检测。",
                },
                "anomalies": [],
                "episodes": [],
                "warnings": warnings,
            }
        )
        return base

    params = build_params(payload)
    params["grid"] = grid
    period = resolve_period(payload, params, values, grid)
    params["period"] = period

    # 第一步：结构分解。检出显著季节结构时按"实际值 − 基线（趋势+季节）"的残差检测，
    # 否则直接检测原序列；这样周期波动（如周末高峰）不会被误判为异常。
    structure = decompose_structure(values, period, params)
    seasonal_adjusted = structure is not None
    if seasonal_adjusted:
        offsets = structure["baseline"]
        working = [
            None if (values[i] is None or offsets[i] is None) else values[i] - offsets[i]
            for i in range(len(values))
        ]
    else:
        offsets = [0.0] * len(values)
        working = values

    methods = select_methods(requested, len(valid_values), seasonal_adjusted)
    min_votes = 1 if len(methods) == 1 else max(2, math.ceil(len(methods) / 2))
    if requested == METHOD_STL and not seasonal_adjusted:
        warnings.append("未检出显著季节结构（季节强度不足），stl 路径已退化为稳健 Z 分数检测")

    # 第二步：多方法投票——命中方法数达到阈值才判定为异常
    # Tukey 1.5×IQR 围栏（≈2.65σ）在 Tukey 语义中只是探索级标记，不是判定线：
    # 若让它参与投票，会与 moving 在噪声极值点强相关地同时命中，把干净序列的
    # 误报率抬高到 15%~45%。集成投票只收 3×IQR 极值围栏（≈4.7σ，与 MAD 3.5σ
    # 同严格度）的强命中；1.5×IQR 命中仅在显式单方法 iqr 调用时直接报告（Tukey 标准语义）。
    votes = {}
    for method in methods:
        hits = DETECTORS[method](working, params)
        for index, detail in hits.items():
            if METHOD_IQR == method and not detail.get("extreme") and requested != METHOD_IQR:
                continue
            votes.setdefault(index, []).append((method, detail))

    # 第三步：变点检测确认水平位移起点（detect_shift_onsets）。点检测器对位移天然只命中
    # 起点与终点（中段已是新常态），且位移偏差被全序列尺度稀释后常凑不齐集成投票；
    # 仅多方法集成时启用，显式单方法调用保持该方法自身语义
    if len(methods) >= 2:
        for index, detail in detect_shift_onsets(working, params).items():
            votes.setdefault(index, []).append((METHOD_SHIFT, detail))

    series_center = median(valid_values)
    anomalies = []
    below_floor = 0
    correlated_below = 0
    for index, hits in votes.items():
        # 变点检测确认的位移起点绕过投票阈值：位移点的偏差被全序列尺度稀释后
        # 常规投票常凑不齐，这正是位移确认作为补充通道存在的原因
        shift_confirmed = any(method == METHOD_SHIFT for method, _ in hits)
        vote_count = sum(1 for method, _ in hits if method != METHOD_SHIFT)
        if vote_count < min_votes and not shift_confirmed:
            continue
        if shift_confirmed:
            best_method, best_detail = next(
                (method, detail) for method, detail in hits if method == METHOD_SHIFT
            )
        else:
            best_method, best_detail = max(hits, key=lambda item: abs(item[1]["score"]))
        score = best_detail["score"]
        if direction == DIRECTION_UP and score <= 0:
            continue
        if direction == DIRECTION_DOWN and score >= 0:
            continue
        # 证据充分性下限：mad 与 moving 共用同一稳健尺度且都以中位数为中心，在同一
        # 序列上命中高度相关，"两票"实为一份证据；iqr 极值围栏在分解残差的"内挤+重尾"
        # 分布上也会低估围栏（IQR 同步缩水），极值票不能单独作为豁免凭证。因此除变点
        # 位移确认外，所有集成命中一律要求得分达到 CORRELATED_VOTE_FLOOR（≈4.5σ），
        # 3.5σ~4.5σ 之间的弱证据不报告——统计显著但证据不足的点计入提示
        if len(methods) >= 2 and not shift_confirmed and abs(score) < CORRELATED_VOTE_FLOOR:
            correlated_below += 1
            continue
        value = values[index]
        expected = offsets[index] + best_detail["expected"]
        deviation_ratio = calc_deviation_ratio(value, expected, series_center)
        # 实际意义下限：统计显著不等于值得关注。当序列本身几乎无噪声（如周期项被分解模型
        # 完整拟合、取值高度规整）时，1%~2% 的正常波动也会算出很高的稳健得分，
        # 这类点不作为异常报告，只计入提示，避免报告被无意义的小波动淹没。
        if deviation_ratio < params["minDeviationRatio"]:
            below_floor += 1
            continue
        extreme = any(detail.get("extreme") for _, detail in hits)
        anomaly_type = TYPE_LEVEL_SHIFT if shift_confirmed else classify_type(value, score)
        severity = grade_severity(score, deviation_ratio, extreme)
        if shift_confirmed and SEVERITY_ORDER[severity] < SEVERITY_ORDER[SEVERITY_MAJOR]:
            # 运行规则确认的是持续位移（≥3 点同向显著偏离），比瞬时尖峰更值得关注，
            # 严重度下限 MAJOR（与 group_episodes 对位移片段的处理一致）
            severity = SEVERITY_MAJOR
        anomalies.append(
            {
                "index": index,
                "time": times[index],
                "value": value,
                "expected": round(expected, 6),
                "score": round(score, 3),
                "deviationRatio": round(deviation_ratio, 4),
                "direction": DIRECTION_UP if score > 0 else DIRECTION_DOWN,
                "severity": severity,
                "type": anomaly_type,
                "methods": sorted({method for method, _ in hits}),
            }
        )

    anomalies.sort(key=lambda item: (-SEVERITY_ORDER[item["severity"]], -abs(item["score"])))
    episodes = group_episodes(sorted(anomalies, key=lambda item: item["index"]))

    severity_counts = {}
    for item in anomalies:
        severity_counts[item["severity"]] = severity_counts.get(item["severity"], 0) + 1

    is_stable = len(anomalies) == 0
    baseline_note = "已按周期 %d 的趋势+季节基线检测残差" % period if seasonal_adjusted else "未检出季节结构，按原序列检测"
    if is_stable:
        conclusion = "在 %s ~ %s 的 %d 个数据点上未检出统计显著异常（方法：%s，阈值 %s，%s）。" % (
            times[0],
            times[-1],
            len(valid_values),
            "/".join(methods),
            params["madThreshold"] if METHOD_MAD in methods else params["zscoreThreshold"],
            baseline_note,
        )
    else:
        top = anomalies[0]
        conclusion = "检出 %d 个异常点、%d 个异常片段；最显著的是 %s（%s，偏离基线约 %.1f%%，得分 %.2f）。" % (
            len(anomalies),
            len(episodes),
            top["time"],
            top["severity"],
            top["deviationRatio"] * 100,
            top["score"],
        )

    if METHOD_MOVING not in methods:
        warnings.append("样本量为 %d 个，未启用局部窗口方法，结论保守（仅 %s 检测器）" % (len(valid_values), "/".join(methods)))
    if below_floor > 0:
        warnings.append(
            "另有 %d 个点统计上显著但相对偏离不足 %.1f%%，未作为异常报告；如需纳回可下调 minDeviationRatio"
            % (below_floor, params["minDeviationRatio"] * 100)
        )
    if correlated_below > 0:
        warnings.append(
            "另有 %d 个点统计显著但集成证据不足（得分低于 %.1f），未作为异常报告"
            % (correlated_below, CORRELATED_VOTE_FLOOR)
        )

    truncated = len(anomalies) > params["maxAnomalies"]
    base.update(
        {
            "detectable": True,
            "config": {
                "method": requested,
                "methodsUsed": methods,
                "minVotes": min_votes,
                "direction": direction,
                "granularity": base["granularity"],
                "period": period,
                "seasonalAdjusted": seasonal_adjusted,
                "seasonalStrength": round(structure["strength"], 4) if seasonal_adjusted else None,
                "params": {
                    "madThreshold": params["madThreshold"],
                    "zscoreThreshold": params["zscoreThreshold"],
                    "iqrK": params["iqrK"],
                    "iqrExtremeK": params["iqrExtremeK"],
                    "window": params["window"],
                    "movingThreshold": params["movingThreshold"],
                    "ewmaAlpha": params["ewmaAlpha"],
                    "ewmaL": params["ewmaL"],
                    "minDeviationRatio": params["minDeviationRatio"],
                },
            },
            "summary": {
                "anomalyCount": len(anomalies),
                "episodeCount": len(episodes),
                "severityCounts": severity_counts,
                "isStable": is_stable,
                "trendHint": describe_trend(valid_values),
                "conclusion": conclusion,
            },
            "anomalies": anomalies[: params["maxAnomalies"]],
            "episodes": episodes,
            "warnings": warnings,
        }
    )
    if truncated:
        base["warnings"].append(
            "异常点超过 %d 个，仅返回前 %d 个，请缩小窗口或按维度拆分检测"
            % (params["maxAnomalies"], params["maxAnomalies"])
        )
    return base


def format_text(result):
    """把检测结果渲染为可读文本，便于直接写入报告"""
    if not result.get("success"):
        return "检测失败: %s" % result.get("error", "未知错误")

    lines = []
    stats = result["seriesStats"]
    lines.append("## 检测结果")
    lines.append("")
    lines.append(
        "- 指标: %s（%s，%s ~ %s，共 %d 点，有效 %d 点）"
        % (
            result.get("metric") or "-",
            result.get("granularity"),
            stats.get("start"),
            stats.get("end"),
            stats.get("points", 0),
            stats.get("validPoints", 0),
        )
    )
    config = result.get("config") or {}
    if config.get("methodsUsed"):
        lines.append(
            "- 检测方法: %s（投票阈值 %s，方向 %s）"
            % ("/".join(config["methodsUsed"]), config.get("minVotes"), config.get("direction"))
        )
    if config.get("seasonalAdjusted"):
        lines.append(
            "- 基线: 周期 %s 的趋势+季节稳健分解（季节强度 %.2f），判定基于残差"
            % (config.get("period"), config.get("seasonalStrength") or 0.0)
        )
    summary = result["summary"]
    lines.append("- 结论: %s" % summary["conclusion"])
    lines.append("- 走势: %s" % summary["trendHint"])
    if summary["severityCounts"]:
        lines.append(
            "- 严重度分布: %s"
            % "，".join("%s %d" % (k, v) for k, v in sorted(summary["severityCounts"].items()))
        )
    lines.append("")

    if result["anomalies"]:
        lines.append("| 时间 | 实际值 | 期望值 | 偏离幅度 | 得分 | 严重度 | 类型 | 命中方法 |")
        lines.append("| --- | --- | --- | --- | --- | --- | --- | --- |")
        for item in result["anomalies"]:
            lines.append(
                "| %s | %s | %s | %.1f%% | %.2f | %s | %s | %s |"
                % (
                    item["time"],
                    item["value"],
                    item["expected"],
                    item["deviationRatio"] * 100,
                    item["score"],
                    item["severity"],
                    item["type"],
                    ",".join(item["methods"]),
                )
            )
        lines.append("")

    if result["episodes"]:
        lines.append("异常片段:")
        for episode in result["episodes"]:
            lines.append(
                "  - %s ~ %s（%d 点，%s，%s，最大得分 %.2f）"
                % (
                    episode["startTime"],
                    episode["endTime"],
                    episode["length"],
                    episode["type"],
                    episode["severity"],
                    episode["maxScore"],
                )
            )
        lines.append("")

    if result["warnings"]:
        lines.append("数据质量提示:")
        for warning in result["warnings"]:
            lines.append("  - %s" % warning)
    return "\n".join(lines).rstrip()


def load_payload(raw_args):
    """载荷解析：JSON 文本 > 文件路径 > 标准输入"""
    if raw_args:
        arguments = list(raw_args)
        value = arguments[0]
        if value and (value.startswith("{") or value.startswith("[")):
            try:
                return json.loads(value)
            except json.JSONDecodeError as error:
                snippet = value if len(value) <= 60 else value[:60] + "..."
                raise ValueError(
                    "JSON 载荷解析失败（%s），收到的开头为 %r；若引号整体丢失"
                    "（形如 {metric: sales_amount}），是 Windows 命令行传参剥引号所致，"
                    "请改传 JSON 文件路径或经标准输入提供" % (error, snippet)
                ) from error
        if os.path.isfile(value):
            with open(value, "r", encoding="utf-8") as handle:
                return json.load(handle)
        raise ValueError("无法解析输入：既不是 JSON 文本，也不是存在的文件路径 -> %s" % value)
    text = sys.stdin.read()
    if not text.strip():
        raise ValueError("缺少输入：请在参数中传入 JSON 载荷，或通过标准输入提供")
    return json.loads(text)


def parse_args(argv):
    """参数解析：首个非选项参数为载荷，--format 控制输出格式"""
    parser = argparse.ArgumentParser(description="指标异常检测引擎")
    parser.add_argument("payload", nargs="?", help="JSON 载荷文本或 JSON 文件路径（缺省读取标准输入）")
    parser.add_argument("--format", "-f", choices=("json", "text"), default="json", help="输出格式，默认 json")
    return parser.parse_args(argv)


def main(argv=None):
    args = parse_args(sys.argv[1:] if argv is None else argv)
    try:
        payload = load_payload([args.payload] if args.payload else [])
        result = build_result(payload)
    except Exception as error:  # 输入/结构错误统一返回结构化失败，便于 Agent 自愈
        result = {"success": False, "error": "%s: %s" % (type(error).__name__, error)}
    if args.format == "text":
        print(format_text(result) if result.get("success") else "检测失败: %s" % result["error"])
    else:
        print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0 if result.get("success") else 1


if __name__ == "__main__":
    sys.exit(main())