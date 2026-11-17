# Sitio del curso

El sitio se construye con MkDocs Material a partir de los README del curso, que son la fuente: aquí no se reescribe ninguna lección. `mkdocs.yml` fija la configuración y `hooks.py` resuelve el resto al construir:

- **Menú:** se genera desde las carpetas que existan en ese momento (rutas, niveles y lecciones, con el título de cada README). Un módulo nuevo aparece solo cuando se sube; uno que falta no rompe nada.
- **Soluciones plegadas:** la línea "Cuando lo hayas intentado, compara con la solución de referencia" de cada taller se convierte en un bloque cerrado que muestra el README de la solución, con un enlace al código en GitHub. El `ANALISIS.md` es una página propia, fuera del menú y del buscador.
- **Enlaces:** los relativos entre README funcionan en el sitio; los que apuntan a código o a carpetas sin README van a GitHub.
- **Solo páginas:** no se publica código, `node_modules`, `target`, `.verificacion` ni `.github`.

## Construir en local

```bash
python3 -m venv .venv && . .venv/bin/activate
pip install -r sitio/requirements.txt
mkdocs build --strict -f sitio/mkdocs.yml   # el resultado queda en sitio/site/
mkdocs serve -f sitio/mkdocs.yml            # vista previa en http://127.0.0.1:8000
```

`--strict` convierte cualquier enlace roto o ancla inexistente en un error: es la comprobación que hace el flujo `.github/workflows/sitio.yml` en cada subida a `main`.
