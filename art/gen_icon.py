#!/usr/bin/env python3
"""Void Wars logo: one geometry, written as the SVG master and the Android icon layers, once per theme.

A delta starfighter climbing out of deep space, the VOID family emblem (two galaxy arms spiralling out of a
white-hot spark) burning in its hull as the reactor core, an engine flame behind it and two bolts of fire
ahead. Themes come from art/themes.json (the VOID desktop themes, shared with VOID Portal).

Run from the project root:  python3 art/gen_icon.py
"""
import json
import math
import os
import re

CX, CY = 54.0, 58.0  # the emblem (reactor core), in the widest part of the hull
SHIP = ("M54,22 L59,36 L61,47 L83,68 L84,75 L65,72 L61,80 L47,80 L43,72 L24,75 L25,68 L47,47 L49,36 Z")
# The lit half of the hull (left of the spine), for a faceted look.
FACET = "M54,22 L49,36 L47,47 L25,68 L24,75 L43,72 L47,80 L54,80 Z"
CANOPY = "M54,29 L57,38 L54,44 L51,38 Z"
STRIPES = ["M66,55 L77,66 L75,68 L64,57 Z", "M42,55 L31,66 L33,68 L44,57 Z"]
FLAME = "M48,80 C48,88 51,95 54,99 C57,95 60,88 60,80 Z"
FLAME_CORE = "M51,80 C51,86 52.5,90 54,93 C55.5,90 57,86 57,80 Z"
BOLTS = [(27, 30, 46), (81, 30, 46)]  # x, top, bottom
THETA_MAX = 1.7 * math.pi
ARM_K = 0.4  # arm size relative to VOID Portal's
R0, R_GROW = 2.5 * ARM_K, 36.0 * ARM_K
S = 4.6 / 9.5  # spark scale


def arm_points(phase, n=64, length=1.0, width_scale=1.0, t0=0.0):
    """Outline of one tapered arm (or the part t0..length of it): thin at the centre, widest early, a fine tail."""
    pts = []
    for i in range(n + 1):
        t = t0 + i / n * (length - t0)
        th = t * THETA_MAX
        r = R0 + R_GROW * t ** 1.25
        pts.append((CX + r * math.cos(th + phase), CY + r * math.sin(th + phase), t))
    left, right = [], []
    for i, (x, y, t) in enumerate(pts):
        x2, y2, _ = pts[min(i + 1, n)] if i < n else pts[i]
        x1, y1, _ = pts[max(i - 1, 0)]
        dx, dy = x2 - x1, y2 - y1
        d = math.hypot(dx, dy) or 1
        nx, ny = -dy / d, dx / d
        w = (0.3 + 2.5 * math.sin(math.pi * min(t, 0.999) ** 0.55)) * width_scale * 0.6 / 2
        left.append((x + nx * w, y + ny * w))
        right.append((x - nx * w, y - ny * w))
    poly = left + right[::-1]
    return "M" + " L".join(f"{x:.2f},{y:.2f}" for x, y in poly) + " Z", pts[-1][:2]


def circle(cx, cy, r):
    return f"M{cx - r:g},{cy:g} a{r:g},{r:g} 0 1,0 {2 * r:g},0 a{r:g},{r:g} 0 1,0 {-2 * r:g},0"


def ellipse(cx, cy, rx, ry):
    return f"M{cx - rx:g},{cy:g} a{rx:g},{ry:g} 0 1,0 {2 * rx:g},0 a{rx:g},{ry:g} 0 1,0 {-2 * rx:g},0"


def capsule(x, y0, y1, r):
    return f"M{x - r:g},{y0:g} a{r:g},{r:g} 0 0,1 {2 * r:g},0 L{x + r:g},{y1:g} a{r:g},{r:g} 0 0,1 {-2 * r:g},0 Z"


def sparkle(cx, cy, r):
    """A four-pointed glint."""
    k = r * 0.28
    return (f"M{cx:.2f},{cy - r:.2f} Q{cx + k:.2f},{cy - k:.2f} {cx + r:.2f},{cy:.2f} Q{cx + k:.2f},{cy + k:.2f} {cx:.2f},{cy + r:.2f} "
            f"Q{cx - k:.2f},{cy + k:.2f} {cx - r:.2f},{cy:.2f} Q{cx - k:.2f},{cy - k:.2f} {cx:.2f},{cy - r:.2f} Z")


SPARK = sparkle(CX, CY, 9.5 * S)
STARS = [(20, 24, 0.9, 0.8), (88, 22, 0.7, 0.6), (91, 70, 1.0, 0.7), (15, 80, 0.7, 0.5), (74, 96, 0.6, 0.6), (64, 12, 0.5, 0.5), (34, 95, 0.5, 0.45)]
PHASES = [-math.pi / 2, math.pi / 2]
SEGMENTS = 14
HULL_GRAD = (54, 22, 54, 80)  # nose to tail


def vd_lin(x1, y1, x2, y2, stops):
    items = "".join(f'<item android:offset="{o}" android:color="{c}"/>' for o, c in stops)
    return f'<gradient android:type="linear" android:startX="{x1:.2f}" android:startY="{y1:.2f}" android:endX="{x2:.2f}" android:endY="{y2:.2f}">{items}</gradient>'


def vd_rad(cx, cy, r, stops):
    items = "".join(f'<item android:offset="{o}" android:color="{c}"/>' for o, c in stops)
    return f'<gradient android:type="radial" android:centerX="{cx}" android:centerY="{cy}" android:gradientRadius="{r}">{items}</gradient>'


def argb(hex_rgb, a):
    return f"#{int(a * 255):02X}{hex_rgb[1:]}"


def mix(a, b, f):
    pa = [int(a[i:i + 2], 16) for i in (1, 3, 5)]
    pb = [int(b[i:i + 2], 16) for i in (1, 3, 5)]
    return "#" + "".join(f"{round(x + (y - x) * f):02X}" for x, y in zip(pa, pb))


def palette(theme):
    """Icon colours from a theme; the same background mapping as VOID Portal's, so the icons sit together."""
    c = theme["colors"]
    light = theme["mode"] == "light"
    a, a2 = c["accent"].upper(), c["accent2"].upper()
    return {
        # Gunmetal hull tinted by the accent: a lit nose running to a dark tail.
        "hull": [(0, mix("#D8DCF0", a2, 0.25)), (0.5, mix("#3A3F5C", a, 0.35)), (1, mix("#14162A", a, 0.25))],
        "facet": mix("#FFFFFF", a2, 0.3),
        "rim": mix(a2, "#FFFFFF", 0.5),
        "stripe": a,
        "canopy": mix(a2, "#FFFFFF", 0.35),
        "arms": [a2, mix(a, "#FFFFFF", 0.25)],
        "hot": mix("#FFFFFF", a2, 0.05),
        "bg": (mix(c["panelAlt"], a, 0.08), c["bg"].upper()) if light else (mix(c["bg"], a, 0.12), mix(c["bg"], "#000000", 0.35)),
        "nebula": (mix(c["bg"], a, 0.35), 0.45) if light else (mix(c["bg"], a, 0.45), 0.85),
        "star": (a, 0.45) if light else ("#FFFFFF", 1.0),
        "core": "#070512",
        "glow": a2,
        "spark_end": mix("#FFFFFF", a2, 0.35),
        "flame": [(0, mix("#FFFFFF", a2, 0.2)), (0.4, a2), (1, mix(a, c["bg"].upper(), 0.2))],
        "bolt": mix(c["warning"].upper(), "#FFFFFF", 0.3),
    }


def arm_segments(phase, color, hot):
    """The arm in short overlapping pieces, coloured along its length: white-hot core, arm colour, fading tail."""
    out = []
    for k in range(SEGMENTS):
        t0, t1 = k / SEGMENTS, min(1.0, (k + 1.35) / SEGMENTS)
        tm = (t0 + t1) / 2
        c = mix(hot, color, min(1.0, tm / 0.22))
        alpha = 1.0 if tm < 0.55 else 1.0 - 0.6 * (tm - 0.55) / 0.45
        d, _ = arm_points(phase, n=10, length=t1, t0=t0)
        out.append((d, c, alpha))
    return out


HEAD = ('<?xml version="1.0" encoding="utf-8"?>\n<vector xmlns:android="http://schemas.android.com/apk/res/android" '
        'xmlns:aapt="http://schemas.android.com/aapt" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">\n')


def background_vd(P):
    out = HEAD
    top, bottom = P["bg"]
    neb, neb_a = P["nebula"]
    star, star_a = P["star"]
    out += f'  <path android:pathData="M0,0h108v108h-108z"><aapt:attr name="android:fillColor">{vd_lin(54, 0, 54, 108, [(0, argb(top, 1)), (1, argb(bottom, 1))])}</aapt:attr></path>\n'
    out += f'  <path android:pathData="M0,0h108v108h-108z"><aapt:attr name="android:fillColor">{vd_rad(54, 54, 50, [(0, argb(neb, neb_a)), (1, argb(neb, 0))])}</aapt:attr></path>\n'
    for cx, cy, r, a in STARS:
        out += f'  <path android:pathData="{circle(cx, cy, r)}" android:fillColor="{argb(star, 1)}" android:fillAlpha="{a * star_a:.2f}"/>\n'
    return out + "</vector>\n"


def foreground_vd(P):
    out = HEAD
    flame = [(o, argb(c, 0.95 - 0.6 * o)) for o, c in P["flame"]]
    out += f'  <path android:pathData="{FLAME}"><aapt:attr name="android:fillColor">{vd_lin(54, 80, 54, 99, flame)}</aapt:attr></path>\n'
    out += f'  <path android:pathData="{FLAME_CORE}" android:fillColor="#E6FFFFFF"/>\n'
    for x, y0, y1 in BOLTS:
        out += f'  <path android:pathData="{capsule(x, y0, y1, 3.2)}" android:fillColor="{argb(P["bolt"], 0.25)}"/>\n'
        out += f'  <path android:pathData="{capsule(x, y0, y1, 1.3)}" android:fillColor="{argb(P["bolt"], 1)}"/>\n'
    out += f'  <path android:pathData="{SHIP}" android:strokeWidth="5" android:strokeAlpha="0.2" android:strokeLineJoin="round" android:strokeColor="{argb(P["glow"], 1)}"/>\n'
    hull = [(o, argb(c, 1)) for o, c in P["hull"]]
    out += f'  <path android:pathData="{SHIP}"><aapt:attr name="android:fillColor">{vd_lin(*HULL_GRAD, hull)}</aapt:attr></path>\n'
    out += f'  <path android:pathData="{FACET}" android:fillColor="{argb(P["facet"], 0.14)}"/>\n'
    for s in STRIPES:
        out += f'  <path android:pathData="{s}" android:fillColor="{argb(P["stripe"], 0.9)}"/>\n'
    out += f'  <path android:pathData="{circle(CX, CY, 13)}"><aapt:attr name="android:fillColor">{vd_rad(CX, CY, 13, [(0, argb(P["core"], 0.95)), (0.7, argb(P["core"], 0.6)), (1, argb(P["core"], 0))])}</aapt:attr></path>\n'
    for phase, color in zip(PHASES, P["arms"]):
        for scale, alpha in [(4.0, 0.1), (2.3, 0.28)]:  # glow
            d, _ = arm_points(phase, width_scale=scale)
            out += f'  <path android:pathData="{d}" android:fillColor="{argb(color, alpha)}"/>\n'
        for d, c, a in arm_segments(phase, color, P["hot"]):
            out += f'  <path android:pathData="{d}" android:fillColor="{argb(c, a)}"/>\n'
    out += f'  <path android:pathData="{circle(CX, CY, 7)}"><aapt:attr name="android:fillColor">{vd_rad(CX, CY, 7, [(0, argb(P["glow"], 0.6)), (1, argb(P["glow"], 0))])}</aapt:attr></path>\n'
    out += f'  <path android:pathData="{SPARK}"><aapt:attr name="android:fillColor">{vd_rad(CX, CY, 9.5 * S, [(0, "#FFFFFFFF"), (1, argb(P["spark_end"], 1))])}</aapt:attr></path>\n'
    out += f'  <path android:pathData="{CANOPY}" android:fillColor="{argb(P["canopy"], 0.9)}"/>\n'
    out += f'  <path android:pathData="{SHIP}" android:strokeWidth="1.1" android:strokeAlpha="0.6" android:strokeLineJoin="round" android:strokeColor="{argb(P["rim"], 1)}"/>\n'
    return out + "</vector>\n"


def mono_vd(color="#FF000000"):
    """The ship as a solid shape with the core cut out and the emblem inside it, so it reads at any size."""
    cut = "".join(arm_points(p, width_scale=1.4)[0] for p in PHASES) + SPARK
    return (HEAD + f'  <path android:pathData="{SHIP}{circle(CX, CY, 11.5)}{CANOPY}" android:fillColor="{color}" android:fillType="evenOdd"/>\n'
            + f'  <path android:pathData="{cut}{FLAME_CORE}" android:fillColor="{color}"/>\n' + "</vector>\n")


def svg(P, size=432):
    top, bottom = P["bg"]
    neb, neb_a = P["nebula"]
    star, star_a = P["star"]
    stops = lambda st, op=1: "".join(f'<stop offset="{o}" stop-color="{c}" stop-opacity="{op}"/>' for o, c in st)
    flame_stops = "".join(f'<stop offset="{o}" stop-color="{c}" stop-opacity="{0.95 - 0.6 * o:.2f}"/>' for o, c in P["flame"])
    o = [f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108" width="{size}" height="{size}">', "<defs>",
         f'<linearGradient id="bg" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="{top}"/><stop offset="1" stop-color="{bottom}"/></linearGradient>',
         f'<radialGradient id="neb" cx="54" cy="54" r="50" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="{neb}" stop-opacity="{neb_a}"/><stop offset="1" stop-color="{neb}" stop-opacity="0"/></radialGradient>',
         f'<linearGradient id="hull" x1="{HULL_GRAD[0]}" y1="{HULL_GRAD[1]}" x2="{HULL_GRAD[2]}" y2="{HULL_GRAD[3]}" gradientUnits="userSpaceOnUse">{stops(P["hull"])}</linearGradient>',
         f'<linearGradient id="flame" x1="54" y1="80" x2="54" y2="99" gradientUnits="userSpaceOnUse">{flame_stops}</linearGradient>',
         f'<radialGradient id="core" cx="{CX}" cy="{CY}" r="13" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="{P["core"]}" stop-opacity=".95"/>'
         f'<stop offset=".7" stop-color="{P["core"]}" stop-opacity=".6"/><stop offset="1" stop-color="{P["core"]}" stop-opacity="0"/></radialGradient>',
         f'<radialGradient id="glow" cx="{CX}" cy="{CY}" r="7" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="{P["glow"]}" stop-opacity=".6"/><stop offset="1" stop-color="{P["glow"]}" stop-opacity="0"/></radialGradient>',
         f'<radialGradient id="spark" cx="{CX}" cy="{CY}" r="{9.5 * S:.2f}" gradientUnits="userSpaceOnUse"><stop offset="0" stop-color="#fff"/><stop offset="1" stop-color="{P["spark_end"]}"/></radialGradient>',
         "</defs>"]
    o += ['<rect width="108" height="108" fill="url(#bg)"/>', '<rect width="108" height="108" fill="url(#neb)"/>']
    o += [f'<circle cx="{x}" cy="{y}" r="{r}" fill="{star}" opacity="{a * star_a:.2f}"/>' for x, y, r, a in STARS]
    o += [f'<path d="{FLAME}" fill="url(#flame)"/>', f'<path d="{FLAME_CORE}" fill="#fff" fill-opacity=".9"/>']
    for x, y0, y1 in BOLTS:
        o += [f'<path d="{capsule(x, y0, y1, 3.2)}" fill="{P["bolt"]}" fill-opacity=".25"/>', f'<path d="{capsule(x, y0, y1, 1.3)}" fill="{P["bolt"]}"/>']
    o += [f'<path d="{SHIP}" fill="none" stroke="{P["glow"]}" stroke-opacity=".2" stroke-width="5" stroke-linejoin="round"/>',
          f'<path d="{SHIP}" fill="url(#hull)"/>', f'<path d="{FACET}" fill="{P["facet"]}" fill-opacity=".14"/>']
    o += [f'<path d="{s}" fill="{P["stripe"]}" fill-opacity=".9"/>' for s in STRIPES]
    o.append(f'<circle cx="{CX}" cy="{CY}" r="13" fill="url(#core)"/>')
    for phase, color in zip(PHASES, P["arms"]):
        for scale, alpha in [(4.0, 0.1), (2.3, 0.28)]:
            o.append(f'<path d="{arm_points(phase, width_scale=scale)[0]}" fill="{color}" fill-opacity="{alpha}"/>')
        o += [f'<path d="{d}" fill="{c}" fill-opacity="{a:.2f}"/>' for d, c, a in arm_segments(phase, color, P["hot"])]
    o += [f'<circle cx="{CX}" cy="{CY}" r="7" fill="url(#glow)"/>', f'<path d="{SPARK}" fill="url(#spark)"/>',
          f'<path d="{CANOPY}" fill="{P["canopy"]}" fill-opacity=".9"/>',
          f'<path d="{SHIP}" fill="none" stroke="{P["rim"]}" stroke-opacity=".6" stroke-width="1.1" stroke-linejoin="round"/>', "</svg>"]
    return "\n".join(o) + "\n"


def alias_block(themes):
    """One launcher entry per theme; the app enables the one matching its theme (see Themes.kt)."""
    out = ["        <!-- ICON ALIASES (generated by art/gen_icon.py) -->"]
    for i, t in enumerate(themes):
        key = t["id"].replace("-", "_")
        out += [
            "        <activity-alias",
            f'            android:name=".Icon_{key}"',
            f'            android:enabled="{"true" if i == 0 else "false"}"',
            '            android:exported="true"',
            f'            android:icon="@mipmap/ic_launcher_{key}"',
            f'            android:roundIcon="@mipmap/ic_launcher_{key}"',
            '            android:targetActivity=".MainActivity">',
            "            <intent-filter>",
            '                <action android:name="android.intent.action.MAIN" />',
            '                <category android:name="android.intent.category.LAUNCHER" />',
            "            </intent-filter>",
            "        </activity-alias>",
        ]
    out.append("        <!-- END ICON ALIASES -->")
    return "\n".join(out)


def adaptive(key):
    return ('<?xml version="1.0" encoding="utf-8"?>\n<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n'
            f'    <background android:drawable="@drawable/ic_launcher_background{key}"/>\n'
            f'    <foreground android:drawable="@drawable/ic_launcher_foreground{key}"/>\n'
            '    <monochrome android:drawable="@drawable/ic_launcher_monochrome"/>\n</adaptive-icon>\n')


if __name__ == "__main__":
    themes = json.load(open("art/themes.json"))
    res = "app/src/main/res/"
    os.makedirs("art/themes", exist_ok=True)
    os.makedirs(res + "drawable", exist_ok=True)
    os.makedirs(res + "mipmap-anydpi-v26", exist_ok=True)
    for i, t in enumerate(themes):
        P = palette(t)
        key = t["id"].replace("-", "_")
        open(f"art/themes/{key}.svg", "w").write(svg(P))
        open(f"{res}drawable/ic_launcher_background_{key}.xml", "w").write(background_vd(P))
        open(f"{res}drawable/ic_launcher_foreground_{key}.xml", "w").write(foreground_vd(P))
        open(f"{res}mipmap-anydpi-v26/ic_launcher_{key}.xml", "w").write(adaptive(f"_{key}"))
        if i == 0:  # the first (default) theme is also the app's own icon
            open("art/wars-icon.svg", "w").write(svg(P))
            open(res + "drawable/ic_launcher_background.xml", "w").write(background_vd(P))
            open(res + "drawable/ic_launcher_foreground.xml", "w").write(foreground_vd(P))
            open(res + "mipmap-anydpi-v26/ic_launcher.xml", "w").write(adaptive(""))
            open(res + "mipmap-anydpi-v26/ic_launcher_round.xml", "w").write(adaptive(""))
    open(res + "drawable/ic_launcher_monochrome.xml", "w").write(mono_vd())
    # Status bar icon: the same shape, white, enlarged to fill the 24dp square.
    note = mono_vd("#FFFFFFFF").replace('android:width="108dp" android:height="108dp"', 'android:width="24dp" android:height="24dp"')
    note = note.replace("  <path", '  <group android:pivotX="54" android:pivotY="56" android:scaleX="1.5" android:scaleY="1.5">\n  <path', 1)
    open(res + "drawable/ic_notification.xml", "w").write(note.replace("</vector>", "  </group>\n</vector>"))
    manifest = "app/src/main/AndroidManifest.xml"
    if os.path.exists(manifest):
        m = open(manifest).read()
        m = re.sub(r"        <!-- ICON ALIASES.*?<!-- END ICON ALIASES -->", lambda _: alias_block(themes), m, flags=re.S)
        open(manifest, "w").write(m)
    print(f"logo written for {len(themes)} themes")
