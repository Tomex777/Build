# NIGHT Play review fixes — 22 October 2026

Canonical review target: https://night-play-combined-preview.vercel.app/

Frontend PR: https://github.com/Tomex777/Build/pull/575  
Drawing backend feature PR: https://github.com/Tomex777/Build/pull/573

## Confirmed live repair

The canonical alias served a Preview deployment, but the three server-only
account bridge variables were scoped only to Production. Their existing values
now cover both environments. A fresh deployment, `dpl_EF9243AnNQbRYjgAUeh3CPKrXQnU`,
was assigned to the canonical alias. Its source is the previous integrated
`ef72438e20479bff2ee0c06cd5b9b570a39c50e3` state.

Live verification returned HTTP 200 for account health and for creating a
pending link command with a secure cookie. The temporary challenge was cancelled
successfully (HTTP 200, unlinked). No WhatsApp identity was impersonated or linked.

The frontend fixes were then deployed to the same canonical alias for diagnostic
QA (before the still-queued CI and merge gates): deployment
`dpl_4rUJva8LQAn3h7oMabCHx2g5JD4A`, source
`de8ea585194bf49d8da77c2e4f96524e46366cc9`. Changed JS matched local source byte
for byte. The canonical browser completed Racing in first place, hit a Für Elise
tile for 100 points at 390×844, and rendered the honest drawing update state.
[Combined preview Racing finish](evidence/night-racing-combined-qa.jpg).

## Implemented fixes

- Racing is a finite, original coastal sprint against three simulated rivals,
  with pointer/keyboard steering, braking, off-road slowdown, real collisions,
  finish order, pause, visibility suspension and replay. Small-step simulation
  keeps collision checks reliable at slower frame rates. The HUD and brake fit
  a 320×568 phone without scrolling the header off-screen.
- Draw & Guess has remote rooms for 2–8 players; the old Club route redirects.
  The SQLite backend owns words, turns, deadlines and scores. The guest never
  receives the answer until reveal. Bounded, idempotent stroke batches support
  retries, delta polling and resumption. Each person draws twice. The page checks
  the backend capability and gives an honest update state before activation.
- Piano Tiles includes 39 selections: two complete public-domain Mutopia scores
  (all 1,454 source note events including bass/treble chords), seven complete
  familiar melodies, nine explicitly labelled excerpts, twenty original studies,
  and endless mode. These are interactive arrangements; equal tile-height gaps
  adapt the source timing. Corrected Frère Jacques retains two-finger cadences.
- The online TTT header uses the shared NIGHT shell. Puzzle, Arcade, Jigsaw and
  Stories close controls use explicitly sized SVGs. Shared links retain their
  styling and touch targets. The piano back link has an accessible mobile name.
- Corrected the Frère Jacques saved-best lookup, duplicate post-win piano replay
  control, duplicate paused-race resume control, and drawing guesses being lost
  on a network failure. Compatible secure-random drawing IDs work on HTTP QA
  origins as well as HTTPS.

- Follow-up browser QA found that Racing and Piano rounds were missing from
  Profile activity. A shared records writer now uses the existing Profile keys,
  bounded history and lifetime counts. Both terminal game outcomes record once;
  storage failure cannot interrupt gameplay. A real wrong-tile loss followed by
  navigation to Profile showed one Piano Tiles round at 390×844.

## Executed checks and rendered evidence

- `node night-play/selftest.mjs` — PASS, including Ludo rule simulations and
  Jigsaw board/save/drag rules.
- `node night-play/account-selftest.cjs` — PASS, including signatures, cookies,
  CSRF/origin checks, guest drawing bearer creation, stroke transport and errors.
- `node night-play/piano-tiles/score-selftest.mjs` — PASS, retained source hashes,
  every imported note and chord, and all 39 catalogue entries.
- `node mscc/night-play-selftest.js` on the separate backend checkout — PASS,
  including new drawing and existing party, TTT, identity, avatar and channel tests.
- Real CUA browser: separate host/guest QA actors at 390×844 and 360×740 joined,
  received a visible remote stroke, kept the word private, guessed correctly,
  rotated artist roles and completed the finite game with genuine server scores.
  These were isolated fixture identities using the actual SQLite module, not
  signed-in production WhatsApp users or independently provisioned devices.
- Real CUA browser: Racing reached three-collision loss, replay, and then a
  first-place finish at 2,500 m on a 320×568 viewport. Retained frame:
  [Racing win](evidence/night-racing-source-qa.jpg).
- Real CUA browser: a visible Piano Tiles pointer hit at 390×844 changed actual
  score to 100; pause/resume and the full-width board were inspected.
- Puzzle SVG close was opened and closed at 360×740; Jigsaw SVG close worked at
  412×915. The shared online TTT header was inspected at 412×915. Desktop welcome,
  library, Racing and piano selection screens were also rendered and inspected.
- Retained drawing completion frame:
  [Drawing game result](evidence/night-draw-source-qa.jpg).

## Required remaining gates

`node night-play/records-selftest.mjs` also passes migration, bounded retention,
count continuity, best-score preservation and unavailable-storage checks.
Independent source review found no blocking bug, including the records follow-up.

GitHub browser and backend jobs were queued at the last inspection; queued is
not passed. The new browser suite exercises five widths, actual Racing outcomes,
modern piano-library flow with a complete multi-touch song, and two independent
browser contexts against the real drawing module using fixture identities.

The protected Azure release batch must activate the backend before drawing rooms
are live. Real WhatsApp-hosted multiplayer, physical Android audio/touch quality,
and a complete all-game win/loss playthrough of the canonical deployment remain
unverified. Do not describe the entire website or real online service as finished.
