import assert from 'node:assert/strict'
import { mkdtemp, writeFile, chmod, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { execFile } from 'node:child_process'
import { promisify } from 'node:util'

const exec = promisify(execFile)
const folder = await mkdtemp(join(tmpdir(), 'mscc-youtube-azure-diagnostic-test-'))
const fake = join(folder, 'fake-yt-dlp')
const diagnostic = new URL('./azure-youtube-diagnostics.mjs', import.meta.url).pathname

async function execute(script) {
  await writeFile(fake, '#!/bin/sh\n' + script)
  await chmod(fake, 0o700)
  const {stdout} = await exec(process.execPath, [diagnostic], {
    env:{...process.env, MSCC_YOUTUBE_YTDLP_BIN:fake},
    timeout:8000,
  })
  const lines = stdout.trim().split(/\r?\n/)
  assert.equal(lines.length, 1, 'diagnostic prints only one redacted JSON record')
  return JSON.parse(lines[0])
}
try {
  const blocked = await execute(`if [ "$1" = "--version" ]; then echo 2026.10.01; exit 0; fi
echo "ERROR: Sign in to confirm you're not a bot. SECRET=private" >&2
exit 1
`)
  assert.equal(blocked.result, 'youtube_access_challenge')
  assert.equal(blocked.extractorInstalled, true)
  assert.equal(blocked.extractorVersion, '2026.10.01')
  assert.equal(blocked.attempts.length, 3)
  assert.equal(blocked.attempts.every(x => x.status === 'youtube_access_challenge'), true)
  assert.equal(JSON.stringify(blocked).includes('SECRET'), false)

  const ok = await execute(`if [ "$1" = "--version" ]; then echo 2026.10.01; exit 0; fi
cat <<'EOF'
{"formats":[{"height":360,"vcodec":"avc1","url":"https://untrusted.example/video"},{"height":720,"vcodec":"avc1","url":"https://untrusted.example/video2"}]}
EOF
`)
  assert.equal(ok.result, 'formats_resolved')
  assert.equal(ok.videoFormatCount, 2)
  assert.equal(ok.maxHeight, 720)
  assert.equal(ok.directVideoFormatCount, 0)
  assert.equal(JSON.stringify(ok).includes('untrusted.example'), false)

  const resolvedAlternative = await execute(`if [ "$1" = "--version" ]; then echo 2026.10.01; exit 0; fi
if printf '%s ' "$@" | grep -q 'player_client=tv,web_safari'; then
cat <<'EOF'
{"formats":[{"height":360,"vcodec":"avc1","url":"https://untrusted.example/video"}]}
EOF
else
echo "ERROR: Sign in to confirm you're not a bot" >&2
exit 1
fi
`)
  assert.equal(resolvedAlternative.successfulProfile, 'tv,web_safari')
  assert.deepEqual(resolvedAlternative.attempts.map(a => a.status),
    ['youtube_access_challenge', 'resolved'])
  assert.equal(resolvedAlternative.result, 'formats_resolved')

  const missing = await exec(process.execPath, [diagnostic], {
    env:{...process.env,MSCC_YOUTUBE_YTDLP_BIN:join(folder, 'missing')},
    timeout:8000,
  })
  assert.equal(JSON.parse(missing.stdout).result, 'extractor_missing')
  console.log('PASS Azure YouTube diagnostic is bounded, redacted, and distinguishes extractor from YouTube botgate')
} finally {
  await rm(folder,{recursive:true,force:true})
}
