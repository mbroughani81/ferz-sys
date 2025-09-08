(ns io.github.mbroughani81.gcbench.bench 
  (:require
    [taoensso.timbre :as timbre]))

(defn message [n] (byte-array 1024 (byte (mod n 128))))

(defn exec [high window-size]
  (loop [result  {}
         current 0]
    (let [new-result (assoc result current (message current))
          new-result (if (>= current window-size)
                       (dissoc new-result (- current window-size))
                       (-> new-result))
          ;; _          (timbre/info "new-result => " new-result)
          ]
      (if (< current high)
        (recur new-result (inc current))
        (timbre/info "Done")))))

(comment
  (exec 10 2)
  (exec 2000000 200000) 

;;
  )
