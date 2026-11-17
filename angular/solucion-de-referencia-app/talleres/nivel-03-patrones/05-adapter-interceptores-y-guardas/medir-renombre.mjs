// 1. Medición: renombra un campo del DTO (userId → user_id) en una copia del taller y deja que el compilador
//    diga qué archivos se rompen. Esos son los archivos que habría que tocar.
import { execFileSync } from 'node:child_process';
import { cpSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { dirname, join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';

const aqui = dirname(fileURLToPath(import.meta.url));
const raiz = join(aqui, '../../..');
const copia = join(raiz, 'tmp/renombre');
rmSync(copia, { recursive: true, force: true });
cpSync(aqui, copia, { recursive: true, filter: (f) => !f.endsWith('.mjs') && !f.endsWith('.md') });
const adaptadores = join(copia, 'adaptadores.ts');
writeFileSync(adaptadores, readFileSync(adaptadores, 'utf8').replace('interface PublicacionDto { userId: number;', 'interface PublicacionDto { user_id: number;'));
writeFileSync(join(copia, 'tsconfig.json'), JSON.stringify({ extends: '../../tsconfig.json', compilerOptions: { noEmit: true, types: ['vitest/globals'] }, include: ['*.ts'] }));
let salida = '';
try { execFileSync('npx', ['tsc', '--pretty', 'false', '-p', join(copia, 'tsconfig.json')], { cwd: raiz, encoding: 'utf8', stdio: 'pipe' }); } catch (e) { salida = e.stdout + e.stderr; }
const archivos = [...new Set([...salida.matchAll(/^(.*?\.ts)\(/gm)].map((m) => relative(copia, join(raiz, m[1]))))];
console.log(`Archivos que se rompen al renombrar userId → user_id: ${archivos.length}`);
archivos.forEach((a) => console.log(`  ${a}`));
rmSync(copia, { recursive: true, force: true });
const esperados = ['adaptadores.ts', 'taller.spec.ts'];
process.exit(JSON.stringify(archivos.sort()) === JSON.stringify(esperados) ? 0 : 1);
