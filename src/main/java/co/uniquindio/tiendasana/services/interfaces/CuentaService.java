package co.uniquindio.tiendasana.services.interfaces;

import co.uniquindio.tiendasana.dto.TokenDTO;
import co.uniquindio.tiendasana.dto.cuentadtos.*;
import co.uniquindio.tiendasana.model.documents.Cuenta;
import jakarta.validation.Valid;

import java.util.Map;

public interface CuentaService {
    /**
     * Metodo que crea una cuenta apartir de la inforamción de un formulario de la vista
     * @param cuentaDTO Data transfer object que tiene la informacion del formulario de registro
     * @return Email de la cuenta
     * @throws Exception Error en caso de que ya exista una cuenta con el correo indicado
     */
    String crearCuenta(CrearCuentaDTO cuentaDTO) throws Exception;

    /**
     * Meotod para actualizar los datos de una cuenta dada la información del formulario de actualizacion
     * @param cuentaDTO Data Transfer Object con la información del formulario de actualización
     * @return Mensaje de exito
     * @throws Exception Error al acceeder a la base de datos
     */
    String actualizarCuenta(ActualizarCuentaDTO cuentaDTO, String emailAutenticado) throws Exception;

    /**
     * Metodo para eliminar una cuenta (Eliminación logica mendiante el estado de Eliminada) dado el email
     * @param email el email asociado de cada cuenta se establece como unico en el negocio
     * @return Mensaje de exito
     * @throws Exception Error al acceder a la base de datos
     */
    String eliminarCuenta(String emailAEliminar, String emailAutenticado) throws Exception;

    /**
     * Metodo para obtener la inforamción relacionada a una cuenta de un usuario dado un usario
     * @param id El email asociado de cada cuenta se establece como unico en el negocio
     * @return Informacion de la cuenta
     * @throws Exception Error al acceder a la base de datos
     * o que mas de una cuenta tenga el email indicado
     */
    InfoCuentaDTO obtenerInfoCuenta(String emailAConsultar, String emailAutenticado) throws Exception;

    /**
     * Metodo para obtener una cuenta dado el email
     * (Este no es un metodo llamado por controladores pero que apoya a los servicios que si son llamados)
     * @param email Email de la cuenta
     * @return Cuenta encontrada
     * @throws Exception Error al acceder a la base de datos o que la cuenta indicada no exista
     */
    Cuenta obtenerCuentaPorEmail(String email) throws Exception;

    /**
     * Metodo para enviar un código de recuperación de contraseña y asociarlo a la cuenta del usuario
     * @param email Email de la cuenta
     * @return Email de la cuenta
     * @throws Exception Error al acceder a la base de datos
     */
    String enviarCodigoRecuperacion(String email) throws Exception;

    /**
     * Metodo para cambiar la contraseña actual de una cuenta dado el codigo de recuperación
     * y la nueva contraseña
     * @param dto contiene el correo asociado a al cuenta, la nueva contraseña y el código de recuperación
     * @return Email de la cuenta
     * @throws Exception Error al acceder a la base de datos, que email indicado no este registrado
     * o el codigo de verificacion no sea correcto
     */
    String cambiarContrasenia(CambiarContraseniaDTO dto) throws Exception;

    String cambiarMiContrasenia(CambiarMiContraseniaDTO dto, String emailAutenticado) throws Exception;

    /**
     * Metodo para validar (Activar) una cuenta dado un código de validación en el correo de los usuarios
     * @param activarCuentaDTO, posee el correo asociado a la cuenta y el código de validación indicado por el
     *                          usuario
     * @return Mensaje de exito
     * @throws Exception Error al acceder a la base de datos, el l codigo de registro ha expirado o es incorrecto
     */
    String validarCodigoRegistro(ActivarCuentaDTO activarCuentaDTO) throws Exception;

    /**
     * Metodo el cual es usado para reenviar y asociar un nuevo código de valicdación en caso de que el usuario no
     * pudiera validar su cuenta ocn el primer código enviado en el registro
     * @param email, correo asociado a una cuenta, se verificara si la cuenta ya está activa antes de reasignar
     * @return Email de la cuenta
     * @throws Exception Error al acceder a la base de datos o la cuenta ya ha sido activada
     */
    String reenviarCodigoRegistro(String email) throws Exception;

    /**
     * Metodo para iniciar sesión el cual devuelve un JWT con los datos de la cuenta que esta iniciando sesión para
     * que sea almacenada en el sessión storage del frontend
     * @param loginDTO DTO con los datos para poder iniciar sesion
     * @return Token JWT con los datos de la cuenta
     * @throws Exception La contraseña de la cuenta es incorrecta, no existe una cuenta con con el
     * correo indicada o no ha sido activada
     */
    TokenDTO login(@Valid LoginDTO loginDTO) throws Exception;

    //Metodo proximo a implementar
    TokenDTO refresh(Map<String, Object> claims) throws Exception;
}
