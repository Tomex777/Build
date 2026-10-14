package com.night.endless.engine.render

/**
 * Mobile GLES3 material for MOLA / LOLA surface meshes. Kilometer-scale
 * geometry is sampled from spacecraft DEM tiles. Colors, regolith grains
 * and wind streaks are deliberately illustrative until georegistered orbital
 * reflectance imagery and rover-scale photo texture tiles are available.
 *
 * Uses derivative-aware fine shading, stable planet coordinates (not
 * screen-space noise), diffuse rock response and Mars-only dust extinction.
 */
internal object SurfaceMaterialShaders {
    const val VERTEX = """#version 300 es
layout(location=0) in vec3 aPosition;
layout(location=1) in vec3 aNormal;
layout(location=2) in vec3 aColor;
uniform mat4 uVp;
uniform vec2 uOffset;
uniform float uPlanetRadiusUnits; // km planetary radius divided by 3km/world unit
out highp vec3 vPosition;
out highp vec3 vNormal;
out highp vec3 vColor;
out highp float vDistance;

void main() {
    vec3 cameraRelative = vec3(aPosition.x + uOffset.x,
                              aPosition.y, aPosition.z + uOffset.y);
    // Local tangent approximation to the actual curved lunar/Martian globe.
    // Beyond ~4 km on Mars and ~3 km on the Moon, curvature pushes distant
    // terrain below the visible horizon instead of forming a fake ceiling.
    float radius = max(uPlanetRadiusUnits, 1.0);
    float surfaceDistance2 = dot(cameraRelative.xz, cameraRelative.xz);
    cameraRelative.y -= surfaceDistance2 / (2.0 * radius);
    gl_Position = uVp * vec4(cameraRelative, 1.0);
    vPosition = aPosition;
    vNormal = normalize(aNormal +
        vec3(cameraRelative.x / radius, 0.0, cameraRelative.z / radius));
    vColor = aColor;
    vDistance = length(cameraRelative.xz);
}
"""

    const val FRAGMENT = """#version 300 es
precision highp float;

in highp vec3 vPosition;
in highp vec3 vNormal;
in highp vec3 vColor;
in highp float vDistance;
uniform vec3 uLight;
uniform int uWorld; // 0 = Mars; 1 = Moon
uniform int uPatchType; // 0 = coarse landscape; 1 = foreground; 2 = stones
uniform vec2 uViewAnchor;
uniform vec2 uMidAnchor;
uniform sampler2D uLandingAlbedo;
uniform int uHasLandingAlbedo;
out vec4 fragColor;

// Hash uses fixed surface coordinates, so regolith patterns do not swim.
float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}
float noise2(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(
        mix(hash21(i), hash21(i + vec2(1.0, 0.0)), u.x),
        mix(hash21(i + vec2(0.0, 1.0)), hash21(i + 1.0), u.x), u.y
    );
}

// Derivative-filtered noise prevents subpixel grains from creating an
// aggressive moire texture across kilometer-distant planetary slopes.
float noiseFiltered(vec2 coordinates) {
    float footprint = max(length(dFdx(coordinates)), length(dFdy(coordinates)));
    return mix(noise2(coordinates), 0.5, smoothstep(0.50, 1.55, footprint));
}

float geologicalDetail(vec2 pos) {
    float low = noiseFiltered(pos * 18.0);
    float medium = noiseFiltered(pos * 115.0);
    float detail = noiseFiltered(pos * 1400.0);
    return low * 0.48 + medium * 0.34 + detail * 0.18;
}

void main() {
    if (uPatchType == 3) {
        // Ground-hugging rock contact shadows. Smoothly taper opacity at
        // ellipse edges to avoid cartoon-hard circles and artificial halos.
        fragColor = vec4(vec3(0.022, 0.019, 0.018), clamp(vColor.r, 0.0, 0.38));
        return;
    }
    // Avoid the precision loss and visible depth-fighting when meter-scale
    // foreground triangles overlap the orbital-resolution DEM grid.
    if (uPatchType == 0 && max(
        abs(vPosition.x - uMidAnchor.x),
        abs(vPosition.z - uMidAnchor.y)) < 0.546) discard;

    if (uPatchType == 4 && max(
        abs(vPosition.x - uViewAnchor.x),
        abs(vPosition.z - uViewAnchor.y)) < 0.0188) discard;
    vec3 N = normalize(vNormal);
    vec3 sun = normalize(uLight);
    vec2 ground = vPosition.xz;
    float granules = geologicalDetail(ground);
    float areaNoise = noiseFiltered(ground * 3.6);
    float grain = noiseFiltered(ground * 470.0);

    // Small-scale bump derived from the *same* geographic material pattern.
    // Prevent unstable sampling derivatives at orbital/surface extremes.
    float detailHeight = (granules - 0.5);
    vec3 dX = dFdx(vPosition);
    vec3 dY = dFdy(vPosition);
    vec3 perturb = cross(dY, N) * dFdx(detailHeight)
                 + cross(N, dX) * dFdy(detailHeight);
    float span = max(length(cross(dX, dY)), 1e-7);
    // Bump normals in distant coarse spacecraft DEM were producing black/
    // white checkerboard bands under the Moon's very low Sun. There is
    // simply no orbital imagery for centimetre-scale normal bumps at
    // kilometer ranges: disable them outside the meter-level foreground.
    float nearDetail = uPatchType == 1
        ? (1.0 - smoothstep(0.015, 0.065, vDistance))
        : (uPatchType == 4
           ? 0.12 * (1.0 - smoothstep(0.02, 0.12, vDistance))
           : 0.0);
    vec3 microNormal = normalize(N + perturb *
         (uWorld == 0 ? 0.018 : 0.025) * nearDetail / span);
    // Keep relief normals in the actual upward-facing terrain hemisphere.
    if (dot(microNormal, N) < 0.14) microNormal = normalize(microNormal + N);

    // Basalt outcrops, iron-oxide fines, and wind-blown surface streaks
    // are approximate physical palettes, not claimed rover photography.
    vec3 albedo;
    float steepness = 1.0 - clamp(N.y, 0.0, 1.0);
    if (uWorld == 0) {
        float darkBasalt = smoothstep(0.34, 0.75,
            areaNoise * 0.55 + steepness * 0.35 + granules * 0.10);
        vec3 rustDust = vec3(0.41, 0.195, 0.105);
        vec3 ferricSand = vec3(0.58, 0.295, 0.145);
        vec3 rock = vec3(0.24, 0.18, 0.15);
        float dunes = smoothstep(0.20, 0.83, noiseFiltered(ground * vec2(80.0, 23.0)));
        albedo = mix(rustDust, ferricSand, dunes * 0.55);
        albedo = mix(albedo, rock, darkBasalt * 0.63);
        albedo *= 0.76 + granules * 0.52;
        albedo *= mix(1.0, 0.83, grain * grain);
    } else {
        float highlands = smoothstep(0.30, 0.72,
            areaNoise * 0.65 + steepness * 0.35);
        vec3 regolith = vec3(0.35, 0.345, 0.335);
        vec3 exposedRock = vec3(0.225, 0.222, 0.22);
        albedo = mix(regolith, exposedRock, highlands * 0.77);
        albedo *= 0.78 + granules * 0.43;
        albedo *= 0.92 + grain * 0.13;
    }

    // NASA Trek site's 81.6-km equirectangular orbital-photo mosaic
    // is registered to MOLA / LOLA's coordinate frame. +X points east,
    // +Z points north. Values outside the site remain procedural.
    if (uHasLandingAlbedo == 1 && uPatchType != 2 &&
        abs(ground.x) <= 13.6 && abs(ground.y) <= 13.6) {
        vec2 photoUv = vec2(0.5 + ground.x / 27.2,
                            0.5 - ground.y / 27.2);
        // Hardware mip-filter the georeferenced NASA mosaic; high-angle
        // ground viewing otherwise shimmers as orbital-sized texels approach
        // subpixel size. Moon WAC imagery has more high-frequency crater
        // detail, so it uses a slightly higher LOD bias.
        float photoLodBias = uWorld == 1 ? 1.95 : 1.10;
        vec3 photo = texture(uLandingAlbedo, photoUv, photoLodBias).rgb;
        // Highlight compression preserves actual photographed geography
        // without turning naturally bright cliffs into glowing white bands.
        photo = photo / (vec3(1.0) + photo * 0.22);
        // Orbital imagery is not photometrically corrected against the
        // in-engine Sun: preserve surface detail but avoid strong baked
        // false-color lighting and keep a small, detailed regolith layer.
        vec3 sourceAlbedo = uWorld == 0
            ? photo * vec3(0.97, 0.94, 0.90)
            : photo * vec3(0.94, 0.94, 0.95);
        // 1024px/81km orbital photos resolve ~80m, not individual pebbles.
        // Do not enlarge those texels into artificial stripes at eye level.
        // Preserve measured albedo for distant ridges and geological context.
        // Blending by patch TYPE caused conspicuous square photographic
        // seams every time the 1m / 21m / NASA DEM LOD changed. Use the
        // actual eye distance for identical albedo at every LOD boundary.
        // The 80m/pixel orbital photograph only dominates distant terrain.
        float photoWeight = mix(0.06, 0.80,
            smoothstep(0.06, 0.70, vDistance));
        photoWeight *= smoothstep(0.012, 0.16, vDistance);
        // Gently hide the edge of the finite NASA Trek site instead of
        // exposing a hard square where photos end and procedural land begins.
        float edgeDistance = max(abs(ground.x), abs(ground.y));
        photoWeight *= 1.0 - smoothstep(12.2, 13.55, edgeDistance);
        albedo = mix(albedo, sourceAlbedo, photoWeight);
        albedo *= 0.90 + 0.17 * granules;
    }

    float direct = max(dot(microNormal, sun), 0.0);
    float halfLambert = smoothstep(0.0, 0.07, direct);
    // No invented lunar atmosphere. Moon's small fill is exposure/ambient
    // approximation; shadow retains significantly less light than day.
    float light = uWorld == 0
        ? 0.21 + 1.17 * direct * halfLambert
        : 0.055 + 1.38 * direct * halfLambert;
    float surfaceAo = mix(1.0, 0.78, pow(steepness, 0.7));
    // Geometrically modelled rocks are darker than surrounding loose dust.
    // Variation between buried basalt and dust-coated stones reduces
    // the repeated orange/black silhouette effect in the previous frame.
    if (uPatchType == 2) {
        albedo *= 0.72 + vColor.r * 0.65;
    }
    vec3 color = albedo * light * surfaceAo;

    if (uWorld == 0) {
        // Warm atmospheric extinction and aerial perspective on Mars, with
        // clear close foreground. The Moon intentionally has zero dust fog.
        vec3 dust = vec3(0.42, 0.235, 0.16);
        float transmittance = exp(-max(vDistance, 0.0) * 0.205);
        color = mix(dust, color, transmittance);
    }

    // Filmic highlight compression only; do not artificially add emissive glow.
    color = color / (vec3(1.0) + color * 0.48);
    fragColor = vec4(pow(max(color, vec3(0.0)), vec3(0.88)), 1.0);
}
"""
}
