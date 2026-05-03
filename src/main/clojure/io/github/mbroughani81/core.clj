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
  (do
    (timbre/set-min-level! :info))

;;
  (-main [])

  ;;

  (def a1 (atom nil))
  (def a2 (atom nil))
  (swap! a1 (fn [_] (sample-adder/cons-A1 a2)))
  (swap! a2 (fn [_] (sample-adder/cons-A2 a1)))

  (automaton/give @a1 (sample-adder/cons-Sum-Message 1 100))

  (sample-adder/start-runner a1)
  (sample-adder/start-runner a2)
;;
  (do
    (dist-db/reset-proj!)
    (def controller (atom nil))
    (def n1 (atom nil))
    (def n2 (atom nil))
    (def n3 (atom nil))
    (swap! controller (fn [_] (dist-db/cons-Controller (atom {}))))
    (swap! n1 (fn [_] (dist-db/cons-Node controller 1)))
    (swap! n2 (fn [_] (dist-db/cons-Node controller 2)))
    (swap! n3 (fn [_] (dist-db/cons-Node controller 3)))

    (automaton/give @controller (dist-db/cons-Join n1))
    (automaton/give @controller (dist-db/cons-Join n2))
    (automaton/give @controller (dist-db/cons-Join n3))
;;
    (def sh1 (promise))
    (def sh2 (promise))
    (def sh3 (promise))
    (dist-db/start-Node-Runner n1 sh1)
    (dist-db/start-Node-Runner n2 sh2)
    (dist-db/start-Node-Runner n3 sh3)
    (dist-db/start-Controller-Runner controller)

    (Thread/sleep 2000)
    (automaton/give @controller (dist-db/cons-Start-DB))

;;
    clojure.lang.PersistentVector)

  (automaton/give @controller (dist-db/cons-Write-Ctrl "k1" "value1"))
  (automaton/give @controller (dist-db/cons-Write-Ctrl "k1" "sechs"))
  (automaton/give @controller (dist-db/cons-Write-Ctrl "k3" "value3"))
  (automaton/give @controller (dist-db/cons-Write-Ctrl "k3" "vv"))

  (automaton/give @controller (dist-db/cons-Read-Ctrl "k1"))

  (dist-db/get-partition 5 "k1")
  (dist-db/get-partition 5 "k3")

  (deliver dist-db/interrupt :stop)

  (-> controller deref :id->nodes-automaton deref keys)
  (-> controller deref :state)

  (-> n3 deref :state deref :topo (get 1) deref :id)

  (-> n1 deref :state deref :topo :partition-id->node-id)
  (-> n2 deref :state deref :topo :partition-id->node-id)
  (-> n3 deref :state deref :topo :partition-id->node-id)

  (-> n1 deref :state deref :data)
  (-> n2 deref :state deref :data)
  (-> n3 deref :state deref :data)
;;
  (import '[sootup.core.inputlocation AnalysisInputLocation])
  (import '[sootup.java.bytecode.frontend.inputlocation OTFCompileAnalysisInputLocation])
  (import '[sootup.java.core.views JavaView])
  (import '[sootup.java.core.types JavaClassType])
  (import '[java.nio.file Paths])
  (def classpath-dir (Paths/get "src/main/java/io/github/mbroughani81/demo/NPlusOne.java" (into-array String [])))
  (def input-location (OTFCompileAnalysisInputLocation. classpath-dir))
  (def view (JavaView. input-location))
  (def factory (.getIdentifierFactory view))
  (def class-name "io.github.mbroughani81.demo.NPlusOne")
  (def class-type (.getClassType factory class-name))
  (def soot-class (.getClass view class-type))
  (println "Class present?" (.isPresent soot-class))
  (def my-class (.get soot-class))
  (.getName my-class)

  (def methods (seq (.getMethods my-class)))
  (println "Number of methods:" (count methods))

  (doseq [method methods]
    (println (.getSignature method)))
  ;;
  (do
    (import 'io.github.mbroughani81.flowgen.TraceGenerator)
    (def classpath "src/main/java/io/github/mbroughani81/demo/NPlusOne.java")
    (def generator (TraceGenerator. classpath))
    (def method-trace-sets (.analyzeClass generator "io.github.mbroughani81.demo.AuthorService"))
    (def trace (.getPath (first (.getTraces (first method-trace-sets)))))
    (doseq [t trace] (println t)))
  ;;
  (defn print-path [trace]
    (doseq [t (.getPath trace)] (println t)))

  (defn trace-diff [trace1 trace2]
    (let [path1 (.getPath trace1)
          path2 (.getPath trace2)]
      (if (= path1 path2)
        (println "Traces are identical.")
        (let [pairs (map vector path1 path2)
              first-diff (first (filter (fn [[a b]] (not= a b)) pairs))]
          (println "Traces differ. First difference at index" (count (take-while (fn [[a b]] (= a b)) pairs)) ":")
          (println "  Trace1 line:" (first first-diff))
          (println "  Trace2 line:" (second first-diff))))))

  (def first-set (first method-trace-sets))
  (def second-set (second method-trace-sets))
  (def trace1 (nth (.getTraces first-set) 0))
  (def trace2 (nth (.getTraces first-set) 1))
  (print-path (nth (.getTraces first-set) 0))

  (defn diff-trace-against-all [target-trace other-traces]
    (doseq [t other-traces]
      (println "Comparing with trace:" (.getId t))
      (trace-diff target-trace t)
      (println "---")))
  (diff-trace-against-all trace1 (rest (.getTraces first-set)))

  ;;
  )
