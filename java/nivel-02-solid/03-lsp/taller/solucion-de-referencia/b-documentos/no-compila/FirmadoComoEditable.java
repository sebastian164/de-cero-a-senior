/** Debe fallar al compilar: un firmado no es un editable. */
class FirmadoComoEditable { DocumentoEditable f(DocumentoFirmado d) { return d; } }
