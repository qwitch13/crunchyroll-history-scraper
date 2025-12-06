# Crunchyroll History Scraper

A Java 21 CLI tool that scrapes your Crunchyroll watch history and exports it to a log file.

## Features

- Automated login to Crunchyroll
- Scrolls through entire watch history
- Extracts episode details (series, season, episode, progress)
- Exports to timestamped log file
- Supports headless browser mode
- Chrome and Firefox browser support

## Requirements

- Java 21+
- Maven 3.6+
- Chrome or Firefox browser

## Build

```bash
mvn clean package
```

## Usage

```bash
java -jar target/crunchyroll-history-scraper-1.0.0.jar <email> <password> [output-path]
```

### Arguments

| Argument | Required | Description |
|----------|----------|-------------|
| email | Yes | Your Crunchyroll email |
| password | Yes | Your Crunchyroll password |
| output-path | No | Custom output file path (default: `~/Documents/YYYY-MM-DD.HH-mm-ss.crunchy.log`) |

### Examples

```bash
# Basic usage
java -jar target/crunchyroll-history-scraper-1.0.0.jar user@email.com password123

# Custom output path
java -jar target/crunchyroll-history-scraper-1.0.0.jar user@email.com password123 ./my-history.log

# Headless mode (no visible browser window)
java -Dheadless=true -jar target/crunchyroll-history-scraper-1.0.0.jar user@email.com password123
```

### Using Maven

```bash
mvn exec:java -Dexec.args="user@email.com password123"
```

## Output Format

The log file contains entries in this format:

```
================================================================================
CRUNCHYROLL WATCH HISTORY EXPORT
Generated: 2025-12-06 22:45:00
Total Entries: 150
================================================================================

[1] [2025-12-05] My Hero Academia - Season 6 - E25: The Final Act Begins (100%)
    URL: https://www.crunchyroll.com/watch/...

[2] [2025-12-04] Jujutsu Kaisen - Season 2 - E23: Shibuya Incident (75%)
    URL: https://www.crunchyroll.com/watch/...

...

================================================================================
END OF EXPORT
================================================================================
```

## License

MIT
