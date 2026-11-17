// ---- react-next/nivel-06-avanzado/01-rendimiento/README.md (bloque 2, desde 'const [pendiente'), copiado tal cual entre el contexto agregado ----
import { useState, useTransition } from 'react';

export function Pestanas() {
  const [pestana, setPestana] = useState('resumen');
const [pendiente, iniciar] = useTransition();
// ---- react-next/nivel-06-avanzado/01-rendimiento/README.md (bloque 2, desde 'iniciar('), copiado tal cual entre el contexto agregado ----
  function alElegirReportes() {
iniciar(() => setPestana('reportes'));                  // actualización no urgente: no bloquea la escritura
  }
  return <button type="button" aria-busy={pendiente} onClick={alElegirReportes}>Pestaña: {pestana}</button>;
}
