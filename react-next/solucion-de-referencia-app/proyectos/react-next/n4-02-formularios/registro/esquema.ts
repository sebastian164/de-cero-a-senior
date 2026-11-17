// ---- react-next/nivel-04-clean-code-y-testing/02-formularios-y-validacion/README.md (bloque 1), copiado tal cual entre el contexto agregado ----
import { z } from 'zod';

// registro/esquema.ts — compartido por cliente y servidor
export const EsquemaRegistro = z.object({
  correo: z.string().email('Escribe un correo válido'),
  documento: z.string().regex(/^\d{6,10}$/, 'Entre 6 y 10 dígitos'),
  clave: z.string().min(12, 'Mínimo 12 caracteres'),
  confirmacion: z.string(),
}).refine((d) => d.clave === d.confirmacion, {
  path: ['confirmacion'],
  message: 'Las claves no coinciden',
});

export type DatosRegistro = z.infer<typeof EsquemaRegistro>;
