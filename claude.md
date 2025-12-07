# Crunchyroll History Scraper - Development Journal

## Project Overview
Java 21 program to scrape Crunchyroll watch history and export to a log file.

## Requirements
- Accept Crunchyroll login credentials (email/password)
- Navigate to https://www.crunchyroll.com/de/history
- Scroll through entire history list
- Compile complete list of watched episodes
- Output to `~/Documents/YYYY-MM-DD.HH-mm-ss.crunchy.log` or user-specified location

## Progress

### Session 1 - 2025-12-06

#### Completed:
- [x] Project structure created
- [x] Maven/Gradle dependencies configured
- [x] Login implementation
- [x] History scrolling logic
- [x] Data extraction
- [x] File output
- [x] GitHub repos created and code pushed

#### Technical Decisions:
- **Web Automation**: Selenium WebDriver (mature, Java-native support)
- **Browser**: Chrome/Chromium with ChromeDriver (auto-managed via WebDriverManager)
- **Build Tool**: Maven (standard for Java projects)
- **Data Format**: Plain text log with episode details

#### Files Created:
- `pom.xml` - Maven configuration with shade plugin for fat JAR
- `src/main/java/com/crunchyroll/scraper/CrunchyrollHistoryScraper.java` - Main class with login, scrolling, extraction
- `src/main/java/com/crunchyroll/scraper/HistoryEntry.java` - Data model (Java record)
- `src/main/java/com/crunchyroll/scraper/BrowserManager.java` - Browser setup (Chrome/Firefox support)

#### GitHub Repositories:
- https://github.com/nebulai13/crunchyroll-history-scraper
- https://github.com/qwitch13/crunchyroll-history-scraper

---

### Session 2 - 2025-12-07

#### Issue:
Automatic login was being detected as bot/automation by Crunchyroll.

#### Solution:
Added **Manual Mode** that avoids bot detection by:
1. User starts Chrome with remote debugging enabled (`--remote-debugging-port=9222`)
2. User logs into Crunchyroll manually in their normal browser
3. User navigates to the history page
4. Scraper connects to the existing Chrome session via debug port
5. Scraper detects history page and begins scraping automatically

#### Completed:
- [x] Added `connectToExistingChrome()` method to BrowserManager
- [x] Added `runManual()` method that waits for history page
- [x] Added `waitForHistoryPage()` method to detect when user is ready
- [x] Updated CLI to support `--manual` / `-m` flag
- [x] Added `--output=PATH` option for output file
- [x] Updated help/usage text with both modes
- [x] Browser stays open in manual mode (doesn't quit on close)

#### Current Status:
Both automatic and manual modes available. Manual mode recommended to avoid bot detection.

---

## Usage

### Build
```bash
mvn clean package
```

### Manual Mode (Recommended)
Avoids bot detection by connecting to your existing Chrome session.

**Step 1:** Start Chrome with remote debugging:
```bash
# Mac
/Applications/Google\ Chrome.app/Contents/MacOS/Google\ Chrome --remote-debugging-port=9222

# Windows
chrome.exe --remote-debugging-port=9222

# Linux
google-chrome --remote-debugging-port=9222
```

**Step 2:** In that Chrome window:
1. Navigate to https://www.crunchyroll.com
2. Log in to your account
3. Navigate to https://www.crunchyroll.com/history

**Step 3:** Run the scraper:
```bash
java -jar target/crunchyroll-history-scraper-1.0.0.jar --manual
```

The scraper will automatically detect the history page and start scraping.

### Automatic Mode (May Trigger Bot Detection)
```bash
java -jar target/crunchyroll-history-scraper-1.0.0.jar <email> <password> <profile> [output-path]
```

### Options
```
--manual, -m         Use manual mode (connect to existing Chrome)
--output=PATH        Specify output file path
-Ddebug.port=PORT    Chrome debug port (default: 9222)
-Dheadless=true      Run headless (automatic mode only)
```

---

## Resume Instructions
If interrupted, check the "Current Status" section above and continue from there.
