# Presentación: qué usar y en qué orden

Elige según el tiempo que tengas. Cada archivo tiene un solo propósito.

| Si tienes… | Usa | Para qué |
|---|---|---|
| **10 minutos** | [05 · Demostración rápida](05-demo-rapida.md) | Cinco procesos con la plataforma en vivo (nuevo alumno con aprobación, profesor en clase, alumna con tarea y examen, apoderada, administración y finanzas) y una hoja de credenciales. Antes: `python3 tools/demo/preparar_exposicion.py`. |
| **45 minutos** (con los tres integrantes) | [02 · Guion](02-guion-de-la-presentacion.md) | Minuto a minuto, quién habla, qué ejecuta y qué dice. |
| **Qué dice cada quien en cada diapositiva** | [Guion por diapositiva (PDF)](guion-por-diapositiva.pdf) | 26 diapositivas con *qué decir*, *qué mostrar* y el pase al siguiente; escenas de la demo de 10 min y hoja de respuestas a preguntas. Se regenera con `python3 tools/presentacion/guion_diapositivas.py`. |
| Preparar las diapositivas | [04 · Diapositivas para generador](04-diapositivas-para-generador.md) | Especificación diapositiva por diapositiva (completa y anexo de 8 para la demo corta). |
| Defender «cómo funciona por dentro» | [01 · Procesos transaccionales](01-procesos-transaccionales.md) | Un proceso por integrante: horario, matrícula con aprobación y cobro idempotente. |
| Enseñar el código | [03 · Enseñanza de código](03-ensenanza-de-codigo.md) | Qué archivos abrir y qué explicar de cada servicio. |
| Entender el planteamiento | [00 · Análisis](00-analisis-de-la-presentacion.md) | Qué pide la clase, reparto, riesgos y entregables. |

## Orden recomendado de preparación
1. Lee el **análisis (00)** y el **reparto** que corresponde a cada integrante.
2. Cada quien estudia **su proceso (01)** y **su código (03)**.
3. Genera las diapositivas con la especificación **(04)**.
4. Ensaya con el **guion (02)** o con la **demo rápida (05)** si el tiempo es corto.
5. Quince minutos antes: levanta el sistema y ejecuta el script de preparación (ver 05).
