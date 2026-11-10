package cl.duoc.tickets.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Cliente de Amazon S3.
 *
 * No se escriben credenciales en el codigo ni en el repositorio: el SDK usa la
 * cadena de credenciales por defecto. En EC2 las obtiene del rol IAM asociado a
 * la instancia (LabRole / LabInstanceProfile en AWS Academy); en local, de las
 * variables de entorno AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY y AWS_SESSION_TOKEN.
 */
@Configuration
public class S3Config {

    @Bean
    public S3Client s3Client(@Value("${app.s3.region}") String region) {
        return S3Client.builder()
                .region(Region.of(region))
                .build();
    }
}
