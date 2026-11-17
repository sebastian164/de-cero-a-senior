// Punto 6 del taller: introduce un error en el código y comprueba que alguna prueba falla.
// Aplica tres mutaciones, una por vez, y exige que `ng test` falle con cada una. Restaura siempre el archivo.
import { execFileSync, spawnSync } from 'node:child_process';
import { copyFileSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const raiz = join(dirname(fileURLToPath(import.meta.url)), '../../..');
const pedidos = join(raiz, 'proyectos/ejemplos/nivel-04-clean-code-y-testing/03-pruebas-unitarias-y-de-componentes/pedidos.ts');
const copia = `${pedidos}.original`;
const mutaciones = [
  ['IVA 19 % → 21 %', 'export const IVA = 0.19;', 'export const IVA = 0.21;'],
  ['la reversa no restaura', 'this.pedidos.set(antes); this.error.set', 'this.error.set'],
  ['estado 1 ya no es pendiente', "d.estado === 1 ? 'pendiente' : 'pagado'", "d.estado === 1 ? 'pagado' : 'pendiente'"],
];
const original = readFileSync(pedidos, 'utf8');
copyFileSync(pedidos, copia);
let fallos = 0;
try {
  for (const [nombre, de, a] of mutaciones) {
    if (!original.includes(de)) throw new Error(`no se encontró el texto a mutar: ${de}`);
    writeFileSync(pedidos, original.replace(de, a));
    const r = spawnSync('npx', ['ng', 'test', 'app', '--include', '../talleres/nivel-04-clean-code-y-testing/03-pruebas-unitarias-y-de-componentes/*.spec.ts', '--include', '../proyectos/ejemplos/nivel-04-clean-code-y-testing/03-pruebas-unitarias-y-de-componentes/*.spec.ts'], { cwd: raiz, encoding: 'utf8' });
    const detectada = r.status !== 0;
    console.log(`${detectada ? 'OK   ' : 'FALLO'} mutación "${nombre}": ${detectada ? 'alguna prueba falló' : 'ninguna prueba lo detectó'}`);
    if (!detectada) fallos++;
  }
} finally {
  writeFileSync(pedidos, original);
  execFileSync('rm', ['-f', copia]);
}
process.exit(fallos ? 1 : 0);
