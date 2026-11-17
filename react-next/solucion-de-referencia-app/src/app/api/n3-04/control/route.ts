// Control de la API simulada para la demostración (fallos y datos mal formados). Solo existe en esta aplicación de referencia.
import { estadoApi, PEDIDOS_INICIALES } from '@talleres/react-next/nivel-03-patrones/04-capa-de-datos-y-adaptadores/api-simulada';

export async function POST(peticion: Request) {
  const cuerpo = (await peticion.json()) as { fallarCancelacion?: boolean; malformado?: boolean; reiniciar?: boolean };
  if (cuerpo.reiniciar) { estadoApi.pedidos = structuredClone(PEDIDOS_INICIALES); estadoApi.peticiones = 0; }
  if (cuerpo.fallarCancelacion !== undefined) estadoApi.fallarCancelacion = cuerpo.fallarCancelacion;
  if (cuerpo.malformado !== undefined) estadoApi.malformado = cuerpo.malformado;
  return Response.json({ ...estadoApi, pedidos: estadoApi.pedidos.length });
}
