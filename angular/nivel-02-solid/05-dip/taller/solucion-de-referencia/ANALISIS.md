# Taller n2-05 — DIP

## 1. Dependencias ocultas de `antes.ts › RecordatoriosAntes`

| Dependencia | Tipo | Problema |
|---|---|---|
| `Date.now()` y `new Date()` | Reloj del sistema | La prueba depende de la hora en que se ejecuta |
| `localStorage` | Almacenamiento del navegador | No existe en el servidor (SSR) ni en Node |
| `new Notification(...)` | API del navegador | Pide permiso, no existe en Node, no se puede observar en una prueba |
| `window.mixpanel` | SDK de un tercero | Cambiar de proveedor toca la lógica |
| `HttpClient` + `subscribe()` sin esperar | Backend | Legítimo como detalle, pero escondido: nadie sabe si terminó |

## 2. DIP y prueba sin navegador con tiempo fijo

`recordatorios.ts` define `RELOJ`, `RegistroDeAvisos`, `Avisador`, `Metricas` y `RecordatoriosRemotos`; la decisión (avisar, pendiente, vencida) es la función pura `decidir`. `recordatorios.spec.ts` lleva `// @vitest-environment node`: corre sin `window` ni `localStorage` (la primera prueba lo comprueba) y arma el servicio con `Injector.create`, sin `TestBed`, con el reloj fijo en `2026-11-15T12:00Z`.

## 3. Cambiar de implementación tocando solo la configuración

`proveedoresNavegador` y `proveedoresEnMemoria` difieren en una entrada (`RegistroDeAvisos`). `Recordatorios` no cambia.

## 4. Una implementación distinta para una sola ruta

`rutas.ts`: `/n2-05/taller` usa `RegistroEnLocalStorage`; `/n2-05/taller/demo` usa `RegistroEnMemoria`. La prueba E2E abre las dos: en la primera, tras avisar y recargar, la cita sigue marcada (el segundo clic da "pendiente"); en la segunda, recargar la olvida.

Detalle encontrado al montarlo: `Recordatorios` es `providedIn: 'root'`. Si solo se registran los contratos en la ruta, el servicio raíz se crea en el inyector raíz y no los ve (error `NG0201`, comprobado en `inyectores.spec.ts`). Por eso cada ruta vuelve a proveer `Recordatorios`: así se instancia en el inyector de la ruta y toma sus contratos.

## 5. Qué no abstraje

- `HttpClient` dentro de `RecordatoriosHttp`: ese adaptador **es** el detalle.
- El enrutador y las tuberías de Angular.
- `new Date(fechaIso)` para convertir el texto de la cita: es una conversión pura, no lee el reloj.
