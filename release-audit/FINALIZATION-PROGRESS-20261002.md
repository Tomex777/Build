# Production finalization checkpoint — 2026-10-02 UTC

No application in this checkpoint is newly declared production-finalized. Current release-only changes are committed on the five canonical branches below. Application source and accepted UI are unchanged.

Cortex secrets are configured and independently matched to the existing owner certificate. CI #215 exposed an apksigner output-format mismatch: the valid signer was labeled V2 Signer. The strict fingerprint check now handles that format; #216 is running. Visible signed API26/API36 release evidence remains required.

Nami has a new unique RSA4096 permanent identity, valid through 2126, after no prior production identity was found. Its encrypted owner backup and separate recovery password file are saved privately and recovery round-trip was verified. No private material was committed. CI configuration and permanent signed installation remain pending.

MirrorChess, Lyra, and Endless preserve recovered owner identities. Endless uses its newer dedicated backup and rejects the obsolete key from the old shared archive. Release CI now tests permanent APKs themselves when all secrets are configured; partial or invalid secrets fail closed. Nami's publishing workflow downloads the exact permanently signed, currently accepted artifacts rather than signing a different build afterward.

Browser at-action approval is pending for the four apps' keystores, aliases and passwords to be stored as encrypted Tomex777/Build Actions secrets. Their owner recovery material is already saved. QA runs started before those secrets exist must not count as permanent signed acceptance.

| Project | Branch | Current HEAD | Current CI |
|---|---|---|---|
| Cortex | cortex-android-live | 6790d6d04c3e086941aa97ba04806922320dd68d | #216 in_progress (36973856932) |
| Nami | nami/standalone-foundation | 276e2e33adb673ee23880b6d2935b9703160c85f | #472 in_progress (36974075213) |
| MirrorChess | mirrorchess-sdk36-min26-20260922 | 80dca5d24fceeb9b136234d8d1a63a8098f51ca4 | #113 in_progress (36974088459) |
| Lyra | spotui-standalone-ci | 959220ed9df4876d19beb73ace2d60c4bb08c899 | #167 in_progress (36974093015) |
| Endless | endless-android-ci | 36e664f33948ce27e6c609108a04a880e6d8f2d7 | #149 in_progress (36974100181) |

Torri remains blocked by native 16K page alignment. Veya lacks acceptance on its newer head. Cubic has conflicting distributed signing identities requiring continuity resolution. Incomplete applications remain excluded. Other inventory rows retain their recorded audit evidence; this is an in-progress checkpoint, not a final release matrix.
