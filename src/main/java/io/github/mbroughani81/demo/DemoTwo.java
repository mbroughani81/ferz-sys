package io.github.mbroughani81.demo;

import io.github.mbroughani81.perfspec.MSpec;
import java.util.concurrent.TimeUnit;

class ExpensiveMessageBuilder {
    @MSpec(max = 200, unit = TimeUnit.MILLISECONDS, sink = true, percentile = 100, desc = "Building debug message")
    public String buildMessage() {
        try {
            Thread.sleep(150);
        } catch (InterruptedException ignored) {
        }
        return "...";
    }
}

interface Logger {
    @MSpec(max = 1, unit = TimeUnit.MILLISECONDS, sink = true, percentile = 100, desc = "Log a message")
    void debug(String msg);

    boolean isDebugEnabled();
}

class SimpleLogger implements Logger {
    private boolean debugEnabled = false;

    public SimpleLogger() {
    }

    public SimpleLogger(boolean debugEnabled) {
        this.debugEnabled = debugEnabled;
    }

    public void debug(String msg) {
        /* cheap no‑op */
    }

    public boolean isDebugEnabled() {
        return debugEnabled;
    }
}

public class DemoTwo {
    private final Logger log = new SimpleLogger(false);
    private final ExpensiveMessageBuilder msgBuilder = new ExpensiveMessageBuilder();

    @MSpec(max = 50, unit = TimeUnit.MILLISECONDS, percentile = 100, desc = "Handle request (buggy: no guard, many message creations)")
    public void processBuggy() {
        for (int i = 0; i < 1_000; i++) {
            log.debug(msgBuilder.buildMessage());
        }
    }

    @MSpec(max = 50, unit = TimeUnit.MILLISECONDS, percentile = 100, desc = "Handle request (fixed: guarded)")
    public void processFixed() {
        for (int i = 0; i < 1_000; i++) {
            if (log.isDebugEnabled()) {
                log.debug(msgBuilder.buildMessage());
            }
        }
    }
}
