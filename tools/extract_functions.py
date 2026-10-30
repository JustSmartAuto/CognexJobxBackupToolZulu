#!/usr/bin/env python3
"""从 CognexInsightFunctionAPI.md 提取电子表格函数数据，生成 JSON。"""
import json
import re
import sys

SRC = "CognexInsightAPI.md" if len(sys.argv) < 2 else sys.argv[1]
OUT = "functions.json" if len(sys.argv) < 3 else sys.argv[2]

text = open(SRC, encoding="utf-8").read()
lines = text.splitlines()

# 按 "## " 标题分段
sections = []  # (name, [lines])
current_name, current_lines = None, []
for line in lines:
    m = re.match(r"^##\s+(.+?)\s*$", line)
    if m:
        if current_name is not None:
            sections.append((current_name, current_lines))
        current_name, current_lines = m.group(1), []
    elif current_name is not None:
        current_lines.append(line)
if current_name is not None:
    sections.append((current_name, current_lines))

GENERIC = {
    "overview", "example", "inputs", "outputs", "remarks", "see also",
    "what is blob analysis?", "when is blob analysis used?",
    "why is blob analysis used?", "how to perform blob analysis",
    "about calibrate", "calibrate functions",
    "printing the calibration pattern using the print grid dialog",
    "about calibrategrid", "caliper overview", "caliper general tab",
    "calibratepoints overview", "compareimage overview", "compareimage example",
    "computeimagesharpness overview", "colorize", "controls functions",
    "coordinate transforms functions", "clocked data storage functions",
    "dialog", "region object", "configure", "image", "binary", "event",
    "what is pattern training?",
}

functions = []
for name, body in sections:
    src_marker = next((l for l in body if "源文件" in l), None)
    if not src_marker:
        continue
    src_file = re.search(r"`([^`]+\.htm)`", src_marker)
    if not src_file:
        continue
    base = src_file.group(1).rsplit("/", 1)[-1][:-4]
    # 函数页: 标题与源文件同名, 且标题是合法标识符
    if not re.fullmatch(r"[A-Za-z][A-Za-z0-9_]*", name.strip()):
        continue
    if base.replace("_", "").lower() != name.strip().replace("_", "").lower():
        continue
    category = "3D" if "3DVisionTools" in src_marker else "2D/通用"
    joined = "\n".join(body)
    # 语法: 优先 Syntax: Name(...)，其次 "has the following syntax: Name(...)"
    sig = None
    m = re.search(r"Syntax:\s*([A-Za-z][A-Za-z0-9_]*\s*\([^)]*\))", joined, re.S)
    if m:
        sig = re.sub(r"\s+", " ", m.group(1)).strip()
    else:
        m = re.search(r"has the following syntax:\s*([A-Za-z][A-Za-z0-9_]*\s*\([^)]*\))", joined, re.S)
        if m:
            sig = re.sub(r"\s+", " ", m.group(1)).strip()
    # 描述: Syntax 行之前的第一段非空文本
    desc = ""
    prefix = joined.split("Syntax:")[0]
    paras = [p.strip() for p in prefix.split("\n\n") if p.strip()]
    for p in paras:
        p = re.sub(r"\*源文件[^\n]*\*", "", p).strip()
        if p and not p.startswith("|") and not p.startswith("#"):
            desc = re.sub(r"\s+", " ", p)
            break
    functions.append({
        "name": name.strip(),
        "signature": sig or (name.strip() + "(...)"),
        "description": desc,
        "category": category,
    })

functions.sort(key=lambda f: f["name"].lower())
json.dump(functions, open(OUT, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
print(f"extracted {len(functions)} functions -> {OUT}")
