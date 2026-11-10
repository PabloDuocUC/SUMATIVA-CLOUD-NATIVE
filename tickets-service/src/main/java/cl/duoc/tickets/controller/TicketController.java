package cl.duoc.tickets.controller;

import cl.duoc.tickets.dto.ActualizarTicketRequest;
import cl.duoc.tickets.dto.CrearTicketRequest;
import cl.duoc.tickets.model.EstadoTicket;
import cl.duoc.tickets.model.Ticket;
import cl.duoc.tickets.service.TicketService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketService servicio;

    public TicketController(TicketService servicio) {
        this.servicio = servicio;
    }

    /** Generar ticket: crea el PDF en EFS y lo sube automaticamente a S3. */
    @PostMapping
    public ResponseEntity<Ticket> generar(@Valid @RequestBody CrearTicketRequest datos) {
        return ResponseEntity.status(HttpStatus.CREATED).body(servicio.generar(datos));
    }

    /** Subir a S3 el ticket que esta en el EFS (subida o re-subida manual). */
    @PostMapping("/{id}/subir")
    public Ticket subirAS3(@PathVariable Long id) {
        return servicio.subirAS3(id);
    }

    /** Historial de tickets generados. Filtros opcionales: evento, usuario, estado y rango de fechas. */
    @GetMapping
    public List<Ticket> historial(
            @RequestParam(required = false) String eventoId,
            @RequestParam(required = false) String usuarioId,
            @RequestParam(required = false) EstadoTicket estado,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return servicio.historial(eventoId, usuarioId, estado,
                desde == null ? null : desde.atStartOfDay(),
                hasta == null ? null : hasta.atTime(23, 59, 59));
    }

    @GetMapping("/{id}")
    public Ticket obtener(@PathVariable Long id) {
        return servicio.obtener(id);
    }

    /** Descargar desde S3 el PDF de un ticket. */
    @GetMapping("/{id}/descargar")
    public ResponseEntity<byte[]> descargar(@PathVariable Long id) {
        Ticket ticket = servicio.obtener(id);
        return archivo(servicio.descargar(ticket), ticket.getNombreArchivo(), MediaType.APPLICATION_PDF);
    }

    /** Descargar desde S3 todos los tickets de un usuario o de un evento, en un ZIP. */
    @GetMapping("/descargar")
    public ResponseEntity<byte[]> descargarPorFiltro(
            @RequestParam(required = false) String eventoId,
            @RequestParam(required = false) String usuarioId) {
        if ((eventoId == null || eventoId.isBlank()) && (usuarioId == null || usuarioId.isBlank())) {
            throw new IllegalArgumentException("Debe indicar eventoId o usuarioId");
        }
        String nombre = "tickets"
                + (eventoId == null || eventoId.isBlank() ? "" : "-" + eventoId)
                + (usuarioId == null || usuarioId.isBlank() ? "" : "-" + usuarioId)
                + ".zip";
        return archivo(servicio.descargarZip(eventoId, usuarioId), nombre,
                MediaType.parseMediaType("application/zip"));
    }

    /** Modificar detalles del ticket: regenera el PDF y actualiza el archivo en S3. */
    @PutMapping("/{id}")
    public Ticket modificar(@PathVariable Long id, @Valid @RequestBody ActualizarTicketRequest cambios) {
        return servicio.modificar(id, cambios);
    }

    /** Eliminar un ticket especifico (S3, EFS y registro). */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        servicio.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<byte[]> archivo(byte[] contenido, String nombre, MediaType tipo) {
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.setContentType(tipo);
        cabeceras.setContentDisposition(ContentDisposition.attachment().filename(nombre).build());
        return ResponseEntity.ok().headers(cabeceras).body(contenido);
    }
}
