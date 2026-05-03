package selenium;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;


@Tag("e2e")
public class RegisterTest extends BaseTest { // Asegúrate de que BaseTest esté correctamente configurado

    @Test
    public void testSuccessfulRegistration() {
        driver.get(BASE_URL + "/register"); // La URL base debe estar definida en BaseTest

        // Identificadores de los campos del formulario de registro según el HTML proporcionado
        WebElement nombreField = driver.findElement(By.id("nombre"));
        // Nota: El campo 'apellido' no está en el HTML proporcionado. Si es necesario, añádelo.
        // WebElement apellidoField = driver.findElement(By.id("apellido"));
        WebElement direccionField = driver.findElement(By.id("direccion"));
        WebElement telefonoField = driver.findElement(By.id("telefono"));
        WebElement emailField = driver.findElement(By.id("email"));
        WebElement contraseniaField = driver.findElement(By.id("contrasenia"));
        WebElement confirmarContraseniaField = driver.findElement(By.id("confirmacionContrasena")); // ID corregido
        WebElement termsCheckbox = driver.findElement(By.id("terms"));

        // El selector del botón de registro puede ser más específico si es necesario.
        // Por ejemplo, si el texto es constante: By.xpath("//button[contains(text(),'Registrarse')]")
        // O si tiene una clase única además de btn-primary.
        WebElement registerButton = driver.findElement(By.cssSelector("button.btn-primary[type='submit']"));


        // Usar un email único para cada ejecución de prueba
        String uniqueEmail = "testuser" + System.currentTimeMillis() + "@example.com";

        nombreField.sendKeys("Tester Selenium");
        // apellidoField.sendKeys("UsuarioPrueba"); // Si se añade el campo apellido
        direccionField.sendKeys("Calle Falsa 123, Ciudad Test");
        telefonoField.sendKeys("3001234560");
        emailField.sendKeys(uniqueEmail);
        contraseniaField.sendKeys("Password123!");
        confirmarContraseniaField.sendKeys("Password123!");

        // Hacer clic en el checkbox de términos y condiciones
        if (!termsCheckbox.isSelected()) {
            termsCheckbox.click();
        }
        assertTrue(termsCheckbox.isSelected(), "El checkbox de términos y condiciones debería estar seleccionado.");

        // Esperar a que el botón de registro sea clickeable, especialmente si hay validaciones asíncronas
        wait.until(ExpectedConditions.elementToBeClickable(registerButton));
        registerButton.click();

        // Verificar redirección a la página de login.
        // Es importante que la espera explícita (wait) esté configurada en BaseTest.
        wait.until(ExpectedConditions.urlContains("/login"));
        assertTrue(driver.getCurrentUrl().contains("/login"), "No se redirigió a la página de login después del registro.");

        System.out.println("Registro completado, redirigido a login. Email usado: " + uniqueEmail);
    }

    @Test
    public void testRegistrationWithMismatchedPasswords() {
        driver.get(BASE_URL + "/registro");

        WebElement nombreField = driver.findElement(By.id("nombre"));
        // WebElement apellidoField = driver.findElement(By.id("apellido")); // Si se añade
        WebElement direccionField = driver.findElement(By.id("direccion"));
        WebElement telefonoField = driver.findElement(By.id("telefono"));
        WebElement emailField = driver.findElement(By.id("email"));
        WebElement contraseniaField = driver.findElement(By.id("contrasenia"));
        WebElement confirmarContraseniaField = driver.findElement(By.id("confirmacionContrasena"));
        WebElement termsCheckbox = driver.findElement(By.id("terms"));
        WebElement registerButton = driver.findElement(By.cssSelector("button.btn-primary[type='submit']"));

        String uniqueEmailMismatch = "mismatch" + System.currentTimeMillis() + "@example.com";

        nombreField.sendKeys("Test Mismatch");
        // apellidoField.sendKeys("User Mismatch"); // Si se añade
        direccionField.sendKeys("Otra Calle Falsa 456");
        telefonoField.sendKeys("3009876543");
        emailField.sendKeys(uniqueEmailMismatch);
        contraseniaField.sendKeys("Password123!");
        confirmarContraseniaField.sendKeys("Password456!"); // Contraseña diferente

        if (!termsCheckbox.isSelected()) {
            termsCheckbox.click();
        }
        assertTrue(termsCheckbox.isSelected(), "El checkbox de términos y condiciones debería estar seleccionado.");

        // Opción 1: Verificar si el botón está deshabilitado (si Angular lo deshabilita)
        // Esta es la aserción actual de tu prueba. Puede que necesite un pequeño tiempo para que Angular actualice el estado del botón.
        // A veces, es mejor intentar hacer clic y esperar un error, o verificar un mensaje de error.
        // Para que isEnabled() funcione de manera fiable, el estado [disabled] debe estar presente en el DOM.
        // assertTrue(!registerButton.isEnabled(), "El botón de registro debería estar deshabilitado si las contraseñas no coinciden.");

        // Opción 2: Verificar si aparece el mensaje de error de contraseñas no coincidentes
        // Esto es más robusto si el botón no se deshabilita pero se muestra un error.
        // El HTML tiene: <div class="password-mismatch-alert">...Las contraseñas no coinciden...</div>
        // Es posible que este mensaje solo aparezca después de un intento de submit o al perder el foco del campo.
        // Para probarlo, podrías hacer clic en el botón si está habilitado, o simular la pérdida de foco.

        // Por ahora, mantendremos la lógica de que el botón se deshabilita,
        // pero si esto falla, considera verificar el mensaje de error.
        // Si el botón SÍ se puede clickear, y luego muestra el error:
        if (registerButton.isEnabled()) {
            registerButton.click();
            WebElement mismatchAlert = wait.until(ExpectedConditions.visibilityOfElementLocated(By.className("password-mismatch-alert")));
            assertTrue(mismatchAlert.isDisplayed(), "Debería mostrarse la alerta de contraseñas no coincidentes.");
            assertTrue(mismatchAlert.getText().contains("Las contraseñas no coinciden"), "El texto de la alerta no es el esperado.");
        } else {
            // Si el botón está deshabilitado desde el principio por la validación de Angular
            assertFalse(registerButton.isEnabled(), "El botón de registro debería estar deshabilitado si las contraseñas no coinciden y hay validación en tiempo real.");
        }

        System.out.println("Prueba de registro con contraseñas no coincidentes completada.");
    }
}
