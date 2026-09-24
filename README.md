# Annie

Annie is a command-first media chat UI. The app keeps provider adapters isolated and routes deterministic commands without requiring AI.

## Local checks

- `npm test` runs extension-host edge-case tests.
- `npm run check` checks JavaScript syntax.

GitHub Actions runs both checks on pushes and pull requests.

## Extension host

Each adapter declares an ID, display name, supported media types, enabled state, and priority, and implements `search({ query, mediaType, signal })`. Routing tries the configured default first, then enabled fallbacks by priority. Empty responses, malformed payloads, exceptions, timeouts, and cancellation are recorded per attempt.

The initial app enables only YouTube link playback through YouTube's official privacy-enhanced embedded player. It does not extract or download media. Catalog integrations require an authorized provider API or content license before an adapter can be enabled.

The UI displays the requested provider names and their current availability, rather than silently substituting a different source.
