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
uniform int uWorld; // 0 Mars, 1 Moon, 2 Mercury, 3 Ceres, 4 Europa, 5 Titan
uniform int uPatchType; // 0 = coarse landscape; 1 = foreground; 2 = stones
uniform vec2 uViewAnchor;
uniform vec2 uMidAnchor;
uniform sampler2D uLandingAlbedo;
uniform sampler2D uMicroMaterial;
uniform int uHasLandingAlbedo;
out vec4 fragColor;

// Hash uses fixed surface coordinates, so regolith patterns do not swim.
float hash21(vec2 p) {
    // Integer hashing preserves centimetre detail at kilometre-scale site
    // coordinates, where float multiply/fract hashes lose their low bits.
    uvec2 q = uvec2(ivec2(p));
    uint h = q.x * 374761393u ^ q.y * 668265263u;
    h = (h ^ (h >> 13u)) * 1274126177u;
    h ^= h >> 16u;
    return float(h & 0x00ffffffu) / 16777216.0;
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
        abs(vPosition.z - uMidAnchor.y)) < 0.560) discard;

    // Clip to the exact mesh extents (160 * 21m / 2 and
    // 112 * 1.05m / 2). The old inset left coplanar overlap rings
    // that produced diagonal depth-buffer stippling on lunar slopes.
    if (uPatchType == 4 && max(
        abs(vPosition.x - uViewAnchor.x),
        abs(vPosition.z - uViewAnchor.y)) < 0.0196) discard;
    vec3 N = normalize(vNormal);
    vec3 sun = normalize(uLight);
    vec2 ground = vPosition.xz;
    float granules = geologicalDetail(ground);
    float areaNoise = noiseFiltered(ground * 3.6);
    float grain = noiseFiltered(ground * 470.0);
    // 1cm and 6cm albedo grains are derivative-filtered and low contrast;
    // subpixel grit fades to its mean instead of crawling during walking.
    // Two rotated samples of a seamless 256x256 RGBA micro-material provide
    // centimetre-scale colour, grains and chipped stone inclusions at eye
    // level. The atlas is generated at runtime (< 350 KiB with mipmaps),
    // so the APK stays lean. Hardware mipmaps suppress walking shimmer.
    // Do not project fictional cm-resolution detail onto orbital DEM hills.
    // Fade the material before the actual 58.8 m square mesh boundary.
    // A radial-only distance fade left conspicuous square albedo/normal
    // discontinuities where 1 m terrain hands off to 21 m terrain.
    float squareDistance = max(
        abs(ground.x - uViewAnchor.x), abs(ground.y - uViewAnchor.y)
    );
    float patchEdgeFade = 1.0 - smoothstep(0.0120, 0.0194, squareDistance);
    float microWeight = (uPatchType == 1 || uPatchType == 2)
        ? patchEdgeFade * (1.0 - smoothstep(0.005, 0.056, vDistance)) : 0.0;
    vec4 micro = vec4(0.5, 0.5, 0.5, 0.85);
    if (microWeight > 0.005) {
        vec2 atlasUv = ground * 330.0;
        vec2 rotatedUv = vec2(atlasUv.x * 0.52 - atlasUv.y * 0.85,
                              atlasUv.x * 0.85 + atlasUv.y * 0.52);
        vec4 grains = texture(uMicroMaterial, atlasUv);
        vec4 fines = texture(uMicroMaterial, rotatedUv * 4.5 + vec2(7.3, 19.9));
        micro = mix(grains, fines, 0.42);
    }
    float grit = mix(0.5, micro.g, microWeight);

    // Small-scale bump derived from the *same* geographic material pattern.
    // Prevent unstable sampling derivatives at orbital/surface extremes.
    float detailHeight = mix(granules - 0.5, micro.g - 0.5, microWeight);
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
        ? patchEdgeFade * (1.0 - smoothstep(0.015, 0.065, vDistance)) : 0.0;
    // Positions and derivative spans are in 3km world units. The old
    // 0.018/0.025 strengths implied 54/75 METRES of material height,
    // driving normals into black/white bands on grazing lunar slopes.
    // Fine regolith relief is centimetre-scale and foreground-only;
    // the 21m midfield mesh cannot resolve a fine-grain normal field.
    float bumpHeightUnits = uWorld == 0 ? 0.000010 : 0.000012;
    vec3 microNormal = normalize(N + perturb *
         bumpHeightUnits * nearDetail / span);
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
        if (uWorld == 2) { // Mercury: charcoal-grey impact regolith
            regolith = vec3(.28, .272, .266);
            exposedRock = vec3(.135, .132, .13);
        } else if (uWorld == 3) { // Ceres: hydrated dark carbonaceous material
            regolith = vec3(.225, .225, .215);
            exposedRock = vec3(.13, .132, .126);
        } else if (uWorld == 4) { // Europa: ice crust and dark lineaments
            regolith = vec3(.63, .67, .68);
            exposedRock = vec3(.22, .20, .18);
            highlands = smoothstep(.40, .68, areaNoise * .85 + steepness * .15);
        } else if (uWorld == 5) { // Titan: orange atmospheric organic dune fines
            regolith = vec3(.33, .215, .11);
            exposedRock = vec3(.22, .14, .074);
        }
        albedo = mix(regolith, exposedRock, highlands * .77);
        albedo *= 0.78 + granules * 0.43;
        albedo *= 0.92 + grain * 0.13;
    }

    // World-specific macro-albedo stays anchored in ground coordinates,
    // matching all LOD meshes without flickering across camera motion.
    if (uWorld == 4) {
        // Europa: pale water ice crisscrossed by narrow oxidized/reddish
        // lineaments. This is interpretive geology, NOT Galileo image data.
        // Avoid bright blue "frozen lake" fantasy coloring.
        vec2 ridge = ground * 2.6;
        float warp = (noiseFiltered(ground * 7.0) - 0.5) * 0.24;
        float crackA = abs(sin(ridge.x * 0.78 + ridge.y * 0.26 + warp));
        float crackB = abs(sin(ridge.y * 0.92 - ridge.x * 0.16 - warp * 0.5));
        float darkBand = 1.0 - smoothstep(0.025, 0.155, min(crackA, crackB));
        float smaller = 1.0 - smoothstep(0.018, 0.07,
            abs(sin(ridge.x * 2.8 - ridge.y * 1.43 + warp)));
        float weathering = noiseFiltered(ground * 3.1);
        vec3 cleanIce = vec3(.74, .77, .755);
        vec3 hydratedBrown = vec3(.34, .267, .235);
        albedo = mix(cleanIce * (.85 + .20 * weathering),
            hydratedBrown, clamp(darkBand * .57 + smaller * .09, 0.0, .63));
        albedo *= .96 + .10 * granules;
    } else if (uWorld == 5) {
        // Titan: fine organic dunes with broad alternating low albedo
        // streaks. Dunes have no sharp artificial polygon outlines.
        float windWarp = (noiseFiltered(ground * 1.7) - .5) * .34;
        float ripple = sin((ground.x * .67 + ground.y * 3.5 + windWarp) * 7.0);
        float duneDark = smoothstep(-.38, .76, ripple);
        float veneer = noiseFiltered(ground * 5.3);
        vec3 fines = vec3(.37,.242,.118);
        vec3 organics = vec3(.19,.127,.075);
        albedo = mix(fines, organics, .48 * duneDark);
        albedo *= .83 + .32 * veneer;
    } else if (uWorld == 3) {
        // Ceres: much darker carbonaceous surface, rare pale hydrated salt
        // outcrops. Avoid coating the entire dwarf planet in white.
        float bright = smoothstep(.82, .96,
            noiseFiltered(ground * 2.4) * .64 +
            noiseFiltered(ground * 7.5) * .36);
        albedo = mix(albedo, vec3(.63,.60,.53), bright * .38);
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

    albedo *= 0.95 + 0.10 * grit;
    // Keep photographed geography at mid/far range and show believable
    // fines/inclusions only at ground level. Do not make metre-sized pixels.
    float fineContrast = clamp(0.50 + 1.23 * micro.r, 0.67, 1.20);
    float inclusions = smoothstep(0.40, 0.82, micro.b);
    albedo *= mix(1.0, fineContrast * (1.0 - 0.075 * inclusions), microWeight);
    float direct = max(dot(microNormal, sun), 0.0);
    float halfLambert = smoothstep(0.0, 0.07, direct);
    // No invented lunar atmosphere. Moon's small fill is exposure/ambient
    // approximation; shadow retains significantly less light than day.
    float light = uWorld == 0
        ? 0.21 + 1.17 * direct * halfLambert
        : uWorld == 5
            ? 0.33 + 0.69 * direct * halfLambert
            : 0.055 + 1.38 * direct * halfLambert;
    float surfaceAo = mix(1.0, 0.78, pow(steepness, 0.7));
    // Geometrically modelled rocks are darker than surrounding loose dust.
    // Variation between buried basalt and dust-coated stones reduces
    // the repeated orange/black silhouette effect in the previous frame.
    if (uPatchType == 2) {
        float rockWeathering = noiseFiltered(
            (vPosition.xz + vPosition.y * vec2(.37, .61)) * 18000.0);
        albedo *= (0.72 + vColor.r * 0.65) * (.92 + .16 * rockWeathering);
    }
    vec3 color = albedo * light * surfaceAo;
    // Regolith is overwhelmingly diffuse. A restrained, rough reflection
    // on exposed stone edges prevents flat-looking matte plastic boulders.
    if (uPatchType == 2 && direct > 0.0) {
        vec3 viewDirection = normalize(vec3(
            uViewAnchor.x - vPosition.x, 0.0010,
            uViewAnchor.y - vPosition.z
        ));
        float rough = clamp(micro.a, 0.68, 0.98);
        vec3 halfway = normalize(sun + viewDirection);
        float highlight = pow(max(dot(N, halfway), 0.0), mix(22.0, 7.0, rough));
        color += vec3(0.007) * highlight * direct * microWeight;
    }

    if (uWorld == 5) {
        // Titan's optically thick nitrogen/methane haze obscures distance.
        float transmission = exp(-max(vDistance, 0.0) * 1.25);
        color = mix(vec3(.48,.285,.125), color, transmission);
    }
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
