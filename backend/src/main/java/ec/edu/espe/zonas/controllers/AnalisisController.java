package ec.edu.espe.zonas.controllers;

import ec.edu.espe.zonas.dtos.AnalisisRequestDto;
import ec.edu.espe.zonas.dtos.AnalisisResponseDto;
import ec.edu.espe.zonas.services.AnalisisServicio;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controlador REST para el servicio de análisis de vulnerabilidades.
 * Permite invocar el modelo ML desde una UI o herramienta externa.
 *
 * Base URL: /api/v1/analisis
 */
@RestController
@RequestMapping("/api/v1/analisis")
@RequiredArgsConstructor
public class AnalisisController {

    private final AnalisisServicio analisisServicio;

    /**
     * POST /api/v1/analisis/codigo
     *
     * Analiza un fragmento de código Java y retorna si es vulnerable o seguro.
     *
     * Body esperado:
     * {
     *   "codigo_fuente": "public class ... { ... }",
     *   "nombre_archivo": "MiClase.java"
     * }
     */
    @PostMapping("/codigo")
    public ResponseEntity<AnalisisResponseDto> analizarCodigo(
            @Valid @RequestBody AnalisisRequestDto request) {
        AnalisisResponseDto resultado = analisisServicio.analizarCodigo(request);
        return ResponseEntity.ok(resultado);
    }

    /**
     * GET /api/v1/analisis/health
     *
     * Verifica si el microservicio FastAPI y el modelo ML están disponibles.
     * Útil para que la UI muestre el estado del sistema de seguridad.
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
