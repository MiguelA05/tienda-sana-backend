package co.uniquindio.tiendasana.repos;

import co.uniquindio.tiendasana.model.documents.Cuenta;
import co.uniquindio.tiendasana.model.enums.EstadoCuenta;
import co.uniquindio.tiendasana.model.enums.Rol;
import co.uniquindio.tiendasana.model.mongo.CuentaDocument;
import co.uniquindio.tiendasana.model.vo.CodigoValidacion;
import co.uniquindio.tiendasana.model.vo.Usuario;
import co.uniquindio.tiendasana.repos.mongo.CuentaDocumentRepository;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Repository
public class CuentaRepo {

    private final CuentaDocumentRepository mongo;

    public CuentaRepo(CuentaDocumentRepository mongo) {
        this.mongo = mongo;
    }

    public List<Cuenta> obtenerCuentas() {
        return mongo.findAll().stream().map(this::toCuenta).collect(Collectors.toList());
    }

    public int contarCuentasExistintes() {
        return (int) mongo.count();
    }

    public Cuenta mapearCuenta(List<Object> row) {
        Usuario usuario = Usuario.builder()
                .nombre(row.get(0).toString())
                .telefono(row.get(1).toString())
                .direccion(row.get(2).toString())
                .build();

        String email = row.get(3).toString();
        String contrasenia = row.get(4).toString();
        Rol rol = Rol.valueOf(row.get(5).toString().toUpperCase());
        EstadoCuenta estado = EstadoCuenta.valueOf(row.get(6).toString().toUpperCase());
        LocalDateTime fechaRegistro = LocalDateTime.parse(row.get(7).toString());

        CodigoValidacion codigoValidacionRegistro = CodigoValidacion.builder()
                .codigo(row.get(8).toString())
                .fechaCreacion(LocalDateTime.parse(row.get(9).toString()))
                .build();

        CodigoValidacion codigoValidacionContrasenia = CodigoValidacion.builder()
                .codigo(row.get(10).toString())
                .fechaCreacion(LocalDateTime.parse(row.get(11).toString()))
                .build();

        return Cuenta.builder()
                .usuario(usuario)
                .email(email)
                .contrasenia(contrasenia)
                .rol(rol)
                .estado(estado)
                .fechaRegistro(fechaRegistro)
                .codigoValidacionContrasenia(codigoValidacionContrasenia)
                .codigoValidacionRegistro(codigoValidacionRegistro)
                .build();
    }

    public List<Object> mapearCuentaInverso(Cuenta cuenta) {
        Usuario usuario = cuenta.getUsuario();
        CodigoValidacion codigoValidacionRegistro = cuenta.getCodigoValidacionRegistro();
        CodigoValidacion codigoValidacionContrasenia = cuenta.getCodigoValidacionContrasenia();
        return Arrays.asList(
                usuario.getNombre(),
                usuario.getTelefono(),
                usuario.getDireccion(),
                cuenta.getEmail(),
                cuenta.getContrasenia(),
                cuenta.getRol().toString(),
                cuenta.getEstado().toString(),
                cuenta.getFechaRegistro().toString(),
                codigoValidacionRegistro.getCodigo(),
                codigoValidacionRegistro.getFechaCreacion().toString(),
                codigoValidacionContrasenia.getCodigo(),
                codigoValidacionContrasenia.getFechaCreacion().toString()
        );
    }

    public void guardar(Cuenta cuenta) {
        mongo.save(toDocument(cuenta));
    }

    public List<Cuenta> filtrar(Predicate<Cuenta> expresion) {
        return obtenerCuentas().stream().filter(expresion).collect(Collectors.toList());
    }

    public int obtenerIndiceCuenta(String email) {
        List<Cuenta> cuentas = obtenerCuentas();
        for (int i = 0; i < cuentas.size(); i++) {
            if (cuentas.get(i).getEmail().equals(email)) {
                return i;
            }
        }
        return -1;
    }

    public void actualizar(Cuenta cuenta) throws IOException {
        if (!mongo.existsById(cuenta.getEmail())) {
            throw new IOException("Registro no encontrado");
        }
        mongo.save(toDocument(cuenta));
    }

    public Optional<Cuenta> obtenerPorEmail(String email) throws IOException {
        List<Cuenta> cuentasObtenidas = filtrar(cuenta -> cuenta.getEmail().equals(email));
        if (cuentasObtenidas.isEmpty()) {
            return Optional.empty();
        }
        if (cuentasObtenidas.size() > 1) {
            throw new IOException("Mas de una cuenta tiene ese email");
        }
        return Optional.of(cuentasObtenidas.get(0));
    }

    public List<Cuenta> obtenerPorEmailMongo(String email) {
        return mongo.findByEmail(email).stream().map(this::toCuenta).collect(Collectors.toList());
    }

    private CuentaDocument toDocument(Cuenta c) {
        Usuario u = c.getUsuario();
        CodigoValidacion cr = c.getCodigoValidacionRegistro();
        CodigoValidacion cc = c.getCodigoValidacionContrasenia();
        return CuentaDocument.builder()
                .email(c.getEmail())
                .nombre(u.getNombre())
                .telefono(u.getTelefono())
                .direccion(u.getDireccion())
                .contrasenia(c.getContrasenia())
                .rol(c.getRol().name())
                .estado(c.getEstado().name())
                .fechaRegistro(c.getFechaRegistro())
                .codigoRegistro(cr.getCodigo())
                .fechaCodigoRegistro(cr.getFechaCreacion())
                .codigoContrasenia(cc.getCodigo())
                .fechaCodigoContrasenia(cc.getFechaCreacion())
                .build();
    }

    private Cuenta toCuenta(CuentaDocument d) {
        Usuario usuario = Usuario.builder()
                .nombre(d.getNombre())
                .telefono(d.getTelefono())
                .direccion(d.getDireccion())
                .build();
        CodigoValidacion codigoValidacionRegistro = CodigoValidacion.builder()
                .codigo(d.getCodigoRegistro())
                .fechaCreacion(d.getFechaCodigoRegistro())
                .build();
        CodigoValidacion codigoValidacionContrasenia = CodigoValidacion.builder()
                .codigo(d.getCodigoContrasenia())
                .fechaCreacion(d.getFechaCodigoContrasenia())
                .build();
        return Cuenta.builder()
                .usuario(usuario)
                .email(d.getEmail())
                .contrasenia(d.getContrasenia())
                .rol(Rol.valueOf(d.getRol().toUpperCase()))
                .estado(EstadoCuenta.valueOf(d.getEstado().toUpperCase()))
                .fechaRegistro(d.getFechaRegistro())
                .codigoValidacionRegistro(codigoValidacionRegistro)
                .codigoValidacionContrasenia(codigoValidacionContrasenia)
                .build();
    }
}
