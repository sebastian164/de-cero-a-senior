// El objeto de dominio completo que antes viajaba a todas partes.
export interface Usuario {
  id: string; nombres: string; apellidos: string; correo: string; telefono: string;
  perfil: { fotoUrl: string; biografia: string; sitioWeb: string };
  roles: string[]; preferencias: { tema: 'claro' | 'oscuro'; idioma: string; boletin: boolean };
  direcciones: { calle: string; ciudad: string; pais: string }[];
  pedidos: { id: string; total: number }[];
  creadoEn: string; ultimoIngreso: string;
}
export const usuarioCompleto = (): Usuario => ({
  id: 'u1', nombres: 'Ana María', apellidos: 'Gómez Ruiz', correo: 'ana@correo.co', telefono: '3001234567',
  perfil: { fotoUrl: 'favicon.ico', biografia: 'Desarrolladora', sitioWeb: 'https://ejemplo.co' },
  roles: ['cliente'], preferencias: { tema: 'claro', idioma: 'es', boletin: true },
  direcciones: [{ calle: 'Cra 7 # 1-2', ciudad: 'Bogotá', pais: 'CO' }],
  pedidos: [{ id: 'p1', total: 120000 }, { id: 'p2', total: 50000 }],
  creadoEn: '2024-01-01', ultimoIngreso: '2026-11-01',
});
