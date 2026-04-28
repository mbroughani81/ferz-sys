package io.github.mbroughani81.scratch;

public class FalseSharingBenchmark {

    public static final int NUM_THREADS = 4;
    public static final long ITERATIONS = 1250_000_000L;

    // --------------------------------------------------
    // Contended class (false sharing)
    // --------------------------------------------------
    public static class VolatileLong {
        public volatile long value = 0L;
    }

    // --------------------------------------------------
    // Padded class to avoid false sharing
    // --------------------------------------------------
    public static class PaddedVolatileLong {
        public volatile long value = 0L;
        // Padding to push value away from next object's value
        public long p1, p2, p3, p4, p5, p6, p7;
    }

    // --------------------------------------------------
    // Worker logic
    // --------------------------------------------------
    public static class Worker implements Runnable {
        private final long iterations;
        private final Object[] array;
        private final int index;

        public Worker(Object[] array, int index, long iterations) {
            this.array = array;
            this.index = index;
            this.iterations = iterations;
        }

        @Override
        public void run() {
            if (array[index] instanceof VolatileLong target) {
                for (long i = 0; i < iterations; i++) {
                    target.value = i;
                }
            } else if (array[index] instanceof PaddedVolatileLong target) {
                for (long i = 0; i < iterations; i++) {
                    target.value = i;
                }
            }
        }
    }

    // --------------------------------------------------
    // Benchmark runner
    // --------------------------------------------------
    private static long runBenchmark(Object[] array) throws InterruptedException {
        Thread[] threads = new Thread[NUM_THREADS];

        long start = System.nanoTime();

        for (int i = 0; i < NUM_THREADS; i++) {
            threads[i] = new Thread(new Worker(array, i, ITERATIONS));
            threads[i].start();
        }

        for (Thread t : threads) {
            t.join();
        }

        return (System.nanoTime() - start) / 1_000_000;
    }

    // --------------------------------------------------
    // Main entry point
    // --------------------------------------------------
    public static void main(String[] args) throws Exception {
        Object[] contended = new Object[NUM_THREADS];
        Object[] padded = new Object[NUM_THREADS];
        Object[] gg = new Object[NUM_THREADS];

        for (int i = 0; i < NUM_THREADS; i++) {
            contended[i] = new VolatileLong();
            padded[i] = new PaddedVolatileLong();
            gg[i] = new VolatileLong();
        }

        System.out.println("Warming...");
        long tt = runBenchmark(gg);

        System.out.println("Running padded test...");
        long t2 = runBenchmark(padded);

        System.out.println("Running false sharing test...");
        long t1 = runBenchmark(contended);

        System.out.println("\nResults (ms):");
        System.out.println("  False sharing: " + t1);
        System.out.println("  Padded:        " + t2);
    }
}
