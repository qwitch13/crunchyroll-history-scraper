package com.crunchyroll.scraper;

import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Scrapes Crunchyroll watch history and exports to a log file.
 */
public class CrunchyrollHistoryScraper {
    private static final Logger LOG = LoggerFactory.getLogger(CrunchyrollHistoryScraper.class);

    private static final String LOGIN_URL = "https://www.crunchyroll.com/de/login";
    private static final String HISTORY_URL = "https://www.crunchyroll.com/de/history";
    private static final String HISTORY_URL_EN = "https://www.crunchyroll.com/history";
    private static final DateTimeFormatter FILE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd.HH-mm-ss");

    private final WebDriver driver;
    private final WebDriverWait wait;
    private final String email;
    private final String password;
    private final String profileName;
    private final Path outputPath;
    private final boolean manualMode;

    /**
     * Constructor for automatic mode (handles login automatically).
     */
    public CrunchyrollHistoryScraper(WebDriver driver, String email, String password, String profileName, Path outputPath) {
        this.driver = driver;
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(30));
        this.email = email;
        this.password = password;
        this.profileName = profileName;
        this.outputPath = outputPath;
        this.manualMode = false;
    }

    /**
     * Constructor for manual mode (user handles login in their browser).
     */
    public CrunchyrollHistoryScraper(WebDriver driver, Path outputPath) {
        this.driver = driver;
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(30));
        this.email = null;
        this.password = null;
        this.profileName = null;
        this.outputPath = outputPath;
        this.manualMode = true;
    }

    public void run() throws IOException {
        LOG.info("Starting Crunchyroll History Scraper");

        try {
            login();
            selectProfile();
            navigateToHistory();
            List<HistoryEntry> entries = scrapeHistory();
            exportToFile(entries);
            LOG.info("Scraping completed successfully! Found {} entries", entries.size());
        } catch (Exception e) {
            LOG.error("Scraping failed: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Manual mode: waits for user to navigate to history page, then scrapes.
     * This avoids bot detection by letting user handle login manually.
     */
    public void runManual() throws IOException {
        LOG.info("Starting Crunchyroll History Scraper (MANUAL MODE)");
        LOG.info("========================================");
        LOG.info("Please open Chrome with remote debugging:");
        LOG.info("  /Applications/Google\\ Chrome.app/Contents/MacOS/Google\\ Chrome --remote-debugging-port=9222");
        LOG.info("");
        LOG.info("Then:");
        LOG.info("  1. Navigate to https://www.crunchyroll.com");
        LOG.info("  2. Log in to your account manually");
        LOG.info("  3. Navigate to https://www.crunchyroll.com/history");
        LOG.info("");
        LOG.info("Waiting for history page...");
        LOG.info("========================================");

        try {
            waitForHistoryPage();
            LOG.info("History page detected! Starting scrape...");

            List<HistoryEntry> entries = scrapeHistory();
            exportToFile(entries);
            LOG.info("Scraping completed successfully! Found {} entries", entries.size());
        } catch (Exception e) {
            LOG.error("Scraping failed: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Waits for user to navigate to the Crunchyroll history page.
     */
    private void waitForHistoryPage() {
        LOG.info("Monitoring browser for history page...");

        WebDriverWait longWait = new WebDriverWait(driver, Duration.ofSeconds(600)); // 10 minutes
        longWait.until(driver -> {
            String url = driver.getCurrentUrl().toLowerCase();
            boolean isHistoryPage = url.contains("crunchyroll.com/history") ||
                    url.contains("crunchyroll.com/de/history") ||
                    url.contains("crunchyroll.com/en/history");

            if (!isHistoryPage) {
                // Log current URL every few seconds to show progress
                LOG.debug("Current URL: {} (waiting for history page)", url);
            }
            return isHistoryPage;
        });

        // Give page time to fully load
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void login() {
        LOG.info("Navigating to login page...");
        driver.get(LOGIN_URL);

        // Wait for and handle cookie consent if present
        handleCookieConsent();

        // Check for and wait for CAPTCHA to be solved
        waitForCaptcha();

        LOG.info("Entering credentials...");
        try {
            // Wait for SSO page to load (may redirect to sso.crunchyroll.com)
            Thread.sleep(3000);

            // Wait for email field - SSO page uses input fields
            WebElement emailField = wait.until(ExpectedConditions.elementToBeClickable(
                    By.cssSelector("input[type='email'], input[name='username'], input[autocomplete='email']")));
            emailField.clear();
            emailField.sendKeys(email);
            LOG.info("Email entered");

            // Wait a moment for any field validation
            Thread.sleep(500);

            // Find and fill password field - must wait for it to be interactable
            WebElement passwordField = wait.until(ExpectedConditions.elementToBeClickable(
                    By.cssSelector("input[type='password'], input[name='password'], input[autocomplete='current-password']")));
            passwordField.clear();
            passwordField.sendKeys(password);
            LOG.info("Password entered");

            // Wait a moment before clicking login
            Thread.sleep(1000);

            // Click login button using JavaScript for reliability
            WebElement loginButton = wait.until(ExpectedConditions.elementToBeClickable(
                    By.cssSelector("button[type='submit'], form button, button[class*='submit'], button[class*='login']")));

            // Try JavaScript click first (more reliable)
            JavascriptExecutor js = (JavascriptExecutor) driver;
            js.executeScript("arguments[0].click();", loginButton);
            LOG.info("Login button clicked via JavaScript");

            // Also try pressing Enter on password field as backup
            Thread.sleep(500);
            try {
                passwordField.sendKeys(Keys.RETURN);
                LOG.info("Enter key pressed");
            } catch (Exception ignored) {}

            // Wait for login to complete with longer timeout
            WebDriverWait longWait = new WebDriverWait(driver, Duration.ofSeconds(60));
            longWait.until(ExpectedConditions.or(
                    ExpectedConditions.urlContains("crunchyroll.com/home"),
                    ExpectedConditions.urlContains("crunchyroll.com/de"),
                    ExpectedConditions.urlContains("crunchyroll.com/history"),
                    ExpectedConditions.not(ExpectedConditions.urlContains("sso.crunchyroll.com")),
                    ExpectedConditions.presenceOfElementLocated(By.cssSelector("[data-t='header-profile-btn']")),
                    ExpectedConditions.presenceOfElementLocated(By.cssSelector(".erc-profile-menu")),
                    ExpectedConditions.presenceOfElementLocated(By.cssSelector("[class*='profile']"))
            ));

            LOG.info("Login successful!");
            Thread.sleep(2000); // Brief pause for page to stabilize
        } catch (Exception e) {
            LOG.error("Login failed. Taking screenshot for debugging...");
            takeScreenshot("login_failed");
            throw new RuntimeException("Login failed: " + e.getMessage(), e);
        }
    }

    private void handleCookieConsent() {
        try {
            Thread.sleep(2000); // Wait for cookie dialog to appear
            WebElement acceptButton = driver.findElement(By.cssSelector(
                    "button[data-t='cookie-consent-accept-all'], " +
                    "button.consent-btn, " +
                    "#onetrust-accept-btn-handler, " +
                    "button[aria-label*='Accept'], " +
                    "button[aria-label*='Akzeptieren']"));
            acceptButton.click();
            LOG.info("Cookie consent accepted");
            Thread.sleep(500);
        } catch (NoSuchElementException e) {
            LOG.debug("No cookie consent dialog found");
        } catch (Exception e) {
            LOG.debug("Could not handle cookie consent: {}", e.getMessage());
        }
    }

    private void waitForCaptcha() {
        LOG.info("Checking for CAPTCHA...");

        try {
            // Check if there's a CAPTCHA/verification challenge
            String pageSource = driver.getPageSource().toLowerCase();
            String currentUrl = driver.getCurrentUrl().toLowerCase();

            boolean hasCaptcha = pageSource.contains("captcha") ||
                    pageSource.contains("verify you are human") ||
                    pageSource.contains("verifying you are human") ||
                    pageSource.contains("challenge") ||
                    currentUrl.contains("challenge") ||
                    currentUrl.contains("captcha");

            if (hasCaptcha) {
                LOG.warn("========================================");
                LOG.warn("CAPTCHA DETECTED!");
                LOG.warn("Please solve the CAPTCHA in the browser window.");
                LOG.warn("The script will continue automatically once solved.");
                LOG.warn("========================================");

                takeScreenshot("captcha_detected");

                // Wait up to 5 minutes for CAPTCHA to be solved
                WebDriverWait captchaWait = new WebDriverWait(driver, Duration.ofSeconds(300));
                captchaWait.until(driver -> {
                    String source = driver.getPageSource().toLowerCase();
                    String url = driver.getCurrentUrl().toLowerCase();

                    // CAPTCHA is solved when we're no longer on a challenge page
                    boolean stillHasCaptcha = source.contains("verify you are human") ||
                            source.contains("verifying you are human") ||
                            url.contains("challenge");

                    if (!stillHasCaptcha) {
                        LOG.info("CAPTCHA solved! Continuing...");
                        return true;
                    }
                    return false;
                });

                // Give a moment for the page to settle
                Thread.sleep(2000);
            } else {
                LOG.info("No CAPTCHA detected");
            }
        } catch (Exception e) {
            LOG.warn("Error checking for CAPTCHA: {}. Continuing anyway...", e.getMessage());
        }
    }

    private void selectProfile() {
        LOG.info("Checking for profile selection page...");

        try {
            // Wait a bit for potential profile page
            Thread.sleep(3000);

            // Check if we're on a profile selection page
            String currentUrl = driver.getCurrentUrl();
            if (!currentUrl.contains("profile") && !currentUrl.contains("select")) {
                // Try to find profile elements anyway
                List<WebElement> profiles = driver.findElements(By.cssSelector(
                        "[data-t='profile-item'], [class*='profile-item'], [class*='ProfileItem'], " +
                        "div[class*='profile'] button, div[class*='Profile'] button"
                ));

                if (profiles.isEmpty()) {
                    LOG.info("No profile selection page detected, continuing...");
                    return;
                }
            }

            LOG.info("Profile selection page detected. Looking for profile: {}", profileName);
            takeScreenshot("profile_selection");

            // Find the profile by name
            WebElement targetProfile = null;

            // Try various selectors to find profiles
            List<WebElement> profileElements = driver.findElements(By.cssSelector(
                    "[data-t='profile-item'], [class*='profile'], [class*='Profile'], " +
                    "button[class*='profile'], div[role='button']"
            ));

            for (WebElement profile : profileElements) {
                String text = profile.getText().toLowerCase();
                if (text.contains(profileName.toLowerCase())) {
                    targetProfile = profile;
                    LOG.info("Found matching profile: {}", text);
                    break;
                }
            }

            // If not found by text, try finding by attribute
            if (targetProfile == null) {
                try {
                    targetProfile = driver.findElement(By.xpath(
                            "//*[contains(translate(text(), 'ABCDEFGHIJKLMNOPQRSTUVWXYZ', 'abcdefghijklmnopqrstuvwxyz'), '" +
                            profileName.toLowerCase() + "')]"
                    ));
                } catch (NoSuchElementException ignored) {}
            }

            if (targetProfile != null) {
                // Click the profile
                JavascriptExecutor js = (JavascriptExecutor) driver;
                js.executeScript("arguments[0].click();", targetProfile);
                LOG.info("Profile '{}' selected", profileName);

                // Wait for profile selection to complete
                Thread.sleep(3000);
            } else {
                LOG.warn("Could not find profile '{}'. Available profiles:", profileName);
                for (WebElement p : profileElements) {
                    LOG.warn("  - {}", p.getText());
                }
                // Click the first profile as fallback
                if (!profileElements.isEmpty()) {
                    JavascriptExecutor js = (JavascriptExecutor) driver;
                    js.executeScript("arguments[0].click();", profileElements.get(0));
                    LOG.info("Selected first available profile as fallback");
                    Thread.sleep(3000);
                }
            }
        } catch (Exception e) {
            LOG.warn("Error during profile selection: {}. Continuing anyway...", e.getMessage());
        }
    }

    private void navigateToHistory() {
        LOG.info("Navigating to history page...");
        driver.get(HISTORY_URL);

        try {
            // Wait for page to fully load
            Thread.sleep(5000);

            // Take screenshot to debug
            takeScreenshot("history_page");
            LOG.info("Current URL: {}", driver.getCurrentUrl());

            // Wait for any content to appear - broad selectors
            wait.until(ExpectedConditions.or(
                    ExpectedConditions.presenceOfElementLocated(By.cssSelector("[data-t='history-content']")),
                    ExpectedConditions.presenceOfElementLocated(By.cssSelector("[class*='history']")),
                    ExpectedConditions.presenceOfElementLocated(By.cssSelector("[class*='playable']")),
                    ExpectedConditions.presenceOfElementLocated(By.cssSelector("[class*='browse']")),
                    ExpectedConditions.presenceOfElementLocated(By.cssSelector("a[href*='/watch/']")),
                    ExpectedConditions.presenceOfElementLocated(By.tagName("article"))
            ));
            LOG.info("History page loaded");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            LOG.warn("Could not verify history page loaded, continuing anyway: {}", e.getMessage());
            takeScreenshot("history_page_error");
        }
    }

    private List<HistoryEntry> scrapeHistory() {
        LOG.info("Scraping history entries...");
        List<HistoryEntry> entries = new ArrayList<>();
        Set<String> seenUrls = new HashSet<>();

        // Take initial screenshot for debugging
        takeScreenshot("before_scraping");

        int previousCount = 0;
        int scrollAttempts = 0;
        int maxScrollAttempts = 100;

        while (scrollAttempts < maxScrollAttempts) {
            try {
                // Find all history cards/items
                List<WebElement> cards = findHistoryCards();

                for (WebElement card : cards) {
                    try {
                        HistoryEntry entry = extractEntryFromCard(card);
                        if (entry != null && entry.url() != null && !seenUrls.contains(entry.url())) {
                            seenUrls.add(entry.url());
                            entries.add(entry);
                        }
                    } catch (StaleElementReferenceException e) {
                        LOG.debug("Stale element, skipping...");
                    } catch (Exception e) {
                        LOG.debug("Error extracting entry: {}", e.getMessage());
                    }
                }

                LOG.info("Found {} unique entries so far...", entries.size());

                // Check if we found new entries
                if (entries.size() == previousCount) {
                    scrollAttempts++;
                    if (scrollAttempts >= 3) {
                        if (entries.size() > 0) {
                            LOG.info("No new entries found after {} scroll attempts, finishing...", scrollAttempts);
                        } else {
                            LOG.warn("No entries found. Taking debug screenshot...");
                            takeScreenshot("no_entries_found");
                        }
                        break;
                    }
                } else {
                    scrollAttempts = 0;
                    previousCount = entries.size();
                }

                // Scroll down to load more
                scrollDown();
                Thread.sleep(1500);

            } catch (NoSuchSessionException e) {
                LOG.error("Browser session lost. Entries collected so far: {}", entries.size());
                break;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                LOG.warn("Error during scraping: {}. Continuing...", e.getMessage());
                scrollAttempts++;
            }
        }

        LOG.info("Total unique entries found: {}", entries.size());
        return entries;
    }

    private List<WebElement> findHistoryCards() {
        List<WebElement> cards = new ArrayList<>();

        // Try multiple selectors for different Crunchyroll layouts
        String[] selectors = {
                ".history-playable-card",
                "[data-t='playable-card']",
                ".playable-card",
                ".erc-browse-collection .browse-card",
                ".watchlist-card",
                ".history-item",
                "[class*='history'] [class*='card']",
                "a[href*='/watch/']"
        };

        for (String selector : selectors) {
            try {
                cards = driver.findElements(By.cssSelector(selector));
                if (!cards.isEmpty()) {
                    LOG.debug("Found {} cards with selector: {}", cards.size(), selector);
                    return cards;
                }
            } catch (Exception e) {
                LOG.debug("Selector {} failed: {}", selector, e.getMessage());
            }
        }

        return cards;
    }

    private HistoryEntry extractEntryFromCard(WebElement card) {
        String seriesTitle = null;
        String episodeTitle = null;
        String seasonInfo = null;
        String episodeNumber = null;
        String watchedDate = null;
        String progress = null;
        String url = null;

        // Try to extract URL
        try {
            if ("a".equalsIgnoreCase(card.getTagName())) {
                url = card.getAttribute("href");
            } else {
                WebElement link = card.findElement(By.cssSelector("a[href*='/watch/']"));
                url = link.getAttribute("href");
            }
        } catch (Exception e) {
            LOG.debug("Could not find URL in card");
        }

        // Try to extract series title
        String[] seriesSelectors = {
                "[data-t='series-title']",
                ".series-title",
                "h5",
                "[class*='series']",
                ".title a"
        };
        for (String sel : seriesSelectors) {
            try {
                WebElement el = card.findElement(By.cssSelector(sel));
                seriesTitle = el.getText().trim();
                if (!seriesTitle.isEmpty()) break;
            } catch (Exception ignored) {}
        }

        // Try to extract episode title
        String[] episodeSelectors = {
                "[data-t='episode-title']",
                ".episode-title",
                "h6",
                "[class*='episode']",
                ".subtitle"
        };
        for (String sel : episodeSelectors) {
            try {
                WebElement el = card.findElement(By.cssSelector(sel));
                episodeTitle = el.getText().trim();
                if (!episodeTitle.isEmpty()) break;
            } catch (Exception ignored) {}
        }

        // Try to extract season/episode info
        String[] metaSelectors = {
                "[data-t='episode-info']",
                ".episode-info",
                "[class*='season']",
                ".meta-info",
                "span[class*='episode']"
        };
        for (String sel : metaSelectors) {
            try {
                WebElement el = card.findElement(By.cssSelector(sel));
                String text = el.getText().trim();
                if (!text.isEmpty()) {
                    // Parse "S1 E12" or "Season 1, Episode 12" formats
                    if (text.contains("S") && text.contains("E")) {
                        seasonInfo = text;
                    } else if (text.matches(".*\\d+.*")) {
                        episodeNumber = text;
                    }
                    break;
                }
            } catch (Exception ignored) {}
        }

        // Try to extract progress
        String[] progressSelectors = {
                "[data-t='progress']",
                ".progress-bar",
                "[class*='progress']"
        };
        for (String sel : progressSelectors) {
            try {
                WebElement el = card.findElement(By.cssSelector(sel));
                String width = el.getCssValue("width");
                String style = el.getAttribute("style");
                if (style != null && style.contains("width")) {
                    progress = style.replaceAll(".*width:\\s*([\\d.]+%).*", "$1");
                }
                break;
            } catch (Exception ignored) {}
        }

        // Try to extract watched date
        String[] dateSelectors = {
                "[data-t='watched-date']",
                ".watched-date",
                "time",
                "[class*='date']"
        };
        for (String sel : dateSelectors) {
            try {
                WebElement el = card.findElement(By.cssSelector(sel));
                watchedDate = el.getText().trim();
                if (watchedDate.isEmpty()) {
                    watchedDate = el.getAttribute("datetime");
                }
                if (watchedDate != null && !watchedDate.isEmpty()) break;
            } catch (Exception ignored) {}
        }

        // If we couldn't find structured data, try to parse the full card text
        if (seriesTitle == null && episodeTitle == null) {
            try {
                String fullText = card.getText().trim();
                if (!fullText.isEmpty()) {
                    String[] lines = fullText.split("\n");
                    if (lines.length > 0) seriesTitle = lines[0].trim();
                    if (lines.length > 1) episodeTitle = lines[1].trim();
                    if (lines.length > 2) seasonInfo = lines[2].trim();
                }
            } catch (Exception ignored) {}
        }

        if (url != null || seriesTitle != null) {
            return new HistoryEntry(seriesTitle, episodeTitle, seasonInfo, episodeNumber, watchedDate, progress, url);
        }

        return null;
    }

    private void scrollDown() {
        JavascriptExecutor js = (JavascriptExecutor) driver;
        js.executeScript("window.scrollBy(0, 800);");
    }

    private void exportToFile(List<HistoryEntry> entries) throws IOException {
        Path parent = outputPath.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }

        try (BufferedWriter writer = Files.newBufferedWriter(outputPath)) {
            writer.write("=".repeat(80));
            writer.newLine();
            writer.write("CRUNCHYROLL WATCH HISTORY EXPORT");
            writer.newLine();
            writer.write("Generated: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            writer.newLine();
            writer.write("Total Entries: " + entries.size());
            writer.newLine();
            writer.write("=".repeat(80));
            writer.newLine();
            writer.newLine();

            for (int i = 0; i < entries.size(); i++) {
                writer.write(String.format("[%d] %s", i + 1, entries.get(i).toLogLine()));
                writer.newLine();
                writer.newLine();
            }

            writer.write("=".repeat(80));
            writer.newLine();
            writer.write("END OF EXPORT");
            writer.newLine();
            writer.write("=".repeat(80));
            writer.newLine();
        }

        LOG.info("History exported to: {}", outputPath.toAbsolutePath());
    }

    private void takeScreenshot(String name) {
        try {
            if (driver instanceof TakesScreenshot) {
                byte[] screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
                Path screenshotPath = Path.of(System.getProperty("user.home"), "Documents",
                        name + "_" + System.currentTimeMillis() + ".png");
                Files.write(screenshotPath, screenshot);
                LOG.info("Screenshot saved: {}", screenshotPath);
            }
        } catch (Exception e) {
            LOG.warn("Failed to take screenshot: {}", e.getMessage());
        }
    }

    public static void main(String[] args) {
        boolean manualMode = false;
        String outputPathArg = null;

        // Parse arguments
        List<String> positionalArgs = new ArrayList<>();
        for (String arg : args) {
            if (arg.equals("--manual") || arg.equals("-m")) {
                manualMode = true;
            } else if (arg.startsWith("--output=")) {
                outputPathArg = arg.substring("--output=".length());
            } else if (!arg.startsWith("-")) {
                positionalArgs.add(arg);
            }
        }

        // Manual mode: connect to existing browser
        if (manualMode) {
            runManualMode(outputPathArg);
            return;
        }

        // Automatic mode: requires credentials
        if (positionalArgs.size() < 3) {
            printUsage();
            System.exit(1);
        }

        String email = positionalArgs.get(0);
        String password = positionalArgs.get(1);
        String profileName = positionalArgs.get(2);

        Path outputPath;
        if (positionalArgs.size() >= 4) {
            outputPath = Path.of(positionalArgs.get(3));
        } else if (outputPathArg != null) {
            outputPath = Path.of(outputPathArg);
        } else {
            String filename = LocalDateTime.now().format(FILE_FORMAT) + ".crunchy.log";
            outputPath = Path.of(System.getProperty("user.home"), "Documents", filename);
        }

        boolean headless = Boolean.parseBoolean(System.getProperty("headless", "false"));

        try (BrowserManager browserManager = new BrowserManager(headless)) {
            WebDriver driver = browserManager.initChrome();

            CrunchyrollHistoryScraper scraper = new CrunchyrollHistoryScraper(driver, email, password, profileName, outputPath);
            scraper.run();

        } catch (Exception e) {
            LOG.error("Scraper failed: {}", e.getMessage(), e);
            System.exit(1);
        }
    }

    private static void runManualMode(String outputPathArg) {
        Path outputPath;
        if (outputPathArg != null) {
            outputPath = Path.of(outputPathArg);
        } else {
            String filename = LocalDateTime.now().format(FILE_FORMAT) + ".crunchy.log";
            outputPath = Path.of(System.getProperty("user.home"), "Documents", filename);
        }

        int debugPort = Integer.parseInt(System.getProperty("debug.port",
                String.valueOf(BrowserManager.DEFAULT_DEBUG_PORT)));

        System.out.println();
        System.out.println("=== MANUAL MODE ===");
        System.out.println();
        System.out.println("Step 1: Start Chrome with remote debugging (if not already running):");
        System.out.println();
        System.out.println("  Mac:");
        System.out.println("    /Applications/Google\\ Chrome.app/Contents/MacOS/Google\\ Chrome --remote-debugging-port=" + debugPort);
        System.out.println();
        System.out.println("  Windows:");
        System.out.println("    chrome.exe --remote-debugging-port=" + debugPort);
        System.out.println();
        System.out.println("  Linux:");
        System.out.println("    google-chrome --remote-debugging-port=" + debugPort);
        System.out.println();
        System.out.println("Step 2: Log in to Crunchyroll manually in that browser");
        System.out.println();
        System.out.println("Step 3: Navigate to: https://www.crunchyroll.com/history");
        System.out.println();
        System.out.println("The scraper will automatically detect the history page and start scraping!");
        System.out.println();
        System.out.println("Connecting to Chrome on port " + debugPort + "...");
        System.out.println();

        try (BrowserManager browserManager = new BrowserManager(false)) {
            WebDriver driver = browserManager.connectToExistingChrome(debugPort);

            CrunchyrollHistoryScraper scraper = new CrunchyrollHistoryScraper(driver, outputPath);
            scraper.runManual();

        } catch (Exception e) {
            LOG.error("Scraper failed: {}", e.getMessage(), e);
            System.out.println();
            System.out.println("ERROR: Could not connect to Chrome. Make sure:");
            System.out.println("  1. Chrome is running with --remote-debugging-port=" + debugPort);
            System.out.println("  2. No other process is using port " + debugPort);
            System.out.println();
            System.exit(1);
        }
    }

    private static void printUsage() {
        System.out.println("Crunchyroll History Scraper");
        System.out.println();
        System.out.println("Usage:");
        System.out.println();
        System.out.println("  MANUAL MODE (recommended - avoids bot detection):");
        System.out.println("    java -jar crunchyroll-scraper.jar --manual [--output=path]");
        System.out.println();
        System.out.println("    Connects to your existing Chrome browser where you've already logged in.");
        System.out.println("    Start Chrome with: --remote-debugging-port=9222");
        System.out.println();
        System.out.println("  AUTOMATIC MODE (may trigger bot detection):");
        System.out.println("    java -jar crunchyroll-scraper.jar <email> <password> <profile> [output-path]");
        System.out.println();
        System.out.println("Arguments:");
        System.out.println("  --manual, -m    Use manual mode (connect to existing Chrome)");
        System.out.println("  --output=PATH   Specify output file path");
        System.out.println("  email           Your Crunchyroll email (automatic mode)");
        System.out.println("  password        Your Crunchyroll password (automatic mode)");
        System.out.println("  profile         Profile name to select (automatic mode)");
        System.out.println();
        System.out.println("Environment Variables:");
        System.out.println("  -Ddebug.port=PORT   Chrome debug port (default: 9222)");
        System.out.println("  -Dheadless=true     Run in headless mode (automatic mode only)");
        System.out.println();
        System.out.println("Output:");
        System.out.println("  Default: ~/Documents/YYYY-MM-DD.HH-mm-ss.crunchy.log");
    }
}
