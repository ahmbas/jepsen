(ns fstorage.core
  (:require [clojure.tools.logging :refer :all]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.string :as str]
            [jepsen [core :as jepsen]
             [db :as db]
             ;[net       :as net]
             [control :as c]
             [client :as client]
             [nemesis :as nemesis]
             [generator :as gen]
             [checker :as checker]
             [tests :as tests]
             [util :refer [timeout local-time real-pmap with-thread-name]]
             [store :as store]]
            [knossos.model :refer [register cas-register]]
            [knossos.linear :as linear]
            [knossos.linear.report :as linear.report]
            [knossos.history :as history])
  (:use     [clojure.java.shell :only [sh]])
  (:import (java.io PushbackReader)))


(def fsdev "/home/gary/jepsen/fstorage/data/testdev")
(def data "/home/gary/jepsen/fstorage/data/temp")
(def offset 2)

(defn node-ids
  [test]
  (->> test
       :nodes
       (map-indexed (fn [i node][node i]))
       (into {})))

(defn node-id
  [test node]
  ((node-ids test) node))

(defn db
  [version]
  (reify db/DB
    (setup! [_ test node]
      ;(info node "installing db")
      ;(c/su
      (c/exec :echo (node-id test node) :> "/root/jepsen-nid")
      ;)
      )

    (teardown! [_ test node])
    ;(info node "tearing down db"))))
    ))

(defn get-value
  [device, offset]
  (->> (str "cat " data)
       (str "dd if=" device " skip=" offset " of=" data " bs=2 count=1;")
       (sh "sh" "-c")
       :out
       edn/read-string))

(defn set-value
  [device, offset, value]
  (->> (str "dd if=" data " of=" device " seek=" offset " bs=2 count=1")
       (str "echo " value " > " data " ;")
       (sh "sh" "-c")))

(defn client
  [conn a]
  (reify client/Client
    (setup! [_ test node]
      (client fsdev, offset))

    (invoke! [this test op]
      (timeout 5000 (assoc op :type :info, :error :timeout)
               (case (:f op)
                 :read  (assoc op :type :ok, :value (get-value conn a))
                 :write (do (set-value conn a (:value op))
                            (assoc op :type :ok))
                 :cas   (let [[value value'] (:value op)
                              type           (atom :fail)]
                          (if (= (get-value conn a) value)
                            (do (reset! type :ok)
                                (set-value conn a value'))
                            (reset! type :fail))
                          (assoc op :type @type)))))

    (teardown! [_ test])))

(defn split-n2
  [nodes]
  (let [coll (remove (fn [x] (= x :n2)) nodes)]
    [[:n2], coll]))

(defn partition-node-n2
  []
  (nemesis/partitioner (comp nemesis/complete-grudge split-n2)))

(defn total-time
  [history]
  (loop [pairs (history/pairs history)
         total 0]
    (if (nil? pairs)
      {:total-time total}
      (let [[invoke complete] (first pairs)
            pairs (next pairs)]
        (if (= :invoke (:type invoke))
          (recur pairs (+ total (- (:time complete) (:time invoke))))
          (recur pairs total)))))
  )

(def fs-checker
  (reify checker/Checker
    (check [_ test model history opts]
      (merge {:valid? true} (total-time history)))))

(defn r   [_ _] {:type :invoke, :f :read, :value nil})
(defn w   [_ _] {:type :invoke, :f :write, :value (rand-int 5)})
(defn cas [_ _] {:type :invoke, :f :cas, :value [(rand-int 5)(rand-int 5)]})

(defn fstorage-test
  []
  tests/noop-test
  (assoc tests/noop-test
    :nodes [:n1 :n2 :n3 :n4]
    :name "fstorage"
    ;:db (db "")
    :concurrency 1
    :client (client nil nil)
    :nemesis (nemesis/partition-random-halves)
    ;:nemesis (nemesis/partition-random-node)
    :generator (->> (gen/mix [r w cas])
                    (gen/stagger 1)
                    ;(gen/clients)
                    (gen/nemesis
                      (gen/seq (cycle [(gen/sleep 5)
                                       {:type :info, :f :start}
                                       (gen/sleep 5)
                                       {:type :info, :f :stop}])))
                    (gen/time-limit 15))
    :model (cas-register 0)
    :checker (checker/compose
               {:perf   (checker/perf)
                :linear checker/linearizable}))
    ;:checker checker/linearizable)
  )

(defn read-history
  "Reads a history file of [process type f value] tuples, or maps."
  [f]
  (with-open [r (PushbackReader. (io/reader f))]
    (->> (repeatedly #(edn/read {:eof nil} r))
         (take-while identity)
         (map (fn [op]
                (if (map? op)
                  op
                  (let [[process type f value] op]
                       {:process process
                        :type    type
                        :f       f
                        :value   value}))))
         vec)))

; Convert history.txt file to history.edn in order to use read-history
(defn convert-to-edn
  [f]
  (let [n (str/replace f ".txt" ".edn")]
       (with-open [r (io/reader f)]
         (with-open [w (io/writer n)]
           (doseq [l (line-seq r)]
             ; fix for zookeeper test history file
             ;(let [[w1 w2 w3 w4 w5] (str/split l #"\s+")]
             ;     (->> (if (= w1 ":nemesis")
             ;            l
             ;            (if (= (first w4) \[)
             ;              (str/join " " [w1 w2 w3 w4 w5])
             ;              (str/join " " [w1 w2 w3 w4])))
             ;          ((fn [s]
             ;             (.write w (str "[" s "]\n"))))))))))
             (.write w (str "[" l "]\n"))))))
  )

(defn linear-test
  [file]
  (let [history (read-history (str file ".edn"))
        model (cas-register 0)
        analysis (linear/analysis model history)]
    (if (= false (:valid? analysis))
      (do (println "Analysis failed, generating .svg")
          (linear.report/render-analysis! history analysis (str file ".svg")))
      (println "Analysis passed"))
    ))

(defn pair-test
  [file]
  (let [history (read-history (str file ".edn"))]
    (println (history/pairs history))))

(defn analyse
  [file]
   (convert-to-edn (str file ".txt"))
   (linear-test file)
   )

(defn perf-test
  [t]
  (let [test (assoc (fstorage-test)
               :nemesis (partition-node-n2)
               :generator (->> w
                               (gen/stagger 1)
                               ;(gen/clients)
                               (gen/nemesis
                                 (gen/seq (cycle [(gen/sleep t)
                                                  {:type :info, :f :start}
                                                  (gen/sleep t)
                                                  {:type :info, :f :stop}])))
                               (gen/limit 100))
               :checker fs-checker)]
    (jepsen/run! test)))

(defn -main
  "Test entry."
  []
  (info "Start fstorage testing")

  (jepsen/run! (fstorage-test))
  )
