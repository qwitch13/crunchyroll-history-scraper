package com.crunchyroll.scraper;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * Manages browser/WebDriver lifecycle.
 */
public class BrowserManager implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(BrowserManager.class);
    public static final int DEFAULT_DEBUG_PORT = 9222;

    private WebDriver driver;
    private final boolean headless;
    private boolean isManualMode = false;

    public BrowserManager(boolean headless) {
        this.headless = headless;
    }

    /**
     * Connect to an existing Chrome browser with remote debugging enabled.
     * User must start Chrome with: --remote-debugging-port=9222
     */
    public WebDriver connectToExistingChrome(int debugPort) {
        LOG.info("Connecting to existing Chrome on debug port {}...", debugPort);
        WebDriverManager.chromedriver().setup();

        ChromeOptions options = new ChromeOptions();
        options.setExperimentalOption("debuggerAddress", "localhost:" + debugPort);

        driver = new ChromeDriver(options);
        configureTimeouts();
        isManualMode = true;

        LOG.info("Connected to existing Chrome browser successfully");
        return driver;
    }

    public boolean isManualMode() {
        return isManualMode;
    }

    public WebDriver initChrome() {
        LOG.info("Setting up Chrome WebDriver...");
        WebDriverManager.chromedriver().setup();

        ChromeOptions options = new ChromeOptions();
        if (headless) {
            options.addArguments("--headless=new");
        }

        // Anti-detection options
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-gpu");
        options.addArguments("--window-size=1920,1080");
        options.addArguments("--lang=de-DE,de");
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.addArguments("--disable-extensions");
        options.addArguments("--disable-infobars");
        options.addArguments("--disable-popup-blocking");
        options.addArguments("--start-maximized");
        options.addArguments("--ignore-certificate-errors");

        // Set a real user agent
        options.addArguments("--user-agent=Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");

        // Experimental options to avoid detection
        options.setExperimentalOption("excludeSwitches", new String[]{"enable-automation", "enable-logging"});
        options.setExperimentalOption("useAutomationExtension", false);

        // Preferences to avoid detection
        java.util.Map<String, Object> prefs = new java.util.HashMap<>();
        prefs.put("credentials_enable_service", false);
        prefs.put("profile.password_manager_enabled", false);
        options.setExperimentalOption("prefs", prefs);

        driver = new ChromeDriver(options);
        configureTimeouts();

        // Execute script to mask webdriver
        try {
            ((org.openqa.selenium.JavascriptExecutor) driver).executeScript(
                "Object.defineProperty(navigator, 'webdriver', {get: () => undefined});"
            );
        } catch (Exception e) {
            LOG.debug("Could not mask webdriver property: {}", e.getMessage());
        }

        LOG.info("Chrome WebDriver initialized successfully");
        return driver;
    }

    public WebDriver initFirefox() {
        LOG.info("Setting up Firefox WebDriver...");
        WebDriverManager.firefoxdriver().setup();

        FirefoxOptions options = new FirefoxOptions();
        if (headless) {
            options.addArguments("--headless");
        }
        options.addArguments("--width=1920");
        options.addArguments("--height=1080");

        driver = new FirefoxDriver(options);
        configureTimeouts();

        LOG.info("Firefox WebDriver initialized successfully");
        return driver;
    }

    private void configureTimeouts() {
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(60));
        driver.manage().timeouts().scriptTimeout(Duration.ofSeconds(30));
    }

    public WebDriver getDriver() {
        return driver;
    }

    @Override
    public void close() {
        if (driver != null) {
            if (isManualMode) {
                LOG.info("Manual mode: leaving browser open for user");
            } else {
                LOG.info("Closing WebDriver...");
                try {
                    driver.quit();
                } catch (Exception e) {
                    LOG.warn("Error closing WebDriver: {}", e.getMessage());
                }
            }
        }
    }
}
