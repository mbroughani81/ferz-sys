package io.github.mbroughani81.demo;

import io.github.mbroughani81.perfspec.MSpec;
import io.github.mbroughani81.perfspec.MSpec.SpecMode;

class HeavyLogger {

    @MSpec(mode = SpecMode.LATENCY, max = 1, sink = true, desc = "Creating heavy object")
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

    @MSpec(mode = SpecMode.SQUEEZE, desc = "Buggy service method – creates many loggers")
    public void processBuggy() {
        for (int i = 0; i < 100; i++) {
            HeavyLogger logger = new HeavyLogger();
            doActualWork();
            logger.info("done");
        }
    }

    @MSpec(mode = SpecMode.SQUEEZE, desc = "Fixed service method – uses static logger")
    public void processFixed() {
        for (int i = 0; i < 100; i++) {
            doActualWork();
            LOGGER.info("done");
        }
    }
}
