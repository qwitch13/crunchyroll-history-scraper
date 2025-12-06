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

    private WebDriver driver;
    private final boolean headless;

    public BrowserManager(boolean headless) {
        this.headless = headless;
    }

    public WebDriver initChrome() {
        LOG.info("Setting up Chrome WebDriver...");
        WebDriverManager.chromedriver().setup();

        ChromeOptions options = new ChromeOptions();
        if (headless) {
            options.addArguments("--headless=new");
        }
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");
        options.addArguments("--disable-gpu");
        options.addArguments("--window-size=1920,1080");
        options.addArguments("--lang=en-US");
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.setExperimentalOption("excludeSwitches", new String[]{"enable-automation"});

        driver = new ChromeDriver(options);
        configureTimeouts();

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
            LOG.info("Closing WebDriver...");
            try {
                driver.quit();
            } catch (Exception e) {
                LOG.warn("Error closing WebDriver: {}", e.getMessage());
            }
        }
    }
}
