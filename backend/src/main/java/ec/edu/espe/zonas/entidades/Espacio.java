package ec.edu.espe.zonas.entidades;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Column;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.EnumType;

import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.UUID;
import java.time.LocalDateTime;

@Entity
@Table(name="espacios")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder

public class Espacio{

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique=true, nullable=false, length=12)//EJEMPLO ESPACIO-1A-ZONA-A1
    private String codigo;
    @Column(length = 12)
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(nullable=false)
    private TipoEspacio tipo;

    @Column(nullable = false)
    private boolean activo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoEspacio estado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_zona")
    private Zona zona;

    @Column
    private LocalDateTime fechaCreacion;
    @Column
    private LocalDateTime fechaModificacion;
}