# Structured code and copy messages (WIP)

Annie supports first-party, data-only message kinds `code` and `copy`.
Both are rendered in the existing chat bubbles by the Kotlin/Compose host,
with user-tappable Copy buttons. Neither executes arbitrary source.

JavaScript QuickJS package commands can return e.g.:

```js
return annie.messages.code({
  title: "Example script",
  language: "javascript",
  code: "const n = 42;\\nconsole.log(n);"
});
```

or

```js
return annie.messages.copy({
  title: "Download link",
  text: "https://example.com/file"
});
```

Do not store API keys or passwords in copyable messages unless the user
explicitly requests this. Native clipboard content may be readable by
the user and by Android clipboard integration.

These are WIP sources on the rich-message-types branch; they are **not** part
of an approved release until merged and validated on both emulators.
