/** Debe fallar al compilar: el contrato común Documento no promete editar(). */
class EditarComoDocumento { void f(Documento d) { d.editar("cambio"); } }
