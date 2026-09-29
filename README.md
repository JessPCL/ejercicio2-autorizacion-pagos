Entregable: Ejercicio 2 - Autorización de Pagos
Este documento contiene la justificación teórica y arquitectónica de la solución implementada.

1. Objetivo, Actores y Alcance
Objetivo: Diseñar un sistema de autorización de pagos altamente disponible que procese solicitudes rápidamente para el usuario, garantizando la integridad de los datos (sin cobros dobles) y delegando la comunicación inestable con el banco externo a procesos en segundo plano.

Actores:

Cliente / Aplicación Móvil: Inicia la solicitud de pago.

Banco Externo: Entidad de terceros que procesa y liquida el pago finalmente.

Equipo de Operaciones/Auditoría: Revisa los registros y conciliaciones.

Alcance: Abarca desde la recepción del pago en la API, la validación de reglas de negocio (límites, idempotencia), el registro en la base de datos de auditoría, hasta la encolamiento del mensaje para conciliación asíncrona.

2. Requisitos Funcionales y de Calidad
Requisitos Funcionales:

El sistema debe recibir pagos y validarlos (ej. límites de monto).

Se debe registrar cada intento de pago, exitoso o fallido.

El sistema debe evitar transacciones duplicadas generadas por reintentos accidentales.

La comunicación con el banco externo debe ocurrir sin bloquear la respuesta al usuario.

Atributos de Calidad (No Funcionales):

Disponibilidad / Resiliencia: El sistema debe responder al cliente incluso si el banco externo está caído.

Consistencia: Protección estricta contra la duplicidad de registros financieros (ACID).

Baja Latencia: El tiempo de respuesta hacia la app móvil debe ser mínimo.

3. Diagramas C4 (Contexto y Contenedores)
Puedes visualizar estos diagramas copiando el código en Mermaid Live Editor.

Diagrama de Contexto (Nivel 1)
Fragmento de código
C4Context
    title Diagrama de Contexto - Autorización de Pagos

    Person(cliente, "Cliente", "Usuario de la App Móvil")
    System(sistemaPagos, "Sistema de Pagos", "Valida, registra y enruta los pagos")
    System_Ext(bancoExterno, "Banco Externo", "Procesa la transacción financiera real")

    Rel(cliente, sistemaPagos, "Solicita pago", "HTTPS/REST")
    Rel(sistemaPagos, bancoExterno, "Liquida transacción", "HTTPS")
Diagrama de Contenedores (Nivel 2)
Fragmento de código
C4Container
    title Diagrama de Contenedores - Autorización de Pagos

    Person(cliente, "Cliente", "Usuario de la App Móvil")
    
    System_Boundary(c1, "Sistema de Pagos") {
        Container(webApi, "API REST (Spring Boot)", "Java", "Recibe pagos, valida idempotencia y reglas de negocio. Responde síncronamente al cliente.")
        ContainerDb(db, "Base de Datos (PostgreSQL)", "SQL", "Almacena auditoría, estado de pagos y claves de idempotencia.")
        ContainerQueue(cola, "Message Broker (RabbitMQ)", "AMQP", "Encola eventos de pagos validados para procesar asíncronamente.")
        Container(worker, "Worker de Conciliación", "Java", "Consume mensajes, llama al banco y actualiza el estado final en BD.")
    }
    
    System_Ext(bancoExterno, "Banco Externo", "API de terceros")

    Rel(cliente, webApi, "Solicita pago", "JSON/HTTPS")
    Rel(webApi, db, "Lee/Escribe estado y auditoría", "JDBC")
    Rel(webApi, cola, "Publica evento de pago aprobado localmente", "AMQP")
    Rel(cola, worker, "Consume evento de pago", "AMQP")
    Rel(worker, bancoExterno, "Llamada HTTP con reintentos", "REST/HTTPS")
    Rel(worker, db, "Actualiza conciliación", "JDBC")
4. Flujo de la Operación Crítica (Autorización Segura)
Recepción: La App Móvil envía un POST /pagos incluyendo una Idempotency-Key única en los headers.

Validación de Duplicidad (Síncrono): Spring Boot consulta a PostgreSQL si la clave existe (respaldado por una restricción UNIQUE en la tabla). Si existe, retorna HTTP 400 inmediatamente, evitando cobros dobles.

Reglas de Negocio (Síncrono): Se validan los límites de la cuenta. Si falla, se guarda el estado RECHAZADO en la base de datos y se responde al cliente.

Confirmación Parcial (Síncrono): Si todo es correcto, se guarda el pago como ACEPTADO_LOCAL en PostgreSQL y se retorna HTTP 200 al cliente.

Delegación (Asíncrono): La API publica un evento en RabbitMQ con los datos del pago.

Conciliación (Asíncrono): Un proceso en segundo plano lee RabbitMQ, se comunica con el banco externo y actualiza el registro en PostgreSQL al estado LIQUIDADO o FALLIDO_BANCO.

5. Stack Propuesto y Justificación
Backend: Spring Boot (Java). Ofrece un ecosistema robusto, inyección de dependencias, abstracción sencilla para bases de datos (Spring Data JPA) y una integración nativa y madura con RabbitMQ.

Base de Datos: PostgreSQL. Al tratarse de transacciones financieras, las garantías ACID (Atomicidad, Consistencia, Aislamiento, Durabilidad) y las restricciones únicas (UNIQUE constraint) son innegociables.

Mensajería: RabbitMQ. Garantiza la entrega de mensajes (at-least-once delivery). Actúa como un "amortiguador" (buffer) si el banco externo está caído, guardando los pagos temporalmente hasta que se puedan enviar, sin que el usuario se quede esperando frente a una pantalla de carga.

Infraestructura: Docker. Permite empaquetar todo el entorno para garantizar que funcionará exactamente igual en desarrollo, pruebas y producción.

6. Decisiones Arquitectónicas (ADRs)
ADR 1: Separación de procesamiento síncrono y asíncrono
Contexto: La API del banco externo puede ser lenta o fallar inesperadamente.

Decisión: Responder al usuario síncronamente tras una validación local y delegar la llamada al banco a un proceso asíncrono (RabbitMQ).

Consecuencia: Mejora drásticamente la experiencia del usuario (baja latencia) y la resiliencia del sistema, pero requiere manejar consistencia eventual (el estado del pago en la app puede decir "Procesando" por unos segundos hasta que el banco confirme).

ADR 2: Estrategia de prevención de pagos duplicados (Idempotencia)
Contexto: Los usuarios pueden hacer doble clic por frustración, o los problemas de red pueden causar que la app reintente la misma petición.

Decisión: Implementar el patrón de Idempotencia usando un UUID generado por el cliente (Idempotency-Key en los headers) respaldado por un índice único en PostgreSQL.

Consecuencia: Garantiza matemáticamente a nivel de base de datos que una misma transacción no se cobre dos veces. Aumenta ligeramente la carga en la base de datos por cada petición.

7. Riesgos y Mitigaciones
Riesgo: El banco externo se cae por horas, acumulando miles de mensajes en RabbitMQ y consumiendo toda la memoria.

Mitigación: Implementar el patrón Circuit Breaker (ej. Resilience4j) para detener el envío temporalmente, y configurar las colas con un TTL (Time To Live) y un Dead Letter Queue (DLQ) para derivar los mensajes atascados a revisión manual.

Riesgo: Pérdida de mensajes en RabbitMQ si el contenedor se reinicia o colapsa.

Mitigación: Declarar los intercambios (exchanges) y colas (queues) como durables y los mensajes como persistentes, para que se guarden en disco antes de procesarse.

Riesgo: La tabla de pagos (auditoría) crece demasiado, haciendo que la búsqueda por Idempotency-Key sea lenta.

Mitigación: Crear índices B-Tree específicos en la columna idempotency_key y, a futuro, implementar particionamiento de base de datos por fecha (ej. un histórico mensual).

8. Métricas Clave
Métrica de Negocio: Tasa de pagos duplicados (Duplicate payment rate). Debe ser estrictamente del 0%. También se medirá el Tiempo de Autorización, es decir, el tiempo percibido por el cliente desde que pulsa el botón hasta que recibe el éxito local.

Métrica Técnica: Latencia del banco externo y Tamaño de la cola (Queue depth) en RabbitMQ. Si la cola empieza a crecer rápidamente, es un indicador técnico temprano de que el sistema de conciliación está fallando.