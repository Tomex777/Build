import { spawn } from "node:child_process";
import { access, chmod, mkdir, readFile, writeFile } from "node:fs/promises";
import { createRequire } from "node:module";
import { delimiter, dirname, join } from "node:path";
import { parseExternalModuleManifest } from "../external/protocol";

export interface DependencyInstallResult {
  ok: boolean;
  moduleId: string;
  runtime: string;
  installed: boolean;
  detail: string;
  output: string[];
}

function safeModuleId(value: unknown): string {
  const id = String(value ?? "").trim().toLowerCase();
  if (!/^[a-z0-9][a-z0-9.-]{0,63}$/.test(id)) throw new Error("Invalid module id.");
  return id;
}

async function exists(path: string): Promise<boolean> {
  try {
    await access(path);
    return true;
  } catch {
    return false;
  }
}

function run(
  executable: string,
  args: string[],
  cwd: string,
  env: NodeJS.ProcessEnv = process.env,
  timeoutMs = 10 * 60_000,
): Promise<string[]> {
  return new Promise((resolve, reject) => {
    const output: string[] = [];
    let settled = false;
    const child = spawn(executable, args, {
      cwd,
      env,
      windowsHide: true,
      shell: false,
      stdio: ["ignore", "pipe", "pipe"],
    });
    const add = (prefix: string, chunk: unknown) => {
      for (const line of String(chunk).split(/\r?\n/).map((item) => item.trim()).filter(Boolean)) {
        output.push(prefix + line);
        if (output.length > 200) output.shift();
      }
    };
    child.stdout.setEncoding("utf8");
    child.stderr.setEncoding("utf8");
    child.stdout.on("data", (chunk) => add("", chunk));
    child.stderr.on("data", (chunk) => add("! ", chunk));

    const timer = setTimeout(() => {
      if (!child.killed) child.kill();
      if (!settled) {
        settled = true;
        reject(new Error("Dependency installation timed out.\n" + output.slice(-20).join("\n")));
      }
    }, timeoutMs);

    child.on("error", (error) => {
      if (settled) return;
      settled = true;
      clearTimeout(timer);
      reject(error);
    });
    child.on("exit", (code) => {
      if (settled) return;
      settled = true;
      clearTimeout(timer);
      if (code === 0) resolve(output);
      else reject(new Error(output.slice(-30).join("\n") || "Dependency installer exited with " + String(code) + "."));
    });
  });
}

export class ModuleRuntimeManager {
  constructor(
    private readonly modulesRoot: string,
    private readonly appExecutable = process.execPath,
    private readonly withProgramNetworkAccess: <T>(program: string, operation: () => Promise<T>) => Promise<T> = (_program, operation) => operation(),
  ) {}

  private async manifest(moduleId: string) {
    const directory = join(this.modulesRoot, moduleId);
    const raw = await readFile(join(directory, "bailey.module.json"), "utf8");
    return { directory, manifest: parseExternalModuleManifest(JSON.parse(raw)) };
  }

  private async bundledNpm(directory: string): Promise<string[]> {
    const localRequire = createRequire(__filename);
    let npmPackagePath = localRequire.resolve("npm/package.json");
    if (npmPackagePath.includes("app.asar")) npmPackagePath = npmPackagePath.replace("app.asar", "app.asar.unpacked");
    const npmCli = join(dirname(npmPackagePath), "bin", "npm-cli.js");
    const shimDir = join(directory, ".bailey-runtime");
    await mkdir(shimDir, { recursive: true });

    if (process.platform === "win32") {
      const nodeShim = "@echo off\r\nset ELECTRON_RUN_AS_NODE=1\r\n\"" + this.appExecutable.replace(/"/g, "\"\"") + "\" %*\r\n";
      await writeFile(join(shimDir, "node.cmd"), nodeShim, "utf8");
    } else {
      const nodeShim = "#!/bin/sh\nELECTRON_RUN_AS_NODE=1 exec \"" + this.appExecutable.replace(/"/g, "\\\"") + "\" \"$@\"\n";
      const path = join(shimDir, "node");
      await writeFile(path, nodeShim, "utf8");
      await chmod(path, 0o755);
    }

    return run(
      this.appExecutable,
      [npmCli, "install", "--omit=dev", "--no-audit", "--no-fund"],
      directory,
      {
        ...process.env,
        ELECTRON_RUN_AS_NODE: "1",
        PATH: shimDir + delimiter + (process.env.PATH ?? ""),
        npm_config_update_notifier: "false",
      },
    );
  }

  private async pythonDependencies(directory: string, command: string): Promise<string[]> {
    const requirements = join(directory, "requirements.txt");
    if (!(await exists(requirements))) return [];
    const venv = join(directory, ".bailey-venv");
    const pythonCommand = command === "bailey-python"
      ? (process.platform === "win32" ? "python" : "python3")
      : command;
    const prefix = pythonCommand === "py" ? ["-3"] : [];
    const createOutput = await run(pythonCommand, [...prefix, "-m", "venv", venv], directory);
    const python = process.platform === "win32"
      ? join(venv, "Scripts", "python.exe")
      : join(venv, "bin", "python");
    const installOutput = await this.withProgramNetworkAccess(python, () => run(
      python,
      ["-m", "pip", "install", "--disable-pip-version-check", "-r", requirements],
      directory,
    ));
    return [...createOutput, ...installOutput];
  }

  async installDependencies(moduleIdValue: unknown): Promise<DependencyInstallResult> {
    const moduleId = safeModuleId(moduleIdValue);
    const { directory, manifest } = await this.manifest(moduleId);
    const command = manifest.runtime.command;

    if (command === "bailey-node") {
      if (!(await exists(join(directory, "package.json")))) {
        return {
          ok: true,
          moduleId,
          runtime: "javascript",
          installed: false,
          detail: "No package.json; nothing to install.",
          output: [],
        };
      }
      const output = await this.bundledNpm(directory);
      return {
        ok: true,
        moduleId,
        runtime: "javascript",
        installed: true,
        detail: "JavaScript dependencies installed.",
        output,
      };
    }

    if (["python", "python3", "py", "bailey-python"].includes(command)) {
      if (!(await exists(join(directory, "requirements.txt")))) {
        return {
          ok: true,
          moduleId,
          runtime: "python",
          installed: false,
          detail: "No requirements.txt; nothing to install.",
          output: [],
        };
      }
      const output = await this.pythonDependencies(directory, command);
      return {
        ok: true,
        moduleId,
        runtime: "python",
        installed: true,
        detail: "Python virtual environment is ready.",
        output,
      };
    }

    return {
      ok: true,
      moduleId,
      runtime: command,
      installed: false,
      detail: "Bailey does not manage dependencies for " + command + ".",
      output: [],
    };
  }
}
