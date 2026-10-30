# Enseñanza de código: recorrido para explicarlo en clase

Cómo usarlo: cada bloque es **un archivo, qué mostrar y qué decir** (30–60 s por bloque). Abre el archivo en el editor, resalta las líneas y lee la «frase clave».
Las rutas son desde `services/`. Los números de línea son del estado actual del repositorio.

---

## Parte 1 · Hermes (planificación, alumnos, notificaciones)

### 1.1 La simulación es la misma transacción, revertida
`planificacion-ms/…/service/impl/HorarioGeneradorServiceImpl.java:144-145` (la clase es `@Transactional`, línea 30)
```java
if (simular) {
    TransactionAspectSupport.currentTransactionStatus().setRollbackOnly(); // nada de esto se guarda
}
```
> **Frase clave:** «No escribí un segundo algoritmo para simular: ejecuto exactamente el mismo código y marco la transacción para revertir. Lo que veo es lo que se guardaría.»

### 1.2 Invariantes: el choque se rechaza antes de escribir
`…/service/impl/HorarioServiceImpl.java:94-100` (validación) y `HorarioRepository` (`existsChoqueDocente/Aula/Seccion`)
```java
if (repository.existsChoqueDocente(a.getDocente().getId(), h.getDiaSemana(), b.getId(), h.getId())) { … }
if (h.getAula() != null && repository.existsChoqueAula(…)) { … }
if (repository.existsChoqueSeccion(seccionId, h.getDiaSemana(), b.getId(), h.getId())) { … }
```
> **Frase clave:** «Tres preguntas a la base: ¿el docente, el aula o la sección ya están ocupados en ese día y bloque? Si sí, 409 y no se escribe nada.»

### 1.3 Publicar: versión + evento en una sola transacción
`…/service/impl/PublicacionHorarioServiceImpl.java:38-66` (clase `@Transactional`, línea 27)
```java
int version = repository.findFirstBySeccionIdOrderByVersionDesc(seccionId).map(p -> p.getVersion() + 1).orElse(1);
…
repository.saveAndFlush(publicacion);
…
eventos.publishEvent(HorarioPublicadoEvent.of(seccionId, …, version, docenteUsuarioIds, publicacion.getPublicadoEn()));
```
> **Frase clave:** «No llamo a Kafka aquí. Publico un evento *de dominio*; quien lo convierte en mensaje lo hace dentro de la misma transacción.»

### 1.4 Outbox: el evento se guarda antes del commit
`…/messaging/outbox/OutboxRecorder.java:28-36`
```java
@TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = true)
public void registrar(DomainEvent event) {
    jdbc.sql("INSERT INTO outbox_eventos (topico, clave, payload) VALUES (:topico, :clave, :payload)") … .update();
}
```
> **Frase clave:** «`BEFORE_COMMIT`: el INSERT del evento viaja en la misma transacción que la versión. O entran los dos o no entra ninguno.»

### 1.5 El relé: después del commit, y de respaldo cada 2 s
`…/messaging/outbox/OutboxRelay.java:49-58`
```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
public void alConfirmar(DomainEvent ignorado) { publicarPendientes(); }

@Scheduled(fixedDelayString = "${app.outbox.intervalo:PT2S}", …)
public void programado() { publicarPendientes(); }
```
> **Frase clave:** «Si Kafka está caído, el evento se queda en la tabla y el programado lo reintenta. Nunca se pierde y nunca se inventa.»

### 1.6 Consumidor idempotente (notificaciones)
`notificaciones-ms/…/messaging/NotificacionEventConsumer.java` + tabla `eventos_procesados`
> **Frase clave:** «Si el mismo `eventId` llega dos veces, la segunda se descarta: el aviso no se duplica.»

### 1.7 Extra: cuentas con nombre.apellido y clave = DNI
`auth-ms/…/service/UsuarioService.java` (`crearCuentaPersona`, `baseUsername`) y `…/config/JwtKeyConfig.java` (clave de DEV persistida)
> **Frase clave:** «El usuario se arma con primer nombre y primer apellido sin tildes; la clave inicial es el documento y el cambio es obligatorio al primer ingreso.»

---

## Parte 2 · Andre (matrícula, asistencia, tareas)

### 2.1 El cupo: una sentencia atómica
`matricula-ms/…/repository/CupoSeccionRepository.java:14-17`
```java
@Query("UPDATE CupoSeccion c SET c.ocupados = c.ocupados + 1, c.actualizadoEn = CURRENT_TIMESTAMP "
     + "WHERE c.seccionId = :seccionId AND c.ocupados < c.capacidad")
int reservar(@Param("seccionId") Long seccionId);
```
> **Frase clave:** «Devuelve 0 si ya no hay cupo. No leo y luego escribo; la comprobación y el incremento son una sola operación: dos matrículas por la última vacante no pueden ganar las dos.»

### 2.2 TX1: cupo + matrícula + historial + evento
`matricula-ms/…/service/MatriculaService.java:166-208`
```java
return tx.execute(status -> {
    cupoService.asegurar(sec);
    cupoService.reservar(sec.id());           // lanza 409 si no hay cupo
    …
    Matricula m = matriculaRepository.save(…); // PRE_MATRICULA, vence en N días
    …
    outbox.registrar(KafkaConfig.TOPIC_MATRICULA_CREADA, ev);
    return MatriculaMapper.toResponse(m);
});
```
> **Frase clave:** «Primero se validan los servicios externos fuera de la transacción; dentro solo va lo local. Si `reservar` falla, nada de lo demás se escribe.»

### 2.3 El outbox exige transacción
`matricula-ms/…/service/EventoOutboxService.java:25`
```java
@Transactional(propagation = Propagation.MANDATORY)
public void registrar(String topic, MatriculaEvent payload) { … }
```
> **Frase clave:** «`MANDATORY`: si alguien intenta registrar un evento sin transacción, falla. Así es imposible olvidar la regla.»

### 2.4 TX3: confirmar por pago, idempotente
`…/service/MatriculaService.java:217-237`
```java
if ("MATRICULADO".equals(m.getEstado())) { log.info("ya estaba confirmada…"); return; }
if (!"PRE_MATRICULA".equals(m.getEstado())) { /* historial + alerta, no se confirma */ return; }
m.setEstado("MATRICULADO"); … outbox.registrar(KafkaConfig.TOPIC_MATRICULA_CONFIRMADA, …);
```
> **Frase clave:** «Si el pago llega dos veces, la segunda no hace nada. Si llega con la matrícula anulada, no la resucita: deja constancia y avisa.»

### 2.5 Compensación por tiempo
`…/scheduler/VencimientoJob.java:21-40`
```java
@Scheduled(cron = "0 0 * * * *", zone = "America/Lima")
@SchedulerLock(name = "matricula_vencimiento_job", lockAtLeastFor = "30s", lockAtMostFor = "10m")
public void anularVencidas() { … service.anularPorVencimiento(m.getId()); … }
```
> **Frase clave:** «Una reserva sin pago no retiene el cupo para siempre. ShedLock hace que, con varias réplicas, solo una ejecute el job.»

### 2.6 Consumidor del pago
`…/messaging/PagoEventConsumer.java:46-60` — `@KafkaListener(topics = TOPIC_PAGO_CONFIRMADO, groupId = "matricula-ms")`, con `eventos_procesados` y reintentos si la matrícula aún no es visible.

### 2.7 Orquestación de la matrícula en línea
`…/service/MatriculaEnLineaService.java` (`iniciar`, `pagar`): registra alumno → pre-matrícula → espera la cuota → cobra → confirma → crea cuentas → correo.
> **Frase clave:** «Es orquestación *sobre* la saga: si algo falla después del pago, el pago **no** se deshace; secretaría completa el vínculo. Un pago confirmado es un hecho.»

---

## Parte 3 · Witmer (pago, exámenes, calificaciones)

### 3.1 Idempotencia al entrar
`pago-ms/…/service/PagoService.java:37-44`
```java
@Transactional
public PagoResponse registrarPago(RegistrarPagoRequest req, Long usuarioId) {
    Optional<Pago> pagoExistente = pagoRepository.findByIdempotencyKey(req.getIdempotencyKey());
    if (pagoExistente.isPresent()) { return toResponse(pagoExistente.get()); }
```
> **Frase clave:** «El cliente manda una clave por intención de pago. Si repite por un corte de red, recibe el mismo pago; no se cobra dos veces.»

### 3.2 Validar antes de escribir
`…/PagoService.java:62-93`: estado de la cuota, monto ≤ saldo, suma de detalles = total, caja abierta.
> **Frase clave:** «Todas las reglas se comprueban antes de tocar un saldo; un error lanza excepción y la transacción revierte.»

### 3.3 Saldos con concurrencia optimista
`…/PagoService.java:111-135`: `cuota.setSaldo(nuevoSaldo)` + `cuotaRepository.save(cuota); // Dispara validación optimista de versión`
> **Frase clave:** «La cuota tiene `@Version`: si otro cobro la modificó entre mi lectura y mi escritura, el segundo falla en lugar de pisar al primero.»

### 3.4 Comprobante sin huecos ni duplicados
`…/service/ComprobanteService.java:36-47`
```java
SerieComprobante serieComp = serieComprobanteRepository.findFirstByTipoActivaForUpdate(tipoFinal)…;
long nuevoNumero = serieComp.getUltimoNumero() + 1;
```
> **Frase clave:** «`FOR UPDATE` bloquea la fila de la serie: dos cobros simultáneos se turnan y el correlativo sale correcto.»

### 3.5 Evento en la misma transacción
`…/PagoService.java:139-142` y `crearOutboxPagoConfirmado` (línea 148)
```java
Comprobante comprobante = comprobanteService.emitirComprobante(pagoGuardado, req.getTipoComprobante());
crearOutboxPagoConfirmado(pagoGuardado, matriculaIdPrincipal, cuotaIds, conceptoTipoPrincipal, conceptoNombrePrincipal);
```
> **Frase clave:** «Pago, saldos, comprobante y evento: una transacción. Si algo falla, no hay pago ni evento.»

### 3.6 El relé de pago
`…/messaging/OutboxPublisher.java:23-45` — `@Scheduled(fixedDelay = 2000)`, `findPendientesForUpdate()`, marca `enviado_en` o suma `intentos` y guarda `ultimo_error`.

### 3.7 La compensación: anular
`…/service/AnulacionService.java:32-64`
```java
if (pago.getCajaSesion() != null && "CERRADA".equals(pago.getCajaSesion().getEstado())) { throw … }
for (PagoDetalle det : pago.getDetalles()) {
    cuota.setSaldo(cuota.getSaldo().add(det.getMontoAplicado()));
    cuota.setEstado(cuota.getSaldo().compareTo(cuota.getMontoTotal()) >= 0 ? "PENDIENTE" : "PARCIAL");
}
pago.setEstado("ANULADO"); … comprobante.setEstado("ANULADO"); … // + evento pago.anulado en el outbox
```
> **Frase clave:** «La compensación no borra nada: marca anulado, devuelve el saldo y deja el rastro para auditoría.»

---

## Parte 4 · Lo que se enseña entre todos

| Tema | Archivo | Frase clave |
|---|---|---|
| Servicio a servicio | `*/filter/ServiceTokenFilter.java` y `FeignConfig` | «`/internal/**` solo responde con el token de servicio; el Gateway no lo enruta.» |
| Límite en el borde | `infra/eduspace-gateway/…/RateLimitFilter.java` | «Cubo de fichas por IP; la web pública tiene su propio cubo más estricto.» |
| Identidad | `JwtKeyConfig` (clave persistente en DEV) + claim `usuarioId` | «Ningún servicio asume `usuarioId == alumnoId`; se traduce con endpoints internos.» |
| Réplica local | `matriculas_local` (asistencia, tareas, exámenes, calificaciones, pago) | «Nadie consulta a matrícula en cada petición: se alimenta con eventos.» |
| Pasarela simulada | `matricula-ms/…/util/TarjetaSimulada.java` | «Luhn + vencimiento + CVV; no guarda la tarjeta; la 0002 se rechaza.» |
| Constancia PDF | `…/util/PdfConstancia.java` | «PDF A4 sin dependencias externas.» |

## Cómo guiar a la clase por el código (orden sugerido)
1. Muestra la **tabla** `outbox_eventos` con una fila reciente (`podman exec e2e-pg-pago psql … -c "select topic, enviado_en from outbox_eventos order by creado_en desc limit 3"`).
2. Ejecuta el script de demo del integrante.
3. Abre **un solo** archivo del bloque correspondiente y lee las líneas resaltadas.
4. Vuelve a la tabla/Kafka UI para cerrar el círculo («aquí está el evento»).
