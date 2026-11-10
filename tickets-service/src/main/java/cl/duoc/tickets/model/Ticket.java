package cl.duoc.tickets.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Metadatos de un ticket. El PDF vive en EFS (temporal) y en S3 (definitivo);
 * esta entidad guarda donde esta cada copia y los datos para historial y estadisticas.
 */
@Entity
@Table(name = "tickets")
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String eventoId;

    @Column(nullable = false)
    private String usuarioId;

    @Column(nullable = false)
    private String nombreEvento;

    @Column(nullable = false)
    private String nombreAsistente;

    private LocalDateTime fechaEvento;

    private String asiento;

    @Column(nullable = false)
    private Integer precio;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoTicket estado;

    /** Nombre del archivo, por ejemplo ticket001.pdf */
    private String nombreArchivo;

    /** Ruta del PDF dentro del EFS, por ejemplo /app/efs/tickets/evento123/usuarioX/ticket001.pdf */
    private String rutaEfs;

    /** Clave del objeto en S3, por ejemplo evento123/usuarioX/ticket001.pdf */
    private String claveS3;

    private boolean subidoAS3;

    private LocalDateTime fechaSubidaS3;

    @Column(nullable = false)
    private LocalDateTime fechaCreacion;

    private LocalDateTime fechaModificacion;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEventoId() { return eventoId; }
    public void setEventoId(String eventoId) { this.eventoId = eventoId; }

    public String getUsuarioId() { return usuarioId; }
    public void setUsuarioId(String usuarioId) { this.usuarioId = usuarioId; }

    public String getNombreEvento() { return nombreEvento; }
    public void setNombreEvento(String nombreEvento) { this.nombreEvento = nombreEvento; }

    public String getNombreAsistente() { return nombreAsistente; }
    public void setNombreAsistente(String nombreAsistente) { this.nombreAsistente = nombreAsistente; }

    public LocalDateTime getFechaEvento() { return fechaEvento; }
    public void setFechaEvento(LocalDateTime fechaEvento) { this.fechaEvento = fechaEvento; }

    public String getAsiento() { return asiento; }
    public void setAsiento(String asiento) { this.asiento = asiento; }

    public Integer getPrecio() { return precio; }
    public void setPrecio(Integer precio) { this.precio = precio; }

    public EstadoTicket getEstado() { return estado; }
    public void setEstado(EstadoTicket estado) { this.estado = estado; }

    public String getNombreArchivo() { return nombreArchivo; }
    public void setNombreArchivo(String nombreArchivo) { this.nombreArchivo = nombreArchivo; }

    public String getRutaEfs() { return rutaEfs; }
    public void setRutaEfs(String rutaEfs) { this.rutaEfs = rutaEfs; }

    public String getClaveS3() { return claveS3; }
    public void setClaveS3(String claveS3) { this.claveS3 = claveS3; }

    public boolean isSubidoAS3() { return subidoAS3; }
    public void setSubidoAS3(boolean subidoAS3) { this.subidoAS3 = subidoAS3; }

    public LocalDateTime getFechaSubidaS3() { return fechaSubidaS3; }
    public void setFechaSubidaS3(LocalDateTime fechaSubidaS3) { this.fechaSubidaS3 = fechaSubidaS3; }

    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }

    public LocalDateTime getFechaModificacion() { return fechaModificacion; }
    public void setFechaModificacion(LocalDateTime fechaModificacion) { this.fechaModificacion = fechaModificacion; }
}
