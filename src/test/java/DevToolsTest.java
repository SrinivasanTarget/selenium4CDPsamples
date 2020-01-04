
import static java.time.Duration.ofSeconds;
import static org.openqa.selenium.devtools.network.Network.*;
import static org.openqa.selenium.devtools.performance.Performance.getMetrics;
import static org.openqa.selenium.support.ui.ExpectedConditions.elementToBeClickable;
import static java.util.Optional.of;
import static java.util.Optional.empty;
import static org.testng.Assert.*;

import com.google.common.collect.ImmutableList;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.By;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.devtools.DevTools;
import org.openqa.selenium.devtools.Log;
import org.openqa.selenium.devtools.network.Network;
import org.openqa.selenium.devtools.network.model.*;
import org.openqa.selenium.devtools.performance.Performance;
import org.openqa.selenium.devtools.performance.model.Metric;
import org.openqa.selenium.devtools.performance.model.TimeDomain;
import org.openqa.selenium.devtools.profiler.Profiler;
import org.openqa.selenium.devtools.profiler.model.ScriptCoverage;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.*;

public class DevToolsTest {

    private DevTools devTools;
    private ChromeDriver driver;
    private WebDriverWait webDriverWait;

    private String safariUserAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 11_0 like Mac OS X) AppleWebKit/604.1.38 (KHTML, like Gecko) Version/11.0 Mobile/15A372 Safari/604.1";

    private By whereAmI = By.tagName("button");
    private By network = By.id("network");
    private By orangeButton = By.className("button-orange");
    private By timer = By.className("comment");
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
        params.put("latitude", 51.5055);
        params.put("longitude", 0.0754);
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
        assertEquals(devTools.send(getAllCookies()).asSeleniumCookies().size(), 0);
    }

    @Test
    public void getPerformanceMetricsByTimeTicks() {
        devTools.send(Performance.setTimeDomain(TimeDomain.timeTicks));
        devTools.send(Performance.enable());
        driver.get("https://testproject.io/");
        List<Metric> metrics = devTools.send(getMetrics());
        metrics.forEach(metric -> System.out.println(metric.getName() + ":" + metric.getValue()));
        assertFalse(metrics.isEmpty());
        devTools.send(disable());
    }

    @Test
    public void consoleErrorTest() {
        devTools.send(Log.enable());
        driver.get("https://devtools.glitch.me/console/log.html");
        devTools.addListener(Log.entryAdded(), logEntry -> {
//            System.out.println(logEntry.asSeleniumLogEntry().getMessage());
            assertEquals(logEntry.asSeleniumLogEntry().getLevel().getName(), "SEVERE");
        });
        webDriverWait.until(elementToBeClickable(network)).click();
        devTools.send(Log.disable());
    }

    @Test
    public void blockURLTest() {
        devTools.send(Network.enable(of(100000000), empty(), empty()));
        devTools.send(Network.setBlockedURLs(ImmutableList.of("https://blog.testproject.io/wp-content/uploads/2019/10/pop-up-illustration.png")));

        devTools.addListener(loadingFailed(), loadingFailed -> {
            assertEquals(loadingFailed.getBlockedReason(), BlockedReason.inspector);
        });

        driver.get("https://blog.testproject.io/2019/11/26/next-generation-front-end-testing-using-webdriver-and-devtools-part-1/");
        devTools.send(Network.disable());
    }

    @Test
    public void emulateDeviceTest() {
        devTools.send(enable(of(100000000), empty(), empty()));
        Map<String, Object> userAgent = new HashMap<>();
        userAgent.put("userAgent", safariUserAgent);
        driver.executeCdpCommand("Emulation.setUserAgentOverride", userAgent);

        Map<String, Object> deviceMetrics = new HashMap<>();
        deviceMetrics.put("width", 375);
        deviceMetrics.put("height", 812);
        deviceMetrics.put("deviceScaleFactor", 3);
        deviceMetrics.put("mobile", true);
        deviceMetrics.put("scale", 1);
        driver.executeCdpCommand("Emulation.setDeviceMetricsOverride", deviceMetrics);
        driver.get("https://testproject.io/");
        webDriverWait.until(elementToBeClickable(orangeButton));
        assertNotNull(webDriverWait.until(elementToBeClickable(orangeButton)).getText());
    }

    @Test
    public void emulateTimezoneTest() {
        Map<String, Object> deviceMetrics = new HashMap<>();
        deviceMetrics.put("timezoneId", "Antarctica/Casey");

        devTools.send(enable(of(100000000), empty(), empty()));
        driver.executeCdpCommand("Emulation.setTimezoneOverride", deviceMetrics);
        driver.get("https://momentjs.com/");
        webDriverWait.until(elementToBeClickable(timer));
    }

    @Test
    public void coverageTest() {
        devTools.send(Profiler.enable());
        driver.get("https://github.com/");
        devTools.send(Profiler.setSamplingInterval(30));
        List<ScriptCoverage> bestEffort = devTools.send(Profiler.getBestEffortCoverage());
        bestEffort.stream().filter(scriptCoverage -> {
            System.out.println(scriptCoverage);
            boolean contains = scriptCoverage.getUrl().contains("github-bootstrap");
            return contains;
        });
        assertNotNull(bestEffort);
        assertFalse(bestEffort.isEmpty());
        devTools.send(Profiler.disable());
    }

    @AfterMethod
    public void after() {
        devTools.close();
        driver.quit();
    }
}
