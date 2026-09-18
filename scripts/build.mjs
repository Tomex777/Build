import { build } from "esbuild";
import { copyFile, mkdir, rm } from "node:fs/promises";

await rm("dist", { recursive: true, force: true });
await mkdir("dist/main", { recursive: true });
await mkdir("dist/engine", { recursive: true });
await mkdir("dist/renderer", { recursive: true });

await build({
  entryPoints: ["src/main/bootstrap.ts"],
  outfile: "dist/main/main.cjs",
  bundle: true,
  platform: "node",
  format: "cjs",
  target: "node22",
  external: ["electron", "npm", "npm/*", "@aws-sdk/client-s3", "@aws-sdk/s3-request-presigner", "@azure/storage-blob", "@google-cloud/storage", "@supabase/supabase-js"],
  sourcemap: true,
});

await build({
  entryPoints: ["src/main/preload.ts"],
  outfile: "dist/main/preload.cjs",
  bundle: true,
  platform: "node",
  format: "cjs",
  target: "node22",
  external: ["electron"],
  sourcemap: true,
});

await build({
  entryPoints: ["src/engine/worker.ts"],
  outfile: "dist/engine/worker.cjs",
  bundle: true,
  platform: "node",
  format: "cjs",
  target: "node22",
  sourcemap: true,
});

for (const [entry, outfile] of [
  ["src/renderer/renderer.ts", "dist/renderer/renderer.js"],
  ["src/renderer/studio-extras.ts", "dist/renderer/studio-extras.js"],
  ["src/renderer/chats.ts", "dist/renderer/chats.js"],
]) {
  await build({
    entryPoints: [entry],
    outfile,
    bundle: true,
    platform: "browser",
    format: "iife",
    target: "chrome140",
    sourcemap: true,
  });
}

await copyFile("src/renderer/index.html", "dist/renderer/index.html");
await copyFile("src/renderer/styles.css", "dist/renderer/styles.css");
await copyFile("src/renderer/studio.css", "dist/renderer/studio.css");
await copyFile("src/renderer/chats.css", "dist/renderer/chats.css");
