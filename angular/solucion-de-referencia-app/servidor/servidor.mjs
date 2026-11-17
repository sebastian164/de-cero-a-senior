// Servidor de la solución: sirve la aplicación construida y una API simulada por sesión.
// Sin dependencias: solo node:http. Uso: PUERTO=49200 RAIZ=dist/app/browser node servidor/servidor.mjs
import { createReadStream, existsSync, statSync } from 'node:fs';
import { createServer } from 'node:http';
import { extname, join, normalize } from 'node:path';
import { apis, reiniciar } from './apis.mjs';

const PUERTO = Number(process.env.PUERTO ?? 49200);
const RAIZ = process.env.RAIZ ?? 'dist/app/browser';
const CABECERAS_EXTRA = process.env.CSP ? { 'Content-Security-Policy': process.env.CSP } : {};
const TIPOS = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.css': 'text/css', '.json': 'application/json', '.ico': 'image/x-icon', '.svg': 'image/svg+xml', '.png': 'image/png', '.txt': 'text/plain', '.xml': 'application/xml' };
const esperar = (ms) => new Promise((r) => setTimeout(r, ms));

const leerCuerpo = (req) => new Promise((resolver) => {
  let datos = '';
  req.on('data', (c) => (datos += c));
  req.on('end', () => { try { resolver(datos ? JSON.parse(datos) : undefined); } catch { resolver(datos); } });
});

createServer(async (req, res) => {
  const url = new URL(req.url, `http://localhost:${PUERTO}`);
  if (url.pathname.startsWith('/api/')) {
    if (url.pathname === '/api/_reiniciar') { reiniciar(); res.writeHead(204).end(); return; }
    const [, , sesion, ...resto] = url.pathname.split('/');
    const manejador = apis[sesion];
    const cuerpo = await leerCuerpo(req);
    const r = manejador ? await manejador({ metodo: req.method, ruta: '/' + resto.join('/'), url, cuerpo, cabeceras: req.headers }) : undefined;
    if (!r) { res.writeHead(404, { 'Content-Type': 'application/json' }).end('{"error":"no existe"}'); return; }
    if (r.demora) await esperar(r.demora);
    if (r.cortar) { req.socket.destroy(); return; }
    res.writeHead(r.estado ?? 200, { 'Content-Type': 'application/json', ...(r.cabeceras ?? {}) });
    res.end(r.cuerpo === undefined ? '' : JSON.stringify(r.cuerpo));
    return;
  }
  let archivo = normalize(join(RAIZ, decodeURIComponent(url.pathname)));
  if (!archivo.startsWith(normalize(RAIZ)) || !existsSync(archivo) || statSync(archivo).isDirectory()) archivo = join(RAIZ, 'index.html');
  res.writeHead(200, { 'Content-Type': TIPOS[extname(archivo)] ?? 'application/octet-stream', ...CABECERAS_EXTRA });
  createReadStream(archivo).pipe(res);
}).listen(PUERTO, () => console.log(`servidor en http://localhost:${PUERTO} (raíz ${RAIZ})`));
