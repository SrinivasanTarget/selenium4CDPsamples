
import static java.time.Duration.ofSeconds;
import static org.openqa.selenium.devtools.network.Network.*;
import static org.openqa.selenium.support.ui.ExpectedConditions.elementToBeClickable;
import static java.util.Optional.of;
import static java.util.Optional.empty;
import static org.testng.Assert.*;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.By;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.devtools.DevTools;
import org.openqa.selenium.devtools.network.Network;
import org.openqa.selenium.devtools.network.model.*;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.*;

public class DevToolsTest {

    private DevTools devTools;
    private ChromeDriver driver;
    private WebDriverWait webDriverWait;

    private By whereAmI = By.tagName("button");
    private By latitude = By.id("lat-value");
    private By longitude = By.id("long-value");

    @BeforeMethod
    public void before(){
        WebDriverManager.chromedriver().setup();
        driver = new ChromeDriver();
        devTools = driver.getDevTools();
        devTools.createSession();
        webDriverWait = new WebDriverWait(driver, ofSeconds(3));
    }

    @Test
    public void setGeoLocationTest() {
        driver.get("https://the-internet.herokuapp.com/geolocation");
        webDriverWait.until(elementToBeClickable(whereAmI)).click();
        String lat = webDriverWait.until(elementToBeClickable(latitude)).getText();
        String lng = driver.findElement(longitude).getText();

        Map<String, Object> params = new HashMap<>();
        params.put("latitude", 27.1751);
        params.put("longitude", 78.0421);
        params.put("accuracy", 1);

        driver.executeCdpCommand("Emulation.setGeolocationOverride", params);
        webDriverWait.until(elementToBeClickable(whereAmI)).click();

        assertNotEquals(webDriverWait.until(elementToBeClickable(latitude)).getText(), lat);
        assertNotEquals(driver.findElement(longitude).getText(), lng);
    }

    @Test
    public void emulateNetworkConditionsTest() {
        devTools.send(enable(of(100000000), empty(), empty()));
        devTools.send(
                emulateNetworkConditions(false,
                        100,
                        1000,
                        2000,
                        of(ConnectionType.cellular3g)));
        driver.get("https://seleniumconf.co.uk");
    }

    @Test
    public void validateNetworkResponseTest() {
        final RequestId[] requestIds = new RequestId[1];
        devTools.send(Network.enable(of(100000000), empty(), empty()));

        devTools.addListener(responseReceived(), responseReceived -> {
            assertNotNull(responseReceived.getResponse());
            requestIds[0] = responseReceived.getRequestId();
        });

        driver.navigate().to("http://dummy.restapiexample.com/api/v1/employee/1");
        ResponseBody responseBody = devTools.send(getResponseBody(requestIds[0]));
        assertTrue(responseBody.getBody().contains("id"));
    }

    @Test
    public void validateCookiesTest() {
        devTools.send(Network.enable(of(100000000), empty(), empty()));

        driver.navigate().to("https://ads.google.com/intl/en_IN/home/");
        Cookies allCookies = devTools.send(getAllCookies());
        assertNotNull(allCookies.asSeleniumCookies().stream().filter(cookie ->
                "_ga".equalsIgnoreCase(cookie.getName())).findAny().orElse(null));

        devTools.send(clearBrowserCookies());
        assertTrue(devTools.send(getAllCookies()).asSeleniumCookies().size() == 0);
    }

    @AfterMethod
    public void after() {
        driver.quit();
    }
}
