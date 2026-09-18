import { spawn } from "node:child_process";

export interface RuntimeDetection {
  id: "bailey-node" | "python" | "java" | "go" | "rust";
  label: string;
  available: boolean;
  command: string;
  version?: string;
  detail?: string;
}

function runVersion(command: string, args: string[]): Promise<{ ok: boolean; output?: string; error?: string }> {
  return new Promise((resolve) => {
    let stdout = "";
    let stderr = "";
    let settled = false;
    const finish = (value: { ok: boolean; output?: string; error?: string }) => {
      if (settled) return;
      settled = true;
      clearTimeout(timer);
      resolve(value);
    };
    const child = spawn(command, args, { windowsHide: true, shell: false, stdio: ["ignore", "pipe", "pipe"] });
    const timer = setTimeout(() => {
      child.kill();
      finish({ ok: false, error: "Timed out" });
    }, 3500);
    child.stdout.setEncoding("utf8");
    child.stderr.setEncoding("utf8");
    child.stdout.on("data", (chunk: string) => { stdout += chunk; });
    child.stderr.on("data", (chunk: string) => { stderr += chunk; });
    child.on("error", (error) => finish({ ok: false, error: error.message }));
    child.on("exit", (code) => {
      const output = `${stdout}\n${stderr}`.trim().split(/\r?\n/).find(Boolean);
      finish(code === 0 ? { ok: true, output } : { ok: false, output, error: `Exited with ${String(code)}` });
    });
  });
}

async function detect(
  id: RuntimeDetection["id"],
  label: string,
  candidates: Array<{ command: string; args: string[] }>,
): Promise<RuntimeDetection> {
  for (const candidate of candidates) {
    const result = await runVersion(candidate.command, candidate.args);
    if (result.ok) {
      return { id, label, available: true, command: candidate.command, version: result.output };
    }
  }
  return { id, label, available: false, command: candidates[0]?.command ?? id, detail: "Not found on PATH" };
}

export async function detectModuleRuntimes(): Promise<RuntimeDetection[]> {
  const external = await Promise.all([
    detect("python", "Python", [
      { command: "python", args: ["--version"] },
      { command: "py", args: ["-3", "--version"] },
      { command: "python3", args: ["--version"] },
    ]),
    detect("java", "Java", [{ command: "java", args: ["-version"] }]),
    detect("go", "Go", [{ command: "go", args: ["version"] }]),
    detect("rust", "Rust", [{ command: "rustc", args: ["--version"] }]),
  ]);
  return [
    {
      id: "bailey-node",
      label: "Bailey embedded Node",
      available: true,
      command: "bailey-node",
      version: process.version,
      detail: "Bundled with Bailey Host",
    },
    ...external,
  ];
}
