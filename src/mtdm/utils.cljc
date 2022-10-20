(ns mtdm.utils
  (:require [clojure.pprint :refer [cl-format]]
            #?(:clj [clojure.java.io :as io]
               :cljr [clojure.clr.io :as io])))

(def output-dir (str "H:/data/occupancy"))
(defn get-keys [coll indexes]
  (if (map? coll)
    (map #(get coll %) indexes)
    (map #(nth coll %) indexes)))

 (defn cal-no-value [cardinalities value]
;;(println "entered into nominal loop")
;;(println value)
(let [cardinalities1 (->> cardinalities
                          (first)
                          (drop-last)
                          (ffirst)
                        )
     total-records (->> cardinalities
                         (first)
                          (drop-last)
                           (first)
                            (second) )
] 
 
(loop [x cardinalities1
       y 0

]
;; (println x)
;;(println y)
(when (< y (count cardinalities1))



    (if (= (first (keys x)) value)
      (do
       ;; (println "value matched")
       
        (let [prob  (/ (inc (nth (vals cardinalities1) y) )  (inc total-records))
              ]
         ;; (println "prob is" prob)
          prob
          )  
        )
 (recur (rest x) (inc y))
      )
   
   
    )
  )
)

 )
 
(defn positions
  [pred coll]
;;(println "inside function" pred coll)
  (keep-indexed (fn [idx x]
                  (when (pred x)
                    idx))
                coll) )
;; (defn multiply-itself [coll]
;; (cond
;; (= (count (coll)) 1)
;; coll
;; :else
;; (reduce * coll)
;; )
;; )
(def populated-vector? 
  (fn 
    [item] 
    (= (count item) 1)))

(defn cal-nu-value [mid-value value]

;;(println "inside util")
;;(println "mid" mid-value)
 (def pi 3.14159)
(def e 2.71828)
(def y 0)
(let [mid-value (->> mid-value
                   (first)
                   (drop-last))
mean-value (->> mid-value
          (ffirst)
)
stddev (->> mid-value
            (first)
            (second)
)

y (* (/ 1 (* stddev (Math/sqrt (* 2 pi)))) (Math/pow e (* -1 (/ (Math/pow (- value mean-value) 2) (* 2 (Math/pow stddev 2))))))
   
]
y
)
)



(defn find-all-nested
  [m k]
  (->> (tree-seq map? vals m)
       (filter map?)
       (keep k)))

(defn round2
  "Round a double to the given precision (number of significant digits)"
  [precision d]
  (let [factor (Math/pow 10 precision)]
    (/ (Math/round (* d factor)) factor)))

(defn index-of [coll item]
  (->> (map-indexed vector coll)
       (filter #(= item (second %)))
       (first)
       (first)))

(defn indexes-where [pred sequence]
  (->> (map-indexed vector sequence)
       (filter #(pred (second %)))
       (map first)))

(defn keys-where [pred hashmap]
  (->> hashmap
       (map vec)
       (filter #(pred (second %)))
       (map first)))

(defn max-fn [func coll]
  (reduce #(if (>= (func %1) (func %2)) %1 %2) coll))

(defn seq-contains? [coll item]
  (some #(= % item) coll))

(defn seq-is-subset? [super-coll sub-coll]
  (every? #(seq-contains? super-coll %) sub-coll))

(defn percent [value]
  (str (cl-format nil "~,2F" (* 100 value)) "%"))

(defn mean [xs]
  (if (empty? xs)
    0
    (/ (apply + xs) (count xs))))

(defn std-dev
  [values]
  (let [mu (mean values)]
    (Math/sqrt (/ (reduce + (map #(Math/pow (- % mu) 2) values))
                  (- (count values) 1)))))

(defn updated-mean [mean-old new-value count-data]

(+ mean-old (/ (- new-value mean-old) (+ count-data 1)) )
)


(defn updated-std-dev [std-dev-old new-value count-data mean-old mean-new]

(Math/sqrt (/ (+ (* (- count-data 2) (Math/pow std-dev-old 2)) (* (- new-value mean-new) (- new-value mean-old))) (- count-data 1)))

)

(defn weighted-mean
  "Find the weighted mean for a sequence of items, where the value is
  given by value-key and the weight is given by ."
  [value-key weight-key items]
  (let [weights (map weight-key items)
        total-weight (reduce + weights)]
    (as-> items $
      (map #(* (value-key %) (weight-key %)) $)
      (reduce + $)
      (/ $ total-weight))))

(defn rolling-difference
  "Returns a sequence of numerical differences between subsequent
  `values`."
  [values]
  (map - (rest values) (drop-last values)))

(defn safe-division
  ([x y fallback]
   (if (= 0 y) fallback (/ x y)))
  ([x y]
   (safe-division x y 0)))

(defn bool-to-int
  [b]
  (if b 1 0))

(defn standard-deviation [coll]
  (if (<= (count coll) 1)
    0
    (let [avg (mean coll)
          squares (for [x coll]
                    (let [x-avg (- x avg)]
                      (* x-avg x-avg)))
          total (count coll)]
      (-> (/ (apply + squares)
             (- total 1))
          (Math/sqrt)))))

(defn filtered-proportion [pred items]
  (loop [items items total-true 0 total-false 0]
    (if (empty? items)
      (safe-division total-true (+ total-true total-false) 0)
      (if (pred (first items))
        (recur (rest items) (inc total-true) total-false)
        (recur (rest items) total-true (inc total-false))))))

(defn replace-in-seq
  "Replaces all occurrences of a given value in a sequence with a
  replacement value."
  [needle replacement haystack]
  (map #(if (= % needle) replacement %) haystack))

(defn dot-product
  "Computes the dot-product of xs and ys."
  [xs ys]
  (->> (map * xs ys)
       (reduce +)))

(defn map-vals [f m]
  (into {} (map (fn [[k v]] [k (f v)]) m)))

(defn keys->map
  "Returns a map where each key maps to the value given by applying
  val-fn."
  [val-fn keys]
  (zipmap keys (map val-fn keys)))

(def ^:dynamic *format-precision* 5)

(defn format-number [num]
  (let [f (str "%." *format-precision* "f")
        formatted-num (format f (float num))]
    (if (re-matches #"-0\.0+" formatted-num)
      (subs formatted-num 1) ;; Remove negative sign from negative zero
      formatted-num)))

(defn log-time-string
  []
  #?(:clj (.format (java.text.SimpleDateFormat. "yyyyMMdd-HHmm")
                   (new java.util.Date))
     :cljr "todo"))

(defmacro with-file-reader [path sym & body]
  `(with-open [~sym #?(:clj (io/reader ~path)
                       :cljr (io/text-reader ~path))]
     (do ~@body)))

(def ^:dynamic *debug* true)

(defmacro debug [& body]
  `(when *debug*
     (do ~@body)))
