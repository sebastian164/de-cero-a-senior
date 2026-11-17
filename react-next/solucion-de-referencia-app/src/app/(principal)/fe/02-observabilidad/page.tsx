import { BotonesDeFalla, ManejadorGlobal, NuevaFicha, PanelQuePuedeRomperse, Seccion } from '@app/fe/observabilidad';

export const metadata = { title: 'frontend-senior/02 · Observabilidad' };

export default function Pagina() {
  return (
    <main>
      <h1>frontend-senior/02 · Calidad, entrega y observabilidad</h1>
      <ManejadorGlobal />
      <h2>Límites de error por sección</h2>
      <Seccion nombre="Inventario"><PanelQuePuedeRomperse nombre="Inventario" /></Seccion>
      <Seccion nombre="Recomendaciones"><PanelQuePuedeRomperse nombre="Recomendaciones" /></Seccion>
      <h2>Errores no controlados (se reportan al colector con la versión y la traza)</h2>
      <BotonesDeFalla />
      <h2>Bandera de función con porcentaje</h2>
      <NuevaFicha />
    </main>
  );
}
