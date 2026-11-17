// Presupuesto de tamaño (README frontend-senior/02): falla si el JavaScript construido (gzip) supera el límite. Se ejecuta después de `next build`.
import { readdirSync, readFileSync, statSync } from 'node:fs';
import { join } from 'node:path';
import { gzipSync } from 'node:zlib';

const LIMITE = Number(process.env.PRESUPUESTO_JS_GZIP ?? 400_000); // bytes; la construcción actual pesa ~355 000
const raiz = process.env.DIST_DIR ?? '.next';
let total = 0;
let archivos = 0;
(function recorrer(d) {
  for (const n of readdirSync(d)) {
    const r = join(d, n);
    if (statSync(r).isDirectory()) recorrer(r);
    else if (r.endsWith('.js')) {
      total += gzipSync(readFileSync(r)).length;
      archivos++;
    }
  }
})(join(raiz, 'static'));
const linea = `JavaScript construido: ${archivos} archivos, ${total} bytes gzip (límite ${LIMITE})`;
if (total > LIMITE) {
  console.error(`SUPERA EL PRESUPUESTO. ${linea}`);
  process.exit(1);
}
console.log(`Dentro del presupuesto. ${linea}`);
