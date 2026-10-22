# NIGHT Play — Website Building Rules (WBR)

**Status: mandatory.** Applies to everyone editing, reviewing, testing or deploying the NIGHT Play web experience: the lead, every specialist, QA, collaborators, and AI coding agents. Read this document **before** website work and use it as a release checklist. These rules supplement `night-play/AGENTS.md`, `night-play/DESIGN.md`, the existing Channels design lock and the root agent coordination instructions. More specific approved design locks continue to apply.

## 1. One website, one combined preview

- **The canonical user-facing test website is [NIGHT Play — Main Combined Preview](https://night-play-combined-preview.vercel.app/).**
- Vercel project: `night-play-combined-preview` (`prj_J2thiZpQDrmSTx3llKkcYbPWETXG`). Source is `Tomex777/Build/night-play/`. The active shared integration line at the time this document was written is `feature/party-online-rooms-20261019`; **look up its actual latest head and open PRs at the start of every session**, rather than assuming this branch or its deployment is current forever.
- **Build and test changes as part of the whole NIGHT Play website**, not a disconnected example, separate public game site, or alternate collection of pages. No standalone game or new Nomi/multiplayer preview should be presented to the owner as the finished NIGHT Play experience.
- A local browser, disposable test URL or isolated branch is fine **for development and debugging**, but it is **not** the acceptance/delivery target. Merge approved changes into the shared integration codebase, update/deploy the **same combined-preview project**, and verify the actual feature there before claiming it is available to the owner.
- Do not treat `night-play.vercel.app`, an independent Vercel game/lab project, or a per-commit `.vercel.app` URL as a replacement for the **main combined preview**. Those can remain internal diagnostics; the link handed to the owner for NIGHT Play testing is the main combined-preview alias.
- **Verify deployment truth:** check Vercel's exact deployment ID, connected Git branch and commit SHA behind the alias; compare with the intended integrated commit. `READY` on an unrelated deployment, a new branch/PR, or a green CI run does not prove the main combined preview was updated. If the alias is still serving an earlier commit, say so and do not claim the new change is live.
- Preview changes must never silently promote to the live production website or change the separate bot/backend. Preserve production restrictions and obtain any required authorization before those releases.

## 2. WBR design language — no generic “AI-built” UI

- **Minimal, flat, deliberate and mobile-first.** Respect NIGHT's established visual identity, official horizon logo, navigation, colors, proportions, spacing and screen hierarchy. Preserve approved designs; don't restyle an established feature without authorization.
- **Proper SVGs.** Every icon that needs an icon must use a meaningful, crisp, consistent local SVG. No emoji, Unicode placeholders, cheap icon fonts, decorative arrows or fake SVG-looking text. Game artwork should be original/approved and relevant to actual gameplay, not stock filler.
- **No redundancy.** One clear control per action. No duplicate reload, restart, connect, settings, sound, back or pause buttons; no repeated “not connected” banners. Put options into an unobtrusive, purposeful settings/pause surface instead of cluttering gameplay with persistent controls.
- **No filler copy or fake states.** Remove pointless section labels, “preview/demo” excuses, “privacy-first/local” marketing filler, manufactured statistics, fake rooms, unverified connections, or a nonfunctional button that only looks complete.
- **Custom, designed inputs.** Do not drop default Android/browser-looking text boxes, selects or sliders into an otherwise custom UI. Preserve accessibility, labels, focus states and on-screen keyboard behavior.
- **Real interactions, not props.** A button, card, icon or setting shown to users must do what it promises. No pass-and-play disguised as online multiplayer; no fake global scoreboard or identity sync.
- Responsive layouts must work at **320, 360, 390 and 412 CSS-pixel widths**, as well as desktop. Never hide overflow bugs or shrink the playable board unnecessarily to make a screenshot look tidy. Keep touch targets reachable; no fixed HUD obscuring play.
- No unnecessary background music. Use gameplay SFX; Piano Tiles' music is its gameplay, and Jigsaw may optionally play subtle, soothing music. No redundant global audio slider; follow the individual game's settings contract.

## 3. Build against existing NIGHT Play behavior

- Inspect the actual game code, shared UI shell, current site routes, relevant tests, previous user feedback and design locks **before editing**. Reuse existing components when appropriate; do not add a second implementation of an existing action.
- Preserve the approved Games, Home, Profile, Updates, Channels, account and game flows. Channels must honor `night-play/channels/DESIGN-LOCK.md`; never break WhatsApp channel owner boundaries or invent linked channels.
- WhatsApp-linked player identity must be real data: name changes made on NIGHT Play must update authoritative player identity wherever required, not just a cosmetic label.
- Online games require **real remotely connected players**. Hosts should be able to share an invite link; guests join independently. Do not put single-phone modes under online play.
- Preserve progress/restart behavior, fair challenge, legal rules, collision and failure states. Follow existing Jigsaw, WHOT, runner, Snake, Piano Tiles and other game-specific instructions.

## 4. Actual testing is required

1. **Inspect → implement → play → screenshot → diagnose → fix → replay.** Do not equate “build passed,” “route loads,” or “I read the source” with playable quality.
2. Run `node night-play/selftest.mjs`, applicable browser/gameplay tests and `.github/workflows/night-play-preview-ci.yml`.
3. Actually play on Chromium using **real mouse/keyboard and mobile touch or emulated touch input**. For applicable games, verify first gesture, dragging/flicking, normal progression, real score changes, losing and winning, pause, restart, settings and resume. Check speed, animation, hazards, challenge and longer play sessions; inspect screenshots, not just test exit codes.
4. Test phone widths **320/360/390/412** and desktop; check clipping, device orientation as relevant and that drag follows the finger. Multiplayer needs **two independent browser/device contexts talking to the real server**, including reconnect and host/guest permissions.
5. Once changes are integrated, **retest on the main combined-preview URL**. Confirm the exact deployed commit, route and gameplay. If a feature has not reached that URL, report it as *implemented on branch / PR only*, not deployed or done.

## 5. Integration and handoff

- Agents work through small, nonconflicting branches and reviewable PRs against the **current real NIGHT Play integration head**. Do not blindly merge overlapping game engines or shared CSS. Coordinator owns the shared site shell, catalogue and combined-preview deployment. Preserve the shared-file locks in `night-play/AGENTS.md`.
- A specialist may use an isolated URL for investigating issues, but **final review and the link presented to the user must be the combined preview**, once integrated. Do not proliferate game-specific “final previews.”
- Before handing off, provide: changed files / commit / PR, tests and actual play evidence, whether integrated, **main combined-preview deployment SHA**, direct path on the main combined preview, and any unverified or blocked behavior.
- **Never claim a site was deployed merely because source was pushed.** Never say another agent is currently working unless a real active session is verified. No asynchronous work is implied by Markdown instructions.

**Quick acceptance question:** “Does the real game work and look right **inside the main combined preview**, at phone and desktop sizes, under WBR?” If not, the NIGHT Play website task is not finished.
