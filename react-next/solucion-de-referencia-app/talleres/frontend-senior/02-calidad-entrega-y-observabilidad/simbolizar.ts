// Traduce una traza de producción (archivo minificado:línea:columna) a la posición original, con los source maps de la construcción.
import { readFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { SourceMapConsumer } from 'source-map';

export interface Marco {
  url: string;
  linea: number;
  columna: number;
}
export interface MarcoOriginal {
  fuente: string | null;
  linea: number | null;
  columna: number | null;
  nombre: string | null;
}

export function marcosDe(pila: string): Marco[] {
  return [...pila.matchAll(/(https?:\/\/[^\s)]+?\.js):(\d+):(\d+)/g)].map((m) => ({ url: m[1]!, linea: Number(m[2]), columna: Number(m[3]) }));
}

/** El mapa se busca donde lo dice el propio archivo (`//# sourceMappingURL=`): con Turbopack su nombre NO es `<archivo>.js.map`. */
async function rutaDelMapa(archivoJs: string): Promise<string> {
  try {
    const url = /\/\/# sourceMappingURL=(\S+)\s*$/.exec(await readFile(archivoJs, 'utf8'))?.[1];
    if (url) return join(dirname(archivoJs), url);
  } catch {
    /* sin el JS: se prueba el nombre convencional */
  }
  return `${archivoJs}.map`;
}

/** `raiz` es la carpeta `.next/static` de la construcción; la URL pública `/_next/static/…` se traduce a esa carpeta. */
export async function simbolizar(pila: string, raiz: string): Promise<MarcoOriginal[]> {
  const salida: MarcoOriginal[] = [];
  const consumidores = new Map<string, SourceMapConsumer | null>();
  for (const m of marcosDe(pila)) {
    const ruta = await rutaDelMapa(join(raiz, new URL(m.url).pathname.replace(/^\/_next\/static\//, '')));
    if (!consumidores.has(ruta)) {
      try {
        consumidores.set(ruta, await new SourceMapConsumer(JSON.parse(await readFile(ruta, 'utf8'))));
      } catch {
        consumidores.set(ruta, null);
      }
    }
    const c = consumidores.get(ruta);
    if (!c) continue;
    const o = c.originalPositionFor({ line: m.linea, column: m.columna - 1 });
    salida.push({ fuente: o.source, linea: o.line, columna: o.column, nombre: o.name });
  }
  for (const c of consumidores.values()) c?.destroy();
  return salida;
}
