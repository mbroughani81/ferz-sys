(ns io.github.mbroughani81.core
  (:gen-class)
  (:require
   [io.github.mbroughani81.impls.sample-adder :as sample-adder]
   [io.github.mbroughani81.impls.dist-db :as dist-db]
   [io.github.mbroughani81.automaton :as automaton]

   [clojure.core.async :as async]
   [taoensso.timbre :as timbre]
   [io.github.mbroughani81.step :as step]))

;; -------------------------------------------------- ;;
;; -------------------------------------------------- ;;

(defn -main [& _]
  (println "Hello"))

;; -------------------------------------------------- ;;




(comment
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
    (dist-db/start-Node-Runner n1)
    (dist-db/start-Node-Runner n2)
    (dist-db/start-Node-Runner n3)
    (dist-db/start-Controller-Runner controller)

;;
    )

;;
  )
