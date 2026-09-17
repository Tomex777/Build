import { build } from "esbuild";
import { copyFile, mkdir, rm } from "node:fs/promises";

await rm("dist", { recursive: true, force: true });
await mkdir("dist/main", { recursive: true });
await mkdir("dist/renderer", { recursive: true });

await build({
  entryPoints: ["src/main/main.ts"],
  outfile: "dist/main/main.cjs",
  bundle: true,
  platform: "node",
  format: "cjs",
  target: "node22",
  external: ["electron"],
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
  entryPoints: ["src/renderer/renderer.ts"],
  outfile: "dist/renderer/renderer.js",
  bundle: true,
  platform: "browser",
  format: "iife",
  target: "chrome140",
  sourcemap: true,
});

await copyFile("src/renderer/index.html", "dist/renderer/index.html");
await copyFile("src/renderer/styles.css", "dist/renderer/styles.css");
