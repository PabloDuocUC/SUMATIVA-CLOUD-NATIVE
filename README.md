# Tickets Service

Sistema de Gestión de Reservas de Eventos con Tickets.
Desarrollo Cloud Native.

Microservicio Spring Boot que genera tickets en PDF, los guarda temporalmente en **Amazon EFS**, los sube automáticamente a **Amazon S3** organizados por evento y usuario, y se despliega en **EC2** mediante un pipeline de **GitHub Actions** que publica la imagen en **Docker Hub**.

## Video Demostrativo:

[Ver video de la evaluación](https://youtu.be/j80cthBL-B8)

## Arquitectura

```
git push (main)
      │
      ▼
GitHub Actions ──► pruebas automáticas (mvn test)
      │
      ├─► docker build + push ──► Docker Hub (usuario/tickets-service:latest)
      │
      └─► SSH a EC2 ──► monta EFS en /mnt/efs ──► docker run -v /mnt/efs:/app/efs

Cliente (Postman) ──► EC2 :8080 ──► contenedor tickets-service
                                        │
                                        ├─► /app/efs/tickets/{evento}/{usuario}/ticketNNN.pdf   (EFS, temporal)
                                        └─► s3://bucket/{evento}/{usuario}/ticketNNN.pdf         (S3, definitivo)
```

Flujo de un ticket:

1. `POST /api/tickets` registra el ticket y genera su PDF.
2. El PDF se escribe en el EFS montado en el contenedor (`/app/efs`).
3. El mismo archivo se sube automáticamente a S3 con la clave `eventoId/usuarioId/ticketNNN.pdf`.
4. Al modificar un ticket, el PDF se regenera en EFS y reemplaza al objeto en S3.

## Endpoints

| Método | Ruta | Descripción |
|---|---|---|
| POST | `/api/tickets` | Genera un ticket (PDF en EFS + subida automática a S3) |
| POST | `/api/tickets/{id}/subir` | Sube a S3 el ticket que está en EFS |
| GET | `/api/tickets/{id}/descargar` | Descarga el PDF de un ticket desde S3 |
| GET | `/api/tickets/descargar?eventoId=&usuarioId=` | Descarga desde S3 los tickets de un evento o usuario (ZIP) |
| PUT | `/api/tickets/{id}` | Modifica los detalles y actualiza el archivo en EFS y S3 |
| DELETE | `/api/tickets/{id}` | Elimina el ticket de S3, del EFS y del registro |
| GET | `/api/tickets?eventoId=&usuarioId=&estado=&desde=&hasta=` | Historial de tickets generados, con filtros |
| GET | `/api/tickets/{id}` | Detalle de un ticket |
| GET | `/api/eventos/{eventoId}/estadisticas` | Ventas y reservas de un evento |
| GET | `/api/eventos/estadisticas` | Ventas y reservas de todos los eventos |
| GET | `/api/efs/archivos` | Archivos presentes en el EFS |

Ejemplo de cuerpo para generar un ticket:

```json
{
  "eventoId": "evento123",
  "usuarioId": "usuarioX",
  "nombreEvento": "Festival Cloud Native 2026",
  "nombreAsistente": "Ana Rojas",
  "fechaEvento": "2026-12-05T21:00:00",
  "asiento": "A-10",
  "precio": 25000,
  "estado": "VENDIDO"
}
```

`estado` acepta `RESERVADO` (por defecto), `VENDIDO` o `CANCELADO`.
`eventoId` y `usuarioId` solo aceptan letras, números, guion y guion bajo, porque forman la ruta de carpetas en EFS y S3.

La colección de Postman con todas las pruebas está en `postman/Tickets-Service.postman_collection.json`.

## Estructura del proyecto

```
src/main/java/cl/duoc/tickets
├── controller   TicketController, EventoController, EfsController
├── service      TicketService (lógica), PdfService, EfsService, S3Service
├── repository   TicketRepository (Spring Data JPA)
├── model        Ticket, EstadoTicket
├── dto          CrearTicketRequest, ActualizarTicketRequest, EstadisticasEvento
├── exception    Manejo centralizado de errores
└── config       S3Config (cliente de AWS SDK v2)
```

## Configuración

| Variable | Valor por defecto | Uso |
|---|---|---|
| `EFS_PATH` | `/app/efs` | Carpeta donde el contenedor ve el EFS |
| `S3_BUCKET` | `tickets-eventos-bucket` | Bucket de destino |
| `AWS_REGION` | `us-east-1` | Región del bucket |
| `DATA_PATH` | `/app/data` | Base H2 con los metadatos de los tickets |

Las credenciales de AWS no están en el código ni en el repositorio: la instancia EC2 usa un rol IAM (`LabInstanceProfile`) y el SDK las obtiene de ahí.

## Pipeline CI/CD

Archivo: `.github/workflows/main.yml`. Se ejecuta en cada push a `main`.

1. **pruebas**: ejecuta los tests de todos los endpoints (EFS y S3 simulados).
2. **desplegar**: construye la imagen, la publica en Docker Hub, se conecta por SSH a la EC2, monta el EFS si no está montado y levanta el contenedor con `-v /mnt/efs:/app/efs`. Termina verificando que la aplicación responde.

Secrets requeridos en GitHub:

| Secret | Contenido |
|---|---|
| `DOCKERHUB_USERNAME` | Usuario de Docker Hub |
| `DOCKERHUB_TOKEN` | Token de acceso de Docker Hub |
| `EC2_HOST` | IP pública de la instancia EC2 |
| `EC2_SSH_KEY` | Contenido completo de la llave privada `.pem` |
| `EFS_DNS` | DNS del sistema de archivos, por ejemplo `fs-xxxx.efs.us-east-1.amazonaws.com` |
| `S3_BUCKET` | Nombre del bucket |

## Ejecución local (opcional)

```bash
mvn test                      # pruebas, no requiere AWS
docker build -t tickets-service .
docker run -p 8080:8080 -v $(pwd)/efs:/app/efs -v $(pwd)/data:/app/data \
  -e S3_BUCKET=mi-bucket -e AWS_ACCESS_KEY_ID=... -e AWS_SECRET_ACCESS_KEY=... -e AWS_SESSION_TOKEN=... \
  tickets-service
```
Entrega: Pablo Gallardo
