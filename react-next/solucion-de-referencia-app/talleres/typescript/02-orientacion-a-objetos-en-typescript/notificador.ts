// Taller TS 02 — punto 3: interfaz Notificador, tres implementaciones y una función que no las conoce.
export interface Notificador {
  enviar(destino: string, mensaje: string): Promise<void>;
}

export class NotificadorCorreo implements Notificador {
  readonly enviados: string[] = [];
  async enviar(destino: string, mensaje: string) {
    if (!destino.includes('@')) throw new Error(`Correo inválido: ${destino}`);
    this.enviados.push(`correo:${destino}:${mensaje}`);
  }
}

export class NotificadorSms implements Notificador {
  readonly enviados: string[] = [];
  async enviar(destino: string, mensaje: string) {
    this.enviados.push(`sms:${destino}:${mensaje.slice(0, 160)}`);
  }
}

// Sin `implements`: cumple la interfaz por su forma (tipado estructural).
export const notificadorConsola = {
  registro: [] as string[],
  async enviar(destino: string, mensaje: string) {
    this.registro.push(`consola:${destino}:${mensaje}`);
  },
};

/** Avisa por todos los canales; un canal que falla no impide los demás. */
export async function avisarATodos(notificadores: readonly Notificador[], destino: string, mensaje: string) {
  const resultados = await Promise.allSettled(notificadores.map((n) => n.enviar(destino, mensaje)));
  return resultados.filter((r) => r.status === 'rejected').length;
}
