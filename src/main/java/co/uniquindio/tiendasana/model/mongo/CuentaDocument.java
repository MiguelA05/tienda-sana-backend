package co.uniquindio.tiendasana.model.mongo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "accounts")
public class CuentaDocument {

    @Id
    private String email;

    private String nombre;
    private String telefono;
    private String direccion;

    private String contrasenia;
    private String rol;
    private String estado;
    private LocalDateTime fechaRegistro;

    private String codigoRegistro;
    private LocalDateTime fechaCodigoRegistro;
    private String codigoContrasenia;
    private LocalDateTime fechaCodigoContrasenia;
}
