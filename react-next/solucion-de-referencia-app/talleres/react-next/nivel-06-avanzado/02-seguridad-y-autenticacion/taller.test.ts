// Solución del taller de nivel-06/02 (la parte que se prueba sin navegador): firma, límite de tasa, retorno y capa de acceso.
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { firmar, nuevaSesion, verificar } from './firma';
import { crearLimitador } from './limite';
import { retornoSeguro } from './retorno';
import type { Usuario } from './usuarios';

const usuarioActual = vi.hoisted(() => vi.fn<() => Promise<Usuario | null>>());
vi.mock('@talleres/react-next/nivel-06-avanzado/02-seguridad-y-autenticacion/sesion', () => ({ obtenerUsuarioActual: usuarioActual }));
vi.mock('next/cache', () => ({ updateTag: vi.fn(), cacheLife: vi.fn(), cacheTag: vi.fn(), revalidateTag: vi.fn() }));
const SECRETO = 's'.repeat(32);

describe('firma de la sesión', () => {
  const datos = nuevaSesion('u-ana', 30, 1_000);
  it('verifica lo que firmó', () => { expect(verificar(firmar(datos, SECRETO), SECRETO, 2_000)).toEqual(datos); });
  it('rechaza una firma de otro secreto', () => { expect(verificar(firmar(datos, SECRETO), 'x'.repeat(32), 2_000)).toBeNull(); });
  it('rechaza el cuerpo alterado (cambiar de usuario)', () => {
    const [cuerpo, firma] = firmar(datos, SECRETO).split('.');
    const otro = Buffer.from(JSON.stringify({ ...datos, uid: 'u-admin' })).toString('base64url');
    expect(verificar(`${otro}.${firma}`, SECRETO, 2_000)).toBeNull();
    expect(cuerpo).toBeTruthy();
  });
  it('rechaza una sesión vencida, vacía o malformada', () => {
    expect(verificar(firmar(datos, SECRETO), SECRETO, datos.exp + 1)).toBeNull();
    expect(verificar(undefined, SECRETO)).toBeNull();
    expect(verificar('sin-punto', SECRETO)).toBeNull();
    expect(verificar('a.b', SECRETO)).toBeNull();
  });
});

describe('límite de tasa del ingreso', () => {
  it('permite 5 intentos por ventana y bloquea el sexto con el tiempo de espera', () => {
    const l = crearLimitador(5, 60_000);
    for (let i = 0; i < 5; i++) expect(l.intentar('ip:1', 1_000 + i).permitido).toBe(true);
    const sexto = l.intentar('ip:1', 2_000);
    expect(sexto.permitido).toBe(false);
    expect(sexto.reintentarEnMs).toBe(60_000 - 1_000);
  });
  it('claves distintas no se afectan, y la ventana se libera con el tiempo', () => {
    const l = crearLimitador(1, 1_000);
    expect(l.intentar('a', 0).permitido).toBe(true);
    expect(l.intentar('a', 500).permitido).toBe(false);
    expect(l.intentar('b', 500).permitido).toBe(true);
    expect(l.intentar('a', 1_001).permitido).toBe(true);
  });
});

describe('redirecciones abiertas', () => {
  it.each(['/n6/02-seguridad/privado', '/pedidos?estado=1'])('acepta la ruta propia %s', (v) => expect(retornoSeguro(v)).toBe(v));
  it.each(['//evil.example', 'https://evil.example', '/\\evil.example', 'javascript:alert(1)', '', null, undefined, 42, '/\t/evil.example'])('rechaza %j', (v) => {
    expect(retornoSeguro(v)).toBe('/n6/02-seguridad/privado');
  });
});

describe('capa de acceso a datos: autorización y proyección', () => {
  const ana: Usuario = { id: 'u-ana', nombre: 'Ana', correo: 'ana@correo.example', esAdministrador: false };
  const admin: Usuario = { ...ana, id: 'u-admin', esAdministrador: true };
  const ID_ANA = '0b7c6a2e-1d3f-4e5a-9b8c-7d6e5f4a3b21';
  const ID_LUIS = '2d9e8c4a-3f5b-4a7c-9d0e-9f8a7b6c5d43';
  beforeEach(async () => { (await import('@/react-next/n6-02-seguridad/bd')).bd.reiniciar(); });

  it('sin sesión no devuelve nada', async () => {
    usuarioActual.mockResolvedValue(null);
    const { obtenerPedido } = await import('@/react-next/n6-02-seguridad/acceso');
    expect(await obtenerPedido(ID_ANA)).toBeNull();
  });
  it('un usuario lee su pedido, proyectado: sin campos internos', async () => {
    usuarioActual.mockResolvedValue(ana);
    const { obtenerPedido } = await import('@/react-next/n6-02-seguridad/acceso');
    const p = await obtenerPedido(ID_ANA);
    expect(p).toEqual({ id: ID_ANA, estado: 'pagado', total: 32000, lineas: [{ producto: 'Café', cantidad: 1 }] });
    expect(p).not.toHaveProperty('costoInterno');
    expect(p).not.toHaveProperty('notaDeRiesgo');
    expect(p).not.toHaveProperty('clienteId');
  });
  it('un usuario NO puede leer el pedido de otro: obtiene lo mismo que si no existiera', async () => {
    usuarioActual.mockResolvedValue(ana);
    const { obtenerPedido } = await import('@/react-next/n6-02-seguridad/acceso');
    expect(await obtenerPedido(ID_LUIS)).toBeNull();
    expect(await obtenerPedido('00000000-0000-4000-8000-000000000000')).toBeNull();
  });
  it('el administrador sí lo lee', async () => {
    usuarioActual.mockResolvedValue(admin);
    const { obtenerPedido } = await import('@/react-next/n6-02-seguridad/acceso');
    expect((await obtenerPedido(ID_LUIS))?.id).toBe(ID_LUIS);
  });
  it('la lista de un usuario solo trae lo suyo', async () => {
    usuarioActual.mockResolvedValue(ana);
    const { listarPedidosVisibles } = await import('@/react-next/n6-02-seguridad/acceso-lista');
    expect((await listarPedidosVisibles()).map((p) => p.id)).not.toContain(ID_LUIS);
  });
});

describe('acción "Después": un usuario NO puede modificar el recurso de otro', () => {
  const ana: Usuario = { id: 'u-ana', nombre: 'Ana', correo: 'ana@correo.example', esAdministrador: false };
  const ID_LUIS = '2d9e8c4a-3f5b-4a7c-9d0e-9f8a7b6c5d43';
  const ID_ANA = '0b7c6a2e-1d3f-4e5a-9b8c-7d6e5f4a3b21';
  beforeEach(async () => { (await import('@/react-next/n6-02-seguridad/bd')).bd.reiniciar(); });

  it('sin sesión: no autenticado', async () => {
    usuarioActual.mockResolvedValue(null);
    const { eliminarPedido } = await import('@/react-next/n6-02-seguridad/eliminar-despues');
    expect(await eliminarPedido(ID_ANA)).toEqual({ ok: false, error: 'No autenticado' });
  });
  it.each([[undefined], [42], ['no-es-uuid'], [{ id: ID_ANA }]])('entrada inválida %j', async (entrada) => {
    usuarioActual.mockResolvedValue(ana);
    const { eliminarPedido } = await import('@/react-next/n6-02-seguridad/eliminar-despues');
    expect(await eliminarPedido(entrada)).toEqual({ ok: false, error: 'Solicitud inválida' });
  });
  it('pedido de otro: se rechaza y sigue existiendo', async () => {
    usuarioActual.mockResolvedValue(ana);
    const { eliminarPedido } = await import('@/react-next/n6-02-seguridad/eliminar-despues');
    const { bd } = await import('@/react-next/n6-02-seguridad/bd');
    expect(await eliminarPedido(ID_LUIS)).toEqual({ ok: false, error: 'Pedido no encontrado' });
    expect(await bd.pedido.findUnique({ where: { id: ID_LUIS } })).not.toBeNull();
  });
  it('pedido propio: se elimina', async () => {
    usuarioActual.mockResolvedValue(ana);
    const { eliminarPedido } = await import('@/react-next/n6-02-seguridad/eliminar-despues');
    const { bd } = await import('@/react-next/n6-02-seguridad/bd');
    expect(await eliminarPedido(ID_ANA)).toEqual({ ok: true });
    expect(await bd.pedido.findUnique({ where: { id: ID_ANA } })).toBeNull();
  });
  it('la versión "Antes" del README borra el de cualquiera (por eso se corrige)', async () => {
    const { eliminarPedido } = await import('@/react-next/n6-02-seguridad/eliminar-antes');
    const { bd } = await import('@/react-next/n6-02-seguridad/bd');
    await eliminarPedido(ID_LUIS);
    expect(await bd.pedido.findUnique({ where: { id: ID_LUIS } })).toBeNull();
  });
});
