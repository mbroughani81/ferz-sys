(ns io.github.mbroughani81.perf.test1
  (:require
   [clojure.test :as t]
   [clojure.java.io :as io]

   [taoensso.timbre :as timbre]
   [clj-async-profiler.core :as prof]

   [io.github.mbroughani81.automaton :as automaton]
   [io.github.mbroughani81.impls.dist-db :as dist-db]))

(t/deftest simple-3-node-exec []
  (let [_          (dist-db/reset-proj!)
        ;;
        controller (atom nil)
        n1         (atom nil)
        n2         (atom nil)
        n3         (atom nil)
        _          (swap! controller (fn [_] (dist-db/cons-Controller (atom {}))))
        _          (swap! n1 (fn [_] (dist-db/cons-Node controller 1)))
        _          (swap! n2 (fn [_] (dist-db/cons-Node controller 2)))
        _          (swap! n3 (fn [_] (dist-db/cons-Node controller 3)))
        _          (automaton/give @controller (dist-db/cons-Join n1))
        _          (automaton/give @controller (dist-db/cons-Join n2))
        _          (automaton/give @controller (dist-db/cons-Join n3))
        sh1        (promise)
        sh2        (promise)
        sh3        (promise)
        _          (dist-db/start-Node-Runner n1 sh1)
        _          (dist-db/start-Node-Runner n2 sh2)
        _          (dist-db/start-Node-Runner n3 sh3)
        _          (dist-db/start-Controller-Runner controller)
        _          (Thread/sleep 2000)
        _          (automaton/give @controller (dist-db/cons-Start-DB))
        _          (Thread/sleep 2000)
        _          (dist-db/get-partition 5 "k1")
        _          (automaton/give @controller (dist-db/cons-Write-Ctrl "k1" "value1"))
        _          (automaton/give @controller (dist-db/cons-Read-Ctrl "k1"))
        LIMIT      100000
        _          (loop [cnt 0]
                     (let [e (dist-db/cons-Read-Ctrl "k1")
                           _ (automaton/give @controller e)])
                     (when (< cnt LIMIT)
                       (recur (inc cnt))))
        _          (Thread/sleep 5000)]
    (timbre/info "FINISHED")
    (deliver dist-db/interrupt :stop)))


(comment
  (simple-3-node-exec)
  ;;
  (timbre/set-min-level! :warn)
  (prof/serve-ui 8080)
  (prof/profile (simple-3-node-exec))
  ;;
  (do
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
    (Thread/sleep 2000)
;;
    )
  (dist-db/get-partition 5 "k1")
  (automaton/give @controller (dist-db/cons-Write-Ctrl "k1" "value1"))
  (automaton/give @controller (dist-db/cons-Read-Ctrl "k1"))

  (-> @controller :state deref :partition-id->node-id)

  (def LIMIT 10000000)
  (loop [cnt 0]
    (let [e (dist-db/cons-Read-Ctrl "k1")
          _ (automaton/give @controller e)])
    (when (< cnt LIMIT)
      (recur (inc cnt))))

  (deliver dist-db/interrupt :stop)

;;
  (prof/profile (reduce + (range 1000000)))
  (def results-dir "/tmp/clj-async-profiler/results/")
  (def latest-file (->> (io/file results-dir)
                        (file-seq)
                        (filter #(re-find #"collapsed.txt$" (.getName %)))
                        (sort-by #(.lastModified %))
                        (last)))
  latest-file
  (timbre/info
   (when latest-file
     (slurp latest-file)))
;;
  (let [;;
        ;; filter [{:type :filter
        ;;          :what "io.github.mbroughani81"}
        ;;         {:type :remove
        ;;          :what "start_thread"}]
        ;;
        filter []
        ;;
        ]
    (prof/profile
     {:event                 :wall
      :predefined-transforms filter
      :threads               :true}
     (simple-3-node-exec)))

;;
  )
