import { DatabaseSync } from "node:sqlite";
import { join } from "node:path";
import type { ExternalHostServiceContext, ExternalModuleManager } from "../external/external-module-manager";

function objectParams(raw: unknown): Record<string, unknown> {
  return raw && typeof raw === "object" && !Array.isArray(raw) ? raw as Record<string, unknown> : {};
}

function databasePath(context: ExternalHostServiceContext, rawName: unknown): string {
  if (!context.dataDirectory) throw new Error("Database service requires the module storage capability.");
  const name = String(rawName ?? "main").trim().toLowerCase();
  if (!/^[a-z0-9][a-z0-9._-]{0,63}$/.test(name)) throw new Error("Database name is invalid.");
  return join(context.dataDirectory, `${name}.sqlite`);
}

function sql(raw: unknown, readOnly = false): string {
  const value = String(raw ?? "").trim();
  if (!value || value.length > 100_000) throw new Error("SQL must be 1–100000 characters.");
  if (readOnly && !/^(select|with|pragma|explain)\b/i.test(value)) throw new Error("Read service only accepts SELECT, WITH, PRAGMA or EXPLAIN statements.");
  return value;
}

function bind(statement: any, method: "all" | "get" | "run", params: unknown): any {
  if (Array.isArray(params)) return statement[method](...params);
  if (params && typeof params === "object") return statement[method](params);
  return statement[method]();
}

function withDatabase<T>(path: string, work: (db: DatabaseSync) => T): T {
  const db = new DatabaseSync(path);
  try {
    db.exec("PRAGMA journal_mode=WAL; PRAGMA foreign_keys=ON;");
    return work(db);
  } finally {
    db.close();
  }
}

export function registerDatabaseServices(manager: ExternalModuleManager): void {
  manager.registerService("database", "all", (raw, context) => {
    const input = objectParams(raw);
    return withDatabase(databasePath(context, input.database), (db) => {
      const statement = db.prepare(sql(input.sql, true));
      return { rows: bind(statement, "all", input.params) };
    });
  }, "database.read");

  manager.registerService("database", "get", (raw, context) => {
    const input = objectParams(raw);
    return withDatabase(databasePath(context, input.database), (db) => {
      const statement = db.prepare(sql(input.sql, true));
      return { row: bind(statement, "get", input.params) ?? null };
    });
  }, "database.read");

  manager.registerService("database", "run", (raw, context) => {
    const input = objectParams(raw);
    return withDatabase(databasePath(context, input.database), (db) => {
      const statement = db.prepare(sql(input.sql));
      const result = bind(statement, "run", input.params) as { changes?: number | bigint; lastInsertRowid?: number | bigint };
      return {
        changes: Number(result.changes ?? 0),
        lastInsertRowid: result.lastInsertRowid === undefined ? undefined : String(result.lastInsertRowid),
      };
    });
  }, "database.write");

  manager.registerService("database", "exec", (raw, context) => {
    const input = objectParams(raw);
    return withDatabase(databasePath(context, input.database), (db) => {
      db.exec(sql(input.sql));
      return { ok: true };
    });
  }, "database.write");
}
