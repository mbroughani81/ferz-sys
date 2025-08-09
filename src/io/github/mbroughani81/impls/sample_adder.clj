(ns io.github.mbroughani81.impls.sample-adder
  (:require
   [io.github.mbroughani81.automaton :as automaton]

   [taoensso.timbre :as timbre]
   [clojure.core.async :as async]))

;; -------------------------------------------------- ;;

(defn cons-Sum-Message [x y]
  (-> {:type  "sum"
       :start x
       :end   y}))

(defn cons-Add-Message [x]
  (-> {:type  "add"
       :value x}))

(defn cons-Print-Message []
  (-> {:type  "print"}))

;; -------------------------------------------------- ;;

(defrecord A1 [A2 ->buff]
  automaton/Automaton
  (give [_ m]
    (async/>!! ->buff m))
  (receive [_ m]
    (case (:type m)
      "sum" (let [start (:start m)
                  end   (:end m)]
              (loop [cur start]
                (if (> cur end)
                  (automaton/give @A2 (cons-Print-Message))
                  (do
                    (automaton/give @A2 (cons-Add-Message cur))
                    (recur (inc cur)))))
              (-> nil))

      nil)))

(defn cons-A1 [A2]
  (map->A1 {:A2     A2
            :->buff (async/chan 100)}))

;; -------------------------------------------------- ;;

(defrecord A2 [A1 ->buff state]
  automaton/Automaton
  (give [_ m]
    (async/>!! ->buff m))
  (receive [_ m]
    (case (:type m)
      "add" (let [value (:value m)]
              (swap! state (fn [x] (+ x value))))
      "print" (timbre/info "RESULT => " (deref state)))
    (-> nil)))

(defn cons-A2 [A1]
  (map->A2 {:A1     A1
            :->buff (async/chan 100)
            :state  (atom 0)}))


