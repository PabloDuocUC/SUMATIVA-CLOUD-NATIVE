package cl.duoc.tickets.controller;

import cl.duoc.tickets.service.EfsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Permite verificar que archivos hay en el almacenamiento temporal (EFS). */
@RestController
@RequestMapping("/api/efs")
public class EfsController {

    private final EfsService efs;

    public EfsController(EfsService efs) {
        this.efs = efs;
    }

    @GetMapping("/archivos")
    public Map<String, Object> archivos() {
        List<String> archivos = efs.listarArchivos();
        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("rutaEfs", efs.rutaRaiz());
        respuesta.put("total", archivos.size());
        respuesta.put("archivos", archivos);
        return respuesta;
    }
}
