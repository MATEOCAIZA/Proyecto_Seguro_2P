package ec.edu.espe.zonas.utils;

import ec.edu.espe.zonas.dtos.EspacioRequestDto;
import ec.edu.espe.zonas.dtos.EspacioResponseDto;
import ec.edu.espe.zonas.entidades.Espacio;
import ec.edu.espe.zonas.entidades.EstadoEspacio;
import ec.edu.espe.zonas.entidades.TipoEspacio;
import ec.edu.espe.zonas.entidades.Zona;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UtilsMappers — Pruebas Unitarias")
class UtilsMappersTest {

    private final UtilsMappers mapper = new UtilsMappers();

    @Test
    @DisplayName("toResponseDto — Debe retornar null si el objeto es nulo")
    void toResponseDto_nulo_debeRetornarNull() {
        assertNull(mapper.toResponseDto(null));
    }

    @Test
    @DisplayName("toResponseDto — Debe retornar DTO mapeado correctamente")
    void toResponseDto_valido_debeMapear() {
        UUID idZona = UUID.randomUUID();
        UUID idEspacio = UUID.randomUUID();
        Zona zona = Zona.builder().id(idZona).nombre("Zona Test").build();
        LocalDateTime ahora = LocalDateTime.now();

        Espacio espacio = Espacio.builder()
                .id(idEspacio)
                .codigo("ESP-01")
                .descripcion("Descripción")
                .tipo(TipoEspacio.AUTO)
                .activo(true)
                .estado(EstadoEspacio.DISPONIBLE)
                .zona(zona)
                .fechaCreacion(ahora)
                .fechaModificacion(ahora)
                .build();

        EspacioResponseDto dto = mapper.toResponseDto(espacio);

        assertNotNull(dto);
        assertEquals(idEspacio, dto.getId());
        assertEquals("ESP-01", dto.getCodigo());
        assertEquals("Descripción", dto.getDescripcion());
        assertEquals(TipoEspacio.AUTO, dto.getTipo());
        assertTrue(dto.isActivo());
        assertEquals(EstadoEspacio.DISPONIBLE, dto.getEstado());
        assertEquals(idZona, dto.getIdZona());
        assertEquals("Zona Test", dto.getNombreZona());
        assertEquals(ahora, dto.getFechaCreacion());
        assertEquals(ahora, dto.getFechaModificacion());
    }

    @Test
    @DisplayName("toEntityEspacio — Debe retornar null si el request es nulo")
    void toEntityEspacio_nulo_debeRetornarNull() {
        assertNull(mapper.toEntityEspacio(null));
    }

    @Test
    @DisplayName("toEntityEspacio — Debe retornar entidad mapeada correctamente")
    void toEntityEspacio_valido_debeMapear() {
        EspacioRequestDto request = EspacioRequestDto.builder()
                .codigo("ESP-01")
                .descripcion("Descripción")
                .tipo(TipoEspacio.AUTO)
                .estado(EstadoEspacio.DISPONIBLE)
                .build();

        Espacio espacio = mapper.toEntityEspacio(request);

        assertNotNull(espacio);
        assertEquals("ESP-01", espacio.getCodigo());
        assertEquals("Descripción", espacio.getDescripcion());
        assertEquals(TipoEspacio.AUTO, espacio.getTipo());
        assertEquals(EstadoEspacio.DISPONIBLE, espacio.getEstado());
    }
}
