# build_singlefile.py — 把详解 html 引用的本地图片/SVG 全部 base64 内嵌
# 用法: python build_singlefile.py <html文件> [-o 输出文件]
#  - 引用 *.jpg / *.png / *.gif 的 <img src="..."> → data URI
#  - 同目录 *.svg（即使未被引用，若加 --svg-all 则全部收编到末尾画廊；默认只处理被引用的）
#  - SVG 内 xlink:href="xxx.jpg" 先替换为 base64，再整体作为 data:image/svg+xml 内嵌
# 依赖: 仅标准库；HTML 读写均为 UTF-8
import base64, mimetypes, re, sys, pathlib

def b64(p): return base64.b64encode(pathlib.Path(p).read_bytes()).decode()

def main():
    args = [a for a in sys.argv[1:] if not a.startswith('-')]
    if not args:
        sys.exit("usage: python build_singlefile.py <html> [-o out.html]")
    src = pathlib.Path(args[0])
    out = pathlib.Path(sys.argv[sys.argv.index('-o')+1]) if '-o' in sys.argv else \
          src.with_name(src.stem + '-singlefile.html')
    html = src.read_text(encoding='utf-8')
    base = src.parent

    # 1) 普通图片引用内嵌
    def embed_img(m):
        fn = m.group(1)
        fp = base / fn
        if not fp.exists(): return m.group(0)
        mime = mimetypes.guess_type(fn)[0] or 'application/octet-stream'
        return f'src="data:{mime};base64,{b64(fp)}"'
    html = re.sub(r'src="([^"]+\.(?:jpg|jpeg|png|gif|bmp|webp))"', embed_img, html,
                  flags=re.I)

    # 2) SVG 叠图：先内嵌其底图引用，再以 data URI 替换/收编
    jpg_cache = {}
    def embed_jpg(m):
        fn = m.group(1)
        fp = base / fn
        if not fp.exists(): return m.group(0)
        if fn not in jpg_cache: jpg_cache[fn] = b64(fp)
        return f'xlink:href="data:image/jpeg;base64,{jpg_cache[fn]}"'

    def svg_data_uri(fp):
        txt = fp.read_text(encoding='utf-8')
        txt = re.sub(r'xlink:href="([^"]+\.(?:jpg|jpeg|png))"', embed_jpg, txt,
                     flags=re.I)
        return 'data:image/svg+xml;base64,' + base64.b64encode(txt.encode('utf-8')).decode()

    # 2a) 已被引用的 svg
    def embed_svg(m):
        fp = base / m.group(1)
        return f'src="{svg_data_uri(fp)}"' if fp.exists() else m.group(0)
    html = re.sub(r'src="([^"]+\.svg)"', embed_svg, html, flags=re.I)

    # 2b) 未被引用的 svg 收编进末尾（body 结束前）
    unref = [p for p in base.glob('*.svg')
             if p.name not in html and not html.startswith('data:')]
    if unref:
        figs = ''.join(
            f'\n<figure><img src="{svg_data_uri(p)}" alt="{p.name}">'
            f'<figcaption>SVG 叠图（内嵌）：{p.name}</figcaption></figure>\n'
            for p in sorted(unref))
        anchor = '</body>'
        if anchor in html:
            html = html.replace(anchor, figs + anchor, 1)
        else:
            html += figs

    out.write_text(html, encoding='utf-8')
    left = re.findall(r'src="(?!data:)[^"]+"', html)
    print(f'written: {out} ({out.stat().st_size//1024} KB)')
    print(f'external src left: {len(left)}', left[:5])

if __name__ == '__main__':
    main()
