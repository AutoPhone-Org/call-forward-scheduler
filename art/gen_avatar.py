"""生成组织头像 PNG（蓝色波浪徽章，与 art/logo.svg 视觉一致）"""
import math
import os

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))


def make_png(size: int = 1024) -> Image.Image:
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    # 蓝色渐变背景（圆角方形）
    c1 = (37, 99, 235)   # #2563EB
    c2 = (14, 165, 233)  # #0EA5E9
    for y in range(size):
        t = y / size
        r = int(c1[0] + (c2[0] - c1[0]) * t)
        g = int(c1[1] + (c2[1] - c1[1]) * t)
        b = int(c1[2] + (c2[2] - c1[2]) * t)
        ImageDraw.Draw(img).line([(0, y), (size, y)], fill=(r, g, b, 255))

    # 圆角遮罩
    radius = int(size * 0.227)
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).rounded_rectangle(
        [0, 0, size - 1, size - 1], radius=radius, fill=255)
    img.putalpha(mask)

    # 三条白色波浪
    def wave(y0, amp, width, alpha):
        layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        ld = ImageDraw.Draw(layer)
        pts = []
        for x in range(size):
            t = x / size * 2 * math.pi
            pts.append((x, y0 + amp * math.sin(t)))
        ld.line(pts, fill=(255, 255, 255, alpha), width=width)
        img.alpha_composite(layer)

    w = int(size * 0.066)
    wave(int(size * 0.40), int(size * 0.055), w, 235)
    wave(int(size * 0.52), int(size * 0.055), w, 255)
    wave(int(size * 0.64), int(size * 0.055), w, 235)
    return img


def main():
    img = make_png(1024)
    out = os.path.join(HERE, "org-avatar.png")
    img.save(out)
    print("生成:", out, img.size)


if __name__ == "__main__":
    main()
