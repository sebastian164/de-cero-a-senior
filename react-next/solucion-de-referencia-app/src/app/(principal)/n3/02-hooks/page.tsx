import { Bloque } from '@app/bloque';
import { TablaPedidos } from '@talleres/react-next/nivel-03-patrones/02-hooks-personalizados/ui/tabla-pedidos';
import { FormularioClave, FormularioClaveConBiblioteca, FormularioContacto } from '@talleres/react-next/nivel-03-patrones/02-hooks-personalizados/ui/formularios';

export const metadata = { title: 'nivel-03/02 · Hooks personalizados' };

export default function Pagina() {
  return (
    <main>
      <h1>nivel-03/02 · Hooks personalizados</h1>
      <Bloque titulo="usePedidos (README) + useTabla + usePaginacion (README)" origen="talleres/…/02-hooks-personalizados/ui/tabla-pedidos.tsx">
        <TablaPedidos />
      </Bloque>
      <Bloque titulo="useFormulario en dos formularios, y la biblioteca para comparar" origen="talleres/…/ui/formularios.tsx">
        <FormularioContacto />
        <FormularioClave />
        <FormularioClaveConBiblioteca />
      </Bloque>
    </main>
  );
}
