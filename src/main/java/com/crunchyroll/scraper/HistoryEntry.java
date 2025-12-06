package com.crunchyroll.scraper;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Represents a single entry in the Crunchyroll watch history.
 */
public record HistoryEntry(
        String seriesTitle,
        String episodeTitle,
        String seasonInfo,
        String episodeNumber,
        String watchedDate,
        String progress,
        String url
) {
    private static final DateTimeFormatter LOG_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public String toLogLine() {
        StringBuilder sb = new StringBuilder();
        sb.append("[").append(watchedDate != null ? watchedDate : "Unknown Date").append("] ");
        sb.append(seriesTitle != null ? seriesTitle : "Unknown Series");

        if (seasonInfo != null && !seasonInfo.isBlank()) {
            sb.append(" - ").append(seasonInfo);
        }

        if (episodeNumber != null && !episodeNumber.isBlank()) {
            sb.append(" - ").append(episodeNumber);
        }

        if (episodeTitle != null && !episodeTitle.isBlank()) {
            sb.append(": ").append(episodeTitle);
        }

        if (progress != null && !progress.isBlank()) {
            sb.append(" (").append(progress).append(")");
        }

        if (url != null && !url.isBlank()) {
            sb.append("\n    URL: ").append(url);
        }

        return sb.toString();
    }

    @Override
    public String toString() {
        return toLogLine();
    }
}
