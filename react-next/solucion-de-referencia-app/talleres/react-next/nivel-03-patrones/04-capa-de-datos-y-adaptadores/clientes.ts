// Punto 1: la segunda entidad (clientes) con esquema, adaptador y función de acceso, igual que pedidos en el README.
import { z } from 'zod';
import { aErrorDeDominio } from '@/react-next/n3-04-datos/pedidos/datos/errores';

const ClienteDto = z.object({
  documento: z.string().regex(/^\d+$/),
  nombre_completo: z.string().min(1),
  correo_electronico: z.string().email(),
  activo: z.union([z.literal(0), z.literal(1)]),
});

export interface Cliente {
  readonly documento: string;
  readonly nombre: string;
  readonly correo: string;
  readonly activo: boolean;
}

/** El único lugar que conoce los nombres de campo del servidor. */
export const aCliente = (dto: z.infer<typeof ClienteDto>): Cliente => ({
  documento: dto.documento,
  nombre: dto.nombre_completo,
  correo: dto.correo_electronico,
  activo: dto.activo === 1,
});

export async function listarClientes(base = '', signal?: AbortSignal): Promise<Cliente[]> {
  const respuesta = await fetch(`${base}/api/n3-04/clientes`, { signal });
  if (!respuesta.ok) throw aErrorDeDominio(respuesta.status);
  return z.array(ClienteDto).parse(await respuesta.json()).map(aCliente);
}
