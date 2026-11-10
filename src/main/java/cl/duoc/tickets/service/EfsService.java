package cl.duoc.tickets.service;

import cl.duoc.tickets.exception.AlmacenamientoException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * Almacenamiento temporal en Amazon EFS.
 *
 * El EFS se monta en la instancia EC2 (/mnt/efs) y se comparte con el contenedor
 * (/app/efs), por lo que para la aplicacion es una carpeta mas del sistema de
 * archivos. Los tickets quedan organizados como tickets/{eventoId}/{usuarioId}/archivo.pdf.
 */
@Service
public class EfsService {

    private final Path raiz;

    public EfsService(@Value("${app.efs.ruta}") String ruta) {
        this.raiz = Path.of(ruta, "tickets").toAbsolutePath().normalize();
    }

    /** Guarda (o reemplaza) el PDF en el EFS y devuelve su ruta. */
    public Path guardar(String eventoId, String usuarioId, String nombreArchivo, byte[] contenido) {
        Path destino = resolver(eventoId, usuarioId, nombreArchivo);
        try {
            Files.createDirectories(destino.getParent());
            Files.write(destino, contenido);
            return destino;
        } catch (IOException e) {
            throw new AlmacenamientoException("No se pudo escribir el ticket en EFS: " + destino, e);
        }
    }

    public boolean existe(String ruta) {
        return ruta != null && Files.isRegularFile(Path.of(ruta));
    }

    public byte[] leer(String ruta) {
        try {
            return Files.readAllBytes(Path.of(ruta));
        } catch (IOException e) {
            throw new AlmacenamientoException("No se pudo leer el ticket desde EFS: " + ruta, e);
        }
    }

    public void eliminar(String ruta) {
        if (ruta == null) {
            return;
        }
        try {
            Files.deleteIfExists(Path.of(ruta));
        } catch (IOException e) {
            throw new AlmacenamientoException("No se pudo eliminar el ticket del EFS: " + ruta, e);
        }
    }

    /** Lista las rutas (relativas a la carpeta tickets) de todos los archivos que hay en el EFS. */
    public List<String> listarArchivos() {
        if (!Files.isDirectory(raiz)) {
            return List.of();
        }
        try (Stream<Path> archivos = Files.walk(raiz)) {
            return archivos.filter(Files::isRegularFile)
                    .map(p -> raiz.relativize(p).toString().replace('\\', '/'))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new AlmacenamientoException("No se pudo listar el contenido del EFS", e);
        }
    }

    public String rutaRaiz() {
        return raiz.toString();
    }

    private Path resolver(String eventoId, String usuarioId, String nombreArchivo) {
        Path destino = raiz.resolve(eventoId).resolve(usuarioId).resolve(nombreArchivo).normalize();
        if (!destino.startsWith(raiz)) {
            throw new IllegalArgumentException("Ruta fuera del EFS");
        }
        return destino;
    }
}
