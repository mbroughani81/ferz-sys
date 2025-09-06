(ns io.github.mbroughani81.core
  (:gen-class)
  (:require
   [io.github.mbroughani81.impls.sample-adder :as sample-adder]
   [io.github.mbroughani81.impls.dist-db :as dist-db]
   [io.github.mbroughani81.automaton :as automaton]

   [taoensso.timbre :as timbre]

   [io.github.mbroughani81.perf.test1 :as test1]))

;; -------------------------------------------------- ;;
;; -------------------------------------------------- ;;


(Thread/setDefaultUncaughtExceptionHandler
 (reify Thread$UncaughtExceptionHandler
   (uncaughtException [_ thread ex]
     (timbre/error ex "Uncaught exception on" (.getName thread)))))

(defn -main [& _]
  (timbre/info "Hello")
  (timbre/set-min-level! :warn)
  (test1/simple-3-node-exec)
  (shutdown-agents)
  (System/exit 0))

;; -------------------------------------------------- ;;




(comment
  (do
    (timbre/set-min-level! :info))

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
    )

  (automaton/give @controller (dist-db/cons-Write-Ctrl "k1" "value1"))
  (automaton/give @controller (dist-db/cons-Write-Ctrl "k1" "sechs"))
  (automaton/give @controller (dist-db/cons-Write-Ctrl "k3" "value3"))
  (automaton/give @controller (dist-db/cons-Write-Ctrl "k3" "vv"))

  (automaton/give @controller (dist-db/cons-Read-Ctrl "k3"))

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
  )
