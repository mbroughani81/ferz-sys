package io.github.mbroughani81.scratch;

public class LockContentionBenchmark {

    static final int THREADS = Runtime.getRuntime().availableProcessors();
    static final long ITERATIONS = 500_000;

    static class Counter {
        // long value = 0;

        // synchronized void inc() {
        //     value++;
        // }

        java.util.concurrent.atomic.LongAdder value = new java.util.concurrent.atomic.LongAdder();

        void inc() {
            value.increment();
        }
    }

    static class Worker implements Runnable {
        private final Counter counter;

        Worker(Counter counter) {
            this.counter = counter;
        }

        @Override
        public void run() {
            for (long i = 0; i < ITERATIONS; i++) {
                counter.inc();
            }
        }
    }

    public static void main(String[] args) throws Exception {

        Counter counter = new Counter();
        Thread[] threads = new Thread[THREADS];

        long start = System.nanoTime();

        for (int i = 0; i < THREADS; i++) {
            threads[i] = new Thread(new Worker(counter));
            threads[i].start();
        }

        for (Thread t : threads) {
            t.join();
        }

        long elapsed = (System.nanoTime() - start) / 1_000_000;

        System.out.println("Threads: " + THREADS);
        System.out.println("Counter: " + counter.value);
        System.out.println("Elapsed(ms): " + elapsed);
    }
}
