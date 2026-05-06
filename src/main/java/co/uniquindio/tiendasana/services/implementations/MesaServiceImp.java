package co.uniquindio.tiendasana.services.implementations;

import co.uniquindio.tiendasana.dto.mesadtos.*;
import co.uniquindio.tiendasana.model.documents.Mesa;
import co.uniquindio.tiendasana.model.enums.EstadoMesa;
import co.uniquindio.tiendasana.model.enums.Localidad;
import co.uniquindio.tiendasana.repos.MesaRepo;
import co.uniquindio.tiendasana.services.interfaces.MesaService;
import co.uniquindio.tiendasana.utils.MesaConstantes; // Asegúrate que esta constante es la correcta para la hoja de cliente

import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;


import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
public class MesaServiceImp implements MesaService {

    private final MesaRepo mesaRepo;

    public MesaServiceImp(MesaRepo mesaRepo) {
        this.mesaRepo = mesaRepo;
    }

    @Override
    public MesaInfoDTO obtenerInfoMesa(String mesaId) throws Exception {
        // Para información general/cliente, obtenemos de la hoja de cliente.
        Optional<Mesa> mesaObtenida = mesaRepo.obtenerPorIdDesdeHojaCliente(mesaId);
        if (mesaObtenida.isEmpty()) {
            throw new Exception("Mesa no encontrada con ID: " + mesaId);
        }
        Mesa mesa = mesaObtenida.get();
        return new MesaInfoDTO(
                mesa.getId(),
                mesa.getNombre(),
                mesa.getEstado(), // Esto es String
                mesa.getLocalidad() != null ? mesa.getLocalidad().getLocalidad() : "-", // Esto es String
                mesa.getPrecioReserva(),
                mesa.getCapacidad(),
            mesa.getImagen(),
            mesa.getDuracionReservaMinutos()
        );
    }

    @Override
    public ListaMesasDTO obtenerMesasCliente(int pagina) throws Exception {
        // Llama al método renombrado y específico para la paginación del cliente en MesaRepo
        MesasTotalDTO paginaMesas = mesaRepo.obtenerMesasClientePaginado(pagina, MesaConstantes.ELEMENTOSPAGINA);
        List<Mesa> mesas = paginaMesas.mesas();
        List<MesaItemDTO> mesasItems = mesas.stream()
                .map(mesa -> new MesaItemDTO(
                        mesa.getId(),
                        mesa.getNombre(),
                        mesa.getEstado(),
                        mesa.getLocalidad() != null ? mesa.getLocalidad().getLocalidad() : "-",
                        mesa.getPrecioReserva(),
                        mesa.getCapacidad(),
                    mesa.getImagen(),
                    mesa.getDuracionReservaMinutos()
                ))
                .collect(Collectors.toList());

        return new ListaMesasDTO(
                paginaMesas.totalMesas() > 0 ? (int) Math.ceil((double) paginaMesas.totalMesas() / MesaConstantes.ELEMENTOSPAGINA) : 0,
                mesasItems
        );
    }

    /**
     * Obtiene la entidad Mesa. Si es para una operación interna que requiere la
     * mesa canónica (ej. para actualizarla), usa obtenerMesaPorIdOriginal.
     * Si es para mostrar info al cliente, usa obtenerPorIdDesdeHojaCliente.
     * Este método ahora obtiene la mesa de la hoja principal, asumiendo que es para operaciones internas.
     */
    @Override
    public Mesa obtenerMesa(String mesaId) throws Exception {
        // Para operaciones internas como cambiar estado, obtenemos de la hoja principal.
        Optional<Mesa> mesaObtenida = mesaRepo.obtenerMesaPorIdOriginal(mesaId);
        if (mesaObtenida.isEmpty()) {
            throw new Exception("Mesa no encontrada en el registro principal con ID: " + mesaId);
        }
        return mesaObtenida.get();
    }

    @Override
    public void cambiarEstadoMesa(String mesaId, String estadoNuevoStr) throws Exception {
        // Obtener la mesa de la hoja principal para asegurar que actualizamos la fuente canónica.
        Mesa mesa = obtenerMesa(mesaId); // Esto ahora llama a obtenerMesaPorIdOriginal a través de la sobrecarga.

        EstadoMesa nuevoEstado;
        try {
            nuevoEstado = EstadoMesa.fromEstado(estadoNuevoStr);
        } catch (IllegalArgumentException e) {
            throw new Exception("Estado de mesa proporcionado no es válido: " + estadoNuevoStr);
        }

        mesa.setEstado(nuevoEstado); // El setter en Mesa debería aceptar el Enum
        mesaRepo.actualizar(mesa); // MesaRepo.actualizar opera sobre la hoja principal
        System.out.println("Se ha actualizado el estado de la mesa ID " + mesaId + " a " + nuevoEstado.getEstado() + " en la hoja principal.");
    }

    @Override
    public ListaMesasDTO filtrarMesas(FiltroMesaDTO filtroMesaDTO) throws Exception {
        Predicate<Mesa> filtro = getMesaPredicate(filtroMesaDTO);

        // Filtrar desde la hoja de cliente
        List<Mesa> mesasFiltradas = mesaRepo.filtrarMesasDeHoja(filtro, MesaConstantes.HOJA_CLIENTE);

        int pageSize = MesaConstantes.ELEMENTOSPAGINA; // Usar la constante
        int totalItems = mesasFiltradas.size();
        int totalPaginas = (totalItems == 0) ? 0 : (int) Math.ceil((double) totalItems / pageSize);

        List<Mesa> paginatedList = obtenerListaPaginada(mesasFiltradas, filtroMesaDTO.pagina(), pageSize, totalItems);

        List<MesaItemDTO> mesasItems = paginatedList.stream()
                .map(mesa -> new MesaItemDTO(
                        mesa.getId(),
                        mesa.getNombre(),
                        mesa.getEstado(),
                        (mesa.getLocalidad() != null ? mesa.getLocalidad().getLocalidad() : "-"),
                        mesa.getPrecioReserva(),
                        mesa.getCapacidad(),
                    mesa.getImagen(),
                    mesa.getDuracionReservaMinutos()
                ))
                .collect(Collectors.toList());

        return new ListaMesasDTO(totalPaginas, mesasItems);
    }

    private List<Mesa> obtenerListaPaginada(List<Mesa> mesasFiltradas, int pagina, int pageSize, int totalItems) {
        List<Mesa> paginatedList;
        int pageNumber = pagina;
        if (pageNumber < 0) {
            pageNumber = 0;
        }

        if (totalItems == 0 || pageNumber * pageSize >= totalItems) {
            paginatedList = Collections.emptyList();
        } else {
            int startItem = pageNumber * pageSize;
            int endItem = Math.min(startItem + pageSize, totalItems);
            paginatedList = mesasFiltradas.subList(startItem, endItem);
        }
        return paginatedList;
    }

    private static @NotNull Predicate<Mesa> getMesaPredicate(FiltroMesaDTO filtroMesaDTO) {
        boolean filtroVacio = (filtroMesaDTO.nombre() == null || filtroMesaDTO.nombre().isEmpty()) &&
                (filtroMesaDTO.localidad() == null || filtroMesaDTO.localidad().isEmpty()) &&
                filtroMesaDTO.capacidad() == 0;

        if (filtroVacio) {
            return mesa -> mesa != null;
        }

        Predicate<Mesa> filtro = mesa -> {
            if (mesa == null) return false; // Seguridad adicional
            boolean matches = true;

            if (filtroMesaDTO.nombre() != null && !filtroMesaDTO.nombre().isEmpty()) {
                matches &= (mesa.getNombre() != null &&
                        mesa.getNombre().toLowerCase().contains(filtroMesaDTO.nombre().toLowerCase()));
            }

            if (filtroMesaDTO.capacidad() > 0) { // Solo filtrar si la capacidad es mayor a 0
                matches &= mesa.getCapacidad() >= filtroMesaDTO.capacidad();
            }

            if (filtroMesaDTO.localidad() != null && !filtroMesaDTO.localidad().isEmpty()) {
                matches &= (mesa.getLocalidad() != null &&
                        mesa.getLocalidad().getLocalidad().equalsIgnoreCase(filtroMesaDTO.localidad()));
            }
            return matches;
        };
        return filtro;
    }

    @Override
    public List<String> listarLocalidades() throws Exception {
        // Esto obtiene los valores del Enum Localidad, lo cual es correcto.
        List<String> localidades = Arrays.stream(Localidad.values())
                .map(Localidad::getLocalidad)
                .collect(Collectors.toList());

        if (localidades.isEmpty()) {
            // Esto no debería ocurrir si el Enum Localidad tiene valores.
            throw new Exception("No existen localidades definidas en el sistema.");
        }
        return localidades;
    }
}