package dev.m4s4t0.voidwars.model

/** Credits come from flying; cores (rarer) from bosses, medals, missions and the daily drop. */
enum class Currency { CREDITS, CORES }

/** What a part costs to own and the pilot level it opens at. Free parts are owned from the start. */
data class Price(val amount: Int, val currency: Currency = Currency.CREDITS, val level: Int = 1) {
    val free get() = amount == 0
}

private fun cr(amount: Int, level: Int) = Price(amount, Currency.CREDITS, level)
private fun co(amount: Int, level: Int) = Price(amount, Currency.CORES, level)
private val FREE = Price(0)

/** A piece of the ship the hangar can swap. [key] is how it's saved and owned ("hull:DART"). */
sealed interface Part {
    val name: String
    val title: String
    val desc: String
    val price: Price
    val slot: Slot
    val key get() = "${slot.name.lowercase()}:$name"
}

enum class Slot(val title: String, val upgradable: Boolean) {
    HULL("Hull", true), WINGS("Wings", false), ENGINE("Engine", false),
    PRIMARY("Weapon", true), SUPPORT("Support", true), SPECIAL("Special", true),
}

/**
 * The body of the ship: how much punishment it takes, how fast it moves, and how big a target it is ([hitbox], world
 * units, the tiny core enemy bullets have to touch). Bonuses are fractions (0.1 = +10%).
 */
enum class Hull(
    override val title: String, override val desc: String, override val price: Price,
    val hp: Int, val speed: Float, val hitbox: Float,
    val damage: Float = 0f, val graze: Float = 0f, val charge: Float = 0f,
) : Part {
    DART("Dart", "A trusty all-rounder", FREE, hp = 100, speed = 1f, hitbox = 0.2f),
    WASP("Wasp", "Quick and slim, but thin-skinned", cr(1200, 3), hp = 80, speed = 1.18f, hitbox = 0.16f),
    MANTA("Manta", "A broad wing of armour", cr(2600, 5), hp = 140, speed = 0.92f, hitbox = 0.24f, charge = 0.1f),
    TALON("Talon", "Built around its guns", cr(4200, 8), hp = 110, speed = 1.04f, hitbox = 0.2f, damage = 0.12f),
    BULWARK("Bulwark", "A flying fortress", cr(6800, 11), hp = 210, speed = 0.82f, hitbox = 0.28f),
    PHANTOM("Phantom", "Hard to see, harder to hit", cr(9500, 14), hp = 90, speed = 1.12f, hitbox = 0.13f, graze = 0.5f),
    HALO("Halo", "A ring hull that hums with power", co(30, 17), hp = 125, speed = 1.02f, hitbox = 0.2f, charge = 0.35f),
    WRAITH("Wraith", "The void's own flagship", co(70, 22), hp = 170, speed = 1.14f, hitbox = 0.16f, damage = 0.18f, graze = 0.2f);

    override val slot get() = Slot.HULL
}

enum class Wings(
    override val title: String, override val desc: String, override val price: Price,
    val damage: Float = 0f, val speed: Float = 0f, val graze: Float = 0f, val fireRate: Float = 0f, val magnet: Float = 0f, val charge: Float = 0f,
) : Part {
    SWEPT("Swept", "Clean and balanced", FREE),
    DELTA("Delta", "+8% damage", cr(700, 2), damage = 0.08f),
    FORWARD("Forward", "+12% speed", cr(1500, 4), speed = 0.12f),
    TWIN("Twin", "+30% graze range", cr(2400, 6), graze = 0.3f),
    BLADE("Blade", "+12% fire rate", cr(3800, 9), fireRate = 0.12f),
    ARC("Arc", "+40% magnet, +15% special charge", cr(5200, 12), magnet = 0.4f, charge = 0.15f),
    CROWN("Crown", "+10% damage, +10% fire rate", co(25, 16), damage = 0.1f, fireRate = 0.1f);

    override val slot get() = Slot.WINGS
}

enum class Engine(
    override val title: String, override val desc: String, override val price: Price,
    val speed: Float = 0f, val regen: Float = 0f, val iframes: Float = 0f, val trail: Float = 1f,
) : Part {
    ION("Ion drive", "Steady blue thrust", FREE),
    AFTERBURNER("Afterburner", "+15% speed and a long hot trail", cr(1800, 4), speed = 0.15f, trail = 1.8f),
    FUSION("Fusion core", "Repairs the hull while you're not hit", cr(4800, 10), regen = 1.2f, trail = 1.2f),
    QUANTUM("Quantum drive", "Longer invulnerability after a hit", co(20, 13), iframes = 0.9f, trail = 0.8f);

    override val slot get() = Slot.ENGINE
}

/** The main gun. Fires by itself; its upgrade level adds streams, damage and size (see the engine's Weapons). */
enum class Primary(override val title: String, override val desc: String, override val price: Price) : Part {
    PULSE("Pulse blaster", "Fast twin bolts; more streams as it levels", FREE),
    SPREAD("Scatter cannon", "A wide fan of shots", cr(900, 2)),
    LASER("Lance beam", "A continuous beam that cuts through everything", cr(2200, 5)),
    HOMING("Seeker missiles", "Missiles that hunt their own targets", cr(3400, 7)),
    PLASMA("Plasma wave", "Big slow orbs that pierce", cr(5000, 10)),
    RAIL("Rail driver", "Charged slugs that punch through a line", cr(7500, 13)),
    FLAK("Flak burst", "Shells that burst into shrapnel", co(35, 18));

    override val slot get() = Slot.PRIMARY
}

enum class Support(override val title: String, override val desc: String, override val price: Price) : Part {
    NONE("None", "Nothing extra", FREE),
    DRONES("Wing drones", "Two drones that fly beside you and fire", cr(1400, 3)),
    MISSILES("Missile pod", "Homing missiles every second or so", cr(2800, 6)),
    ORBITALS("Orbital shields", "Orbs that circle you and eat bullets", cr(4500, 9)),
    TESLA("Tesla coil", "Chains lightning between nearby enemies", co(25, 15));

    override val slot get() = Slot.SUPPORT
}

/** The big button. Charges from kills and grazes. */
enum class Special(override val title: String, override val desc: String, override val price: Price, val seconds: Float) : Part {
    NOVA("Nova bomb", "Wipes every bullet off the screen and blasts everything", FREE, 0f),
    OVERDRIVE("Overdrive", "Triple fire rate and invulnerable for a moment", cr(2000, 4), 5f),
    PHASE("Phase shift", "Slows time and lets bullets pass through you", cr(3600, 7), 4f),
    SINGULARITY("Singularity", "A black hole that swallows bullets and crushes ships", cr(6000, 11), 5f),
    BARRAGE("Barrage", "A swarm of missiles at every enemy on screen", co(30, 16), 0f);

    override val slot get() = Slot.SPECIAL
}

/** A paint colour (ARGB). */
data class Swatch(val id: String, val title: String, val argb: Long, val price: Price = FREE) {
    val key get() = "color:$id"
}

val SWATCHES = listOf(
    Swatch("graphite", "Graphite", 0xFF3A3D4A),
    Swatch("snow", "Snow", 0xFFE8EAF2),
    Swatch("violet", "Void violet", 0xFFA970FF),
    Swatch("cyan", "Cyan", 0xFF22E3FF),
    Swatch("crimson", "Crimson", 0xFFE0304E),
    Swatch("gold", "Gold", 0xFFFFC23D),
    Swatch("navy", "Navy", 0xFF1F2E66, cr(150, 1)),
    Swatch("teal", "Teal", 0xFF14A3A0, cr(150, 1)),
    Swatch("lime", "Lime", 0xFF9BFF3A, cr(250, 2)),
    Swatch("orange", "Orange", 0xFFFF7A1F, cr(250, 2)),
    Swatch("pink", "Hot pink", 0xFFFF4FB8, cr(300, 3)),
    Swatch("mint", "Mint", 0xFF6BFFC8, cr(300, 3)),
    Swatch("sky", "Sky", 0xFF6FB6FF, cr(350, 4)),
    Swatch("plum", "Plum", 0xFF6A2C8C, cr(350, 4)),
    Swatch("rust", "Rust", 0xFFA4482A, cr(400, 5)),
    Swatch("olive", "Olive", 0xFF6E7A34, cr(400, 5)),
    Swatch("ice", "Ice", 0xFFBDF4FF, cr(500, 6)),
    Swatch("blood", "Blood", 0xFF7A0A1E, cr(500, 7)),
    Swatch("sun", "Sunflare", 0xFFFFE45C, cr(600, 8)),
    Swatch("ink", "Ink", 0xFF0C0C14, cr(600, 9)),
    Swatch("rose", "Rose gold", 0xFFE6A08A, co(5, 10)),
    Swatch("toxic", "Toxic", 0xFF4DFF5A, co(5, 12)),
    Swatch("ultra", "Ultraviolet", 0xFF7B2CFF, co(8, 14)),
    Swatch("pearl", "Pearl", 0xFFF4EEDC, co(8, 16)),
)

/** How the paint catches the light (the shader's look for the hull). */
enum class Finish(val title: String, val desc: String, val price: Price) {
    MATTE("Matte", "Flat and stealthy", FREE),
    GLOSS("Gloss", "A clean shine", FREE),
    METAL("Metallic", "Brushed metal", cr(800, 3)),
    CHROME("Chrome", "Mirror bright", cr(2500, 7)),
    HOLO("Holo", "Shifts colour as it turns", co(15, 11)),
    VOID("Void", "Starlight swirling under the paint", co(40, 18));

    val key get() = "finish:$name"
}

/** A pattern painted in the second colour over the first. */
enum class Decal(val title: String, val price: Price) {
    NONE("Plain", FREE),
    STRIPE("Racing stripe", FREE),
    CHEVRON("Chevrons", cr(400, 2)),
    SPLIT("Split", cr(600, 4)),
    CHECKER("Checker", cr(900, 6)),
    TIGER("Tiger", cr(1400, 9)),
    CIRCUIT("Circuit", co(10, 13));

    val key get() = "decal:$name"
}

data class Paint(
    val primary: String = "graphite",
    val secondary: String = "violet",
    val glow: String = "cyan",
    val finish: Finish = Finish.GLOSS,
    val decal: Decal = Decal.STRIPE,
)

fun swatch(id: String) = SWATCHES.firstOrNull { it.id == id } ?: SWATCHES.first()

/** Everything the hangar puts together. */
data class Loadout(
    val hull: Hull = Hull.DART,
    val wings: Wings = Wings.SWEPT,
    val engine: Engine = Engine.ION,
    val primary: Primary = Primary.PULSE,
    val support: Support = Support.NONE,
    val special: Special = Special.NOVA,
    val paint: Paint = Paint(),
) {
    fun part(slot: Slot): Part = when (slot) {
        Slot.HULL -> hull
        Slot.WINGS -> wings
        Slot.ENGINE -> engine
        Slot.PRIMARY -> primary
        Slot.SUPPORT -> support
        Slot.SPECIAL -> special
    }

    fun with(p: Part): Loadout = when (p) {
        is Hull -> copy(hull = p)
        is Wings -> copy(wings = p)
        is Engine -> copy(engine = p)
        is Primary -> copy(primary = p)
        is Support -> copy(support = p)
        is Special -> copy(special = p)
    }

    /** The flying numbers, given each part's upgrade level ([up] returns 1..MAX_UPGRADE). */
    fun stats(up: (Part) -> Int): ShipStats {
        val hullUp = up(hull) - 1
        return ShipStats(
            maxHp = (hull.hp * (1f + 0.12f * hullUp)).toInt(),
            speed = 26f * hull.speed * (1f + wings.speed + engine.speed),
            hitbox = hull.hitbox,
            damage = 1f + hull.damage + wings.damage,
            fireRate = 1f + wings.fireRate,
            graze = 1.1f * (1f + hull.graze + wings.graze),
            magnet = 3.2f * (1f + wings.magnet),
            charge = 1f + hull.charge + wings.charge + 0.08f * (up(special) - 1),
            regen = engine.regen,
            iframes = 1.4f + engine.iframes,
            primaryLevel = up(primary),
            supportLevel = up(support),
            specialLevel = up(special),
        )
    }

    companion object {
        val ALL_PARTS: Map<Slot, List<Part>> = mapOf(
            Slot.HULL to Hull.entries, Slot.WINGS to Wings.entries, Slot.ENGINE to Engine.entries,
            Slot.PRIMARY to Primary.entries, Slot.SUPPORT to Support.entries, Slot.SPECIAL to Special.entries,
        )

        fun partOf(key: String): Part? {
            val (slot, name) = key.split(':').takeIf { it.size == 2 } ?: return null
            return ALL_PARTS[Slot.entries.firstOrNull { it.name.lowercase() == slot } ?: return null]?.firstOrNull { it.name == name }
        }
    }
}

data class ShipStats(
    val maxHp: Int,
    /** World units a second. */
    val speed: Float,
    val hitbox: Float,
    val damage: Float,
    val fireRate: Float,
    /** How close (world units) a bullet must pass to count as a graze. */
    val graze: Float,
    val magnet: Float,
    val charge: Float,
    /** Hull points a second, after a few seconds without a hit. */
    val regen: Float,
    val iframes: Float,
    val primaryLevel: Int,
    val supportLevel: Int,
    val specialLevel: Int,
)

const val MAX_UPGRADE = 5

/** Credits to take [p] from [level] to level + 1 (null when it's maxed or can't be upgraded). */
fun upgradeCost(p: Part, level: Int): Int? {
    if (!p.slot.upgradable || level >= MAX_UPGRADE || p == Support.NONE) return null
    val base = when (p.price.currency) {
        Currency.CORES -> 900 + p.price.amount * 40
        Currency.CREDITS -> 300 + p.price.amount / 4
    }
    return (base * floatArrayOf(1f, 2.2f, 4f, 7f)[level - 1]).toInt() / 10 * 10
}
