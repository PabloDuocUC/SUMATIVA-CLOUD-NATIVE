package cl.duoc.tickets.controller;

import cl.duoc.tickets.dto.EstadisticasEvento;
import cl.duoc.tickets.service.TicketService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Estadisticas de ventas y reservas por evento. */
@RestController
@RequestMapping("/api/eventos")
public class EventoController {

    private final TicketService servicio;

    public EventoController(TicketService servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/estadisticas")
    public List<EstadisticasEvento> deTodos() {
        return servicio.estadisticasDeTodos();
    }

    @GetMapping("/{eventoId}/estadisticas")
    public EstadisticasEvento deUnEvento(@PathVariable String eventoId) {
        return servicio.estadisticas(eventoId);
    }
}
