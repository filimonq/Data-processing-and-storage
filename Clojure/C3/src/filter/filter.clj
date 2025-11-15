(ns filter.filter
  (:gen-class))

(defn- filter-block
  [pred block]
  (persistent!
    (reduce (fn [^clojure.lang.ITransientVector acc x]
              (if (pred x) (conj! acc x) acc))
            (transient [])
            block)))

(defn pfilter-blocked
  "Параллельный ленивый filter c обработкой блоками.
   Опции:
   - :block-size   (по умолчанию 1024)
   - :parallelism  (по умолчанию = числу ядер CPU)
   Сохраняет порядок. Подходит для конечных и бесконечных последовательностей.
   Вперёд вычисляет не больше parallelism * block-size элементов."
  ([pred coll]
   (pfilter-blocked pred coll {}))
  ([pred coll {:keys [block-size parallelism]
               :or   {block-size 1024
                      parallelism (.availableProcessors (Runtime/getRuntime))}}]
   (let [parallelism (max 1 (int parallelism))
         block-size  (max 1 (int block-size))
         chunks      (partition-all block-size coll)]
     (letfn [(launch-one [chs pool]
               (if-let [c (first chs)]
                 [(rest chs) (conj pool (future (filter-block pred c)))]
                 [chs pool]))
             (fill-pool [chs pool]
               (loop [chs chs, pool pool]
                 (if (< (count pool) parallelism)
                   (let [[chs' pool'] (launch-one chs pool)]
                     (if (identical? chs chs')
                       [chs' pool']
                       (recur chs' pool')))
                   [chs pool])))
             (step [chs pool]
               (lazy-seq
                 (let [[chs pool] (fill-pool chs pool)]
                   (if (empty? pool)
                     nil
                     (let [res   (deref (first pool))
                           pool' (subvec pool 1)]
                       (if (seq res)
                         (concat res (step chs pool'))
                         (step chs pool')))))))]
       (step chunks (vector))))))

(defn prime?
  [n]
  (and (> n 1)
       (let [n (long n)
             limit (long (Math/sqrt n))]
         (loop [d 2]
           (cond
             (> d limit) true
             (zero? (mod n d)) false
             :else (recur (inc d)))))))

(defn -main
  "Быстрый бенч: сравнение обычного filter и pfilter-blocked на prime?.
   Запуск: clj -M -m filter.filter"
  [& _]
  (let [n   150000
        xs  (range 2 n)
        p   (max 2 (.availableProcessors (Runtime/getRuntime)))
        bs  1024]
    (println "CPU cores:" p)
    (println "Data size:" (count xs) "block-size:" bs "parallelism:" p)
    (println "Serial filter:")
    (time (println "primes:" (count (doall (filter prime? xs)))))
    (println "\nParallel pfilter-blocked:")
    (time (println "primes:" (count (doall (pfilter-blocked prime? xs
                               {:block-size bs :parallelism p}))))))
  (shutdown-agents))