import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname } from "node:path";

export type TrackedTrafficCategory = "module-services" | "storage";

interface DayRecord {
  "module-services": number;
  storage: number;
}

interface TrafficDocument {
  version: 1;
  days: Record<string, DayRecord>;
}

function localDay(date: Date): string {
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${date.getFullYear()}-${month}-${day}`;
}

export class NetworkTrafficLedger {
  private document: TrafficDocument = { version: 1, days: {} };
  private writeTail: Promise<void> = Promise.resolve();
  constructor(private readonly filePath: string) {}

  async load(): Promise<void> {
    try {
      const parsed = JSON.parse(await readFile(this.filePath, "utf8")) as TrafficDocument;
      if (parsed.version === 1 && parsed.days && typeof parsed.days === "object") this.document = parsed;
    } catch (error) {
      if ((error as NodeJS.ErrnoException).code !== "ENOENT") throw error;
      await this.flush();
    }
  }

  record(category: TrackedTrafficCategory, bytes: number): void {
    if (!Number.isSafeInteger(bytes) || bytes <= 0) return;
    const today = localDay(new Date());
    const current = this.document.days[today] ??= { "module-services": 0, storage: 0 };
    current[category] = Math.min(Number.MAX_SAFE_INTEGER, current[category] + bytes);
    const oldest = new Date();
    oldest.setDate(oldest.getDate() - 14);
    const cutoff = localDay(oldest);
    for (const day of Object.keys(this.document.days)) if (day < cutoff) delete this.document.days[day];
    this.writeTail = this.writeTail.then(() => this.flush()).catch(() => {});
  }

  today(): { moduleServicesBytes: number; storageBytes: number } {
    const record = this.document.days[localDay(new Date())];
    return { moduleServicesBytes: record?.["module-services"] ?? 0, storageBytes: record?.storage ?? 0 };
  }

  private async flush(): Promise<void> {
    await mkdir(dirname(this.filePath), { recursive: true });
    await writeFile(this.filePath, `${JSON.stringify(this.document, null, 2)}\n`, "utf8");
  }
}
