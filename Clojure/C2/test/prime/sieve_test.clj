(ns prime.sieve-test
  (:require [clojure.test :refer :all]
            [prime.sieve :as sut]))

(deftest first-10-primes
  (is (= (take 10 (sut/primes))
         [2 3 5 7 11 13 17 19 23 29])))

(deftest nth-primes-known
  (is (= 2 (nth (sut/primes) 0)))
  (is (= 7919 (nth (sut/primes) 999))))

(defn prime-by-check? [n]
  (cond
    (< n 2) false
    :else
    (let [ps (sut/primes)]
      (not-any? #(zero? (mod n %))
                (take-while #(<= (* % %) n) ps)))))

(deftest first-200-are-prime
  (is (every? prime-by-check? (take 200 (sut/primes)))))
