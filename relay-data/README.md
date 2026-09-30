# Relay data layer (Agent 2)

Owned by Agent 2 for the Relay multi-agent experiment. This subtree contains no UI and no fake production data.

## Public UI contract

Use `RelayDataSource` from `com.tomex.relay.data`.

- `snapshot()` returns tasks, activity/history, settings, and a monotonic in-process revision.
- `observe(listener)` immediately emits current state and then emits after persisted mutations.
- `createTask`, `editTask`, `deleteTask`, and `setTaskCompleted` implement the task lifecycle.
- `updateSettings` persists `ThemeMode` and `showCompleted`.
- Blank titles are rejected. No-op edits and repeated completion states do not create duplicate activity.
- Activity records remain after deletion and carry the last task title needed for history UI.

## Persistence

`AndroidAtomicRelayPersistence(context)` stores one versioned state file under the app's private internal files directory using Android `AtomicFile`. There is no server, account, cloud sync, external-storage dependency, or permission requirement.

The codec is tolerant of individual malformed records and refuses unknown file headers instead of crashing startup.

## Integration

The source files are intentionally isolated because Relay had no Android project/module on the repository baseline when Agent 2 started. Agent 4 can cherry-pick this commit, then add `relay-data/src/main/kotlin` as a source directory in the final app module or move the package unchanged under the final app source tree.

For JVM unit tests, add JUnit 4.13.2 and include `relay-data/src/test/kotlin`.

UI callers should perform mutation calls from their normal background coroutine/executor because persistence writes are synchronous and atomic.
