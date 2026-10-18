#!/usr/bin/env bash
# Pin upstream *CC0* photos from Poly Haven and bake offline, no runtime network.
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
world="$root/app/src/main/assets/world"
mkdir -p "$world"
download_asset() {
    local url="$1" dest="$2" expected_sha="$3"
    if [ ! -s "$dest" ]; then
        curl --fail --location --retry 3 --max-time 180 -o "$dest.tmp" "$url"
        mv "$dest.tmp" "$dest"
    fi
    echo "Poly Haven CC0 source: $url"
    echo "$expected_sha  $dest" | sha256sum --check --status || {
        rm -f "$dest"
        echo "Photo checksum mismatches the CC0 licensed source: $url" >&2
        exit 1
    }
}
sky_url="https://dl.polyhaven.org/file/ph-assets/HDRIs/extra/Tonemapped%20JPG/kloofendal_48d_partly_cloudy_puresky.jpg"
grass_url="https://dl.polyhaven.org/file/ph-assets/Textures/png/1k/leafy_grass/leafy_grass_diff_1k.png"
download_asset "$sky_url" "$world/original_kloofendal_sky.jpg" "2bdd7abe32f1d2f7a626e0a6e45bb808313f29bd41d758eacbdba91560e93f04"
download_asset "$grass_url" "$world/leafy_grass_diff_1k.png" "2a4356e6aef5db70bb803a31fa265321b733b1f77c7c80ac26ae9aa0552f345d"
# Source panorama needed only during build. Keep at build input path for CI;
# never pack the full-res source panorama into the production APK.
if [ ! -d "$root/.skybake-env" ]; then
    python3 -m venv "$root/.skybake-env"
    "$root/.skybake-env/bin/pip" -q install Pillow==11.3.0
fi
"$root/.skybake-env/bin/python" "$root/scripts/bake-photographic-sky.py" \
    --source "$world/original_kloofendal_sky.jpg" \
    --output "$world/real_daylight_sky.misesky" --size 512
rm "$world/original_kloofendal_sky.jpg"
# Photos credited in docs. Licensing: https://polyhaven.com/license (CC0).

# A genuinely branched and leaf-meshed CC0 oak (9,450 individual mesh
# leaves), not Mise's earlier five-sphere demo tree. Innerscene original
# artist asset: https://www.innerscene.com/tools/library/3d-parts/oak-street-tree-detailed-leaves-a273fef9
# ~5.9MB, native glTF geometry with no external textures/Blender processing.
oak_file="$root/app/src/main/assets/models/mise_oak_tree.glb"
oak_original="$world/source_original_oak.glb"
oak_url="https://www.innerscene.com/api/library/oak-street-tree-detailed-leaves-a273fef9/download"
curl --fail --location --retry 3 --max-time 180 -o "$oak_original.tmp" "$oak_url"
mv "$oak_original.tmp" "$oak_original"
echo "a02698ef246d8005110c291c510b8f7f397ae26e69414aa538cc4993b06256dd  $oak_original" | sha256sum --check --status || {
    rm -f "$oak_original"
    echo "Source mismatch for original Innerscene CC0 oak GLB" >&2
    exit 1
}
"$root/.skybake-env/bin/pip" -q install "trimesh==4.11.1"
"$root/.skybake-env/bin/python" "$root/scripts/optimize-oak.py" \
    --source "$oak_original" --output "$oak_file"
rm -f "$oak_original"
echo "Verified + losslessly batched CC0 oak tree: $oak_file"
