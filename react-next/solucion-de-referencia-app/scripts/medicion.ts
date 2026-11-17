// Las pruebas que miden algo dejan el número en mediciones/vitest.jsonl para citarlo en los ANALISIS.md.
import { appendFileSync, mkdirSync } from 'node:fs';
import { join } from 'node:path';

const dir = join(process.cwd(), 'mediciones');

export function registrarMedicion(nombre: string, valores: Record<string, number | string>) {
  mkdirSync(dir, { recursive: true });
  appendFileSync(join(dir, 'vitest.jsonl'), JSON.stringify({ nombre, ...valores, fecha: new Date().toISOString() }) + '\n');
}
