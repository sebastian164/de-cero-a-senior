# Taller n3-06 — Composición

| # | Solución | Prueba |
|---|---|---|
| 1 | `directivas.ts`: `AutofocoDirective` (con `afterNextRender`, no rompe el SSR) y `ClicFueraDirective` (escucha `document:click` desde `host`) | `taller.spec.ts` › 1 |
| 2 | `dialogo.ts`: huecos `[titulo]`, `[cuerpo]`, `[acciones]` y uno por defecto; rol `dialog` con `aria-labelledby`; foco inicial en Cerrar | `taller.spec.ts` › 2 y E2E |
| 3 | `lista-con-estados.ts` (la del ejemplo, igual) usada con `Pedido` (con plantilla de carga propia) y con `Usuario` | `taller.spec.ts` › 3 |
| 4 | `antes.ts`: `BaseComponent` inventado con tres hijos. `despues.ts`: `usarCarga()` con `inject()` + `ResaltableDirective` por `hostDirectives` | `taller.spec.ts` › 4 |
| 5 | El diálogo declara `hostDirectives: [{ directive: ClicFueraDirective, outputs: ['appClicFuera: fuera'] }]` | `taller.spec.ts` › 2 (clic fuera cierra) |

## 4. Qué cambió al quitar el `BaseComponent`

| En la clase base | Problema | Ahora |
|---|---|---|
| `destruido$` + `ngOnDestroy` + `takeUntil` | Cada hijo depende de que nadie sobrescriba `ngOnDestroy` sin llamar a `super` | `takeUntilDestroyed(inject(DestroyRef))` dentro de `usarCarga` |
| `ngOnInit` que llama a `alIniciar()` abstracto | Método obligatorio aunque el hijo no cargue nada (`ResumenAntes` lo deja vacío) | Nada: se llama a `usarCarga` en un inicializador de campo |
| `cargando` como campo mutable | No es una señal: no funciona con `OnPush` sin `markForCheck` | `Carga<T>` con `valor` y `cargando` como señales de solo lectura |
| `avisos` inyectado en todos | `ResumenAntes` recibe un servicio que no usa | Solo lo inyecta `usarCarga` |
| Resaltado con `@HostBinding`/`@HostListener` | Todos lo heredan | `ResaltableDirective` por `hostDirectives`, quien la quiere la declara |

El resumen ya no hereda carga ni avisos: solo pidió el resaltado. Y cada hijo puede combinar varias funciones `usar…`;
con herencia solo había una base.

## Clic que abre y clic fuera

Se comprobó si el mismo clic que abre el diálogo lo cerraba (llega a `document` después del botón). Con Angular 22 sin
zone.js no ocurre: el diálogo se crea en la detección de cambios que sigue al evento, cuando la propagación ya terminó.
La prueba unitaria 2 y la E2E abren el diálogo sin `stopPropagation()` y siguen viéndolo.
