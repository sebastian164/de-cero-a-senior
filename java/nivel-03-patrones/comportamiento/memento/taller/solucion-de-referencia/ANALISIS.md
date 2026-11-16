# Memento — análisis

1. **Memento**: `Configurador.guardar()` devuelve un `Memento` opaco (interfaz sin métodos; la implementación es un `record` privado). Los accesorios se copian con `List.copyOf`: si el memento guardara la misma lista, deshacer después de agregar un accesorio no lo quitaría (`laListaDeAccesoriosSeCopia`). Al restaurar se copia de vuelta al contenido de la lista propia.

2. **Límite de 20**: `Historial` descarta la instantánea más vieja (`removeLast`) al pasar de 20. Tras 25 cambios solo se pueden deshacer 20 y la memoria queda en 5 GB (el estado tras el quinto cambio).

3. **Configuración inmutable** (`Configuracion` + `HistorialInmutable`): no hay memento ni `guardar/restaurar`; cada versión es un valor y el historial guarda los valores. Desaparecen la copia defensiva al guardar y al restaurar, el *cast* del memento y el riesgo de compartir la lista (es `List.copyOf`, no modificable). Coste: cada cambio crea un objeto nuevo y la clase necesita métodos `con...`. Es más simple y más segura; Memento se justifica cuando el originador es mutable por naturaleza (un editor, una entidad con identidad) o no se puede cambiar.

4. **Command o Memento para deshacer**:
   - **Memento** si el estado es pequeño y la operación inversa es difícil o no existe (aplicar un filtro a una imagen pequeña, reordenar una lista).
   - **Command** si el estado es grande y la operación inversa es barata y exacta (en un documento de 50 MB, guardar "inserté 3 caracteres en la posición 10" en lugar de 50 MB por paso; un depósito se deshace con un retiro).
   Criterio: tamaño del estado × número de pasos frente a costo y exactitud de la inversa. Si ambos son caros, se combinan: comandos con instantáneas cada N pasos.
