package selenium;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Disabled;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("e2e")
@Disabled("E2E Selenium tests disabled for CI/local build")
public class LecturaTitulo {

    private WebDriver driver;

    @BeforeEach
    public void setUp() {
        // Asegúrate de tener el driver correcto según tu sistema operativo
        System.setProperty("webdriver.chrome.driver", "C:/Users/user/Downloads/chromedriver-win64/chromedriver.exe");
        driver = new ChromeDriver();
    }

    @Test
    public void testHomePageTitle() {
        driver.get("http://localhost:4200");
        String expectedTitle = "TiendaSanaFrontend";
        assertEquals(expectedTitle, driver.getTitle());
    }

    @AfterEach
    public void tearDown() {
        if (driver != null) {
            driver.quit();
        }
    }
}
