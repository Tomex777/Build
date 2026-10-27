# Private Night bot patch

This overlay is for the **separate Private Night WhatsApp bot** supplied as a `Bot/` ZIP. It is not MSCC and does not modify other apps in this repository.

## Required behavior
- Only the configured primary owner may run commands; other people are ignored silently.
- Owner commands posted in groups or another person's DM are rerouted to the owner's configured WhatsApp DM. The original chat must not receive the result, a reply, quote, or reaction.
- Commands in the owner's own DM run there normally.
- Group events and ordinary AI/free-chat replies are disabled.
- The gate is internal and adds no command/menu entry. `.mode public` cannot disable it.
- Branding is **Private Night**. Legacy identifiers needed for reading existing sessions remain for compatibility.

## Branch contents
- `core/privateGate.js`: the owner-only routing gate.
- `tests/privateGate.test.js`: focused tests for routing, denial, primary-owner enforcement, private buttons, and output isolation.
- `PRIVATE_NIGHT_PATCH.diff.gz`: compressed unified patch for the integrated changes to `index.js`, `handler.js`, config, branding, and merged nested patch. Decompress it before applying.
- The full modified bot source is supplied as a separate ZIP in the task response.

This repository's `main` branch did not contain the standalone bot source tree, so this branch is an overlay rather than a replacement for unrelated apps. Nothing was merged into `main`.

## Checks and limitation
Run `node --test tests/privateGate.test.js` from the **full bot root** after applying the patch; all eight included tests pass there. All JavaScript files in the supplied source were syntax-checked. The supplied ZIP is missing the `modules/` directory that `index.js`/ `handler.js` reference, so live startup could not be verified from this input alone. Restore those matching module files before deployment.
