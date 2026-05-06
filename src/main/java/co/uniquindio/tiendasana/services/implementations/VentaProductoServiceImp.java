package co.uniquindio.tiendasana.services.implementations;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import co.uniquindio.tiendasana.dto.EmailDTO;
import co.uniquindio.tiendasana.dto.ventadtos.CrearVentaProductoDTO;
import co.uniquindio.tiendasana.dto.ventadtos.DetalleVentaDTO;
import co.uniquindio.tiendasana.dto.ventadtos.PaymentResponseDTO;
import co.uniquindio.tiendasana.dto.ventadtos.VentaItemDTO;
import co.uniquindio.tiendasana.exceptions.ProductoParseException;
import co.uniquindio.tiendasana.model.documents.*;
import co.uniquindio.tiendasana.model.vo.DetalleCarrito;
import co.uniquindio.tiendasana.model.vo.DetalleVentaProducto;
import co.uniquindio.tiendasana.model.vo.Pago;
import com.mercadopago.MercadoPagoConfig;
import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.client.preference.PreferenceBackUrlsRequest;
import com.mercadopago.client.preference.PreferenceClient;
import com.mercadopago.client.preference.PreferenceItemRequest;
import com.mercadopago.client.preference.PreferenceRequest;
import com.mercadopago.resources.payment.PaymentStatus;
import com.mercadopago.resources.preference.Preference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import co.uniquindio.tiendasana.repos.VentaProductoRepo;
import co.uniquindio.tiendasana.services.interfaces.*;
import com.google.firebase.database.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.velocity.exception.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class VentaProductoServiceImp implements VentaProductoService {
    private static final Logger logger = LoggerFactory.getLogger(VentaProductoServiceImp.class);
    private final ProductoService productoService;
    private final CarritoComprasService carritoComprasService;
    private final EmailService emailService;
    private final CuentaService cuentaService;
    private final PromocionService promocionService;
    private final VentaProductoRepo ventaProductoRepo;
    private final String mercadoPagoAccessToken;
    private final String mercadoPagoFrontendBaseUrl;
    private final String mercadoPagoWebhookBaseUrl;

    /**
     * Constructor de la clase VentaProductoServiceImp
     * @param cuentaService Servicio de cuentas
     * @param productoService Servicio de productos
     * @param carritoComprasService Servicio de carrito de compras
     * @param emailService Servicio de correo electrónico
     * @param promocionService Servicio de promociones
     * @param ventaProductoRepo Repositorio de ventas de productos
     */
    public VentaProductoServiceImp(CuentaService cuentaService, ProductoService productoService, CarritoComprasService carritoComprasService, EmailService emailService, PromocionService promocionService, VentaProductoRepo ventaProductoRepo,
                                   @Value("${mercadopago.access-token:}") String mercadoPagoAccessToken,
                                   @Value("${mercadopago.frontend-base-url:http://localhost:4200}") String mercadoPagoFrontendBaseUrl,
                                   @Value("${mercadopago.webhook-base-url:http://localhost:8080}") String mercadoPagoWebhookBaseUrl) {
        this.cuentaService = cuentaService;
        this.productoService = productoService;
        this.carritoComprasService = carritoComprasService;
        this.emailService = emailService;
        this.promocionService = promocionService;

        this.ventaProductoRepo = ventaProductoRepo;
        this.mercadoPagoAccessToken = mercadoPagoAccessToken;
        this.mercadoPagoFrontendBaseUrl = sanitizeBaseUrl(mercadoPagoFrontendBaseUrl);
        this.mercadoPagoWebhookBaseUrl = sanitizeBaseUrl(mercadoPagoWebhookBaseUrl);
    }

    /**
     * Metodo para crear una venta de productos
     * @param crearVentaProductoDTO DTO con la información de la venta
     * @return ID de la venta creada
     * @throws Exception
     */
    @Override
    public String crearVenta(CrearVentaProductoDTO crearVentaProductoDTO) throws Exception {
        VentaProducto ventaProducto = new VentaProducto();

        ventaProducto.setFecha(LocalDateTime.now());
        ventaProducto.setEmailUsario(crearVentaProductoDTO.emailUsuario());

        Cuenta cuenta = cuentaService.obtenerCuentaPorEmail(crearVentaProductoDTO.emailUsuario());

        ventaProducto.setId(UUID.randomUUID().toString());

        CarritoCompras carritoCompras = carritoComprasService.getCarritoCompras(crearVentaProductoDTO.emailUsuario(), cuenta.getEmail());
        List<DetalleVentaProducto> items = getOrderDetails(carritoCompras, ventaProducto.getId());
        ventaProducto.setProductos(items);

        if (crearVentaProductoDTO.idPromocion() != null && !crearVentaProductoDTO.idPromocion().isEmpty()) {

            Promocion promocion = promocionService.getPromocion(crearVentaProductoDTO.idPromocion());

            if (promocion == null ) {
                throw new ResourceNotFoundException("La promocion no existe");
            }


            if (promocion.getId() == null) {
                throw new ResourceNotFoundException("La promocion no tiene un id válido para crear la orden");
            }


            float totalWithDiscount = calculateTotal(items, promocion.getId(), crearVentaProductoDTO.emailUsuario());
            ventaProducto.setTotal(totalWithDiscount);


        } else {
            ventaProducto.setTotal(calculateTotal(items, null, crearVentaProductoDTO.emailUsuario()));
        }

        VentaProducto createOrder = ventaProductoRepo.guardarVentaProducto(ventaProducto);

        return createOrder.getId();
    }


    /**
     * Metodo para obtener los detalles de la orden a partir del carrito de compras
     * @param carritoCompras Carrito de compras del cliente
     * @return Lista de detalles de la orden
     */
    private @NotNull List<DetalleVentaProducto> getOrderDetails(CarritoCompras carritoCompras, String idVentaProducto) {
        List<DetalleVentaProducto> items = new ArrayList<>();
        List<DetalleCarrito> details = carritoCompras.getProductos();
        details.forEach(carDetail -> {
            try {

                Producto producto = productoService.obtenerProducto(String.valueOf(carDetail.getProductoId()));

                DetalleVentaProducto orderDetail = new DetalleVentaProducto();
                orderDetail.setProductoId(carDetail.getProductoId());
                orderDetail.setValor(carDetail.getSubtotal());
                orderDetail.setCantidad(carDetail.getCantidad());
                orderDetail.setVentaId(idVentaProducto);
                items.add(orderDetail);

            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }

        });
        return items;
    }

    /**
     * Metodo para calcular el total de la venta
     * @param items Lista de detalles de la venta
     * @param promocionId ID de la promoción
     * @param usuarioId ID del usuario
     * @return Total de la venta
     */
    private float calculateTotal(List<DetalleVentaProducto> items, String promocionId, String usuarioId) {
        if (promocionId != null && !promocionId.isEmpty()) {

            Promocion promocion = promocionService.getPromocion(promocionId);

            if (promocion == null) {
                throw new ResourceNotFoundException("Coupon not found with id: " + promocionId);
            }

            float total = 0;
            for (DetalleVentaProducto detalleVentaProducto : items) {
                total += detalleVentaProducto.getValor();
            }


            return total * (1 - promocion.getPorcentajeDescuento());
        } else {
            float total = 0;
            for (DetalleVentaProducto detalleVentaProducto : items) {
                total += detalleVentaProducto.getValor();
            }
            return total;
        }
    }

    /**
     * Metodo para obtener las ventas de un cliente
     * @param emailCliente ID del cliente
     * @return Lista de ventas del cliente
     */
    private List<VentaProducto> obtenerVentasProductoPorCliente(String emailCliente) throws IOException, ProductoParseException {
        return ventaProductoRepo.filtrarVentasSimple(ventaProducto -> ventaProducto.getEmailUsario().equals(emailCliente));
    }

    /**
     * Metodo para obtener una venta de producto por su ID
     * @param idVentaProducto ID de la venta de producto
     * @return Venta de producto
     * @throws ResourceNotFoundException
     */
    @Override
    public VentaProducto obtenerVentaProducto(String idVentaProducto) throws Exception {
        List<VentaProducto> ventaProducto = ventaProductoRepo.filtrarVentasSimple(ventaProducto1 -> {
            return ventaProducto1.getId().equals(idVentaProducto); });
        if (ventaProducto.isEmpty()) {
            throw new Exception("La venta con el id: " + idVentaProducto + " no existe");
        }
        return ventaProducto.get(0);
    }

    /**
     * Metodo para borrar una venta de producto
     * @param idVentaProducto ID de la venta de producto
     * @return Mensaje de éxito
     * @throws Exception
     */
    @Override
    public String borrarVentaProducto(String idVentaProducto) throws Exception {
        VentaProducto ventaBorrar = obtenerVentaProducto(idVentaProducto);
        Pago pago = ventaBorrar.getPago();
        if(pago ==null || (!(pago.getStatus().equals(PaymentStatus.APPROVED) && pago.getStatusDetail().equalsIgnoreCase("accredited"))) ){
            ventaBorrar.setEmailUsario("-");
            ventaProductoRepo.actualizarVentaSimple(ventaBorrar);
            return "La venta fue borrada";
        }else{
            throw new Exception("No se puede borrar la venta porque ya fue aprobada");
        }
    }

    /**
     * Metodo para obtener la información de una venta
     * @param ventaProductoId ID de la venta de producto
     * @return Información de la venta
     * @throws ResourceNotFoundException
     */
    @Override
    public VentaItemDTO obtenerInformacionVenta(String ventaProductoId) throws Exception {
        VentaProducto ventaProducto = obtenerVentaProducto(ventaProductoId); // Método que obtiene la orden

        return mapearAVentaItemDTO(ventaProducto);
    }

    /**
     * Metodo para mapear una venta de producto a un DTO
     * @param ventaProducto Venta de producto
     * @return DTO de la venta
     */
    private VentaItemDTO mapearAVentaItemDTO(VentaProducto ventaProducto) {
        return new VentaItemDTO(
                ventaProducto.getEmailUsario() != null ? ventaProducto.getEmailUsario().toString() : null,
                ventaProducto.getFecha(),
                mapearADetalleVentaProducto(ventaProducto.getProductos()),
                ventaProducto.getPago() != null ? ventaProducto.getPago().getPaymentType() : null,
                ventaProducto.getPago() != null ? ventaProducto.getPago().getStatus() : null,
                ventaProducto.getPago() != null ? ventaProducto.getPago().getDate() : null,
                ventaProducto.getPago() != null ? ventaProducto.getPago().getTransactionValue() : 0f,
                ventaProducto.getId(),
                ventaProducto.getTotal(),
                ventaProducto.getPromocionId() != null ? ventaProducto.getPromocionId() : null);

    }

    /**
     * Metodo para listar las ventas de un cliente
     * @param emailCliente ID del cliente
     * @return Lista de ventas del cliente
     */
    @Override
    public List<VentaItemDTO> listarVentasCliente(String emailCliente) throws IOException, ProductoParseException {
        List<VentaProducto> ventas = ventaProductoRepo.filtrarVentasSimple(ventaProducto -> ventaProducto.getEmailUsario().equals(emailCliente));
        return obtenerVentaItemDto(ventas);
    }

    /**
     * Metodo para mapear una lista de ventas a un DTO
     * @param ventas Lista de ventas
     * @return Lista de DTOs de ventas
     */
    @NotNull
    private List<VentaItemDTO> obtenerVentaItemDto(List<VentaProducto> ventas) {
        return ventas.stream().map(this::mapearAVentaItemDTO
        ).collect(Collectors.toList());
    }

    /**
     * Metodo para mapear una lista de productos a un DTO
     * @param productos Lista de productos
     * @return Lista de DTOs de productos
     */
    private @NotNull List<DetalleVentaDTO> mapearADetalleVentaProducto(List<DetalleVentaProducto> productos) {
        return productos.stream().map(e -> new DetalleVentaDTO(
                e.getProductoId(),
                e.getValor(),
                e.getCantidad()
        )).collect(Collectors.toList());
    }

    /**
     * Metodo para realizar el pago de una venta
     * @param ventaProductoId ID de la venta de producto
     * @return Respuesta de la pasarela de pago
     * @throws Exception
     */
    @Override
    public PaymentResponseDTO makePayment(String ventaProductoId) throws Exception {
        try {
            // Obtener la orden guardada en la base de datos y los ítems de la orden
            VentaProducto ventaGuardar = obtenerVentaProducto(ventaProductoId);

            List<PreferenceItemRequest> itemsGateway = new ArrayList<>();
            // Comprobar si hay un cupón de descuento en la orden
            Promocion promocion = null;
            if (ventaGuardar.getPromocionId() != null) {
                promocion = promocionService.getPromocion(ventaGuardar.getPromocionId());
            }

            // Recorrer los items de la orden y crea los ítems de la pasarela
            for (DetalleVentaProducto item : ventaGuardar.getProductos()) {
                // Obtener el evento y la localidad del ítem
                Producto producto = productoService.obtenerProducto(item.getProductoId());

                float unitPrice = (promocion != null) ?
                        Math.max(0, producto.getPrecioUnitario() - (producto.getPrecioUnitario() * promocion.getPorcentajeDescuento())) :
                        producto.getPrecioUnitario();


                // Crear el item de la pasarela
                PreferenceItemRequest itemRequest =
                        PreferenceItemRequest.builder()
                                .id(producto.getId())
                                .title(producto.getNombre())
                                .pictureUrl(producto.getImagen())
                                .categoryId(producto.getCategoria())
                                .quantity(item.getCantidad())
                                .currencyId("COP")
                                .unitPrice(BigDecimal.valueOf(unitPrice))
                                .build();
                itemsGateway.add(itemRequest);


            }

                if (mercadoPagoAccessToken == null || mercadoPagoAccessToken.isBlank()) {
                throw new IllegalStateException("No se configuró MERCADOPAGO_ACCESS_TOKEN");
                }
                MercadoPagoConfig.setAccessToken(mercadoPagoAccessToken);

            PreferenceBackUrlsRequest backUrls = PreferenceBackUrlsRequest.builder()
                    .success(buildFrontendBackUrl("success"))
                    .failure(buildFrontendBackUrl("failure"))
                    .pending(buildFrontendBackUrl("pending"))
                    .build();


            // Construir la preferencia de la pasarela con los ítems, metadatos y urls de retorno
            PreferenceRequest preferenceRequest = PreferenceRequest.builder()
                    .backUrls(backUrls)
                    .items(itemsGateway)
                    .metadata(Map.of("id_venta", ventaGuardar.getId()))
                    .notificationUrl(mercadoPagoWebhookBaseUrl + "/api/public/venta/receive-notification")
                    .build();


            // Crear la preferencia en la pasarela de MercadoPago
            PreferenceClient client = new PreferenceClient();
            Preference preference = client.create(preferenceRequest);


            // Guardar el código de la pasarela en la orden
            ventaGuardar.setCodigoPasarela(preference.getId());
            ventaProductoRepo.actualizarVentaSimple(ventaGuardar);



            return new PaymentResponseDTO(
                    preference.getInitPoint(),
                    ventaProductoId
            );
        }catch (Exception e) {
            e.printStackTrace();
            throw new Exception("Error al crear la preferencia de pago");
        }

    }

    /**
     * Metodo para recibir notificaciones de MercadoPago
     * @param request Notificación de MercadoPago
     */
    @Override
    public void receiveNotificationFromMercadoPago(Map<String, Object> request) {
        try {
            // Obtener el tipo de notificación
            Object tipo = request.get("type");

            // Si la notificación es de un pago entonces obtener el pago y la orden asociada
            if ("payment".equals(tipo)) {
                // Capturamos el JSON que viene en el request y lo convertimos a un String
                String input = request.get("data").toString();

                // Extraemos los números de la cadena, es decir, el id del pago
                String idPago = input.replaceAll("\\D+", "");

                // Se crea el cliente de MercadoPago y se obtiene el pago con el id
                PaymentClient client = new PaymentClient();
                com.mercadopago.resources.payment.Payment payment = client.get(Long.parseLong(idPago));

                // Obtener el id de la orden asociada al pago que viene en los metadatos
                String idVenta = payment.getMetadata().get("id_venta").toString();

                // Se obtiene la orden guardada en la base de datos y se le asigna el pago, ademas de aumentar la cantidad de entradas vendidas
                VentaProducto ventaProducto = obtenerVentaProducto(idVenta);
                Pago orderPago = createPayment(payment);

                ventaProducto.setPago(orderPago);
                ventaProductoRepo.actualizarVentaSimple(ventaProducto);
                Cuenta cuenta = cuentaService.obtenerCuentaPorEmail(ventaProducto.getEmailUsario());


                if (ventaProducto.getPago().getStatus().equalsIgnoreCase("APPROVED") && ventaProducto.getPago().getStatusDetail().equalsIgnoreCase("accredited")) {
                    int idx = 0;
                    for (DetalleVentaProducto detalleVentaProducto : ventaProducto.getProductos()) {
                        String invRef = idVenta + "/" + idx++;
                        productoService.reducirCantidadProductosStock(
                                detalleVentaProducto.getProductoId(), detalleVentaProducto.getCantidad(), invRef);
                    }
                    enviarResumenVenta(cuenta.getEmail(), ventaProducto);
                    carritoComprasService.borrarTodosLosItemsDelCarrito(ventaProducto.getEmailUsario());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @Override
    public String solicitarReembolsoVenta(String ventaProductoId, String emailSolicitante, boolean esAdmin) throws Exception {
        VentaProducto venta = obtenerVentaProducto(ventaProductoId);

        if (!esAdmin) {
            if (emailSolicitante == null || emailSolicitante.isBlank()) {
                throw new IllegalArgumentException("No se pudo determinar el usuario solicitante");
            }
            if (venta.getEmailUsario() == null || !venta.getEmailUsario().equalsIgnoreCase(emailSolicitante)) {
                throw new IllegalAccessException("No tienes permisos para reembolsar esta compra");
            }
        }

        Pago pago = venta.getPago();
        if (pago == null) {
            throw new IllegalStateException("La venta no tiene información de pago");
        }

        if (isRefundedPayment(pago)) {
            logger.info("Reembolso idempotente para venta {}. Ya estaba reembolsada.", ventaProductoId);
            return "La venta ya había sido reembolsada";
        }

        if (!isApprovedPayment(pago)) {
            throw new IllegalStateException("Solo se permiten reembolsos para pagos aprobados");
        }

        String paymentId = pago.getId();
        if (paymentId == null || paymentId.isBlank() || "-".equals(paymentId)) {
            throw new IllegalStateException("No existe paymentId para tramitar el reembolso");
        }

        if (mercadoPagoAccessToken == null || mercadoPagoAccessToken.isBlank()) {
            throw new IllegalStateException("No se configuró MERCADOPAGO_ACCESS_TOKEN");
        }

        logger.info("Solicitando reembolso en Mercado Pago. ventaId={}, paymentId={}", ventaProductoId, paymentId);
        RefundResult refundResult = requestRefundToMercadoPago(paymentId);
        if (!refundResult.success()) {
            logger.warn("Fallo de reembolso en MP. ventaId={}, paymentId={}, detalle={}", ventaProductoId, paymentId, refundResult.message());
            throw new IllegalStateException("No se pudo completar el reembolso en Mercado Pago: " + refundResult.message());
        }

        int idx = 0;
        for (DetalleVentaProducto detalle : venta.getProductos()) {
            String invRef = ventaProductoId + "/refund/" + idx++;
            productoService.aumentarCantidadProductosStock(detalle.getProductoId(), detalle.getCantidad(), invRef);
        }

        pago.setStatus("refunded");
        pago.setStatusDetail("refunded");
        pago.setDate(LocalDateTime.now());
        venta.setPago(pago);
        ventaProductoRepo.actualizarVentaSimple(venta);

        logger.info("Reembolso exitoso. ventaId={}, paymentId={}, stockRepuestoItems={}", ventaProductoId, paymentId, venta.getProductos().size());
        return "Reembolso aplicado correctamente";
    }

    private String buildFrontendBackUrl(String status) {
        return mercadoPagoFrontendBaseUrl + "/carrito?status=" + status;
    }

    private String sanitizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "";
        }
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    private boolean isApprovedPayment(Pago pago) {
        String status = pago.getStatus() == null ? "" : pago.getStatus();
        String detail = pago.getStatusDetail() == null ? "" : pago.getStatusDetail();
        return "approved".equalsIgnoreCase(status) && "accredited".equalsIgnoreCase(detail);
    }

    private boolean isRefundedPayment(Pago pago) {
        String status = pago.getStatus() == null ? "" : pago.getStatus();
        String detail = pago.getStatusDetail() == null ? "" : pago.getStatusDetail();
        return "refunded".equalsIgnoreCase(status) || "refunded".equalsIgnoreCase(detail);
    }

    private RefundResult requestRefundToMercadoPago(String paymentId) {
        try {
            String body = "{\"amount\":null}";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.mercadopago.com/v1/payments/" + paymentId + "/refunds"))
                    .header("Authorization", "Bearer " + mercadoPagoAccessToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            int code = response.statusCode();

            if (code >= 200 && code < 300) {
                return new RefundResult(true, "OK");
            }

            String message = "HTTP " + code;
            try {
                JsonNode node = new ObjectMapper().readTree(response.body());
                if (node.has("message")) {
                    message = node.get("message").asText(message);
                }
            } catch (Exception ignored) {
                // Si no se puede parsear el body, se conserva el mensaje por código HTTP.
            }

            return new RefundResult(false, message);
        } catch (Exception ex) {
            return new RefundResult(false, ex.getMessage());
        }
    }

    private record RefundResult(boolean success, String message) {}

    /**
     * Metodo para crear un objeto Pago a partir de un objeto Payment
     * @param payment Objeto Payment de MercadoPago
     * @return Objeto Pago
     */
    private Pago createPayment(com.mercadopago.resources.payment.Payment payment) {
        Pago orderPayment = new Pago();
        orderPayment.setId(payment.getId().toString());
        orderPayment.setDate(payment.getDateCreated().toLocalDateTime());
        orderPayment.setStatus(payment.getStatus());
        orderPayment.setStatusDetail(payment.getStatusDetail());
        orderPayment.setPaymentType(payment.getPaymentTypeId());
        orderPayment.setCurrency(payment.getCurrencyId());
        orderPayment.setAuthorizationCode(payment.getAuthorizationCode());
        orderPayment.setTransactionValue(payment.getTransactionAmount().floatValue());
        return orderPayment;
    }

    /**
     * Metodo para enviar un resumen de la venta al correo del cliente
     * @param email Correo del cliente
     * @param ventaProducto Venta de producto
     * @return Mensaje de éxito
     * @throws ResourceNotFoundException
     * @throws Exception
     */
    public String enviarResumenVenta(String email, VentaProducto ventaProducto) throws ResourceNotFoundException, Exception {
        Cuenta cuenta = cuentaService.obtenerCuentaPorEmail(email);

        String qrCodeUrl = "https://quickchart.io/qr?text=" + ventaProducto.getId() + "&size=300";
        byte[] qrCodeImage = emailService.downloadImage(qrCodeUrl);

        String subject = "Resumen de tu compra en Tienda Sana";
        StringBuilder body = new StringBuilder();

        // Inicio del HTML con estilos CSS incorporados
        body.append("<!DOCTYPE html>");
        body.append("<html>");
        body.append("<head>");
        body.append("<meta charset='UTF-8'>");
        body.append("<style>");
        body.append("body { font-family: 'Helvetica', Arial, sans-serif; color: #333; line-height: 1.6; margin: 0; padding: 0; }");
        body.append(".container { max-width: 600px; margin: 0 auto; padding: 20px; background-color: #f8f8f8; }");
        body.append(".header { background-color: #4CAF50; color: white; padding: 20px; text-align: center; border-radius: 5px 5px 0 0; }");
        body.append(".content { background-color: #ffffff; padding: 20px; border-radius: 0 0 5px 5px; box-shadow: 0 2px 4px rgba(0,0,0,0.1); }");
        body.append("h1 { margin: 0; color: white; font-size: 24px; }");
        body.append("h2 { color: #4CAF50; font-size: 20px; border-bottom: 2px solid #4CAF50; padding-bottom: 10px; margin-top: 20px; }");
        body.append(".product { background-color: #f9f9f9; padding: 15px; margin: 10px 0; border-left: 4px solid #4CAF50; border-radius: 4px; }");
        body.append(".total { background-color: #4CAF50; color: white; padding: 10px; text-align: right; margin-top: 20px; font-size: 18px; border-radius: 4px; }");
        body.append(".footer { margin-top: 20px; font-size: 12px; text-align: center; color: #777; }");
        body.append(".qr-code { text-align: center; margin: 20px 0; }");
        body.append("</style>");
        body.append("</head>");

        // Contenido del correo
        body.append("<body>");
        body.append("<div class='container'>");

        // Encabezado
        body.append("<div class='header'>");
        body.append("<h1>Tienda Sana</h1>");
        body.append("</div>");

        // Contenido principal
        body.append("<div class='content'>");
        body.append("<h2>¡Hola ").append(cuenta.getUsuario().getNombre()).append("!</h2>");
        body.append("<p>Gracias por tu compra en Tienda Sana. A continuación encontrarás un resumen de tu pedido:</p>");

        // Detalles del pedido
        body.append("<h2>Detalles del Pedido:</h2>");
        body.append("<p><strong>Número de Pedido:</strong> #").append(ventaProducto.getId()).append("<br>");
        body.append("<strong>Fecha de Compra:</strong> ").append(ventaProducto.getFecha()).append("</p>");

        // Información de pago
        if (ventaProducto.getPago() != null) {
            body.append("<p><strong>Método de Pago:</strong> ").append(traducirMetodoPago(ventaProducto.getPago().getPaymentType())).append("<br>");
            body.append("<strong>Estado del Pago:</strong> ").append(traducirEstadoPago(ventaProducto.getPago().getStatus())).append("</p>");
        }

        // Productos comprados
        body.append("<h2>Productos Adquiridos:</h2>");
        for (DetalleVentaProducto item : ventaProducto.getProductos()) {
            Producto producto = productoService.obtenerProducto(item.getProductoId());
            body.append("<div class='product'>");
            body.append("<p><strong>").append(producto.getNombre()).append("</strong><br>");
            body.append("<em>").append(producto.getDescripcion()).append("</em><br>");
            body.append("Cantidad: ").append(item.getCantidad()).append("<br>");
            body.append("Precio unitario: $").append(formatearPrecio(producto.getPrecioUnitario())).append("<br>");
            body.append("Subtotal: $").append(formatearPrecio(producto.getPrecioUnitario() * item.getCantidad())).append("</p>");
            body.append("</div>");
        }

        // Total
        body.append("<div class='total'>");
        body.append("Total pagado: $").append(formatearPrecio(ventaProducto.getTotal()));
        body.append("</div>");

        // Código QR
        body.append("<div class='qr-code'>");
        body.append("<p><strong>Código de confirmación</strong></p>");
        body.append("<img src='cid:qrCodeImage' alt='Código QR de tu pedido' width='150' height='150'>");
        body.append("<p>Presenta este código para recoger tu pedido</p>");
        body.append("</div>");

        // Pie de página
        body.append("<div class='footer'>");
        body.append("<p>Si tienes alguna duda sobre tu pedido, no dudes en contactarnos a través de <a href='mailto:soporte@tiendasana.com'>soporte@tiendasana.com</a>.</p>");
        body.append("<p>© ").append(java.time.Year.now().getValue()).append(" Tienda Sana. Todos los derechos reservados.</p>");
        body.append("</div>");

        body.append("</div>"); // Cierre del div content
        body.append("</div>"); // Cierre del div container
        body.append("</body>");
        body.append("</html>");

        // Enviar el correo con la imagen embebida
        emailService.sendEmailHtmlWithAttachment(new EmailDTO(subject, body.toString(), email), qrCodeImage, "qrCodeImage");

        return "El resumen de tu compra ha sido enviado a tu correo electrónico";
    }

    // Métodos auxiliares para traducción
    private String traducirMetodoPago(String paymentType) {
        switch (paymentType.toLowerCase()) {
            case "credit_card":
                return "Tarjeta de Crédito";
            case "debit_card":
                return "Tarjeta de Débito";
            case "paypal":
                return "PayPal";
            case "cash":
                return "Efectivo";
            default:
                return paymentType;
        }
    }

    private String traducirEstadoPago(String status) {
        switch (status.toLowerCase()) {
            case "completed":
                return "Completado";
            case "pending":
                return "Pendiente";
            case "failed":
                return "Fallido";
            case "refunded":
                return "Reembolsado";
            default:
                return status;
        }
    }

    private String formatearPrecio(double precio) {
        return String.format("%.2f", precio);
    }






}
