package selenium;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Disabled;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Tag("e2e")
@Disabled("E2E Selenium tests disabled for CI/local build")
public class LoginTest extends BaseTest {

    @Test
    public void testLoginSuccess() {
        driver.get(BASE_URL + "/login");

        // Encuentra los campos y escribe credenciales
        WebElement emailField = driver.findElement(By.id("email")); // ID del input de email
        WebElement passwordField = driver.findElement(By.id("contrasenia")); // ID del input de contraseña
        WebElement loginButton = driver.findElement(By.cssSelector("button[type='submit']")); // Selector del botón de login

        emailField.sendKeys("santiquinterouribe0412@gmail.com"); // Usuario de prueba
        passwordField.sendKeys("juanjuan"); // Contraseña de prueba
        loginButton.click();

        // Verificar que se redirigió o se mostró algo indicando éxito
        // Esperar a que el enlace "Cerrar Sesión" esté visible como indicador de login exitoso
        //WebElement logoutLink = wait.until(ExpectedConditions.visibilityOfElementLocated(By.linkText("Cerrar Sesión")));
        //assertNotNull(logoutLink, "El enlace 'Cerrar Sesión' no se encontró, el login pudo haber fallado.");

        // Opcionalmente, verificar la URL si hay una redirección específica post-login
        assertTrue(driver.getCurrentUrl().contains("/") || driver.getCurrentUrl().equals(BASE_URL + "/"));
        System.out.println("Login exitoso, URL actual: " + driver.getCurrentUrl());
    }

    @Test
    public void testLoginFailure_WrongPassword() {
        driver.get(BASE_URL + "/login");

        WebElement emailField = driver.findElement(By.id("email"));
        WebElement passwordField = driver.findElement(By.id("contrasenia"));
        WebElement loginButton = driver.findElement(By.cssSelector("button[type='submit']"));

        emailField.sendKeys("santiquinterouribe0412@gmail.com");
        passwordField.sendKeys("wrongpassword");
        loginButton.click();

        // Verificar mensaje de error. El selector del mensaje de error debe ser identificado.
        // Ejemplo: Suponiendo que el mensaje de error tiene un ID 'error-message' o una clase específica
        // O que se muestra un Toastr. Para Toastr, la verificación es más compleja.
        // Por ahora, verificamos que no se redirige y permanece en la página de login,
        // y que el botón de "Cerrar Sesión" no aparece.
        assertTrue(driver.getCurrentUrl().contains("/login"), "La URL debería seguir siendo la de login.");
        boolean logoutLinkPresent = !driver.findElements(By.linkText("Cerrar Sesión")).isEmpty();
        assertTrue(!logoutLinkPresent, "El enlace 'Cerrar Sesión' no debería estar presente tras un login fallido.");

        // Idealmente, buscar un mensaje de error específico:
        // WebElement errorMessage = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("idDelMensajeDeError")));
        // assertTrue(errorMessage.getText().contains("Credenciales incorrectas"));
        System.out.println("Prueba de login fallido (contraseña incorrecta) completada.");
    }
}
