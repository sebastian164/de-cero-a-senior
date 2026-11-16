# Bridge — análisis

1. **Una clase por combinación** (`combinaciones/Combinaciones.java`): 3 reportes × 3 formatos = **9 clases** (más una base). Cada formato se repite en 3 clases y cada reporte en otras 3.

2. **Bridge**: `Reporte` (abstracción: ventas, inventario, cartera) tiene un `Formato` (implementación: PDF, Excel, pantalla). 3 + 3 = 6 clases concretas.

3. **Un tipo y un formato más**:
   | | Con combinaciones | Con Bridge |
   |---|---|---|
   | Nuevo reporte (nómina) | 3 clases nuevas (una por formato) | 1 clase nueva (`ReporteNomina`) |
   | Nuevo formato (CSV) | 4 clases nuevas (una por reporte, ya con nómina) | 1 clase nueva (`Csv`) |
   | Total | 9 → 16 clases | 6 → 8 clases, **0 modificadas** |

4. **Colores en cartera**: el primer impulso es `if (formato instanceof Pdf)` dentro de `ReporteCartera`, y eso revela que las dimensiones dejan de ser independientes. El diseño lo aguanta **solo** si se amplía la interfaz de implementación con una operación expresada en términos de intención (`resaltar(texto, Nivel.ALERTA)`), con un `default` que degrada a texto (`!! ...`) para los formatos sin color. Así:
   - el reporte pide "alerta", no "rojo", y no conoce formatos;
   - PDF y Excel sobrescriben con color real; pantalla y CSV heredan la degradación.

   Coste: hubo que tocar la interfaz `Formato` (1 método con `default`) y las 2 implementaciones con color. Lección: la independencia de las dimensiones es una hipótesis; cuando un caso necesita una capacidad que solo tienen algunas implementaciones, hay que subir esa capacidad al contrato de forma abstracta o aceptar que el puente tiene fugas. La prueba `unFormatoNuevoSinColorDegradaSinTocarLaAbstraccion` lo verifica por comportamiento: un `Formato` definido en la propia prueba, que `ReporteCartera` no conoce, produce la degradación `!!` sin color, y otro que sí sobrescribe `resaltar` aplica su marca, sin tocar el reporte.
