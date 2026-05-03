package co.uniquindio.tiendasana.dto.cuentadtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Data tranfer object para recibir información de creacion de la cuenta
 * @param nombre
 * @param telefono
 * @param direccion
 * @param email
 * @param contrasenia
 */
public record CrearCuentaDTO(
        @NotBlank(message = "El campo de nombre es obligatorio")
        @Size(max = 50, message = "El nombre debe de tener máximo 50 caracteres")
        String nombre,
        @NotBlank(message = "El campo de telefono es obligatorio")
        @Size(min = 10, max = 15, message = "El numero de telefono debe de ser entre 10 y 15 caracteres de largo")
        String telefono,
        @NotBlank(message = "El campo de dirección es obligatorio")
        @Size(max = 255, message = "La direccion tiene como máximo 255 caracteres")
        String direccion,
        @NotBlank(message = "Campo Email no puede estar vacio")
        @Email(message = "Formato de email invalido")
        String email,
        @NotBlank(message = "La contraseña es obligatoria")
        @Size(min = 7, max = 20, message = "La contraseña debe de tener entre 7 y 20 caracteres")
        String contrasenia
) {
}
