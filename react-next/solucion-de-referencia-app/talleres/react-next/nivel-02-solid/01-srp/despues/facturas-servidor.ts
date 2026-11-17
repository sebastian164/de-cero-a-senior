// Datos del API simulado de facturas (lo sirve /api/n2-01/facturas/[id] y el simulacro de red de las pruebas).
export const FACTURAS: Record<string, { numero: string; cliente_nombre: string; cliente_tipo: 'normal' | 'frecuente' | 'mayorista' | 'exento'; fecha_emision: string; lineas: { desc: string; valor_centavos: number }[] }> = {
  '1': { numero: 'FE-001', cliente_nombre: 'Tienda La 14', cliente_tipo: 'mayorista', fecha_emision: '2026-03-05T15:00:00Z',
         lineas: [{ desc: 'Café x 50', valor_centavos: 90_000_000 }, { desc: 'Azúcar x 20', valor_centavos: 30_000_000 }] },
  '2': { numero: 'FE-002', cliente_nombre: 'Ana Gómez', cliente_tipo: 'frecuente', fecha_emision: '2026-03-06T15:00:00Z',
         lineas: [{ desc: 'Café', valor_centavos: 1_800_000 }] },
};
