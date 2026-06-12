package ec.edu.espe.zonas.services;

import ec.edu.espe.zonas.dtos.AnalisisRequestDto;

/**
 * Interfaz para el servicio de mensajería (productor de RabbitMQ).
 * Permite enviar solicitudes de análisis de manera asíncrona.
 */
public interface AnalisisMensajeriaServicio {

    /**
     * Encola una solicitud de análisis en la cola de RabbitMQ.
     *
     * @param request Datos de la solicitud de análisis
     */
    void enviarSolicitudAnalisis(AnalisisRequestDto request);
}
