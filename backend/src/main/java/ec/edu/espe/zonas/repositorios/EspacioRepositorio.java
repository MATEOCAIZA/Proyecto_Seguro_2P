package ec.edu.espe.zonas.repositorios;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import ec.edu.espe.zonas.entidades.Espacio;
import ec.edu.espe.zonas.entidades.EstadoEspacio;
import ec.edu.espe.zonas.entidades.TipoEspacio;

public interface EspacioRepositorio extends JpaRepository<Espacio, UUID> {

    boolean existsByCodigo(String codigo);

    long countByTipo(TipoEspacio tipo);

    List<Espacio> findByZona(UUID idZona);

    List<Espacio> findByZonaAndEstado(UUID idZona, EstadoEspacio estado);

    List<Espacio> findByEstado(EstadoEspacio estado);

}
