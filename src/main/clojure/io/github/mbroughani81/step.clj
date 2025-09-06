(ns io.github.mbroughani81.step
  (:require
   [clojure.core.async :as async]

   [io.github.mbroughani81.automaton :as automaton]))

(defn step [automaton]
  (let [m (-> automaton deref :->buff async/<!!)]
    (automaton/receive @automaton m)))

