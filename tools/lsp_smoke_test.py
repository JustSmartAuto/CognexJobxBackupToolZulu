#!/usr/bin/env python3
"""LSP 服务器冒烟测试：通过 stdio 发送 initialize/didOpen/hover/completion/signatureHelp。"""
import json
import subprocess
import sys

JAR = sys.argv[1] if len(sys.argv) > 1 else "build/libs/cognex-jobx-lsp-server-1.0.0-all.jar"

proc = subprocess.Popen(
    ["java", "-jar", JAR],
    stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL,
    text=False,
)

next_id = 0
pending_notifications = []


def send(payload: dict):
    body = json.dumps(payload).encode()
    proc.stdin.write(b"Content-Length: %d\r\n\r\n" % len(body) + body)
    proc.stdin.flush()


def request(method: str, params: dict):
    global next_id
    next_id += 1
    send({"jsonrpc": "2.0", "id": next_id, "method": method, "params": params})
    while True:
        msg = read_msg()
        if "id" in msg:
            return msg
        pending_notifications.append(msg)


def notify(method: str, params: dict):
    send({"jsonrpc": "2.0", "method": method, "params": params})


def read_msg():
    headers = {}
    while True:
        line = proc.stdout.readline().decode().strip()
        if not line:
            break
        k, _, v = line.partition(":")
        headers[k.strip().lower()] = v.strip()
    body = proc.stdout.read(int(headers["content-length"]))
    return json.loads(body)


def drain_notifications():
    """读取当前可读的服务器通知（用于取 publishDiagnostics）。"""
    out = list(pending_notifications)
    pending_notifications.clear()
    return out


URI = "file:///test.script.js"
SCRIPT = '"use strict";\nfunction Script() { this.total = 0; }\nmodule.exports = Script;\nScript.prototype.run = function(a, b) { return a + b; };\nScript.prototype.draw = function(gr) { gr.plotPoint(1, 2, "p", 0x00FF00); };\n'

failures = []


def check(name, cond, detail=""):
    print(("PASS " if cond else "FAIL ") + name + (" | " + detail if detail and not cond else ""))
    if not cond:
        failures.append(name)


init = request("initialize", {"processId": None, "capabilities": {}})
check("initialize", init.get("result", {}).get("capabilities", {}).get("hoverProvider") is True)
notify("initialized", {})
notify("textDocument/didOpen", {"textDocument": {"uri": URI, "languageId": "javascript", "version": 1, "text": SCRIPT}})
request("textDocument/hover", {"textDocument": {"uri": URI}, "position": {"line": 0, "character": 1}})  # 拉取通知
diags = [m for m in drain_notifications() if m.get("method") == "textDocument/publishDiagnostics"]
check("publishDiagnostics received", len(diags) == 1)
check("diagnostics clean", len(diags[0].get("params", {}).get("diagnostics", [])) == 0 if diags else False)

# hover on plotPoint (line 4: ... gr.plotPoint(...)), 光标在标识符内部
line4 = SCRIPT.splitlines()[4]
ch = line4.index("plotPoint") + 3
hover = request("textDocument/hover", {"textDocument": {"uri": URI}, "position": {"line": 4, "character": ch}})
hv = json.dumps(hover, ensure_ascii=False)
check("hover plotPoint", "plotPoint" in hv and "绘制" in hv, hv[:200])

# hover on spreadsheet-style word (module) → 返回 null 即可
hover2 = request("textDocument/hover", {"textDocument": {"uri": URI}, "position": {"line": 0, "character": 3}})
check("hover non-symbol ok", "result" in hover2)

# completion 顶层: 空位置（行首）
comp = request("textDocument/completion", {"textDocument": {"uri": URI}, "position": {"line": 0, "character": 0}})
labels = [i.get("label", "") for i in comp.get("result", {}).get("items", [])]
check("completion functions", "Accumulate" in labels and "BlobToBlob" in labels, str(labels[:5]))
check("completion script api", "plotPoint" in labels and "uptime" in labels)
check("completion keywords", "function" in labels)
check("completion snippets", any("script-template" == l for l in labels))

# completion 成员访问 gr. → 只出绘制方法
line4b = SCRIPT.splitlines()[4]
chgr = line4b.index("gr.") + 3
compm = request("textDocument/completion", {"textDocument": {"uri": URI}, "position": {"line": 4, "character": chgr}})
labelsm = [i.get("label", "") for i in compm.get("result", {}).get("items", [])]
check("completion gr. members", "plotPoint" in labelsm, str(labelsm[:10]))

# signature help: 光标在 plotPoint( 参数列表内
chsig = line4b.index("plotPoint(") + len("plotPoint(") + 1
sig = request("textDocument/signatureHelp", {"textDocument": {"uri": URI}, "position": {"line": 4, "character": chsig}})
sigs = sig.get("result", {}).get("signatures", [])
check("signatureHelp plotPoint", any("plotPoint" in s.get("label", "") for s in sigs), json.dumps(sig, ensure_ascii=False)[:200])

# diagnostics on broken script
BAD = '"use strict";\nfunction Script() {}\nmodule.exports = Script;\ngr.plotFoo(1);\nvar x = (1 + 2;\n'
notify("textDocument/didChange", {"textDocument": {"uri": URI, "version": 2}, "contentChanges": [{"text": BAD}]})
request("textDocument/hover", {"textDocument": {"uri": URI}, "position": {"line": 0, "character": 1}})  # 拉取通知
diags2 = [m for m in drain_notifications() if m.get("method") == "textDocument/publishDiagnostics"]
msgs = [d.get("message", "") for d in diags2[-1].get("params", {}).get("diagnostics", [])] if diags2 else []
check("diag missing run", any("run" in m for m in msgs), str(msgs))
check("diag unknown gr method", any("plotFoo" in m for m in msgs), str(msgs))
check("diag unbalanced paren", any("未闭合" in m or "多余" in m for m in msgs), str(msgs))

request("shutdown", {})
send({"jsonrpc": "2.0", "method": "exit"})
proc.wait(timeout=10)
print("FAILURES: " + (", ".join(failures) if failures else "none"))
sys.exit(1 if failures else 0)
