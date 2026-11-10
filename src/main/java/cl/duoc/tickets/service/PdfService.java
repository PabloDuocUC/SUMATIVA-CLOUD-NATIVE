package cl.duoc.tickets.service;

import cl.duoc.tickets.model.Ticket;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;

/** Genera el PDF personalizado de un ticket. */
@Service
public class PdfService {

    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");

    public byte[] generar(Ticket ticket) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document documento = new Document(PageSize.A5.rotate(), 36, 36, 36, 36);
        PdfWriter.getInstance(documento, salida);
        documento.open();

        Font titulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20);
        Font subtitulo = FontFactory.getFont(FontFactory.HELVETICA, 11, Color.DARK_GRAY);
        Font etiqueta = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);
        Font valor = FontFactory.getFont(FontFactory.HELVETICA, 11);

        Paragraph encabezado = new Paragraph("TICKET DE EVENTO", titulo);
        encabezado.setAlignment(Element.ALIGN_CENTER);
        documento.add(encabezado);

        Paragraph evento = new Paragraph(ticket.getNombreEvento(), subtitulo);
        evento.setAlignment(Element.ALIGN_CENTER);
        evento.setSpacingAfter(16);
        documento.add(evento);

        PdfPTable tabla = new PdfPTable(new float[]{1, 2});
        tabla.setWidthPercentage(100);
        fila(tabla, "Nro. de ticket", String.format("%03d", ticket.getId()), etiqueta, valor);
        fila(tabla, "Evento", ticket.getEventoId(), etiqueta, valor);
        fila(tabla, "Usuario", ticket.getUsuarioId(), etiqueta, valor);
        fila(tabla, "Asistente", ticket.getNombreAsistente(), etiqueta, valor);
        fila(tabla, "Fecha del evento",
                ticket.getFechaEvento() == null ? "Por confirmar" : ticket.getFechaEvento().format(FORMATO),
                etiqueta, valor);
        fila(tabla, "Asiento", ticket.getAsiento() == null ? "General" : ticket.getAsiento(), etiqueta, valor);
        fila(tabla, "Precio", "$" + String.format("%,d", ticket.getPrecio()).replace(',', '.'), etiqueta, valor);
        fila(tabla, "Estado", ticket.getEstado().name(), etiqueta, valor);
        fila(tabla, "Emitido", ticket.getFechaCreacion().format(FORMATO), etiqueta, valor);
        if (ticket.getFechaModificacion() != null) {
            fila(tabla, "Ultima modificacion", ticket.getFechaModificacion().format(FORMATO), etiqueta, valor);
        }
        documento.add(tabla);

        documento.close();
        return salida.toByteArray();
    }

    private void fila(PdfPTable tabla, String nombre, String contenido, Font etiqueta, Font valor) {
        PdfPCell izquierda = new PdfPCell(new Phrase(nombre, etiqueta));
        PdfPCell derecha = new PdfPCell(new Phrase(contenido, valor));
        izquierda.setPadding(6);
        derecha.setPadding(6);
        izquierda.setBackgroundColor(new Color(240, 240, 240));
        tabla.addCell(izquierda);
        tabla.addCell(derecha);
    }
}
