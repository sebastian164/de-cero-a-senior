// Punto 3 del taller: la comprobación automática de límites pasa con el código real y falla con una importación indebida.
// Se escriben archivos temporales (y se borran) con cuatro violaciones distintas y se exige que ESLint las nombre todas.
import { spawnSync } from 'node:child_process';
import { mkdirSync, rmSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const aqui = dirname(fileURLToPath(import.meta.url));
const raiz = join(aqui, '../../..');
const lint = (...rutas) => spawnSync('npx', ['eslint', '--format', 'json', ...rutas], { cwd: raiz, encoding: 'utf8' });
const resumen = (r) => JSON.parse(r.stdout).flatMap((f) => f.messages.map((m) => `${f.filePath.split('/tienda/')[1]}: ${m.message}`));

let fallos = 0;
const limpio = lint(join(aqui, 'tienda'));
const problemas = resumen(limpio);
console.log(`${limpio.status === 0 ? 'OK   ' : 'FALLO'} el código de la tienda cumple las reglas (${problemas.length} problemas)`);
if (limpio.status !== 0) { fallos++; console.log(problemas.join('\n')); }

const malos = {
  'pedidos/ui/mala-importacion-profunda.ts': "import { CatalogoApi } from '../../catalogo/datos/catalogo.api';\nexport const x = CatalogoApi;\n",
  'pedidos/dominio/mala-dominio-angular.ts': "import { signal } from '@angular/core';\nexport const y = signal(1);\n",
  'catalogo/ui/mala-ui-datos.ts': "import { CatalogoApi } from '../datos/catalogo.api';\nexport const z = CatalogoApi;\n",
  'compartido/ui/mala-compartido.ts': "import { Sesion } from '../../nucleo/sesion';\nexport const w = Sesion;\n",
};
const tienda = join(aqui, 'tienda');
try {
  for (const [ruta, codigo] of Object.entries(malos)) { mkdirSync(dirname(join(tienda, ruta)), { recursive: true }); writeFileSync(join(tienda, ruta), codigo); }
  const sucio = lint(tienda);
  const salida = resumen(sucio);
  for (const ruta of Object.keys(malos)) {
    const detectada = salida.some((m) => m.startsWith(ruta));
    console.log(`${detectada ? 'OK   ' : 'FALLO'} la importación indebida de ${ruta.split('/').pop()} ${detectada ? 'falla: ' + salida.find((m) => m.startsWith(ruta)).split(': ')[1] : 'NO se detectó'}`);
    if (!detectada) fallos++;
  }
} finally {
  for (const ruta of Object.keys(malos)) rmSync(join(tienda, ruta), { force: true });
}
process.exit(fallos ? 1 : 0);
