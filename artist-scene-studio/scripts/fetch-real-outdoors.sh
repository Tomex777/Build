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

# All are real photographer-sourced CC0 PBR diffuse albedos, not procedural noise.
# Paths follow Poly Haven's published material files and are bundled offline.
for material in dirt rock_ground granular_concrete asphalt_floor; do
    target="$world/${material}_diff_1k.png"
    url="https://dl.polyhaven.org/file/ph-assets/Textures/png/1k/${material}/${material}_diff_1k.png"
    if [ ! -s "$target" ]; then
        curl --fail --location --retry 3 --max-time 180 -o "$target.tmp" "$url"
        mv "$target.tmp" "$target"
    fi
    # Prevent success with HTML or truncated downloads.
    "$root/.skybake-env/bin/python" - "$target" <<'PY'
from PIL import Image
import sys
with Image.open(sys.argv[1]) as im:
    im.verify()
with Image.open(sys.argv[1]) as im:
    assert im.width >= 512 and im.height >= 512, (im.width, im.height)
PY
    case "$material" in
        dirt) hash="96b3eb441121c41806a4766f37eeeeb4667af73207ccd8bfb56bbd446a221d42" ;;
        rock_ground) hash="550625077ba51480af4d0a08d072a03a0995f6910fb34693312e02ce5b73c6f3" ;;
        granular_concrete) hash="a105afb64bf874f2d00b6601b98d4fe602e4624479b3f791fbf5a818f5d59a14" ;;
        asphalt_floor) hash="a61a821ec9828cbc6b34fc6e1d40d6460b3966dfb85d1c376b93056d1668053a" ;;
    esac
    echo "$hash  $target" | sha256sum --check --status || {
        rm -f "$target"
        echo "Checksum mismatch for licensed Poly Haven ground: $material" >&2
        exit 1
    }
    echo "Verified pinned Poly Haven CC0 photo: $material"
done
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

# Real-scale, textured architectural and car models. Avoid the low-poly city
# and toy-car collections: these are independent unbranded CC0 mesh assets.
# SHA256 will be pinned after CI fetches and inspects their actual bytes.
fetch_mise_model() {
    local slug="$1" path="$2" hash="$3"
    local target="$root/app/src/main/assets/models/$path"
    local url="https://www.innerscene.com/api/library/$slug/download"
    if [ ! -s "$target" ]; then
        curl --fail --location --retry 3 --max-time 180 -o "$target.tmp" "$url"
        mv "$target.tmp" "$target"
    fi
    test "$(wc -c < "$target")" -gt 300000 || {
        echo "Incomplete detailed scene asset: $slug" >&2
        exit 1
    }
    # Prevent shipping truncated or unrelated downloaded HTTP content.
    "$root/.skybake-env/bin/python" - "$target" <<'PY'
import sys,struct
with open(sys.argv[1],'rb') as f:
    magic,version,nbytes=struct.unpack('<III',f.read(12))
    assert magic==0x46546c67 and version==2, "Not a glTF 2 GLB"
    assert nbytes==__import__('os').path.getsize(sys.argv[1]), "Truncated GLB"
PY
    echo "$hash  $target" | sha256sum --check --status || {
        rm -f "$target"
        echo "Checksum mismatch for CC0 3D model: $slug" >&2
        exit 1
    }
    echo "Verified pinned CC0 detailed 3D model: $slug"
}
fetch_mise_model "detached-two-storey-house-66774933" "mise_house_two_storey.glb" "55901983c4c2629e7bc548777c695e73b6d48fd1db9a904840a88a301e87e838"
fetch_mise_model "bungalow-house-85d40048" "mise_house_bungalow.glb" "792372518d4739d21786361d1bd12d9e0eeb7d3a95aaf0a7d4bb8cf6c214cdad"
fetch_mise_model "sedan-car-42109739" "mise_sedan.glb" "4cb896f740de4da871c1d87c4af2b734db3d1e8fac852e308f1fca03a63b25d3"
