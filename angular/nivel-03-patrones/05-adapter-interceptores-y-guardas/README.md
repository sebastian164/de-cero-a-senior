# 05 — Adapter, interceptores y guardas

Tres patrones clásicos que Angular trae incorporados: Adapter para aislar la API, Chain of Responsibility en los interceptores HTTP y en las guardas de ruta.

## Adapter: que la API no dicte tu modelo

**Problema.** Los componentes usan directamente la forma de la respuesta del servidor (`fecha_creacion`, `cliente.nombre_completo`, `estado: 2`). Cada cambio del backend se propaga por toda la interfaz.

**Solución.** Un tipo para lo que llega (DTO), otro para lo que la aplicación usa (modelo), y una función que traduce en el borde.

```ts
// Lo que envía el servidor
interface PedidoDto {
  id: number;
  fecha_creacion: string;
  cliente: { nombre_completo: string };
  estado: 1 | 2 | 3;
  valor_centavos: number;
}

// Lo que usa la aplicación
export interface Pedido {
  readonly id: string;
  readonly creadoEn: Date;
  readonly cliente: string;
  readonly estado: 'pendiente' | 'pagado' | 'cancelado';
  readonly total: number;
}

const ESTADOS = { 1: 'pendiente', 2: 'pagado', 3: 'cancelado' } as const;

export const aPedido = (dto: PedidoDto): Pedido => ({
  id: String(dto.id),
  creadoEn: new Date(dto.fecha_creacion),
  cliente: dto.cliente.nombre_completo,
  estado: ESTADOS[dto.estado],
  total: dto.valor_centavos / 100,
});

@Injectable({ providedIn: 'root' })
export class PedidosApi {
  private readonly http = inject(HttpClient);
  listar(): Observable<Pedido[]> {
    return this.http.get<PedidoDto[]>('/api/pedidos').pipe(map((dtos) => dtos.map(aPedido)));
  }
}
```

Un cambio en el servidor toca una función. El adaptador es además el lugar para validar lo que llega.

## Interceptores: una cadena para cada petición

Cada petición HTTP atraviesa una cadena de funciones; cada una puede modificarla, cortarla o tratar la respuesta. Es el lugar de lo transversal.

```ts
export const interceptorDeAutenticacion: HttpInterceptorFn = (peticion, siguiente) => {
  const token = inject(Sesion).token();
  return siguiente(token ? peticion.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : peticion);
};

export const interceptorDeErrores: HttpInterceptorFn = (peticion, siguiente) => {
  const avisos = inject(Avisos);
  const router = inject(Router);
  return siguiente(peticion).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401) router.navigateByUrl('/ingreso');
      else if (error.status >= 500) avisos.error('El servicio no está disponible. Intenta más tarde.');
      return throwError(() => error);        // se informa y se deja que quien llamó decida
    }),
  );
};

provideHttpClient(withInterceptors([interceptorDeAutenticacion, interceptorDeErrores]));
```

Usos típicos: autenticación, manejo uniforme de errores, indicador de carga, reintentos, caché, registro, cabecera de correlación. **El orden importa.**

## Guardas y *resolvers*: la cadena de la navegación

```ts
export const requiereSesion: CanActivateFn = (_ruta, estado) => {
  const sesion = inject(Sesion);
  return sesion.activa()
    ? true
    : inject(Router).createUrlTree(['/ingreso'], { queryParams: { volverA: estado.url } });
};

export const requiereRol = (rol: Rol): CanActivateFn => () =>
  inject(Sesion).tieneRol(rol) || inject(Router).createUrlTree(['/sin-permiso']);

export const rutas: Routes = [
  { path: 'admin', canActivate: [requiereSesion, requiereRol('admin')], loadChildren: () => import('./admin/rutas') },
];
```

`canDeactivate` protege de perder cambios sin guardar; `canMatch` decide si una ruta aplica.

Un *resolver* es una función que obtiene un dato antes de activar la ruta. Con `withComponentInputBinding()`, el resultado llega al componente como una entrada:

```ts
export const pedidoDeLaRuta: ResolveFn<Pedido> = (ruta) =>
  inject(PedidosApi).obtener(ruta.paramMap.get('id')!);

export const rutas: Routes = [
  { path: 'pedidos/:id', resolve: { pedido: pedidoDeLaRuta }, loadComponent: () => import('./detalle-pedido') },
];

// En el componente: el nombre de la entrada coincide con la clave de "resolve"
readonly pedido = input.required<Pedido>();
```

La navegación espera a que el dato llegue, así que el componente nunca se pinta a medias. El costo es que el usuario no ve nada mientras tanto. Para la mayoría de las pantallas es mejor navegar de inmediato y mostrar el estado de carga con un [recurso](../../nivel-01-fundamentos/04-datos-asincronos-con-resource/); el *resolver* se justifica cuando la pantalla no tiene sentido sin el dato.

Interceptores, guardas y *resolvers* son hoy funciones. Las versiones basadas en clases (`HttpInterceptor`, `CanActivate`, `Resolve`) siguen apareciendo en código antiguo.

**Advertencia de seguridad:** las guardas son experiencia de usuario, no seguridad. Todo el código del frontend es visible y modificable. La autorización real ocurre en el servidor.

## Cuándo no

- Si la API ya devuelve exactamente tu modelo y la controla tu mismo equipo, el adaptador puede esperar.
- No metas lógica de negocio en interceptores ni guardas.
- Un interceptor que trata un caso particular de un solo endpoint está en el lugar equivocado.

## Errores comunes

- DTO usados como modelo en toda la aplicación.
- Interceptor de errores que se traga el error.
- Reintentar automáticamente peticiones no idempotentes.
- Agregar el token a peticiones hacia dominios de terceros.
- Confiar en las guardas para proteger datos.
- Lógica pesada en *resolvers* que bloquea la navegación sin dar señal al usuario.

## Taller

1. Escribe los adaptadores de una API real o pública para dos entidades. Cambia un nombre de campo en el DTO y mide cuántos archivos tocaste.
2. Implementa los interceptores de autenticación, errores y cabecera de correlación. Prueba el orden.
3. Implementa renovación del token ante un 401, de modo que varias peticiones simultáneas provoquen una sola renovación.
4. Implementa guardas de sesión, de rol y de cambios sin guardar.
5. Demuestra, con las herramientas del navegador, que una guarda no protege los datos.

Rama `taller/ng-n3-05-adapter-interceptores`.

Cuando lo hayas intentado, compara con la [solución de referencia](taller/solucion-de-referencia/README.md).

## Preguntas senior

1. ¿Por qué separar DTO de modelo y cuándo no vale la pena?
2. ¿Qué responsabilidades van en un interceptor y cuáles no?
3. ¿Cómo manejas la renovación de token con peticiones concurrentes?
4. ¿Por qué una guarda no es una medida de seguridad?
