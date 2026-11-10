package cl.duoc.tickets.dto;

/** Resumen de ventas y reservas de un evento. */
public record EstadisticasEvento(
        String eventoId,
        String nombreEvento,
        long totalTickets,
        long vendidos,
        long reservados,
        long cancelados,
        long ingresosVentas,
        long montoReservado,
        long usuariosDistintos,
        long ticketsEnS3
) {
}
