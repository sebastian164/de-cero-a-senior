// Puntos 1 a 4 del taller sobre un espacio de trabajo Nx real (proyectos/nx-tienda): etiquetas y reglas, grafo,
// "solo lo afectado" y el sistema de diseño. Cada comprobación imprime OK/FALLO; sale con código 1 si alguna falla.
import { execFileSync, spawnSync } from 'node:child_process';
import { appendFileSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const raiz = join(dirname(fileURLToPath(import.meta.url)), '../../../proyectos/nx-tienda');
const env = { ...process.env, NX_DAEMON: 'false', NX_NO_CLOUD: 'true', CI: 'true' };
const nx = (...args) => spawnSync('npx', ['nx', ...args, '--skip-nx-cache', '--output-style=static'], { cwd: raiz, encoding: 'utf8', env });
const nxPlano = (...args) => spawnSync('npx', ['nx', ...args], { cwd: raiz, encoding: 'utf8', env });
const git = (...args) => execFileSync('git', ['-c', 'user.email=v@v', '-c', 'user.name=v', ...args], { cwd: raiz, encoding: 'utf8' });
const limpio = (s) => s.replace(/\x1b\[[0-9;]*m/g, '');
let fallos = 0;
const comprobar = (ok, texto, detalle = '') => { console.log(`${ok ? 'OK   ' : 'FALLO'} ${texto}`); if (!ok) { fallos++; if (detalle) console.log(detalle); } };

// Seguridad: add, commit y checkout solo se ejecutan en un repositorio PROPIO de nx-tienda. Si esa carpeta no es la raíz
// de un repositorio (por ejemplo, el .git anidado no viajó con la copia), se crea uno nuevo; si no se puede, se aborta.
const realpath = (p) => execFileSync('realpath', [p], { encoding: 'utf8' }).trim();
let raizGit = '';
try { raizGit = execFileSync('git', ['rev-parse', '--show-toplevel'], { cwd: raiz, encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }).trim(); } catch { /* sin repositorio */ }
if (!raizGit || realpath(raizGit) !== realpath(raiz)) {
  console.log('      nx-tienda no es la raíz de un repositorio propio: se crea uno con git init');
  execFileSync('git', ['init', '-q'], { cwd: raiz });
  // El repositorio es temporal: se borra al terminar (también si falla) para no dejar un .git anidado en el curso.
  process.on('exit', () => rmSync(join(raiz, '.git'), { recursive: true, force: true }));
}
const toplevel = execFileSync('git', ['rev-parse', '--show-toplevel'], { cwd: raiz, encoding: 'utf8' }).trim();
if (realpath(toplevel) !== realpath(raiz)) { console.error('FALLO: no se pudo aislar el repositorio de nx-tienda; se aborta sin tocar git'); process.exit(1); }

// Estado base: todo lo que haya se confirma para poder medir "lo afectado".
git('add', '-A'); try { git('commit', '-qm', 'base'); } catch { /* nada que confirmar */ }

// 1. Reglas de dependencia por etiquetas
const bien = nx('run-many', '-t', 'lint');
comprobar(bien.status === 0, 'el espacio cumple las reglas de límites (nx run-many -t lint)', limpio(bien.stdout).slice(-600));

const lib = (ruta, contenido) => { const f = join(raiz, ruta); const antes = readFileSync(f, 'utf8'); writeFileSync(f, antes + contenido); return () => writeFileSync(f, antes); };
const casos = [
  ['scope:shop → scope:api (ámbito indebido)', 'packages/shop/feature-products/src/index.ts', "\nimport { } from '@org/api/products';\n", 'feature-products', /scope:shop|can only depend|only depend/],
  ['type:data → type:ui (tipo indebido)', 'packages/shared/models/src/index.ts', "\nimport { } from '@org/design-system';\n", 'models', /type:data|can only depend|only depend/],
];
for (const [nombre, archivo, codigo, proyecto, esperado] of casos) {
  const restaurar = lib(archivo, codigo);
  try {
    const r = nx('lint', proyecto);
    const salida = limpio(r.stdout + r.stderr);
    comprobar(r.status !== 0 && esperado.test(salida), `la dependencia indebida ${nombre} hace fallar el lint`, salida.slice(-700));
    const linea = salida.split('\n').find((l) => /can only depend|only depend/.test(l));
    if (linea) console.log(`      ${linea.trim().slice(0, 190)}`);
  } finally { restaurar(); }
}
git('checkout', '--', '.');

// 2. Grafo de dependencias
const grafoJson = join(raiz, 'tmp-grafo.json');
const g = nxPlano('graph', `--file=${grafoJson}`);
const grafo = JSON.parse(readFileSync(grafoJson, 'utf8')).graph;
const aristas = Object.entries(grafo.dependencies).flatMap(([a, ds]) => ds.filter((d) => grafo.nodes[d.target]).map((d) => `${a} -> ${d.target}`));
comprobar(g.status === 0 && aristas.length > 0, `grafo de dependencias entre proyectos (${aristas.length} aristas)`);
console.log(aristas.sort().map((a) => `      ${a}`).join('\n'));
execFileSync('rm', ['-f', grafoJson]);
appendFileSync(join(dirname(fileURLToPath(import.meta.url)), 'GRAFO-NX.txt'), '');
writeFileSync(join(dirname(fileURLToPath(import.meta.url)), 'GRAFO-NX.txt'), aristas.sort().join('\n') + '\n');

// 3. Solo lo afectado
const afectados = (archivo) => {
  const f = join(raiz, archivo); const antes = readFileSync(f, 'utf8'); writeFileSync(f, antes + '\n// cambio de prueba\n');
  try { return JSON.parse(nxPlano('show', 'projects', '--affected', '--base=HEAD', '--json').stdout).sort(); }
  finally { writeFileSync(f, antes); }
};
const todos = JSON.parse(nxPlano('show', 'projects', '--json').stdout);
const aHoja = afectados('packages/shop/feature-product-detail/src/index.ts');
const aModelos = afectados('packages/shared/models/src/index.ts');
console.log(`      proyectos en total: ${todos.length}`);
console.log(`      cambio en feature-product-detail → afectados: ${aHoja.join(', ')}`);
console.log(`      cambio en models (compartido)    → afectados: ${aModelos.join(', ')}`);
comprobar(aHoja.length < aModelos.length && aHoja.length < todos.length, 'un cambio en una hoja afecta a menos proyectos que un cambio en una biblioteca compartida');
comprobar(!aHoja.includes('data') && !aHoja.includes('api'), 'cambiar feature-product-detail no afecta a data ni a api');
const t0 = Date.now();
const f = join(raiz, 'packages/shop/feature-product-detail/src/index.ts'); const antes = readFileSync(f, 'utf8'); writeFileSync(f, antes + '\n// cambio de prueba\n');
try {
  const r = nx('affected', '-t', 'test', '--base=HEAD');
  const salida = limpio(r.stdout + r.stderr);
  const m = /(\d+) tasks?: (\d+) succeeded/.exec(salida) ?? /Successfully ran target test for (\d+) projects?/.exec(salida);
  comprobar(r.status === 0, `nx affected -t test ejecuta solo lo afectado (${m ? m[0] : 'ver salida'}; ${((Date.now() - t0) / 1000).toFixed(1)} s)`, salida.slice(-500));
} finally { writeFileSync(f, antes); }
git('checkout', '--', '.');

// 4. Sistema de diseño
const ds = nx('test', 'design-system');
comprobar(ds.status === 0, 'nx test design-system: los tres componentes se prueban', limpio(ds.stdout + ds.stderr).slice(-500));

// Construcción de la aplicación
const b = nx('build', 'shop');
comprobar(b.status === 0, 'nx build shop', limpio(b.stdout + b.stderr).slice(-500));
process.exit(fallos ? 1 : 0);
