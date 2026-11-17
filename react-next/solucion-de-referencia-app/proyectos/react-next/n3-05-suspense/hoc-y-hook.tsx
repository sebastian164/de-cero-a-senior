'use client';
// ---- react-next/nivel-03-patrones/05-suspense-limites-de-error-y-patrones-heredados/README.md (bloque 3), copiado tal cual entre el contexto agregado ----
import { conSesion, useSesion, Ingreso } from './sesion';

// Antes: HOC
const PaginaProtegida = conSesion(Pagina);

// Hoy: hook
function Pagina() {
  const sesion = useSesion();
  if (!sesion) return <Ingreso />;
  return <p>Hola, {sesion.nombre}</p>;
}

export { PaginaProtegida, Pagina };
