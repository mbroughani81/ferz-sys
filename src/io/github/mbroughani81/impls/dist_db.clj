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

(def interrupt (promise))

;; -------------------------------------------------- ;;

(defn cons-Join [node]
  (-> {:type :join
       :node node}))

(defn cons-Heart-Beat [id]
  (-> {:type :heart-beat
       :id   id}))

(defn cons-start-db []
  (-> {:type :start-db}))

;; -------------------------------------------------- ;;

(defn cons-Send-Heart-Beat []
  (-> {:type :send-heart-beat}))

;; -------------------------------------------------- ;;

(defn get-replication-cnt [p-id->n-id partition-id]
  (let [nodes-list (get p-id->n-id partition-id)]
    (count nodes-list)))

(defn get-node-partition [partition-id->node-id node-id]
  (->> partition-id->node-id
       (filter #(contains? (set (second %)) node-id))
       (map first)))

;; -------------------------------------------------- ;;

(defn handle-join [controller m]
  (timbre/info "MMM => " (:type m) (keys m))
  (let [node (-> m :node)
        id->nodes-automaton (-> controller :id->nodes-automaton)]
    (timbre/info "Node id =>" (-> node deref :id))
    (timbre/info "type " (type node) (type (deref node)) (-> node deref keys))
    (swap! id->nodes-automaton (fn [x] (assoc x (-> node deref :id) node)))
    (timbre/info "OKKKK")
    )
  (-> nil))

(defn handle-heart-beat [controller m]
  (let [state (-> controller :state)
        id    (-> m :id)]
    (swap! state (fn [s] (assoc-in s [:node-id->status id] :active))))
  (-> nil))

(defn handle-topo-update [controller]
  (let [old-state          (-> controller :state deref)
        partition-count    (-> old-state :partition-count)
        replication-factor (-> old-state :replication-factor)]
    (loop [partition-id 0]
      (let [state                   (-> controller :state deref)
            partition-id->node-id   (-> state :partition-id->node-id)
            node-id->status         (-> state :node-id->status)
            ;; delete the inactive nodes from topology
            partition-id->node-id   (->> partition-id->node-id
                                         (map (fn [[p-id n-ids]]
                                                (-> [p-id
                                                     (remove #(= (get node-id->status %) :dead)
                                                                  n-ids)]))))
            ;;
            current-replication-cnt (get-replication-cnt partition-id->node-id
                                                         partition-id)
            remaining-cnt           (- replication-factor current-replication-cnt)
            current-p-nodes         (-> partition-id->node-id
                                        (get partition-id))
            active-nodes            (->> node-id->status
                                         (filter #(= (second %) :active))
                                         (map first))
            candidates              (->> active-nodes
                                         (remove #(contains? current-p-nodes %)))
            node-partitions         (->> candidates
                                         (map
                                          #(-> [% (get-node-partition
                                                   partition-id->node-id %)]))
                                         (into {}))
            _                       (timbre/spy node-partitions)
            final-cands             (->> candidates
                                         (sort-by (fn [n-id]
                                                    (count (get node-partitions n-id)))))
            _                       (timbre/debug "candidates => " candidates)
            _                       (timbre/debug "node-partitions => " node-partitions)
            _                       (timbre/debug "final-cands => " final-cands)
            final-cands             (take remaining-cnt final-cands)
            _                       (timbre/debug "FINAL => " final-cands)
            _                       (reduce (fn [_ n-id]
                                              (timbre/debug "adding => " n-id)
                                              (swap! (-> controller :state)
                                                     (fn [x]
                                                       (update-in x
                                                                  [:partition-id->node-id
                                                                   partition-id]
                                                                  #(conj % n-id)))))
                                            nil
                                            final-cands)
            _                       (timbre/debug "state => " (-> controller :state deref))])
      (when-not (== partition-id (dec partition-count))
        (recur (inc partition-id))))))

(defn handle-start-db [controller]
  (handle-topo-update controller))

(defrecord Controller [id->nodes-automaton ->buff state]
  automaton/Automaton
  (give [_ m]
    (async/>!! ->buff m))
  (receive [this m]
    (case (:type m)
      :join        (handle-join this m)
      :start-db    (handle-start-db this)
      :topo-update (handle-topo-update this)
      :heart-beat  (handle-heart-beat this m)
      nil)))

(defn cons-Controller [id->nodes-automaton]
  (map->Controller {:id->nodes-automaton id->nodes-automaton
                    :->buff              (async/chan 100)
                    :state               (atom {:partition-count       5
                                                :replication-factor    2
                                                :partition-id->node-id {}
                                                :node-id->status       {}})}))

(defn start-Controller-Runner [A]
  (async/thread
    (loop []
      (step/step A)
      (when (-> interrupt realized? not)
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

(defn start-Node-Runner [A shutdown]
  (let [cancel-chime (chime/chime-at
                      (chime/periodic-seq (Instant/now) (Duration/ofSeconds 5))
                      (fn [time]
                        (let [status (-> A deref :state deref :status)]
                          (when (= status nil)
                            (automaton/give @A (cons-Send-Heart-Beat))))))]
    (async/thread
      (loop []
        (Thread/sleep 5000)
        (when (or (realized? interrupt) (realized? shutdown))
          (timbre/info "GGG")
          (cancel-chime))
        (recur))))

  (async/thread
    ;; Add a ticker that will send A a :send-heart-beat event
    (loop []
      (step/step A)
      (when (-> interrupt realized? not)
        (recur)))))

(comment
  (do
    (def c (cons-Controller nil))
    (let [state (-> c :state deref)
          state (assoc-in state [:node-id->status 0] :active)
          state (assoc-in state [:node-id->status 1] :active)
          state (assoc-in state [:node-id->status 2] :active)]
      (swap! (-> c :state) (fn [_] (-> state))))
    (timbre/info (-> c :state))

    (handle-topo-update c)
    ;;
    )

;;
  )
