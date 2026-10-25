package dev.m4s4t0.voidwars.model

/** What a hub has around it besides its stations; the renderer draws each its own way. */
enum class Scenery { HAVEN, BELT, SCRAP, STORM, WRECKS, RIFT, AURORA, EMBERS, HIVE, FOG, RINGS, SUN, PHANTOM, FLEET, CRYSTALS, GAS, HOLE, ARMADA, DUSK, SHARDS, SINGULARITY }

/**
 * One of the hubs the pilot flies around between missions. The first is home; each campaign sector beaten moves the
 * operation one hub further across the galaxy. They differ only in looks: the sky, a planet (big and low for the
 * orbital ones), the colours of the stations, and the [scenery] and rocks around them.
 *
 * [planetX] is a fraction of the screen's half-width, [planetY] of its half-height; [planetSize] is the planet's radius
 * in half-heights. [rocks] drift around the hub, in a ring outside the stations or, if [field], scattered all through it.
 */
class HubTheme(
    val title: String, val place: String, val scenery: Scenery,
    val nebulaA: Long, val nebulaB: Long, val planet: Long,
    val planetX: Float, val planetY: Float, val planetSize: Float,
    val hull: Long, val trim: Long, val glow: Long,
    val rock: Long, val dust: Long,
    val rocks: Int = 14, val field: Boolean = false, val traffic: Int = 8,
) {
    /** The sector whose boss opens this hub, or -1 for home. */
    val sector get() = Hubs.all.indexOf(this) - 1
}

object Hubs {
    val all = listOf(
        HubTheme("Haven", "Home port, at the edge of charted space", Scenery.HAVEN,
            0xFF24206A, 0xFF0B4A66, 0xFF5A8CFF, 0.55f, 0.35f, 0.5f,
            0xFF2E3448, 0xFF4A3A7A, 0xFF7CE0FF, 0xFF6B5E58, 0xFF9AD8FF),
        HubTheme("Rimward Belt", "An outpost hollowed out of an asteroid belt", Scenery.BELT,
            0xFF3A2410, 0xFF1A2A3A, 0xFFB08A5A, -0.7f, 0.55f, 0.3f,
            0xFF4A4038, 0xFF8A6A3A, 0xFFFFC23D, 0xFF6E5E4E, 0xFFFFD8A0, rocks = 70, field = true),
        HubTheme("Redline Yard", "A pirate scrapyard in the red dust", Scenery.SCRAP,
            0xFF6A1018, 0xFF3A1A10, 0xFFFF5A3A, 0.6f, -0.5f, 0.45f,
            0xFF4A2E2A, 0xFF8A2A1A, 0xFFFF5A3A, 0xFF6E4A44, 0xFFFF9A8A, rocks = 24, traffic = 12),
        HubTheme("Stormeye", "Anchored in the calm eye of an ion storm", Scenery.STORM,
            0xFF10306A, 0xFF4A1A80, 0xFF7CE0FF, 0.2f, 0.75f, 0.35f,
            0xFF203A5A, 0xFF5A7AC0, 0xFFA0E8FF, 0xFF4E5A6E, 0xFFA0E8FF, rocks = 10),
        HubTheme("Graveyard Watch", "Among the hulks of a fleet that never came home", Scenery.WRECKS,
            0xFF102A20, 0xFF1A1A2A, 0xFF5AAA7A, -0.55f, -0.45f, 0.4f,
            0xFF2A302E, 0xFF3E4A3A, 0xFF7BFF9A, 0xFF4A4A52, 0xFF9AFFB8, rocks = 12, traffic = 4),
        HubTheme("Heartscar", "On the lip of the rift where the Heart fell", Scenery.RIFT,
            0xFF3A0A5A, 0xFF0A0A2A, 0xFFB040FF, 0f, 0.6f, 0.25f,
            0xFF1A0E2E, 0xFF3A1E6A, 0xFFC070FF, 0xFF3A2E52, 0xFFC9A8FF, rocks = 18, traffic = 5),
        HubTheme("Frostline Orbit", "Low orbit over a frozen world", Scenery.AURORA,
            0xFF0A3A5A, 0xFF1A5A4A, 0xFFB8E0FF, 0.1f, -2.35f, 1.9f,
            0xFFB8C8D8, 0xFF4A7AA0, 0xFFA0E8FF, 0xFF8AA8C0, 0xFFE0F4FF, rocks = 20),
        HubTheme("Cinder Station", "In the glare of a dying red star", Scenery.EMBERS,
            0xFF5A1A08, 0xFF2A0A04, 0xFFFF4A1A, -0.6f, 0.45f, 0.85f,
            0xFF3A2218, 0xFFE0603A, 0xFFFFA81F, 0xFF5A3A2A, 0xFFFFB070, rocks = 16),
        HubTheme("Hive Hollow", "Grown, not built, inside a dead hive", Scenery.HIVE,
            0xFF2A4A08, 0xFF0A2A1A, 0xFFC8FF2A, 0.65f, 0.6f, 0.3f,
            0xFF2A3A10, 0xFF9AC020, 0xFFC8FF2A, 0xFF4A5A2A, 0xFFE0FF9A, rocks = 22, traffic = 14),
        HubTheme("Glasswater", "Lost in the mirror fog", Scenery.FOG,
            0xFF6A6A8A, 0xFF3A4A6A, 0xFFE8E8FF, -0.3f, 0.5f, 0.4f,
            0xFFD8D8E8, 0xFF8A8AAA, 0xFFFFFFFF, 0xFF8A8A9A, 0xFFFFFFFF, rocks = 8),
        HubTheme("Moonfall Ring", "Riding the rings of a shattered moon", Scenery.RINGS,
            0xFF3A2A1A, 0xFF1A1A3A, 0xFFD0A070, 0.75f, 0.9f, 0.75f,
            0xFF6A5A4A, 0xFF3A2A1A, 0xFFFF7A1F, 0xFF7A6A5A, 0xFFFFE0B0, rocks = 60),
        HubTheme("Forgelight", "Skimming the corona of a star", Scenery.SUN,
            0xFF8A4A08, 0xFF5A2A04, 0xFFFFE070, 0f, 1.75f, 1.2f,
            0xFF8A5A10, 0xFFFFC23D, 0xFFFFF35A, 0xFF6A4A2A, 0xFFFFF0A0, rocks = 10),
        HubTheme("Driftlight", "A ghost port that flickers in and out", Scenery.PHANTOM,
            0xFF1A1A3A, 0xFF2A1A4A, 0xFF8A7ACC, 0.5f, -0.6f, 0.35f,
            0xFF1A1A2A, 0xFF5A4A8A, 0xFFC9A8FF, 0xFF3A3A4A, 0xFFC9A8FF, rocks = 12, traffic = 6),
        HubTheme("Choirgate Yards", "The dockyards where the Choir was built", Scenery.FLEET,
            0xFF2A2A3A, 0xFF3A1A0A, 0xFFAA8A6A, -0.65f, -0.5f, 0.5f,
            0xFF4A4E5E, 0xFF8A6A2A, 0xFFFFB03A, 0xFF4A4A52, 0xFFFFD090, rocks = 6, traffic = 18),
        HubTheme("Maw Geode", "Deep in a hollow of living crystal", Scenery.CRYSTALS,
            0xFF0A4A5A, 0xFF4A0A5A, 0xFF5AFFE0, 0.4f, 0.7f, 0.25f,
            0xFF1A3A4A, 0xFF2AB8C0, 0xFF5AFFE0, 0xFF2A8AA0, 0xFFA0FFF0, rocks = 30, field = true),
        HubTheme("Thronefall Orbit", "Low orbit over a storming gas giant", Scenery.GAS,
            0xFF3A1A4A, 0xFF1A2A5A, 0xFFD08AFF, -0.2f, -2.5f, 2.1f,
            0xFF2A2A4A, 0xFF8A4AC0, 0xFFE0A0FF, 0xFF4A3A5A, 0xFFE0C0FF, rocks = 10),
        HubTheme("Event Horizon", "Parked a careful distance from a black hole", Scenery.HOLE,
            0xFF2A0A3A, 0xFF0A1A3A, 0xFFFF9A3A, -0.75f, -0.7f, 0.18f,
            0xFF2A2A3A, 0xFF6A3A1A, 0xFFFF9A3A, 0xFF3A3040, 0xFFFFC090, rocks = 26),
        HubTheme("Silent Moorings", "Moored among the armada's dark carriers", Scenery.ARMADA,
            0xFF0A0A1A, 0xFF1A0A1A, 0xFF4A4A6A, 0.6f, 0.55f, 0.3f,
            0xFF1A1A22, 0xFF2A2A3A, 0xFFFF3D6A, 0xFF2A2A30, 0xFF8A8AAA, rocks = 8, traffic = 3),
        HubTheme("Duskward Orbit", "Low orbit, where it's always sunset", Scenery.DUSK,
            0xFF6A2A3A, 0xFF2A1A5A, 0xFFFF8A5A, 0.15f, -2.3f, 1.85f,
            0xFF3A2A3A, 0xFFE0705A, 0xFFFFB070, 0xFF5A3A40, 0xFFFFC0A0, rocks = 12),
        HubTheme("Shardfall", "Afloat on a sea of glittering shards", Scenery.SHARDS,
            0xFF1A3A6A, 0xFF3A1A5A, 0xFF8AC8FF, -0.45f, 0.65f, 0.35f,
            0xFF2A3A5A, 0xFF7AB0FF, 0xFFB8E0FF, 0xFF5A7AA0, 0xFFE0F0FF, rocks = 54, field = true),
        HubTheme("The Last Light", "At the edge of everything", Scenery.SINGULARITY,
            0xFF2A0A2A, 0xFF0A0A3A, 0xFFFFFFFF, 0.7f, -0.7f, 0.12f,
            0xFF0E0E16, 0xFF3A1E6A, 0xFFFFFFFF, 0xFF2A2A34, 0xFFFFFFFF, rocks = 20),
    )

    init { check(all.size == Campaign.SECTORS.size + 1) }
}
