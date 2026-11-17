// Punto 6 del taller: mide cuánto tarda cada nivel de la estrategia en este proyecto.
import { spawnSync } from 'node:child_process';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const raiz = join(dirname(fileURLToPath(import.meta.url)), '../../..');
const niveles = [
  ['Estático (ngc, tipos estrictos)', ['npx', 'ngc', '-p', 'tsconfig.app.json', '--noEmit']],
  ['Unitarias y de componentes (ng test, todas)', ['npx', 'ng', 'test', 'app']],
  ['E2E de la tienda (Playwright, 7 pruebas)', ['npx', 'playwright', 'test', 'proyectos/ejemplos/nivel-04-clean-code-y-testing/04-pruebas-e2e-y-estrategia', 'talleres/nivel-04-clean-code-y-testing/04-pruebas-e2e-y-estrategia'], { PUERTO_E2E: '49203' }],
];
let fallos = 0;
for (const [nombre, [cmd, ...args], env] of niveles) {
  const t0 = Date.now();
  const r = spawnSync(cmd, args, { cwd: raiz, encoding: 'utf8', env: { ...process.env, ...env } });
  const s = ((Date.now() - t0) / 1000).toFixed(1);
  console.log(`${r.status === 0 ? 'OK   ' : 'FALLO'} ${nombre}: ${s} s`);
  if (r.status !== 0) fallos++;
}
process.exit(fallos ? 1 : 0);
