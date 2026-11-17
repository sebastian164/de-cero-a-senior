// ---- react-next/nivel-05-arquitectura/03-estructura-estado-y-arquitectura-limpia/README.md (bloque 3), copiado tal cual entre el contexto agregado ----
'use client';
export function FiltroDeEstado() {
  const ruta = usePathname();
  const parametros = useSearchParams();
  const router = useRouter();

  function cambiar(estado: string) {
    const nuevos = new URLSearchParams(parametros);
    nuevos.set('estado', estado);
    nuevos.delete('pagina');
    router.replace(`${ruta}?${nuevos}`);
  }
  return (
    <label>Estado{' '}
      <select value={parametros.get('estado') ?? ''} onChange={(e) => cambiar(e.target.value)}>
        <option value="">Todos</option><option value="pendiente">Pendiente</option><option value="pagado">Pagado</option><option value="cancelado">Cancelado</option>
      </select>
    </label>
  );
}

import { usePathname, useRouter, useSearchParams } from 'next/navigation';
