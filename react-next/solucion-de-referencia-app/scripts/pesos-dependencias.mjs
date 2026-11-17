// Taller frontend-senior/02, punto 7: peso real de cada dependencia de producción en un paquete de navegador.
// Empaqueta `import * as m from '<paquete>'` con esbuild (minificado, react y react-dom fuera) y mide el gzip: es la cota superior (sin tree-shaking
// fino); el peso que de verdad viaja depende de qué se importa. También cuenta en cuántos archivos del código se importa.
import { build } from 'esbuild';
import { gzipSync } from 'node:zlib';
import { readFileSync, readdirSync, statSync, writeFileSync, mkdirSync } from 'node:fs';
import { join } from 'node:path';

const dependencias = Object.keys(JSON.parse(readFileSync('package.json', 'utf8')).dependencies);
const archivos = [];
recorrer2('src'); recorrer2('talleres'); recorrer2('proyectos');
function recorrer2(d) {
  for (const n of readdirSync(d)) {
    const r = join(d, n);
    if (statSync(r).isDirectory()) { if (!['node_modules', 'antes'].includes(n)) recorrer2(r); }
    else if (/\.(ts|tsx)$/.test(r)) archivos.push([r, readFileSync(r, 'utf8')]);
  }
}
const filas = [];
for (const paquete of dependencias) {
  const usos = archivos.filter(([, t]) => new RegExp(`from ['"]${paquete.replace(/[/@]/g, (c) => `\\${c}`)}(['"/])`).test(t)).map(([r]) => r);
  let bytes = null; let gzip = null;
  if (!['next', 'server-only', 'pg', 'react', 'react-dom'].includes(paquete)) {
    try {
      const r = await build({ stdin: { contents: `import * as m from '${paquete}'; globalThis.__m = m;`, resolveDir: process.cwd(), loader: 'js' }, bundle: true, minify: true, write: false, platform: 'browser', format: 'esm', external: ['react', 'react-dom', 'react/jsx-runtime'], logLevel: 'silent', define: { 'process.env.NODE_ENV': '"production"' } });
      const salida = r.outputFiles[0].contents; bytes = salida.length; gzip = gzipSync(salida).length;
    } catch (e) { bytes = null; console.error(paquete, String(e.message).split('\n')[0]); }
  }
  filas.push({ paquete, version: JSON.parse(readFileSync(`node_modules/${paquete}/package.json`, 'utf8')).version, bytesMinificados: bytes, bytesGzip: gzip, archivosQueLoImportan: usos.length, importadoDesde: usos.slice(0, 3) });
}
mkdirSync('mediciones', { recursive: true });
writeFileSync('mediciones/pesos-dependencias.json', JSON.stringify(filas, null, 2));
for (const f of filas) console.log(`${f.paquete}@${f.version}`.padEnd(34), String(f.bytesGzip ?? '(servidor/entorno)').padStart(8), 'gzip', ` importado en ${f.archivosQueLoImportan} archivo(s)`);
