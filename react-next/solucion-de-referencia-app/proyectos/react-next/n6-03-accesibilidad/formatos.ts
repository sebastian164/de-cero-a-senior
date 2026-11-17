// ---- react-next/nivel-06-avanzado/03-accesibilidad-e-internacionalizacion/README.md (bloque 3), copiado tal cual entre el contexto agregado ----
export function ejemplos(fecha: Date, cantidad: number) {
new Intl.NumberFormat('es-CO', { style: 'currency', currency: 'COP' }).format(120000);
new Intl.DateTimeFormat('es-CO', { dateStyle: 'long', timeZone: 'America/Bogota' }).format(fecha);
new Intl.PluralRules('es').select(cantidad);

}
