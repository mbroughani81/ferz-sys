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

(defn cons-Start-DB []
  (-> {:type :start-db}))

(defn cons-Read-Ctrl [key]
  (-> {:type :read-ctrl
       :key  key}))

(defn cons-Write-Ctrl [key value]
  (-> {:type  :write-ctrl
       :key   key
       :value value}))

;; -------------------------------------------------- ;;

(defn cons-Send-Heart-Beat []
  (-> {:type :send-heart-beat}))

(defn cons-Write [key value]
  (-> {:type  :write
       :key   key
       :value value}))

(defn cons-Read [key]
  (-> {:type :read
       :key  key}))

(defn cons-update-topo-snapshot [topo]
  (-> {:type :update-topo-snapshot
       :topo topo}))

;; -------------------------------------------------- ;;

(defn get-replication-cnt [p-id->n-id partition-id]
  (let [nodes-list (get p-id->n-id partition-id)]
    (count nodes-list)))

(defn get-node-partition [partition-id->node-id node-id]
  (->> partition-id->node-id
       (filter #(contains? (set (second %)) node-id))
       (map first)))

(defn get-partition [partition-count key]
  (-> key hash abs (mod partition-count)))

(defn update-nodes-topo-snapshot [controller]
  (let [id->nodes-automaton     (-> controller :id->nodes-automaton deref)
        partition-id->node-id   (-> controller :state deref :partition-id->node-id)
        partition-id->leader-id (-> controller :state deref :partition-id->leader-id)
        nodes                   (vals id->nodes-automaton)]
    (timbre/info "HERE" (count nodes))
    (doseq [node nodes]
      (automaton/give @node (cons-update-topo-snapshot
                             {:id->nodes-automaton     id->nodes-automaton
                              :partition-id->node-id   partition-id->node-id
                              :partition-id->leader-id partition-id->leader-id})))))

;; -------------------------------------------------- ;;

(defn handle-join [controller m]
  (timbre/info "MMM => " (:type m) (keys m))
  (let [node (-> m :node)
        id->nodes-automaton (-> controller :id->nodes-automaton)]
    (timbre/info "Node id =>" (-> node deref :id))
    (timbre/info "type " (type node) (type (deref node)) (-> node deref keys))
    (swap! id->nodes-automaton (fn [x] (assoc x (-> node deref :id) node)))
    (timbre/info "OKKKK"))
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
            partition-id->leader-id (-> state :partition-id->leader-id)
            ;; delete the inactive nodes from topology
            partition-id->node-id   (->> partition-id->node-id
                                         (map (fn [[p-id n-ids]]
                                                (-> [p-id
                                                     (remove #(= (get node-id->status %) :dead)
                                                             n-ids)]))))
            ;; assign new nodes
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
            _                       (timbre/debug "state-v1 => " (-> controller :state deref))
            ;; doing leader election
            nodes                   (-> controller
                                        :state
                                        deref
                                        :partition-id->node-id
                                        (get partition-id))
            leader-id               (get partition-id->leader-id partition-id)
            leader-exists?          (= nil leader-id)
            leader-dead?            (= (get node-id->status leader-id) :dead)
            _                       (when (or leader-exists? leader-dead?)
                                      (let [leader-id (rand-nth nodes)]
                                        (swap! (-> controller :state)
                                               (fn [x]
                                                 (assoc-in x
                                                           [:partition-id->leader-id
                                                            partition-id]
                                                           leader-id)))))
            _                       (timbre/debug "state-v2 => " (-> controller :state deref))])
      (when-not (== partition-id (dec partition-count))
        (recur (inc partition-id))))))

(defn handle-start-db [controller]
  (handle-topo-update controller)
  (timbre/info "should-be-here => " (-> controller :state deref))
  (update-nodes-topo-snapshot controller))

(defn handle-read-ctrl [controller m]
  (let [id->node-automaton    (-> controller :id->nodes-automaton deref)
        state                 (-> controller :state deref)
        partition-id->node-id (-> state :partition-id->node-id)
        key                   (-> m :key)
        partition-count       (-> state :partition-count)
        ;;
        p-id                  (get-partition partition-count key)
        nodes                 (get partition-id->node-id p-id)
        node-id               (rand-nth nodes)
        node                  (get id->node-automaton node-id)
        _                     (automaton/give @node (cons-Read key))]))

(defn handle-write-ctrl [controller m]
  ;; find the leader, and send "write" event to it.
  ;; find the partition id.
  (let [id->node-automaton      (-> controller :id->nodes-automaton deref)
        state                   (-> controller :state deref)
        partition-id->leader-id (-> state :partition-id->leader-id)
        key                     (-> m :key)
        value                   (-> m :value)
        partition-count         (-> state :partition-count)
        ;;
        p-id                    (get-partition partition-count key)
        leader-id               (get partition-id->leader-id p-id)
        node                    (get id->node-automaton leader-id)
        _                       (automaton/give @node (cons-Write key value))]))

(defrecord Controller [id->nodes-automaton ->buff state]
  automaton/Automaton
  (give [_ m]
    (async/>!! ->buff m))
  (receive [this m]
    (case (:type m)
      :join        (handle-join this m)
      :start-db    (handle-start-db this)
      :topo-update (handle-topo-update this)
      :read-ctrl   (handle-read-ctrl this m)
      :write-ctrl  (handle-write-ctrl this m)
      :heart-beat  (handle-heart-beat this m)
      nil)))

(defn cons-Controller [id->nodes-automaton]
  (map->Controller {:id->nodes-automaton id->nodes-automaton ;; TODO I don't think this need to be an atom
                    :->buff              (async/chan 100)
                    :state               (atom {:partition-count         5
                                                :replication-factor      2
                                                :partition-id->node-id   {}
                                                :partition-id->leader-id {}
                                                :node-id->status         {}})}))

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

(defn handle-write [node m]
  (timbre/info "HERE?????")
  (let [state              (-> node :state deref)
        data               (-> state :data)
        topo               (-> state :topo)
        id->node-automaton (-> topo :id->nodes-automaton)
        n-id               (-> node :id)
        key                (-> m :key)
        value              (-> m :value)
        p-id               (get-partition 5 key) ;;TODO hardcoded
        data               (assoc data key value)
        _                  (swap! (-> node :state)
                                  (fn [s]
                                    (assoc s :data data)))
        ;; _                  (timbre/info "node-state => " (-> node :state deref))
        ;; if leader, stream the changes to other nodes
        is-leader?         (= (-> topo :partition-id->leader-id (get p-id)) n-id)
        p-node-ids         (-> topo :partition-id->node-id (get p-id))
        _                  (when is-leader?
                             (doseq [p-node-id p-node-ids]
                               (let [p-node (get id->node-automaton p-node-id)]
                                 (when (not= p-node-id n-id)
                                   (automaton/give @p-node (cons-Write key value))))))
        ]))

(defn handle-read [node m]
  (timbre/info "read from node " (-> node :id))
  (let [data  (-> node :state deref :data)
        key   (-> m :key)
        value (get data key)]
    (timbre/info "Read Result: " value)))

(defn handle-update-topo-snapshot [node m]
  (timbre/info "updated!!")
  (let [topo (-> m :topo)
        ;; _    (def tttt topo)
        _    (swap! (-> node :state)
                    (fn [s]
                      (assoc s :topo topo)))
        ;; _    (timbre/info "state-after-update-topo-snapshot => "
        ;;                   (-> node :state deref))
        ])
  (-> nil))

(defrecord Node [controller id ->buff state]
  automaton/Automaton
  (give [_ m]
    (async/>!! ->buff m))
  (receive [this m]
    (case (:type m)
      :write                (handle-write this m)
      :read                 (handle-read this m)
      ;; :change-role
      ;; :assign-partition
      :send-heart-beat      (handle-send-heart-beat this)
      :update-topo-snapshot (handle-update-topo-snapshot this m)
      nil)))

(defn cons-Node [controller id]
  (map->Node {:controller controller
              :id         id
              :->buff     (async/chan 100)
              :state      (atom {:data {}
                                 :topo nil})}))

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
      (timbre/debug "Waiting for event in node " (-> @A :id))
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

  (doseq [x ["1" "222" "3333" "44444" "55555" "a" "b" "c" "dd5" "xyz" "hello" "world" "ddd" "3123" "3vcv" "3123d" "4234134"]]
    (println (mod (hash x) 7)))

  (-> tttt keys)

  (-> tttt :partition-id->node-id)
  (-> tttt :partition-id->leader-id)
  (-> tttt :id->nodes-automaton keys)

   

;;
  )
