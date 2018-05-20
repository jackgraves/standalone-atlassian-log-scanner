package co.uk.jackgraves.logscanner;

import java.util.Objects;

public class Result {
    private String url;
    private String logLine;
    private String date;

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getLogLine() {
        return logLine;
    }

    public void setLogLine(String logLine) {
        this.logLine = logLine;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public Result(String url, String logLine, String date) {
        this.url = url;
        this.logLine = logLine;
        this.date = date;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Result result = (Result) o;
        return Objects.equals(url, result.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(url);
    }

    public Result(String url, String logLine) {
        this.url = url;
        this.logLine = logLine;
    }
}
