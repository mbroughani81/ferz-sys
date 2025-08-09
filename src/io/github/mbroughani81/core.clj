(ns io.github.mbroughani81.core
  (:gen-class)
  (:require
   [io.github.mbroughani81.impls.sample-adder :as sample-adder]
   [io.github.mbroughani81.automaton :as automaton]

   [clojure.core.async :as async]
   [taoensso.timbre :as timbre]))

;; -------------------------------------------------- ;;
;; -------------------------------------------------- ;;

(defn -main [& _]
  (println "Hello"))


;; -------------------------------------------------- ;;

(defn step [automaton]
  (let [m (-> automaton deref :->buff async/<!!)]
    (automaton/receive @automaton m)))

(defn start-runner [A]
  (async/thread
    (loop []
      (step A)
      (recur))))


(comment
  (def a1 (atom nil))
  (def a2 (atom nil))
  (swap! a1 (fn [_] (sample-adder/cons-A1 a2)))
  (swap! a2 (fn [_] (sample-adder/cons-A2 a1)))

  (automaton/give @a1 (sample-adder/cons-Sum-Message 1 100))

  (start-runner a1)
  (start-runner a2)

;;
  )
