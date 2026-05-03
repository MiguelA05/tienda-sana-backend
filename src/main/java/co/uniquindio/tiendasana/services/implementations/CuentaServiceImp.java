package co.uniquindio.tiendasana.services.implementations;

import co.uniquindio.tiendasana.config.JWTUtils;
import co.uniquindio.tiendasana.dto.EmailDTO;
import co.uniquindio.tiendasana.dto.TokenDTO;
import co.uniquindio.tiendasana.repos.CuentaRepo;
import lombok.AllArgsConstructor;
import org.apache.velocity.exception.ResourceNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import co.uniquindio.tiendasana.model.documents.Cuenta;
import co.uniquindio.tiendasana.model.enums.EstadoCuenta;
import co.uniquindio.tiendasana.model.enums.Rol;
import co.uniquindio.tiendasana.model.vo.Usuario;
import co.uniquindio.tiendasana.model.vo.CodigoValidacion;
import co.uniquindio.tiendasana.dto.cuentadtos.*;

import co.uniquindio.tiendasana.exceptions.*;
import co.uniquindio.tiendasana.services.interfaces.CuentaService;
import co.uniquindio.tiendasana.services.interfaces.EmailService;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import javax.xml.transform.sax.SAXSource;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

@Service
@AllArgsConstructor
public class CuentaServiceImp implements CuentaService {
    /**
     * Variables y constantes necesarias para el funcionamiento del servicio
     */
    private final CuentaRepo cuentaRepo;
    private final EmailService emailService;
    private JWTUtils jwtUtils;

    /**
     * Metodo que crea una cuenta apartir de la inforamción de un formulario de la vista
     * @param cuentaDTO Data transfer object que tiene la informacion del formulario de registro
     * @return Email de la cuenta
     * @throws Exception Error en caso de que ya exista una cuenta con el correo indicado
     */
    @Override
    public String crearCuenta(CrearCuentaDTO cuentaDTO) throws Exception {
        List<Cuenta> cuentasObtenidas =
                cuentaRepo.obtenerPorEmailMongo(cuentaDTO.email());

        if (!cuentasObtenidas.isEmpty()) {
            Cuenta cuentaExistente = cuentasObtenidas.get(0);
            if (cuentaExistente.getEstado() == EstadoCuenta.ELIMINADA) {
                cuentaExistente.setEstado(EstadoCuenta.INACTIVA);
                String codigoValidacion = generarCodigoValidacion();
                cuentaExistente.setCodigoValidacionRegistro(new CodigoValidacion(LocalDateTime.now(), codigoValidacion));
                String subject = "Bienvenido nuevamente a Tienda Sana: activa tu cuenta";
                String body = "Tu nuevo código de verificación es: " + codigoValidacion + ". Tienes 15 minutos para activarla.";
                emailService.sendEmail(new EmailDTO(subject, body, cuentaExistente.getEmail()));
                cuentaRepo.actualizar(cuentaExistente);
                return cuentaExistente.getEmail();
            }
            throw new Exception("Ya existe una cuenta con ese correo");
        }
        String contraseniaEncriptada = encriptarContrasenia(cuentaDTO.contrasenia());
        String codigoValidacion = generarCodigoValidacion();
        String codigoValidacionContrasenia = generarCodigoValidacion();
        Cuenta cuenta = Cuenta.builder()
                .email(cuentaDTO.email())
                .contrasenia(contraseniaEncriptada)
                .rol(Rol.CLIENTE)
                .estado(EstadoCuenta.INACTIVA)
                .fechaRegistro(LocalDateTime.now())
                .usuario(Usuario.builder()
                        .nombre(cuentaDTO.nombre())
                        .telefono(cuentaDTO.telefono())
                        .direccion(cuentaDTO.direccion())
                        .build())
                .build();
        cuenta.setCodigoValidacionRegistro(new CodigoValidacion(LocalDateTime.now(), codigoValidacion));
        cuenta.setCodigoValidacionContrasenia(new CodigoValidacion(LocalDateTime.now(), codigoValidacionContrasenia));
        String subject = "Bienvenido a Tienda Sana: activa tu cuenta";
        String body = "Tu código de verificación es: " + codigoValidacion + ". Tienes 15 minutos para activarla.";
        emailService.sendEmail(new EmailDTO(subject, body, cuenta.getEmail()));
        cuentaRepo.guardar(cuenta);
        return cuenta.getEmail();
    }

    /**
     * Meotod para actualizar los datos de una cuenta dada la información del formulario de actualizacion
     * @param cuentaDTO Data Transfer Object con la información del formulario de actualización
     * @return Mensaje de exito
     * @throws Exception Error al acceeder a la base de datos
     */
    @Override
    public String actualizarCuenta(ActualizarCuentaDTO cuentaDTO, String emailAutenticado) throws Exception {
        if (!emailAutenticado.equals(cuentaDTO.email())) {
            throw new AccessDeniedException("No tienes permiso para actualizar los datos de esta cuenta.");
        }

        Cuenta cuenta = obtenerCuentaPorEmail(cuentaDTO.email()); // El email del DTO es el target
        cuenta.getUsuario().setNombre(cuentaDTO.nombre());
        cuenta.getUsuario().setTelefono(cuentaDTO.telefono());
        cuenta.getUsuario().setDireccion(cuentaDTO.direccion());

        if (cuentaDTO.contrasenia() != null && !cuentaDTO.contrasenia().isEmpty() && !cuentaDTO.contrasenia().isBlank()) {
            cuenta.setContrasenia(new BCryptPasswordEncoder().encode(cuentaDTO.contrasenia()));
        }

        cuentaRepo.actualizar(cuenta);
        System.out.println("Actualizando cuenta: " + cuentaDTO.email());
        return "Actualizacion realizada con exito";
    }

    /**
     * Metodo para eliminar una cuenta (Eliminación logica mendiante el estado de Eliminada) dado el email
     * @param email el email asociado de cada cuenta se establece como unico en el negocio
     * @return Mensaje de exito
     * @throws Exception Error al acceder a la base de datos
     */
    @Override
    public String eliminarCuenta(String emailAEliminar, String emailAutenticado) throws Exception {
        if (!emailAutenticado.equals(emailAEliminar)) {
            throw new AccessDeniedException("No tienes permiso para eliminar esta cuenta.");
        }

        Cuenta cuenta = obtenerCuentaPorEmail(emailAEliminar);
        cuenta.setEstado(EstadoCuenta.ELIMINADA);
        cuentaRepo.actualizar(cuenta);
        return "La cuenta ha sido eliminada: " + emailAEliminar;
    }

    /**
     * Método para obtener la inforamción relacionada a una cuenta de un usuario dado un usario
     * @param email el email asociado de cada cuenta se establece como unico en el negocio
     * @return Informacion de la cuenta
     * @throws Exception Error al acceder a la base de datos
     * o que mas de una cuenta tenga el email indicado
     */
    @Override
    public InfoCuentaDTO obtenerInfoCuenta(String emailAConsultar, String emailAutenticado) throws Exception {
        if (!emailAutenticado.equals(emailAConsultar)) {
            Cuenta cuentaAdmin = obtenerCuentaPorEmail(emailAutenticado);
            if (cuentaAdmin.getRol() != Rol.ADMIN) {
            throw new AccessDeniedException("No tienes permiso para obtener la información de esta cuenta.");
            }
        }

        Cuenta cuenta = obtenerCuentaPorEmail(emailAConsultar);
        return new InfoCuentaDTO(
                cuenta.getEmail(),
                cuenta.getUsuario().getNombre(),
                cuenta.getUsuario().getTelefono(),
                cuenta.getUsuario().getDireccion()
        );
    }

    /**
     * Metodo para obtener una cuenta dado el email
     * (Este no es un metodo llamado por controladores pero que apoya a los servicios que si son llamados)
     * @param email Email de la cuenta
     * @return Cuenta encontrada
     * @throws Exception Error al acceder a la base de datos o que la cuenta indicada no exista
     */
    @Override
    public Cuenta obtenerCuentaPorEmail(String email) throws Exception {
        Optional<Cuenta> cuentaObtenida = cuentaRepo.obtenerPorEmail(email);
        if (cuentaObtenida.isEmpty()) {
            throw new Exception("Cuenta no encontrada");
        }
        return cuentaObtenida.get();
    }

    /**
     * Método para que un usuario logueado cambie su propia contraseña.
     * @param dto Contiene la contraseña actual y la nueva contraseña.
     * @param emailAutenticado Email del usuario autenticado que desea cambiar su contraseña.
     * @return Mensaje de éxito.
     * @throws Exception Si la contraseña actual es incorrecta, o el usuario no se encuentra.
     */
    @Override
    public String cambiarMiContrasenia(CambiarMiContraseniaDTO dto, String emailAutenticado) throws Exception {
        Cuenta cuenta = obtenerCuentaPorEmail(emailAutenticado);

        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        if (!passwordEncoder.matches(dto.contraseniaActual(), cuenta.getContrasenia())) {
            throw new Exception("La contraseña actual es incorrecta.");
        }

        cuenta.setContrasenia(encriptarContrasenia(dto.nuevaContrasenia()));
        cuentaRepo.actualizar(cuenta);

        return "Contraseña actualizada correctamente.";
    }

    /**
     * Metodo para enviar un código de recuperación de contraseña y asociarlo a la cuenta del usuario
     * @param email Email de la cuenta
     * @return Email de la cuenta
     * @throws Exception Error al acceder a la base de datos
     */
    @Override
    public String enviarCodigoRecuperacion(String email) throws Exception {
        Cuenta cuenta = obtenerCuentaPorEmail(email);
        String codigoRecuperacion = generarCodigoValidacion();
        String asunto = "Codigo de recuperacion";
        String cuerpo = "Hey! has pedido el codigo de recuperacion de tu contrasenia por tu cuenta de TiendaSana\n" +
                "este es tu codigo de recuperacion: " + codigoRecuperacion + "\nEste codigo durara 15 minutos.";
        cuenta.setCodigoValidacionContrasenia(new CodigoValidacion(LocalDateTime.now(), codigoRecuperacion));
        cuentaRepo.actualizar(cuenta);
        emailService.sendEmail(new EmailDTO(asunto, cuerpo, cuenta.getEmail()));
        return cuenta.getEmail();
    }

    /**
     * Metodo para cambiar la contraseña actual de una cuenta dado el codigo de recuperación
     * y la nueva contraseña
     * @param dto contiene el correo asociado a al cuenta, la nueva contraseña y el código de recuperación
     * @return Email de la cuenta
     * @throws Exception Error al acceder a la base de datos, que email indicado no este registrado
     * o el codigo de verificacion no sea correcto
     */
    @Override
    public String cambiarContrasenia(CambiarContraseniaDTO dto) throws Exception {
        Optional<Cuenta> cuentaOptional = cuentaRepo.obtenerPorEmail(dto.email());
        if (cuentaOptional.isEmpty()) {
            throw new RuntimeException("Este email no esta registrado"); // O ResourceNotFoundException
        }
        Cuenta cuenta = cuentaOptional.get();
        CodigoValidacion codigoValidacionContrasenia = cuenta.getCodigoValidacionContrasenia();
        if (codigoValidacionContrasenia.getCodigo().equals(dto.codigoVerificacion())) {
            if (codigoValidacionContrasenia.getFechaCreacion().plusMinutes(15).isAfter(LocalDateTime.now())) {
                cuenta.setContrasenia(encriptarContrasenia(dto.nuevaContrasenia()));
                cuentaRepo.actualizar(cuenta);
            } else {
                cuentaRepo.actualizar(cuenta);
                throw new Exception("Este codigo de verificacion ha experido");
            }
        } else {
            throw new Exception("Este codigo de verififcacion es incorrecto");
        }
        return cuenta.getEmail();
    }

    /**
     * Metodo para validar (Activar) una cuenta dado un código de validación en el correo de los usuarios
     * @param activarCuentaDTO, posee el correo asociado a la cuenta y el código de validación indicado por el
     *                          usuario
     * @return Mensaje de exito
     * @throws Exception Error al acceder a la base de datos, el l codigo de registro ha expirado o es incorrecto
     */
    @Override
    public String validarCodigoRegistro(ActivarCuentaDTO activarCuentaDTO) throws Exception {
        Cuenta cuenta = obtenerCuentaPorEmail(activarCuentaDTO.email());
        CodigoValidacion codigoValidacionRegistro = cuenta.getCodigoValidacionRegistro();
        if (codigoValidacionRegistro != null) {
            if (codigoValidacionRegistro.getCodigo().equals(activarCuentaDTO.codigoVerificacionRegistro())) {
                if (codigoValidacionRegistro.getFechaCreacion().plusMinutes(15).isAfter(LocalDateTime.now())) {
                    cuenta.setEstado(EstadoCuenta.ACTIVA);
                    cuentaRepo.actualizar(cuenta);
                } else {
                    cuentaRepo.actualizar(cuenta);
                    throw new Exception("El codigo de registro ha expirado");
                }
            } else {
                throw new Exception("Este codigo de registro es incorrecto");
            }
        } else {
            throw new Exception("No se encontró un código de validación pendiente para esta cuenta.");
        }
        return "La cuenta ha sido verificada exitosamente";
    }

    /**
     * Metodo el cual es usado para reenviar y asociar un nuevo código de valicdación en caso de que el usuario no
     * pudiera validar su cuenta ocn el primer código enviado en el registro
     * @param email, correo asociado a una cuenta, se verificara si la cuenta ya está activa antes de reasignar
     * @return Email de la cuenta
     * @throws Exception Error al acceder a la base de datos o la cuenta ya ha sido activada
     */
    @Override
    public String reenviarCodigoRegistro(String email) throws Exception {
        Cuenta cuenta = obtenerCuentaPorEmail(email);
        if(cuenta.getEstado() == EstadoCuenta.ACTIVA) {
            throw new Exception("Esta cuenta ya ha sido activada");
        }
        if(cuenta.getEstado() == EstadoCuenta.ELIMINADA) {
            throw new Exception("Esta cuenta ha sido eliminada y no puede ser reactivada de esta forma.");
        }
        CodigoValidacion codigoValidacionReenviado = new CodigoValidacion(LocalDateTime.now(), generarCodigoValidacion());
        cuenta.setCodigoValidacionRegistro(codigoValidacionReenviado);
        String asunto = "Hey! este es tu NUEVO codigo de activacion para tu cuenta de Tienda Sana";
        String cuerpo = "Tu codigo de activacion es " + codigoValidacionReenviado.getCodigo() + " tienes 15 minutos para realizar la activacion " +
                "de tu cuenta de Tienda Sana.";
        emailService.sendEmail(new EmailDTO(asunto, cuerpo, cuenta.getEmail()));
        cuentaRepo.actualizar(cuenta);
        return cuenta.getEmail();
    }

    /**
     * Metodo para iniciar sesión el cual devuelve un JWT con los datos de la cuenta que esta iniciando sesión para
     * que sea almacenada en el sessión storage del frontend
     * @param loginDTO DTO con los datos para poder iniciar sesion
     * @return Token JWT con los datos de la cuenta
     * @throws Exception La contraseña de la cuenta es incorrecta, no existe una cuenta con con el
     * correo indicada o no ha sido activada
     */
    @Override
    public TokenDTO login(LoginDTO loginDTO) throws Exception {
        Cuenta cuenta = obtenerCuentaPorEmail(loginDTO.email());
        if (cuenta.getEstado() == EstadoCuenta.ELIMINADA) {
            throw new Exception("No existe una cuenta con este correo");
        }
        if (cuenta.getEstado() == EstadoCuenta.INACTIVA) {
            reenviarCodigoRegistro(loginDTO.email());
            throw new Exception("Cuenta no activada");
        }
        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

        if (!passwordEncoder.matches(loginDTO.contrasenia(),cuenta.getContrasenia())) {
            throw new Exception("Contraseña incorrecta");
        }
        Map<String, Object> map = buildClaims(cuenta);

        return new TokenDTO(jwtUtils.generarToken(cuenta.getEmail(), map));
    }

    /**
     * Convierte los datos de la cuenta en un formato clave valor
     * @param cuenta Cuenta
     * @return Datos de la cuenta
     */
    private Map<String, Object> buildClaims(Cuenta cuenta) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("rol", cuenta.getRol().toString());
        claims.put("nombre", cuenta.getUsuario().getNombre());
        claims.put("estado", cuenta.getEstado().toString());
        claims.put("email", cuenta.getEmail());
        return claims;
    }

    //Metodo proximo a implementar correctamente
    @Override
    public TokenDTO refresh(Map<String, Object> claims) throws Exception {
        String email = (String) claims.get("sub");
        if (email == null) {
            throw new Exception("Token de refresco inválido: subject no encontrado.");
        }
        Cuenta cuenta = obtenerCuentaPorEmail(email);
        if (cuenta.getEstado() != EstadoCuenta.ACTIVA) {
            throw new Exception("La cuenta no está activa.");
        }
        return new TokenDTO(jwtUtils.generarToken(cuenta.getEmail(), buildClaims(cuenta)));
    }

    /**
     * Metodo para generar diferentes cadenas de texto las cuales serán usadas para los códigos de
     * recuperación y validacion
     * @return código aleatorio de 6 digitos
     */
    private String generarCodigoValidacion() {
        String alfabeto = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder codigo = new StringBuilder();
        Random random = new Random();
        for (int i = 0; i < 6; i++) {
            int idx = random.nextInt(alfabeto.length());
            codigo.append(alfabeto.charAt(idx));
        }
        return codigo.toString();
    }

    /**
     * Metodo para encriptar las contraseñas de los usuarios en la base de datos
     * @param Contrasenia Contraseña a encriptar
     * @return Contraseña encriptada
     */
    private String encriptarContrasenia(String Contrasenia) {
        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        return passwordEncoder.encode(Contrasenia);
    }
}
