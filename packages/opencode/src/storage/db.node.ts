import { DatabaseSync } from "node:sqlite"
import { drizzle } from "drizzle-orm/node-sqlite"

// bun:sqlite-style facade over node:sqlite. Several storage modules (memory,
// knowledge, mythos, history) call `$client.run(sql, params?)` and
// `$client.query(sql).all/get(...)` — APIs that exist on bun's Database but
// not on node:sqlite's DatabaseSync. Without this shim those call sites throw
// `...$client.run is not a function` at runtime under plain node/Electron.
function withBunStyleApi(sqlite: DatabaseSync): DatabaseSync {
  const client = sqlite as DatabaseSync & {
    run(sql: string, params?: unknown[]): unknown
    query(sql: string): { all(...params: unknown[]): unknown[]; get(...params: unknown[]): unknown }
  }
  if (typeof client.run !== "function") {
    client.run = (sql: string, params?: unknown[]) => {
      if (!params || params.length === 0) return sqlite.exec(sql)
      return sqlite.prepare(sql).run(...(params.map((p) => (p === undefined ? null : p)) as never[]))
    }
  }
  if (typeof client.query !== "function") {
    client.query = (sql: string) => {
      const stmt = sqlite.prepare(sql)
      return {
        all: (...params: unknown[]) => stmt.all(...(params.map((p) => (p === undefined ? null : p)) as never[])),
        get: (...params: unknown[]) => stmt.get(...(params.map((p) => (p === undefined ? null : p)) as never[])),
      }
    }
  }
  return client
}

export function init(path: string) {
  const sqlite = withBunStyleApi(new DatabaseSync(path))
  const db = drizzle({ client: sqlite })
  return db
}
