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

#### Current Status:
Project complete! Ready for testing with real Crunchyroll credentials.

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

## Usage

### Build
```bash
mvn clean package
```

### Run
```bash
java -jar target/crunchyroll-history-scraper-1.0.0.jar <email> <password> [output-path]
```

Or with Maven:
```bash
mvn exec:java -Dexec.args="email@example.com password123"
```

### Headless Mode
```bash
java -Dheadless=true -jar target/crunchyroll-history-scraper-1.0.0.jar <email> <password>
```

---

## Resume Instructions
If interrupted, check the "Current Status" section above and continue from there.
