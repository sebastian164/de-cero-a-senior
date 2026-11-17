// Taller TS 03 — puntos 3, 4 y 5.
export const esperar = (ms: number, senal?: AbortSignal) =>
  new Promise<void>((resolver, rechazar) => {
    const t = setTimeout(resolver, ms);
    senal?.addEventListener('abort', () => {
      clearTimeout(t);
      rechazar(new DOMException('Cancelada', 'AbortError'));
    });
  });

// Punto 3: tres consultas simuladas.
const consulta = (ms: number, valor: string) => async () => { await esperar(ms); return valor; };
export const consultas = [consulta(300, 'cliente'), consulta(500, 'pedidos'), consulta(400, 'facturas')] as const;

export async function enSerie(): Promise<{ valores: string[]; ms: number }> {
  const inicio = performance.now();
  const valores: string[] = [];
  for (const c of consultas) valores.push(await c());
  return { valores, ms: performance.now() - inicio };
}

export async function enParalelo(): Promise<{ valores: string[]; ms: number }> {
  const inicio = performance.now();
  const valores = await Promise.all(consultas.map((c) => c()));
  return { valores, ms: performance.now() - inicio };
}

// Punto 4: buscador que dispara una petición por tecla. La respuesta tarda más cuanto más corto es el término
// (como un servidor que devuelve más resultados para "c" que para "cafe").
export const retardoPara = (termino: string) => Math.max(10, 120 - termino.length * 25);
export async function buscarEnServidor(termino: string, senal?: AbortSignal): Promise<string> {
  await esperar(retardoPara(termino), senal);
  return `resultados de "${termino}"`;
}

/** Con carrera: cada respuesta pisa la pantalla cuando llega, sin importar el orden. */
export function crearBuscadorIngenuo() {
  const pantalla = { mostrado: '' };
  const pendientes: Promise<void>[] = [];
  return {
    pantalla,
    teclear(termino: string) {
      pendientes.push(buscarEnServidor(termino).then((r) => { pantalla.mostrado = r; }));
    },
    esperarTodo: () => Promise.all(pendientes),
  };
}

/** Corregido: cada tecla cancela la petición anterior con AbortController. */
export function crearBuscadorConCancelacion() {
  const pantalla = { mostrado: '', canceladas: 0 };
  const pendientes: Promise<void>[] = [];
  let control: AbortController | null = null;
  return {
    pantalla,
    teclear(termino: string) {
      control?.abort();
      control = new AbortController();
      pendientes.push(
        buscarEnServidor(termino, control.signal)
          .then((r) => { pantalla.mostrado = r; })
          .catch((e: unknown) => {
            if (e instanceof DOMException && e.name === 'AbortError') pantalla.canceladas++;
            else throw e;
          }),
      );
    },
    esperarTodo: () => Promise.all(pendientes),
  };
}

// Punto 5: orden del bucle de eventos.
export function ordenDelBucle(): Promise<string[]> {
  const orden: string[] = [];
  return new Promise((resolver) => {
    orden.push('1 síncrono');
    setTimeout(() => { orden.push('4 setTimeout (tarea)'); resolver(orden); }, 0);
    Promise.resolve().then(() => orden.push('3 promesa (microtarea)'));
    orden.push('2 síncrono');
  });
}
