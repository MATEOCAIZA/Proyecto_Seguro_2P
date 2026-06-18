package ec.edu.espe.zonas.controllers;

import java.util.List;
import java.util.UUID;

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
    private final ZonaServicio zonaServicio;

    @GetMapping("/")
    public ResponseEntity<List<ZonaResponseDto>> listarZonas() {
        try {
            List<ZonaResponseDto> zonas = zonaServicio.listarZonas();
            return ResponseEntity.ok(zonas);
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
            return new ResponseEntity<>(zonaServicio.crearZona(request), HttpStatus.CREATED);
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
            if (idZona == null) {
                throw new IllegalArgumentException("El identificador de zona no puede ser nulo");
            }
            ZonaResponseDto resultado = zonaServicio.actualizarZona(idZona, request);
            return ResponseEntity.ok(resultado);
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
            zonaServicio.activarDesactivar(idZona);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            log.warn("Zona no encontrada al cambiar estado {}: {}", idZona, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error inesperado al cambiar estado de zona {}", idZona, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}

