# Combined-preview account gateway

The canonical `night-play-combined-preview` alias can point to a Vercel deployment
whose target is **Preview**, even though the alias is stable. An alias does not
change the environment used by a built deployment.

The existing server-only `NIGHT_PLAY_SIGNING_KEY`, `NIGHT_PLAY_MSCC_URL`, and
`NIGHT_PLAY_ORIGIN` configuration must therefore cover this project's Preview
deployments as well as Production. Keep the signing key secret. Keep the origin
restricted to `https://night-play-combined-preview.vercel.app`; do not trust the
request Host header or accept arbitrary preview origins for mutations.

After configuration changes, rebuild/redeploy, update the canonical alias,
verify its exact Git SHA, and check `GET /api/account` returns HTTP 200 with an
honest linked/unlinked state. A stale deployment retains its old environment.

`features.draw` is returned only when the backend advertises the capability.
The frontend must show the service-update state until the protected Azure batch
activates drawing rooms. Never fabricate a working room or a WhatsApp identity.
