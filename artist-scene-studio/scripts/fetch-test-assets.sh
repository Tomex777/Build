#!/usr/bin/env sh
set -eu
project_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
asset_dir="$project_dir/app/src/main/assets/models"
asset_file="$asset_dir/boom_box.glb"
asset_url="https://raw.githubusercontent.com/KhronosGroup/glTF-Sample-Assets/7d4ba189827916452eeadc82d4b712dbc6280a6f/Models/BoomBox/glTF-Binary/BoomBox.glb"
expected_sha256="f8b918445ebdd006768232205a62f5182d2208ca57f84c6ccc084943c0bc8f15"
mkdir -p "$asset_dir"
if [ ! -f "$asset_file" ] || [ "$(sha256sum "$asset_file" | cut -d ' ' -f 1)" != "$expected_sha256" ]; then
  temp_file="$asset_file.tmp"
  trap 'rm -f "$temp_file"' EXIT HUP INT TERM
  curl --fail --location --retry 3 --output "$temp_file" "$asset_url"
  printf '%s  %s\n' "$expected_sha256" "$temp_file" | sha256sum --check --status
  mv "$temp_file" "$asset_file"
fi
printf '%s\n' "Verified CC0 GLB fixture: $asset_file"
