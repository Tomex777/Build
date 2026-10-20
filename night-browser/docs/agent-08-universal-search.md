# Agent 8 — Universal Search implementation and integration

Branch: agent/browser-08-universal-search  
Base: feature/night-browser-multi-agent-20261020  
Owner: Agent 8, isolated files under search/ and matching tests. Shared MainActivity/BrowserController/manifest/Gradle are not edited.

## What is actually implemented

1. A **real** OpenAI chat-completions provider transport over HTTPS, with JSON-only response validation. It sends a model inference request; the existing simple Google omnibar fallback is NOT mislabelled AI.
2. Manual opt-in per question and individual page source. Source selection is initially empty; nothing is sent until the user ticks pages, provides a temporary API key and explicitly confirms the single outbound request.
3. The provider receives only typed question, approved titles, site hostnames and per-request sequential source aliases. Original full URLs and their stable local SHA-derived selection identifiers stay on device for clickable results. No content extraction, bookmarks dump, cookies, tokens or full history transmission. Titles and hosts from authentication, payment, health and other obvious sensitive routes are excluded, including URL-encoded sensitive paths; this is a conservative heuristic, not perfect DLP.
4. Private browsing is blocked at the preparation layer *and* in the UI. Caller must pass a real private-tab flag (no default). Agent 3's `BrowserTab.isPrivate` is the expected integration source. Switching into private browsing locally revokes selection/key/result and stops surfacing an in-flight answer.
5. API keys are session-only Compose state; not written to SharedPreferences, logs, files or saved instances. Clear revokes locally; the provider may still retain a request already sent per its terms. Currently fixed to api.openai.com. No silent redirects.
6. Source IDs returned by the model are checked against the approved request. Without an answer referencing approved sources, UI reports a grounded-format failure rather than inventing citations. The confirmation dialog previews the actual question and selected titles/hosts. Catalog mutations revoke old selections and invalidate running responses. This is a navigation tool based on titles and domains; it cannot answer questions requiring actual page bodies.
7. Client caps: at most 60 eligible local candidates, 12 approved pages per request, 500 query characters, 160 title characters per record, 64 KiB maximum response. Network read/connect timeouts and provider HTTP error classification.
8. Custom Compose input fields and scrollable source chooser; temporary-key field is obscured.

## Safe interface contract (Agent 1 + Agent 2 + Agent 3)

At app-shell integration, expose an "AI Search" navigation action (not a second omnibar) and render:

    UniversalSearchPanel(
        bookmarks = browser.store.bookmarks,
        journeys = browser.store.journeys,
        privateTab = browser.active.isPrivate, // available after Agent 3's tabs PR
        onBack = { /* restore prior browser screen */ },
        onOpenSource = { url -> browser.navigate(url); /* open web screen */ },
    )

Add import com.night.browser.search.UniversalSearchPanel.
**Never hardcode privateTab=false once a private-tab feature exists.** The current prototype has no private tabs. Agent 3 should wire true incognito state before shipping this panel to private profiles. MainActivity belongs to Agent 1/2 and is purposely not changed on this branch.

Agent 6 should replace history source inputs with the eventual unified History/Library records after its storage changes, maintaining granular consent. Agent 4 should keep cross-site content reads disabled. Agent 10 should test all permission and privacy transitions.

## Not implemented, not claimed

- There is no bundled on-device model and no background/automatic semantic embedding. Local search candidate generation and filtering are on-device, but **inference requires explicit remote provider setup**. Do not display an on-device AI option as working.
- Page-body semantic search, highlights, or verified factual answering are not supported. Only selected titles and hostnames can ground answers.
- No provider account/API key was supplied, so this branch CANNOT claim a successful live inference response or cost/latency measurements. Need a test key with valid model access, real Android network and user consent.
- Shell entry button and integration are left to the shell owner to avoid editing shared MainActivity while agents work in parallel.
- Build/install/live interaction status must be read from actual CI and Agent 10 testing, not inferred from file creation.

## Test matrix / release gate

- Unit tests: private flag blocks even confirmed requests, lack of confirmation blocks, per-page selection allows only selected IDs, sensitive URL/query/fragment and local hosts suppressed, forged citations removed, source cap enforced.
- CI: :app:testDebugUnitTest and :app:assembleDebug on Java 17, Android API 37.1, GitHub Actions artifact for Agent 10. Prior head `8b75ebd` passed [GitHub workflow 39358253476](https://github.com/Tomex777/Build/actions/runs/39358253476). The subsequent privacy hardening commits, added tests and UI changes require a **fresh green workflow**; do not reuse the older PASS as their evidence.
- Manual emulator: normal tab => choose two benign bookmarks, invoke once, inspect real provider response and cited links. Repeat with bad API key, offline, HTTP 401 and 429, opt-out, Clear, cancelled confirmation, zero selections and no provider configuration. Verify no request in logcat until explicit consent.
- Incognito: open private tab and ensure no controls offer an AI request; call policy with privateTab=true and confirm fail-closed.
- Sensitive URLs: bookmark /login, query token, health result and network private domain and ensure none are offered/shared.
- Packet inspection / provider account request records: verify ONLY question, chosen titles, hostnames; never full URLs, cookies, page contents, or other history.
- Test two API levels and a real phone (Agent 10), latency/cost, process death, and screen rotation; do not sign off based on a Kotlin build alone.

## Privacy clarification

OpenAI receives the prompt and the explicitly chosen record titles/domains once confirmed; this is not zero-data AI. A key or model being unavailable is a visible "not configured" or error state. The API key belongs to the user. The user must review the provider's privacy/billing terms before connecting.

## 2026-10-20 continuation — privacy and exact consent hardening

- **Leak fix:** document titles can be Gecko's fallback full URL or include an account email. The source filter now rejects URL-bearing and email-bearing titles (including percent-encoded URLs), control-character titles and double-encoded sensitive routes. A safe *URL field* alone does not make the *title field* safe to transmit.
- **Identity hardening:** duplicate catalog IDs cannot authorize different URLs under one checkbox.
- **Recall:** candidate assembly checks up to 500 saved bookmarks and 1,500 visit nodes before applying the 60-safe-source UI cap, so the first block of sensitive entries cannot hide valid pages.
- **Consent accuracy:** dialog is scrollable and displays the complete question and **all** selected 160-character title/domain payload records, rather than only five truncated records. While inference runs, input and page selection cannot silently drift from the user-confirmed request. Changing fields after a result invalidates the stale answer.
- **Unit coverage:** title URL/email/encoded URL leaks, double-encoded sensitive paths, same-ID collisions, and long runs of ineligible bookmarks/history.
- **Gate:** earlier head `6658948f6e8e9cad60607c4e79f029ec7c5d5c6b` passed 3 CI workflows (Agent 8 run 39377600104). Latest UI/privacy commit `c2316c23c98b26253ed87a8a739de078a59501c3` created new queued Agent 8 run 39384499937. Do **not** claim this latest commit built until that run finishes successfully, and do not claim a live provider call or install without real device evidence.
- **Integration remains blocked:** Agent 1/2 must wire the entrypoint, Agent 3 must provide actual private-tab state, and Agent 10 must perform consent, network-trace, device and model-provider tests.

### B-008 continued — local retrieval that reaches older saved pages

- Introduced a **bounded on-device index of up to 2,000 safe titles/hosts** (500 bookmarked plus 1,500 visited, de-duplicated); no background API indexing and no embeddings or external semantic search claims.
- The source picker now has a local title/hostname filter, with title-first ranking of results, allowing someone to find entries that are not in the first 60 visible records. Searching the local index itself is **not** presented as AI. All real AI answers still require the configured model, page-by-page opt-in and explicit provider confirmation.
- Selections persist while adjusting local filters; the provider request takes only up to 12 individually selected sources from the full index, not an arbitrary first-60 subset. The exact consent dialog previews all of them. Local index mutation revokes an outstanding selection/result; filter-only change does not.
- Filter and answer surfaces have custom inputs and scrollable results/citations on smaller devices. The question being shared **exactly as typed** is disclosed; URL-like and email-containing *page titles* are suppressed, but user-typed private information cannot be auto-guaranteed safe.
- Expanded unit tests cover older-page discovery, multi-filter consent, local ranking, sensitive title/host suppression, and no overbroad source-ID approval.
- **Current evidence:** the prior verified commit `6658948f6e8e9cad60607c4e79f029ec7c5d5c6b` passed builds; latest feature head `7aa3eea2f479bda1143208d1761fafd1aeabc886` queued Agent 8 workflow **39385066292**. No latest-head build verdict, Android installation, screenshots, live provider inference, latency or billing measurement has been verified. Agent 10 and the shell integrator still own those gates.
