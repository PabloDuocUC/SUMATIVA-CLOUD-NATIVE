package cl.duoc.tickets;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prueba de punta a punta de todos los endpoints. El EFS se reemplaza por una
 * carpeta temporal y S3 por un bucket en memoria, asi el pipeline puede validar
 * la aplicacion sin credenciales de AWS.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TicketsApiTest {

    @TempDir
    static Path carpetaEfs;

    static final Map<String, byte[]> BUCKET = new ConcurrentHashMap<>();

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) {
        registro.add("app.efs.ruta", () -> carpetaEfs.toString());
        registro.add("spring.datasource.url", () -> "jdbc:h2:mem:pruebas;DB_CLOSE_DELAY=-1");
    }

    @TestConfiguration
    static class S3EnMemoria {
        @Bean
        @Primary
        S3Client s3ClientDePrueba() {
            return new S3Client() {
                @Override
                public String serviceName() {
                    return "s3";
                }

                @Override
                public void close() {
                }

                @Override
                public PutObjectResponse putObject(PutObjectRequest peticion, RequestBody cuerpo) {
                    try {
                        BUCKET.put(peticion.key(), cuerpo.contentStreamProvider().newStream().readAllBytes());
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                    return PutObjectResponse.builder().build();
                }

                @Override
                public ResponseBytes<GetObjectResponse> getObjectAsBytes(GetObjectRequest peticion) {
                    byte[] contenido = BUCKET.get(peticion.key());
                    if (contenido == null) {
                        throw NoSuchKeyException.builder().message("no existe").build();
                    }
                    return ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), contenido);
                }

                @Override
                public DeleteObjectResponse deleteObject(DeleteObjectRequest peticion) {
                    BUCKET.remove(peticion.key());
                    return DeleteObjectResponse.builder().build();
                }
            };
        }
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Test
    void flujoCompletoDeTickets() throws Exception {
        // 1. Generar: queda en EFS y se sube solo a S3, ordenado por evento/usuario
        JsonNode t1 = crear("evento123", "usuarioX", "Ana Rojas", 25000, "VENDIDO");
        JsonNode t2 = crear("evento123", "usuarioY", "Luis Soto", 25000, "RESERVADO");
        JsonNode t3 = crear("evento456", "usuarioX", "Ana Rojas", 40000, "VENDIDO");

        long id1 = t1.get("id").asLong();
        String clave1 = t1.get("claveS3").asText();
        assertThat(clave1).isEqualTo(String.format("evento123/usuarioX/ticket%03d.pdf", id1));
        assertThat(t1.get("subidoAS3").asBoolean()).isTrue();
        assertThat(BUCKET).containsKey(clave1);
        assertThat(esPdf(BUCKET.get(clave1))).isTrue();
        Path enEfs = carpetaEfs.resolve("tickets").resolve(clave1);
        assertThat(Files.isRegularFile(enEfs)).isTrue();
        assertThat(Files.readAllBytes(enEfs)).isEqualTo(BUCKET.get(clave1));

        // 2. Subir manualmente a S3 (se borra del bucket y se vuelve a subir desde el EFS)
        BUCKET.remove(clave1);
        mvc.perform(post("/api/tickets/{id}/subir", id1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subidoAS3").value(true));
        assertThat(BUCKET).containsKey(clave1);

        // 3. Descargar un ticket desde S3: llega integro
        byte[] descargado = mvc.perform(get("/api/tickets/{id}/descargar", id1))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(descargado).isEqualTo(BUCKET.get(clave1));

        // 4. Descargar por evento y por usuario (ZIP con la estructura de carpetas)
        assertThat(entradasZip("/api/tickets/descargar?eventoId=evento123"))
                .containsExactlyInAnyOrder(clave1, t2.get("claveS3").asText());
        assertThat(entradasZip("/api/tickets/descargar?usuarioId=usuarioX"))
                .containsExactlyInAnyOrder(clave1, t3.get("claveS3").asText());
        mvc.perform(get("/api/tickets/descargar")).andExpect(status().isBadRequest());

        // 5. Modificar: cambia el registro y se reemplaza el archivo en EFS y en S3
        byte[] antes = BUCKET.get(clave1);
        mvc.perform(put("/api/tickets/{id}", id1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"asiento\":\"VIP-01\",\"precio\":30000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.asiento").value("VIP-01"))
                .andExpect(jsonPath("$.precio").value(30000))
                .andExpect(jsonPath("$.nombreAsistente").value("Ana Rojas"))
                .andExpect(jsonPath("$.claveS3").value(clave1))
                .andExpect(jsonPath("$.subidoAS3").value(true));
        assertThat(BUCKET.get(clave1)).isNotEqualTo(antes);
        assertThat(Files.readAllBytes(enEfs)).isEqualTo(BUCKET.get(clave1));

        // 6. Historial con filtros
        mvc.perform(get("/api/tickets")).andExpect(jsonPath("$.length()").value(3));
        mvc.perform(get("/api/tickets?eventoId=evento123")).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/tickets?usuarioId=usuarioX")).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/tickets?eventoId=evento123&usuarioId=usuarioX"))
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/tickets?estado=RESERVADO")).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/tickets?desde=2000-01-01&hasta=2100-01-01"))
                .andExpect(jsonPath("$.length()").value(3));
        mvc.perform(get("/api/tickets?desde=2100-01-01")).andExpect(jsonPath("$.length()").value(0));

        // 7. Estadisticas por evento
        mvc.perform(get("/api/eventos/evento123/estadisticas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTickets").value(2))
                .andExpect(jsonPath("$.vendidos").value(1))
                .andExpect(jsonPath("$.reservados").value(1))
                .andExpect(jsonPath("$.ingresosVentas").value(30000))
                .andExpect(jsonPath("$.montoReservado").value(25000))
                .andExpect(jsonPath("$.usuariosDistintos").value(2))
                .andExpect(jsonPath("$.ticketsEnS3").value(2));
        mvc.perform(get("/api/eventos/estadisticas")).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/eventos/noexiste/estadisticas")).andExpect(status().isNotFound());

        // 8. Archivos presentes en el EFS
        mvc.perform(get("/api/efs/archivos")).andExpect(jsonPath("$.total").value(3));

        // 9. Eliminar: desaparece de S3, del EFS y del historial
        mvc.perform(delete("/api/tickets/{id}", id1)).andExpect(status().isNoContent());
        assertThat(BUCKET).doesNotContainKey(clave1);
        assertThat(Files.exists(enEfs)).isFalse();
        mvc.perform(get("/api/tickets/{id}", id1)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/tickets/{id}", id1)).andExpect(status().isNotFound());
        mvc.perform(get("/api/tickets")).andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void rechazaDatosInvalidos() throws Exception {
        // un eventoId con "/" o ".." podria escribir fuera de la carpeta del evento
        mvc.perform(post("/api/tickets").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventoId\":\"../otro\",\"usuarioId\":\"u1\",\"nombreEvento\":\"E\","
                                + "\"nombreAsistente\":\"A\",\"precio\":1000}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalle.eventoId").exists());
        mvc.perform(post("/api/tickets").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventoId\":\"e1\",\"usuarioId\":\"u1\",\"nombreEvento\":\"E\","
                                + "\"nombreAsistente\":\"A\",\"precio\":-5}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/tickets").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventoId\":\"e1\",\"usuarioId\":\"u1\",\"nombreEvento\":\"E\","
                                + "\"nombreAsistente\":\"A\",\"precio\":1000,\"estado\":\"REGALADO\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/tickets/999999/descargar")).andExpect(status().isNotFound());
    }

    private JsonNode crear(String evento, String usuario, String asistente, int precio, String estado)
            throws Exception {
        String cuerpo = json.writeValueAsString(Map.of(
                "eventoId", evento,
                "usuarioId", usuario,
                "nombreEvento", "Concierto " + evento,
                "nombreAsistente", asistente,
                "fechaEvento", "2026-12-05T21:00:00",
                "asiento", "A-10",
                "precio", precio,
                "estado", estado));
        String respuesta = mvc.perform(post("/api/tickets")
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(respuesta);
    }

    private List<String> entradasZip(String url) throws Exception {
        byte[] zip = mvc.perform(get(url)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        List<String> nombres = new ArrayList<>();
        try (ZipInputStream entrada = new ZipInputStream(new ByteArrayInputStream(zip))) {
            ZipEntry e;
            while ((e = entrada.getNextEntry()) != null) {
                nombres.add(e.getName());
                assertThat(esPdf(entrada.readAllBytes())).isTrue();
            }
        }
        return nombres;
    }

    private boolean esPdf(byte[] contenido) {
        return new String(contenido, 0, 5, StandardCharsets.ISO_8859_1).equals("%PDF-");
    }
}
