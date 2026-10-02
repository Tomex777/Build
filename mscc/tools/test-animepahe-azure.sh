#!/usr/bin/env bash
set -euo pipefail

QUERY="${1:-Bleach}"
MODE="${2:-}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
FLARE_URL="${MSCC_FLARESOLVERR_URL:-http://127.0.0.1:8191}"
TOR_PROXY="${MSCC_TOR_PROXY:-socks5h://127.0.0.1:9050}"

echo "=== AnimePahe Azure production-path probe ==="
echo "Query: $QUERY"
echo "FlareSolverr: $FLARE_URL"
echo "Tor: $TOR_PROXY"

curl -fsS "${FLARE_URL%/v1}/health" | jq .
curl -fsS --max-time 30 --proxy "$TOR_PROXY" https://check.torproject.org/api/ip | tee /tmp/mscc-pahe-tor.json | jq .
jq -e '.IsTor == true' /tmp/mscc-pahe-tor.json >/dev/null

cd "$ROOT"
export MSCC_LIVE_QUERY="$QUERY"

if [ "$MODE" = "--download-one" ]; then
  echo
  echo ">>> Full runtime proof: search -> episodes -> play -> Kwik -> HLS -> AES-128 -> MP4 decode"
  node anime-source-live-probe.js animepahe
  exit 0
fi

echo
echo ">>> Search + episode-list proof through the installed AnimePahe provider"
node --input-type=module <<'NODE'
import animePahe from './sources/anime/animepahe.js'

const query = process.env.MSCC_LIVE_QUERY || 'Bleach'
const search = await animePahe.run({ action:'search', query, context:{} })
if (!Array.isArray(search?.items) || !search.items.length) {
  throw new Error('AnimePahe search returned no results')
}
console.log('Search results:', search.items.length)
for (const [index, item] of search.items.slice(0, 10).entries()) {
  console.log(String(index + 1) + '.', item.title, item.id)
}
const selected = search.items.find(item => /bleach/i.test(item.title)) || search.items[0]
const listing = await animePahe.run({ action:'episodes', item:selected, context:{} })
if (!Array.isArray(listing?.episodes) || !listing.episodes.length) {
  throw new Error('AnimePahe episode listing returned no episodes')
}
console.log('Selected:', selected.title)
console.log('Episodes:', listing.episodes.length)
console.log('First:', listing.episodes[0])
console.log('PASS: AnimePahe exact production search + release flow')
NODE

echo
echo "For the complete Kwik/HLS/AES download proof:"
echo "  bash $0 \"$QUERY\" --download-one"
