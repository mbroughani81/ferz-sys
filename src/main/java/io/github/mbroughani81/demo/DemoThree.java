package io.github.mbroughani81.demo;

import io.github.mbroughani81.perfspec.MSpec;
import java.util.concurrent.TimeUnit;

class HeavyLogger {
    @MSpec(max = 100, unit = TimeUnit.MILLISECONDS, sink = true, percentile = 100, desc = "Logger initialisation")
    public HeavyLogger() {
        // Expensive real-world: traverse stack trace
        try {
            Thread.sleep(80);
        } catch (InterruptedException ignored) {
        }
    }

    public void info(String msg) {
        /* cheap */
    }
}

public class DemoThree {

    private static final HeavyLogger LOGGER = new HeavyLogger(); // created once

    private void doActualWork() {
        /* cheap */
    }

    @MSpec(max = 10, unit = TimeUnit.MILLISECONDS, percentile = 100, desc = "Buggy service method – creates many heavy loggers")
    public void processBuggy() {
        for (int i = 0; i < 100; i++) {
            HeavyLogger logger = new HeavyLogger();
            doActualWork();
            logger.info("done");
        }
    }

    @MSpec(max = 10, unit = TimeUnit.MILLISECONDS, percentile = 100, desc = "Fixed service method – reuses static logger")
    public void processFixed() {
        for (int i = 0; i < 100; i++) {
            doActualWork();
            LOGGER.info("done");
        }
    }
}
