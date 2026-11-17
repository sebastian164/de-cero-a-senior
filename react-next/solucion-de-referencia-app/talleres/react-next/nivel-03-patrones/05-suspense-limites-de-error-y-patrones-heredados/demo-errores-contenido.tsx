'use client';
// Contenido de la demostración de los puntos 3 y 4. Se carga solo en el cliente (ver demo-errores.tsx):
// sus secciones fallan a propósito, y en el servidor un límite de error de clase no atrapa errores de renderizado.
import { SeccionPedidos } from '@/react-next/n3-05-suspense/limite-de-error';
import { BotonQueFallaConManejo, Inventario, Recomendaciones, Seccion, repararRecomendaciones } from './cliente';

export default function DemoErroresContenido() {
  return (
    <div>
      <h3>Bloque del README: ErrorBoundary + Suspense</h3>
      <SeccionPedidos />
      <h3>Límites por sección</h3>
      <Seccion nombre="recomendaciones" onReintentar={repararRecomendaciones}><Recomendaciones /></Seccion>
      <Seccion nombre="inventario"><Inventario /></Seccion>
      <h3>Error en un manejador, manejado donde ocurre</h3>
      <BotonQueFallaConManejo accion={() => Promise.reject(new Error('El servidor no respondió'))} />
    </div>
  );
}
