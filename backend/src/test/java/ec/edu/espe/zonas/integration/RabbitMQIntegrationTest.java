package ec.edu.espe.zonas.integration;

import ec.edu.espe.zonas.dtos.AnalisisRequestDto;
import ec.edu.espe.zonas.dtos.AnalisisResponseDto;
import ec.edu.espe.zonas.services.AnalisisMensajeriaServicio;
import ec.edu.espe.zonas.services.AnalisisServicio;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.util.Collections;

import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Prueba de integración de extremo a extremo para el flujo de mensajería con RabbitMQ.
 * Excluye la autoconfiguración de la base de datos para poder ejecutarse sin necesidad
 * de levantar PostgreSQL, centrándose exclusivamente en el envío y consumo de mensajes.
 */
@SpringBootTest(properties = {
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration"
})
@Testcontainers
class RabbitMQIntegrationTest {

    // Levanta un contenedor Docker real de RabbitMQ para la prueba
    @Container
    static RabbitMQContainer rabbitMQ = new RabbitMQContainer("rabbitmq:3-management");

    // Asigna dinámicamente los puertos del contenedor a las propiedades de Spring
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.rabbitmq.host", rabbitMQ::getHost);
        registry.add("spring.rabbitmq.port", rabbitMQ::getAmqpPort);
        registry.add("spring.rabbitmq.username", rabbitMQ::getAdminUsername);
        registry.add("spring.rabbitmq.password", rabbitMQ::getAdminPassword);
    }

    @Autowired
    private AnalisisMensajeriaServicio analisisMensajeriaServicio;

    // Reemplaza el bean real con un Mock para no depender de que la FastAPI de Python esté corriendo
    @MockitoBean
    private AnalisisServicio analisisServicio;

    @Test
    void testEnvioYConsumoMensaje() {
        // Arrange
        AnalisisResponseDto mockResponse = AnalisisResponseDto.builder()
                .status("completado")
                .archivo("IntegrationTest.java")
                .esSeguro(true)
                .vulnerabilidadesDetectadas(Collections.emptyList())
                .build();
        
        when(analisisServicio.analizarCodigo(any(AnalisisRequestDto.class))).thenReturn(mockResponse);

        AnalisisRequestDto request = AnalisisRequestDto.builder()
                .codigoFuente("public class IntegrationTest {}")
                .nombreArchivo("IntegrationTest.java")
                .build();

        // Act: Enviar el mensaje a la cola
        analisisMensajeriaServicio.enviarSolicitudAnalisis(request);

        // Assert: Esperar hasta 10 segundos a que el consumidor asíncrono procese el mensaje de la cola
        await()
            .atMost(Duration.ofSeconds(10))
            .untilAsserted(() -> verify(analisisServicio, times(1)).analizarCodigo(any(AnalisisRequestDto.class)));
    }
}
