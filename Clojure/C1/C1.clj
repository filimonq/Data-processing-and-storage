(ns C1 "неймспейс C1"
  (:require [clojure.string :as str]))

(defn strings-no-repeats [alphabet n]
  (let [step (fn [acc _]
               (reduce
                 (fn [res s]
                   (let [lastch (when (pos? (count s))
                                  (subs s (dec (count s))))]
                     (concat res
                             (map #(str s %)
                                  (remove (fn [ch] (= ch lastch)) alphabet)))))
                 '()
                 acc))]
    (if (<= n 0) '("") (reduce step '("") (range n)))))

(defn -main [& args]
  (let [[alphabet-arg n-arg] args
        alphabet (->> (str/split (or alphabet-arg "") #",")
                      (remove #(= % "")))
        n (Integer/parseInt (or n-arg "0"))]
    (println (strings-no-repeats alphabet n))))
