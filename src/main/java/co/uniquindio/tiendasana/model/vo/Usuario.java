package co.uniquindio.tiendasana.model.vo;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@ToString
public class Usuario {
    private String nombre;
    private String direccion;
    private String telefono;

    @Builder
    private Usuario(String nombre, String direccion, String telefono) {
        this.nombre = nombre;
        this.direccion = direccion;
        this.telefono = telefono;
    }
}
