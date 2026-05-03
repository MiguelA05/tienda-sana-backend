package co.uniquindio.tiendasana.dto.cuentadtos;

import co.uniquindio.tiendasana.model.enums.Rol;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.Length;

/**
 * Data transfer object para mostrar informacion de la cuenta cuando sea solicitado
 * @param email
 * @param nombre
 * @param telefono
 * @param direccion
 */
public record InfoCuentaDTO(
        @NotBlank(message = "Email no puede estar vacio")
        @Email(message = "Formato del email no es valido")
        String email,

        @NotBlank(message = "The name cannot be empty")
        @Size(min = 1, max = 100, message = "The name must be between 1 and 100 characters")
        String nombre,

        @Size(min = 10, max = 15, message = "The phone number must be between 10 and 15 characters long")
        String telefono,

        @Size(max = 255, message = "The address cannot exceed 255 characters")
        String direccion


) {
}
