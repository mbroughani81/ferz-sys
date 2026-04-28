package io.github.mbroughani81.scratch;

public class Simulation1 {

    public static void main(String[] args) throws InterruptedException {
        long start = System.currentTimeMillis();

        performWorkflow();

        long end = System.currentTimeMillis();
        System.out.println("Total time: " + (end - start) + " ms");
    }

    public static void performWorkflow() throws InterruptedException {
        firstIO();
        secondIO();
        thirdIO();
    }

    private static void firstIO() throws InterruptedException {
        System.out.println("Starting first IO (100ms)...");
        Thread.sleep(1000);
        System.out.println("Finished first IO");
    }

    private static void secondIO() throws InterruptedException {
        System.out.println("Starting second IO (100ms)...");
        Thread.sleep(1000);
        System.out.println("Finished second IO");
    }

    private static void thirdIO() throws InterruptedException {
        System.out.println("Starting third IO (800ms)...");
        Thread.sleep(8000);
        System.out.println("Finished third IO");
    }
}
