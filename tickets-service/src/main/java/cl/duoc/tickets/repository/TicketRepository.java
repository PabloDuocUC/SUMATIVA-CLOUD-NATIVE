package cl.duoc.tickets.repository;

import cl.duoc.tickets.model.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/**
 * Acceso a los metadatos de los tickets. JpaSpecificationExecutor permite armar
 * la consulta del historial con filtros opcionales.
 */
public interface TicketRepository extends JpaRepository<Ticket, Long>, JpaSpecificationExecutor<Ticket> {

    List<Ticket> findByEventoId(String eventoId);

    @Query("select distinct t.eventoId from Ticket t order by t.eventoId")
    List<String> eventosRegistrados();
}
