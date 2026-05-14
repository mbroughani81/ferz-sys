(ns io.github.mbroughani81.core
  (:gen-class)
  (:require
   [io.github.mbroughani81.impls.sample-adder :as sample-adder]
   [io.github.mbroughani81.impls.dist-db :as dist-db]
   [io.github.mbroughani81.gcbench.bench :as bench]
   [io.github.mbroughani81.automaton :as automaton]
   [taoensso.timbre :as timbre])
  (:import
   ;; [io.github.mbroughani81.scratch FalseSharingBenchmark LockContentionBenchmark MemoryBarrierBenchmark Simulation1]
   [io.github.mbroughani81.demo NPlusOne]))

;; -------------------------------------------------- ;;
;; -------------------------------------------------- ;;

(Thread/setDefaultUncaughtExceptionHandler
 (reify Thread$UncaughtExceptionHandler
   (uncaughtException [_ thread ex]
     (timbre/error ex "Uncaught exception on" (.getName thread)))))

(defn -main [& _]
  (timbre/set-min-level! :warn)
  ;; (bench/exec 2000000 200)
  ;; (FalseSharingBenchmark/main (into-array String []))
  ;; (LockContentionBenchmark/main (into-array String []))
  ;; (MemoryBarrierBenchmark/main (into-array String []))
  ;; (Simulation1/main (into-array String []))
  ;; (println "Hii")
  ;; (NPlusOne/main (into-array String []))
  (shutdown-agents)
  (System/exit 0))

;; -------------------------------------------------- ;;

(comment
  ;;
  (do
    ;; (require 'virgil)
    (require 'clojure.string)
    (import 'io.github.mbroughani81.flowgen.TraceGenerator)
    (import 'io.github.mbroughani81.flowgen.MethodTraceSet$Trace)
    ;; (virgil/watch-and-recompile ["src/main/java"])
    ;;
    )

  (do
    (defn print-path [trace]
      (println (MethodTraceSet$Trace/pathToStr (.getPath trace))))
    (defn find-first-path-diff [trace1 trace2]
      (let [path-str1 (MethodTraceSet$Trace/pathToStr (.getPath trace1))
            path-str2 (MethodTraceSet$Trace/pathToStr (.getPath trace2))
            lines1 (clojure.string/split-lines path-str1)
            lines2 (clojure.string/split-lines path-str2)
            max-lines (max (count lines1) (count lines2))]
        (loop [i 0]
          (when (< i max-lines)
            (let [line1 (if (< i (count lines1)) (nth lines1 i) "<END OF TRACE 1>")
                  line2 (if (< i (count lines2)) (nth lines2 i) "<END OF TRACE 2>")]
              (if (not= line1 line2)
                (do
                  (println "First difference at line" (inc i) ":")
                  (println "Trace1:" line1)
                  (println "Trace2:" line2))
                (recur (inc i))))))))
    ;;
    )

  (print-path (nth (.getTraces second-set) 0))

  (find-first-path-diff (nth (.getTraces second-set) 0)
                        (nth (.getTraces second-set) 1))

  (do
    (println "method-trace-set count " (count method-trace-sets))
    (println "first-set method " (.getMethod first-set))
    (println "first-set count" (count (.getTraces first-set)))
    (println "second-set method " (.getMethod second-set))
    (println "second-set count" (count (.getTraces second-set)))
;;
    )

  (do
    (def classpath "src/main/java/io/github/mbroughani81/demo/DemoOne.java")
    (def generator (TraceGenerator. classpath))
    (def method-trace-sets (.analyzeClass generator "io.github.mbroughani81.demo.EntityService"))
    (def first-set (first method-trace-sets))
    (def second-set (second method-trace-sets)))

  (do
    (def classpath "src/main/java/io/github/mbroughani81/demo/DemoTwo.java")
    (def generator (TraceGenerator. classpath))
    (def method-trace-sets (.analyzeClass generator "io.github.mbroughani81.demo.DemoTwo"))
    (def first-set (first method-trace-sets))
    (def second-set (second method-trace-sets)))

  (do
    (def classpath "src/main/java/io/github/mbroughani81/demo/DemoThree.java")
    (def generator (TraceGenerator. classpath))
    (def method-trace-sets (.analyzeClass generator "io.github.mbroughani81.demo.DemoThree"))
    (def first-set (first method-trace-sets))
    (def second-set (second method-trace-sets)))

;;
  )
