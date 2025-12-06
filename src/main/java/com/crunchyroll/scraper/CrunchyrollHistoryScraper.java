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
    private static final DateTimeFormatter FILE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd.HH-mm-ss");

    private final WebDriver driver;
    private final WebDriverWait wait;
    private final String email;
    private final String password;
    private final Path outputPath;

    public CrunchyrollHistoryScraper(WebDriver driver, String email, String password, Path outputPath) {
        this.driver = driver;
        this.wait = new WebDriverWait(driver, Duration.ofSeconds(30));
        this.email = email;
        this.password = password;
        this.outputPath = outputPath;
    }

    public void run() throws IOException {
        LOG.info("Starting Crunchyroll History Scraper");

        try {
            login();
            navigateToHistory();
            List<HistoryEntry> entries = scrapeHistory();
            exportToFile(entries);
            LOG.info("Scraping completed successfully! Found {} entries", entries.size());
        } catch (Exception e) {
            LOG.error("Scraping failed: {}", e.getMessage(), e);
            throw e;
        }
    }

    private void login() {
        LOG.info("Navigating to login page...");
        driver.get(LOGIN_URL);

        // Wait for and handle cookie consent if present
        handleCookieConsent();

        LOG.info("Entering credentials...");
        try {
            // Wait for email field
            WebElement emailField = wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.cssSelector("input[name='username'], input[type='email'], input#login_form_name")));
            emailField.clear();
            emailField.sendKeys(email);

            // Find and fill password field
            WebElement passwordField = driver.findElement(
                    By.cssSelector("input[name='password'], input[type='password'], input#login_form_password"));
            passwordField.clear();
            passwordField.sendKeys(password);

            // Click login button
            WebElement loginButton = driver.findElement(
                    By.cssSelector("button[type='submit'], button[data-t='login-btn'], .login-button"));
            loginButton.click();

            // Wait for login to complete - check for profile or history link
            wait.until(ExpectedConditions.or(
                    ExpectedConditions.urlContains("/home"),
                    ExpectedConditions.urlContains("/de"),
                    ExpectedConditions.presenceOfElementLocated(By.cssSelector("[data-t='header-profile-btn']")),
                    ExpectedConditions.presenceOfElementLocated(By.cssSelector(".erc-profile-menu"))
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

    private void navigateToHistory() {
        LOG.info("Navigating to history page...");
        driver.get(HISTORY_URL);

        try {
            // Wait for history page to load
            wait.until(ExpectedConditions.or(
                    ExpectedConditions.presenceOfElementLocated(By.cssSelector("[data-t='history-content']")),
                    ExpectedConditions.presenceOfElementLocated(By.cssSelector(".history-content")),
                    ExpectedConditions.presenceOfElementLocated(By.cssSelector(".erc-history-content")),
                    ExpectedConditions.presenceOfElementLocated(By.cssSelector(".watchlist-card")),
                    ExpectedConditions.presenceOfElementLocated(By.cssSelector("[class*='history']"))
            ));
            LOG.info("History page loaded");
        } catch (Exception e) {
            LOG.warn("Could not verify history page loaded, continuing anyway: {}", e.getMessage());
        }
    }

    private List<HistoryEntry> scrapeHistory() {
        LOG.info("Scraping history entries...");
        List<HistoryEntry> entries = new ArrayList<>();
        Set<String> seenUrls = new HashSet<>();

        int previousCount = 0;
        int scrollAttempts = 0;
        int maxScrollAttempts = 100;

        while (scrollAttempts < maxScrollAttempts) {
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
                    // Element became stale, will be re-fetched on next iteration
                    LOG.debug("Stale element, skipping...");
                } catch (Exception e) {
                    LOG.debug("Error extracting entry: {}", e.getMessage());
                }
            }

            LOG.info("Found {} unique entries so far...", entries.size());

            // Check if we found new entries
            if (entries.size() == previousCount) {
                scrollAttempts++;
                if (scrollAttempts >= 3 && entries.size() > 0) {
                    LOG.info("No new entries found after {} scroll attempts, finishing...", scrollAttempts);
                    break;
                }
            } else {
                scrollAttempts = 0;
                previousCount = entries.size();
            }

            // Scroll down to load more
            scrollDown();
            try {
                Thread.sleep(1500); // Wait for content to load
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
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
        if (args.length < 2) {
            System.out.println("Usage: java -jar crunchyroll-scraper.jar <email> <password> [output-path]");
            System.out.println();
            System.out.println("Arguments:");
            System.out.println("  email        Your Crunchyroll email");
            System.out.println("  password     Your Crunchyroll password");
            System.out.println("  output-path  (Optional) Path for output file");
            System.out.println("               Default: ~/Documents/YYYY-MM-DD.HH-mm-ss.crunchy.log");
            System.exit(1);
        }

        String email = args[0];
        String password = args[1];

        Path outputPath;
        if (args.length >= 3) {
            outputPath = Path.of(args[2]);
        } else {
            String filename = LocalDateTime.now().format(FILE_FORMAT) + ".crunchy.log";
            outputPath = Path.of(System.getProperty("user.home"), "Documents", filename);
        }

        boolean headless = Boolean.parseBoolean(System.getProperty("headless", "false"));

        try (BrowserManager browserManager = new BrowserManager(headless)) {
            WebDriver driver = browserManager.initChrome();

            CrunchyrollHistoryScraper scraper = new CrunchyrollHistoryScraper(driver, email, password, outputPath);
            scraper.run();

        } catch (Exception e) {
            LOG.error("Scraper failed: {}", e.getMessage(), e);
            System.exit(1);
        }
    }
}
