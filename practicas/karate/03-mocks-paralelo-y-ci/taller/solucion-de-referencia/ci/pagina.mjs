// Ejecuta el script de la página de Bitácora (docker-kubernetes/app/web/index.html) contra una URL real (el nginx),
// con un DOM mínimo, y escribe lo que la página mostraría al cabo de 0,5 s y de 7 s.
// Uso: node pagina.mjs <base> <ruta de index.html>
import { readFileSync } from 'node:fs';
const [base, ruta] = process.argv.slice(2);
const html = readFileSync(ruta, 'utf8');
const script = html.match(/<script>([\s\S]*?)<\/script>/)[1];
const nodos = {};
const nodo = (id) => (nodos[id] ??= { id, textContent: id === 'mensaje' ? 'Cargando…' : '', value: 'nota de prueba', hijos: [],
  replaceChildren(...h) { this.hijos = h; }, addEventListener(t, f) { this[t] = f; } });
const documento = { getElementById: nodo, createElement: () => ({ textContent: '' }) };
const pedir = (url, op) => fetch(base + url, op);
const errores = [];
process.on('unhandledRejection', (e) => errores.push('promesa rechazada sin capturar: ' + e));
new Function('document', 'fetch', script)(documento, pedir);
const foto = (t) => `[${t}] mensaje="${nodo('mensaje').textContent}" version="${nodo('version').textContent}" host="${nodo('host').textContent}" notas=${nodo('notas').hijos.length}`;
await new Promise((r) => setTimeout(r, 500));
console.log(foto('0,5 s'));
await new Promise((r) => setTimeout(r, 6500));
console.log(foto('7 s  '));
nodo('nota').value = 'una nota desde la página';
await nodo('formulario').submit({ preventDefault() {} }).catch((e) => errores.push('el envío lanzó: ' + e));
await new Promise((r) => setTimeout(r, 6000));
console.log(`[envío] estado="${nodo('estado').textContent}"`);
errores.forEach((e) => console.log('[defecto] ' + e));
process.exit(0);
