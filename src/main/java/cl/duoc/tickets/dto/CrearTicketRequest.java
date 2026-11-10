package cl.duoc.tickets.dto;

import cl.duoc.tickets.model.EstadoTicket;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDateTime;

/**
 * Datos para generar un ticket. eventoId y usuarioId forman la ruta de carpetas
 * en EFS y en S3, por eso solo aceptan letras, numeros, guion y guion bajo.
 */
public record CrearTicketRequest(
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9_-]{1,50}$",
                message = "solo letras, numeros, guion y guion bajo (maximo 50)")
        String eventoId,

        @NotBlank @Pattern(regexp = "^[A-Za-z0-9_-]{1,50}$",
                message = "solo letras, numeros, guion y guion bajo (maximo 50)")
        String usuarioId,

        @NotBlank String nombreEvento,
        @NotBlank String nombreAsistente,
        LocalDateTime fechaEvento,
        String asiento,
        @NotNull @Min(0) Integer precio,
        EstadoTicket estado
) {
}
