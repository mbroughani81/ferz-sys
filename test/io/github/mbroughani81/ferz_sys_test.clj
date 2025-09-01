(ns io.github.mbroughani81.ferz-sys-test
  (:require [clojure.test :as t]
            [taoensso.tufte :as tufte]))

(tufte/add-handler! :my-console-handler
                    (tufte/handler:console
                     {:output-fn
                      (tufte/format-signal-fn
                       {:format-pstats-opts {:columns [:n :p50 :mean :clock :sum]}})}))

(tufte/defnp get-x [] (Thread/sleep 500)             "x val")
(tufte/defnp get-y [] (Thread/sleep (rand-int 1000)) "y val")

(tufte/profile {:level :info, :id ::my-profiling-id}
               (dotimes [_ 5]
                 (get-x)
                 (get-y)))


(t/deftest a-test
  (t/testing "FIXME, I fail."
    (t/is (= 0 1))))
