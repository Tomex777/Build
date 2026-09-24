import { cp, mkdir, readFile, rm, writeFile } from "node:fs/promises";
import { resolve } from "node:path";

const root = resolve(".");
const dist = resolve(root, "dist");
const html = await readFile(resolve(root, "index.html"), "utf8");
if (!html.includes("./src/styles.css") || !html.includes("./src/app.mjs")) {
  throw new Error("Annie HTML must reference its local source assets");
}
await rm(dist, { recursive: true, force: true });
await mkdir(dist, { recursive: true });
await cp(resolve(root, "index.html"), resolve(dist, "index.html"));
await cp(resolve(root, "src"), resolve(dist, "src"), { recursive: true });
await writeFile(resolve(dist, "README.txt"), "Annie web build. Serve this folder over HTTP; configure a referrer-restricted YouTube Data API key in /extensions.\n", "utf8");
console.log("Annie web build written to dist/");
