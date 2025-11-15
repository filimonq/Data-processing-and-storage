(ns filter.filter-test
  (:require [clojure.test :refer :all]
            [filter.filter :as pf]))

(deftest correctness-finite
  (let [xs (range 0 10000)]
    (is (= (doall (filter odd? xs))
           (doall (pf/pfilter-blocked odd? xs))))))

(deftest correctness-empty
  (is (= [] (doall (pf/pfilter-blocked odd? [])))))

(deftest correctness-infinite
  (is (= (take 20 (filter even? (range)))
         (take 20 (pf/pfilter-blocked even? (range))))))

(deftest preserves-order
  (is (= (take 50 (filter #(zero? (mod % 7)) (range)))
         (take 50 (pf/pfilter-blocked #(zero? (mod % 7)) (range)
                                      {:block-size 11 :parallelism 3})))))

(deftest laziness-bounded-work
  (let [calls (atom 0)
        pred  (fn [x] (swap! calls inc) true)
        s     (pf/pfilter-blocked pred (range 100) {:block-size 5 :parallelism 1})]
    (is (= [0 1 2] (take 3 s)))
    (is (<= @calls 5))))

(deftest ^:perf print-timings
  (let [n 60000
        xs (range 2 n)]
    (println "\n--- PERF DEMO ---")
    (time (count (doall (filter pf/prime? xs))))
    (time (count (doall (pf/pfilter-blocked pf/prime? xs
                                           {:block-size 1024
                                            :parallelism (max 2 (.availableProcessors (Runtime/getRuntime)))}))))))
