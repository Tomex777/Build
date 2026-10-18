#!/usr/bin/env bash
# Pin upstream *CC0* photos from Poly Haven and bake offline, no runtime network.
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
world="$root/app/src/main/assets/world"
mkdir -p "$world"
download_asset() {
    local url="$1" dest="$2" out expected
    if [ ! -s "$dest" ]; then
        curl --fail --location --retry 3 --max-time 180 -o "$dest.tmp" "$url"
        mv "$dest.tmp" "$dest"
    fi
    echo "Poly Haven CC0 source: $url"
    sha256sum "$dest"
}
sky_url="https://dl.polyhaven.org/file/ph-assets/HDRIs/extra/Tonemapped%20JPG/kloofendal_48d_partly_cloudy_puresky.jpg"
grass_url="https://dl.polyhaven.org/file/ph-assets/Textures/png/1k/leafy_grass/leafy_grass_diff_1k.png"
download_asset "$sky_url" "$world/original_kloofendal_sky.jpg"
download_asset "$grass_url" "$world/leafy_grass_diff_1k.png"
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
