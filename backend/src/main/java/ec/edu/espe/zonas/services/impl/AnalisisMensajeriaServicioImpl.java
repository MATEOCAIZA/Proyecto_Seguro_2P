package ec.edu.espe.zonas.services.impl;

import ec.edu.espe.zonas.config.RabbitMQConfig;
import ec.edu.espe.zonas.dtos.AnalisisRequestDto;
import ec.edu.espe.zonas.services.AnalisisMensajeriaServicio;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

/**
 * Implementación de la mensajería de RabbitMQ. Encola mensajes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalisisMensajeriaServicioImpl implements AnalisisMensajeriaServicio {

    private final RabbitTemplate rabbitTemplate;

    @Override
    public void enviarSolicitudAnalisis(AnalisisRequestDto request) {
        log.info("Encolando solicitud de análisis para el archivo '{}' en RabbitMQ...", request.getNombreArchivo());
        
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE,
                RabbitMQConfig.ROUTING_KEY,
                request
        );
        
        log.info("Solicitud para '{}' encolada exitosamente.", request.getNombreArchivo());
    }
}
