package io.github.mbroughani81.scratch;

public class MemoryBarrierBenchmark {

    static final long ITERATIONS = 200_000_000;

    static class PlainCounter {
        long value = 0;
    }

    static class VolatileCounter {
        volatile long value = 0;
    }

    static long testPlain() {
        PlainCounter c = new PlainCounter();

        long start = System.nanoTime();

        for (long i = 0; i < ITERATIONS; i++) {
            c.value = i;
        }

        return (System.nanoTime() - start) / 1_000_000;
    }

    static long testVolatile() {
        VolatileCounter c = new VolatileCounter();

        long start = System.nanoTime();

        for (long i = 0; i < ITERATIONS; i++) {
            c.value = i;
        }

        return (System.nanoTime() - start) / 1_000_000;
    }

    public static void main(String[] args) {

        long plain = testPlain();
        long volatileTime = testVolatile();

        System.out.println("Plain write (ms): " + plain);
        System.out.println("Volatile write (ms): " + volatileTime);
    }
}
