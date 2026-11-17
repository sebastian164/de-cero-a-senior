// Reglas de cupones (Mercadeo). Puras y con la fecha como parámetro: se prueban sin reloj real.
export type ResultadoCupon = { readonly valido: true; readonly descuento: number } | { readonly valido: false; readonly motivo: string };

export const aplicarCupon = (codigo: string, subtotal: number, hoy: Date): ResultadoCupon => {
  switch (codigo.trim().toUpperCase()) {
    case 'BIENVENIDA':
      return subtotal > 100_000 ? { valido: true, descuento: 20_000 } : { valido: false, motivo: 'El cupón exige una compra mayor a $ 100.000' };
    case 'NAVIDAD':
      return hoy.getMonth() === 11 ? { valido: true, descuento: Math.round(subtotal * 0.15) } : { valido: false, motivo: 'El cupón NAVIDAD solo vale en diciembre' };
    default:
      return { valido: false, motivo: 'Cupón inválido' };
  }
};
