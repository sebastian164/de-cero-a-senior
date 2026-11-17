// Sirve dist/host en 49281 y dist/remote en 49282 (con CORS: el anfitrión descarga el remoto desde otro origen).
import { createReadStream, existsSync, statSync } from 'node:fs';
import { createServer } from 'node:http';
import { extname, join, normalize } from 'node:path';
const TIPOS = { '.html': 'text/html', '.js': 'text/javascript', '.mjs': 'text/javascript', '.css': 'text/css', '.json': 'application/json', '.ico': 'image/x-icon' };
const servir = (raiz, puerto, cors) => createServer((req, res) => {
  const ruta = new URL(req.url, 'http://x').pathname;
  let f = normalize(join(raiz, ruta));
  if (!f.startsWith(normalize(raiz)) || !existsSync(f) || statSync(f).isDirectory()) f = join(raiz, 'index.html');
  res.writeHead(200, { 'Content-Type': TIPOS[extname(f)] ?? 'application/octet-stream', ...(cors ? { 'Access-Control-Allow-Origin': '*' } : {}) });
  createReadStream(f).pipe(res);
}).listen(puerto);
const conCors = process.env.SIN_CORS !== '1';
servir('dist/host/browser', 49281, false);
servir('dist/remote/browser', 49282, conCors);
console.log('anfitrión 49281, remoto 49282, CORS', conCors);
