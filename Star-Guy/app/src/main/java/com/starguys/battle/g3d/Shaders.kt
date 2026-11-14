package com.starguys.battle.g3d

/** GLSL ES 3.00 sources. Kept in Kotlin strings so the build has no asset files. */
object Shaders {
    const val SHADOW_SIZE = 2048

    private const val HEAD = "#version 300 es\nprecision highp float;\nprecision highp int;\n"

    /** shared lighting / fog helpers (fragment) */
    private const val LIGHT = """
uniform vec3 uSun;
uniform vec3 uSunCol;
uniform vec3 uSkyAmb;
uniform vec3 uGndAmb;
uniform vec3 uCam;
uniform vec3 uFogCol;
uniform vec2 uFog;
uniform float uTime;
uniform float uShadowOn;
uniform highp sampler2DShadow uShadow;

float shadowAt(vec4 ls, float ndl) {
    if (uShadowOn < 0.5) return 1.0;
    vec3 p = ls.xyz / ls.w * 0.5 + 0.5;
    if (p.x <= 0.002 || p.x >= 0.998 || p.y <= 0.002 || p.y >= 0.998 || p.z >= 1.0) return 1.0;
    float bias = 0.0009 + 0.0022 * (1.0 - clamp(ndl, 0.0, 1.0));
    float t = 1.0 / 2048.0;
    float s = texture(uShadow, vec3(p.xy + vec2(-0.7, -0.7) * t, p.z - bias));
    s += texture(uShadow, vec3(p.xy + vec2(0.7, -0.7) * t, p.z - bias));
    s += texture(uShadow, vec3(p.xy + vec2(-0.7, 0.7) * t, p.z - bias));
    s += texture(uShadow, vec3(p.xy + vec2(0.7, 0.7) * t, p.z - bias));
    return s * 0.25;
}

vec3 shade(vec3 base, vec3 N, vec3 W, float sh, float rimAmt, float specAmt) {
    float ndl = dot(N, uSun);
    float d = smoothstep(-0.02, 0.32, ndl) * sh;
    vec3 amb = mix(uGndAmb, uSkyAmb, N.y * 0.5 + 0.5);
    vec3 V = normalize(uCam - W);
    vec3 H = normalize(uSun + V);
    float sp = smoothstep(0.55, 0.75, pow(max(dot(N, H), 0.0), 28.0)) * d * specAmt;
    float rim = smoothstep(0.45, 0.95, 1.0 - max(dot(N, V), 0.0)) * rimAmt * (0.35 + 0.65 * d);
    return base * (amb + uSunCol * d) + vec3(sp * 0.28) + rim * vec3(0.75, 0.82, 1.0) * 0.32;
}

vec3 applyFog(vec3 c, vec3 W) {
    float dist = length(W - uCam);
    float f = smoothstep(uFog.x, uFog.y, dist);
    return mix(c, uFogCol, f);
}

float hash2(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
float noise2(vec2 p) {
    vec2 i = floor(p); vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash2(i), hash2(i + vec2(1.0, 0.0)), u.x), mix(hash2(i + vec2(0.0, 1.0)), hash2(i + vec2(1.0, 1.0)), u.x), u.y);
}
"""

    // ---------------------------------------------------------------- instanced meshes
    val meshVS = HEAD + """
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNrm;
layout(location = 2) in vec4 iM0;
layout(location = 3) in vec4 iM1;
layout(location = 4) in vec4 iM2;
layout(location = 5) in vec4 iM3;
layout(location = 6) in vec4 iCol;
uniform mat4 uVP;
uniform mat4 uLightVP;
uniform vec3 uCam;
uniform float uOutline;
out vec3 vN;
out vec3 vW;
out vec4 vCol;
out vec4 vLS;
void main() {
    vec3 c0 = iM0.xyz; vec3 c1 = iM1.xyz; vec3 c2 = iM2.xyz;
    vec3 n = c0 * (aNrm.x / max(dot(c0, c0), 1e-6)) + c1 * (aNrm.y / max(dot(c1, c1), 1e-6)) + c2 * (aNrm.z / max(dot(c2, c2), 1e-6));
    n = normalize(n);
    vec4 w = mat4(iM0, iM1, iM2, iM3) * vec4(aPos, 1.0);
    if (uOutline > 0.0) {
        float d = length(w.xyz - uCam);
        float sz = min(min(length(c0), length(c1)), length(c2));
        w.xyz += n * min(clamp(d * uOutline, 0.45, 3.2), sz * 0.12 + 0.3);
    }
    vN = n; vW = w.xyz; vCol = iCol;
    vLS = uLightVP * w;
    gl_Position = uVP * w;
}
"""

    val meshFS = HEAD + LIGHT + """
in vec3 vN;
in vec3 vW;
in vec4 vCol;
in vec4 vLS;
uniform float uMode;
out vec4 oCol;
void main() {
    vec3 base = vCol.rgb;
    if (uMode > 2.5) { // outline: darkened tint of the base colour
        oCol = vec4(applyFog(base * 0.22 + vec3(0.03, 0.02, 0.06), vW), 1.0);
        return;
    }
    if (uMode > 1.5) { // glow (additive)
        oCol = vec4(applyFog(base, vW) * vCol.a, 1.0);
        return;
    }
    vec3 N = normalize(vN);
    if (!gl_FrontFacing) N = -N;
    if (vCol.a > 1.5) { // emissive solid
        oCol = vec4(applyFog(base, vW), 1.0);
        return;
    }
    float ndl = dot(N, uSun);
    float sh = shadowAt(vLS, ndl);
    vec3 c = shade(base, N, vW, sh, 1.0, 1.0);
    float a = 1.0;
    if (uMode > 0.5) { // glass
        a = vCol.a;
        vec3 V = normalize(uCam - vW);
        float fr = pow(1.0 - abs(dot(N, V)), 2.0);
        c += vec3(0.35) * fr;
        a = clamp(a + fr * 0.35, 0.0, 1.0);
    }
    oCol = vec4(applyFog(c, vW), a);
}
"""

    // ---------------------------------------------------------------- world chunks
    val worldVS = HEAD + """
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNrm;
layout(location = 2) in vec4 aCol;
uniform mat4 uVP;
uniform mat4 uLightVP;
uniform float uTime;
out vec3 vN;
out vec3 vW;
out vec4 vCol;
out vec4 vLS;
void main() {
    vec3 p = aPos;
    float mat = aCol.a;
    if (mat > 0.05 && mat < 0.45 && aNrm.y > 0.5) { // liquids gently wave
        p.y += sin(uTime * 2.2 + p.x * 0.05 + p.z * 0.037) * 1.6 + sin(uTime * 3.1 - p.x * 0.031 + p.z * 0.06) * 1.0;
    }
    vN = aNrm; vW = p; vCol = aCol;
    vLS = uLightVP * vec4(p, 1.0);
    gl_Position = uVP * vec4(p, 1.0);
}
"""

    val worldFS = HEAD + LIGHT + """
in vec3 vN;
in vec3 vW;
in vec4 vCol;
in vec4 vLS;
uniform float uDetail;
out vec4 oCol;
void main() {
    vec3 N = normalize(vN);
    vec3 base = vCol.rgb;
    float mat = vCol.a;
    // 0.0 ground, 0.1 water, 0.2 lava, 0.3 paint/slime, 0.5 wall, 0.6 crate, 0.7 brick, 0.8 metal, 0.9 plain
    vec2 q = vW.xz;
    if (N.y > 0.5) {
        if (mat < 0.05) {
            float n = noise2(q * 0.09) * 0.6 + noise2(q * 0.31) * 0.4;
            base *= 0.9 + 0.2 * n * uDetail + 0.0;
            float sp = step(0.93, hash2(floor(q * 0.22)));
            base = mix(base, base * 1.18, sp * 0.5 * uDetail);
            vec2 g = abs(fract(q / 64.0) - 0.5);
            float edge = smoothstep(0.47, 0.5, max(g.x, g.y));
            base *= 1.0 - edge * 0.12 * uDetail;
        } else if (mat < 0.15) { // water
            float w = noise2(q * 0.035 + vec2(uTime * 0.25, uTime * 0.17)) + noise2(q * 0.07 - vec2(uTime * 0.2, 0.0));
            base = mix(base, vec3(0.9, 0.97, 1.0), smoothstep(1.25, 1.6, w) * 0.6);
        } else if (mat < 0.25) { // lava jelly: emissive bubbles
            float w = noise2(q * 0.05 + vec2(0.0, uTime * 0.3));
            vec3 c = mix(base, vec3(1.0, 0.78, 0.86), smoothstep(0.6, 0.9, w));
            oCol = vec4(applyFog(c * 1.15, vW), 1.0);
            return;
        } else if (mat < 0.35) {
            float w = noise2(q * 0.06 + vec2(uTime * 0.3, uTime * 0.21));
            base = mix(base, base * 1.35, smoothstep(0.65, 0.95, w));
        }
    } else {
        if (mat > 0.65 && mat < 0.75) { // bricks
            vec2 b = vec2((vW.x + vW.z) / 21.0, vW.y / 12.0);
            b.x += floor(b.y) * 0.5;
            vec2 f = abs(fract(b) - 0.5);
            base *= 1.0 - 0.22 * smoothstep(0.42, 0.5, max(f.x * 0.9, f.y));
        } else if (mat > 0.45 && mat < 0.55) {
            float n = noise2(vec2(vW.x + vW.z, vW.y) * 0.08);
            base *= 0.93 + 0.12 * n * uDetail;
            base *= 1.0 - 0.1 * smoothstep(0.42, 0.5, abs(fract(vW.y / 30.0) - 0.5));
        } else if (mat > 0.55 && mat < 0.65) { // crate planks
            float pl = abs(fract((vW.y) / 16.0) - 0.5);
            base *= 1.0 - 0.18 * smoothstep(0.44, 0.5, pl);
        }
    }
    float ndl = dot(N, uSun);
    float sh = shadowAt(vLS, ndl);
    float spec = (mat > 0.05 && mat < 0.15) ? 2.5 : 0.25;
    vec3 c = shade(base, N, vW, sh, 0.2, spec);
    oCol = vec4(applyFog(c, vW), 1.0);
}
"""

    // ---------------------------------------------------------------- shadow depth
    val depthInstVS = HEAD + """
layout(location = 0) in vec3 aPos;
layout(location = 2) in vec4 iM0;
layout(location = 3) in vec4 iM1;
layout(location = 4) in vec4 iM2;
layout(location = 5) in vec4 iM3;
uniform mat4 uLightVP;
void main() { gl_Position = uLightVP * (mat4(iM0, iM1, iM2, iM3) * vec4(aPos, 1.0)); }
"""
    val depthWorldVS = HEAD + """
layout(location = 0) in vec3 aPos;
uniform mat4 uLightVP;
void main() { gl_Position = uLightVP * vec4(aPos, 1.0); }
"""
    val depthFS = HEAD + "out vec4 oCol;\nvoid main() { oCol = vec4(1.0); }\n"

    // ---------------------------------------------------------------- sprites
    val spriteVS = HEAD + """
layout(location = 0) in vec2 aCorner;
layout(location = 1) in vec4 iA;
layout(location = 2) in vec4 iB;
layout(location = 3) in vec4 iC;
uniform mat4 uVP;
uniform vec3 uCam;
uniform vec3 uRight;
uniform vec3 uUp;
out vec2 vUV;
out vec4 vCol;
out vec3 vW;
flat out int vShape;
out float vParam;
void main() {
    int kind = int(iA.w + 0.5);
    int mode = kind / 16;
    vShape = kind - mode * 16;
    vec2 c = aCorner;
    vec3 w;
    vParam = iB.w;
    if (mode == 0) {
        float cr = cos(iB.z); float sr = sin(iB.z);
        vec2 r = vec2(c.x * cr - c.y * sr, c.x * sr + c.y * cr);
        w = iA.xyz + uRight * (r.x * iB.x) + uUp * (r.y * iB.y);
    } else if (mode == 1) {
        float cr = cos(iB.z); float sr = sin(iB.z);
        vec2 r = vec2(c.x * iB.x, c.y * iB.y);
        w = iA.xyz + vec3(r.x * cr - r.y * sr, 0.0, r.x * sr + r.y * cr);
    } else {
        vec3 a = iA.xyz; vec3 b = iB.xyz;
        vec3 mid = mix(a, b, c.x * 0.5 + 0.5);
        vec3 side = cross(b - a, uCam - mid);
        float l = length(side);
        side = l > 1e-4 ? side / l : uRight;
        w = mid + side * (c.y * iB.w);
        vParam = 0.0;
    }
    vUV = c; vCol = iC; vW = w;
    gl_Position = uVP * vec4(w, 1.0);
}
"""

    val spriteFS = HEAD + """
in vec2 vUV;
in vec4 vCol;
in vec3 vW;
flat in int vShape;
in float vParam;
uniform vec3 uCam;
uniform vec3 uFogCol;
uniform vec2 uFog;
uniform float uAdd;
out vec4 oCol;
float hash2(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
void main() {
    vec2 uv = vUV;
    float r = length(uv);
    float a = 0.0;
    vec3 col = vCol.rgb;
    if (vShape == 0) { float k = clamp(1.0 - r, 0.0, 1.0); a = k * k; }
    else if (vShape == 1) { a = 1.0 - smoothstep(0.86, 1.0, r); col *= mix(1.0, 0.72, smoothstep(0.6, 0.95, r)); }
    else if (vShape == 2) { float t = max(vParam, 0.06); a = 1.0 - smoothstep(t * 0.5, t * 0.5 + 0.06, abs(r - (1.0 - t * 0.5))); }
    else if (vShape == 3) {
        float ang = atan(uv.y, uv.x);
        float k = pow(abs(cos(ang * 2.5)), 3.0);
        a = 1.0 - smoothstep(0.0, 0.08, r - mix(0.38, 1.0, k));
    }
    else if (vShape == 4) { a = step(max(abs(uv.x), abs(uv.y)), 1.0); }
    else if (vShape == 5) {
        float ang = abs(atan(uv.y, uv.x));
        float e = smoothstep(vParam, vParam - 0.04, ang) * (1.0 - smoothstep(0.96, 1.0, r));
        float rim = smoothstep(0.88, 0.97, r) + smoothstep(vParam - 0.08, vParam - 0.02, ang);
        a = e * (0.55 + 0.45 * clamp(rim, 0.0, 1.0));
    }
    else if (vShape == 6) {
        vec2 q = abs(uv);
        float e = 1.0 - smoothstep(0.92, 1.0, max(q.x, q.y));
        float rim = smoothstep(0.75, 0.9, q.y);
        a = e * (0.5 + 0.5 * rim);
    }
    else if (vShape == 7) {
        float n = hash2(floor((uv + 2.0) * 3.0));
        float k = clamp(1.0 - r * (0.85 + 0.25 * n), 0.0, 1.0);
        a = smoothstep(0.0, 0.35, k);
        col *= 0.85 + 0.25 * (1.0 - r);
    }
    else if (vShape == 8) {
        float y = abs(uv.y);
        float x = uv.x + y * 0.9;
        a = step(-0.2, x) * step(x, 0.35) * step(y, 0.85);
    }
    else if (vShape == 9) { float k = 1.0 - abs(uv.y); a = smoothstep(0.0, 0.6, k); col = mix(col, vec3(1.0), smoothstep(0.55, 0.95, k) * 0.7); }
    else if (vShape == 10) { float k = clamp(1.0 - r, 0.0, 1.0); a = smoothstep(0.0, 0.7, k); col = vec3(0.04, 0.03, 0.1); }
    else if (vShape == 11) { float k = clamp(1.0 - abs(uv.x) * 3.0, 0.0, 1.0) * clamp(1.0 - abs(uv.y), 0.0, 1.0); a = k; }
    a *= vCol.a;
    if (a < 0.003) discard;
    float dist = length(vW - uCam);
    float f = smoothstep(uFog.x, uFog.y, dist);
    if (uAdd > 0.5) { oCol = vec4(col * a * (1.0 - f), 1.0); }
    else { oCol = vec4(mix(col, uFogCol, f), a); }
}
"""

    // ---------------------------------------------------------------- sky
    val skyVS = HEAD + """
layout(location = 0) in vec2 aCorner;
out vec2 vNdc;
void main() { vNdc = aCorner; gl_Position = vec4(aCorner, 1.0, 1.0); }
"""

    val skyFS = HEAD + """
in vec2 vNdc;
uniform mat4 uInvVP;
uniform vec3 uCam;
uniform vec3 uZenith;
uniform vec3 uHorizon;
uniform vec3 uAbyss;
uniform vec3 uSun;
uniform float uTime;
uniform float uStars;
uniform float uClouds;
out vec4 oCol;
float hash2(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
float noise2(vec2 p) {
    vec2 i = floor(p); vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash2(i), hash2(i + vec2(1.0, 0.0)), u.x), mix(hash2(i + vec2(0.0, 1.0)), hash2(i + vec2(1.0, 1.0)), u.x), u.y);
}
void main() {
    vec4 p = uInvVP * vec4(vNdc, 1.0, 1.0);
    vec3 d = normalize(p.xyz / p.w - uCam);
    float h = d.y;
    vec3 c;
    if (h >= 0.0) c = mix(uHorizon, uZenith, pow(clamp(h, 0.0, 1.0), 0.55));
    else c = mix(uHorizon, uAbyss, pow(clamp(-h * 2.5, 0.0, 1.0), 0.6));
    float s = max(dot(d, uSun), 0.0);
    c += vec3(1.0, 0.9, 0.7) * (pow(s, 900.0) * 1.6 + pow(s, 24.0) * 0.25);
    if (uClouds > 0.0 && h > 0.02) {
        vec2 q = d.xz / (h + 0.12) * 2.2 + vec2(uTime * 0.012, uTime * 0.004);
        float n = noise2(q) * 0.55 + noise2(q * 2.3) * 0.3 + noise2(q * 5.1) * 0.15;
        float cl = smoothstep(0.52, 0.78, n) * smoothstep(0.02, 0.22, h) * uClouds;
        vec3 cc = mix(vec3(1.0), uHorizon, 0.25) * (0.88 + 0.12 * n);
        c = mix(c, cc, cl * 0.85);
    }
    if (uStars > 0.0) {
        vec2 q = vec2(atan(d.z, d.x) * 160.0, asin(clamp(d.y, -1.0, 1.0)) * 160.0);
        float st = step(0.975, hash2(floor(q))) * (1.0 - smoothstep(0.08, 0.22, length(fract(q) - 0.5)));
        float tw = 0.6 + 0.4 * sin(uTime * 3.0 + hash2(floor(q) + 7.0) * 30.0);
        c += vec3(st * tw * uStars * smoothstep(-0.3, 0.1, h + 0.3));
    }
    oCol = vec4(c, 1.0);
}
"""
}
