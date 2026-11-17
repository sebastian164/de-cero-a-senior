// Punto 2: las dependencias más pesadas del paquete (metafile de esbuild, producido por `ng build --stats-json`).
import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
const salida = 'tmp/analisis-paquete';
execFileSync('npx', ['ng', 'build', 'app', '--stats-json', '--output-path', salida], { stdio: 'ignore' });
const meta = JSON.parse(readFileSync(`${salida}/browser-stats.json`, 'utf8'));
const pesos = {};
for (const salida_ of Object.values(meta.outputs)) {
  for (const [entrada, info] of Object.entries(salida_.inputs ?? {})) {
    const m = /node_modules\/((?:@[^/]+\/)?[^/]+)\//.exec(entrada);
    const nombre = m ? m[1] : '(código propio)';
    pesos[nombre] = (pesos[nombre] ?? 0) + info.bytesInOutput;
  }
}
const orden = Object.entries(pesos).sort((a, b) => b[1] - a[1]);
const propio = pesos['(código propio)'] ?? 0;
const terceros = orden.filter(([n]) => n !== '(código propio)');
console.log('paquete                              bytes en la salida de esbuild');
for (const [n, b] of orden.slice(0, 8)) console.log(`${n.padEnd(34)} ${(b / 1024).toFixed(1).padStart(8)} KB`);
console.log(`\nTres dependencias más pesadas: ${terceros.slice(0, 3).map(([n]) => n).join(', ')}`);
console.log(`Código propio: ${(propio / 1024).toFixed(1)} KB; dependencias: ${(terceros.reduce((s, [, b]) => s + b, 0) / 1024).toFixed(1)} KB`);
if (terceros.length < 3) process.exit(1);
