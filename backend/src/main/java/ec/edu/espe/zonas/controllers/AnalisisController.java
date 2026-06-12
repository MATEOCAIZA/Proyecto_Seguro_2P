package ec.edu.espe.zonas.controllers;

import ec.edu.espe.zonas.dtos.AnalisisRequestDto;
import ec.edu.espe.zonas.dtos.AnalisisResponseDto;
import ec.edu.espe.zonas.services.AnalisisMensajeriaServicio;
import ec.edu.espe.zonas.services.AnalisisServicio;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * Controlador REST para el servicio de análisis de vulnerabilidades.
 * Permite invocar el modelo ML desde una UI o herramienta externa.
 *
 * Base URL: /api/v1/analisis
 *
 * Medidas de seguridad aplicadas (CWE-319):
 * - Se valida que la petición llegue exclusivamente por HTTPS.
 *   En producción el proxy (Nginx/Render) siempre usa TLS, pero se
 *   añade una doble comprobación a nivel de aplicación.
 * - El cuerpo del request lleva @Valid para rechazar entradas malformadas
 *   antes de que lleguen al servicio o sean reenviadas a FastAPI.
 * - No se loguea el código fuente recibido (evita exponer datos sensibles
 *   en texto claro en los logs).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/analisis")
@RequiredArgsConstructor
public class AnalisisController {

    private final AnalisisServicio analisisServicio;
    private final AnalisisMensajeriaServicio analisisMensajeriaServicio;

    // -------------------------------------------------------------------------
    // Helper: verifica que la petición llegue por canal cifrado (HTTPS / TLS).
    // Soporta el header X-Forwarded-Proto que ponen los proxies inversos.
    // -------------------------------------------------------------------------
    private void requireSecureChannel(HttpServletRequest httpRequest) {
        String proto = httpRequest.getHeader("X-Forwarded-Proto");
        boolean esHttps = "https".equalsIgnoreCase(proto) || httpRequest.isSecure();
        if (!esHttps) {
            log.warn("Intento de envío de código fuente por canal no cifrado desde IP: {}",
                    httpRequest.getRemoteAddr());
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Esta operación requiere una conexión segura (HTTPS). " +
                    "La transmisión de código fuente en texto claro no está permitida."
            );
        }
    }

    /**
     * POST /api/v1/analisis/codigo
     *
     * Analiza un fragmento de código Java y retorna si es vulnerable o seguro.
     * Requiere HTTPS para evitar transmisión de código en texto claro (CWE-319).
     *
     * Body esperado:
     * {
     *   "codigo_fuente": "public class ... { ... }",
     *   "nombre_archivo": "MiClase.java"
     * }
     */
    @PostMapping("/codigo")
    public ResponseEntity<AnalisisResponseDto> analizarCodigo(
            @Valid @RequestBody AnalisisRequestDto request,
            HttpServletRequest httpRequest) {

        // CWE-319: bloquear peticiones no cifradas antes de procesar datos sensibles
        requireSecureChannel(httpRequest);

        // Solo se registra el nombre del archivo, nunca el código fuente
        log.info("Solicitud de análisis recibida para: '{}'", request.getNombreArchivo());

        AnalisisResponseDto resultado = analisisServicio.analizarCodigo(request);
        return ResponseEntity.ok(resultado);
    }

    /**
     * POST /api/v1/analisis/codigo/async
     *
     * Encola un fragmento de código para análisis asíncrono vía RabbitMQ.
     * Requiere HTTPS para evitar transmisión de código en texto claro (CWE-319).
     * Retorna inmediatamente una confirmación.
     */
    @PostMapping("/codigo/async")
    public ResponseEntity<Map<String, String>> analizarCodigoAsync(
            @Valid @RequestBody AnalisisRequestDto request,
            HttpServletRequest httpRequest) {

        // CWE-319: bloquear peticiones no cifradas antes de encolar datos sensibles
        requireSecureChannel(httpRequest);

        log.info("Encolando solicitud asíncrona para: '{}'", request.getNombreArchivo());

        analisisMensajeriaServicio.enviarSolicitudAnalisis(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "status",   "ENCOLADO",
                "mensaje",  "La solicitud de análisis ha sido enviada a la cola de RabbitMQ.",
                "archivo",  request.getNombreArchivo()
        ));
    }

    /**
     * GET /api/v1/analisis/health
     *
     * Verifica si el microservicio FastAPI y el modelo ML están disponibles.
     * Este endpoint no maneja datos sensibles y puede ser accedido por HTTP.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> verificarEstado() {
        boolean disponible = analisisServicio.verificarDisponibilidad();
        return ResponseEntity.ok(Map.of(
                "microservicio_disponible", disponible,
                "mensaje", disponible
                        ? "El microservicio de análisis está operativo."
                        : "El microservicio de análisis no está disponible."
        ));
    }
}
