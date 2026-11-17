// API simulada: un módulo por sesión en ./apis/<sesion>.mjs que exporta { manejador, reiniciar }.
import { readdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const carpeta = join(dirname(fileURLToPath(import.meta.url)), 'apis');
export const apis = {};
const reinicios = [];
for (const archivo of readdirSync(carpeta).filter((a) => a.endsWith('.mjs')).sort()) {
  const modulo = await import(pathToFileURL(join(carpeta, archivo)).href);
  apis[archivo.replace('.mjs', '')] = modulo.manejador;
  if (modulo.reiniciar) reinicios.push(modulo.reiniciar);
}
export const reiniciar = () => reinicios.forEach((r) => r());
