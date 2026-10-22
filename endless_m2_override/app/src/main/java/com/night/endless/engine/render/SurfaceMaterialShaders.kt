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
uniform sampler2D uOrbitalDetail;
uniform int uHasOrbitalDetail;
uniform sampler2D uOrbitalNormal;
uniform int uHasOrbitalNormal;
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
    // Above Nyquist the value is exactly its 0.5 mean. Early-exit BEFORE
    // four integer hashes and interpolation per sample. At orbital distances
    // most fine grains are many pixels across in texture space but subpixel
    // on screen; hashing their discarded detail wasted considerable mobile
    // shader time and increased the risk of frame stutter on software GLES.
    // dFdx/dFdy are evaluated unconditionally as GLES3 requires.
    if (footprint >= 1.55) return 0.5;
    float measured = noise2(coordinates);
    if (footprint <= 0.50) return measured;
    return mix(measured, 0.5, smoothstep(0.50, 1.55, footprint));
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
    // Leave a one-metre overlap between the 21m midfield and 1.05m
    // foreground mesh. Exact edge-to-edge masks leave 1px pinholes at
    // grazing angles: real Titan screenshot showed a dotted orange line
    // of sky shining through the join. The near mesh is depth-biased
    // forward only during its draw to win the small overlap cleanly.
    if (uPatchType == 4 && max(
        abs(vPosition.x - uViewAnchor.x),
        abs(vPosition.z - uViewAnchor.y)) < 0.0192) discard;
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
        // Martian regolith reflects dusty ochre, weathered basalt, and
        // subdued reddish fines, not a uniformly fluorescent orange.
        // Physical color cue from NASA Mars mineralogy; the exact rock and
        // silt distributions below are illustrative for Olympus Mons.
        vec3 rustDust = vec3(.445, .263, .181);
        vec3 ferricSand = vec3(.527, .326, .219);
        vec3 rock = vec3(.285, .207, .173);
        float dunes = smoothstep(0.20, 0.83,
            noiseFiltered(ground * vec2(80.0, 23.0)));
        albedo = mix(rustDust, ferricSand, dunes * 0.55);
        albedo = mix(albedo, rock, darkBasalt * 0.63);
        // Low-frequency dust cover and basalt patches create broad,
        // correlated 10-150m material zones in ground coordinates.
        float ferricCover = noiseFiltered(ground * 22.0 + vec2(7.0, -9.0));
        float basaltPebbles = noiseFiltered(ground * 176.0 + vec2(-22.0, 14.0));
        float mineralDeposit = smoothstep(.37, .69,
            ferricCover * .65 + basaltPebbles * .35);
        albedo *= mix(.80, 1.12, mineralDeposit);
        albedo *= 0.78 + granules * 0.48;
        albedo *= mix(1.0, 0.87, grain * grain);
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
        if (uWorld == 2 || uWorld == 3) {
            // Moonlight-like low contrast in the older screenshots made
            // Mercury/Ceres look like flat uniform grey. Actual regolith
            // contains ejecta sheets, basaltic clasts and mottled patches.
            // Stable sub-kilometre noise, derivative-filtered at a distance.
            float ejecta = noiseFiltered(ground * 9.4) * 0.61 +
                           noiseFiltered(ground * 30.0) * 0.39;
            float deposit = smoothstep(.24, .78, ejecta);
            albedo *= mix(.79, 1.22, deposit);
        }
    }

    // World-specific macro-albedo stays anchored in ground coordinates,
    // matching all LOD meshes without flickering across camera motion.
    if (uWorld == 4) {
        // Europa: real ice crusts have bright cleaner plates broken by
        // brown-red non-ice deposits in double ridges and crosscutting
        // lineaments. Source geology is NASA Galileo-inspired. The
        // geometry/positions here are fictional until registered DEMs.
        // Domain warping prevents perfect sinusoidal painted stripes.
        float noiseA = noiseFiltered(ground * 16.7 + vec2(13.3,-5.1)) - .5;
        float noiseB = noiseFiltered(ground * 33.7 + vec2(-23.9,41.7)) - .5;
        vec2 faultPosition = ground + vec2(noiseA,noiseB) * .024;
        float alpha = dot(faultPosition, vec2(21.5, 7.9)) + noiseA * 1.3;
        float beta = dot(faultPosition, vec2(-6.3, 31.1)) + noiseB * .96;
        float primary = abs(sin(alpha));
        float secondary = abs(sin(beta));
        // Deposits lie in a narrow trough between bright ridge lips.
        float darkRift = exp(-primary*primary/.017) * .72 +
                         exp(-secondary*secondary/.024) * .43;
        float ridgeLip = exp(-pow(primary-.19,2.0)/.005) +
                         exp(-pow(secondary-.23,2.0)/.008);
        float grainIce = noiseFiltered(ground * 124.0);
        float brokenPlates = noiseFiltered(ground * 7.9 + vec2(11.0,-3.0));
        // Tiny branch fractures (<10m) show at eye level only, never
        // alias into artificial zebra marks in the distant landscape.
        float tiny = abs(sin(dot(ground,vec2(480.0,177.0))+noiseA*2.5));
        float branch = (1.0-smoothstep(.014,.065,tiny)) *
            (1.0-smoothstep(.008,.12,vDistance));
        vec3 cleanIce = vec3(.865,.875,.847);
        vec3 youngIce = vec3(.952,.949,.914);
        vec3 stainedIce = vec3(.36,.244,.192);
        float plateValue = smoothstep(.30,.72,brokenPlates);
        albedo = mix(cleanIce, youngIce, plateValue*.55);
        float deposited = clamp(darkRift*.72+branch*.14,0.0,.82);
        albedo = mix(albedo,stainedIce,deposited);
        albedo = mix(albedo,youngIce,ridgeLip*.045);
        albedo *= .965 + .06*grainIce;
    } else if (uWorld == 5) {
        // Titan: broad wind-lain longitudinal dunes plus fine ripple
        // streaks with direction-coherent albedo. The visible wave period
        // is consistent with their new modelled dune-height geometry.
        float windWarp = (noiseFiltered(ground * 3.7) - .5) * .25;
        float crestPhase = (ground.x * 2.9 + ground.y * 5.9 + windWarp)*1.18;
        float crest = sin(crestPhase);
        float leeSide = smoothstep(-.55,.72,crest);
        float ripple = sin((ground.x * 92.0 + ground.y * 29.0 +
                          windWarp*22.0));
        float rippleShade = .5+.5*ripple;
        float veneer = noiseFiltered(ground * 8.3);
        vec3 fines = vec3(.40,.258,.129);
        vec3 organics = vec3(.20,.132,.072);
        albedo = mix(fines,organics,leeSide*.43);
        albedo *= .85 + .22*veneer + .04*rippleShade;
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
        if (uWorld == 0 && uHasOrbitalDetail == 1) {
            // The optional high-resolution Olympus product contributes
            // *detail*, not a new fake orange global color. High-pass
            // its luminance against its own low mip so photographed
            // kilometer-scale albedo remains registered to Viking.
            // Both datasets cover the same 81.6km ground coordinates.
            vec3 fine = texture(uOrbitalDetail,photoUv,1.25).rgb;
            vec3 smoothSource = texture(uOrbitalDetail,photoUv,4.0).rgb;
            float detail = dot(fine-smoothSource,vec3(.299,.587,.114));
            detail = clamp(detail*1.65,-.16,.16);
            float farWeight = smoothstep(.12,.46,vDistance);
            sourceAlbedo *= 1.0+detail*farWeight;
        }
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

    // Selected additional NASA Olympus photographs resolve ridges at a
    // finer scale than MOLA; their 3x3 grayscale gradients can provide
    // restrained, distance-filtered normal variation to the true MOLA
    // mesh. This is an inferred optical bump, NOT measured elevations.
    // At eye level the generated meter-resolution regolith takes over.
    if (uWorld == 0 && uHasOrbitalNormal == 1 &&
        abs(ground.x) <= 13.6 && abs(ground.y) <= 13.6) {
        vec2 uv = vec2(.5+ground.x/27.2,.5-ground.y/27.2);
        vec2 optical = texture(uOrbitalNormal,uv,1.25).rg*2.0-1.0;
        float strength = smoothstep(.14,.66,vDistance);
        float edge = 1.0-smoothstep(12.1,13.55,max(abs(ground.x),abs(ground.y)));
        microNormal = normalize(microNormal +
             vec3(-optical.x,0.0,optical.y)*1.1*strength*edge);
    }

    albedo *= 0.95 + 0.10 * grit;
    // Keep photographed geography at mid/far range and show believable
    // fines/inclusions only at ground level. Do not make metre-sized pixels.
    float fineContrast = clamp(0.50 + 1.23 * micro.r, 0.67, 1.20);
    float inclusions = smoothstep(0.40, 0.82, micro.b);
    albedo *= mix(1.0, fineContrast * (1.0 - 0.075 * inclusions), microWeight);
    float direct = max(dot(microNormal, sun), 0.0);
    // Each moving terrain mesh carries directional sky visibility baked
    // from its ACTUAL triangles. Coarse terrain, rocks and decals retain
    // their original lighting; do not shade away reflected ambient light.
    // The near/mid patch edge is faded to 1.0 to hide truncated ray casts.
    float terrainSunVisibility = (uPatchType == 1 || uPatchType == 4)
        ? clamp(vColor.r, 0.10, 1.0) : 1.0;
    float sunlitDirect = direct * terrainSunVisibility;
    float halfLambert = smoothstep(0.0, 0.07, direct);
    // No invented lunar atmosphere. Moon's small fill is exposure/ambient
    // approximation; shadow retains significantly less light than day.
    float light = uWorld == 0
        ? 0.21 + 1.17 * sunlitDirect * halfLambert
        : uWorld == 5
            ? 0.33 + 0.69 * sunlitDirect * halfLambert
            : 0.055 + 1.38 * sunlitDirect * halfLambert;
    // Water-ice frost backscatter avoids grey-rock appearance without
    // giving Europa an impossible internally glowing/snowy surface.
    if (uWorld == 4) {
        float iceFacing = clamp(dot(microNormal, sun), 0.0, 1.0);
        light += .14 * pow(iceFacing, 1.7) * terrainSunVisibility;
    }
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
        // Suspended Martian dust reddens and desaturates distant basins.
        // Calibrate the far fade separately from ground colour; it must
        // never look like a flat paper cut-out along the crater rim.
        vec3 dust = vec3(.448, .264, .187);
        float transmittance = exp(-max(vDistance, 0.0) * 0.235);
        color = mix(dust, color, transmittance);
    }

    // Individual camera exposure: Europa is illuminated at 5AU and
    // almost monochromatic, but a camera automatically exposes its
    // reflective ice brighter than lunar-grey stone. Exposure is NOT
    // fictitious additional sunlight. Keep Mars/Moon exactly calibrated.
    if (uWorld == 4) {
        color *= 1.47;
        // Broad waxy ice reflection, restrained by microfacet roughness
        // and only visible where the surface actually faces the Sun.
        vec3 viewDirection = normalize(vec3(
            uViewAnchor.x - vPosition.x, .00078,
            uViewAnchor.y - vPosition.z));
        float glossy = max(dot(microNormal, normalize(sun+viewDirection)),0.0);
        float glint = pow(glossy, 48.0) * sunlitDirect;
        color += vec3(.045,.050,.053)*glint;
    } else if (uWorld == 5) {
        color *= 1.13;
    } else if (uWorld == 2 || uWorld == 3) {
        // Airless Mercury and Ceres are dark in absolute reflected sunlight.
        // As with Apollo cameras, set a darker-scene exposure rather than
        // adding fictional atmospheric ambient light. Keep the sky black.
        // The earlier frame was nearly featureless charcoal on a dark panel.
        color *= 1.58;
    }
    // Filmic highlight compression, not flat ambient emissive haze.
    color = color / (vec3(1.0) + color * 0.48);
    fragColor = vec4(pow(max(color, vec3(0.0)), vec3(0.88)), 1.0);
}
"""
}
