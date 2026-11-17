# Taller n2-04 — ISP

## 1. Entradas reducidas y tamaño de los datos de prueba

| Componente | Antes | Después |
|---|---|---|
| Avatar | `usuario: Usuario` (lee `perfil.fotoUrl`, `nombres`, `apellidos`) | `fotoUrl: string`, `nombre: string` |
| Saludo | `usuario: Usuario` (lee `nombres`, `ultimoIngreso`) | `datos: Pick<Usuario, 'nombres' \| 'ultimoIngreso'>` |
| Resumen de compras | `usuario: Usuario` (lee `pedidos[].total`) | `totales: readonly number[]` |

Medido por `taller.spec.ts` (JSON de los datos que cada prueba necesita): **antes 1416 bytes** (tres veces un `Usuario` completo, porque cada prueba debe construirlo entero para satisfacer el tipo), **después 80 bytes**. Además, un cambio en `direcciones` o `preferencias` ya no toca a ninguno de los tres.

## 2. Matriz consumidor × método de `UsuariosServiceAntes`

| Método | Ingreso | Encabezado | Perfil | Admin | Ajustes | Contador |
|---|:-:|:-:|:-:|:-:|:-:|:-:|
| iniciarSesion | ✔ | | | | | |
| cerrarSesion | | ✔ | | | | |
| usuarioActual | | ✔ | ✔ | | | |
| actualizarPerfil, cambiarFoto | | | ✔ | | | |
| listarUsuarios, bloquearUsuario, asignarRol | | | | ✔ | | |
| leerPreferencias, guardarPreferencias | | | | | ✔ | |
| contarPedidosDelUsuarioActual | | | | | | ✔ |
| estadisticasDeCompra | | | | | | (nadie: candidato a borrar) |

Los bloques de la matriz son los contratos: `Sesion`, `EdicionDePerfil`, `AdministracionDeUsuarios`, `Preferencias`, `ConteoDePedidos` (`servicios.ts`).

## 3. Una sola clase y `useExisting`

`UsuariosEnMemoria` implementa los cinco; `proveedoresDeUsuarios` registra cada contrato con `useExisting`. La prueba 3 comprueba que todos resuelven a la misma instancia (comparten estado: iniciar sesión cambia lo que devuelve el conteo).

## 4. Doble de un método

`ContadorPedidosComponent` se prueba con `{ delUsuarioActual: () => of(9) }`. Con el servicio de antes, el doble tenía que satisfacer doce métodos o recurrir a `as any`.

## 5. Dónde decidí no segregar

`Sesion` agrupa `iniciar`, `cerrar` y `actual` aunque la pantalla de ingreso solo use `iniciar`: los tres cambian juntos (el mismo token, la misma cookie) y separarlos en tres clases abstractas de un método obligaría a inyectar dos o tres contratos en el encabezado. También el `Usuario` sigue entero dentro del perfil completo, que usa casi todos sus campos.
