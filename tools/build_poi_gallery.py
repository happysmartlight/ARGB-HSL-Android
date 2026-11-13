"""Đóng gói thư viện mẫu POI của desktop cho app Android.

- Ảnh nguồn: thu nhỏ (cạnh dài ≤ MAX_SIDE, cạnh ngắn vẫn ≥ MIN_SHORT nếu ảnh gốc đủ lớn) → WebP.
- Thumbnail: vẽ bằng ĐÚNG code desktop (`services.poi_gallery.render_thumb`) → WebP.
- gallery.json: nhóm + mục (file, thumb, title, group, shape, featured).
Chạy (từ gốc repo Android, cần repo desktop ở D:/Github/Tool-POI-HSL):
  D:/Github/Tool-POI-HSL/.venv/Scripts/python.exe tools/build_poi_gallery.py app/src/main/assets/poi_gallery --quality 90
"""
import io, json, os, sys

sys.path.insert(0, r"D:\Github\Tool-POI-HSL")
from PIL import Image  # noqa: E402
from services import poi_gallery  # noqa: E402

OUT = sys.argv[1]
LOSSLESS = "--lossless" in sys.argv
QUALITY = int(sys.argv[sys.argv.index("--quality") + 1]) if "--quality" in sys.argv else 90
MAX_SIDE = 360
MIN_SHORT = 192
THUMB = 192
SRC_DIR = r"D:\Github\Tool-POI-HSL\assets\poi_gallery"

g = poi_gallery.load_gallery(SRC_DIR)
os.makedirs(os.path.join(OUT, "src"), exist_ok=True)
os.makedirs(os.path.join(OUT, "thumb"), exist_ok=True)
items = []
total_src = total_thumb = 0
for it in g.items:
    with Image.open(it.path) as im:
        im.load()
        img = im.copy()
    w, h = img.size
    s = min(1.0, MAX_SIDE / max(w, h))
    s = max(s, min(1.0, MIN_SHORT / min(w, h)))
    if s < 1.0:
        img = img.resize((max(1, round(w * s)), max(1, round(h * s))), Image.LANCZOS)
    if img.mode not in ("RGB", "RGBA"):
        img = img.convert("RGBA" if "A" in img.getbands() or img.mode == "P" else "RGB")
    stem = os.path.splitext(it.file)[0]
    src_name = f"src/{stem}.webp"
    buf = io.BytesIO()
    if LOSSLESS:
        img.save(buf, "WEBP", lossless=True, method=6)
    else:
        img.save(buf, "WEBP", quality=QUALITY, method=6)
    open(os.path.join(OUT, src_name), "wb").write(buf.getvalue())
    total_src += buf.tell()

    thumb = poi_gallery.render_thumb(it, THUMB)
    tb = io.BytesIO()
    thumb.save(tb, "WEBP", quality=82, method=6)
    thumb_name = f"thumb/{stem}.webp"
    open(os.path.join(OUT, thumb_name), "wb").write(tb.getvalue())
    total_thumb += tb.tell()

    items.append({"file": src_name, "thumb": thumb_name, "title": it.title, "group": it.group,
                  "shape": it.shape, **({"featured": True} if it.featured else {})})

doc = {"version": 1, "groups": [{"id": gid, "name": name} for gid, name in g.groups], "items": items}
with open(os.path.join(OUT, "gallery.json"), "w", encoding="utf-8") as f:
    json.dump(doc, f, ensure_ascii=False, indent=1)
print(f"{len(items)} mẫu · nguồn {total_src / 1e6:.2f} MB · thumbnail {total_thumb / 1e6:.2f} MB")
