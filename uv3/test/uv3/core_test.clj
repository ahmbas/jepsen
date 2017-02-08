(ns uv3.core-test
  (:require [clojure.test :refer :all]
            [uv3.core :refer :all]
            [jepsen.core :as jepsen]
            [jepsen.generator :as gen]
            [jepsen.checker :as checker]
            [jepsen.tests :as tests]
            [knossos.model :refer [cas-register]])
  (:use     clojure.tools.logging))


(deftest uv3-cp-test
  (info "consistency test\n")
  (set-reg 0)
  (let [test (assoc tests/noop-test
               :nodes [:n1 :n2]
               :name "uv3-cap-test"
               :concurrency 3
               :client (client)
               :nemesis (partition-uni)
               :generator (->> (gen/mix [r w cas])
                               (gen/stagger 1)
                               (gen/nemesis
                                 (gen/seq (cycle [(gen/sleep 5)
                                                  {:type :info, :f :start}
                                                  (gen/sleep 5)
                                                  {:type :info, :f :stop}])))
                               (gen/time-limit 100))
               :model (cas-register 0)
               :checker (checker/compose
                          {:perf   (checker/perf)
                           :linear checker/linearizable}))]
    (is (:valid? (:results (jepsen/run! test))))))
