// ---- react-next/nivel-03-patrones/01-composicion-y-componentes-compuestos/README.md (bloque 3), copiado tal cual entre el contexto agregado ----
import { PanelDesplegable } from './panel-desplegable';
import { HistorialDePedidos } from './historial';

// Contexto: componente de servidor que usa la línea del README.
export async function PanelConHistorial({ id }: { id: string }) {
  return (
<PanelDesplegable titulo="Historial">       {/* cliente: maneja abrir y cerrar */}
  <HistorialDePedidos clienteId={id} />     {/* servidor: consulta la base de datos */}
</PanelDesplegable>
  );
}
