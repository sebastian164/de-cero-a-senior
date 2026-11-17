// Contexto: componente de servidor que "consulta la base de datos". Su código no viaja al navegador.
import 'server-only';

const MARCA_SERVIDOR = 'historial-solo-servidor-7c1f'; // si aparece en un .js del cliente, la frontera se rompió

export async function HistorialDePedidos({ clienteId }: { clienteId: string }) {
  const pedidos = [{ id: 'H-1', fecha: '2026-01-10' }, { id: 'H-2', fecha: '2026-02-03' }];
  return (
    <ul data-origen={MARCA_SERVIDOR.slice(0, 9)}>
      {pedidos.map((p) => <li key={p.id}>{p.id} · {p.fecha} · cliente {clienteId}</li>)}
    </ul>
  );
}
