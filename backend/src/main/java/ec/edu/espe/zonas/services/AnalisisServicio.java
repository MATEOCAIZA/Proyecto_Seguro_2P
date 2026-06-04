package ec.edu.espe.zonas.services;

import ec.edu.espe.zonas.dtos.AnalisisRequestDto;
import ec.edu.espe.zonas.dtos.AnalisisResponseDto;

/**
 * Interfaz del servicio de análisis de vulnerabilidades.
 * Abstrae la comunicación con el microservicio FastAPI Python.
 */
public interface AnalisisServicio {

    /**
     * Envía código fuente Java al microservicio ML y retorna el resultado del análisis.
     *
     * @param request DTO con el código fuente y nombre del archivo
     * @return DTO con el resultado del análisis (es_seguro, vulnerabilidades, etc.)
     */
    AnalisisResponseDto analizarCodigo(AnalisisRequestDto request);

    /**
     * Verifica que el microservicio de análisis esté operativo.
     *
     * @return true si el microservicio responde y el modelo está cargado
     */
    boolean verificarDisponibilidad();
}
