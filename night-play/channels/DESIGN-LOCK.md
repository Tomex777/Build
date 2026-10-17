# NIGHT Play — Channels UI LOCKED

**Approved by product owner:** 2026-10-17  
**Frozen visual reference:** commit `13331aa68bd3bfb47380cd9c20d4eac353f72ebe`  
**Reference branch:** `baseline/night-play-channels-approved-20261017`  
**Preview approved:** `https://night-play-combined-preview-cz5h3cemn-tomex777.vercel.app/channels/`

## Approval scope

The **Your channels** page layout, styling, navigation and mobile presentation are approved. **Do not redesign or cosmetically restyle** without fresh, explicit user approval. This supersedes previous channel-screen mockups, the former oversized "Your Channels." screen and the separate channel-page header.

Preserve the following:

- The actual NIGHT Play **horizon logo** and **shared header** with Home, Games and Profile; at mobile widths, use the site's **same bottom navigation**. Visual chrome must remain shared with the main website via `night-play/site-shell.css`.
- Approved **warm near-white background, forest-green accents**, restrained typography, compact heading, page spacing, button shapes, thin borders and quiet flat presentation. No generic arrows, faux glossy decorations, stacked unnecessary controls, repeated descriptions or oversized onboarding cards.
- **No channel URL textbox, link icon or "Copy channel link" button.** The user shares a link privately with NIGHT on WhatsApp, then sends `.joinchannel` and explicitly consents with Yes/No. The website displays their verified/claimable channels, not a replacement WhatsApp invitation form.
- **No redundant Refresh channels button.** Load on entry/visibility; when the backend is genuinely unavailable, offer *Try again* only. Do not conflate a 503/backend outage with an unlinked WhatsApp identity.
- **Compact empty state** and a short, contextual explanation of how to connect another channel. Show **real backend-sourced** channels only; never pre-fill fake names. Preserve legitimate per-channel **Copy proof**, **Check access** and **Disconnect** tools, with an in-page disconnect confirmation and a warning that removing a website claim does not revoke WhatsApp admin permissions.
- Keep authenticated content private to the linked owner. On mobile no horizontal scrolling, floating toolbar-like application controls or default browser dialogs.
- Preserve mobile accessibility, focus states, touch-sized controls and meaningful text statuses.

## Regression gate

`node night-play/channels/browser-smoke.cjs` is included in `.github/workflows/night-play-preview-ci.yml`. It exercises the signed account mock, mobile and desktop header/nav, consent guide, empty state, proof copy, verification, guarded disconnect, outage behavior and screenshot captures. CI passing **does not replace human visual review**. Compare screenshots against the baseline when touching `night-play/channels/index.html` or `night-play/site-shell.css`.

## Allowed changes without a new design approval

Backend fixes, security/authentication improvements, accessibility fixes and essential working features are welcome **provided the approved appearance and control hierarchy are preserved**. A request to redesign any of these elements requires new product-owner approval. The baseline branch is a stable restore point, **not a GitHub-enforced branch-protection policy**.

The Channels **visual approval is separate** from verification that the MSCC Azure backend, WhatsApp channel admin permissions and real Anonymous Nights publishing are working in production.
