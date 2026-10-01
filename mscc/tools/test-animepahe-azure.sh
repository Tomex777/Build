#!/usr/bin/env bash
set -euo pipefail

QUERY="${1:-Bleach}"
MODE="${2:-}"
FLARE_URL="${MSCC_FLARESOLVERR_URL:-http://127.0.0.1:8191/v1}"
PAHE_ROOT="${MSCC_PAHEBATCHER_ROOT:-/opt/pahebatcher}"
OUT_ROOT="${MSCC_PAHE_TEST_OUT:-/tmp/mscc-animepahe-test}"
WORK="$(mktemp -d /tmp/mscc-pahe-probe.XXXXXX)"
trap 'rm -rf "$WORK"' EXIT

need() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "Missing command: $1" >&2
    exit 2
  }
}
need curl
need jq
need python3

echo "=== AnimePahe Azure probe ==="
echo "Query: $QUERY"
echo "FlareSolverr: $FLARE_URL"

HEALTH_URL="${FLARE_URL%/v1}/health"
curl -fsS "$HEALTH_URL" | jq . >/dev/null
echo "FlareSolverr: healthy"

ENCODED_QUERY="$(python3 -c 'import sys,urllib.parse,time; print(urllib.parse.quote(sys.argv[1]+" "+str(int(time.time()))))' "$QUERY")"
SEARCH_URL="https://animepahe.pw/api?m=search&q=$ENCODED_QUERY&page=1"

flare_get() {
  local url="$1" out="$2"
  jq -n     --arg url "$url"     '{cmd:"request.get",url:$url,maxTimeout:120000,waitInSeconds:2,disableMedia:true}'   | curl -fsS -X POST "$FLARE_URL"       -H 'Content-Type: application/json'       --data-binary @-       -o "$out"
}

extract_json() {
  local response_file="$1" json_file="$2"
  python3 - "$response_file" "$json_file" <<'PY'
import html, json, re, sys
src=json.load(open(sys.argv[1],encoding="utf-8"))
if src.get("status") != "ok":
    raise SystemExit("FlareSolverr failed: "+str(src.get("message")))
sol=src.get("solution") or {}
body=str(sol.get("response") or "").strip()
if not body:
    raise SystemExit("FlareSolverr returned an empty response")
m=re.search(r"<pre[^>]*>([\s\S]*?)</pre>", body, re.I)
if m:
    body=html.unescape(m.group(1)).strip()
else:
    maybe=re.sub(r"<[^>]+>","",body).strip()
    if maybe.startswith("{") or maybe.startswith("["):
        body=html.unescape(maybe)
try:
    data=json.loads(body)
except Exception as exc:
    title=""
    mt=re.search(r"<title[^>]*>([\s\S]*?)</title>",str(sol.get("response") or ""),re.I)
    if mt: title=re.sub(r"\s+"," ",html.unescape(mt.group(1))).strip()
    raise SystemExit(f"Response was not AnimePahe JSON. HTTP={sol.get('status')} title={title!r}: {exc}")
json.dump(data,open(sys.argv[2],"w",encoding="utf-8"),indent=2)
PY
}

echo
echo ">>> 1. Search through FlareSolverr -> Tor"
flare_get "$SEARCH_URL" "$WORK/search-envelope.json"
extract_json "$WORK/search-envelope.json" "$WORK/search.json"

COUNT="$(jq '.data | length' "$WORK/search.json")"
echo "AnimePahe search results: $COUNT"
if [ "$COUNT" -lt 1 ]; then
  echo "No AnimePahe results returned for '$QUERY'." >&2
  exit 3
fi

jq -r '.data[:10] | to_entries[] | "\(.key+1). \(.value.title)  [id=\(.value.id)]"' "$WORK/search.json"

SELECTED="$(jq -c '.data[0]' "$WORK/search.json")"
TITLE="$(jq -r '.title' <<<"$SELECTED")"
SESSION="$(jq -r '.session' <<<"$SELECTED")"
ANIME_ID="$(jq -r '.id' <<<"$SELECTED")"

echo
echo "Selected: $TITLE"
echo "Anime ID: $ANIME_ID"
echo "Session:  $SESSION"

RELEASE_URL="https://animepahe.pw/api?m=release&id=$SESSION&sort=episode_asc&page=1"
echo
echo ">>> 2. Episode API through the same FlareSolverr/Tor route"
flare_get "$RELEASE_URL" "$WORK/episodes-envelope.json"
extract_json "$WORK/episodes-envelope.json" "$WORK/episodes.json"

EP_COUNT="$(jq '.data | length' "$WORK/episodes.json")"
LAST_PAGE="$(jq -r '.last_page // 1' "$WORK/episodes.json")"
echo "Episodes on first API page: $EP_COUNT"
echo "Episode API pages:          $LAST_PAGE"
jq -r '.data[:12][] | "Episode \(.episode)   session=\(.session)"' "$WORK/episodes.json"

if [ "$MODE" != "--download-one" ]; then
  echo
  echo "PASS: AnimePahe search + episode API worked from this Azure route."
  echo "To test a real media download and MP4 decode:"
  echo "  bash $0 \"$QUERY\" --download-one"
  exit 0
fi

echo
echo ">>> 3. Installing/updating smolfiddle/pahebatcher test harness"
need git
need ffmpeg

if [ ! -d "$PAHE_ROOT/.git" ]; then
  sudo rm -rf "$PAHE_ROOT"
  sudo git clone --depth 1 https://github.com/smolfiddle/pahebatcher.git "$PAHE_ROOT"
  sudo chown -R "$USER:$USER" "$PAHE_ROOT"
else
  git -C "$PAHE_ROOT" pull --ff-only
fi

if [ ! -x "$PAHE_ROOT/venv/bin/python" ]; then
  python3 -m venv "$PAHE_ROOT/venv"
fi
"$PAHE_ROOT/venv/bin/python" -m pip install --upgrade pip
"$PAHE_ROOT/venv/bin/python" -m pip install -e "$PAHE_ROOT"

SERIES_URL="https://animepahe.pw/anime/$SESSION"
mkdir -p "$OUT_ROOT"

echo
echo ">>> 4. Pahebatcher list test"
(
  cd "$PAHE_ROOT"
  FLARESOLVERR_URL="$FLARE_URL"     "$PAHE_ROOT/venv/bin/python" -m pahebatcher     "$SERIES_URL" --list --verbose
)

echo
echo ">>> 5. Downloading exactly one episode at 720p"
(
  cd "$PAHE_ROOT"
  FLARESOLVERR_URL="$FLARE_URL"     "$PAHE_ROOT/venv/bin/python" -m pahebatcher     "$SERIES_URL"     --range 1     --audio jpn     -q 720     -j 1     -w 8     -o "$OUT_ROOT"     --verbose
)

MP4="$(find "$OUT_ROOT" -type f -iname '*.mp4' -printf '%T@ %p\n' | sort -nr | head -1 | cut -d' ' -f2-)"
if [ -z "$MP4" ] || [ ! -s "$MP4" ]; then
  echo "No MP4 was produced." >&2
  exit 4
fi

echo
echo "Downloaded:"
ls -lh "$MP4"

echo
echo ">>> 6. ffprobe"
ffprobe -v error   -show_entries format=format_name,duration,size,bit_rate   -show_entries stream=index,codec_type,codec_name,width,height   -of json "$MP4" | jq .

DURATION="$(ffprobe -v error -show_entries format=duration -of default=nw=1:nk=1 "$MP4")"

echo
echo ">>> 7. Decode first 12 seconds"
ffmpeg -nostdin -v error -i "$MP4" -t 12 -map 0:v:0 -f null -

MID="$(python3 - "$DURATION" <<'PY'
import sys
d=float(sys.argv[1] or 0)
print(int(max(0,min(300,d*0.45))))
PY
)"
if [ "$MID" -gt 5 ]; then
  echo ">>> 8. Seek to ${MID}s and decode another 10 seconds"
  ffmpeg -nostdin -v error -ss "$MID" -i "$MP4" -t 10 -map 0:v:0 -f null -
fi

echo
echo "PASS: AnimePahe search, episode listing, HLS download, MP4 assembly and decode all worked."
echo "MP4: $MP4"
