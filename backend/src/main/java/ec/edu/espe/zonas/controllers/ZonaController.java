package ec.edu.espe.zonas.controllers;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import ec.edu.espe.zonas.dtos.ZonaRequestDto;
import ec.edu.espe.zonas.dtos.ZonaResponseDto;
import ec.edu.espe.zonas.services.ZonaServicio;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v1/zonas")
@RequiredArgsConstructor
public class ZonaController {

    private static final Pattern UUID_PATTERN =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    /** Solo letras, números, espacios y guiones — previene inyección en nombre de zona. */
    private static final Pattern NOMBRE_ZONA_PATTERN =
            Pattern.compile("^[\\p{L}\\p{N} \\-]{1,32}$");

    private final ZonaServicio zonaServicio;

    @GetMapping("/")
    public ResponseEntity<List<ZonaResponseDto>> listarZonas() {
        try {
            List<ZonaResponseDto> zonas = zonaServicio.listarZonas();
            boolean esListaValida = zonas != null;
            if (!esListaValida) {
                throw new IllegalStateException("El servicio retornó una lista nula");
            }
            return ResponseEntity.ok(zonas);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            log.warn("Parámetro inválido al listar zonas: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error inesperado al listar zonas", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PostMapping("/")
    public ResponseEntity<ZonaResponseDto> crearZona(@Valid @RequestBody ZonaRequestDto request) {
        try {
            Objects.requireNonNull(request, "El cuerpo de la solicitud no puede ser nulo");
            boolean nombreSeguro = request.getNombre() != null
                    && NOMBRE_ZONA_PATTERN.matcher(request.getNombre()).matches();
            if (!nombreSeguro) {
                throw new IllegalArgumentException("El nombre de zona contiene caracteres no permitidos");
            }
            ZonaResponseDto creada = zonaServicio.crearZona(request);
            return new ResponseEntity<>(creada, HttpStatus.CREATED);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            log.warn("Datos inválidos al crear zona: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error inesperado al crear zona", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PutMapping("/{idZona}")
    public ResponseEntity<ZonaResponseDto> actualizarZona(
            @PathVariable UUID idZona,
            @Valid @RequestBody ZonaRequestDto request) {
        try {
            Objects.requireNonNull(request, "El cuerpo de la solicitud no puede ser nulo");
            boolean idValido = idZona != null
                    && UUID_PATTERN.matcher(idZona.toString()).matches();
            if (!idValido) {
                throw new IllegalArgumentException("El identificador de zona no tiene formato UUID válido");
            }
            boolean nombreSeguro = request.getNombre() != null
                    && NOMBRE_ZONA_PATTERN.matcher(request.getNombre()).matches();
            if (!nombreSeguro) {
                throw new IllegalArgumentException("El nombre de zona contiene caracteres no permitidos");
            }
            ZonaResponseDto resultado = zonaServicio.actualizarZona(idZona, request);
            return ResponseEntity.ok(resultado);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            log.warn("Argumento inválido al actualizar zona {}: {}", idZona, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error inesperado al actualizar zona {}", idZona, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @PatchMapping("/{idZona}/estado")
    public ResponseEntity<Void> activarDesactivar(@PathVariable UUID idZona) {
        try {
            boolean idValido = idZona != null
                    && UUID_PATTERN.matcher(idZona.toString()).matches();
            if (!idValido) {
                throw new IllegalArgumentException("UUID de zona inválido: " + idZona);
            }
            zonaServicio.activarDesactivar(idZona);
            return ResponseEntity.ok().build();
        } catch (ResponseStatusException e) {
            throw e;
        } catch (IllegalArgumentException e) {
            log.warn("Zona no encontrada al cambiar estado {}: {}", idZona, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error inesperado al cambiar estado de zona {}", idZona, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
