(ns prime.sieve)

(defn primes
  "Infinite lazy seq of primes via incremental Sieve of Eratosthenes (odd-only)."
  []
  (letfn [(insert-step
            [D x step]
            (update D x (fnil conj []) step))
          (sieve [D q]
            (lazy-seq
              (if-let [steps (get D q)]
                (let [D-wo (dissoc D q)
                      D'   (reduce (fn [acc st]
                                     (update acc (+' q st) (fnil conj []) st))
                                   D-wo
                                   steps)]
                  (sieve D' (+ q 2)))
                (cons q
                      (sieve (insert-step D (*' q q) (*' 2 q))
                             (+ q 2))))))]
    (cons 2 (sieve {} 3))))
