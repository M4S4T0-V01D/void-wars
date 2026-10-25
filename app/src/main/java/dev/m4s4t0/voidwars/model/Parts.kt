package dev.m4s4t0.voidwars.model

/**
 * Credits come from flying; cores (rarer) from bosses, medals, missions and the daily drop; void shards from mission
 * modes, contracts and the Rift, spent at the black market. [REWARD] marks things that can't be bought at all: they're
 * handed out by the mode story lines.
 */
enum class Currency { CREDITS, CORES, SHARDS, REWARD }

/** What a part costs to own and the pilot level it opens at. Free parts are owned from the start. */
data class Price(val amount: Int, val currency: Currency = Currency.CREDITS, val level: Int = 1) {
    val free get() = amount == 0 && currency != Currency.REWARD
    val reward get() = currency == Currency.REWARD
}

private fun cr(amount: Int, level: Int) = Price(amount, Currency.CREDITS, level)
private fun co(amount: Int, level: Int) = Price(amount, Currency.CORES, level)
private fun sh(amount: Int, level: Int) = Price(amount, Currency.SHARDS, level)
private val FREE = Price(0)
/** Earned from a mode story line (see [Stories]); never sold. */
val STORY = Price(0, Currency.REWARD)

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
    /** The original hull this one is built on (mesh and handling); null for the originals. */
    private val look: String? = null,
) : Part {
    DART("Dart", "A trusty all-rounder", FREE, hp = 100, speed = 1f, hitbox = 0.2f),
    WASP("Wasp", "Quick and slim, but thin-skinned", cr(1200, 3), hp = 80, speed = 1.18f, hitbox = 0.16f),
    MANTA("Manta", "A broad wing of armour", cr(2600, 5), hp = 140, speed = 0.92f, hitbox = 0.24f, charge = 0.1f),
    TALON("Talon", "Built around its guns", cr(4200, 8), hp = 110, speed = 1.04f, hitbox = 0.2f, damage = 0.12f),
    BULWARK("Bulwark", "A flying fortress", cr(6800, 11), hp = 210, speed = 0.82f, hitbox = 0.28f),
    PHANTOM("Phantom", "Hard to see, harder to hit", cr(9500, 14), hp = 90, speed = 1.12f, hitbox = 0.13f, graze = 0.5f),
    HALO("Halo", "A ring hull that hums with power", co(30, 17), hp = 125, speed = 1.02f, hitbox = 0.2f, charge = 0.35f),
    WRAITH("Wraith", "The void's own flagship", co(70, 22), hp = 170, speed = 1.14f, hitbox = 0.16f, damage = 0.18f, graze = 0.2f),
    VIPER("Viper", "A Wasp with fangs", cr(14000, 26), hp = 100, speed = 1.24f, hitbox = 0.15f, damage = 0.06f, look = "WASP"),
    AEGIS("Aegis", "Shield plating over a fortress frame", cr(20000, 30), hp = 270, speed = 0.86f, hitbox = 0.27f, charge = 0.1f, look = "BULWARK"),
    CORSAIR("Corsair", "A pirate gunship, all barrels", cr(28000, 35), hp = 140, speed = 1.08f, hitbox = 0.2f, damage = 0.24f, look = "TALON"),
    STINGRAY("Stingray", "Wide armour that drinks in energy", cr(36000, 40), hp = 185, speed = 0.98f, hitbox = 0.23f, charge = 0.28f, look = "MANTA"),
    SPECTRE("Spectre", "Black-market stealth plating", sh(60, 30), hp = 115, speed = 1.16f, hitbox = 0.12f, graze = 0.7f, look = "PHANTOM"),
    SERAPH("Seraph", "A ring of light around a burning core", sh(120, 45), hp = 160, speed = 1.08f, hitbox = 0.19f, charge = 0.5f, look = "HALO"),
    LEVIATHAN("Leviathan", "The deep void's dreadnought", co(160, 55), hp = 250, speed = 1.1f, hitbox = 0.17f, damage = 0.26f, graze = 0.2f, look = "WRAITH"),
    ECLIPSE("Eclipse", "Forged from the hearts of fallen bosses", STORY, hp = 230, speed = 1.2f, hitbox = 0.14f, damage = 0.32f, graze = 0.3f, charge = 0.3f, look = "WRAITH"),
    PALADIN("Paladin", "The convoy's own guardian", STORY, hp = 320, speed = 0.96f, hitbox = 0.25f, damage = 0.1f, charge = 0.2f, look = "BULWARK"),
    GHOST("Ghost", "One hit was always enough. It never gets hit.", STORY, hp = 105, speed = 1.28f, hitbox = 0.1f, damage = 0.12f, graze = 1f, look = "PHANTOM");

    val base: Hull get() = look?.let { valueOf(it) } ?: this
    override val slot get() = Slot.HULL
}

enum class Wings(
    override val title: String, override val desc: String, override val price: Price,
    val damage: Float = 0f, val speed: Float = 0f, val graze: Float = 0f, val fireRate: Float = 0f, val magnet: Float = 0f, val charge: Float = 0f,
    private val look: String? = null,
) : Part {
    SWEPT("Swept", "Clean and balanced", FREE),
    DELTA("Delta", "+8% damage", cr(700, 2), damage = 0.08f),
    FORWARD("Forward", "+12% speed", cr(1500, 4), speed = 0.12f),
    TWIN("Twin", "+30% graze range", cr(2400, 6), graze = 0.3f),
    BLADE("Blade", "+12% fire rate", cr(3800, 9), fireRate = 0.12f),
    ARC("Arc", "+40% magnet, +15% special charge", cr(5200, 12), magnet = 0.4f, charge = 0.15f),
    CROWN("Crown", "+10% damage, +10% fire rate", co(25, 16), damage = 0.1f, fireRate = 0.1f),
    RAZOR("Razor", "+18% fire rate, +4% damage", cr(9000, 24), damage = 0.04f, fireRate = 0.18f, look = "BLADE"),
    HAWK("Hawk", "+18% speed, +10% graze range", cr(16000, 32), speed = 0.18f, graze = 0.1f, look = "FORWARD"),
    LATTICE("Lattice", "+45% graze range, +20% magnet", cr(24000, 38), graze = 0.45f, magnet = 0.2f, look = "TWIN"),
    HALBERD("Halberd", "+16% damage", sh(80, 34), damage = 0.16f, look = "DELTA"),
    SOLAR("Solar vanes", "+60% magnet, +30% special charge", co(90, 48), magnet = 0.6f, charge = 0.3f, look = "ARC"),
    PHOENIX("Phoenix", "+15% damage, fire rate and charge", STORY, damage = 0.15f, fireRate = 0.15f, charge = 0.15f, look = "CROWN"),
    MIRAGE("Mirage", "+80% graze range, +10% speed", STORY, speed = 0.1f, graze = 0.8f, look = "TWIN");

    val base: Wings get() = look?.let { valueOf(it) } ?: this
    override val slot get() = Slot.WINGS
}

enum class Engine(
    override val title: String, override val desc: String, override val price: Price,
    val speed: Float = 0f, val regen: Float = 0f, val iframes: Float = 0f, val trail: Float = 1f,
    private val look: String? = null,
) : Part {
    ION("Ion drive", "Steady blue thrust", FREE),
    AFTERBURNER("Afterburner", "+15% speed and a long hot trail", cr(1800, 4), speed = 0.15f, trail = 1.8f),
    FUSION("Fusion core", "Repairs the hull while you're not hit", cr(4800, 10), regen = 1.2f, trail = 1.2f),
    QUANTUM("Quantum drive", "Longer invulnerability after a hit", co(20, 13), iframes = 0.9f, trail = 0.8f),
    PLASMA_JET("Plasma jet", "+22% speed and a blazing trail", cr(15000, 28), speed = 0.22f, trail = 2.2f, look = "AFTERBURNER"),
    NOVA_CORE("Nova core", "Fast repairs between hits", cr(26000, 36), regen = 2.1f, trail = 1.3f, look = "FUSION"),
    WARP("Warp coil", "Long invulnerability and a little speed", sh(90, 40), speed = 0.08f, iframes = 1.4f, trail = 0.9f, look = "QUANTUM"),
    SOLAR_SAIL("Solar sail", "Speed and slow repairs", co(80, 50), speed = 0.1f, regen = 0.8f, trail = 1.4f, look = "ION"),
    ZERO_POINT("Zero-point drive", "Speed, repairs and invulnerability, all at once", STORY, speed = 0.25f, regen = 1f, iframes = 1f, trail = 1.6f, look = "QUANTUM");

    val base: Engine get() = look?.let { valueOf(it) } ?: this
    override val slot get() = Slot.ENGINE
}

/**
 * A gun: fitted as the main one or the backup, or found in a weapon crate. Its upgrade level adds streams, damage and
 * size (see the engine's Weapons). Later guns fire like an original ([base]) with more [power] and [rate].
 */
enum class Primary(
    override val title: String, override val desc: String, override val price: Price,
    val power: Float = 1f, val rate: Float = 1f, private val look: String? = null,
) : Part {
    PULSE("Pulse blaster", "Fast twin bolts; more streams as it levels", FREE),
    SPREAD("Scatter cannon", "A wide fan of shots", cr(900, 2)),
    LASER("Lance beam", "A continuous beam that cuts through everything", cr(2200, 5)),
    HOMING("Seeker missiles", "Missiles that hunt their own targets", cr(3400, 7)),
    PLASMA("Plasma wave", "Big slow orbs that pierce", cr(5000, 10)),
    RAIL("Rail driver", "Charged slugs that punch through a line", cr(7500, 13)),
    FLAK("Flak burst", "Shells that burst into shrapnel", co(35, 18)),
    STORM("Storm repeater", "Pulse bolts, much faster", cr(12000, 25), power = 0.9f, rate = 1.4f, look = "PULSE"),
    HAILSTORM("Hail cannon", "A heavier scatter fan", cr(18000, 30), power = 1.3f, rate = 0.92f, look = "SPREAD"),
    PRISM("Prism lance", "A hotter, wider beam", cr(26000, 36), power = 1.35f, look = "LASER"),
    SWARM("Swarm rack", "Seekers in great flocks", sh(100, 32), power = 1.1f, rate = 1.3f, look = "HOMING"),
    NEUTRON("Neutron wave", "Plasma orbs that crush armour", co(110, 46), power = 1.5f, look = "PLASMA"),
    GAUSS("Gauss lance", "The bounty hunter's rail, tuned to kill", STORY, power = 1.55f, rate = 1.15f, look = "RAIL"),
    STARFALL("Starfall", "Flak that rains like meteors", STORY, power = 1.45f, rate = 1.2f, look = "FLAK");

    val base: Primary get() = look?.let { valueOf(it) } ?: this
    override val slot get() = Slot.PRIMARY
}

enum class Support(
    override val title: String, override val desc: String, override val price: Price,
    val power: Float = 1f, private val look: String? = null,
) : Part {
    NONE("None", "Nothing extra", FREE),
    DRONES("Wing drones", "Two drones that fly beside you and fire", cr(1400, 3)),
    MISSILES("Missile pod", "Homing missiles every second or so", cr(2800, 6)),
    ORBITALS("Orbital shields", "Orbs that circle you and eat bullets", cr(4500, 9)),
    TESLA("Tesla coil", "Chains lightning between nearby enemies", co(25, 15)),
    SENTINELS("Sentinel drones", "Drones with heavier guns", cr(16000, 28), power = 1.4f, look = "DRONES"),
    HIVE("Hive pod", "Bigger missiles, more often", cr(24000, 34), power = 1.35f, look = "MISSILES"),
    BASTION("Bastion ring", "More orbs in a wider ring", sh(90, 38), power = 1.3f, look = "ORBITALS"),
    STORM_LATTICE("Storm lattice", "Lightning that jumps further and hits harder", co(100, 48), power = 1.45f, look = "TESLA"),
    GUARDIAN("Guardian wing", "Salvaged drones that hit like gunships", STORY, power = 1.9f, look = "DRONES");

    val base: Support get() = look?.let { valueOf(it) } ?: this
    override val slot get() = Slot.SUPPORT
}

/** The big button. Charges from kills and grazes. */
enum class Special(
    override val title: String, override val desc: String, override val price: Price, val seconds: Float,
    val power: Float = 1f, private val look: String? = null,
) : Part {
    NOVA("Nova bomb", "Wipes every bullet off the screen and blasts everything", FREE, 0f),
    OVERDRIVE("Overdrive", "Triple fire rate and invulnerable for a moment", cr(2000, 4), 5f),
    PHASE("Phase shift", "Slows time and lets bullets pass through you", cr(3600, 7), 4f),
    SINGULARITY("Singularity", "A black hole that swallows bullets and crushes ships", cr(6000, 11), 5f),
    BARRAGE("Barrage", "A swarm of missiles at every enemy on screen", co(30, 16), 0f),
    SUPERNOVA("Supernova", "A nova bomb with twice the blast", cr(22000, 30), 0f, power = 2f, look = "NOVA"),
    TIME_WARP("Time warp", "A longer, deeper phase shift", cr(30000, 38), 6.5f, look = "PHASE"),
    BERSERK("Berserk", "Overdrive that lasts and lasts", sh(110, 36), 8f, look = "OVERDRIVE"),
    EVENT_HORIZON("Event horizon", "A bigger, hungrier singularity", co(120, 50), 7.5f, power = 1.6f, look = "SINGULARITY"),
    ARMAGEDDON("Armageddon", "A barrage that darkens the sky", STORY, 0f, power = 1.8f, look = "BARRAGE");

    val base: Special get() = look?.let { valueOf(it) } ?: this
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
    Swatch("cobalt", "Cobalt", 0xFF2449C9, cr(300, 2)),
    Swatch("azure", "Azure", 0xFF2A8CFF, cr(350, 2)),
    Swatch("cerulean", "Cerulean", 0xFF3FA9E0, cr(400, 3)),
    Swatch("steel", "Steel blue", 0xFF4A6E92, cr(450, 4)),
    Swatch("midnight", "Midnight", 0xFF141A3A, cr(500, 5)),
    Swatch("glacier", "Glacier", 0xFF9FDBEA, cr(600, 5)),
    Swatch("aqua", "Aqua", 0xFF3BF0E0, cr(650, 6)),
    Swatch("lagoon", "Lagoon", 0xFF0F7F86, cr(700, 7)),
    Swatch("jade", "Jade", 0xFF1FA86A, cr(750, 8)),
    Swatch("emerald", "Emerald", 0xFF0FC46A, cr(800, 8)),
    Swatch("forest", "Forest", 0xFF1E4A2A, cr(900, 9)),
    Swatch("moss", "Moss", 0xFF5A7A2E, cr(950, 10)),
    Swatch("chartreuse", "Chartreuse", 0xFFC8FF2A, cr(1000, 11)),
    Swatch("pistachio", "Pistachio", 0xFFB6E08A, cr(1050, 12)),
    Swatch("sage", "Sage", 0xFF8AA68A, cr(1100, 12)),
    Swatch("seafoam", "Seafoam", 0xFF8FFFD8, cr(1200, 13)),
    Swatch("amber", "Amber", 0xFFFFA81F, cr(1250, 14)),
    Swatch("honey", "Honey", 0xFFE6B34A, cr(1300, 15)),
    Swatch("mustard", "Mustard", 0xFFC9A11A, cr(1350, 15)),
    Swatch("lemon", "Lemon", 0xFFFFF35A, cr(1400, 16)),
    Swatch("tangerine", "Tangerine", 0xFFFF9033, cr(1500, 17)),
    Swatch("copper", "Copper", 0xFFB8693A, cr(1550, 18)),
    Swatch("bronze", "Bronze", 0xFF8C6A3A, cr(1600, 18)),
    Swatch("brass", "Brass", 0xFFC2A25A, cr(1650, 19)),
    Swatch("scarlet", "Scarlet", 0xFFFF2A2A, cr(1700, 20)),
    Swatch("ruby", "Ruby", 0xFFC0103A, cr(1800, 21)),
    Swatch("cherry", "Cherry", 0xFFD4204A, cr(1850, 22)),
    Swatch("wine", "Wine", 0xFF5A1028, cr(1900, 22)),
    Swatch("coral", "Coral", 0xFFFF6F5E, cr(1950, 23)),
    Swatch("salmon", "Salmon", 0xFFFF9A84, cr(2000, 24)),
    Swatch("peach", "Peach", 0xFFFFC4A0, cr(2100, 25)),
    Swatch("blush", "Blush", 0xFFF2B8C6, cr(2150, 25)),
    Swatch("magenta", "Magenta", 0xFFFF2AD4, cr(2200, 26)),
    Swatch("fuchsia", "Fuchsia", 0xFFE040FF, cr(2250, 27)),
    Swatch("orchid", "Orchid", 0xFFC873E6, cr(2300, 28)),
    Swatch("lilac", "Lilac", 0xFFC9A8FF, cr(2400, 28)),
    Swatch("lavender", "Lavender", 0xFFB8B0F0, cr(2450, 29)),
    Swatch("amethyst", "Amethyst", 0xFF8A4FD8, cr(2500, 30)),
    Swatch("indigo", "Indigo", 0xFF3A1F9A, cr(2550, 31)),
    Swatch("grape", "Grape", 0xFF5A2A7A, cr(2600, 32)),
    Swatch("bone", "Bone", 0xFFE8DCC0, cr(2700, 32)),
    Swatch("sand", "Sand", 0xFFD6BE8A, cr(2750, 33)),
    Swatch("khaki", "Khaki", 0xFFA8986A, cr(2800, 34)),
    Swatch("mocha", "Mocha", 0xFF6A4A3A, cr(2850, 35)),
    Swatch("chocolate", "Chocolate", 0xFF4A2A1A, cr(2900, 35)),
    Swatch("charcoal", "Charcoal", 0xFF22242C, cr(3000, 36)),
    Swatch("slate", "Slate", 0xFF5A6070, cr(3050, 37)),
    Swatch("silver", "Silver", 0xFFB8BCC8, cr(3100, 38)),
    Swatch("ash", "Ash", 0xFF8A8A8E, cr(3150, 38)),
    Swatch("smoke", "Smoke", 0xFF6A6E7A, cr(3200, 39)),
    Swatch("gunmetal", "Gunmetal", 0xFF2E3440, cr(3300, 40)),
    Swatch("titanium", "Titanium", 0xFF9AA0A8, cr(3350, 41)),
    Swatch("neon_green", "Neon green", 0xFF39FF14, co(10, 20)),
    Swatch("neon_pink", "Neon pink", 0xFFFF10F0, co(12, 22)),
    Swatch("neon_blue", "Neon blue", 0xFF1F51FF, co(14, 24)),
    Swatch("neon_orange", "Neon orange", 0xFFFF5F1F, co(16, 26)),
    Swatch("plasma", "Plasma", 0xFFFF3AA0, co(18, 28)),
    Swatch("laser", "Laser red", 0xFFFF0838, co(20, 30)),
    Swatch("electric", "Electric", 0xFF7DF9FF, co(22, 32)),
    Swatch("radium", "Radium", 0xFFAFFF5A, co(24, 34)),
    Swatch("nebula", "Nebula", 0xFF9A3AE0, co(26, 36)),
    Swatch("quasar", "Quasar", 0xFFE0E8FF, co(28, 38)),
    Swatch("pulsar", "Pulsar", 0xFF50D0FF, co(30, 40)),
    Swatch("redshift", "Redshift", 0xFFB8202A, co(32, 42)),
    Swatch("blueshift", "Blueshift", 0xFF2A40E8, sh(15, 12)),
    Swatch("aurora", "Aurora", 0xFF3AFFB0, sh(20, 15)),
    Swatch("solar", "Solar flare", 0xFFFFB020, sh(25, 18)),
    Swatch("comet", "Comet", 0xFFC8F0FF, sh(30, 21)),
    Swatch("abyss", "Abyss", 0xFF05070E, sh(35, 24)),
    Swatch("eventide", "Eventide", 0xFF2A1A4A, sh(40, 27)),
    Swatch("darkmatter", "Dark matter", 0xFF120818, sh(45, 30)),
    Swatch("starlight", "Starlight", 0xFFFFF8E0, sh(50, 33)),
    Swatch("ember", "Ember", 0xFFFF4A10, co(40, 50)),
    Swatch("frostbite", "Frostbite", 0xFFA0E8FF, co(50, 58)),
    Swatch("venom", "Venom", 0xFF7AE01A, STORY),
    Swatch("royal", "Royal purple", 0xFF6A1AB8, STORY),
)

/** How the paint catches the light (the shader's look for the hull). */
enum class Finish(val title: String, val desc: String, val price: Price) {
    MATTE("Matte", "Flat and stealthy", FREE),
    GLOSS("Gloss", "A clean shine", FREE),
    METAL("Metallic", "Brushed metal", cr(800, 3)),
    CHROME("Chrome", "Mirror bright", cr(2500, 7)),
    HOLO("Holo", "Shifts colour as it turns", co(15, 11)),
    VOID("Void", "Starlight swirling under the paint", co(40, 18)),
    PEARL("Pearl", "A soft sheen that shifts with the angle", cr(6000, 15)),
    CARBON("Carbon", "Woven black fibre", cr(9000, 21)),
    NEON("Neon", "Glowing lines over dark paint", sh(40, 18)),
    MAGMA("Magma", "A cooling crust over molten cracks", co(60, 33)),
    FROST("Frost", "Ice crystals over polished paint", STORY),
    PRISM("Prism", "Rainbow bands that slide across the hull", STORY);

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
    CIRCUIT("Circuit", co(10, 13)),
    HEX("Honeycomb", cr(2400, 14)),
    WAVES("Waves", cr(3200, 18)),
    DOTS("Polka", cr(4000, 22)),
    BOLT("Lightning", co(25, 26)),
    SCALES("Scales", sh(30, 20)),
    FLAMES("Flames", sh(45, 30)),
    STARS("Starfield", STORY),
    RUNES("Runes", STORY);

    val key get() = "decal:$name"
}

/** The cosmetic families beyond paint: each fitted one is saved in the [Paint]. */
enum class Look(val title: String) {
    TRAIL("Trail"), SHOT("Shots"), BURST("Bursts"), TITLE("Title"), EMBLEM("Emblem");
}

/**
 * A trail, shot colour, kill burst, pilot title or emblem. [argb] 0 means "use the ship's glow colour"; [style] picks
 * the effect (trails: 0 soft, 1 sparks, 2 rainbow, 3 twin, 4 rings, 5 stardust, 6 flame; shots: 0 tint, 1 shimmer;
 * bursts: 0 plain, 1 shockwave, 2 shards, 3 confetti, 4 implosion). An emblem's [id] is its glyph.
 */
data class Cosmetic(val kind: Look, val id: String, val title: String, val argb: Long = 0, val style: Int = 0, val price: Price = FREE) {
    val key get() = "${kind.name.lowercase()}:$id"
}

private fun c(kind: Look, id: String, title: String, argb: Long, style: Int, price: Price) = Cosmetic(kind, id, title, argb, style, price)

val COSMETICS: List<Cosmetic> = listOf(
    c(Look.TRAIL, "none", "Standard", 0x00000000, 0, FREE),
    c(Look.TRAIL, "ember", "Ember", 0xFFFF6A1F, 0, cr(1400, 4)),
    c(Look.TRAIL, "frost", "Frost", 0xFFA0E8FF, 0, cr(2200, 6)),
    c(Look.TRAIL, "toxic", "Toxic", 0xFF6BFF3A, 0, cr(3100, 8)),
    c(Look.TRAIL, "rose", "Rose", 0xFFFF6FB0, 0, cr(3900, 10)),
    c(Look.TRAIL, "gold", "Gilded", 0xFFFFD050, 0, cr(4700, 12)),
    c(Look.TRAIL, "ghost", "Ghostly", 0xFFE8F0FF, 0, cr(5600, 14)),
    c(Look.TRAIL, "blood", "Blood", 0xFFC0102A, 0, cr(6400, 16)),
    c(Look.TRAIL, "violet", "Violet", 0xFFA970FF, 0, cr(7200, 18)),
    c(Look.TRAIL, "ocean", "Ocean", 0xFF2A8CFF, 0, cr(8100, 20)),
    c(Look.TRAIL, "sparks", "Sparks", 0x00000000, 1, cr(8900, 22)),
    c(Look.TRAIL, "firework", "Fireworks", 0xFFFFC23D, 1, cr(9700, 24)),
    c(Look.TRAIL, "static", "Static", 0xFFB8E8FF, 1, cr(10600, 26)),
    c(Look.TRAIL, "cinders", "Cinders", 0xFFFF4A10, 1, cr(11400, 28)),
    c(Look.TRAIL, "glitter", "Glitter", 0xFFFFF8E0, 1, cr(12200, 30)),
    c(Look.TRAIL, "twin", "Twin stream", 0x00000000, 3, cr(13100, 32)),
    c(Look.TRAIL, "helix", "Helix", 0xFF22E3FF, 3, cr(13900, 34)),
    c(Look.TRAIL, "ribbon", "Ribbon", 0xFFFF4FB8, 3, cr(14700, 36)),
    c(Look.TRAIL, "dna", "Double helix", 0xFF6BFFC8, 3, co(10, 45)),
    c(Look.TRAIL, "pulse", "Pulse rings", 0x00000000, 4, co(19, 46)),
    c(Look.TRAIL, "sonar", "Sonar", 0xFF50D0FF, 4, co(29, 48)),
    c(Look.TRAIL, "halo", "Halo rings", 0xFFFFE45C, 4, co(39, 50)),
    c(Look.TRAIL, "stardust", "Stardust", 0xFFE0E8FF, 5, co(49, 51)),
    c(Look.TRAIL, "comet", "Comet tail", 0xFFC8F0FF, 5, co(60, 53)),
    c(Look.TRAIL, "nebula", "Nebula", 0xFF9A3AE0, 5, sh(20, 42)),
    c(Look.TRAIL, "rainbow", "Rainbow", 0x00000000, 2, sh(33, 43)),
    c(Look.TRAIL, "prism", "Prism", 0x00000000, 2, sh(46, 44)),
    c(Look.TRAIL, "aurora", "Aurora", 0xFF3AFFB0, 2, sh(59, 46)),
    c(Look.TRAIL, "phoenix", "Phoenix fire", 0xFFFF8020, 6, STORY),
    c(Look.TRAIL, "voidwake", "Void wake", 0xFF7B2CFF, 6, STORY),
    c(Look.SHOT, "none", "Standard", 0x00000000, 0, FREE),
    c(Look.SHOT, "crimson", "Crimson", 0xFFFF3050, 0, cr(1800, 5)),
    c(Look.SHOT, "lime", "Lime", 0xFF9BFF3A, 0, cr(3100, 8)),
    c(Look.SHOT, "gold", "Gold", 0xFFFFC23D, 0, cr(4300, 11)),
    c(Look.SHOT, "pink", "Hot pink", 0xFFFF4FB8, 0, cr(5600, 14)),
    c(Look.SHOT, "ice", "Ice", 0xFFBDF4FF, 0, cr(6800, 17)),
    c(Look.SHOT, "orange", "Orange", 0xFFFF7A1F, 0, cr(8100, 20)),
    c(Look.SHOT, "violet", "Violet", 0xFFA970FF, 0, cr(9300, 23)),
    c(Look.SHOT, "white", "White hot", 0xFFFFFFFF, 0, cr(10600, 26)),
    c(Look.SHOT, "teal", "Teal", 0xFF14E3C0, 0, cr(11800, 29)),
    c(Look.SHOT, "blue", "Deep blue", 0xFF3A6AFF, 0, cr(13100, 32)),
    c(Look.SHOT, "toxic", "Toxic", 0xFF4DFF5A, 0, cr(14300, 35)),
    c(Look.SHOT, "ruby", "Ruby", 0xFFE0103A, 0, co(10, 45)),
    c(Look.SHOT, "amber", "Amber", 0xFFFFA81F, 0, co(25, 47)),
    c(Look.SHOT, "lilac", "Lilac", 0xFFC9A8FF, 0, co(39, 50)),
    c(Look.SHOT, "electric", "Electric", 0xFF7DF9FF, 0, co(55, 52)),
    c(Look.SHOT, "blood", "Blood", 0xFFA00A1E, 0, sh(20, 42)),
    c(Look.SHOT, "sun", "Sunfire", 0xFFFFF35A, 0, sh(39, 44)),
    c(Look.SHOT, "rainbow", "Rainbow", 0x00000000, 1, sh(59, 46)),
    c(Look.SHOT, "void", "Void", 0xFF7B2CFF, 1, sh(79, 48)),
    c(Look.BURST, "none", "Standard", 0x00000000, 0, FREE),
    c(Look.BURST, "ring", "Shockwave", 0x00000000, 1, cr(2200, 6)),
    c(Look.BURST, "gold", "Gold rush", 0xFFFFD050, 0, cr(3900, 10)),
    c(Look.BURST, "frost", "Shatter", 0xFFA0E8FF, 2, cr(5600, 14)),
    c(Look.BURST, "toxic", "Toxic cloud", 0xFF6BFF3A, 0, cr(7200, 18)),
    c(Look.BURST, "ember", "Inferno", 0xFFFF6A1F, 2, cr(8900, 22)),
    c(Look.BURST, "pink", "Confetti", 0xFFFF4FB8, 3, cr(10600, 26)),
    c(Look.BURST, "violet", "Void pop", 0xFFA970FF, 1, cr(12200, 30)),
    c(Look.BURST, "white", "Supernova", 0xFFFFFFFF, 1, cr(13900, 34)),
    c(Look.BURST, "ocean", "Splash", 0xFF2A8CFF, 2, co(10, 45)),
    c(Look.BURST, "electric", "Overload", 0xFF7DF9FF, 1, co(29, 48)),
    c(Look.BURST, "rainbow", "Rainbow", 0x00000000, 3, co(49, 51)),
    c(Look.BURST, "blood", "Crimson rain", 0xFFC0102A, 2, sh(20, 42)),
    c(Look.BURST, "implode", "Implosion", 0xFF7B2CFF, 4, STORY),
    c(Look.BURST, "star", "Starburst", 0xFFFFF8E0, 4, STORY),
    c(Look.TITLE, "rookie", "Rookie", 0, 0, FREE),
    c(Look.TITLE, "pilot", "Pilot", 0, 0, cr(1300, 3)),
    c(Look.TITLE, "wingman", "Wingman", 0, 0, cr(2000, 5)),
    c(Look.TITLE, "ace", "Ace", 0, 0, cr(2700, 7)),
    c(Look.TITLE, "gunner", "Gunner", 0, 0, cr(3400, 8)),
    c(Look.TITLE, "ranger", "Ranger", 0, 0, cr(4100, 10)),
    c(Look.TITLE, "scout", "Scout", 0, 0, cr(4800, 12)),
    c(Look.TITLE, "raider", "Raider", 0, 0, cr(5600, 14)),
    c(Look.TITLE, "corsair", "Corsair", 0, 0, cr(6300, 15)),
    c(Look.TITLE, "hunter", "Hunter", 0, 0, cr(7000, 17)),
    c(Look.TITLE, "marauder", "Marauder", 0, 0, cr(7700, 19)),
    c(Look.TITLE, "vanguard", "Vanguard", 0, 0, cr(8400, 20)),
    c(Look.TITLE, "sentinel", "Sentinel", 0, 0, cr(9100, 22)),
    c(Look.TITLE, "warden", "Warden", 0, 0, cr(9800, 24)),
    c(Look.TITLE, "outrider", "Outrider", 0, 0, cr(10600, 26)),
    c(Look.TITLE, "stormchaser", "Stormchaser", 0, 0, cr(11300, 27)),
    c(Look.TITLE, "rockbreaker", "Rockbreaker", 0, 0, cr(12000, 29)),
    c(Look.TITLE, "bullet_dancer", "Bullet dancer", 0, 0, cr(12700, 31)),
    c(Look.TITLE, "graze_master", "Graze master", 0, 0, cr(13400, 32)),
    c(Look.TITLE, "starborn", "Starborn", 0, 0, cr(14100, 34)),
    c(Look.TITLE, "void_walker", "Void walker", 0, 0, cr(14800, 36)),
    c(Look.TITLE, "sky_knight", "Sky knight", 0, 0, co(10, 45)),
    c(Look.TITLE, "night_hawk", "Night hawk", 0, 0, co(18, 46)),
    c(Look.TITLE, "iron_wing", "Iron wing", 0, 0, co(27, 47)),
    c(Look.TITLE, "comet_rider", "Comet rider", 0, 0, co(35, 49)),
    c(Look.TITLE, "red_baron", "Red baron", 0, 0, co(44, 50)),
    c(Look.TITLE, "nebula_drifter", "Nebula drifter", 0, 0, co(52, 52)),
    c(Look.TITLE, "ghost_pilot", "Ghost pilot", 0, 0, co(61, 53)),
    c(Look.TITLE, "dread_captain", "Dread captain", 0, 0, sh(20, 42)),
    c(Look.TITLE, "fleet_admiral", "Fleet admiral", 0, 0, sh(31, 43)),
    c(Look.TITLE, "wraith", "Wraith", 0, 0, sh(42, 44)),
    c(Look.TITLE, "legend", "Legend", 0, 0, sh(54, 45)),
    c(Look.TITLE, "starbreaker", "Starbreaker", 0, 0, sh(60, 40)),
    c(Look.TITLE, "heartbreaker", "Heartbreaker", 0, 0, STORY),
    c(Look.TITLE, "the_last_light", "The Last Light", 0, 0, STORY),
    c(Look.EMBLEM, "none", "None", 0, 0, FREE),
    c(Look.EMBLEM, "star", "Star", 0, 0, cr(1800, 5)),
    c(Look.EMBLEM, "bolt", "Bolt", 0, 0, cr(3100, 8)),
    c(Look.EMBLEM, "skull", "Skull", 0, 0, cr(4300, 11)),
    c(Look.EMBLEM, "shield", "Shield", 0, 0, cr(5600, 14)),
    c(Look.EMBLEM, "fire", "Flame", 0, 0, cr(6800, 17)),
    c(Look.EMBLEM, "target", "Target", 0, 0, cr(8100, 20)),
    c(Look.EMBLEM, "ghost", "Ghost", 0, 0, cr(9300, 23)),
    c(Look.EMBLEM, "heart", "Heart", 0, 0, cr(10600, 26)),
    c(Look.EMBLEM, "rift", "Rift", 0, 0, cr(11800, 29)),
    c(Look.EMBLEM, "level", "Chevrons", 0, 0, cr(13100, 32)),
    c(Look.EMBLEM, "rock", "Rock", 0, 0, cr(14300, 35)),
    c(Look.EMBLEM, "crown", "Crown", 0, 0, co(10, 45)),
    c(Look.EMBLEM, "moon", "Moon", 0, 0, co(25, 47)),
    c(Look.EMBLEM, "eye", "Eye", 0, 0, co(39, 50)),
    c(Look.EMBLEM, "diamond", "Diamond", 0, 0, co(55, 52)),
    c(Look.EMBLEM, "atom", "Atom", 0, 0, sh(20, 42)),
    c(Look.EMBLEM, "spiral", "Spiral", 0, 0, sh(39, 44)),
    c(Look.EMBLEM, "sword", "Sword", 0, 0, sh(59, 46)),
    c(Look.EMBLEM, "wing", "Wing", 0, 0, sh(79, 48)),
)

fun cosmetic(kind: Look, id: String) = COSMETICS.firstOrNull { it.kind == kind && it.id == id } ?: COSMETICS.first { it.kind == kind }
fun cosmetics(kind: Look) = COSMETICS.filter { it.kind == kind }

data class Paint(
    val primary: String = "graphite",
    val secondary: String = "violet",
    val glow: String = "cyan",
    val finish: Finish = Finish.GLOSS,
    val decal: Decal = Decal.STRIPE,
    val trail: String = "none",
    val shot: String = "none",
    val burst: String = "none",
    val title: String = "rookie",
    val emblem: String = "none",
) {
    fun id(kind: Look) = when (kind) { Look.TRAIL -> trail; Look.SHOT -> shot; Look.BURST -> burst; Look.TITLE -> title; Look.EMBLEM -> emblem }
    fun with(c: Cosmetic) = when (c.kind) {
        Look.TRAIL -> copy(trail = c.id); Look.SHOT -> copy(shot = c.id); Look.BURST -> copy(burst = c.id)
        Look.TITLE -> copy(title = c.id); Look.EMBLEM -> copy(emblem = c.id)
    }
    fun look(kind: Look) = cosmetic(kind, id(kind))
}

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
    /** A second gun to swap to in flight (null: none fitted). Never the same as [primary]. */
    val backup: Primary? = null,
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
        is Primary -> copy(primary = p, backup = if (backup == p) primary else backup)
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
            backupLevel = backup?.let(up) ?: 1,
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
    val backupLevel: Int,
    val supportLevel: Int,
    val specialLevel: Int,
)

const val MAX_UPGRADE = 10

private val UPGRADE_STEPS = floatArrayOf(1f, 2.2f, 4f, 7f, 11f, 16f, 23f, 32f, 45f)

/** Credits to take [p] from [level] to level + 1 (null when it's maxed or can't be upgraded). */
fun upgradeCost(p: Part, level: Int): Int? {
    if (!p.slot.upgradable || level >= MAX_UPGRADE || p == Support.NONE) return null
    val base = when (p.price.currency) {
        Currency.CORES -> 900 + p.price.amount * 40
        Currency.CREDITS -> 300 + p.price.amount / 4
        Currency.SHARDS -> 1200 + p.price.amount * 30
        Currency.REWARD -> 8000
    }
    return (base * UPGRADE_STEPS[level - 1]).toInt() / 10 * 10
}
