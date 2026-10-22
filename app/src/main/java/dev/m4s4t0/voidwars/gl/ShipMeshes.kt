package dev.m4s4t0.voidwars.gl

import dev.m4s4t0.voidwars.model.Boss
import dev.m4s4t0.voidwars.model.Engine
import dev.m4s4t0.voidwars.model.Hull
import dev.m4s4t0.voidwars.model.Wings
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Material channel values (see the lit shader). */
object Mat {
    const val PAINT = 0
    const val TRIM = 1
    const val GLOW = 2
    const val DARK = 3
    const val GLASS = 4
}

/**
 * Builds flat-shaded triangle soups (pos3, normal3, material) out of a few primitives: ridge-roofed hulls, faceted
 * slabs, tubes, rings, spikes and lumpy spheres. Everything is placed through [at] (offset, turn about z, scale) and
 * can be mirrored across y = 0.
 */
class MeshBuilder {
    private var d = FloatArray(4096)
    private var n = 0
    var mirror = false
    private var ox = 0f; private var oy = 0f; private var oz = 0f
    private var ca = 1f; private var sa = 0f; private var sc = 1f

    fun at(x: Float = 0f, y: Float = 0f, z: Float = 0f, ang: Float = 0f, s: Float = 1f): MeshBuilder {
        ox = x; oy = y; oz = z; ca = cos(ang); sa = sin(ang); sc = s
        return this
    }

    private fun put(x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float, m: Int) {
        if (n + 7 > d.size) d = d.copyOf(d.size * 2)
        d[n] = x; d[n + 1] = y; d[n + 2] = z; d[n + 3] = nx; d[n + 4] = ny; d[n + 5] = nz; d[n + 6] = m.toFloat()
        n += 7
    }

    private val tmp = FloatArray(9)

    /** A triangle with normal (nx, ny, nz), in local coordinates. */
    fun tri(
        ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float, cx: Float, cy: Float, cz: Float,
        nx: Float, ny: Float, nz: Float, m: Int,
    ) {
        val p = tmp
        p[0] = ax; p[1] = ay; p[2] = az; p[3] = bx; p[4] = by; p[5] = bz; p[6] = cx; p[7] = cy; p[8] = cz
        val rnx = nx * ca - ny * sa
        val rny = nx * sa + ny * ca
        for (k in 0 until 3) {
            val x = p[k * 3]; val y = p[k * 3 + 1]
            p[k * 3] = ox + (x * ca - y * sa) * sc
            p[k * 3 + 1] = oy + (x * sa + y * ca) * sc
            p[k * 3 + 2] = oz + p[k * 3 + 2] * sc
        }
        for (k in 0 until 3) put(p[k * 3], p[k * 3 + 1], p[k * 3 + 2], rnx, rny, nz, m)
        if (mirror) for (k in 0 until 3) put(p[k * 3], -p[k * 3 + 1], p[k * 3 + 2], rnx, -rny, nz, m)
    }

    /** A triangle whose normal comes from its corners, turned to face along the hint (hx, hy, hz). */
    fun face(
        ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float, cx: Float, cy: Float, cz: Float,
        hx: Float, hy: Float, hz: Float, m: Int,
    ) {
        val ux = bx - ax; val uy = by - ay; val uz = bz - az
        val vx = cx - ax; val vy = cy - ay; val vz = cz - az
        var nx = uy * vz - uz * vy
        var ny = uz * vx - ux * vz
        var nz = ux * vy - uy * vx
        val l = sqrt(nx * nx + ny * ny + nz * nz)
        if (l < 1e-7f) return
        nx /= l; ny /= l; nz /= l
        if (nx * hx + ny * hy + nz * hz < 0f) { nx = -nx; ny = -ny; nz = -nz }
        tri(ax, ay, az, bx, by, bz, cx, cy, cz, nx, ny, nz, m)
    }

    fun quad(
        ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float,
        cx: Float, cy: Float, cz: Float, dx: Float, dy: Float, dz: Float,
        hx: Float, hy: Float, hz: Float, m: Int,
    ) {
        face(ax, ay, az, bx, by, bz, cx, cy, cz, hx, hy, hz, m)
        face(ax, ay, az, cx, cy, cz, dx, dy, dz, hx, hy, hz, m)
    }

    /**
     * A body with a ridge along y = 0, from a half outline of (x, y, ridge height) triples running nose to tail:
     * sloped roof panels down to the rim at [zTop], walls down to [zBot]. Both sides are built here.
     */
    fun hull(half: FloatArray, zTop: Float, zBot: Float, m: Int) {
        val was = mirror
        mirror = false
        val k = half.size / 3
        for (i in 0 until k - 1) {
            val x0 = half[i * 3]; val y0 = half[i * 3 + 1]; val r0 = half[i * 3 + 2]
            val x1 = half[i * 3 + 3]; val y1 = half[i * 3 + 4]; val r1 = half[i * 3 + 5]
            for (s in intArrayOf(1, -1)) {
                val a = y0 * s; val b = y1 * s
                quad(x0, a, zTop, x1, b, zTop, x1, 0f, r1, x0, 0f, r0, 0f, 0f, 1f, m)
                val ex = x1 - x0; val ey = b - a
                quad(x0, a, zTop, x1, b, zTop, x1, b, zBot, x0, a, zBot, s * ey, -s * ex, 0f, m)
            }
        }
        // Close a blunt nose or tail.
        for (end in intArrayOf(0, k - 1)) {
            val x = half[end * 3]; val y = half[end * 3 + 1]; val r = half[end * 3 + 2]
            if (y <= 1e-4f) continue
            val hx = if (end == 0) 1f else -1f
            face(x, y, zTop, x, -y, zTop, x, 0f, r, hx, 0f, 0f, m)
            quad(x, y, zTop, x, -y, zTop, x, -y, zBot, x, y, zBot, hx, 0f, 0f, m)
        }
        mirror = was
    }

    /** A flat polygon (x, y pairs) with a raised centre at [zPeak], rim at [zTop] and walls down to [zBot]. */
    fun slab(poly: FloatArray, zTop: Float, zPeak: Float, zBot: Float, m: Int, wallM: Int = m) {
        val k = poly.size / 2
        var area = 0f
        var cx = 0f; var cy = 0f
        for (i in 0 until k) {
            val j = (i + 1) % k
            area += poly[i * 2] * poly[j * 2 + 1] - poly[j * 2] * poly[i * 2 + 1]
            cx += poly[i * 2]; cy += poly[i * 2 + 1]
        }
        cx /= k; cy /= k
        val ccw = area > 0f
        for (i in 0 until k) {
            val j = (i + 1) % k
            val ax = poly[i * 2]; val ay = poly[i * 2 + 1]
            val bx = poly[j * 2]; val by = poly[j * 2 + 1]
            face(ax, ay, zTop, bx, by, zTop, cx, cy, zPeak, 0f, 0f, 1f, m)
            val ex = bx - ax; val ey = by - ay
            val s = if (ccw) 1f else -1f
            if (zBot < zTop) quad(ax, ay, zTop, bx, by, zTop, bx, by, zBot, ax, ay, zBot, s * ey, -s * ex, 0f, wallM)
        }
    }

    fun box(x0: Float, x1: Float, y0: Float, y1: Float, z0: Float, z1: Float, m: Int) =
        slab(floatArrayOf(x0, y0, x1, y0, x1, y1, x0, y1), z1, z1, z0, m)

    /** A tapered tube along x at (y, z), from radius [r0] at [x0] to [r1] at [x1], capped at the back with [capM]. */
    fun tube(x0: Float, x1: Float, y: Float, z: Float, r0: Float, r1: Float, seg: Int, m: Int, capM: Int = m) {
        for (i in 0 until seg) {
            val a0 = 2 * PI.toFloat() * i / seg; val a1 = 2 * PI.toFloat() * (i + 1) / seg
            val c0 = cos(a0); val s0 = sin(a0); val c1 = cos(a1); val s1 = sin(a1)
            val am = (a0 + a1) / 2
            quad(
                x0, y + c0 * r0, z + s0 * r0, x1, y + c0 * r1, z + s0 * r1, x1, y + c1 * r1, z + s1 * r1, x0, y + c1 * r0, z + s1 * r0,
                (r0 - r1) / (x1 - x0 + 1e-4f), cos(am), sin(am), m,
            )
            face(x0, y, z, x0, y + c0 * r0, z + s0 * r0, x0, y + c1 * r0, z + s1 * r0, -1f, 0f, 0f, capM)
            face(x1, y, z, x1, y + c0 * r1, z + s0 * r1, x1, y + c1 * r1, z + s1 * r1, 1f, 0f, 0f, m)
        }
    }

    /** A flat ring about the origin. */
    fun ring(rIn: Float, rOut: Float, zTop: Float, zBot: Float, seg: Int, m: Int, wallM: Int = m) {
        for (i in 0 until seg) {
            val a0 = 2 * PI.toFloat() * i / seg; val a1 = 2 * PI.toFloat() * (i + 1) / seg
            val c0 = cos(a0); val s0 = sin(a0); val c1 = cos(a1); val s1 = sin(a1)
            val cm = cos((a0 + a1) / 2); val sm = sin((a0 + a1) / 2)
            quad(c0 * rIn, s0 * rIn, zTop, c0 * rOut, s0 * rOut, zTop, c1 * rOut, s1 * rOut, zTop, c1 * rIn, s1 * rIn, zTop, 0f, 0f, 1f, m)
            quad(c0 * rOut, s0 * rOut, zTop, c1 * rOut, s1 * rOut, zTop, c1 * rOut, s1 * rOut, zBot, c0 * rOut, s0 * rOut, zBot, cm, sm, 0f, wallM)
            quad(c0 * rIn, s0 * rIn, zTop, c1 * rIn, s1 * rIn, zTop, c1 * rIn, s1 * rIn, zBot, c0 * rIn, s0 * rIn, zBot, -cm, -sm, 0f, wallM)
        }
    }

    /** A four-sided spike from the origin pointing along +x. */
    fun spike(len: Float, w: Float, z: Float, m: Int) {
        val c = floatArrayOf(0f, w, z + w, 0f, -w, z + w, 0f, -w, z - w, 0f, w, z - w)
        for (i in 0 until 4) {
            val j = (i + 1) % 4
            val ay = c[i * 3 + 1]; val az = c[i * 3 + 2]; val by = c[j * 3 + 1]; val bz = c[j * 3 + 2]
            face(0f, ay, az, 0f, by, bz, len, 0f, z, 0.1f, ay + by, az + bz - 2 * z, m)
        }
    }

    /** A sphere of radius [r], once-subdivided icosahedron, made lumpy by [lump] with a seeded wobble. */
    fun blob(cx: Float, cy: Float, cz: Float, r: Float, lump: Float, seed: Int, m: Int, sub: Int = 1) {
        val (v, f) = Ico.make(sub)
        val fs = seed * 1.37f
        fun rad(x: Float, y: Float, z: Float) = r * (1f + lump * (
            sin(x * 3.1f + fs) * 0.5f + sin(y * 4.3f + fs * 2.1f) * 0.35f + sin(z * 5.7f + fs * 0.7f) * 0.3f + sin((x + y) * 7.9f + fs) * 0.15f
            ) / 1.3f)
        val p = FloatArray(v.size)
        for (i in 0 until v.size / 3) {
            val x = v[i * 3]; val y = v[i * 3 + 1]; val z = v[i * 3 + 2]
            val rr = rad(x, y, z)
            p[i * 3] = cx + x * rr; p[i * 3 + 1] = cy + y * rr; p[i * 3 + 2] = cz + z * rr
        }
        for (t in 0 until f.size / 3) {
            val a = f[t * 3] * 3; val b = f[t * 3 + 1] * 3; val c = f[t * 3 + 2] * 3
            val hx = (p[a] + p[b] + p[c]) / 3 - cx; val hy = (p[a + 1] + p[b + 1] + p[c + 1]) / 3 - cy; val hz = (p[a + 2] + p[b + 2] + p[c + 2]) / 3 - cz
            face(p[a], p[a + 1], p[a + 2], p[b], p[b + 1], p[b + 2], p[c], p[c + 1], p[c + 2], hx, hy, hz, m)
        }
    }

    fun build(): FloatArray = d.copyOf(n)
}

/** Unit icosahedron, subdivided [sub] times. */
object Ico {
    private val cache = HashMap<Int, Pair<FloatArray, IntArray>>()

    fun make(sub: Int): Pair<FloatArray, IntArray> = cache.getOrPut(sub) {
        val t = (1f + sqrt(5f)) / 2f
        val vs = arrayListOf(
            -1f, t, 0f, 1f, t, 0f, -1f, -t, 0f, 1f, -t, 0f, 0f, -1f, t, 0f, 1f, t,
            0f, -1f, -t, 0f, 1f, -t, t, 0f, -1f, t, 0f, 1f, -t, 0f, -1f, -t, 0f, 1f,
        )
        for (i in 0 until 12) {
            val l = sqrt(vs[i * 3] * vs[i * 3] + vs[i * 3 + 1] * vs[i * 3 + 1] + vs[i * 3 + 2] * vs[i * 3 + 2])
            vs[i * 3] /= l; vs[i * 3 + 1] /= l; vs[i * 3 + 2] /= l
        }
        var fs = intArrayOf(
            0, 11, 5, 0, 5, 1, 0, 1, 7, 0, 7, 10, 0, 10, 11, 1, 5, 9, 5, 11, 4, 11, 10, 2, 10, 7, 6, 7, 1, 8,
            3, 9, 4, 3, 4, 2, 3, 2, 6, 3, 6, 8, 3, 8, 9, 4, 9, 5, 2, 4, 11, 6, 2, 10, 8, 6, 7, 9, 8, 1,
        )
        repeat(sub) {
            val mids = HashMap<Long, Int>()
            fun mid(a: Int, b: Int): Int {
                val key = if (a < b) a.toLong() * 100000 + b else b.toLong() * 100000 + a
                return mids.getOrPut(key) {
                    var x = (vs[a * 3] + vs[b * 3]) / 2; var y = (vs[a * 3 + 1] + vs[b * 3 + 1]) / 2; var z = (vs[a * 3 + 2] + vs[b * 3 + 2]) / 2
                    val l = sqrt(x * x + y * y + z * z)
                    x /= l; y /= l; z /= l
                    vs.add(x); vs.add(y); vs.add(z)
                    vs.size / 3 - 1
                }
            }
            val out = IntArray(fs.size * 4)
            for (i in 0 until fs.size / 3) {
                val a = fs[i * 3]; val b = fs[i * 3 + 1]; val c = fs[i * 3 + 2]
                val ab = mid(a, b); val bc = mid(b, c); val ca = mid(c, a)
                intArrayOf(a, ab, ca, b, bc, ab, c, ca, bc, ab, bc, ca).copyInto(out, i * 12)
            }
            fs = out
        }
        vs.toFloatArray() to fs
    }
}

/**
 * The shapes of everything that flies, as vertex data. The player's ship points along +x and is about two units long;
 * enemies are built to a radius of about one and scaled by their size.
 */
object ShipMeshes {
    private fun mesh(block: MeshBuilder.() -> Unit) = MeshBuilder().apply(block).build()
    private const val P = Mat.PAINT
    private const val T = Mat.TRIM
    private const val G = Mat.GLOW
    private const val D = Mat.DARK
    private const val Q = Mat.GLASS

    fun hull(h: Hull): FloatArray = mesh {
        when (h) {
            Hull.DART -> {
                hull(floatArrayOf(1f, 0f, 0.22f, 0.5f, 0.18f, 0.3f, -0.2f, 0.3f, 0.32f, -0.8f, 0.32f, 0.26f, -0.95f, 0.26f, 0.2f), 0.08f, -0.12f, P)
                hull(floatArrayOf(0.58f, 0f, 0.4f, 0.3f, 0.11f, 0.44f, 0f, 0.12f, 0.42f, -0.2f, 0.07f, 0.38f), 0.24f, 0.1f, Q)
                mirror = true
                box(-0.7f, -0.25f, 0.27f, 0.36f, -0.08f, 0.16f, D)
                box(-0.9f, -0.5f, 0.05f, 0.1f, 0.2f, 0.33f, T)
            }
            Hull.WASP -> {
                hull(floatArrayOf(1.08f, 0f, 0.18f, 0.45f, 0.12f, 0.24f, -0.35f, 0.2f, 0.27f, -0.95f, 0.13f, 0.18f), 0.06f, -0.1f, P)
                hull(floatArrayOf(0.5f, 0f, 0.36f, 0.25f, 0.08f, 0.38f, -0.15f, 0.06f, 0.34f), 0.2f, 0.08f, Q)
                mirror = true
                slab(floatArrayOf(-0.5f, 0.15f, -0.95f, 0.42f, -1.05f, 0.4f, -0.85f, 0.12f), 0.12f, 0.16f, 0f, T)
                box(0.1f, 0.6f, 0.1f, 0.13f, 0.12f, 0.2f, G)
            }
            Hull.MANTA -> {
                hull(floatArrayOf(0.9f, 0f, 0.18f, 0.4f, 0.35f, 0.24f, -0.3f, 0.7f, 0.2f, -0.7f, 0.6f, 0.16f, -0.9f, 0.25f, 0.16f), 0.06f, -0.1f, P)
                hull(floatArrayOf(0.45f, 0f, 0.36f, 0.2f, 0.12f, 0.38f, -0.25f, 0.13f, 0.34f), 0.18f, 0.06f, Q)
                mirror = true
                slab(floatArrayOf(-0.25f, 0.55f, -0.6f, 0.6f, -0.62f, 0.66f, -0.3f, 0.66f), 0.15f, 0.15f, 0.05f, G)
                box(-0.85f, -0.35f, 0.3f, 0.4f, 0.1f, 0.2f, T)
            }
            Hull.TALON -> {
                hull(floatArrayOf(0.7f, 0f, 0.25f, 0.3f, 0.25f, 0.32f, -0.6f, 0.32f, 0.32f, -0.95f, 0.22f, 0.22f), 0.08f, -0.12f, P)
                hull(floatArrayOf(0.4f, 0f, 0.44f, 0.15f, 0.12f, 0.46f, -0.2f, 0.1f, 0.42f), 0.26f, 0.12f, Q)
                mirror = true
                tube(0.1f, 1.05f, 0.33f, 0.04f, 0.075f, 0.06f, 6, D, D)
                box(0.15f, 0.55f, 0.26f, 0.42f, -0.08f, 0.14f, T)
                box(0.98f, 1.06f, 0.29f, 0.37f, 0f, 0.1f, G)
            }
            Hull.BULWARK -> {
                hull(floatArrayOf(0.85f, 0f, 0.3f, 0.7f, 0.32f, 0.38f, -0.7f, 0.42f, 0.4f, -0.95f, 0.38f, 0.3f), 0.12f, -0.14f, P)
                hull(floatArrayOf(0.62f, 0f, 0.5f, 0.45f, 0.14f, 0.52f, 0.2f, 0.15f, 0.5f), 0.34f, 0.2f, Q)
                mirror = true
                box(-0.6f, 0.4f, 0.36f, 0.5f, -0.1f, 0.22f, T)
                box(-0.85f, -0.65f, 0.4f, 0.5f, 0f, 0.26f, D)
                box(0.0f, 0.3f, 0.47f, 0.51f, 0.06f, 0.16f, G)
            }
            Hull.PHANTOM -> {
                hull(floatArrayOf(1.02f, 0f, 0.13f, 0.2f, 0.22f, 0.18f, -0.5f, 0.5f, 0.12f, -0.7f, 0.3f, 0.12f, -0.95f, 0.2f, 0.1f), 0.04f, -0.08f, P)
                hull(floatArrayOf(0.45f, 0f, 0.24f, 0.2f, 0.09f, 0.25f, -0.1f, 0.07f, 0.22f), 0.12f, 0.04f, Q)
                mirror = true
                slab(floatArrayOf(-0.3f, 0.35f, -0.48f, 0.47f, -0.62f, 0.3f), 0.1f, 0.1f, 0.04f, G)
            }
            Hull.HALO -> {
                hull(floatArrayOf(0.95f, 0f, 0.22f, 0.45f, 0.16f, 0.28f, -0.4f, 0.2f, 0.28f, -0.95f, 0.15f, 0.2f), 0.08f, -0.1f, P)
                hull(floatArrayOf(0.5f, 0f, 0.38f, 0.25f, 0.09f, 0.4f, 0f, 0.08f, 0.36f), 0.22f, 0.1f, Q)
                at(-0.15f)
                ring(0.62f, 0.78f, 0.1f, -0.06f, 20, T, D)
                ring(0.52f, 0.6f, 0.06f, 0.02f, 20, G)
                at()
                mirror = true
                box(-0.3f, 0f, 0.15f, 0.56f, -0.02f, 0.06f, D)
            }
            Hull.WRAITH -> {
                hull(floatArrayOf(0.62f, 0f, 0.3f, 0.2f, 0.2f, 0.34f, -0.6f, 0.3f, 0.32f, -0.95f, 0.2f, 0.22f), 0.1f, -0.12f, P)
                hull(floatArrayOf(0.3f, 0f, 0.46f, 0.05f, 0.11f, 0.47f, -0.3f, 0.09f, 0.42f), 0.28f, 0.14f, Q)
                mirror = true
                slab(floatArrayOf(0.25f, 0.13f, 1.08f, 0.3f, 1.02f, 0.4f, -0.05f, 0.3f), 0.12f, 0.2f, -0.04f, T)
                box(0.6f, 0.95f, 0.3f, 0.34f, 0.12f, 0.2f, G)
                slab(floatArrayOf(-0.5f, 0.28f, -1f, 0.6f, -1.05f, 0.52f, -0.8f, 0.22f), 0.1f, 0.14f, -0.02f, D)
            }
        }
    }

    /** One wing's outline (x, y pairs); the other side is its mirror. Several polygons for multi-part wings. */
    private fun wingShapes(w: Wings): List<FloatArray> = when (w) {
        Wings.SWEPT -> listOf(floatArrayOf(0.2f, 0.25f, -0.6f, 0.95f, -0.82f, 0.95f, -0.6f, 0.25f))
        Wings.DELTA -> listOf(floatArrayOf(0.45f, 0.2f, -0.85f, 0.92f, -0.88f, 0.2f))
        Wings.FORWARD -> listOf(floatArrayOf(-0.1f, 0.25f, 0.38f, 0.95f, 0.22f, 1.02f, -0.62f, 0.25f))
        Wings.TWIN -> listOf(
            floatArrayOf(0.35f, 0.24f, 0.02f, 0.72f, -0.14f, 0.72f, 0f, 0.24f),
            floatArrayOf(-0.3f, 0.25f, -0.72f, 0.88f, -0.88f, 0.88f, -0.72f, 0.25f),
        )
        Wings.BLADE -> listOf(floatArrayOf(0f, 0.28f, 0.92f, 0.62f, 0.86f, 0.71f, -0.6f, 0.5f, -0.6f, 0.28f))
        Wings.ARC -> listOf(floatArrayOf(0.1f, 0.25f, -0.08f, 0.7f, -0.42f, 0.98f, -0.82f, 1.02f, -0.56f, 0.76f, -0.46f, 0.25f))
        Wings.CROWN -> listOf(floatArrayOf(0.12f, 0.25f, 0.02f, 0.82f, -0.2f, 0.5f, -0.36f, 1.02f, -0.5f, 0.5f, -0.72f, 0.86f, -0.8f, 0.25f))
    }

    fun wings(w: Wings): FloatArray = mesh {
        mirror = true
        for (poly in wingShapes(w)) {
            slab(poly, 0.04f, 0.09f, -0.04f, P, T)
            // A trim pod and a running light at the wingtip.
            var bi = 0
            for (i in 0 until poly.size / 2) if (poly[i * 2 + 1] > poly[bi * 2 + 1]) bi = i
            val tx = poly[bi * 2]; val ty = poly[bi * 2 + 1]
            box(tx - 0.16f, tx + 0.06f, ty - 0.07f, ty, -0.03f, 0.1f, T)
            box(tx - 0.02f, tx + 0.06f, ty - 0.05f, ty - 0.01f, 0.1f, 0.13f, G)
        }
    }

    /** Where each engine's nozzles sit across the ship (the world uses the same numbers for its exhaust). */
    fun nozzles(e: Engine): FloatArray = when (e) {
        Engine.ION, Engine.FUSION -> floatArrayOf(0f)
        Engine.AFTERBURNER -> floatArrayOf(-0.3f, 0.3f)
        Engine.QUANTUM -> floatArrayOf(-0.32f, 0f, 0.32f)
    }

    fun engine(e: Engine): FloatArray = mesh {
        val r = when (e) { Engine.ION -> 0.17f; Engine.FUSION -> 0.16f; Engine.AFTERBURNER -> 0.14f; Engine.QUANTUM -> 0.11f }
        val len = if (e == Engine.AFTERBURNER) 0.42f else 0.32f
        for (y in nozzles(e)) {
            tube(-1.08f, -1.08f + len, y, 0.05f, r, r * 0.8f, 8, D, G)
            tube(-1.0f, -0.95f, y, 0.05f, r * 1.12f, r * 1.12f, 8, T, T)
        }
        when (e) {
            Engine.FUSION -> { blob(-0.55f, 0f, 0.3f, 0.13f, 0f, 0, G, 0); at(-0.55f, 0f, 0.3f); ring(0.15f, 0.2f, 0.03f, -0.03f, 12, T) }
            Engine.QUANTUM -> { mirror = true; box(-0.85f, -0.55f, 0.36f, 0.4f, -0.02f, 0.12f, G) }
            else -> {}
        }
    }

    /** The player's wing drones. */
    fun drone(): FloatArray = mesh {
        hull(floatArrayOf(0.4f, 0f, 0.14f, 0.1f, 0.15f, 0.16f, -0.3f, 0.16f, 0.14f), 0.04f, -0.06f, P)
        box(0.05f, 0.3f, -0.04f, 0.04f, 0.12f, 0.2f, G)
        mirror = true
        slab(floatArrayOf(0f, 0.12f, -0.28f, 0.36f, -0.36f, 0.34f, -0.24f, 0.12f), 0.03f, 0.05f, -0.02f, T)
    }

    // ---------------------------------------------------------------- enemies (radius ~1, facing +x)

    fun drone2(): FloatArray = mesh {
        hull(floatArrayOf(0.95f, 0f, 0.36f, 0.35f, 0.6f, 0.36f, -0.5f, 0.62f, 0.3f, -0.85f, 0.25f, 0.24f), 0.1f, -0.15f, P)
        blob(0.25f, 0f, 0.35f, 0.24f, 0f, 0, G, 0)
        mirror = true
        slab(floatArrayOf(-0.2f, 0.55f, -0.75f, 0.95f, -0.9f, 0.85f, -0.6f, 0.45f), 0.08f, 0.12f, -0.05f, T)
    }

    fun dart(): FloatArray = mesh {
        mirror = true
        slab(floatArrayOf(1.05f, 0f, -0.25f, 0.32f, -0.95f, 0.95f, -0.6f, 0.2f, -0.75f, 0f), 0.08f, 0.32f, -0.1f, P, T)
        box(-0.9f, -0.55f, 0.72f, 0.84f, 0.05f, 0.14f, G)
        mirror = false
        box(0.1f, 0.6f, -0.06f, 0.06f, 0.24f, 0.32f, G)
    }

    fun gunship(): FloatArray = mesh {
        hull(floatArrayOf(0.95f, 0f, 0.4f, 0.6f, 0.5f, 0.5f, -0.6f, 0.62f, 0.5f, -0.95f, 0.45f, 0.4f), 0.15f, -0.18f, P)
        box(0.2f, 0.65f, -0.18f, 0.18f, 0.5f, 0.58f, T)
        box(0.55f, 0.66f, -0.14f, 0.14f, 0.5f, 0.6f, G)
        mirror = true
        tube(0.1f, 1.15f, 0.62f, 0.1f, 0.13f, 0.1f, 6, D, D)
        box(1.08f, 1.16f, 0.56f, 0.68f, 0.05f, 0.16f, G)
        box(-0.8f, -0.3f, 0.6f, 0.78f, -0.1f, 0.2f, T)
    }

    fun spinner(): FloatArray = mesh {
        blob(0f, 0f, 0.1f, 0.4f, 0.05f, 3, T, 1)
        blob(0f, 0f, 0.42f, 0.18f, 0f, 0, G, 0)
        ring(0.62f, 0.78f, 0.12f, -0.12f, 18, P, D)
        for (k in 0 until 4) {
            at(ang = k * PI.toFloat() / 2)
            slab(floatArrayOf(0.35f, -0.12f, 1.05f, -0.28f, 1.1f, 0.05f, 0.35f, 0.12f), 0.08f, 0.16f, -0.06f, P, T)
            box(0.92f, 1.02f, -0.2f, 0f, 0.12f, 0.18f, G)
        }
    }

    fun mine(): FloatArray = mesh {
        blob(0f, 0f, 0f, 0.62f, 0.04f, 5, P, 1)
        blob(0f, 0f, 0.5f, 0.2f, 0f, 0, G, 0)
        for (k in 0 until 8) {
            at(cos(k * PI.toFloat() / 4) * 0.5f, sin(k * PI.toFloat() / 4) * 0.5f, 0f, k * PI.toFloat() / 4)
            spike(0.55f, 0.1f, 0f, if (k % 2 == 0) T else D)
        }
    }

    fun lancer(): FloatArray = mesh {
        hull(floatArrayOf(1.1f, 0f, 0.22f, 0.4f, 0.3f, 0.36f, -0.5f, 0.38f, 0.36f, -0.95f, 0.18f, 0.24f), 0.08f, -0.12f, P)
        tube(0.5f, 1.25f, 0f, 0.3f, 0.14f, 0.08f, 6, D)
        blob(1.2f, 0f, 0.3f, 0.12f, 0f, 0, G, 0)
        mirror = true
        slab(floatArrayOf(-0.2f, 0.34f, -0.8f, 0.9f, -0.95f, 0.85f, -0.7f, 0.3f), 0.06f, 0.12f, -0.06f, T)
        box(-0.85f, -0.6f, 0.78f, 0.86f, 0.06f, 0.14f, G)
    }

    fun carrier(): FloatArray = mesh {
        hull(floatArrayOf(0.95f, 0f, 0.3f, 0.75f, 0.45f, 0.42f, -0.7f, 0.55f, 0.44f, -0.95f, 0.4f, 0.34f), 0.12f, -0.16f, P)
        box(-0.5f, 0.2f, -0.15f, 0.15f, 0.42f, 0.56f, T)
        box(-0.3f, 0.05f, -0.06f, 0.06f, 0.56f, 0.62f, G)
        mirror = true
        box(-0.75f, 0.55f, 0.52f, 0.7f, -0.12f, 0.2f, D)
        for (i in 0 until 5) box(-0.6f + i * 0.25f, -0.48f + i * 0.25f, 0.7f, 0.73f, 0f, 0.14f, G)
        tube(0.4f, 1.02f, 0.3f, 0.36f, 0.07f, 0.05f, 6, D, D)
    }

    fun rock(seed: Int): FloatArray = mesh {
        blob(0f, 0f, 0f, 0.92f, 0.32f, seed, P, 1)
        blob(0.35f, 0.3f, 0.45f, 0.32f, 0.3f, seed + 7, D, 0)
    }

    // ---------------------------------------------------------------- bosses (radius ~1)

    fun boss(b: Boss): FloatArray = mesh {
        when (b) {
            Boss.WARDEN -> {
                ring(0.7f, 1f, 0.2f, -0.2f, 24, P, D)
                blob(0f, 0f, 0.1f, 0.55f, 0.06f, 2, T, 1)
                blob(0f, 0f, 0.5f, 0.24f, 0f, 0, G, 0)
                for (k in 0 until 6) {
                    val a = k * PI.toFloat() / 3 + PI.toFloat() / 6
                    at(cos(a) * 0.85f, sin(a) * 0.85f, 0f, a)
                    box(-0.12f, 0.12f, -0.14f, 0.14f, 0.1f, 0.34f, T)
                    box(0.12f, 0.36f, -0.04f, 0.04f, 0.2f, 0.28f, D)
                    box(0.3f, 0.38f, -0.05f, 0.05f, 0.18f, 0.3f, G)
                }
            }
            Boss.HYDRA -> {
                hull(floatArrayOf(0.4f, 0f, 0.4f, 0.1f, 0.55f, 0.46f, -0.7f, 0.7f, 0.42f, -1f, 0.4f, 0.3f), 0.14f, -0.16f, P)
                for (y in floatArrayOf(-0.62f, 0f, 0.62f)) {
                    at(0.15f, y, 0f, 0f, 0.55f)
                    hull(floatArrayOf(1.6f, 0f, 0.3f, 1.1f, 0.3f, 0.5f, 0.2f, 0.32f, 0.5f), 0.2f, -0.1f, T)
                    blob(1.25f, 0f, 0.5f, 0.12f, 0f, 0, G, 0)
                }
                at()
                mirror = true
                slab(floatArrayOf(-0.5f, 0.6f, -1.1f, 1.05f, -1.15f, 0.9f, -0.9f, 0.5f), 0.08f, 0.16f, -0.08f, D)
            }
            Boss.TEMPEST -> {
                blob(0f, 0f, 0.05f, 0.5f, 0.05f, 4, T, 1)
                blob(0f, 0f, 0.42f, 0.26f, 0.0f, 0, G, 1)
                ring(0.5f, 0.62f, 0.14f, -0.06f, 20, D)
                for (k in 0 until 6) {
                    at(ang = k * PI.toFloat() / 3)
                    slab(floatArrayOf(0.5f, -0.15f, 0.95f, 0.1f, 1.05f, 0.45f, 0.62f, 0.3f), 0.08f, 0.18f, -0.08f, P, T)
                    box(0.9f, 1f, 0.2f, 0.32f, 0.12f, 0.2f, G)
                }
            }
            Boss.REVENANT -> {
                hull(floatArrayOf(1.1f, 0f, 0.3f, 0.6f, 0.3f, 0.42f, -0.3f, 0.6f, 0.48f, -0.9f, 0.5f, 0.42f, -1.05f, 0.3f, 0.32f), 0.12f, -0.16f, P)
                hull(floatArrayOf(0.2f, 0f, 0.6f, 0f, 0.18f, 0.62f, -0.5f, 0.2f, 0.58f), 0.42f, 0.2f, Q)
                mirror = true
                slab(floatArrayOf(0.2f, 0.4f, 0.9f, 0.85f, 0.75f, 0.92f, -0.3f, 0.6f), 0.1f, 0.16f, -0.06f, T)
                slab(floatArrayOf(-0.4f, 0.55f, -0.6f, 1.15f, -0.8f, 1.1f, -0.85f, 0.5f), 0.1f, 0.18f, -0.06f, D)
                box(-0.75f, -0.6f, 0.95f, 1.08f, 0.1f, 0.2f, G)
                tube(-1.15f, -0.85f, 0.32f, 0.12f, 0.14f, 0.12f, 8, D, G)
                box(0.3f, 0.75f, 0.32f, 0.36f, 0.3f, 0.36f, G)
            }
            Boss.VOID_HEART -> {
                blob(0f, 0f, 0f, 0.5f, 0.08f, 9, G, 1)
                for (k in 0 until 10) {
                    val a = k * PI.toFloat() / 5
                    at(cos(a) * 0.55f, sin(a) * 0.55f, 0f, a)
                    spike(0.6f + 0.15f * (k % 2), 0.13f, 0f, if (k % 2 == 0) P else D)
                }
                at()
                ring(0.78f, 0.92f, 0.1f, -0.1f, 24, T, D)
                for (k in 0 until 3) {
                    at(ang = k * 2 * PI.toFloat() / 3)
                    blob(0.85f, 0f, 0.2f, 0.18f, 0.2f, k + 11, P, 0)
                }
            }
        }
    }
}
