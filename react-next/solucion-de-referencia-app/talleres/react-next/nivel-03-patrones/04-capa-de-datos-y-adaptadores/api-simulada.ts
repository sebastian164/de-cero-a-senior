// "API pública" simulada con el formato de otro equipo (DTO): la sirven src/app/api/n3-04/** y el simulacro de las pruebas.
export interface PedidoDtoCrudo { id: number; fecha_creacion: string; cliente: { nombre_completo: string }; estado: 1 | 2 | 3; valor_centavos: number }
export interface ClienteDtoCrudo { documento: string; nombre_completo: string; correo_electronico: string; activo: 0 | 1 }

export const PEDIDOS_INICIALES: PedidoDtoCrudo[] = [
  { id: 101, fecha_creacion: '2026-04-01T10:00:00Z', cliente: { nombre_completo: 'Ana Gómez' }, estado: 1, valor_centavos: 12_000_000 },
  { id: 102, fecha_creacion: '2026-04-02T11:00:00Z', cliente: { nombre_completo: 'Luis Pérez' }, estado: 2, valor_centavos: 4_500_000 },
  { id: 103, fecha_creacion: '2026-04-03T12:00:00Z', cliente: { nombre_completo: 'Eva Ríos' }, estado: 1, valor_centavos: 9_900_000 },
  { id: 104, fecha_creacion: '2026-04-04T09:30:00Z', cliente: { nombre_completo: 'Juan Díaz' }, estado: 3, valor_centavos: 2_000_000 },
];

export const CLIENTES_INICIALES: ClienteDtoCrudo[] = [
  { documento: '1012', nombre_completo: 'Ana Gómez', correo_electronico: 'ana@correo.example', activo: 1 },
  { documento: '2034', nombre_completo: 'Luis Pérez', correo_electronico: 'luis@correo.example', activo: 0 },
];

export const estadoApi = { pedidos: structuredClone(PEDIDOS_INICIALES), fallarCancelacion: false, malformado: false, peticiones: 0 };

export function listarDto(filtro: URLSearchParams): unknown[] {
  estadoApi.peticiones++;
  const estado = filtro.get('estado');
  const lista = estadoApi.pedidos.filter((p) => !estado || String(p.estado) === estado);
  // Modo "malformado": el servidor cambió un campo sin avisar (valor_centavos llega como texto).
  return estadoApi.malformado ? lista.map((p) => ({ ...p, valor_centavos: `${p.valor_centavos}` })) : lista;
}

export function cancelarDto(id: string): 'ok' | 'no-existe' | 'falla' {
  if (estadoApi.fallarCancelacion) return 'falla';
  const i = estadoApi.pedidos.findIndex((p) => String(p.id) === id);
  if (i === -1) return 'no-existe';
  estadoApi.pedidos.splice(i, 1);
  return 'ok';
}
