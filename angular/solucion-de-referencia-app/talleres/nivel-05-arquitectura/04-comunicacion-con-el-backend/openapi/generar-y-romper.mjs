// Punto 5 del taller: genera los tipos desde OpenAPI y demuestra que un cambio incompatible rompe la compilación.
// 1) generar → compilar (debe pasar). 2) renombrar `cliente_nombre` en la especificación → regenerar → compilar (debe fallar).
import { spawnSync } from 'node:child_process';
import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const aqui = dirname(fileURLToPath(import.meta.url));
const herramientas = join(aqui, '../../../../proyectos/openapi/node_modules/.bin');
const yaml = join(aqui, 'pedidos.openapi.yaml');
const original = readFileSync(yaml, 'utf8');
const ejecutar = (cmd, args) => spawnSync(join(herramientas, cmd), args, { cwd: aqui, encoding: 'utf8' });
const generar = () => ejecutar('openapi-typescript', [yaml, '-o', join(aqui, 'api.generado.ts')]);
const compilar = () => ejecutar('tsc', ['--noEmit', '--strict', '--skipLibCheck', '--module', 'esnext', '--target', 'es2022', join(aqui, 'cliente.ts')]);
let fallos = 0;
try {
  const g = generar(); if (g.status !== 0) { console.error(g.stdout + g.stderr); process.exit(1); }
  const antes = compilar();
  console.log(`${antes.status === 0 ? 'OK   ' : 'FALLO'} con la especificación original el cliente compila`);
  if (antes.status !== 0) { fallos++; console.log(antes.stdout); }
  writeFileSync(yaml, original.replaceAll('cliente_nombre', 'nombre_cliente'));
  generar();
  const despues = compilar();
  const detectado = despues.status !== 0 && /cliente_nombre/.test(despues.stdout);
  console.log(`${detectado ? 'OK   ' : 'FALLO'} tras renombrar el campo en la especificación, la compilación falla: ${(despues.stdout.split('\n')[0] ?? '').slice(0, 140)}`);
  if (!detectado) fallos++;
} finally {
  writeFileSync(yaml, original);
  generar();
}
process.exit(fallos ? 1 : 0);
