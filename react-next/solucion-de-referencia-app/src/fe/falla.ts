// Función que falla a propósito (taller frontend-senior/02, punto 3): su traza en producción debe poder volver a este archivo con los source maps.
export function provocarFallaNoControlada(): never {
  throw new Error('Fallo provocado desde el taller de observabilidad');
}
