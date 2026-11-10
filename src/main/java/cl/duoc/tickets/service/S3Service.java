package cl.duoc.tickets.service;

import cl.duoc.tickets.exception.AlmacenamientoException;
import cl.duoc.tickets.exception.RecursoNoEncontradoException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/** Operaciones sobre el bucket de Amazon S3 donde quedan los tickets definitivos. */
@Service
public class S3Service {

    private final S3Client s3;
    private final String bucket;

    public S3Service(S3Client s3, @Value("${app.s3.bucket}") String bucket) {
        this.s3 = s3;
        this.bucket = bucket;
    }

    /** Sube el archivo; si la clave ya existe, S3 lo reemplaza (asi se actualiza un ticket). */
    public void subir(String clave, byte[] contenido) {
        try {
            s3.putObject(PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(clave)
                            .contentType("application/pdf")
                            .build(),
                    RequestBody.fromBytes(contenido));
        } catch (SdkException e) {
            throw new AlmacenamientoException("No se pudo subir a S3: s3://" + bucket + "/" + clave, e);
        }
    }

    public byte[] descargar(String clave) {
        try {
            return s3.getObjectAsBytes(GetObjectRequest.builder()
                            .bucket(bucket)
                            .key(clave)
                            .build())
                    .asByteArray();
        } catch (NoSuchKeyException e) {
            throw new RecursoNoEncontradoException("El archivo no existe en S3: " + clave);
        } catch (SdkException e) {
            throw new AlmacenamientoException("No se pudo descargar desde S3: s3://" + bucket + "/" + clave, e);
        }
    }

    public void eliminar(String clave) {
        try {
            s3.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(clave)
                    .build());
        } catch (SdkException e) {
            throw new AlmacenamientoException("No se pudo eliminar de S3: s3://" + bucket + "/" + clave, e);
        }
    }

    public String bucket() {
        return bucket;
    }
}
