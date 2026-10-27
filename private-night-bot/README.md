# Private Night bot patch

This is an overlay patch for the **separate Private Night WhatsApp bot** supplied as a `Bot/` ZIP. It is not MSCC and does not modify other apps in the repository.

## Privacy behavior
- Only the configured primary owner may run commands; other people are ignored silently.
- Owner commands posted in groups or other people's DMs are redirected to the owner's configured WhatsApp DM. Results, replies, quoted messages, and command reactions must not leak into the original chat.
- The owner's own DM remains the only place where command output is visible.
- Group events and ordinary AI/free-chat replies are disabled.
- The gate is internal code and adds no menu/command entry. `.mode public` cannot disable it.
- Branding is changed to **Private Night**. Legacy identifiers required to read existing saved sessions remain for compatibility.

## Files
- `PRIVATE_NIGHT_PATCH.zip`: patch diff plus the gate implementation and tests. Apply the diff to the matching standalone bot snapshot.
- The source archive provided separately contains the integrated changes.

## Tests and known input limitation
Run `node --test tests/privateGate.test.js` from the bot root; the included privacy-gate tests pass. JavaScript syntax checks pass. The supplied bot ZIP is missing its `modules/` directory even though the entry point references it, so the full process could not be startup-tested from that input alone. Restore the matching modules before deploying.

This is an overlay on a new branch because `main` does not contain the standalone bot source tree. No unrelated app was overwritten and nothing was merged to `main`.
