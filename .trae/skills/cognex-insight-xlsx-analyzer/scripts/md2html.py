# md2html.py — 把技能产出的 .md 说明文档包一层样式转成讲解 html
# 用法: python md2html.py <md文件> <标题> <副标题> [-o out.html]
import pathlib, html as H, re, sys

def inline(s):
    """转义后处理行内 markdown：`**粗**` → <strong>，`` `代码` `` → <code>"""
    s = re.sub(r"\*\*([^*]+)\*\*", r"<strong>\1</strong>", s)
    s = re.sub(r"`([^`]+)`", r"<code>\1</code>", s)
    return s

def md_to_html(md, title, sub):
    lines = md.splitlines()
    out = []; i = 0
    inlist = False
    while i < len(lines):
        line = lines[i]
        if line.startswith("|") and i+1 < len(lines) and re.match(r"^\|[\s:|-]+\|$", lines[i+1]):
            rows = []
            while i < len(lines) and lines[i].startswith("|"):
                if not re.match(r"^\|[\s:|-]+\|$", lines[i]):
                    cells = [c.strip().replace("\\|", "|") for c in re.split(r"(?<!\\)\|", lines[i].strip("|"))]
                    rows.append(cells)
                i += 1
            t = "<table>" + "".join(
                "<tr>" + "".join(f"<th>{inline(H.escape(c))}</th>" for c in r) + "</tr>" if j == 0 else
                "<tr>" + "".join(f"<td>{inline(H.escape(c))}</td>" for c in r) + "</tr>"
                for j, r in enumerate(rows)) + "</table>"
            out.append(t); continue
        m = re.match(r'^!\[([^]]*)\]\(([^)]+)\)\s*$', line)
        if m:
            out.append(f'<figure style="margin:16px 0;text-align:center">'
                       f'<img src="{H.escape(m.group(2))}" alt="{H.escape(m.group(1))}" '
                       f'style="max-width:100%;border:1px solid #ccc;border-radius:8px">'
                       f'<figcaption style="font-size:13px;color:#666">{inline(H.escape(m.group(1)))}</figcaption></figure>')
        elif line.startswith("```"):
            i += 1; buf = []
            while i < len(lines) and not lines[i].startswith("```"):
                buf.append(H.escape(lines[i])); i += 1
            out.append(f"<pre><code>{chr(10).join(buf)}</code></pre>")
        elif re.match(r"^\s*---+\s*$", line):
            out.append("<hr style='border:none;border-top:1px solid #dde1e6;margin:18px 0'>")
        elif line.startswith("### "): out.append(f"<h3>{inline(H.escape(line[4:]))}</h3>")
        elif line.startswith("## "): out.append(f"<h2>{inline(H.escape(line[3:]))}</h2>")
        elif line.startswith("# "): out.append(f"<h1>{inline(H.escape(line[2:]))}</h1>")
        elif re.match(r"^\s*(- |\d+\. )", line):
            out.append(f"<li>{inline(H.escape(re.sub(r'^\s*(- |\d+\. )', '', line)))}</li>")
        elif line.strip(): out.append(f"<p>{inline(H.escape(line))}</p>")
        i += 1
    body = "\n".join(out)
    return f"""<!DOCTYPE html>
<html lang="zh-CN"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>{H.escape(title)}</title>
<style>
body{{font-family:"Microsoft YaHei","PingFang SC",Arial,sans-serif;max-width:1000px;margin:0 auto;padding:24px;line-height:1.75;background:#f5f6f8;color:#222}}
header{{background:linear-gradient(135deg,#1565c0,#283593);color:#fff;padding:28px;margin:-24px -24px 20px;border-radius:0}}
header p{{margin:2px 0;opacity:.92;font-size:14px}}
h1{{font-size:24px;margin:0 0 6px}} h2{{color:#1565c0;border-left:5px solid #1565c0;padding-left:10px;font-size:20px;margin-top:28px}}
table{{border-collapse:collapse;width:100%;font-size:14px;background:#fff;margin:10px 0}}
th,td{{border:1px solid #dde1e6;padding:6px 10px;text-align:left;vertical-align:top}} th{{background:#eef2f7}}
code{{background:#eceff1;padding:1px 5px;border-radius:4px;color:#b71c1c;font-family:Consolas,monospace;font-size:13px}}
pre{{background:#263238;color:#e8f0f2;padding:14px 16px;border-radius:8px;overflow-x:auto;font-size:13px}}
pre code{{background:none;color:inherit;padding:0}} li{{margin:4px 0}}
</style></head>
<body><header><h1>{H.escape(title)}</h1><p>{H.escape(sub)}</p></header>
{body}
</body></html>"""

def main():
    args = [a for a in sys.argv[1:] if not a.startswith('-')]
    md = pathlib.Path(args[0]); title = args[1]; sub = args[2]
    out = pathlib.Path(sys.argv[sys.argv.index('-o')+1]) if '-o' in sys.argv else \
          md.with_suffix('.html')
    out.write_text(md_to_html(md.read_text(encoding='utf-8'), title, sub), encoding='utf-8')
    print('written:', out)

if __name__ == '__main__':
    main()
