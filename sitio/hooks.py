"""Hooks de MkDocs para el sitio de "De cero a senior".

Los README del curso son la fuente y no se tocan. Todo lo que el sitio necesita
(navegación, soluciones plegadas, enlaces a GitHub) se resuelve aquí al construir:

* ``on_config``: apunta ``docs_dir`` a la raíz del repositorio y genera el menú
  a partir de las carpetas que existan en ese momento.
* ``on_files``: deja solo las páginas del curso (README, ANALISIS de las
  soluciones, METODOLOGIA, ROADMAP y PROGRESO); el código no se publica.
* ``on_page_markdown``: pliega la solución de referencia de cada taller y reescribe
  los enlaces que no son páginas del sitio hacia GitHub.
"""

import logging
import posixpath
import re
from pathlib import Path
from urllib.parse import quote, unquote

log = logging.getLogger("mkdocs.hooks.sitio")

RAIZ = Path(__file__).resolve().parent.parent
REPO = "https://github.com/sebastian164/de-cero-a-senior"

# Carpetas que nunca forman parte del sitio, en cualquier nivel.
EXCLUIDOS = {
    ".git", ".github", ".vscode", ".devcontainer", ".idea", ".angular",
    ".verificacion", "node_modules", "target", "build", "out", "dist", "coverage",
    "sitio", "venv", ".venv", "site", "salida", "__pycache__",
}
# Páginas sueltas de la raíz que sí son parte del curso.
PAGINAS_RAIZ = {"README.md", "METODOLOGIA.md", "ROADMAP.md", "PROGRESO.md"}
# Carpetas que no entran al menú (se llega a ellas desde los enlaces de la lección).
FUERA_DEL_MENU = {"taller", "ejemplos", "ejemplo", "solucion", "inicio"}

# Rutas de la raíz, en el orden del README raíz y de ROADMAP.md.
RAICES = [
    ("java", "Java y Spring Boot"),
    ("angular", "Angular"),
    ("react-next", "React y Next.js"),
    ("typescript", "Fundamentos de TypeScript"),
    ("frontend-senior", "Nivel senior de frontend"),
    ("hexagonal", "Arquitectura hexagonal con Java"),
    ("docker-kubernetes", "Docker y Kubernetes"),
    ("kafka-y-sagas", "Kafka y sagas"),
    ("hikaricp", "HikariCP"),
    ("nosql", "NoSQL: MongoDB y Redis"),
    ("webflux", "Programación reactiva con WebFlux"),
    ("practicas", "Prácticas: TDD, Karate, SDD y ODD"),
]
RAICES_ORDEN = {nombre: i for i, (nombre, _) in enumerate(RAICES)}

LINEA_SOLUCION = re.compile(
    r"\[solución de referencia\]\(([^)\s]*solucion-de-referencia/README\.md)\)"
)
ENLACE_EN_LINEA = re.compile(r"(\[(?:[^\]\\]|\\.)*\])\(([^()\s]+)((?:\s+\"[^\"]*\")?)\)")
DEFINICION = re.compile(r"^(\s{0,3}\[[^\]]+\]:\s*)(\S+)(.*)$")
CODIGO_EN_LINEA = re.compile(r"(`+[^`\n]*?`+)")
FENCE = re.compile(r"^\s*(`{3,}|~{3,})")


# --------------------------------------------------------------------------
# Qué archivos son páginas
# --------------------------------------------------------------------------

def _partes_excluidas(partes):
    return any(p in EXCLUIDOS for p in partes)


def es_pagina(rel):
    """True si el archivo ``rel`` (ruta relativa a la raíz, con /) se publica como página."""
    if not rel.endswith(".md"):
        return False
    partes = rel.split("/")
    if _partes_excluidas(partes[:-1]):
        return False
    nombre = partes[-1]
    if len(partes) == 1:
        return nombre in PAGINAS_RAIZ
    if nombre not in ("README.md", "ANALISIS.md"):
        return False
    en_solucion = [i for i, p in enumerate(partes[:-1]) if p.startswith("solucion-de-referencia")]
    if not en_solucion:
        return nombre == "README.md"
    i = en_solucion[0]
    if partes[i] != "solucion-de-referencia":
        return False  # p. ej. solucion-de-referencia-app: una aplicación, no una lección
    resto = partes[i + 1:-1]
    if i > 0 and partes[i - 1] == "taller":
        # La solución de un taller: su README va plegado en la lección y solo el
        # ANALISIS es página propia.
        return nombre == "ANALISIS.md" and not resto
    # Soluciones de los proyectos (sin carpeta taller): README y ANALISIS, un nivel.
    return len(resto) <= 1


def _existe_pagina(rel):
    return es_pagina(rel) and (RAIZ / rel).is_file()


# --------------------------------------------------------------------------
# Menú generado desde las carpetas
# --------------------------------------------------------------------------

def _limpiar_inline(texto):
    texto = re.sub(r"!?\[([^\]]*)\]\([^)]*\)", r"\1", texto)
    texto = texto.replace("`", "").replace("**", "").replace("*", "")
    return texto.strip()


def _h1(rel, defecto):
    try:
        with open(RAIZ / rel, encoding="utf-8") as f:
            en_fence = False
            for linea in f:
                if FENCE.match(linea):
                    en_fence = not en_fence
                elif not en_fence and linea.startswith("# "):
                    return _limpiar_inline(linea[2:])
    except OSError:
        pass
    return defecto


def _humanizar(nombre):
    return nombre.replace("-", " ").capitalize()


def _orden_mencion(dir_rel):
    """Orden de aparición de los hijos en los enlaces del README de la carpeta."""
    readme = RAIZ / dir_rel / "README.md"
    orden = {}
    try:
        texto = readme.read_text(encoding="utf-8")
    except OSError:
        return orden
    for m in re.finditer(r"\]\(([^)\s#]+)", texto):
        destino = m.group(1)
        if "://" in destino:
            continue
        for parte in posixpath.normpath(destino).split("/"):
            if parte not in (".", "..", "") and parte not in orden:
                orden[parte] = len(orden)
    return orden


def _items(dir_rel):
    """Hijos de una carpeta como pares (nombre de la hoja, entrada de menú).

    Una carpeta sin README es transparente: sus páginas suben un nivel y se
    ordenan por su propio nombre.
    """
    base = RAIZ / dir_rel if dir_rel else RAIZ
    try:
        hijos = sorted(p.name for p in base.iterdir() if p.is_dir())
    except OSError:
        return []
    items = []
    for nombre in hijos:
        if nombre in EXCLUIDOS or nombre in FUERA_DEL_MENU or nombre.startswith(("solucion-de-referencia", ".")):
            continue
        ruta = f"{dir_rel}/{nombre}" if dir_rel else nombre
        readme = f"{ruta}/README.md"
        if _existe_pagina(readme):
            titulo = _h1(readme, _humanizar(nombre))
            hijos_menu = _nodo(ruta)
            items.append((nombre, {titulo: [readme] + hijos_menu} if hijos_menu else {titulo: readme}))
        else:
            items.extend(_items(ruta))
    return items


def _nodo(dir_rel):
    """Entradas de menú de una carpeta: numeradas primero, el resto en el orden del README."""
    orden = _orden_mencion(dir_rel)

    def clave(par):
        nombre = par[0]
        numerada = re.match(r"^(nivel-)?\d+", nombre) is not None
        return (0, 0, nombre) if numerada else (1, orden.get(nombre, 10_000), nombre)

    return [entrada for _, entrada in sorted(_items(dir_rel), key=clave)]


def _entrada(dir_rel):
    """Entradas de menú de una carpeta de la raíz: su sección, o sus hijos sueltos."""
    readme = f"{dir_rel}/README.md"
    hijos = _nodo(dir_rel)
    if _existe_pagina(readme):
        titulo = _h1(readme, _humanizar(dir_rel.split("/")[-1]))
        return [{titulo: [readme] + hijos}] if hijos else [{titulo: readme}]
    return hijos


def construir_nav():
    nav = []
    for rel in ("README.md", "METODOLOGIA.md", "ROADMAP.md", "PROGRESO.md"):
        if _existe_pagina(rel):
            nav.append({_h1(rel, rel): rel} if rel != "README.md" else {"Inicio": rel})
    conocidos = dict(RAICES)
    carpetas = sorted(
        p.name for p in RAIZ.iterdir()
        if p.is_dir() and not p.name.startswith(".") and p.name not in EXCLUIDOS
    )
    carpetas.sort(key=lambda n: (RAICES_ORDEN.get(n, 1000), n))
    for nombre in carpetas:
        entradas = _entrada(nombre)
        if not entradas:
            continue
        if len(entradas) == 1 and isinstance(list(entradas[0].values())[0], list):
            titulo = conocidos.get(nombre, list(entradas[0].keys())[0])
            nav.append({titulo: list(entradas[0].values())[0]})
        else:
            nav.extend(entradas)
    return nav


# --------------------------------------------------------------------------
# Hooks
# --------------------------------------------------------------------------

def on_config(config):
    # docs_dir es la raíz del repositorio. MkDocs no deja declararlo en el YAML
    # (site_dir quedaría dentro), pero acepta cambiarlo aquí, antes de leer archivos.
    config["docs_dir"] = str(RAIZ)
    config["nav"] = construir_nav()
    return config


def on_files(files, config):
    for f in list(files):
        # Los archivos del tema (src_dir distinto) se quedan; del curso solo las páginas.
        if Path(f.src_dir).resolve() == RAIZ and not es_pagina(f.src_uri):
            files.remove(f)
    return files


def _github(ruta, es_carpeta):
    tipo = "tree" if es_carpeta else "blob"
    return f"{REPO}/{tipo}/main/{quote(ruta.strip('/'))}"


def _resolver(destino, base_dir, pagina_dir):
    """Reescribe un destino de enlace escrito relativo a ``base_dir`` para la página en ``pagina_dir``."""
    if not destino or destino.startswith("#") or destino.startswith("/") or re.match(r"^[a-zA-Z][a-zA-Z0-9+.-]*:", destino):
        return destino
    ruta, almohadilla, ancla = destino.partition("#")
    ruta, interrogante, consulta = ruta.partition("?")
    if not ruta:
        return destino
    candidato = posixpath.normpath(posixpath.join(base_dir, unquote(ruta)))
    if candidato.startswith("..") or candidato == ".":
        return destino
    absoluta = RAIZ / candidato
    if _partes_excluidas(candidato.split("/")):
        return destino
    sufijo = (interrogante + consulta if consulta else "") + (almohadilla + ancla if almohadilla else "")
    if absoluta.is_dir():
        readme = f"{candidato}/README.md"
        if _existe_pagina(readme):
            return posixpath.relpath(readme, pagina_dir or ".") + sufijo
        return _github(candidato, True) + (almohadilla + ancla if almohadilla else "")
    if absoluta.is_file():
        if _existe_pagina(candidato):
            return posixpath.relpath(candidato, pagina_dir or ".") + sufijo
        return _github(candidato, False) + (almohadilla + ancla if almohadilla else "")
    return destino  # No existe: que MkDocs lo avise (en modo estricto, falla).


_AVISADOS = set()


def _modulo_ausente(destino, base_dir):
    """True si el enlace apunta a un módulo de la raíz (nosql/, webflux/...) que aún no existe.

    Los módulos nuevos se listan en el README antes de subirse; mientras no estén,
    el enlace se queda en texto en vez de romper la construcción. Un enlace roto
    dentro de un módulo que sí existe sigue fallando.
    """
    if not destino or destino.startswith(("#", "/")) or re.match(r"^[a-zA-Z][a-zA-Z0-9+.-]*:", destino):
        return False
    candidato = posixpath.normpath(posixpath.join(base_dir, unquote(destino.partition("#")[0])))
    primero = candidato.split("/")[0]
    if candidato.startswith("..") or primero not in RAICES_ORDEN or (RAIZ / primero).exists():
        return False
    if primero not in _AVISADOS:
        _AVISADOS.add(primero)
        log.info("El módulo %s/ no existe todavía: sus enlaces se muestran como texto.", primero)
    return True


def _reescribir_linea(linea, base_dir, pagina_dir):
    def en_enlace(m):
        if _modulo_ausente(m.group(2), base_dir):
            return m.group(1)[1:-1]  # el módulo aún no está en el repositorio: solo el texto
        return m.group(1) + "(" + _resolver(m.group(2), base_dir, pagina_dir) + m.group(3) + ")"

    # El código en línea se esconde mientras se reescriben los enlaces (el texto de un
    # enlace puede llevar código), y se devuelve al final.
    codigos = []

    def esconder(m):
        codigos.append(m.group(0))
        return "\x00%d\x00" % (len(codigos) - 1)

    linea = CODIGO_EN_LINEA.sub(esconder, linea)
    linea = ENLACE_EN_LINEA.sub(en_enlace, linea)
    d = DEFINICION.match(linea)
    if d:
        linea = d.group(1) + _resolver(d.group(2), base_dir, pagina_dir) + d.group(3)
    return re.sub("\x00(\\d+)\x00", lambda m: codigos[int(m.group(1))], linea)


def _reescribir(texto, base_dir, pagina_dir):
    salida, en_fence, marca = [], False, ""
    for linea in texto.split("\n"):
        f = FENCE.match(linea)
        if f:
            if not en_fence:
                en_fence, marca = True, f.group(1)[0] * 3
            elif f.group(1).startswith(marca):
                en_fence = False
            salida.append(linea)
        elif en_fence:
            salida.append(linea)
        else:
            salida.append(_reescribir_linea(linea, base_dir, pagina_dir))
    return "\n".join(salida)


ITEM = re.compile(r"^(\s*)([-*+]|\d+[.)])\s+\S")


def _listas_como_en_github(texto):
    """Ajusta dos costumbres de GitHub que Python-Markdown no admite.

    * Una lista pegada al párrafo que la introduce necesita una línea en blanco antes.
    * Una sublista sangrada con 2 o 3 espacios necesita 4 para anidarse.
    """
    salida, en_fence, previa, sangria_previa = [], False, "", 0
    for linea in texto.split("\n"):
        if FENCE.match(linea):
            en_fence = not en_fence
            salida.append(linea)
            previa = ""
            continue
        if en_fence:
            salida.append(linea)
            continue
        m = ITEM.match(linea)
        if m and previa.strip():
            sangria = len(m.group(1))
            if ITEM.match(previa):
                # sangria_previa es la sangría original de la línea anterior.
                if 0 < sangria < 4 and sangria >= sangria_previa and (sangria > sangria_previa or previa.startswith("    ")):
                    linea = "    " + linea.lstrip()
            elif not previa.startswith((" ", "\t", "|", ">", "#")):
                salida.append("")
        salida.append(linea)
        previa = linea
        sangria_previa = len(m.group(1)) if m else 0
    return "\n".join(salida)


def _demotar(texto):
    """Baja los títulos del README de la solución para que no compitan con los de la lección."""
    salida, en_fence = [], False
    for linea in texto.split("\n"):
        if FENCE.match(linea):
            en_fence = not en_fence
        elif not en_fence:
            m = re.match(r"^(#{1,6})(\s.*)$", linea)
            if m:
                linea = "#" * min(6, len(m.group(1)) + 3) + m.group(2)
        salida.append(linea)
    return "\n".join(salida)


def _bloque_solucion(solucion_dir, pagina_dir):
    """Texto Markdown del bloque plegable con el README de la solución."""
    cuerpo = (RAIZ / solucion_dir / "README.md").read_text(encoding="utf-8").strip("\n")
    lineas = cuerpo.split("\n")
    if lineas and lineas[0].startswith("# "):
        lineas = lineas[1:]
    cuerpo = "\n".join(lineas).lstrip("\n")
    # El aviso de intentar antes ya va arriba del bloque.
    cuerpo = re.sub(r"^> \*\*Intenta el taller antes[^\n]*\n\n?", "", cuerpo)
    cuerpo = _reescribir(_demotar(cuerpo), solucion_dir, pagina_dir)

    analisis = f"{solucion_dir}/ANALISIS.md"
    enlaces = [f"[Ver el código en GitHub]({_github(solucion_dir, True)})"]
    if _existe_pagina(analisis):
        enlaces.append(f"[Leer el análisis completo]({posixpath.relpath(analisis, pagina_dir or '.')})")
    cabecera = (
        "**Intenta el taller antes de abrir esto.** Ver la respuesta primero convierte "
        "el taller en lectura. Ábrela para comparar con tu entrega, no para empezar.\n\n"
        + " · ".join(enlaces)
        + "\n\n---\n\n"
    )
    contenido = cabecera + cuerpo
    sangrado = "\n".join(("    " + l) if l.strip() else "" for l in contenido.split("\n"))
    return '??? tip "Ver solución de referencia"\n\n' + sangrado + "\n"


def _plegar_solucion(texto, pagina_src):
    pagina_dir = posixpath.dirname(pagina_src)
    salida, en_fence, plegada = [], False, False
    for linea in texto.split("\n"):
        if FENCE.match(linea):
            en_fence = not en_fence
        m = None if en_fence or plegada else LINEA_SOLUCION.search(linea)
        if m and linea.startswith("Cuando lo hayas intentado"):
            readme_sol = posixpath.normpath(posixpath.join(pagina_dir, m.group(1)))
            if (RAIZ / readme_sol).is_file():
                solucion_dir = posixpath.dirname(readme_sol)
                salida.append("Cuando lo hayas intentado, compara con la solución de referencia.")
                salida.append("")
                salida.append(_bloque_solucion(solucion_dir, pagina_dir))
                plegada = True
                continue
        salida.append(linea)
    solucion_dir = posixpath.join(pagina_dir, "taller/solucion-de-referencia")
    if not plegada and posixpath.basename(pagina_src) == "README.md" and (RAIZ / solucion_dir / "README.md").is_file():
        # La lección tiene solución publicada pero su README aún no la enlaza con la línea
        # estándar: se añade al final para que el sitio no dependa de ese detalle.
        log.info("%s no enlaza su solución de referencia con la línea estándar; se añade al final.", pagina_src)
        salida += [
            "",
            "## Solución de referencia",
            "",
            "Cuando lo hayas intentado, compara con la solución de referencia.",
            "",
            _bloque_solucion(solucion_dir, pagina_dir),
        ]
    return "\n".join(salida)


def on_page_markdown(markdown, page, config, files):
    src = page.file.src_uri
    if "solucion-de-referencia" in src.split("/"):
        # Las páginas de la solución no salen en el buscador: serían un atajo al spoiler.
        page.meta["search"] = {"exclude": True}
    markdown = _listas_como_en_github(_plegar_solucion(markdown, src))
    return _reescribir(markdown, posixpath.dirname(src), posixpath.dirname(src))


def on_page_content(html, page, config, files):
    # El README de la solución, plegado dentro de la lección, tampoco se indexa.
    return html.replace(
        '<details class="tip">\n<summary>Ver solución de referencia</summary>',
        '<details class="tip" data-search-exclude>\n<summary>Ver solución de referencia</summary>',
    )
