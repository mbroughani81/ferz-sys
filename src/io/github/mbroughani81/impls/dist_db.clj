(ns io.github.mbroughani81.impls.dist-db
  (:require
   [clojure.core.async :as async]
   [taoensso.timbre :as timbre]
   [chime.core :as chime]

   [io.github.mbroughani81.automaton :as automaton]
   [io.github.mbroughani81.step :as step])
  (:import
   [java.time Instant Duration]))

;; -------------------------------------------------- ;;

(def interrupt (atom false))

;; -------------------------------------------------- ;;

(defn cons-Join [node]
  (-> {:type :join
       :node node}))

(defn cons-Heart-Beat [id]
  (-> {:type :heart-beat
       :id   id}))

;; -------------------------------------------------- ;;

(defn cons-Send-Heart-Beat []
  (-> {:type :send-heart-beat}))


;; -------------------------------------------------- ;;

(defn handle-join [controller m]
  (timbre/info "MMM => " (:type m) (keys m))
  (let [node (-> m :node)
        id->nodes-automaton (-> controller :id->nodes-automaton)]
    (timbre/info "Node id =>" (-> node deref :id))
    (timbre/info "type " (type node) (type (deref node)) (-> node deref keys))
    (swap! id->nodes-automaton (fn [x] (assoc x (-> node deref :id) node))))
  (-> nil))

(defn handle-heart-beat [controller m]
  (timbre/info "Got heart-beat => " m)
  (-> nil))

(defn handle-topo-update [controller m])

(defrecord Controller [id->nodes-automaton ->buff state]
  automaton/Automaton
  (give [_ m]
    (async/>!! ->buff m))
  (receive [this m]
    (case (:type m)
      :join        (handle-join this m)
      :topo-update (handle-topo-update this m)
      :heart-beat  (handle-heart-beat this m)
      nil)))

(defn cons-Controller [id->nodes-automaton]
  (map->Controller {:id->nodes-automaton id->nodes-automaton
                    :->buff (async/chan 100)
                    :state (atom {})}))

(defn start-Controller-Runner [A]
  (async/thread
    (loop []
      (step/step A)
      (when (-> interrupt deref not)
        (recur)))))

;; -------------------------------------------------- ;;

(defn handle-send-heart-beat [node]
  (let [controller (-> node :controller deref)
        id         (-> node :id)]
    (automaton/give controller (cons-Heart-Beat id))))

(defrecord Node [controller id ->buff state]
  automaton/Automaton
  (give [_ m]
    (async/>!! ->buff m))
  (receive [this m]
    (case (:type m)
      :write
      :read
      :change-role
      :assign-partition
      :send-heart-beat (handle-send-heart-beat this)
      nil)))

(defn cons-Node [controller id]
  (map->Node {:controller controller
              :id         id
              :->buff     (async/chan 100)
              :state      (atom {})}))

;; -------------------------------------------------- ;;


;; -------------------------------------------------- ;;

(defn start-Node-Runner [A]
  (let [cancel-chime (chime/chime-at
                      (chime/periodic-seq (Instant/now) (Duration/ofSeconds 5))
                      (fn [time]
                        (let [status (-> A deref :state deref :status)]
                          (when (= status nil)
                            (automaton/give @A (cons-Send-Heart-Beat))))))]
    (async/thread
      (loop []
        (Thread/sleep 5000)
        (when (-> interrupt deref)
          (timbre/info "GGG")
          (cancel-chime))
        (recur))))

  (async/thread
    ;; Add a ticker that will send A a :send-heart-beat event
    (loop []
      (step/step A)
      (when (-> interrupt deref not)
        (recur)))))
