# Taller 05-08 — Domain-Driven Design: clínica veterinaria con hospitalización (solución)

Negocio elegido: una clínica veterinaria con consulta, hospitalización, farmacia, peluquería y facturación. El agregado está en `src/main/java/veterinaria/hospitalizacion` y `mvn test` ejecuta 7 pruebas (6 de invariantes y eventos, 1 de ArchUnit que prohíbe setters).

## 1. Glosario (15 términos)

| Término | Significado | ¿Varía por área? |
|---|---|---|
| **Paciente** | El animal atendido | **Sí**: en Facturación el "cliente" es el tutor y el paciente es solo una línea del detalle |
| **Tutor** | Persona responsable del paciente | |
| **Consulta** | Evaluación con motivo, examen y diagnóstico | **Sí**: para Agenda es un bloque de 20 minutos; para Clínica, un acto médico |
| **Control** | Consulta de seguimiento de un diagnóstico previo, con tarifa reducida | |
| **Hospitalización** | Estancia del paciente en la clínica bajo tratamiento | |
| **Prescripción** | Orden de un medicamento, dosis y frecuencia | **Sí**: para Farmacia es una solicitud de despacho |
| **Toma** | Cada administración programada de una prescripción | |
| **Dosis** | Cantidad de medicamento por toma (mg) | |
| **Dosis máxima** | Límite en mg por kilo de peso | |
| **Alta** | Fin de la hospitalización autorizado por el médico | **Sí**: para Facturación dispara el cierre de la cuenta |
| **Historia clínica** | Registro cronológico de actos médicos del paciente | |
| **Producto** | Algo que se vende o se consume | **Sí**: Farmacia (lote, vencimiento, existencias), Facturación (tarifa, impuesto), Peluquería (insumo) |
| **Plan de vacunación** | Calendario de vacunas según especie y edad | |
| **Triage** | Clasificación de urgencia al llegar | |
| **Cuenta de hospitalización** | Acumulado de cargos de la estancia | |

## 2. Contextos delimitados y mapa

```
               ┌──────────────┐  evento AltaOtorgada  ┌──────────────┐
               │ Hospitaliza- │ ────────────────────► │ Facturación  │──ACL──► Software contable / DIAN
               │ ción (núcleo)│                       └──────────────┘
               └──────┬───────┘                              ▲
       copia Medicam. │ (cliente-proveedor)                  │ cargos de consulta
               ┌──────▼───────┐                       ┌──────┴───────┐
               │  Farmacia    │                       │   Clínica    │
               └──────────────┘                       │ (consultas,  │
                                                      │  historia)   │
               ┌──────────────┐  PacienteId           └──────────────┘
               │ Agenda y     │ ◄──────────────────────────┘
               │ pacientes    │
               └──────────────┘
```

Capa anticorrupción: entre Facturación y el software contable/facturación electrónica externo, cuyo modelo (terceros, NIT, códigos de impuesto) no debe filtrarse a la clínica. Una segunda, más pequeña, frente al catálogo del proveedor de medicamentos que usa Farmacia.

## 3. Subdominios

| Subdominio | Tipo | Inversión |
|---|---|---|
| Hospitalización (tratamiento y seguridad del paciente) | Núcleo | Modelo rico, el mejor diseño, pruebas exhaustivas |
| Clínica (consultas e historia) | Núcleo/soporte | Diseño cuidado pero más simple |
| Farmacia (existencias, lotes) | Soporte | CRUD con reglas de vencimiento |
| Agenda y pacientes | Soporte | CRUD |
| Facturación electrónica | Genérico | Comprar o integrar un proveedor |
| Autenticación, notificaciones | Genérico | Usar lo existente |

## 4. Agregado `Hospitalizacion` (Java puro, sin setters)

- Raíz: `Hospitalizacion`. Entidad interna: `Prescripcion` (sus mutadores son de paquete, ArchUnit lo verifica).
- Objetos de valor: `Peso`, `Dosis` (en microgramos), `Medicamento`, `PacienteId`, `HospitalizacionId`.
- Eventos: `PacienteHospitalizado`, `DosisAdministrada`, `AltaOtorgada` (tipo `sealed`).
- Invariantes con prueba: dosis máxima por peso (límite exacto permitido), toma adelantada más de 30 min rechazada, prescripción suspendida, nada después del alta, alta bloqueada con tomas atrasadas más de 2 h.

## 5. Qué quedó fuera y cómo se mantiene consistente

- **El paciente** (otro agregado de Agenda y pacientes): se referencia por `PacienteId`.
- **El catálogo de medicamentos** (Farmacia): se copia el dato necesario (`maximoMgPorKg`) al prescribir. Si Farmacia cambia el máximo, las prescripciones vigentes no cambian, que es lo clínicamente correcto.
- **Las existencias**: la administración no descuenta inventario en la misma transacción. `DosisAdministrada` se publica y Farmacia descuenta de forma eventual; un faltante se resuelve en Farmacia, no bloquea al enfermero.
- **La cuenta**: Facturación reacciona a `DosisAdministrada` (cargo) y a `AltaOtorgada` (cierre).

## 6. Autocrítica: qué no necesita DDD

Agenda (turnos y recordatorios), peluquería, inventario de insumos y la facturación electrónica. Son CRUD o genéricos: un modelo táctico rico ahí solo agrega mapeo y ceremonia.
