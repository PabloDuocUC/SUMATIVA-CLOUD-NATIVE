package cl.duoc.tickets.exception;

/** Falla al leer o escribir en EFS o en S3. */
public class AlmacenamientoException extends RuntimeException {
    public AlmacenamientoException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
