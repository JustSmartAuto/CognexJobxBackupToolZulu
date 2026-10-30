import openpyxl, sys, io
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
wb = openpyxl.load_workbook(sys.argv[1], data_only=True)
ws = wb['单元格']
start, end, maxc = int(sys.argv[2]), int(sys.argv[3]), int(sys.argv[4]) if len(sys.argv)>4 else 6
for r in range(start, end+1):
    vals = [ws.cell(r, c) for c in range(1, maxc+1)]
    if all(v.value is None for v in vals):
        continue
    parts = []
    for v in vals:
        if v.value is None:
            parts.append('')
        else:
            s = str(v.value).replace('\n', ' ')
            if len(s) > 150:
                s = s[:150] + '...'
            parts.append(s)
    print(r, ' | '.join(parts))
