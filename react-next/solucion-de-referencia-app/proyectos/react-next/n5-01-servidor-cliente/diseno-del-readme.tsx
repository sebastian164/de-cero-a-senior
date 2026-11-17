// ---- react-next/nivel-05-arquitectura/01-componentes-de-servidor-y-de-cliente/README.md (bloque 4), copiado tal cual entre el contexto agregado ----
import { Proveedores } from '@app/proveedores';

// app/layout.tsx — servidor
export default function Diseno({ children }: { children: React.ReactNode }) {
  return <html lang="es"><body><Proveedores>{children}</Proveedores></body></html>;
}
