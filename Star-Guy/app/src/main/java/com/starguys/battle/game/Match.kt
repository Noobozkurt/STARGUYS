package com.starguys.battle.game

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import com.starguys.battle.core.G
import com.starguys.battle.core.GameApp
import com.starguys.battle.core.Save
import com.starguys.battle.core.Sfx
import com.starguys.battle.core.ang
import com.starguys.battle.core.angleDiff
import com.starguys.battle.core.clamp
import com.starguys.battle.core.cosf
import com.starguys.battle.core.dist
import com.starguys.battle.core.floorInt
import com.starguys.battle.core.len
import com.starguys.battle.core.pick
import com.starguys.battle.core.rf
import com.starguys.battle.core.ri
import com.starguys.battle.core.sinf
import kotlin.math.abs
import com.starguys.battle.core.atan2

class MatchConfig(
    val mode: Int, val hero: Int, val biome: Biome?, val role: Int = 0,
    val creative: String? = null, val creativeKind: Int = 0, val seed: Long = System.nanoTime(),
    val difficulty: Int = Save.difficulty, val brSize: Int = Save.royaleSize,
    /** all humans (multiplayer); null in single player */
    val players: List<NetPlayer>? = null, val localId: Int = 0, val versus: Boolean = false
) {
    val net get() = players != null
}

class Ping(val x: Float, val y: Float, val team: Int, var life: Float = 4f)

class Match(val app: GameApp, val cfg: MatchConfig) {
    lateinit var map: TileMap
    lateinit var nav: Nav
    val fighters = ArrayList<Fighter>()
    val mobs = ArrayList<Mob>()
    val projs = ArrayList<Projectile>()
    val props = ArrayList<Prop>()
    val pickups = ArrayList<Pickup>()
    val vehicles = ArrayList<Vehicle>()
    val summons = ArrayList<Summon>()
    val zones = ArrayList<Zone>()
    val platforms = ArrayList<MovingPlatform>()
    val pings = ArrayList<Ping>()
    val hittables = ArrayList<Body>()
    val movers = ArrayList<Body>()
    private val drawList = ArrayList<Body>()
    private val solidGrid = HashMap<Int, ArrayList<Prop>>()
    private val targetsCache = ArrayList<Body>()

    val fx = Fx(this)
    val cam = Camera()
    var player: Fighter? = null
    lateinit var mode: GameMode
    lateinit var hud: Hud
    var biome = Biome.MEADOW
    var time = 0f
    var gravity = 1f
    var zeroG = false
    var slowMo = 1f
    private var slowT = 0f
    var over = false
    var won = false
    var resultTitle = ""
    var paused = false
    val envBody: Body
    var boss: Boss? = null
    var spectate: Fighter? = null
    var killer: Fighter? = null
    var killCamT = 0f
    var stormX = 0f; var stormY = 0f; var stormR = -1f
    var finishCb: (() -> Unit)? = null
    private val bgPaint = Paint()
    private val fogPaint = Paint()
    private var bgH = -1f
    var fogR = 0f
    var ended = false

    init {
        envBody = Body(this).apply { team = TEAM_NEUTRAL }
        cam.m = this
    }

    private val pendingHumans = ArrayList<NetPlayer>()
    private var hostTeam = 0

    fun start() {
        com.starguys.battle.core.seedSim(cfg.seed)
        cfg.players?.let { ps -> pendingHumans.addAll(ps.filter { it.id != 0 }.sortedBy { it.id }) }
        mode = com.starguys.battle.modes.Modes.create(cfg.mode, this)
        hud = Hud(this)
        mode.timeLeft = mode.timeLimit
        mode.build()
        if (!::map.isInitialized) map = TileMap(20, 20, biome)
        // remote humans that no mode slot accepted take over bots
        for (np in ArrayList(pendingHumans)) {
            val f = fighters.lastOrNull { !it.human && it.team != TEAM_MOBS } ?: break
            makeHuman(f, np, f.hero in Heroes.list)
        }
        pendingHumans.clear()
        player = fighters.firstOrNull { it.human && it.netId == cfg.localId } ?: player
        nav = Nav(map)
        map.onDestroyed = { tx, ty, tp -> onTileDestroyed(tx, ty, tp) }
        biome = map.biome
        gravity = if (biome.lowGravity) 0.42f else 1f
        fogR = if (biome.fog) 5.2f * T else 0f
        val p = player
        cam.targetZoom = app.h / (11.5f * T)
        if (p != null) cam.snap(p.x, p.y - 30f) else cam.snap(map.pxW / 2, map.pxH / 2)
        solidGrid.clear()
        for (pr in props) registerSolid(pr)
        Sfx.music(2)
    }

    // ------------------------------------------------------------------ spawning helpers
    fun addProj(p: Projectile) { projs.add(p) }
    fun addMob(mb: Mob) { mobs.add(mb); if (mb is Boss) boss = mb }
    fun addProp(p: Prop) { props.add(p); if (p is MovingPlatform) platforms.add(p); registerSolid(p) }
    fun addPickup(p: Pickup) { pickups.add(p) }
    fun addZone(z: Zone) { zones.add(z) }
    fun addSummon(s: Summon) { summons.add(s) }
    fun addVehicle(v: Vehicle) { vehicles.add(v) }

    private fun registerSolid(p: Prop) {
        if (p.solidR <= 0f) return
        val k = p.tileY() * 10000 + p.tileX()
        val l = solidGrid.getOrPut(k) { ArrayList() }
        if (!l.contains(p)) l.add(p)
    }

    fun spawnFighter(hero: HeroDef, name: String, team: Int, human: Boolean, x: Float, y: Float, skin: Int = 0, netId: Int = -1): Fighter {
        val f = Fighter(this, hero, name, team, human, skin)
        f.netId = netId
        f.x = x; f.y = y; f.z = maxOf(0f, map.groundAt(x, y)); f.safeX = x; f.safeY = y
        if (!human) f.brain = Bot(f)
        fighters.add(f)
        if (human && netId == cfg.localId) player = f
        return f
    }

    /** The host's fighter (player 0). In single player this is you. */
    fun spawnPlayer(team: Int, x: Float, y: Float, hero: HeroDef? = null): Fighter {
        hostTeam = team
        val np = cfg.players?.firstOrNull { it.id == 0 }
        return if (np != null) {
            val h = hero ?: Heroes.byId(np.hero)
            spawnFighter(h, np.name, team, true, x, y, np.skin, 0)
        } else {
            val h = hero ?: Heroes.byId(cfg.hero)
            spawnFighter(h, Save.playerName, team, true, x, y, Save.skin(h.id), 0)
        }
    }

    private fun takeHuman(team: Int): NetPlayer? {
        if (pendingHumans.isEmpty()) return null
        val ok = if (cfg.versus) team != hostTeam else (team == hostTeam || team >= 2)
        return if (ok) pendingHumans.removeAt(0) else null
    }

    private fun makeHuman(f: Fighter, np: NetPlayer, ownHero: Boolean) {
        f.human = true; f.netId = np.id; f.brain = null; f.name = np.name
        f.skin = np.skin
        if (ownHero) f.setHero(Heroes.byId(np.hero)) else f.color = f.hero.colors[np.skin.coerceIn(0, f.hero.colors.size - 1)]
        if (np.id == cfg.localId) player = f
        pendingHumans.remove(np)
    }

    fun spawnBots(n: Int, teamOf: (Int) -> Int, pos: (Int) -> FloatArray, heroPool: List<HeroDef> = Heroes.list): List<Fighter> {
        val out = ArrayList<Fighter>()
        val names = BOT_NAMES.toMutableList(); names.shuffle(com.starguys.battle.core.RNG)
        for (i in 0 until n) {
            val p = pos(i)
            val h = heroPool[ri(heroPool.size)]
            val skin = ri(3)
            val team = teamOf(i)
            val np = takeHuman(team)
            if (np != null) {
                val hh = if (heroPool === Heroes.list) Heroes.byId(np.hero) else h
                out.add(spawnFighter(hh, np.name, team, true, p[0], p[1], np.skin, np.id))
            } else out.add(spawnFighter(h, names[i % names.size], team, false, p[0], p[1], skin))
        }
        return out
    }

    /** A remote player left: a bot takes over their hero (deterministically, on every device). */
    fun dropPlayer(id: Int) {
        val f = fighters.firstOrNull { it.human && it.netId == id } ?: return
        f.human = false; f.brain = Bot(f); f.moveX = 0f; f.moveY = 0f
        hud.feed("${f.name} a quitté la partie (bot)", 0xFFFFC94D.toInt())
    }

    /** Applies one human's input. Actions only on the first tick of a turn. */
    fun applyInput(f: Fighter, inp: PlayerInput, first: Boolean) {
        if (f.out) { f.moveX = 0f; f.moveY = 0f; return }
        f.moveX = inp.mx; f.moveY = inp.my
        if (inp.aiming) { f.face = inp.aim; f.aimT = 0f }
        if (inp.hold) f.attack(inp.aim, inp.holdDist)
        if (!first) return
        for (a in inp.acts) {
            when (a[0].toInt()) {
                PlayerInput.ATTACK -> f.attack(a[1], a[2])
                PlayerInput.SUPER -> f.useSuper(a[1], a[2])
                PlayerInput.JUMP -> f.jump()
                PlayerInput.GADGET -> f.useGadget(a[1])
                PlayerInput.ITEM -> f.useItem()
                PlayerInput.INTERACT -> interact(f)
                PlayerInput.PING -> addPing(a[1], a[2], f.team)
            }
        }
    }

    /** Cheap fingerprint of the simulation state, compared between devices to detect desyncs. */
    fun checksum(): Long {
        var h = 1469598103934665603L
        fun mix(v: Int) { h = (h xor v.toLong()) * 1099511628211L }
        mix(java.lang.Float.floatToIntBits(time))
        for (f in fighters) { mix(java.lang.Float.floatToIntBits(f.x)); mix(java.lang.Float.floatToIntBits(f.y)); mix(java.lang.Float.floatToIntBits(f.hp)); mix(if (f.out) 1 else 0) }
        for (mb in mobs) { mix(java.lang.Float.floatToIntBits(mb.x)); mix(java.lang.Float.floatToIntBits(mb.hp)) }
        mix(projs.size); mix(pickups.size); mix(mobs.size)
        return h
    }

    fun randomSpawn(minDist: Float = 4f * T, area: IntArray? = null): FloatArray {
        val x0 = area?.get(0) ?: 1; val y0 = area?.get(1) ?: 1
        val x1 = area?.get(2) ?: (map.w - 2); val y1 = area?.get(3) ?: (map.h - 2)
        var best: FloatArray? = null; var bestD = -1f
        for (tries in 0 until 60) {
            val tx = ri(x0, x1); val ty = ri(y0, y1)
            val tp = map.t(tx, ty)
            if (!(tp == Tl.FLOOR || tp == Tl.BUSH || tp == Tl.ICE || tp == Tl.RAIL)) continue
            if (map.e(tx, ty) > 0 && tries < 40) continue
            val px = tx * T + T / 2; val py = ty * T + T / 2
            var md = 99999f
            for (f in fighters) if (!f.out) md = minOf(md, dist(px, py, f.x, f.y))
            for (mb in mobs) md = minOf(md, dist(px, py, mb.x, mb.y))
            if (md >= minDist) return floatArrayOf(px, py)
            if (md > bestD) { bestD = md; best = floatArrayOf(px, py) }
        }
        return best ?: floatArrayOf(map.pxW / 2, map.pxH / 2)
    }

    // ------------------------------------------------------------------ queries
    fun isEnemy(team: Int, b: Body): Boolean {
        if (b is Fighter && b.out) return false
        val bt = b.team
        if (bt == TEAM_NEUTRAL || team == TEAM_NEUTRAL) return true
        return bt != team
    }

    fun platformAt(x: Float, y: Float, z: Float): MovingPlatform? {
        for (p in platforms) if (p.contains(x, y) && z >= p.topZ - 40f) return p
        return null
    }

    fun losClear(x0: Float, y0: Float, x1: Float, y1: Float, z: Float): Boolean {
        val d = dist(x0, y0, x1, y1)
        val n = (d / 16f).toInt() + 1
        for (i in 1 until n) {
            val k = i.toFloat() / n
            val x = x0 + (x1 - x0) * k; val y = y0 + (y1 - y0) * k
            if (map.blocksShot((x / T).floorInt(), (y / T).floorInt(), z)) return false
        }
        return true
    }

    fun losWalk(x0: Float, y0: Float, x1: Float, y1: Float, z: Float): Boolean {
        val d = dist(x0, y0, x1, y1)
        if (d > 8f * T) return false
        val n = (d / 20f).toInt() + 1
        val px = -(y1 - y0) / d.coerceAtLeast(1f) * 14f; val py = (x1 - x0) / d.coerceAtLeast(1f) * 14f
        var zz = z
        for (i in 1..n) {
            val k = i.toFloat() / n
            val x = x0 + (x1 - x0) * k; val y = y0 + (y1 - y0) * k
            for (s in -1..1) {
                val tx = ((x + px * s) / T).floorInt(); val ty = ((y + py * s) / T).floorInt()
                val tp = map.t(tx, ty)
                if (tp == Tl.VOID || tp == Tl.LAVA) return false
                if (tp == Tl.COLLAPSE && map.aux(tx, ty) == 1) return false
                val g = map.groundZ(tx, ty)
                if (g > zz + STEP && tp != Tl.LADDER) return false
                if (s == 0) zz = maxOf(g, minOf(zz, g + 200f))
            }
        }
        return true
    }

    fun canStand(x: Float, y: Float, r: Float): Boolean {
        val tx = (x / T).floorInt(); val ty = (y / T).floorInt()
        if (!map.inside(tx, ty)) return false
        if (!map.walkable(tx, ty)) return false
        if (map.groundZ(tx, ty) <= VOID_Z + 1) return false
        return !map.isRaised(tx, ty) || map.e(tx, ty) > 0 && !Tl.isWallLike(map.t(tx, ty))
    }

    fun bodyOnTile(tx: Int, ty: Int): Boolean {
        for (b in movers) {
            if (b.x + b.radius > tx * T && b.x - b.radius < tx * T + T && b.y + b.radius > ty * T && b.y - b.radius < ty * T + T) return true
        }
        return false
    }

    fun liveTargets(): List<Body> = targetsCache

    fun nearestFighter(x: Float, y: Float, range: Float): Fighter? {
        var best: Fighter? = null; var bd = range
        for (f in fighters) { if (f.out || f.vehicle != null && f.vehicle?.protectsDriver == true) continue; val d = dist(x, y, f.x, f.y); if (d < bd) { bd = d; best = f } }
        return best
    }

    fun nearestEnemyBody(src: Body, range: Float): Body? {
        var best: Body? = null; var bd = range
        for (b in hittables) {
            if (b === src || !isEnemy(src.team, b) || b is Breakable || b is Ball) continue
            if (b is Fighter && b.invisT > 0 && b.revealT <= 0) continue
            val d = src.dist(b)
            if (d < bd) { bd = d; best = b }
        }
        return best
    }

    fun mobTarget(mob: Mob): Body? {
        mode.mobTarget(mob)?.let { return it }
        var best: Body? = null; var bd = if (mob.isBoss) 99999f else 15f * T
        for (b in targetsCache) {
            if (b.team == mob.team) continue
            if (b is Fighter && b.invisT > 0 && b.hero.id == 3) continue
            var d = mob.dist(b)
            if (b is Summon && b.kind == Summon.DECOY) d *= 0.5f
            if (d < bd) { bd = d; best = b }
        }
        return best
    }

    fun visibleToPlayer(b: Body): Boolean {
        val p = player ?: return true
        val viewer = if (p.out) (spectate ?: p) else p
        if (b is Fighter) {
            if (!isEnemy(viewer.team, b) || b === viewer) return true
            if (b.revealT > 0) return true
            return canSee(viewer, b)
        }
        if (b is Mob && fogR > 0f) return dist(viewer.x, viewer.y, b.x, b.y) < fogR + 60f || nearLantern(b.x, b.y)
        return true
    }

    fun canSee(v: Fighter, b: Fighter): Boolean {
        if (b.out) return false
        if (b.revealT > 0) return true
        if (b.invisT > 0 && b.hero.id == 3) return false
        val d = dist(v.x, v.y, b.x, b.y)
        if (fogR > 0f && d > fogR && !nearLantern(b.x, b.y)) return false
        val bt = map.t(b.tileX(), b.tileY())
        if (bt == Tl.BUSH && d > 2.3f * T && b.aimT > 1f) return false
        return true
    }

    fun nearLantern(x: Float, y: Float): Boolean {
        for (p in props) if (p is Decor && p.kind == Decor.LANTERN && dist(x, y, p.x, p.y) < 2.6f * T) return true
        return false
    }

    fun volAt(x: Float, y: Float): Float = clamp(1f - dist(x, y, cam.x, cam.y) / 1500f, 0f, 1f)
    fun sound(id: Int, x: Float, y: Float, vol: Float = 1f, rate: Float = 1f) { Sfx.play(id, vol * volAt(x, y), rate) }

    // ------------------------------------------------------------------ combat helpers
    fun explode(x: Float, y: Float, r: Float, dmg: Float, owner: Body?, team: Int, knock: Float, tiles: Boolean = true, color: Int = 0xFFFFE14D.toInt()) {
        if (dmg > 0f) for (b in ArrayList(hittables)) {
            if (!b.hittable) continue
            val d = dist(x, y, b.x, b.y)
            if (d > r + b.radius) continue
            if (team != TEAM_NEUTRAL && !isEnemy(team, b)) continue
            val fall = 1f - 0.45f * clamp(d / r, 0f, 1f)
            val a = ang(x, y, b.x, b.y)
            if (b.hurt(dmg * fall, owner ?: envBody, cosf(a) * knock * fall, sinf(a) * knock * fall)) {
                if (b !is Vehicle && b !is Boss) b.vz = maxOf(b.vz, 220f * fall)
            }
        }
        if (tiles && dmg > 0f) {
            val tr = (r / T).toInt() + 1
            val cx = (x / T).floorInt(); val cy = (y / T).floorInt()
            for (ty in cy - tr..cy + tr) for (tx in cx - tr..cx + tr) {
                if (dist(x, y, tx * T + T / 2, ty * T + T / 2) < r + T * 0.3f) map.damage(tx, ty, dmg * 0.8f)
            }
        }
        fx.flash(x, y - 20f, r * 1.4f, color, 0.35f)
        fx.flash(x, y - 20f, r * 0.7f, 0xFFFFFFFF.toInt(), 0.18f)
        fx.ring(x, y - 10f, r, color, 0.4f)
        fx.ring(x, y - 10f, r * 0.6f, 0xFFFFFFFF.toInt(), 0.3f)
        fx.smoke(x, y - 20f, 8, 0xFFEDE6F5.toInt())
        fx.stars(x, y - 20f, 6, color)
        cam.shakeAt(x, y, 7f)
        sound(Sfx.EXPLODE, x, y)
    }

    fun healArea(x: Float, y: Float, r: Float, amount: Float, team: Int) {
        for (f in fighters) if (!f.out && f.team == team && dist(x, y, f.x, f.y) < r) f.heal(amount)
        fx.ring(x, y, r, 0xFF7CFF8A.toInt())
    }

    fun melee(src: Body, x: Float, y: Float, a: Float, range: Float, halfArc: Float, dmg: Float, knock: Float, reflect: Boolean, stun: Float, color: Int) {
        fx.swoosh(x, y - src.z * ZS - 24f, range * 0.85f, a, halfArc, color)
        for (b in ArrayList(hittables)) {
            if (b === src || !b.hittable || !isEnemy(src.team, b)) continue
            val d = dist(x, y, b.x, b.y)
            if (d > range + b.radius) continue
            if (abs(b.z - src.z) > 70f && b !is Vehicle) continue
            val ba = ang(x, y, b.x, b.y)
            if (d > b.radius + 10f && abs(angleDiff(a, ba)) > halfArc + 0.15f) continue
            if (b.hurt(dmg, src, cosf(ba) * knock, sinf(ba) * knock)) {
                if (stun > 0f) b.applyStatus(St.STUN, stun, src)
                fx.hit(b.x, b.y - b.z * ZS - 30f, color)
            }
        }
        if (reflect) for (p in projs) {
            if (!p.alive || !p.reflectable || p.team == src.team) continue
            val d = dist(x, y, p.x, p.y)
            if (d > range + 20f) continue
            if (abs(angleDiff(a, ang(x, y, p.x, p.y))) > halfArc + 0.3f) continue
            p.reflect(src, src.team)
            fx.stars(p.x, p.y - p.z * ZS, 3, 0xFFFFFFFF.toInt())
        }
        // smash crates in front
        val hx = x + cosf(a) * range * 0.7f; val hy = y + sinf(a) * range * 0.7f
        map.damage((hx / T).floorInt(), (hy / T).floorInt(), dmg * 0.6f)
        for (pr in props) if (pr is Breakable && pr.hittable && dist(hx, hy, pr.x, pr.y) < range * 0.5f + 20f) pr.hurt(dmg, src, 0f, 0f)
        sound(Sfx.SWING, x, y)
    }

    fun beam(src: Body, a: Float, range: Float, dmg: Float, width: Float, color: Int, pierceBodies: Boolean, wallPierce0: Int) {
        var wallPierce = wallPierce0
        val sx = src.x + cosf(a) * 20f; val sy = src.y + sinf(a) * 20f
        val z = src.z + 26f
        val step = 12f
        var d = 0f
        var ex = sx; var ey = sy
        val hit = HashSet<Body>()
        var lastTile = -1
        var inWall = false
        while (d < range) {
            d += step
            ex = sx + cosf(a) * d; ey = sy + sinf(a) * d
            val tx = (ex / T).floorInt(); val ty = (ey / T).floorInt()
            val ti = ty * 10000 + tx
            if (map.blocksShot(tx, ty, z)) {
                if (ti != lastTile) {
                    lastTile = ti
                    if (!inWall && wallPierce > 0 && isThin(tx, ty, a)) { wallPierce--; inWall = true; map.damage(tx, ty, dmg * 0.3f); continue }
                    if (inWall) continue
                    map.damage(tx, ty, dmg * 0.5f)
                    if (map.t(tx, ty) == Tl.GLASS) sound(Sfx.GLASS, ex, ey)
                    break
                } else if (inWall) continue else break
            } else inWall = false
            var stop = false
            for (b in hittables) {
                if (b === src || !b.hittable || hit.contains(b) || !isEnemy(src.team, b)) continue
                if (abs(b.x - ex) > b.radius + width || abs(b.y - ey) > b.radius + width) continue
                if (dist(ex, ey, b.x, b.y) < b.radius + width * 0.5f) {
                    hit.add(b)
                    if (b.hurt(dmg, src, cosf(a) * 200f, sinf(a) * 200f)) fx.hit(b.x, b.y - b.z * ZS - 30f, color)
                    if (!pierceBodies) { stop = true; break }
                }
            }
            if (stop) break
        }
        val zz = z * ZS
        fx.line(sx, sy - zz, ex, ey - zz, color, 0.22f, width)
        fx.line(sx, sy - zz, ex, ey - zz, 0xFFFFFFFF.toInt(), 0.12f, width * 0.35f)
    }

    private fun isThin(tx: Int, ty: Int, a: Float): Boolean {
        val nx = (tx + 0.5f + cosf(a) * 1.0f).floorInt(); val ny = (ty + 0.5f + sinf(a) * 1.0f).floorInt()
        return !Tl.isWallLike(map.t(nx, ny))
    }

    /** Projectile reached a blocking tile. Returns true if consumed. */
    fun onShotTile(p: Projectile, tx: Int, ty: Int): Boolean {
        if (p.wallPierce > 0 && isThin(tx, ty, atan2(p.vy, p.vx))) { p.wallPierce--; return false }
        if (p.bounces > 0 && !p.lob) {
            p.bounces--
            val cx = tx * T + T / 2; val cy = ty * T + T / 2
            if (abs(p.x - cx) > abs(p.y - cy)) p.vx = -p.vx else p.vy = -p.vy
            p.x += p.vx * 0.02f; p.y += p.vy * 0.02f
            return false
        }
        val tp = map.t(tx, ty)
        val destroyed = map.damage(tx, ty, p.dmg * p.tileDmg)
        if (tp == Tl.GLASS) sound(Sfx.GLASS, p.x, p.y, if (destroyed) 1f else 0.4f)
        fx.hit(p.x, p.y - p.z * ZS, p.color)
        if (p.explodeR > 0f) explode(p.x, p.y, p.explodeR, p.dmg, p.owner, p.team, p.knock + 200f)
        if (p.grapple) {
            val o = p.owner as? Fighter
            if (o != null) { val aa = ang(o.x, o.y, p.x, p.y); val dd = dist(o.x, o.y, p.x, p.y); val sp = minOf(1400f, dd * 3.2f); o.vx = cosf(aa) * sp; o.vy = sinf(aa) * sp; o.vz = 260f; o.dashT = 0.35f }
        }
        if (p.sticky) addProp(StickyMine(this, null, p.x - p.vx * 0.02f, p.y - p.vy * 0.02f, p.ownerF, p.team))
        if (p.boomerang && !p.returning) { p.returning = true; return false }
        p.alive = false
        return true
    }

    fun stickMine(b: Body, owner: Fighter?, team: Int) { addProp(StickyMine(this, b, b.x, b.y, owner, team)) }

    fun reveal(team: Int, x: Float, y: Float, r: Float, t: Float) {
        for (f in fighters) if (!f.out && f.team != team && dist(x, y, f.x, f.y) < r) f.revealT = maxOf(f.revealT, t)
    }

    fun dropLoot(x: Float, y: Float, n: Int) {
        for (i in 0 until n) {
            val a = rf() * 6.28f; val px = x + cosf(a) * 40f; val py = y + sinf(a) * 40f
            val r = rf()
            val p = when {
                r < 0.4f -> Pickup(this, Pickup.WEAPON, Weapons.pickupable.pick(), px, py)
                r < 0.75f -> Pickup(this, Pickup.ITEM, ri(4), px, py)
                else -> Pickup(this, Pickup.GADGET, ri(Gadgets.list.size), px, py)
            }
            addPickup(p)
        }
    }

    private fun onTileDestroyed(tx: Int, ty: Int, tp: Int) {
        val x = tx * T + T / 2; val y = ty * T + T / 2
        when (tp) {
            Tl.GLASS -> { fx.debris(x, y - 30f, 0xFFCDEBFF.toInt()); sound(Sfx.GLASS, x, y) }
            Tl.BOX, Tl.LOW -> { fx.debris(x, y - 30f, 0xFFD9A066.toInt()); sound(Sfx.THUD, x, y, 0.6f); if (mode.dropOnDeath && rf() < 0.35f) dropLoot(x, y, 1) }
            Tl.BRICK -> fx.debris(x, y - 30f, 0xFFE07B4A.toInt())
            Tl.FOAM -> fx.smoke(x, y, 3, 0xFFFFFFFF.toInt())
        }
    }

    // ------------------------------------------------------------------ vehicles & interaction
    fun enterVehicle(f: Fighter, v: Vehicle) {
        if (v.driver != null || f.vehicle != null) return
        v.driver = f; f.vehicle = v; v.team = f.team
        f.superT = 0f; f.superKind = -1
        sound(Sfx.ENGINE, v.x, v.y)
    }

    fun exitVehicle(f: Fighter) {
        val v = f.vehicle ?: return
        v.driver = null; f.vehicle = null; v.team = TEAM_NEUTRAL
        v.held?.let { it.heldBy = null }; v.held = null
        f.x = v.x + 40f; f.y = v.y + 20f
        if (!canStand(f.x, f.y, f.radius)) { f.x = v.x; f.y = v.y }
        f.z = if (v.kind == Vehicle.BALLOON) v.z else maxOf(0f, map.groundAt(f.x, f.y))
        f.vz = 0f; f.parachutes += if (v.kind == Vehicle.BALLOON) 0 else 0
        if (v.kind == Vehicle.BALLOON && v.z > 40f) { f.gravScale = 1f; f.parachutes = maxOf(f.parachutes, 0) }
    }

    private fun findInteract(f: Fighter, doIt: Boolean): String? {
        if (f.out) return null
        if (f.vehicle != null) { if (doIt) exitVehicle(f); return "Descendre" }
        var bestV: Vehicle? = null; var bd = 1.4f * T
        for (v in vehicles) if (v.alive && v.driver == null) {
            val d = dist(f.x, f.y, v.x, v.y); if (d < bd && abs(f.z - v.z) < 80f) { bd = d; bestV = v }
        }
        if (bestV != null) { if (doIt) enterVehicle(f, bestV); return "Monter" }
        // swap pickups
        for (p in pickups) {
            if (!p.alive || dist(f.x, f.y, p.x, p.y) > T * 0.9f) continue
            if (p.kind == Pickup.WEAPON && f.modeWeapon == null && f.weapon != null && f.weapon?.id != p.id) {
                if (doIt) { val old = f.weapon!!; addPickup(Pickup(this, Pickup.WEAPON, old.id, f.x, f.y + 20f)); f.weapon = Weapons.list[p.id]; f.wAmmo = Weapons.list[p.id].ammo; p.alive = false; sound(Sfx.PICKUP, f.x, f.y) }
                return "Échanger"
            }
            if (p.kind == Pickup.GADGET && f.gadget != null && f.gadget?.id != p.id) {
                if (doIt) { addPickup(Pickup(this, Pickup.GADGET, f.gadget!!.id, f.x, f.y + 20f)); giveGadget(f, p.id); p.alive = false; sound(Sfx.PICKUP, f.x, f.y) }
                return "Échanger"
            }
        }
        var bestP: Prop? = null; var bpd = 99999f; var label: String? = null
        for (pr in props) {
            if (!pr.alive) continue
            val l = pr.interactLabel(f) ?: continue
            val d = dist(f.x, f.y, pr.x, pr.y)
            if (d < pr.interactRange && d < bpd) { bpd = d; bestP = pr; label = l }
        }
        if (bestP != null) { if (doIt) bestP.interact(f); return label }
        val ml = mode.interactLabel(f)
        if (ml != null) { if (doIt) mode.interact(f); return ml }
        return null
    }

    fun interactLabel(f: Fighter) = findInteract(f, false)
    fun interact(f: Fighter) { findInteract(f, true) }

    fun giveGadget(f: Fighter, id: Int) {
        val g = Gadgets.list[id]
        if (id == Gadgets.PARACHUTE) { f.parachutes++; fx.text(f.x, f.y - 90f, "+1 PARACHUTE", 0xFFFF6B6B.toInt()); return }
        f.gadget = g; f.gadgetCharges = g.charges
    }

    private fun collectPickups() {
        for (p in pickups) {
            if (!p.alive) continue
            for (f in fighters) {
                if (f.out || f.vehicle != null) continue
                if (abs(f.x - p.x) > 30f || abs(f.y - p.y) > 30f || abs(f.z - p.z) > 40f) continue
                if (!mode.canPickup(f, p)) continue
                var took = false
                when (p.kind) {
                    Pickup.WEAPON -> if (f.modeWeapon == null && (f.weapon == null || f.weapon?.id == p.id)) {
                        f.weapon = Weapons.list[p.id]; f.wAmmo = Weapons.list[p.id].ammo; f.unarmed = false; took = true
                        if (f.isLocal) hud.toastSmall(Weapons.list[p.id].name)
                    }
                    Pickup.GADGET -> if (f.gadget == null || p.id == Gadgets.PARACHUTE) { giveGadget(f, p.id); took = true; if (f.isLocal) hud.toastSmall(Gadgets.list[p.id].name) }
                    Pickup.ITEM -> if (f.items.size < 3) { f.items.add(p.id); took = true; if (f.isLocal) hud.toastSmall(Items.list[p.id].name) }
                    Pickup.HEAL -> if (f.hp < f.maxHp) { f.heal(1200f); took = true; sound(Sfx.HEAL, f.x, f.y) }
                    Pickup.STAR -> { f.chargeSuper(0.5f); took = true }
                    Pickup.GOLD -> {}
                }
                if (took) { p.alive = false; sound(Sfx.PICKUP, p.x, p.y, 0.7f); fx.stars(p.x, p.y - 20f, 4, 0xFFFFE14D.toInt()); break }
            }
        }
    }

    fun addPing(x: Float, y: Float, team: Int) {
        pings.add(Ping(x, y, team))
        for (f in fighters) if (!f.human && f.team == team) f.brain?.ping(x, y)
        if (player?.team == team) { Sfx.play(Sfx.COUNT, 0.6f, 1.5f); fx.ring(x, y, 60f, 0xFFFBBF24.toInt(), 0.6f) }
    }

    fun onFighterDeath(f: Fighter, killer: Fighter?) {
        val k = killer
        if (k != null) hud.feed("${k.name} ⟶ ${f.name}", if (k === player) 0xFF7CFF8A.toInt() else if (f === player) 0xFFFF5E7E.toInt() else 0xFFFFFFFF.toInt())
        else hud.feed("${f.name} est éliminé", if (f === player) 0xFFFF5E7E.toInt() else 0xFFDDDDDD.toInt())
        if (mode.dropOnDeath) {
            f.weapon?.let { addPickup(Pickup(this, Pickup.WEAPON, it.id, f.safeX + rf(-20f, 20f), f.safeY + rf(-20f, 20f))) }
            for (it in f.items) addPickup(Pickup(this, Pickup.ITEM, it, f.safeX + rf(-30f, 30f), f.safeY + rf(-30f, 30f)))
            f.weapon = null; f.items.clear()
        }
        if (mode.respawns) f.respawnT = mode.respawnDelay
        if (f === player) {
            this.killer = k
            killCamT = if (k != null && k !== f) 1.6f else 0.8f
            if (!cfg.net) { slowMo = 0.35f; slowT = 1.2f }
            Sfx.buzz(120)
            spectate = k?.takeIf { !it.out }
        }
        if (k === player) { cam.shake(3f); Sfx.buzz(40) }
        mode.onDeath(f, k)
    }

    fun onMobDeath(mb: Mob, killer: Fighter?) {
        if (killer != null) killer.score += mb.worth
        if (!mb.isBoss && rf() < 0.08f) addPickup(Pickup(this, Pickup.HEAL, 0, mb.x, mb.y))
        if (mb is Boss) { if (boss === mb) boss = null; dropLoot(mb.x, mb.y, 3) }
        mode.onMobDeath(mb, killer)
    }

    fun finish(playerWon: Boolean, title: String) {
        if (over) return
        over = true; won = playerWon; resultTitle = title
        mode.endT = 3f
        if (!cfg.net) { slowMo = 0.5f; slowT = 1.5f }
        Sfx.play(if (playerWon) Sfx.WIN else Sfx.LOSE)
        player?.emote = if (playerWon) 10f else 0f
        if (playerWon) player?.let { fx.confetti(it.x, it.y - 60f, 60) }
    }

    // ------------------------------------------------------------------ update
    /** Single-player driver: local input + one simulation step + presentation. */
    fun update(rawDt: Float) {
        if (paused) { hud.update(rawDt); return }
        if (slowT > 0) { slowT -= rawDt; if (slowT <= 0f) slowMo = 1f }
        val dt = rawDt * slowMo
        player?.let { applyInput(it, hud.takeInput(), true) }
        simStep(dt)
        frame(rawDt)
    }

    /** Presentation only (camera, HUD, end-of-match timer). Never touches the simulation. */
    fun frame(rawDt: Float) {
        updateCamera(rawDt)
        hud.update(rawDt)
        if (over) {
            mode.endT -= rawDt
            if (mode.endT <= 0f && !ended) { ended = true; finishCb?.invoke() }
        }
    }

    /** One deterministic simulation step (identical on every device in multiplayer). */
    fun simStep(dt: Float) {
        time += dt
        map.update(dt)
        rebuildLists()
        if (!over) mode.update(dt)
        mode.handleRespawns(dt)
        var i = 0
        while (i < fighters.size) { val f = fighters[i]; if (!f.out) f.brain?.update(dt); f.update(dt); i++ }
        i = 0; while (i < mobs.size) { mobs[i].update(dt); i++ }
        i = 0; while (i < vehicles.size) { vehicles[i].update(dt); i++ }
        i = 0; while (i < summons.size) { summons[i].update(dt); i++ }
        i = 0; while (i < props.size) { props[i].update(dt); i++ }
        i = 0; while (i < pickups.size) { pickups[i].update(dt); i++ }
        i = 0; while (i < projs.size) { val p = projs[i]; if (p.alive) p.update(dt); i++ }
        i = 0; while (i < zones.size) { zones[i].update(dt); i++ }
        separate()
        pushOutOfProps()
        collectPickups()
        for (pg in pings) pg.life -= dt
        pings.removeAll { it.life <= 0f }
        fx.update(dt)
        projs.removeAll { !it.alive }
        mobs.removeAll { !it.alive }
        summons.removeAll { !it.alive }
        pickups.removeAll { !it.alive }
        zones.removeAll { !it.alive }
        vehicles.removeAll { !it.alive }
        if (props.any { !it.alive }) { props.removeAll { !it.alive }; platforms.removeAll { !it.alive } }
    }

    private fun rebuildLists() {
        hittables.clear(); movers.clear(); targetsCache.clear()
        for (f in fighters) if (!f.out) {
            if (f.hittable) hittables.add(f)
            if (f.vehicle == null && f.heldBy == null && f.cannonT <= 0f && f.zipline == null) movers.add(f)
            if (f.team != TEAM_MOBS && f.cannonT <= 0f) targetsCache.add(f)
        }
        for (mb in mobs) if (mb.alive) { hittables.add(mb); movers.add(mb) }
        for (v in vehicles) if (v.alive) { if (v.hittable) hittables.add(v); if (v.kind != Vehicle.COASTER && v.kind != Vehicle.BALLOON) movers.add(v) }
        for (s in summons) if (s.alive) { hittables.add(s); if (s.kind == Summon.CLONE) movers.add(s); if (s.kind != Summon.TURRET) targetsCache.add(s) }
        for (p in props) if (p.alive && p.hittable) { hittables.add(p); if (p is Ball) movers.add(p) }
    }

    private fun separate() {
        val n = movers.size
        for (a in 0 until n) {
            val A = movers[a]
            if (A is Vehicle && A.kind == Vehicle.COASTER) continue
            for (b in a + 1 until n) {
                val B = movers[b]
                if (A is Ball || B is Ball) continue
                val rr = A.radius + B.radius
                val dx = B.x - A.x; val dy = B.y - A.y
                if (abs(dx) > rr || abs(dy) > rr) continue
                if (abs(A.z - B.z) > 50f) continue
                val d2 = dx * dx + dy * dy
                if (d2 >= rr * rr || d2 < 0.0001f) continue
                val d = kotlin.math.sqrt(d2)
                val push = (rr - d) * 0.5f
                val nx = dx / d; val ny = dy / d
                val wa = B.mass / (A.mass + B.mass); val wb = A.mass / (A.mass + B.mass)
                A.x -= nx * push * wa * 2f * 0.5f; A.y -= ny * push * wa * 2f * 0.5f
                B.x += nx * push * wb * 2f * 0.5f; B.y += ny * push * wb * 2f * 0.5f
            }
        }
    }

    private fun pushOutOfProps() {
        if (solidGrid.isEmpty()) return
        for (b in movers) {
            val tx = b.tileX(); val ty = b.tileY()
            for (dy in -1..1) for (dx in -1..1) {
                val l = solidGrid[(ty + dy) * 10000 + tx + dx] ?: continue
                for (p in l) {
                    if (!p.alive || p.solidR <= 0f || p === b) continue
                    if (b.z > p.z + 60f) continue
                    val rr = p.solidR + b.radius
                    val ddx = b.x - p.x; val ddy = b.y - p.y
                    val d2 = ddx * ddx + ddy * ddy
                    if (d2 < rr * rr && d2 > 0.0001f) {
                        val d = kotlin.math.sqrt(d2)
                        b.x = p.x + ddx / d * rr; b.y = p.y + ddy / d * rr
                    }
                }
            }
        }
        // solid props also stop projectiles
        for (p in projs) {
            if (!p.alive || p.lob) continue
            val l = solidGrid[((p.y / T).floorInt()) * 10000 + (p.x / T).floorInt()] ?: continue
            for (pr in l) {
                if (!pr.alive || !pr.blocksShots || pr.hittable) continue
                if (p.z > pr.z + 70f) continue
                if (dist(p.x, p.y, pr.x, pr.y) < pr.solidR + p.r * 0.5f) {
                    if (p.boomerang && !p.returning) { p.returning = true; continue }
                    p.alive = false; fx.hit(p.x, p.y - p.z * ZS, p.color)
                    mode.onPropShot(pr, p)
                    if (p.explodeR > 0f) explode(p.x, p.y, p.explodeR, p.dmg, p.owner, p.team, p.knock + 200f)
                }
            }
        }
    }

    private fun updateCamera(dt: Float) {
        val p = player
        var target: Body? = p
        if (p != null && p.out) {
            if (killCamT > 0f) { killCamT -= dt; target = killer?.takeIf { !it.out } ?: p }
            else {
                val s = spectate
                if (s == null || s.out) spectate = fighters.filter { !it.out && (it.team == p.team || !mode.respawns) }.minByOrNull { dist(it.x, it.y, cam.x, cam.y) }
                target = if (mode.respawns) p else (spectate ?: p)
            }
        }
        val baseZoom = app.h / (11.5f * T)
        var zm = 1f
        if (p?.vehicle?.kind == Vehicle.BALLOON) zm = 0.72f
        if (p?.giantT ?: 0f > 0f || (p?.growth ?: 0) > 3) zm = 0.85f
        cam.targetZoom = baseZoom * zm * hud.zoomMul
        val t = target
        if (t != null) {
            val v = (t as? Fighter)?.vehicle
            val tx = v?.x ?: t.x; val ty = (v?.y ?: t.y) - (v?.z ?: t.z) * ZS - 30f
            cam.update(tx, ty, dt)
        }
    }

    // ------------------------------------------------------------------ render
    fun draw(c: Canvas) {
        val w = app.w; val h = app.h
        // background
        if (bgH != h) { bgPaint.shader = LinearGradient(0f, 0f, 0f, h, biome.bgTop, biome.bgBottom, Shader.TileMode.CLAMP); bgH = h }
        c.drawRect(0f, 0f, w, h, bgPaint)
        if (biome == Biome.SPACE || map.oobVoid) for (i in 0 until 60) {
            val sx = ((i * 211.7f - cam.x * 0.1f) % w + w) % w; val sy = ((i * 97.3f - cam.y * 0.1f) % h + h) % h
            G.circle(c, sx, sy, 1.5f + (i % 3), G.alpha(0xFFFFFFFF.toInt(), 0.4f + 0.3f * sinf(time * 2f + i)), false)
        }
        c.save()
        val z = cam.zoom
        c.translate(w / 2 + cam.sx, h / 2 + cam.sy)
        c.scale(z, z)
        c.translate(-cam.x, -cam.y)
        val vx0 = cam.x - w / 2 / z; val vx1 = cam.x + w / 2 / z
        val vy0 = cam.y - h / 2 / z; val vy1 = cam.y + h / 2 / z
        val tx0 = maxOf(0, (vx0 / T).floorInt() - 1); val tx1 = minOf(map.w - 1, (vx1 / T).floorInt() + 1)
        val ty0 = maxOf(0, (vy0 / T).floorInt() - 1); val ty1 = minOf(map.h - 1, (vy1 / T).floorInt() + 5)
        map.drawGround(c, tx0, ty0, tx1, ty1, time)
        map.drawShadows(c, tx0 - 1, ty0 - 1, tx1, ty1)
        for (zn in zones) zn.drawUnder(c)
        mode.drawUnder(c)
        val myTeam = player?.team
        for (pg in pings) {
            if (myTeam != null && pg.team != myTeam) continue
            val a = clamp(pg.life, 0f, 1f)
            G.fade = a
            G.ow = 3f
            G.oval(c, pg.x, pg.y, 40f, 16f, 0x55F59E0B, false)
            G.rect(c, pg.x - 9f, pg.y - 300f, pg.x + 9f, pg.y, 0x44FDE68A, false)
            G.ring(c, pg.x, pg.y, 22f + (time * 50f % 34f), 5f, 0xFFFBBF24.toInt())
            val by = pg.y - 150f + sinf(time * 5f) * 8f
            G.poly(c, 0xFFF59E0B.toInt(), true, pg.x, by + 34f, pg.x - 16f, by + 8f, pg.x + 16f, by + 8f)
            G.circle(c, pg.x, by, 24f, 0xFFFBBF24.toInt())
            G.text(c, "!", pg.x, by + 11f, 32f, G.WHITE)
            G.fade = 1f
        }
        // sorted entities
        drawList.clear()
        for (f in fighters) if (!f.out && visibleToPlayer(f)) drawList.add(f)
        for (mb in mobs) if (visibleToPlayer(mb)) drawList.add(mb)
        drawList.addAll(vehicles); drawList.addAll(summons); drawList.addAll(pickups)
        for (p in props) if (p.x > vx0 - 300f && p.x < vx1 + 300f && p.y > vy0 - 100f && p.y < vy1 + 400f) drawList.add(p)
        drawList.sortBy { it.sortY }
        var di = 0
        // entities above the first visible row
        G.depth = true
        while (di < drawList.size && (drawList[di].sortY / T).floorInt() < ty0) { drawList[di].draw(c); di++ }
        for (ty in ty0..ty1) {
            map.drawRaisedRow(c, ty, tx0, tx1, time)
            while (di < drawList.size && (drawList[di].sortY / T).floorInt() <= ty) { drawList[di].draw(c); di++ }
            map.drawBushRow(c, ty, tx0, tx1, time)
        }
        while (di < drawList.size) { drawList[di].draw(c); di++ }
        for (p in projs) { p.shadow(c) }
        for (p in projs) if (p.x > vx0 - 100f && p.x < vx1 + 100f && p.y > vy0 - 100f && p.y < vy1 + 200f) p.draw(c)
        for (zn in zones) zn.drawOver(c)
        mode.drawOver(c)
        G.depth = false
        fx.draw(c)
        // storm
        if (stormR > 0f) {
            G.stroke.shader = null
            G.stroke.color = 0x88B46CFF.toInt()
            val big = 6000f
            G.stroke.strokeWidth = big
            c.drawCircle(stormX, stormY, stormR + big / 2, G.stroke)
            G.ring(c, stormX, stormY, stormR, 8f, 0xFFE0B0FF.toInt())
        }
        hud.drawWorld(c)
        c.restore()
        // fog of war (haunted swamp)
        if (fogR > 0f) {
            val p = player
            val fx0 = w / 2; val fy0 = h / 2
            val r = fogR * z * (if (p?.revealT ?: 0f > 0f) 1.4f else 1f)
            fogPaint.shader = RadialGradient(fx0, fy0, r * 1.25f, intArrayOf(0x00000000, 0x00000000, 0xE60A120D.toInt()), floatArrayOf(0f, 0.62f, 1f), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w, h, fogPaint)
        }
        drawVignette(c, w, h)
        hud.draw(c)
    }

    private val vignettePaint = Paint()
    private val lightPaint = Paint()
    private var vigKey = 0f
    /** Cinematic vignette + warm key light from the top-left: adds depth to the whole frame. */
    private fun drawVignette(c: Canvas, w: Float, h: Float) {
        if (vigKey != w * 10000f + h) {
            vigKey = w * 10000f + h
            vignettePaint.shader = RadialGradient(w / 2, h / 2, kotlin.math.sqrt(w * w + h * h) * 0.62f, intArrayOf(0x00000000, 0x00000000, 0x660F172A), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
            lightPaint.shader = LinearGradient(0f, 0f, w * 0.6f, h * 0.8f, intArrayOf(0x22FEF3C7, 0x00FEF3C7), null, Shader.TileMode.CLAMP)
        }
        c.drawRect(0f, 0f, w, h, lightPaint)
        c.drawRect(0f, 0f, w, h, vignettePaint)
    }

    companion object {
        val BOT_NAMES = listOf("Haricot", "Biscotte", "Zigzag", "Pompon", "Tartine", "Bulle", "Cornichon", "Patate", "Moustache", "Choupi",
            "Gnocchi", "Chaussette", "Paprika", "Pirouette", "Croquette", "Noisette", "Tagine", "Couscous", "Msemen", "Chebakia",
            "Baghrir", "Zaatar", "Sardine", "Fennec", "Tortilla", "Bretzel", "Mochi", "Nougat", "Pistache", "Kiwi", "Radis", "Cactus",
            "Bonbon", "Flan", "Gaufre", "Crêpe", "Churros", "Macaron", "Brioche", "Clafoutis", "Praline", "Sésame", "Harissa", "Safran",
            "Citronnelle", "Bambou", "Yuzu", "Wasabi", "Kebab", "Falafel")
    }
}

/** Mine stuck on a target (or a wall), explodes after a delay. */
class StickyMine(m: Match, val target: Body?, px: Float, py: Float, val owner: Fighter?, val ownerTeam: Int) : Prop(m, px, py) {
    var life = 1.5f
    val ox = if (target != null) px - target.x else 0f
    val oy = if (target != null) py - target.y else 0f
    override fun update(dt: Float) {
        super.update(dt)
        val tg = target
        if (tg != null) { x = tg.x; y = tg.y; z = tg.z + 30f }
        life -= dt
        if (life <= 0f) { alive = false; m.explode(x, y, 1.2f * T, 1200f, owner ?: m.envBody, ownerTeam, 700f) }
    }
    override fun draw(c: Canvas) {
        val sy = y - z * ZS
        G.ow = 2f
        G.circle(c, x, sy, 9f, 0xFFFF5E7E.toInt())
        if ((t * 10).toInt() % 2 == 0) G.circle(c, x, sy, 4f, 0xFFFFFFFF.toInt(), false)
    }
}
