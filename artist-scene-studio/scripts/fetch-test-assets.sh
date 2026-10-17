#!/usr/bin/env sh
set -eu
project_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
asset_dir="$project_dir/app/src/main/assets/models"
asset_file="$asset_dir/boom_box.glb"
asset_url="https://raw.githubusercontent.com/KhronosGroup/glTF-Sample-Assets/7d4ba189827916452eeadc82d4b712dbc6280a6f/Models/BoomBox/glTF-Binary/BoomBox.glb"
expected_sha256="f8b918445ebdd006768232205a62f5182d2208ca57f84c6ccc084943c0bc8f15"
rigged_file="$asset_dir/cesium_man.glb"
rigged_url="https://raw.githubusercontent.com/KhronosGroup/glTF-Sample-Assets/7d4ba189827916452eeadc82d4b712dbc6280a6f/Models/CesiumMan/glTF-Binary/CesiumMan.glb"
rigged_sha256="b7001eaeea8254bd44773bcd247e78696d94169388fbb2a1800fc69434e777d9"
mkdir -p "$asset_dir"
if [ ! -f "$asset_file" ] || [ "$(sha256sum "$asset_file" | cut -d ' ' -f 1)" != "$expected_sha256" ]; then
  temp_file="$asset_file.tmp"
  trap 'rm -f "$temp_file"' EXIT HUP INT TERM
  curl --fail --location --retry 3 --output "$temp_file" "$asset_url"
  printf '%s  %s\n' "$expected_sha256" "$temp_file" | sha256sum --check --status
  mv "$temp_file" "$asset_file"
fi
printf '%s\n' "Verified CC0 GLB fixture: $asset_file"
if [ ! -f "$rigged_file" ] || [ "$(sha256sum "$rigged_file" | cut -d ' ' -f 1)" != "$rigged_sha256" ]; then
  temp_file="$rigged_file.tmp"
  trap 'rm -f "$temp_file"' EXIT HUP INT TERM
  curl --fail --location --retry 3 --output "$temp_file" "$rigged_url"
  printf '%s  %s\n' "$rigged_sha256" "$temp_file" | sha256sum --check --status
  mv "$temp_file" "$rigged_file"
fi
printf '%s\n' "Verified CC-BY-4.0 skinned humanoid fixture: $rigged_file"

rigged_figure_file="$asset_dir/rigged_figure.glb"
rigged_figure_url="https://raw.githubusercontent.com/KhronosGroup/glTF-Sample-Assets/7d4ba189827916452eeadc82d4b712dbc6280a6f/Models/RiggedFigure/glTF-Binary/RiggedFigure.glb"
rigged_figure_sha256="d6be85417d3e256861ee733eea6916093a7af7c79c16366181fd8abcaeb38cf5"
if [ ! -f "$rigged_figure_file" ] || [ "$(sha256sum "$rigged_figure_file" | cut -d ' ' -f 1)" != "$rigged_figure_sha256" ]; then
  temp_file="$rigged_figure_file.tmp"
  trap 'rm -f "$temp_file"' EXIT HUP INT TERM
  curl --fail --location --retry 3 --output "$temp_file" "$rigged_figure_url"
  printf '%s  %s\n' "$rigged_figure_sha256" "$temp_file" | sha256sum --check --status
  mv "$temp_file" "$rigged_figure_file"
fi
printf '%s\n' "Verified CC-BY-4.0 low-poly humanoid starter: $rigged_figure_file"

color_cube_file="$asset_dir/color_cube.glb"
color_cube_url="https://raw.githubusercontent.com/KhronosGroup/glTF-Sample-Assets/7d4ba189827916452eeadc82d4b712dbc6280a6f/Models/BoxVertexColors/glTF-Binary/BoxVertexColors.glb"
color_cube_sha256="9c48227f33b0ba2fbcf23b98ebf60d1c8ae0c6e6c5281e0aa3cc58affee10382"
if [ ! -f "$color_cube_file" ] || [ "$(sha256sum "$color_cube_file" | cut -d ' ' -f 1)" != "$color_cube_sha256" ]; then
  temp_file="$color_cube_file.tmp"
  trap 'rm -f "$temp_file"' EXIT HUP INT TERM
  curl --fail --location --retry 3 --output "$temp_file" "$color_cube_url"
  printf '%s  %s\n' "$color_cube_sha256" "$temp_file" | sha256sum --check --status
  mv "$temp_file" "$color_cube_file"
fi
printf '%s\n' "Verified CC0 color-cube prop starter: $color_cube_file"

python3 "$project_dir/scripts/build-humanoid.py"
# GENUINE 3D anime avatar: official VRoid Studio beta HairSample_Male model.
# The manufacturer (pixiv) designates this exact beta model CC0:
# https://vroid.pixiv.help/hc/en-us/articles/4402614652569
# Source is an unmodified VRM/glTF-binary at a pinned upstream Git commit.
# VRM 0.0 is structurally a GLB, so Filament reads the geometry and rig as glTF.
# No VRoid web service, account or payment required. Metadata is preserved.
anime_file="$asset_dir/mise_anime.glb"
anime_blob_sha1="46e8c583ce5df3b8ee167141b59ba3ad583a2687"
anime_url="https://raw.githubusercontent.com/madjin/vrm-samples/e16eb187100149a315ad92c3c9968f1d5baa6c7d/vroid/beta/HairSample_Male.vrm"
# Always checksum the original before modifying it for the Filament loader.
# Source is kept in /tmp so the untransformed .vrm is NOT bundled in the APK.
anime_source="$(mktemp)"
trap 'rm -f "$anime_source"' EXIT HUP INT TERM
curl --fail --location --retry 3 --output "$anime_source" "$anime_url"
[ "$(git hash-object "$anime_source")" = "$anime_blob_sha1" ] || {
  echo "VRoid CC0 anime source checksum does not match pinned Git object" >&2
  exit 1
}
python3 "$project_dir/scripts/prepare-anime-glb.py" "$anime_source" "$anime_file"
# Android 16's OpenGL renderer cannot reliably display the 91-joint palette
# while API 26 happens to support it. The male mesh uses at most 58 real bone
# influences; compact GPU indices while keeping the full editable 91-bone rig.
python3 "$project_dir/scripts/compact-anime-skins.py" "$anime_file" "$anime_file"
rm -f "$anime_source"
python3 "$project_dir/scripts/check-anime-glb.py" "$anime_file"
python3 "$project_dir/scripts/extract-vrm-springs.py" "$anime_file" "$asset_dir/mise_anime.spring.json"
# A second *licensed* anime character base with distinct hair, face and outfit.
# HairSample_Female is explicitly CC0 under the same official pixiv FAQ.
female_file="$asset_dir/mise_anime_female.glb"
female_url="https://raw.githubusercontent.com/madjin/vrm-samples/e16eb187100149a315ad92c3c9968f1d5baa6c7d/vroid/beta/HairSample_Female.vrm"
female_blob_sha1="0e5235f9a7972fbcbceb74960479b208c27a8707"
female_source="$(mktemp)"
trap 'rm -f "$female_source"' EXIT HUP INT TERM
curl --fail --location --retry 3 --output "$female_source" "$female_url"
[ "$(git hash-object "$female_source")" = "$female_blob_sha1" ] || {
  echo "VRoid female anime source checksum does not match pinned Git object" >&2
  exit 1
}
python3 "$project_dir/scripts/prepare-anime-glb.py" "$female_source" "$female_file"
# VRoid female uses 119 joints in each skin, which exceeds safe GPU
# palette size on our tested low-feature-level Android 8 emulator.
# Reduce each GPU palette to its actual vertex influences while preserving
# editable full node hierarchy, all vertices/skin weights and texture images.
python3 "$project_dir/scripts/compact-anime-skins.py" "$female_file" "$female_file"
rm -f "$female_source"
python3 "$project_dir/scripts/check-anime-glb.py" "$female_file"
python3 "$project_dir/scripts/extract-vrm-springs.py" "$female_file" "$asset_dir/mise_anime_female.spring.json"

python3 "$project_dir/scripts/build-scene-actors.py"
