package cl.duoc.tickets.service;

import cl.duoc.tickets.dto.ActualizarTicketRequest;
import cl.duoc.tickets.dto.CrearTicketRequest;
import cl.duoc.tickets.dto.EstadisticasEvento;
import cl.duoc.tickets.exception.AlmacenamientoException;
import cl.duoc.tickets.exception.RecursoNoEncontradoException;
import cl.duoc.tickets.model.EstadoTicket;
import cl.duoc.tickets.model.Ticket;
import cl.duoc.tickets.repository.TicketRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Logica del sistema de tickets. Flujo de un ticket:
 *   1. se registra y se genera su PDF,
 *   2. el PDF se guarda en EFS (almacenamiento temporal),
 *   3. se sube automaticamente a S3 en {eventoId}/{usuarioId}/ticketNNN.pdf.
 */
@Service
public class TicketService {

    private final TicketRepository repositorio;
    private final PdfService pdf;
    private final EfsService efs;
    private final S3Service s3;

    public TicketService(TicketRepository repositorio, PdfService pdf, EfsService efs, S3Service s3) {
        this.repositorio = repositorio;
        this.pdf = pdf;
        this.efs = efs;
        this.s3 = s3;
    }

    /** Genera el ticket, lo guarda en EFS y lo sube automaticamente a S3. */
    public Ticket generar(CrearTicketRequest datos) {
        Ticket ticket = new Ticket();
        ticket.setEventoId(datos.eventoId());
        ticket.setUsuarioId(datos.usuarioId());
        ticket.setNombreEvento(datos.nombreEvento());
        ticket.setNombreAsistente(datos.nombreAsistente());
        ticket.setFechaEvento(datos.fechaEvento());
        ticket.setAsiento(datos.asiento());
        ticket.setPrecio(datos.precio());
        ticket.setEstado(datos.estado() == null ? EstadoTicket.RESERVADO : datos.estado());
        ticket.setFechaCreacion(ahora());
        ticket = repositorio.save(ticket); // se guarda primero para obtener el numero de ticket

        String nombreArchivo = String.format("ticket%03d.pdf", ticket.getId());
        ticket.setNombreArchivo(nombreArchivo);
        ticket.setClaveS3(ticket.getEventoId() + "/" + ticket.getUsuarioId() + "/" + nombreArchivo);

        byte[] contenido = pdf.generar(ticket);
        Path ruta = efs.guardar(ticket.getEventoId(), ticket.getUsuarioId(), nombreArchivo, contenido);
        ticket.setRutaEfs(ruta.toString());
        repositorio.save(ticket);

        return subirYRegistrar(ticket, contenido);
    }

    /** Sube (o vuelve a subir) a S3 el PDF que esta en el EFS. */
    public Ticket subirAS3(Long id) {
        Ticket ticket = obtener(id);
        byte[] contenido;
        if (efs.existe(ticket.getRutaEfs())) {
            contenido = efs.leer(ticket.getRutaEfs());
        } else {
            // el archivo temporal ya no esta en el EFS: se regenera a partir de los datos
            contenido = pdf.generar(ticket);
            Path ruta = efs.guardar(ticket.getEventoId(), ticket.getUsuarioId(), ticket.getNombreArchivo(), contenido);
            ticket.setRutaEfs(ruta.toString());
        }
        return subirYRegistrar(ticket, contenido);
    }

    /** Modifica los detalles, regenera el PDF en EFS y reemplaza el objeto en S3. */
    public Ticket modificar(Long id, ActualizarTicketRequest cambios) {
        Ticket ticket = obtener(id);
        if (cambios.nombreEvento() != null && !cambios.nombreEvento().isBlank()) {
            ticket.setNombreEvento(cambios.nombreEvento());
        }
        if (cambios.nombreAsistente() != null && !cambios.nombreAsistente().isBlank()) {
            ticket.setNombreAsistente(cambios.nombreAsistente());
        }
        if (cambios.fechaEvento() != null) {
            ticket.setFechaEvento(cambios.fechaEvento());
        }
        if (cambios.asiento() != null) {
            ticket.setAsiento(cambios.asiento());
        }
        if (cambios.precio() != null) {
            ticket.setPrecio(cambios.precio());
        }
        if (cambios.estado() != null) {
            ticket.setEstado(cambios.estado());
        }
        ticket.setFechaModificacion(ahora());

        byte[] contenido = pdf.generar(ticket);
        Path ruta = efs.guardar(ticket.getEventoId(), ticket.getUsuarioId(), ticket.getNombreArchivo(), contenido);
        ticket.setRutaEfs(ruta.toString());
        ticket.setSubidoAS3(false); // la copia de S3 queda desactualizada hasta que termine la subida
        repositorio.save(ticket);

        return subirYRegistrar(ticket, contenido);
    }

    /** Elimina el ticket de S3, del EFS y del registro. */
    public void eliminar(Long id) {
        Ticket ticket = obtener(id);
        s3.eliminar(ticket.getClaveS3());
        efs.eliminar(ticket.getRutaEfs());
        repositorio.delete(ticket);
    }

    public Ticket obtener(Long id) {
        return repositorio.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el ticket con id " + id));
    }

    /** Historial de tickets generados, con filtros opcionales. */
    public List<Ticket> historial(String eventoId, String usuarioId, EstadoTicket estado,
                                  LocalDateTime desde, LocalDateTime hasta) {
        String evento = vacioANull(eventoId);
        String usuario = vacioANull(usuarioId);
        // cada filtro se agrega a la consulta solo si fue enviado
        Specification<Ticket> filtro = (ticket, consulta, cb) -> {
            List<Predicate> condiciones = new ArrayList<>();
            if (evento != null) {
                condiciones.add(cb.equal(ticket.get("eventoId"), evento));
            }
            if (usuario != null) {
                condiciones.add(cb.equal(ticket.get("usuarioId"), usuario));
            }
            if (estado != null) {
                condiciones.add(cb.equal(ticket.get("estado"), estado));
            }
            if (desde != null) {
                condiciones.add(cb.greaterThanOrEqualTo(ticket.<LocalDateTime>get("fechaCreacion"), desde));
            }
            if (hasta != null) {
                condiciones.add(cb.lessThanOrEqualTo(ticket.<LocalDateTime>get("fechaCreacion"), hasta));
            }
            return cb.and(condiciones.toArray(new Predicate[0]));
        };
        return repositorio.findAll(filtro, Sort.by(Sort.Direction.DESC, "fechaCreacion", "id"));
    }

    /** Descarga desde S3 el PDF de un ticket. */
    public byte[] descargar(Ticket ticket) {
        return s3.descargar(ticket.getClaveS3());
    }

    /** Descarga desde S3 todos los tickets de un evento y/o usuario y los entrega en un ZIP. */
    public byte[] descargarZip(String eventoId, String usuarioId) {
        List<Ticket> tickets = historial(eventoId, usuarioId, null, null, null).stream()
                .filter(Ticket::isSubidoAS3)
                .toList();
        if (tickets.isEmpty()) {
            throw new RecursoNoEncontradoException("No hay tickets en S3 para el filtro indicado");
        }
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(salida)) {
            for (Ticket ticket : tickets) {
                zip.putNextEntry(new ZipEntry(ticket.getClaveS3())); // conserva evento/usuario/archivo
                zip.write(s3.descargar(ticket.getClaveS3()));
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new AlmacenamientoException("No se pudo armar el ZIP de tickets", e);
        }
        return salida.toByteArray();
    }

    public EstadisticasEvento estadisticas(String eventoId) {
        List<Ticket> tickets = repositorio.findByEventoId(eventoId);
        if (tickets.isEmpty()) {
            throw new RecursoNoEncontradoException("No hay tickets registrados para el evento " + eventoId);
        }
        return resumir(eventoId, tickets);
    }

    public List<EstadisticasEvento> estadisticasDeTodos() {
        return repositorio.eventosRegistrados().stream()
                .map(evento -> resumir(evento, repositorio.findByEventoId(evento)))
                .toList();
    }

    private EstadisticasEvento resumir(String eventoId, List<Ticket> tickets) {
        long vendidos = contar(tickets, EstadoTicket.VENDIDO);
        long reservados = contar(tickets, EstadoTicket.RESERVADO);
        long cancelados = contar(tickets, EstadoTicket.CANCELADO);
        long ingresos = sumar(tickets, EstadoTicket.VENDIDO);
        long montoReservado = sumar(tickets, EstadoTicket.RESERVADO);
        long usuarios = tickets.stream().map(Ticket::getUsuarioId).distinct().count();
        long enS3 = tickets.stream().filter(Ticket::isSubidoAS3).count();
        return new EstadisticasEvento(eventoId, tickets.get(0).getNombreEvento(), tickets.size(),
                vendidos, reservados, cancelados, ingresos, montoReservado, usuarios, enS3);
    }

    private long contar(List<Ticket> tickets, EstadoTicket estado) {
        return tickets.stream().filter(t -> t.getEstado() == estado).count();
    }

    private long sumar(List<Ticket> tickets, EstadoTicket estado) {
        return tickets.stream().filter(t -> t.getEstado() == estado).mapToLong(Ticket::getPrecio).sum();
    }

    private Ticket subirYRegistrar(Ticket ticket, byte[] contenido) {
        s3.subir(ticket.getClaveS3(), contenido);
        ticket.setSubidoAS3(true);
        ticket.setFechaSubidaS3(ahora());
        return repositorio.save(ticket);
    }

    private LocalDateTime ahora() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
    }

    private String vacioANull(String texto) {
        return texto == null || texto.isBlank() ? null : texto;
    }
}
