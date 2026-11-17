// Prototipo del chat (problema 2 del taller): orden por secuencia del servidor, envío optimista, reintento y reconexión sin duplicados.
export type Estado = 'enviando' | 'enviado' | 'fallido';
export interface Mensaje {
  idCliente: string;
  secuencia: number | null;
  texto: string;
  estado: Estado;
}
export interface DelServidor {
  idCliente: string;
  secuencia: number;
  texto: string;
}

export interface Chat {
  mensajes: Mensaje[];
  ultimaSecuencia: number;
}
export const vacio = (): Chat => ({ mensajes: [], ultimaSecuencia: 0 });

const ordenar = (m: Mensaje[]): Mensaje[] => [...m].sort((a, b) => (a.secuencia ?? Infinity) - (b.secuencia ?? Infinity) || a.idCliente.localeCompare(b.idCliente));

/** Envío optimista: el mensaje aparece al final, sin secuencia, hasta que el servidor lo confirme. */
export function enviarOptimista(c: Chat, idCliente: string, texto: string): Chat {
  if (c.mensajes.some((m) => m.idCliente === idCliente)) return c;
  return { ...c, mensajes: [...c.mensajes, { idCliente, secuencia: null, texto, estado: 'enviando' }] };
}

/** Llega un mensaje del servidor (propio o ajeno, en cualquier orden, posiblemente repetido). */
export function recibir(c: Chat, s: DelServidor): Chat {
  const existe = c.mensajes.find((m) => m.idCliente === s.idCliente);
  const confirmado: Mensaje = { idCliente: s.idCliente, secuencia: s.secuencia, texto: s.texto, estado: 'enviado' };
  const mensajes = existe ? c.mensajes.map((m) => (m.idCliente === s.idCliente ? confirmado : m)) : [...c.mensajes, confirmado];
  return { mensajes: ordenar(mensajes), ultimaSecuencia: Math.max(c.ultimaSecuencia, s.secuencia) };
}

export const marcarFallido = (c: Chat, idCliente: string): Chat => ({
  ...c,
  mensajes: c.mensajes.map((m) => (m.idCliente === idCliente && m.estado === 'enviando' ? { ...m, estado: 'fallido' } : m)),
});
export const reintentar = (c: Chat, idCliente: string): Chat => ({
  ...c,
  mensajes: c.mensajes.map((m) => (m.idCliente === idCliente && m.estado === 'fallido' ? { ...m, estado: 'enviando' } : m)),
});
/** Al reconectar: qué pedir al servidor (todo lo posterior a lo último visto) y qué reenviar (lo que quedó sin confirmar). */
export const alReconectar = (c: Chat) => ({
  desde: c.ultimaSecuencia,
  reenviar: c.mensajes.filter((m) => m.estado !== 'enviado').map((m) => ({ idCliente: m.idCliente, texto: m.texto })),
});

/** Servidor simulado: asigna la secuencia y es idempotente por idCliente (por eso reenviar es seguro). */
export class ServidorSimulado {
  private log: DelServidor[] = [];
  publicar(idCliente: string, texto: string): DelServidor {
    const previo = this.log.find((m) => m.idCliente === idCliente);
    if (previo) return previo;
    const m = { idCliente, secuencia: this.log.length + 1, texto };
    this.log.push(m);
    return m;
  }
  desde(secuencia: number): DelServidor[] {
    return this.log.filter((m) => m.secuencia > secuencia);
  }
}
