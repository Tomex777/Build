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
python3 "$project_dir/scripts/build-scene-actors.py"
