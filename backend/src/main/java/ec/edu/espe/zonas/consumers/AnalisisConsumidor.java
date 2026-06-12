package ec.edu.espe.zonas.consumers;

import ec.edu.espe.zonas.config.RabbitMQConfig;
import ec.edu.espe.zonas.dtos.AnalisisRequestDto;
import ec.edu.espe.zonas.dtos.AnalisisResponseDto;
import ec.edu.espe.zonas.services.AnalisisServicio;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumidor de RabbitMQ que escucha la cola de solicitudes de análisis de código.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AnalisisConsumidor {

    private final AnalisisServicio analisisServicio;

    /**
     * Escucha la cola 'analisis.solicitudes' y procesa el mensaje de análisis.
     * Invoca el microservicio de Python de manera asíncrona respecto a la petición HTTP original.
     *
     * @param request Datos de la solicitud de análisis recibidos desde la cola.
     */
    @RabbitListener(queues = RabbitMQConfig.QUEUE)
    public void consumirSolicitudAnalisis(AnalisisRequestDto request) {
        log.info("Mensaje recibido en la cola: archivo '{}' listo para análisis.", request.getNombreArchivo());
        
        try {
            // Llamar al microservicio FastAPI HTTP para realizar el análisis pesado
            AnalisisResponseDto respuesta = analisisServicio.analizarCodigo(request);
            
            if (respuesta != null) {
                if (Boolean.TRUE.equals(respuesta.getEsSeguro())) {
                    log.info(">>> RESULTADO ASÍNCRONO: El archivo '{}' es SEGURO.", request.getNombreArchivo());
                } else {
                    log.warn(">>> RESULTADO ASÍNCRONO: El archivo '{}' es VULNERABLE. Se detectaron {} vulnerabilidades.",
                            request.getNombreArchivo(),
                            respuesta.getVulnerabilidadesDetectadas() != null ? respuesta.getVulnerabilidadesDetectadas().size() : 0);
                }
            }
        } catch (Exception e) {
            log.error("Error al procesar el análisis de forma asíncrona para '{}': {}", 
                    request.getNombreArchivo(), e.getMessage());
        }
    }
}
