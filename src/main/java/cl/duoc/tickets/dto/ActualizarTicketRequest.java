package cl.duoc.tickets.dto;

import cl.duoc.tickets.model.EstadoTicket;
import jakarta.validation.constraints.Min;

import java.time.LocalDateTime;

/**
 * Detalles modificables de un ticket. Solo se cambian los campos enviados;
 * los que vienen en null conservan su valor. eventoId y usuarioId no se
 * modifican porque definen la ubicacion del archivo en EFS y S3.
 */
public record ActualizarTicketRequest(
        String nombreEvento,
        String nombreAsistente,
        LocalDateTime fechaEvento,
        String asiento,
        @Min(0) Integer precio,
        EstadoTicket estado
) {
}
