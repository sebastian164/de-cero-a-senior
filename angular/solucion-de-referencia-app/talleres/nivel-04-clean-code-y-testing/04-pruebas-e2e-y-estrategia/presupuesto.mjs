// Punto 4 del taller: un presupuesto de tamaño que hace fallar la construcción.
// Baja temporalmente el límite del paquete inicial a 50 kB, ejecuta `ng build` y exige que falle por el presupuesto.
// angular.json se restaura siempre.
import { spawnSync } from 'node:child_process';
import { readFileSync, rmSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const raiz = join(dirname(fileURLToPath(import.meta.url)), '../../..');
const ruta = join(raiz, 'angular.json');
const original = readFileSync(ruta, 'utf8');
try {
  const cfg = JSON.parse(original);
  const inicial = cfg.projects.app.architect.build.configurations.production.budgets.find((b) => b.type === 'initial');
  inicial.maximumError = '50kB';
  writeFileSync(ruta, JSON.stringify(cfg, null, 2));
  const r = spawnSync('npx', ['ng', 'build', 'app', '--output-path', 'tmp/presupuesto'], { cwd: raiz, encoding: 'utf8' });
  const salida = `${r.stdout}${r.stderr}`.replace(/\x1b\[[0-9;]*m/g, '');
  const ok = r.status !== 0 && /exceeded maximum budget/i.test(salida);
  console.log(ok ? 'OK    la construcción falla por el presupuesto:' : 'FALLO la construcción no falló por presupuesto');
  console.log(salida.split('\n').filter((l) => /budget/i.test(l)).slice(0, 3).join('\n'));
  process.exitCode = ok ? 0 : 1;
} finally {
  writeFileSync(ruta, original);
  rmSync(join(raiz, 'tmp/presupuesto'), { recursive: true, force: true });
}
