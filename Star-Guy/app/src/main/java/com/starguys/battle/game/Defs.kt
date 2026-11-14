package com.starguys.battle.game

import com.starguys.battle.core.Sfx
import com.starguys.battle.core.TAU
import com.starguys.battle.core.clamp
import com.starguys.battle.core.cosf
import com.starguys.battle.core.rf
import com.starguys.battle.core.sinf

enum class Aim { LINE, CONE, LOB, ARC, SELF, POINT }

class WeaponDef(
    val id: Int, val name: String, val desc: String, val color: Int, val aim: Aim,
    val range: Float, val cooldown: Float, val ammo: Int, val reload: Float = 1.4f,
    val auto: Boolean = false, val width: Float = 20f, val spread: Float = 0f, val melee: Boolean = false,
    val recoil: Float = 120f,
    val fire: (Fighter, Float, Float) -> Unit
)

class HeroDef(
    val id: Int, val name: String, val cls: String, val look: String, val hp: Float, val speed: Float,
    val colors: IntArray, val attack: WeaponDef, val superName: String, val superDesc: String,
    val superCost: Float, val superAim: Aim, val superRange: Float, val prefRange: Float
)

class GadgetDef(val id: Int, val name: String, val desc: String, val color: Int, val charges: Int)
class ItemDef(val id: Int, val name: String, val desc: String, val color: Int)

/** Shooting helpers shared by heroes, weapons, turrets and bosses. */
object Fire {
    fun muzzle(f: Body, a: Float, p: Projectile, off: Float = 22f) {
        p.x = f.x + cosf(a) * off; p.y = f.y + sinf(a) * off
        p.z = f.z + 26f * (if (f is Fighter) f.scale else 1f)
        p.team = f.team; p.owner = f
    }

    fun bullet(f: Body, a: Float, speed: Float, rangeTiles: Float, dmg: Float, r: Float, vis: Int, color: Int,
               init: (Projectile.() -> Unit)? = null): Projectile {
        val p = Projectile(f.m)
        muzzle(f, a, p)
        val rangeMul = if (f is Fighter) f.rangeMul() else 1f
        val dmul = if (f is Fighter) f.dmgMul() else 1f
        p.vx = cosf(a) * speed; p.vy = sinf(a) * speed
        p.maxTravel = rangeTiles * T * rangeMul; p.life = p.maxTravel / speed + 0.1f
        p.dmg = dmg * dmul; p.r = r * (if (f is Fighter) f.scale.coerceAtMost(1.6f) else 1f); p.vis = vis; p.color = color
        init?.invoke(p)
        f.m.addProj(p)
        return p
    }

    fun spread(f: Body, a: Float, n: Int, spreadDeg: Float, speed: Float, range: Float, dmg: Float, r: Float, vis: Int, color: Int,
               init: (Projectile.() -> Unit)? = null) {
        val sp = Math.toRadians(spreadDeg.toDouble()).toFloat()
        for (i in 0 until n) {
            val k = if (n == 1) 0f else i / (n - 1f) - 0.5f
            bullet(f, a + k * sp + rf(-0.03f, 0.03f), speed * rf(0.92f, 1.05f), range, dmg, r, vis, color, init)
        }
    }

    fun lob(f: Body, a: Float, d: Float, maxRange: Float, dmg: Float, explodeR: Float, vis: Int, color: Int,
            flight: Float = 0.65f, init: (Projectile.() -> Unit)? = null): Projectile {
        val p = Projectile(f.m)
        muzzle(f, a, p, 12f)
        val rangeMul = if (f is Fighter) f.rangeMul() else 1f
        val dd = clamp(d, T * 0.8f, maxRange * T * rangeMul)
        val tx = f.x + cosf(a) * dd; val ty = f.y + sinf(a) * dd
        p.lob = true
        p.vx = (tx - p.x) / flight; p.vy = (ty - p.y) / flight
        val g = GRAV * f.m.gravity
        val tz = f.m.map.groundAt(tx, ty).coerceAtLeast(-50f)
        p.vz = ((tz - p.z) + 0.5f * g * flight * flight) / flight
        p.life = flight * 3f
        p.dmg = dmg * (if (f is Fighter) f.dmgMul() else 1f); p.explodeR = explodeR * T; p.vis = vis; p.color = color; p.r = 11f
        init?.invoke(p)
        f.m.addProj(p)
        return p
    }

    fun melee(f: Fighter, a: Float, rangeTiles: Float, arcDeg: Float, dmg: Float, knock: Float, reflect: Boolean = false,
              stun: Float = 0f, color: Int = 0xFFFFFFFF.toInt()) {
        f.m.melee(f, f.x, f.y, a, rangeTiles * T * f.scale.coerceAtMost(1.8f), Math.toRadians(arcDeg / 2.0).toFloat(), dmg * f.dmgMul(), knock, reflect, stun, color)
    }

    fun beam(f: Body, a: Float, rangeTiles: Float, dmg: Float, width: Float, color: Int, pierceBodies: Boolean, wallPierce: Int) {
        val rangeMul = if (f is Fighter) f.rangeMul() else 1f
        f.m.beam(f, a, rangeTiles * T * rangeMul, dmg * (if (f is Fighter) f.dmgMul() else 1f), width, color, pierceBodies, wallPierce)
    }
}

object Weapons {
    const val MAGNET = 0; const val FOAM = 1; const val GRAPPLE = 2; const val FLAME = 3; const val BOOMERANG = 4
    const val CONFETTI = 5; const val BUBBLE = 6; const val RAIL = 7; const val SUCKER = 8; const val BAT = 9
    const val HAMMER = 10; const val SWORD = 11; const val PAN = 12; const val HUNTER = 13; const val GOLDPAN = 14

    val list: List<WeaponDef> = listOf(
        WeaponDef(MAGNET, "Fusil à aimant", "Attire les ennemis vers toi", 0xFFE84545.toInt(), Aim.LINE, 6f, 0.45f, 14) { f, a, _ ->
            Fire.bullet(f, a, 1000f, 6f, 420f, 10f, PV.MAGNET, 0xFFE84545.toInt()) { pull = 650f }
            f.m.sound(Sfx.ZAP, f.x, f.y)
        },
        WeaponDef(FOAM, "Canon à mousse", "Crée des plateformes temporaires où l'on peut monter", 0xFFE8F0FF.toInt(), Aim.LOB, 6f, 0.7f, 10) { f, a, d ->
            Fire.lob(f, a, d, 6f, 220f, 0f, PV.FOAM, 0xFFFFFFFF.toInt(), 0.55f) { foam = true; r = 14f }
            f.m.sound(Sfx.BUBBLE, f.x, f.y)
        },
        WeaponDef(GRAPPLE, "Arbalète grappin", "Attaque et te propulse jusqu'à l'impact", 0xFF8B5A3C.toInt(), Aim.LINE, 8f, 0.9f, 8, width = 12f) { f, a, _ ->
            Fire.bullet(f, a, 1500f, 8f, 650f, 8f, PV.ARROW, 0xFFB0B0B8.toInt()) { grapple = true }
            f.m.sound(Sfx.SWING, f.x, f.y)
        },
        WeaponDef(FLAME, "Lance-flammes à paillettes", "Dégâts sur la durée", 0xFFFF5EC4.toInt(), Aim.CONE, 3.3f, 0.07f, 80, auto = true, spread = 30f, recoil = 20f) { f, a, _ ->
            Fire.bullet(f, a + rf(-0.22f, 0.22f), 520f, 3.3f, 85f, 12f, PV.FLAME, 0xFFFF5EC4.toInt()) { pierce = true; status = St.BURN; statusT = 2f; life = 0.6f; reflectable = false; tileDmg = 0.2f }
            f.m.sound(Sfx.FLAME, f.x, f.y, 0.5f)
        },
        WeaponDef(BOOMERANG, "Boomerang géant", "Revient, et touche à l'aller comme au retour", 0xFFFF9A3D.toInt(), Aim.LINE, 6.5f, 1.0f, 8, width = 30f) { f, a, _ ->
            Fire.bullet(f, a, 780f, 6.5f, 780f, 18f, PV.BOOMERANG, 0xFFFF9A3D.toInt()) { boomerang = true; life = 4f; maxTravel = 6.5f * T * f.rangeMul(); knock = 200f; tileDmg = 0.5f }
            f.m.sound(Sfx.SWING, f.x, f.y)
        },
        WeaponDef(CONFETTI, "Tromblon confettis", "Fusil à pompe qui aveugle", 0xFFB46CFF.toInt(), Aim.CONE, 4f, 0.9f, 10, spread = 36f, recoil = 380f) { f, a, _ ->
            for (i in 0 until 8) Fire.bullet(f, a + rf(-0.32f, 0.32f), rf(850f, 1050f), 4f, 250f, 7f, PV.CONFETTI, Projectile.randomConfettiColor()) { status = St.BLIND; statusT = 1.4f }
            f.m.sound(Sfx.SHOTGUN, f.x, f.y)
        },
        WeaponDef(BUBBLE, "Pistolet à bulles", "Enferme l'ennemi 1 s dans une bulle", 0xFF90E0FF.toInt(), Aim.LINE, 6.5f, 0.7f, 12) { f, a, _ ->
            Fire.bullet(f, a, 720f, 6.5f, 300f, 14f, PV.BUBBLE, 0xFF90E0FF.toInt()) { status = St.BUBBLE; statusT = 1f }
            f.m.sound(Sfx.BUBBLE, f.x, f.y)
        },
        WeaponDef(RAIL, "Railgun jouet", "Traverse les murs fins", 0xFF7CF0FF.toInt(), Aim.LINE, 12f, 1.6f, 6, width = 10f, recoil = 300f) { f, a, _ ->
            Fire.beam(f, a, 12f, 1600f, 10f, 0xFF7CF0FF.toInt(), true, 1)
            f.m.sound(Sfx.LASER, f.x, f.y)
        },
        WeaponDef(SUCKER, "Lanceur de ventouses", "Colle une mine sur l'ennemi", 0xFFFF5E7E.toInt(), Aim.LINE, 6.5f, 0.9f, 8) { f, a, _ ->
            Fire.bullet(f, a, 900f, 6.5f, 150f, 10f, PV.SUCKER, 0xFFFF5E7E.toInt()) { sticky = true }
            f.m.sound(Sfx.SHOOT, f.x, f.y)
        },
        WeaponDef(BAT, "Batte de baseball", "Renvoie les projectiles", 0xFFD2A36A.toInt(), Aim.ARC, 1.8f, 0.55f, 40, melee = true, spread = 140f, recoil = 0f) { f, a, _ ->
            Fire.melee(f, a, 1.8f, 140f, 900f, 950f, reflect = true, color = 0xFFD2A36A.toInt())
        },
        WeaponDef(HAMMER, "Marteau", "Lent mais dévastateur, étourdit", 0xFFB06B3A.toInt(), Aim.ARC, 1.9f, 0.95f, 999, melee = true, spread = 100f, recoil = 0f) { f, a, _ ->
            Fire.melee(f, a, 1.9f, 100f, 1500f, 1100f, stun = 0.6f, color = 0xFFFFE14D.toInt())
        },
        WeaponDef(SWORD, "Épée", "Large et rapide", 0xFFC8D4E8.toInt(), Aim.ARC, 2.0f, 0.45f, 999, melee = true, spread = 150f, recoil = 0f) { f, a, _ ->
            Fire.melee(f, a, 2.0f, 150f, 900f, 420f, color = 0xFFDDEEFF.toInt())
        },
        WeaponDef(PAN, "Poêle", "Renvoie les tirs, étourdit un peu", 0xFF4A4A55.toInt(), Aim.ARC, 1.6f, 0.55f, 999, melee = true, spread = 120f, recoil = 0f) { f, a, _ ->
            Fire.melee(f, a, 1.6f, 120f, 820f, 650f, reflect = true, stun = 0.3f, color = 0xFFFFFFFF.toInt())
        },
        WeaponDef(HUNTER, "Méga-blaster", "L'arme du chasseur", 0xFFFF3D3D.toInt(), Aim.LINE, 8f, 0.16f, 999, auto = true, recoil = 30f) { f, a, _ ->
            Fire.bullet(f, a + rf(-0.05f, 0.05f), 1300f, 8f, 480f, 9f, PV.BULLET, 0xFFFF3D3D.toInt())
            f.m.sound(Sfx.SHOOT, f.x, f.y, 0.6f)
        },
        WeaponDef(GOLDPAN, "Poêle en or", "Dernière arme : un coup suffit !", 0xFFFFD23F.toInt(), Aim.ARC, 1.8f, 0.6f, 999, melee = true, spread = 130f, recoil = 0f) { f, a, _ ->
            Fire.melee(f, a, 1.8f, 130f, 6000f, 900f, reflect = true, color = 0xFFFFD23F.toInt())
        }
    )
    val pickupable = intArrayOf(MAGNET, FOAM, GRAPPLE, FLAME, BOOMERANG, CONFETTI, BUBBLE, RAIL, SUCKER, BAT)
    val meleeOnly = intArrayOf(HAMMER, SWORD, PAN)
    val gunGame = intArrayOf(MAGNET, CONFETTI, BUBBLE, SUCKER, BOOMERANG, FLAME, GRAPPLE, RAIL, FOAM, BAT, GOLDPAN)
}

object Gadgets {
    const val TRAMPOLINE = 0; const val WALL = 1; const val DECOY = 2; const val TURRET = 3; const val JETPACK = 4
    const val PARACHUTE = 5; const val BANANA = 6; const val GLUE = 7; const val RADAR = 8
    val list = listOf(
        GadgetDef(TRAMPOLINE, "Trampoline portable", "Un saut géant", 0xFF5BD3FF.toInt(), 2),
        GadgetDef(WALL, "Mur gonflable", "Une couverture instantanée", 0xFFFF8CC6.toInt(), 2),
        GadgetDef(DECOY, "Leurre haricot", "Un mannequin qui attire les tirs", 0xFFFFC94D.toInt(), 1),
        GadgetDef(TURRET, "Tourelle jouet", "Tire toute seule pendant 10 s", 0xFF7CFF8A.toInt(), 1),
        GadgetDef(JETPACK, "Jetpack", "3 poussées", 0xFFB0B8C8.toInt(), 3),
        GadgetDef(PARACHUTE, "Parachute de secours", "Annule une chute dans le vide", 0xFFFF6B6B.toInt(), 1),
        GadgetDef(BANANA, "Peau de banane", "Fait glisser les ennemis", 0xFFFFE14D.toInt(), 3),
        GadgetDef(GLUE, "Grenade glue", "Englue une zone", 0xFFB6E35A.toInt(), 2),
        GadgetDef(RADAR, "Radar", "Révèle les ennemis proches", 0xFF35C6F4.toInt(), 2)
    )
}

object Items {
    const val SODA = 0; const val LOLLIPOP = 1; const val SHIELD = 2; const val MUSHROOM = 3
    val list = listOf(
        ItemDef(SODA, "Soda vitesse", "Bonus de vitesse", 0xFFFF5E7E.toInt()),
        ItemDef(LOLLIPOP, "Sucette", "Régénère la vie", 0xFFFF8CC6.toInt()),
        ItemDef(SHIELD, "Bouclier en bulle", "Absorbe les dégâts", 0xFF6BD3FF.toInt()),
        ItemDef(MUSHROOM, "Champignon géant", "Taille +50 % pendant 10 s", 0xFFE84545.toInt())
    )
}

object Heroes {
    private fun c(vararg v: Long) = IntArray(v.size) { v[it].toInt() }

    val list: List<HeroDef> = listOf(
        HeroDef(0, "Gumbo", "Tank", "Gros chewing-gum rose", 5600f, 228f, c(0xFFFF7EC4, 0xFF7EE0FF, 0xFFB5FF6B),
            WeaponDef(100, "Crachat de gomme", "5 boules de gomme à courte portée", 0xFFFF7EC4.toInt(), Aim.CONE, 3.3f, 0.3f, 3, 1.3f, spread = 32f) { f, a, _ ->
                Fire.spread(f, a, 5, 32f, 720f, 3.3f, 340f, 10f, PV.GUM, 0xFFFF7EC4.toInt()); f.m.sound(Sfx.BUBBLE, f.x, f.y)
            }, "Bulle rebondissante", "Se gonfle en bulle et rebondit sur les ennemis", 4600f, Aim.LINE, 5f, 1.8f),
        HeroDef(1, "Volt", "Soutien", "Pile électrique sur pattes", 3400f, 240f, c(0xFFFFD23F, 0xFF7CF0FF, 0xFFFF7A3D),
            WeaponDef(101, "Arc électrique", "Éclair qui rebondit sur un 2e ennemi", 0xFFFFE14D.toInt(), Aim.LINE, 7f, 0.35f, 3, 1.35f, width = 14f) { f, a, _ ->
                Fire.bullet(f, a, 1150f, 7f, 740f, 9f, PV.BOLT, 0xFFFFE14D.toInt()) { chain = 1 }; f.m.sound(Sfx.ZAP, f.x, f.y)
            }, "Champ de recharge", "Crée un champ qui recharge les supers alliés et grille les ennemis", 4000f, Aim.SELF, 3f, 5f),
        HeroDef(2, "Mama Tortue", "Tank mobile", "Tortue grand-mère en rollers", 5000f, 262f, c(0xFF6CC56A, 0xFF4FB5F0, 0xFFE0A050),
            WeaponDef(102, "Coup de sac à main", "Frappe au corps à corps et repousse", 0xFFB46CFF.toInt(), Aim.ARC, 1.7f, 0.4f, 3, 1.2f, spread = 110f, melee = true) { f, a, _ ->
                Fire.melee(f, a, 1.7f, 110f, 1150f, 520f, color = 0xFFB46CFF.toInt())
            }, "Carapace-boule", "Rentre dans sa carapace et fonce comme une boule", 4200f, Aim.LINE, 6f, 1.2f),
        HeroDef(3, "Pixel", "Assassin", "Cube rétro 8-bit", 3200f, 272f, c(0xFF35C6F4, 0xFFFF5E7E, 0xFF7CFF8A),
            WeaponDef(103, "Rafale 8-bit", "3 pixels en rafale", 0xFF35C6F4.toInt(), Aim.LINE, 5.2f, 0.32f, 3, 1.15f, width = 12f) { f, a, _ ->
                for (k in 0 until 3) Fire.bullet(f, a + rf(-0.04f, 0.04f), 1000f - k * 40f, 5.2f, 400f, 7f, PV.PIXEL, 0xFF35C6F4.toInt()) { x -= cosf(a) * k * 26f; y -= sinf(a) * k * 26f }
                f.m.sound(Sfx.SHOOT, f.x, f.y, 1f, 1.4f)
            }, "Téléportation", "Se téléporte de quelques mètres et devient invisible 2 s", 3600f, Aim.POINT, 4.5f, 2.5f),
        HeroDef(4, "Chef Boulette", "Soigneur", "Boulette de riz cuisinier", 3600f, 240f, c(0xFFFFFFFF, 0xFFFFE6B0, 0xFFD9F2FF),
            WeaponDef(104, "Grain de riz", "Boule de riz qui éclabousse et soigne les alliés", 0xFFFFFFFF.toInt(), Aim.LOB, 6.5f, 0.45f, 3, 1.4f) { f, a, d ->
                Fire.lob(f, a, d, 6.5f, 920f, 1.1f, PV.RICE, 0xFFFFFFFF.toInt()) { heal = 450f }; f.m.sound(Sfx.JUMP, f.x, f.y, 0.6f)
            }, "Onigiris", "Lance des onigiris qui soignent", 3800f, Aim.LOB, 6f, 4.5f),
        HeroDef(5, "Capitaine Bouchon", "Artilleur", "Bouchon de champagne pirate", 3200f, 235f, c(0xFFD2A36A, 0xFFB0B8C8, 0xFF8B5A3C),
            WeaponDef(105, "Bouchon rebondissant", "Rebondit une fois puis explose", 0xFFD2A36A.toInt(), Aim.LOB, 7f, 0.5f, 3, 1.5f) { f, a, d ->
                Fire.lob(f, a, d, 7f, 1000f, 1.0f, PV.CORK, 0xFFD2A36A.toInt(), 0.6f) { bounces = 1 }; f.m.sound(Sfx.POP, f.x, f.y, 0.5f)
            }, "Super bouchon", "Tire un bouchon géant qui explose en zone", 4300f, Aim.LOB, 7.5f, 5f),
        HeroDef(6, "Nuage", "Contrôle", "Nuage boudeur", 3400f, 242f, c(0xFFDCE6F5, 0xFF8A93A8, 0xFFFFC6E6),
            WeaponDef(106, "Grêle", "3 grêlons qui ralentissent", 0xFFDDF4FF.toInt(), Aim.CONE, 5.5f, 0.35f, 3, 1.35f, spread = 18f) { f, a, _ ->
                Fire.spread(f, a, 3, 18f, 920f, 5.5f, 460f, 8f, PV.HAIL, 0xFFDDF4FF.toInt()) { status = St.SLOW; statusT = 0.9f }
                f.m.sound(Sfx.SHOOT, f.x, f.y, 0.8f, 1.2f)
            }, "Averse", "Fait pleuvoir pour ralentir et éteindre les feux", 4000f, Aim.POINT, 6f, 4.5f),
        HeroDef(7, "Rex-Tracteur", "Constructeur", "Tracteur avec une crête de dino", 4600f, 228f, c(0xFF7CC243, 0xFFE84545, 0xFFFFC23D),
            WeaponDef(107, "Mottes de terre", "2 mottes de terre", 0xFF8B5A3C.toInt(), Aim.LINE, 5.5f, 0.35f, 3, 1.3f, width = 26f) { f, a, _ ->
                Fire.spread(f, a, 2, 8f, 860f, 5.5f, 640f, 11f, PV.DIRT, 0xFF8B5A3C.toInt()); f.m.sound(Sfx.THUD, f.x, f.y, 0.5f)
            }, "Mur instantané", "Pose un mur de briques instantané", 3000f, Aim.POINT, 4f, 3.5f),
        HeroDef(8, "Lady Mousse", "Survie", "Éponge chic", 4200f, 240f, c(0xFFFFE14D, 0xFFFF8CC6, 0xFF8CE0C6),
            WeaponDef(108, "Bulles de savon", "Grosse bulle lente qui traverse", 0xFFB8F0FF.toInt(), Aim.LINE, 6f, 0.45f, 3, 1.4f, width = 34f) { f, a, _ ->
                Fire.bullet(f, a, 520f, 6f, 600f, 17f, PV.SOAP, 0xFFB8F0FF.toInt()) { pierce = true }; f.m.sound(Sfx.BUBBLE, f.x, f.y)
            }, "Absorption", "Absorbe les dégâts puis les renvoie", 4200f, Aim.SELF, 3f, 2.5f),
        HeroDef(9, "Glitch", "Trickster", "Personnage qui « bugue »", 3300f, 255f, c(0xFFB46CFF, 0xFF35F4A0, 0xFFFF7A3D),
            WeaponDef(109, "Rayon bugué", "Rayon qui traverse les ennemis", 0xFFB46CFF.toInt(), Aim.LINE, 7f, 0.4f, 3, 1.35f, width = 12f) { f, a, _ ->
                Fire.beam(f, a, 7f, 800f, 8f, 0xFFB46CFF.toInt(), true, 0); f.m.sound(Sfx.LASER, f.x, f.y, 0.6f, 1.3f)
            }, "Clones leurres", "Crée 3 clones leurres", 3800f, Aim.SELF, 3f, 4f),
        HeroDef(10, "Mamie Météo", "Sniper", "Vieille dame au parapluie", 2900f, 236f, c(0xFFB46CFF, 0xFFFF5E7E, 0xFF35C6F4),
            WeaponDef(110, "Tir de parapluie", "Tir de très longue portée", 0xFFFFFFFF.toInt(), Aim.LINE, 10f, 0.5f, 3, 1.6f, width = 10f) { f, a, _ ->
                Fire.bullet(f, a, 1700f, 10f, 1350f, 7f, PV.UMBRELLA, 0xFFFFFFFF.toInt()); f.m.sound(Sfx.SHOOT, f.x, f.y, 1f, 0.8f)
            }, "Parapluie", "Le parapluie sert de bouclier et de planeur", 3600f, Aim.LINE, 4f, 7.5f),
        HeroDef(11, "Bolt le Toaster", "Dégâts", "Grille-pain robot", 3700f, 240f, c(0xFFD8DEE8, 0xFFFF5E7E, 0xFF6CC56A),
            WeaponDef(111, "Toasts brûlants", "2 toasts qui brûlent", 0xFFE0A050.toInt(), Aim.LINE, 6f, 0.35f, 3, 1.3f, width = 20f) { f, a, _ ->
                for (k in 0 until 2) Fire.bullet(f, a + (k - 0.5f) * 0.08f, 960f, 6f, 640f, 10f, PV.TOAST, 0xFFE0A050.toInt()) { status = St.BURN; statusT = 0.8f; x -= cosf(a) * k * 20f; y -= sinf(a) * k * 20f }
                f.m.sound(Sfx.SHOOT, f.x, f.y, 1f, 0.9f)
            }, "Tartines explosives", "Éjecte des tartines explosives", 4200f, Aim.LOB, 6f, 4f)
    )

    /** The plain "haricot" used for unarmed prey and decoys. */
    val bean = HeroDef(12, "Haricot", "Proie", "Haricot sans arme", 3000f, 250f, c(0xFFFFC94D, 0xFF7CFF8A, 0xFF6BD3FF),
        WeaponDef(112, "Mains nues", "Pas d'arme !", 0xFFFFFFFF.toInt(), Aim.ARC, 1.1f, 0.6f, 3, 1.2f, melee = true, spread = 90f) { f, a, _ ->
            Fire.melee(f, a, 1.1f, 90f, 120f, 600f)
        }, "Roulade", "Une roulade rapide", 2500f, Aim.LINE, 3f, 1.5f)

    /** Infected slime used in Infection mode. */
    val slime = HeroDef(13, "Infecté", "Slime", "Slime contagieux", 3200f, 285f, c(0xFF7BE84A, 0xFF7BE84A, 0xFF7BE84A),
        WeaponDef(113, "Câlin gluant", "Contamine au contact", 0xFF7BE84A.toInt(), Aim.ARC, 1.2f, 0.4f, 3, 0.6f, melee = true, spread = 120f) { f, a, _ ->
            Fire.melee(f, a, 1.2f, 120f, 500f, 300f, color = 0xFF7BE84A.toInt())
        }, "Bond gluant", "Un grand bond", 2500f, Aim.LINE, 4f, 1.2f)

    fun byId(id: Int): HeroDef = when (id) { 12 -> bean; 13 -> slime; else -> list[id.coerceIn(0, list.size - 1)] }
}

fun randomAngle() = rf() * TAU
