#!/usr/bin/env python3
"""文档-代码口径一致性检查（methods.md / SKILL.md / references/examples ↔ anomaly_detect.py）

methods.md 与 SKILL.md 是 scripts/anomaly_detect.py 的人工镜像：同一套阈值与参数在文档
和代码里各写一遍，没有自动校验就会漂移（曾出现 moving 阈值文档写 3.0、代码实际用 3.5）。
本脚本解析文档中的参数速查表、检测器公式门限（含比较符号）、内置常量清单、严重度标准与
样本量门槛，逐项与脚本常量比对；另将 references/examples 下示例文件记载的"真实输出"用
脚本实跑复核（示例也是给 Agent 看的提示词，输出漂移同样误导）。任一数值或比较符号不一致、
或文档中应有的条目解析不到，都以非零码退出，用于本地检查或将来接入 CI。

用法:
    python scripts/check_doc_consistency.py                  # 校验本 skill 目录
    python scripts/check_doc_consistency.py --skill-dir 目录
"""

import argparse
import importlib.util
import json
import re
import sys
from pathlib import Path

DOC_METHODS = "references/methods.md"
DOC_SKILL = "SKILL.md"
ENGINE_SCRIPT = "scripts/anomaly_detect.py"
EXAMPLES_DIR = "references/examples"

# 文档"引擎内置常量"清单必须列出的常量（脚本新增内置常量时同步加入本表）
BUILTIN_CONSTANTS = (
    "SHIFT_WINDOW",
    "SHIFT_WINDOW_MISSING_TOLERANCE",
    "SHIFT_Z_THRESHOLD",
    "CORRELATED_VOTE_FLOOR",
    "WINSOR_BOUND_SIGMA",
    "DECOMPOSE_REFINE_ROUNDS",
    "GRANULARITY_STEP_TOLERANCE",
)

# 参数速查表：文档行键 → 脚本常量（组合行按顺序一一对应）
PARAM_ROWS = (
    (("threshold",), ("DEFAULT_MAD_THRESHOLD",)),
    (("madThreshold",), ("DEFAULT_MAD_THRESHOLD",)),
    (("zscoreThreshold",), ("DEFAULT_ZSCORE_THRESHOLD",)),
    (("iqrK", "iqrExtremeK"), ("DEFAULT_IQR_K", "DEFAULT_IQR_EXTREME_K")),
    (("window",), ("DEFAULT_MOVING_WINDOW",)),
    (("movingThreshold",), ("DEFAULT_MOVING_THRESHOLD",)),
    (("alpha", "l"), ("DEFAULT_EWMA_ALPHA", "DEFAULT_EWMA_L")),
    (("minDeviationRatio",), ("MIN_DEVIATION_RATIO",)),
    (("maxAnomalies",), ("DEFAULT_MAX_ANOMALIES",)),
)

# 检测器小节的公式门限：(小节标题关键字, 正则, (常量名,), 期望比较符号)
# 正则的符号字符类含全角 ≥≤，否则符号被改写成正负号时会退化成"解析失败"而非"符号不一致"
DETECTOR_FORMULAS = (
    ("MAD 稳健修正 Z 分数", r"([><≥≤]=?)\s*([0-9.]+)\s*判为异常", ("DEFAULT_MAD_THRESHOLD",), ">"),
    ("经典 Z 分数", r"([><≥≤]=?)\s*([0-9.]+)\s*判为异常", ("DEFAULT_ZSCORE_THRESHOLD",), ">"),
    ("局部线性趋势残差", r"([><≥≤]=?)\s*([0-9.]+)\s*判为异常", ("DEFAULT_MOVING_THRESHOLD",), ">"),
)


class Report:
    """收集检查结果：任一 DRIFT / 解析失败即整体失败"""

    def __init__(self):
        self.failures = []
        self.checked = 0

    def ok(self, label, doc_value, code_value):
        self.checked += 1
        print("  [OK]    %-38s 文档=%-8s 代码=%s" % (label, doc_value, code_value))

    def drift(self, label, doc_value, code_value):
        self.checked += 1
        self.failures.append(label)
        print("  [漂移]  %-38s 文档=%-8s 代码=%s" % (label, doc_value, code_value))

    def missing(self, label, detail):
        self.failures.append(label)
        print("  [缺项]  %-38s %s" % (label, detail))

    def compare(self, label, doc_value, code_value):
        if doc_value is None:
            self.missing(label, "文档中未解析到该项（结构调整后请同步本检查脚本）")
        elif isinstance(doc_value, float) and isinstance(code_value, (int, float)):
            (self.ok if abs(doc_value - code_value) < 1e-9 else self.drift)(label, doc_value, code_value)
        else:
            (self.ok if doc_value == code_value else self.drift)(label, doc_value, code_value)


def load_engine(skill_dir):
    spec = importlib.util.spec_from_file_location("anomaly_detect_doc_check", skill_dir / ENGINE_SCRIPT)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def section_body(text, needle):
    """取含关键字的小节正文（到下一个同级或更高级标题为止，子小节计入正文）"""
    marks = list(re.finditer(r"^(#{2,4}) .*$", text, re.MULTILINE))
    for position, mark in enumerate(marks):
        if needle not in mark.group(0):
            continue
        level = len(mark.group(1))
        end = len(text)
        for following in marks[position + 1:]:
            if len(following.group(1)) <= level:
                end = following.start()
                break
        return text[mark.end():end]
    return None


def param_rows(text):
    """参数速查表 → {("键", ...): (单元格列表, 默认值单元格中的数值列表)}

    默认值可能是数值（3.5）或枚举字面量（auto/both/自动推断），两者都要保留：
    直接丢弃非数值行会让 method/direction 这类行静默漏检。
    """
    body = section_body(text, "参数速查") or ""
    rows = {}
    for line in body.splitlines():
        if not line.startswith("|"):
            continue
        cells = [cell.strip() for cell in line.strip("|").split("|")]
        if len(cells) < 2 or set(cells[0]) <= set("- "):
            continue
        keys = tuple(re.findall(r"`([A-Za-z]+)`", cells[0]))
        if keys:
            rows[keys] = (cells, re.findall(r"[0-9]+(?:\.[0-9]+)?", cells[1]))
    return rows


def method_table_cell(text, method):
    """SKILL.md 检测方法速查表中某方法行的"关键参数"单元格"""
    body = section_body(text, "检测方法速查") or ""
    match = re.search(r"\|\s*`" + method + r"`[^|]*\|[^|]*\|\s*([^|]*?)\s*\|", body)
    return match.group(1) if match else None


def check_doc_params(report, methods_text, engine):
    print("\n[1] methods.md 参数速查表 ↔ 脚本默认值")
    rows = param_rows(methods_text)
    for keys, constant_names in PARAM_ROWS:
        row = rows.get(keys)
        values = row[1] if row else []
        for position, constant_name in enumerate(constant_names):
            label = "参数 %s" % keys[position]
            if position >= len(values):
                report.missing(label, "参数速查表中未解析到该行/该值")
                continue
            report.compare(label, float(values[position]), getattr(engine, constant_name))
    print("\n[2] methods.md 头部载荷说明 ↔ 脚本（method/direction 默认值与可选值清单）")
    method_cells = rows[("method",)][0] if ("method",) in rows else []
    report.compare(
        "method 默认值",
        method_cells[1].strip("`") if len(method_cells) > 1 else None,
        engine.METHOD_AUTO,
    )
    direction_cells = rows[("direction",)][0] if ("direction",) in rows else []
    report.compare(
        "direction 默认值",
        direction_cells[1].strip("`") if len(direction_cells) > 1 else None,
        engine.DIRECTION_BOTH,
    )
    documented = set(re.findall(r"[a-z]+", method_cells[2])) if len(method_cells) > 2 else set()
    listed = documented & set(engine.SUPPORTED_METHODS)
    absent = sorted(set(engine.SUPPORTED_METHODS) - listed)
    if absent:
        report.missing("method 可选值清单", "文档漏列: %s" % ", ".join(absent))
    else:
        report.ok("method 可选值清单", "%d 项" % len(engine.SUPPORTED_METHODS), "全部列出")


def check_builtin_constants(report, methods_text, engine):
    print("\n[3] methods.md 内置常量清单 ↔ 脚本常量")
    body = section_body(methods_text, "参数速查") or ""
    documented = {
        name: float(value)
        for name, value in re.findall(r"`([A-Z][A-Z0-9_]*)`\s*=\s*([0-9.]+)", body)
    }
    for name in BUILTIN_CONSTANTS:
        code_value = getattr(engine, name, None)
        report.compare("内置常量 %s" % name, documented.get(name), code_value)


def check_detector_formulas(report, methods_text, engine):
    print("\n[4] methods.md 检测器公式门限与比较符号 ↔ 脚本")
    for needle, pattern, constant_names, expected_operator in DETECTOR_FORMULAS:
        body = section_body(methods_text, needle)
        match = re.search(pattern, body) if body else None
        if not match:
            report.missing("%s 公式门限" % needle, "公式中未解析到判据（请检查文档结构）")
            continue
        operator, value = match.group(1), float(match.group(2))
        report.compare("%s 门限" % needle, value, getattr(engine, constant_names[0]))
        if expected_operator is not None:
            report.compare("%s 比较符号" % needle, operator, expected_operator)
    print("\n[5] methods.md 其余门限（IQR / 位移）↔ 脚本")
    iqr_body = section_body(methods_text, "Tukey 箱线图围栏") or ""
    iqr_match = re.search(r"k\s*=\s*([0-9.]+)\s*判为离群[\s\S]*?k\s*=\s*([0-9.]+)\s*判为极值", iqr_body)
    if iqr_match:
        report.compare("IQR 离群围栏倍数", float(iqr_match.group(1)), engine.DEFAULT_IQR_K)
        report.compare("IQR 极值围栏倍数", float(iqr_match.group(2)), engine.DEFAULT_IQR_EXTREME_K)
    else:
        report.missing("IQR 围栏倍数", "公式段落未解析到 k 值")
    shift_body = section_body(methods_text, "水平位移确认") or ""
    shift_match = re.search(r"SHIFT_Z_THRESHOLD`?\(([0-9.]+)\)", shift_body)
    report.compare(
        "位移确认门限",
        float(shift_match.group(1)) if shift_match else None,
        engine.SHIFT_Z_THRESHOLD,
    )
    report.compare(
        "位移确认符号",
        "≥" if shift_match and re.search(r"\|Z\|\s*≥", shift_body) else None,
        "≥",
    )
    ewma_body = section_body(methods_text, "EWMA 指数加权控制图") or ""
    ewma_match = re.search(r"α\s*=\s*([0-9.]+)[，,]\s*L\s*=\s*([0-9.]+)", ewma_body)
    if ewma_match:
        report.compare("EWMA 平滑系数 α", float(ewma_match.group(1)), engine.DEFAULT_EWMA_ALPHA)
        report.compare("EWMA 控制限 L", float(ewma_match.group(2)), engine.DEFAULT_EWMA_L)
    else:
        report.missing("EWMA 参数", "公式段落未解析到 α/L")
    # EWMA 判据写的是符号 L 而非数字：数值由上一项校验，这里校验比较符号
    ewma_judge = re.search(r"([><]=?)\s*L\s*判为异常", ewma_body)
    report.compare("EWMA 比较符号", ewma_judge.group(1) if ewma_judge else None, ">")


def check_severity_and_gates(report, methods_text, engine):
    print("\n[6] methods.md 严重度标准 ↔ 脚本")
    body = section_body(methods_text, "严重度分级与类型分类") or ""
    scores = [float(value) for value in re.findall(r"稳健得分\s*≥\s*([0-9.]+)", body)]
    report.compare("CRITICAL 稳健得分", scores[0] if len(scores) > 0 else None, engine.SCORE_CRITICAL)
    report.compare("MAJOR 稳健得分", scores[1] if len(scores) > 1 else None, engine.SCORE_MAJOR)
    ratios = [float(value) / 100.0 for value in re.findall(r"相对偏离\s*≥\s*([0-9.]+)%", body)]
    report.compare(
        "CRITICAL 相对偏离", ratios[0] if len(ratios) > 0 else None, engine.DEVIATION_RATIO_CRITICAL
    )
    report.compare("MAJOR 相对偏离", ratios[1] if len(ratios) > 1 else None, engine.DEVIATION_RATIO_MAJOR)
    floor_match = re.search(r"`deviationRatio < minDeviationRatio`", body)
    report.compare("实际意义下限符号", "<" if floor_match else None, "<")

    print("\n[7] methods.md 样本量门槛 ↔ 脚本")
    gates = section_body(methods_text, "样本量门槛") or ""
    detect_floor = re.search(r"\|\s*<\s*([0-9]+)\s*\|", gates)
    report.compare(
        "检测下限点数",
        float(detect_floor.group(1)) if detect_floor else None,
        engine.MIN_VALID_POINTS,
    )
    moving_floor = re.search(r"\|\s*≥\s*([0-9]+)\s*\|", gates)
    report.compare(
        "moving 启用点数",
        float(moving_floor.group(1)) if moving_floor else None,
        engine.MIN_POINTS_FOR_MOVING,
    )
    season_floor = re.search(r"MIN_POINTS_PER_SEASON\s*=\s*([0-9]+)", gates)
    report.compare(
        "分解所需周期倍数",
        float(season_floor.group(1)) if season_floor else None,
        engine.MIN_POINTS_PER_SEASON,
    )


def check_skill_doc(report, skill_text, engine):
    print("\n[8] SKILL.md 检测方法速查表 ↔ 脚本")
    expectations = (
        ("mad", r"threshold\s*([0-9.]+)", ("DEFAULT_MAD_THRESHOLD",)),
        ("zscore", r"threshold\s*([0-9.]+)", ("DEFAULT_ZSCORE_THRESHOLD",)),
        ("moving", r"window\s*([0-9]+)、threshold\s*([0-9.]+)", ("DEFAULT_MOVING_WINDOW", "DEFAULT_MOVING_THRESHOLD")),
        ("ewma", r"alpha\s*([0-9.]+)、L\s*([0-9.]+)", ("DEFAULT_EWMA_ALPHA", "DEFAULT_EWMA_L")),
        ("shift", r"门限\s*([0-9.]+)", ("SHIFT_Z_THRESHOLD",)),
    )
    for method, pattern, constant_names in expectations:
        cell = method_table_cell(skill_text, method)
        match = re.search(pattern, cell) if cell else None
        if not match:
            report.missing("SKILL.md %s 关键参数" % method, "速查表中未解析到该行/该参数")
            continue
        for position, constant_name in enumerate(constant_names):
            report.compare(
                "SKILL.md %s" % method,
                float(match.group(position + 1)),
                getattr(engine, constant_name),
            )
    iqr_cell = method_table_cell(skill_text, "iqr")
    iqr_match = re.search(r"k=([0-9.]+)[^0-9]*([0-9.]+)", iqr_cell) if iqr_cell else None
    if iqr_match:
        report.compare("SKILL.md iqr 围栏倍数", float(iqr_match.group(1)), engine.DEFAULT_IQR_K)
        report.compare("SKILL.md iqr 极值倍数", float(iqr_match.group(2)), engine.DEFAULT_IQR_EXTREME_K)
    else:
        report.missing("SKILL.md iqr 关键参数", "速查表中未解析到 k 值")

    print("\n[9] SKILL.md 检测口径默认值 ↔ 脚本")
    threshold_row = re.search(
        r"判定阈值 threshold\s*\|\s*MAD\s*([0-9.]+)\s*/\s*3σ\s*([0-9.]+)\s*/\s*IQR\s*([0-9.]+)\s*与\s*([0-9.]+)",
        skill_text,
    )
    if threshold_row:
        for position, (label, constant_name) in enumerate((
            ("SKILL.md MAD 阈值", "DEFAULT_MAD_THRESHOLD"),
            ("SKILL.md 3σ 阈值", "DEFAULT_ZSCORE_THRESHOLD"),
            ("SKILL.md IQR 阈值", "DEFAULT_IQR_K"),
            ("SKILL.md IQR 极值阈值", "DEFAULT_IQR_EXTREME_K"),
        )):
            report.compare(label, float(threshold_row.group(position + 1)), getattr(engine, constant_name))
    else:
        report.missing("SKILL.md 判定阈值行", "未解析到 MAD/3σ/IQR 阈值行")
    floor_row = re.search(r"minDeviationRatio\s*\|\s*([0-9.]+)", skill_text)
    report.compare(
        "SKILL.md 实际意义下限",
        float(floor_row.group(1)) if floor_row else None,
        engine.MIN_DEVIATION_RATIO,
    )
    method_cell = re.search(r"检测方法 method\s*\|\s*`([a-z]+)`", skill_text)
    report.compare(
        "SKILL.md method 默认值",
        method_cell.group(1) if method_cell else None,
        engine.METHOD_AUTO,
    )

    print("\n[10] SKILL.md 严重度口径 ↔ 脚本（Agent 直接按此陈述，数字漂移同样有害）")
    for severity, ratio_constant, score_constant in (
        ("CRITICAL", "DEVIATION_RATIO_CRITICAL", "SCORE_CRITICAL"),
        ("MAJOR", "DEVIATION_RATIO_MAJOR", "SCORE_MAJOR"),
    ):
        match = re.search(
            r"`%s`：偏离幅度\s*≥\s*([0-9.]+)%%\s*或稳健 Z\s*≥\s*([0-9.]+)" % severity,
            skill_text,
        )
        if not match:
            report.missing("SKILL.md %s 严重度" % severity, "未解析到严重度行")
            continue
        report.compare(
            "SKILL.md %s 偏离幅度" % severity,
            float(match.group(1)) / 100.0,
            getattr(engine, ratio_constant),
        )
        report.compare(
            "SKILL.md %s 稳健 Z" % severity,
            float(match.group(2)),
            getattr(engine, score_constant),
        )


def check_examples(report, skill_dir, engine):
    """示例文件是给 Agent 看的提示词，记载的"真实输出"必须与脚本当前行为一致。

    示例是 JSONC（注释 + 多个 JSON 对象）：取含 `series` 的对象作为检测请求实跑，
    与含 `config`+`summary` 的对象（文档记载的响应）按"文档键逐项比对"——文档是节选，
    只要求文档写出的每个字段与实跑一致，不要求文档穷举全部字段。
    """
    print("\n[11] references/examples 示例记载输出 ↔ 脚本实跑")
    examples_dir = skill_dir / EXAMPLES_DIR
    paths = sorted(examples_dir.glob("*.json")) if examples_dir.is_dir() else []
    if not paths:
        report.missing("examples 目录", "%s 下未找到示例文件" % EXAMPLES_DIR)
        return
    for path in paths:
        text = path.read_text(encoding="utf-8")
        payload, documented = None, None
        for candidate in iter_json_objects(text):
            if isinstance(candidate, dict) and "series" in candidate and payload is None:
                payload = candidate
            if isinstance(candidate, dict) and "config" in candidate and "summary" in candidate and documented is None:
                documented = candidate
        if payload is None or documented is None:
            report.missing("示例 %s" % path.name, "未解析到检测请求（series）或记载响应（config+summary）")
            continue
        actual = engine.build_result(json.loads(json.dumps(payload)))
        check_documented_object(
            report, "示例 %s" % path.name, documented, actual
        )


def iter_json_objects(text):
    """按花括号配对切出顶层 JSON 对象（容忍 JSONC 注释与前后散文）"""
    depth, start, in_string, escaped = 0, None, False, False
    for position, char in enumerate(text):
        if in_string:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == '"':
                in_string = False
            continue
        if char == '"':
            in_string = True
        elif char == "{":
            if depth == 0:
                start = position
            depth += 1
        elif char == "}":
            depth -= 1
            if depth == 0 and start is not None:
                try:
                    yield json.loads(text[start:position + 1])
                except ValueError:
                    pass
                start = None


def check_documented_object(report, label, documented, actual, prefix=""):
    """按文档写出的键逐项比对（dict 递归；文档未写的键不要求）"""
    if not isinstance(documented, dict):
        if documented != actual:
            report.drift("%s%s" % (label, prefix), documented, actual)
        else:
            report.checked += 1
        return
    for key, doc_value in documented.items():
        actual_value = actual.get(key) if isinstance(actual, dict) else None
        if isinstance(doc_value, dict) and isinstance(actual_value, dict):
            check_documented_object(report, label, doc_value, actual_value, "%s.%s" % (prefix, key))
        elif isinstance(doc_value, list) and doc_value and all(isinstance(item, dict) for item in doc_value):
            # 列表按条目对齐比较（anomalies/episodes 顺序文档与实现一致：严重度+得分排序）
            if not isinstance(actual_value, list) or len(actual_value) < len(doc_value):
                report.drift("%s%s.%s 条数" % (label, prefix, key), len(doc_value), actual_value if not isinstance(actual_value, list) else len(actual_value))
                continue
            for position, item in enumerate(doc_value):
                check_documented_object(report, label, item, actual_value[position], "%s.%s[%d]" % (prefix, key, position))
        else:
            if doc_value is None:
                # 文档显式写了 null（如 seasonalStrength: null）≠ 未解析到：校验实际值同为空
                (report.ok if actual_value is None else report.drift)(
                    "%s%s.%s" % (label, prefix, key), "null", actual_value
                )
            else:
                report.compare("%s%s.%s" % (label, prefix, key), doc_value, actual_value)


def main(argv=None):
    parser = argparse.ArgumentParser(description="skill 文档与检测脚本的口径一致性检查")
    parser.add_argument("--skill-dir", default=None, help="skill 根目录（缺省为脚本上级的上级）")
    args = parser.parse_args(argv)
    skill_dir = Path(args.skill_dir).resolve() if args.skill_dir else Path(__file__).resolve().parents[1]

    methods_path = skill_dir / DOC_METHODS
    skill_path = skill_dir / DOC_SKILL
    for path in (methods_path, skill_path, skill_dir / ENGINE_SCRIPT):
        if not path.is_file():
            print("缺少文件: %s" % path)
            return 2

    engine = load_engine(skill_dir)
    methods_text = methods_path.read_text(encoding="utf-8")
    skill_text = skill_path.read_text(encoding="utf-8")

    print("检查目录: %s" % skill_dir)
    report = Report()
    check_doc_params(report, methods_text, engine)
    check_builtin_constants(report, methods_text, engine)
    check_detector_formulas(report, methods_text, engine)
    check_severity_and_gates(report, methods_text, engine)
    check_skill_doc(report, skill_text, engine)
    check_examples(report, skill_dir, engine)

    print("\n共检查 %d 项，漂移/缺项 %d 项" % (report.checked, len(report.failures)))
    if report.failures:
        print("口径不一致: %s" % "；".join(report.failures))
        print("请同步文档与脚本（口径以 %s 的常量为准）" % ENGINE_SCRIPT)
        return 1
    print("文档与脚本口径一致")
    return 0


if __name__ == "__main__":
    sys.exit(main())
