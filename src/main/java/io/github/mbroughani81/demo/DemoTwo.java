package io.github.mbroughani81.demo;

import io.github.mbroughani81.perfspec.MSpec;
import io.github.mbroughani81.perfspec.MSpec.SpecMode;
import io.github.mbroughani81.perfspec.Skip;

class ExpensiveMessageBuilder {
    @MSpec(mode = SpecMode.LATENCY, max = 1, sink = true, desc = "Building debug message")
    public String buildMessage() {
        try {
            Thread.sleep(150);
        } catch (InterruptedException ignored) {
        }
        return "...";
    }
}

interface Logger {
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

    @Skip
    public boolean isDebugEnabled() {
        return debugEnabled;
    }
}

public class DemoTwo {
    private final Logger log = new SimpleLogger(false);
    private final ExpensiveMessageBuilder msgBuilder = new ExpensiveMessageBuilder();

    @MSpec(mode = SpecMode.SQUEEZE, desc = "Buggy service method – calls without check")
    public void processBuggy() {
        for (int i = 0; i < 1_000; i++) {
            log.debug(msgBuilder.buildMessage());
        }
        return;
    }

    @MSpec(mode = SpecMode.SQUEEZE, desc = "Fixed service method – calls with check")
    public void processFixed() {
        for (int i = 0; i < 1_000; i++) {
            if (log.isDebugEnabled()) {
                log.debug(msgBuilder.buildMessage());
            }
        }
        return;
    }
}
