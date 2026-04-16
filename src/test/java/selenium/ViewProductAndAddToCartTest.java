package selenium;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

@Tag("e2e")
public class ViewProductAndAddToCartTest extends BaseTest {

    @BeforeEach
    @Override
    public void setUp() {
        super.setUp();
        // Este test requiere que el usuario esté logueado para añadir al carrito y ver el carrito.
        // Puedes descomentar la siguiente línea si tienes un usuario de prueba válido.
        // login("santiquinterouribe0412@gmail.com", "juanjuan");
        // O navegar directamente si algunas partes no requieren login.
    }

    @Test
    public void testViewProductDetailsAndAddToCart() {
        driver.get(BASE_URL + "/"); // Asumiendo que los productos están en la home page

        // Esperar a que los cards de productos se carguen
        // El selector 'app-card .card' asume que app-card-grid renderiza app-card y dentro hay un div.card
        wait.until(ExpectedConditions.numberOfElementsToBeMoreThan(By.cssSelector("app-card-grid app-card .card"), 0));
        List<WebElement> productCards = driver.findElements(By.cssSelector("app-card-grid app-card .card"));
        assertFalse(productCards.isEmpty(), "No se encontraron cards de productos en la página.");

        // Tomar el primer producto para la prueba
        WebElement firstProductCard = productCards.get(0);
        // Obtener el nombre del producto para verificación posterior (opcional)
        // String productName = firstProductCard.findElement(By.cssSelector("h3")).getText();

        // Hacer clic en la imagen del producto para ver detalles
        // (el HTML de card.component.html tiene (click)="verDetalle()" en la imagen)
        WebElement productImage = firstProductCard.findElement(By.cssSelector("img"));
        productImage.click();

        // Verificar que se navega a la página de detalles del producto
        wait.until(ExpectedConditions.urlContains("/detalle-producto/"));
        assertTrue(driver.getCurrentUrl().contains("/detalle-producto/"), "No se redirigió a la página de detalles del producto.");

        // En la página de detalles, agregar al carrito
        // El botón es <button (click)="agregarAlCarrito()">Agregar al Carrito</button>
        WebElement addToCartButtonDetails = wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//button[contains(text(),'Agregar al Carrito')]")));
        addToCartButtonDetails.click();

        // Verificar que el producto se agregó (ej. un mensaje de Toastr o navegando al carrito)
        // Los Toastr son difíciles de verificar directamente. Navegaremos al carrito.
        // Primero, es posible que necesites iniciar sesión si no lo hiciste antes.
        // Si el "Agregar al Carrito" requiere login y no estabas logueado, aquí fallaría o te redirigiría a login.
        // Asumamos que el login ya se manejó o que agregar al carrito funciona para invitados y luego pide login al comprar.

        // Si no estás logueado, el botón de agregar al carrito podría no funcionar o redirigir a login.
        // Para este test, vamos a asumir que el usuario DEBE estar logueado para que "Agregar al Carrito" funcione
        // y nos lleve a un estado donde podamos verificar el carrito.
        // Si el login no se hizo en setUp, este test necesitará adaptaciones.
        // Por ahora, si el login es mandatorio, el test fallará aquí si no se hizo.
        // Si el login se hizo en el setUp:
        driver.get(BASE_URL + "/carrito");
        wait.until(ExpectedConditions.urlContains("/carrito"));

        // Verificar que el carrito no está vacío
        // El selector para los items del carrito dependerá de tu shopping-car.component.html
        // Suponiendo que cada item tiene una clase 'cart-item'
        List<WebElement> cartItems = driver.findElements(By.cssSelector(".cart-item")); // AJUSTA ESTE SELECTOR
        // Si el selector anterior no funciona, busca un h3 dentro de app-shopping-car, o similar
        // List<WebElement> cartItems = driver.findElements(By.cssSelector("app-shopping-car h3")); // Ejemplo alternativo si hay encabezados de items

        // Una verificación más simple si la estructura es compleja: buscar texto que indique que hay items.
        // Por ejemplo, si el producto añadido tiene un nombre específico que se muestra en el carrito.
        // O si el botón "Realizar Compra" está habilitado.
        WebElement realizarCompraButton = driver.findElement(By.xpath("//button[contains(text(),'Realizar Compra')]"));
        assertTrue(realizarCompraButton.isEnabled(), "El botón 'Realizar Compra' debería estar habilitado si hay items en el carrito.");

        System.out.println("Producto visto en detalle y agregado al carrito exitosamente.");
    }
}
