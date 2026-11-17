// Punto 2 del taller: grafo de dependencias entre funcionalidades y detección de ciclos.
// Lee los imports relativos de tienda/**, los agrupa por carpeta de primer nivel y escribe GRAFO.md (Mermaid).
import { readdirSync, readFileSync, statSync, writeFileSync } from 'node:fs';
import { dirname, join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const aqui = dirname(fileURLToPath(import.meta.url));
const tienda = join(aqui, 'tienda');
const archivos = (d) => readdirSync(d).flatMap((n) => (statSync(join(d, n)).isDirectory() ? archivos(join(d, n)) : n.endsWith('.ts') && !n.includes('.rutas.eager') ? [join(d, n)] : []));
const modulo = (ruta) => { const [primero] = relative(tienda, ruta).split('/'); return primero.endsWith('.ts') ? 'raiz' : primero; };

export const ciclos = (aristas) => {
  const encontrados = [];
  const visitar = (nodo, camino) => {
    if (camino.includes(nodo)) { encontrados.push([...camino.slice(camino.indexOf(nodo)), nodo]); return; }
    for (const [a, b] of aristas) if (a === nodo) visitar(b, [...camino, nodo]);
  };
  new Set(aristas.flatMap(([a]) => a)).forEach((n) => visitar(n, []));
  return encontrados;
};

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const aristas = new Set();
  for (const f of archivos(tienda)) {
    for (const [, origen] of readFileSync(f, 'utf8').matchAll(/from '(\.[^']*)'/g)) {
      const desde = modulo(f), hacia = modulo(resolve(dirname(f), origen));
      if (desde !== hacia && desde !== 'raiz') aristas.add(`${desde}|${hacia}`);
    }
  }
  const lista = [...aristas].map((a) => a.split('|')).sort();
  writeFileSync(join(aqui, 'GRAFO.md'), `# Grafo de dependencias entre funcionalidades (generado por grafo.mjs)\n\n\`\`\`mermaid\ngraph LR\n${lista.map(([a, b]) => `  ${a} --> ${b}`).join('\n')}\n\`\`\`\n`);
  console.log(lista.map(([a, b]) => `${a} -> ${b}`).join('\n'));
  // Autocomprobación del detector con un ciclo sintético.
  const prueba = ciclos([['a', 'b'], ['b', 'c'], ['c', 'a']]);
  if (prueba.length === 0) { console.error('FALLO el detector no encontró un ciclo conocido'); process.exit(1); }
  const reales = ciclos(lista);
  console.log(reales.length ? `FALLO ciclos: ${reales.map((c) => c.join(' -> ')).join('; ')}` : 'OK   sin ciclos entre funcionalidades (el detector encuentra el ciclo sintético a -> b -> c -> a)');
  process.exit(reales.length ? 1 : 0);
}
