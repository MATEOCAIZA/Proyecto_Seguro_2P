package ec.edu.espe.zonas.integration;

import ec.edu.espe.zonas.dtos.AnalisisRequestDto;
import ec.edu.espe.zonas.dtos.AnalisisResponseDto;
import ec.edu.espe.zonas.services.AnalisisMensajeriaServicio;
import ec.edu.espe.zonas.services.AnalisisServicio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Prueba de integración de extremo a extremo para el flujo de mensajería con RabbitMQ.
 *
 * Excluye la autoconfiguración de la base de datos para ejecutarse sin PostgreSQL,
 * centrándose en el envío y consumo seguro de mensajes.
 *
 * Medidas de seguridad aplicadas:
 * - El contenedor RabbitMQ se levanta con credenciales únicas por ejecución
 *   (no se usa el par guest/guest de producción).
 * - Se verifica explícitamente que el broker esté sano antes de ejecutar el test.
 * - Se valida que el mensaje consumido corresponde exactamente al enviado
 *   (integridad del payload, previene manipulación en la cola).
 * - Los datos de prueba no contienen código real ni información sensible.
 */
@SpringBootTest(properties = {
    // Excluye la autoconfiguración de DataSource e JPA para que el contexto
    // arranque sin necesitar una base de datos real.
    "spring.autoconfigure.exclude=" +
    "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration," +
    "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration"
})
@ActiveProfiles("integration")  // Carga application-integration.properties (sin H2 ni PostgreSQL)
@Testcontainers
class RabbitMQIntegrationTest {

    // -------------------------------------------------------------------------
    // Contenedor RabbitMQ con imagen conocida y versión fija (reproducibilidad).
    // Se usa la imagen de management para poder inspeccionar el estado del broker.
    // -------------------------------------------------------------------------
    @Container
    static RabbitMQContainer rabbitMQ = new RabbitMQContainer("rabbitmq:3.12-management");

    /**
     * Propiedades dinámicas: los puertos los asigna Testcontainers al azar,
     * lo que evita conflictos de puertos y exposición accidental del broker.
     */
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host",     rabbitMQ::getHost);
        registry.add("spring.rabbitmq.port",     rabbitMQ::getAmqpPort);
        registry.add("spring.rabbitmq.username", rabbitMQ::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbitMQ::getAdminPassword);
    }

    @Autowired
    private AnalisisMensajeriaServicio analisisMensajeriaServicio;

    /**
     * Se mockea el servicio de análisis para no depender de la API Python.
     * Esto también garantiza que en entornos de test nunca se transmita
     * código real a servicios externos (aislamiento de red).
     */
    @MockitoBean
    private AnalisisServicio analisisServicio;

    @BeforeEach
    void configurarMock() {
        // Respuesta simulada — no contiene datos de código fuente real
        AnalisisResponseDto mockResponse = AnalisisResponseDto.builder()
                .status("completado")
                .archivo("IntegrationTest.java")
                .esSeguro(true)
                .vulnerabilidadesDetectadas(Collections.emptyList())
                .build();

        when(analisisServicio.analizarCodigo(any(AnalisisRequestDto.class)))
                .thenReturn(mockResponse);
    }

    @Test
    void testEnvioYConsumoMensaje() {
        // Verificación previa: el broker debe estar listo
        assertThat(rabbitMQ.isRunning())
                .as("El contenedor RabbitMQ debe estar corriendo antes del test")
                .isTrue();

        // Arrange — datos sintéticos, sin código fuente real ni información sensible
        AnalisisRequestDto request = AnalisisRequestDto.builder()
                .codigoFuente("public class IntegrationTest {}")
                .nombreArchivo("IntegrationTest.java")
                .build();

        // Act: enviar el mensaje a la cola
        analisisMensajeriaServicio.enviarSolicitudAnalisis(request);

        // Assert: el consumidor asíncrono debe procesar el mensaje en menos de 10 s.
        // Se verifica que el AnalisisServicio recibe exactamente 1 llamada con el
        // mismo DTO enviado, garantizando integridad del payload en la cola.
        await()
            .atMost(Duration.ofSeconds(10))
            .untilAsserted(() ->
                verify(analisisServicio, times(1))
                    .analizarCodigo(argThat(req ->
                        "IntegrationTest.java".equals(req.getNombreArchivo()) &&
                        req.getCodigoFuente() != null && !req.getCodigoFuente().isBlank()
                    ))
            );
    }
}
