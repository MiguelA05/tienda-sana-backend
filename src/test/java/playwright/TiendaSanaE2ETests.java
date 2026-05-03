package playwright;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import com.microsoft.playwright.options.WaitUntilState;
import org.junit.jupiter.api.*;
import com.microsoft.playwright.assertions.PageAssertions;
import com.microsoft.playwright.assertions.LocatorAssertions;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import org.junit.jupiter.api.Tag;
import static org.junit.jupiter.api.Assertions.assertTrue;


@Tag("e2e")
public class TiendaSanaE2ETests {
/**
    Playwright playwright;
    Browser browser;
    BrowserContext context;
    Page page;

    private final String BASE_URL = "https://tienda-sana-frontend.vercel.app";
    private final String USER_EMAIL_VALID = "miraortega2020@gmail.com"; // Usuario existente y activo
    private final String USER_PASSWORD_VALID = "@Miguel0515";
    private final String USER_EMAIL_NEW = "testuser" + System.currentTimeMillis() + "@example.com";
    private final String USER_NOMBRE_NEW = "Test Simple";
    private final String USER_DIRECCION_NEW = "Calle Falsa 123 # 45-67 Centro";
    private final String USER_TELEFONO_NEW = "3001112233";


    @BeforeAll
    void launchBrowser() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(false).setSlowMo(100));
    }

    @AfterAll
    void closeBrowser() {
        if (playwright != null) {
            playwright.close();
        }
    }

    @BeforeEach
    void createContextAndPage() {
        context = browser.newContext(new Browser.NewContextOptions().setViewportSize(1280, 720).setLocale("es-ES"));
        page = context.newPage();
        page.setDefaultTimeout(20000); // Aumentado ligeramente el timeout por defecto
    }

    @AfterEach
    void closeContext() {
        if (context != null) {
            context.close();
        }
    }

    void loginUser(String userEmail, String userPassword) {
        page.navigate(BASE_URL + "/login");
        page.fill("#email", userEmail); // ID del LoginComponent
        page.fill("#contrasenia", userPassword); // ID del LoginComponent
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Ingresar")).click(); // Texto del botón en LoginComponent
        assertThat(page).hasURL(Pattern.compile(BASE_URL + "/?$"), new PageAssertions.HasURLOptions().setTimeout(25000)); // Espera redirección a la home
        assertThat(page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Cerrar Sesión"))).isVisible(); // Elemento en HeaderComponent
    }

    @Test
    @Order(1)
    @DisplayName("Registro de nuevo usuario exitoso (Simple)")
    void testSuccessfulUserRegistration_Simple() {
        page.navigate(BASE_URL + "/register");

        // Campos alineados con RegisterComponent
        page.fill("#nombre", USER_NOMBRE_NEW);
        page.fill("#direccion", USER_DIRECCION_NEW); // Nuevo campo Dirección
        page.fill("#telefono", USER_TELEFONO_NEW);
        page.fill("#email", USER_EMAIL_NEW);
        page.fill("#contrasenia", USER_PASSWORD_VALID);
        page.fill("#confirmacionContrasena", USER_PASSWORD_VALID); // ID corregido según el HTML

        // Localizar y marcar el checkbox de términos y condiciones
        Locator termsCheckbox = page.locator("#terms");
        assertThat(termsCheckbox).isVisible(); // Asegurarse de que el checkbox es visible
        termsCheckbox.check(); // Marcar el checkbox
        assertThat(termsCheckbox).isChecked(); // Verificar que está marcado

        // Hacer clic en el botón de registrarse
        // El HTML muestra un botón con clase btn-primary y texto "Registrarse" (o "Procesando...")
        // Usaremos getByRole para mayor robustez si el texto cambia debido a isLoading
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(Pattern.compile("Registrarse|Procesando...", Pattern.CASE_INSENSITIVE))).click();

        assertThat(page).hasURL(Pattern.compile(BASE_URL + "/verificar-cuenta.*"), new PageAssertions.HasURLOptions().setTimeout(25000));
        System.out.println("Registro simple exitoso para: " + USER_EMAIL_NEW + ". Redirigido a verificación de cuenta.");
    }

    @Test
    @Order(2)
    @DisplayName("Login de usuario exitoso (Simple)")
    void testSuccessfulLogin_Simple() {
        loginUser(USER_EMAIL_VALID, USER_PASSWORD_VALID);
        System.out.println("Login simple exitoso para: " + USER_EMAIL_VALID);
    }

    @Test
    @Order(3)
    @DisplayName("Login fallido - Contraseña incorrecta (Simple)")
    void testFailedLogin_IncorrectPassword_Simple() {
        page.navigate(BASE_URL + "/login");
        page.fill("#email", USER_EMAIL_VALID);
        page.fill("#contrasenia", "wrongpassword");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Ingresar")).click();

        // Verifica que permanece en la página de login y no aparece "Cerrar Sesión"
        assertThat(page).hasURL(Pattern.compile(BASE_URL + "/login"), new PageAssertions.HasURLOptions().setTimeout(10000));
        assertThat(page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Cerrar Sesión"))).isHidden();
        System.out.println("Login simple fallido (contraseña incorrecta) verificado.");
    }

    @Test
    @Order(4)
    @DisplayName("Visualización de lista de productos (Simple)")
    void testViewProductList_Simple() {
        page.navigate(BASE_URL + "/productos");
        assertThat(page.locator("app-card .card").first()).isVisible(new LocatorAssertions.IsVisibleOptions().setTimeout(25000));
        assertTrue(page.locator("app-card .card").count() > 0, "Debería mostrarse al menos un producto.");
        assertThat(page.locator("app-card .card .card-title").first()).isVisible();
        System.out.println("Visualización simple de lista de productos verificada.");
    }

    @Test
    @Order(5)
    @DisplayName("Agregar producto al carrito (Simple)")
    void testAddProductToCart_Simple() {
        loginUser(USER_EMAIL_VALID, USER_PASSWORD_VALID);
        page.navigate(BASE_URL + "/productos");

        Locator firstProductCard = page.locator("app-card .card").first();
        assertThat(firstProductCard).isVisible();
        String productName = firstProductCard.locator(".card-title").textContent().trim();

        firstProductCard.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Agregar al Carrito")).click();
        page.waitForTimeout(1000);

        page.navigate(BASE_URL + "/carrito");
        assertThat(page.locator("app-shopping-car").getByText(Pattern.compile(Pattern.quote(productName), Pattern.CASE_INSENSITIVE))).isVisible();
        System.out.println("Producto '" + productName + "' agregado al carrito y verificado (simple).");
    }

    @Test
    @Order(6)
    @DisplayName("Iniciar proceso de compra desde el carrito (Simple)")
    void testProceedToCheckout_Simple() {
        loginUser(USER_EMAIL_VALID, USER_PASSWORD_VALID);
        page.navigate(BASE_URL + "/productos");
        page.locator("app-card .card").first().getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Agregar al Carrito")).click();
        page.waitForTimeout(1000);

        page.navigate(BASE_URL + "/carrito");
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Realizar Compra")).click();

        page.waitForURL(url -> url.contains("mercadopago.com"), new Page.WaitForURLOptions().setTimeout(30000));
        assertThat(page).hasURL(Pattern.compile(".*mercadopago\\.com.*"));
        System.out.println("Redirección a MercadoPago verificada (simple).");
    }

    @Test
    @Order(7)
    @DisplayName("Crear una reserva de mesa (Simple)")
    void testCreateTableReservation_Simple() {
        loginUser(USER_EMAIL_VALID, USER_PASSWORD_VALID);
        page.navigate(BASE_URL + "/mesas");

        Locator firstAvailableTable = page.locator("app-card-mesa .card-mesa:has-text('Estado: DISPONIBLE')").first();
        assertThat(firstAvailableTable).isVisible(new LocatorAssertions.IsVisibleOptions().setTimeout(25000));
        firstAvailableTable.click();

        assertThat(page).hasURL(Pattern.compile(BASE_URL + "/detalle-mesa/\\d+"), new PageAssertions.HasURLOptions().setTimeout(15000));

        LocalDate tomorrow = LocalDate.now().plusDays(1);
        String reservationDate = tomorrow.format(DateTimeFormatter.ISO_LOCAL_DATE);

        page.fill("#fechaReserva", reservationDate);
        page.fill("#horaReserva", "15:00");
        page.fill("#cantidadPersonas", "3");
        page.fill("#comentarios", "Reserva de prueba simple.");

        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Confirmar Reserva")).click();

        assertThat(page).hasURL(Pattern.compile(BASE_URL + "/?$"), new PageAssertions.HasURLOptions().setTimeout(25000));
        System.out.println("Reserva de mesa simple creada para la fecha: " + reservationDate);
    }
    **/
}
