package ec.edu.espe.zonas.controllers;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import ec.edu.espe.zonas.dtos.EspacioRequestDto;
import ec.edu.espe.zonas.dtos.EspacioResponseDto;
import ec.edu.espe.zonas.entidades.EstadoEspacio;
import ec.edu.espe.zonas.services.EspacioServicio;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/espacios")
@RequiredArgsConstructor
public class EspacioController {

    private final EspacioServicio servicioEspacio;

    @GetMapping("/")
    public ResponseEntity<List<EspacioResponseDto>> listarEspacios() {
        return ResponseEntity.ok(servicioEspacio.obtenerEspacios());
    }

    @PostMapping("/")
    public ResponseEntity<EspacioResponseDto> crearEspacio(@Valid @RequestBody EspacioRequestDto request) {
        return new ResponseEntity<>(servicioEspacio.crearEspacio(request), HttpStatus.CREATED);
    }

    @PutMapping("/{idEspacio}")
    public ResponseEntity<EspacioResponseDto> actualizarEspacio(
            @PathVariable UUID idEspacio,
            @Valid @RequestBody EspacioRequestDto request) {
        return ResponseEntity.ok(servicioEspacio.actualizarEspacio(idEspacio, request));
    }

    @DeleteMapping("/{idEspacio}")
    public ResponseEntity<Void> eliminarEspacio(@PathVariable UUID idEspacio) {
        servicioEspacio.eliminarEspacio(idEspacio);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{idEspacio}/estado/{estado}")
    public ResponseEntity<EspacioResponseDto> cambiarEstado(
            @PathVariable UUID idEspacio,
            @PathVariable EstadoEspacio estado) {
        return ResponseEntity.ok(servicioEspacio.cambiarEstado(idEspacio, estado));
    }

    @GetMapping("/estado/{estado}")
    public ResponseEntity<List<EspacioResponseDto>> listarPorEstado(@PathVariable EstadoEspacio estado) {
        return ResponseEntity.ok(servicioEspacio.obtenerEspaciosPorEstado(estado));
    }

    @GetMapping("/zona/{idZona}/estado/{estado}")
    public ResponseEntity<List<EspacioResponseDto>> listarPorZonaYEstado(
            @PathVariable UUID idZona,
            @PathVariable EstadoEspacio estado) {
        return ResponseEntity.ok(servicioEspacio.obtenerEspaciosPorZonaPorEstado(idZona, estado));
    }

}
