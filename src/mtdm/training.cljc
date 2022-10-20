(ns mtdm.training
  (:require
   [mtdm.dataset.base
    :refer [pop-record-and-rest get-schema]]
   [mtdm.evaluation
    :refer [results-summary]]
   [mtdm.classifier.distributed.base
    :refer [get-all-sites get-site-changelog]]
   [mtdm.classifier.distributed.dynamic-distributed
    :refer [dynamic-distributed-classifier]]
   [mtdm.classifier.distributed.dynamic-monitors
    :refer [make-creation-significant-agreement-monitor-factory
            make-removal-inverted-agreement-monitor-factory
            make-removal-accuracy-usage-monitor-factory
            make-blacklist-permanent-monitor-factory
            make-blacklist-source-accuracy-monitor-factory]]
   [mtdm.classifier.base
    :refer [process-record describe-model]]
[mtdm.classifier.distributed.sites
             :refer [init-sites-atom add-t-site! remove-t-site!
                     get-all-source-sites get-site-features]]

[mtdm.classifier.distributed.distributed
    :refer [distributed-classifier default-moving-average-generator]]
 [mtdm.utils
    :refer [map-vals percent debug mean std-dev positions]]
[mtdm.utils.timing :refer [get-current-thread-time!]]
   [mtdm.utils
    :refer [map-vals percent debug]]
   [mtdm.utils.random
    :refer [seeded-shuffle]]
 [mtdm.classifier.moa-classifier :refer [hoeffding-tree]]
[mtdm.trees.parse-model :refer [parse-moa-tree-model-string]]
   [mtdm.classifier.distributed.sites
    :refer [make-site-structure p-site t-site]]))

;; new code adds here

(def sites-paired-left (atom {}))
(def side (atom ""))
(def sites-paired-right (atom {}))
(def highest-tf (atom 0))
(def total-number-records-left (atom 0))
(def total-number-records-right (atom 0))
(def left-scanned-sites (atom ()))
(def right-scanned-sites (atom ()))
(def level (atom 0))

(defn make-classifier
  [classifier dataset]

;;(def dataset-feature (dataset :schema))
   (loop [dataset dataset
          trees []
          i 0 
          record-sets []
       ;; cond-probs []
          ]
     (let [[record rest-dataset] (pop-record-and-rest dataset) 


]
       (if record
         (let [raw-result (doall (process-record classifier record))
               result (assoc raw-result :truth (last (:values record)))
               record-id (:id record)
               record-values (drop-last (:values record)) 
             record-set {:id record-id :values record-values}
 
]
(recur rest-dataset (conj trees result) (inc i) (conj record-sets record-set) 
;;(conj cond-probs fcond-prob)
                  )

)

         
        trees
;;[results cond-probs]
)
)

)

)

;; new code ends here

(defn train-classifier
  ([classifier dataset]
   (train-classifier classifier dataset nil))
  ([classifier dataset progress-atom]
   (loop [dataset dataset
          results []
          i 0]
     (let [[record rest-dataset] (pop-record-and-rest dataset)]
       (if record
         (let [raw-result (doall (process-record classifier record))
               result (assoc raw-result :truth (last (:values record)))]
           (when (and (= (mod i 1000) 0) progress-atom)
             (reset! progress-atom i))
           #_(debug
              (if (= (mod i 1000) 0)
                (println "Processed" i "records")))
           (recur rest-dataset (conj results result) (inc i)))
         results)))))

(defn build-classifier
  [base-classifier dataset base-site-structure best-pairs
   {:keys [site-window-size site-training-time shared-sources?
           creation-window-size creation-time-threshold
           removal-window-size removal-time-threshold
           trouble-factor t-site-input-type creation-agreement-threshold
           removal-accuracy-threshold removal-usage-threshold]}
   & {:keys [p-site-aggregation-rule moving-average-generator
             detrend-confidence? confidence-jitter trouble-classifier
             disable-monitors disable-monitor-logging]}]
  (let [creation-monitor-factory (make-creation-significant-agreement-monitor-factory
                                  creation-window-size
                                  creation-agreement-threshold
                                  creation-time-threshold)
        removal-monitor-factory (make-removal-accuracy-usage-monitor-factory
                                 removal-window-size
                                 removal-accuracy-threshold
                                 removal-usage-threshold
                                 removal-time-threshold)
        blacklist-monitor-factory (make-blacklist-source-accuracy-monitor-factory)]
    (dynamic-distributed-classifier base-site-structure
                                    (get-schema dataset)
                                    base-classifier
                                    moving-average-generator
                                    site-window-size
                                    trouble-factor
                                    t-site-input-type
                                    site-training-time
                                    shared-sources?
                                    creation-monitor-factory
                                    removal-monitor-factory
                                    blacklist-monitor-factory
                                    p-site-aggregation-rule
                                    detrend-confidence?
                                    confidence-jitter
                                    (or trouble-classifier base-classifier)
                                    :disable-monitors disable-monitors
                                    :disable-monitor-logging disable-monitor-logging
                                    :best-pairs best-pairs)))

(defn build-naive-classifier
  [base-classifier dataset base-site-structure best-pairs
   & {:keys [p-site-aggregation-rule moving-average-generator
             detrend-confidence? confidence-jitter]}]
  (dynamic-distributed-classifier base-site-structure
                                  (get-schema dataset)
                                  base-classifier
                                  moving-average-generator
                                  1
                                  0
                                  nil
                                  1
                                  false
                                  (make-creation-significant-agreement-monitor-factory 1 2 1)
                                  (make-removal-inverted-agreement-monitor-factory)
                                  (make-blacklist-permanent-monitor-factory)
                                  p-site-aggregation-rule
                                  detrend-confidence?
                                  confidence-jitter
                                  base-classifier
                                  :disable-monitors true 
                                  :best-pairs best-pairs))

(defn build-inde-classifier
 [base-classifier dataset base-site-structure
   {:keys [site-window-size site-training-time shared-sources?
           creation-window-size creation-time-threshold
           removal-window-size removal-time-threshold
           trouble-factor t-site-input-type creation-agreement-threshold
           removal-accuracy-threshold removal-usage-threshold]}
   & {:keys [p-site-aggregation-rule moving-average-generator
             detrend-confidence? confidence-jitter trouble-classifier
             disable-monitors disable-monitor-logging]}]
;;(println "inside inde")
  (let [creation-monitor-factory (make-creation-significant-agreement-monitor-factory
                                  creation-window-size
                                  creation-agreement-threshold
                                  creation-time-threshold)
        removal-monitor-factory (make-removal-accuracy-usage-monitor-factory
                                 removal-window-size
                                 removal-accuracy-threshold
                                 removal-usage-threshold
                                 removal-time-threshold)
        blacklist-monitor-factory (make-blacklist-source-accuracy-monitor-factory)

moving-average-generator (or moving-average-generator
                                     (default-moving-average-generator site-window-size))
        sites-atom (init-sites-atom base-site-structure (get-schema dataset)
                                    base-classifier
                                    moving-average-generator)
        ;; Create a new distributed classifier with a pre-generated
        ;; sites-atom that we retain a reference to so that the
        ;; structure can be manipulated.
       

]
;;(println creation-monitor-factory)
(distributed-classifier base-site-structure (get-schema dataset)
                                            base-classifier site-window-size
                                           :sites-atom sites-atom
                                           :p-site-aggregation-rule p-site-aggregation-rule
                                           :detrend-confidence? detrend-confidence?
                                           :confidence-jitter confidence-jitter)

        
    


 ) )

(defn feature-to-site [feature schema base-structure]
(let [feature-full (first (filter #(= (:name %) feature) schema)) 
feature-no (first (positions #{feature-full} schema)) 
att-list (->> base-structure
              (map :attributes) 
      
)
att-list-match (loop [x att-list
                 y []
]
(if (not-empty x)
(do   
(let [att-match (first (positions #{feature-no} (first x)))
     att-site  (if (not= att-match nil) (first x))     
    ]
;;(println att-match)
;;(println att-site)
(recur (rest x) (conj y att-site))
)
)
(first (filter #(not= % nil) y)) 
)

)
site-no (first (positions #{att-list-match} att-list))
site-name (->> (get base-structure site-no)
               (:label)             
)  
]

[site-no site-name]  
)

)

(defn counts-mine [vs x y]
;;(println "ind=side" x)
;;(println (first x) (second x) )
  (loop [vs vs, cs []]
    (if (empty? vs)
      cs
      (recur (rest vs), (conj cs (and (some #(= x %) (first vs))  (some #(= y %) (first vs)))
;;(and (= true (contains? (first vs) x)) (= true (contains? (first vs) y)))
                              )))))

(defn String->Number [str]
  (let [n (read-string str)]
       (if (number? n) n nil)))

(defn checking2 [tree-structure part base-structure dataset inde-results records]
;;(println "inside" (count records) part)

(let [tree-part (second (get tree-structure 1))
;;set the part (get tree-structure 0)

 ]
;;(println "hf" @highest-tf)
;; (if (= @level 2)
;;   (println "get tree" (second (get tree-structure 1)) ))

    (if (and (= (:type tree-part) :branch) (> (count records) @highest-tf)) 
    (do
  ;;    (println "branch")
    (let [
       schema (get-schema dataset)
      splitting-value (String->Number (subs (ffirst (:branches tree-part))  2))

      ;;information of root
      root (->> tree-part
          :feature
          ) 
    root-full (first (filter #(= (:name %) root) (get-schema dataset))) 
   root-feature-no (first (positions #{root-full} (get-schema dataset))) 
   ;; records (->> (:records dataset)
   ;;               ;;(map :values)
   ;;          ;;  (filter #(get (:values %) root-feature-no)) 
   ;;               )
   [root-node-site-no root-node-site]  (feature-to-site root schema base-structure)


;;information about left subtree
  records-flowing-left (filter #(<= (get (:values %) root-feature-no) splitting-value) records)

;; troubledsites-records-flowing-left   (for [ x inde-results
;;                                 y records-flowing-left :when (= (:id y) (:id (get (:breakdown x) 0)) )] (:troubled-sites x))

;; trouble-records-flowing-left  (filter #(= % true) (counts troubledsites-records-flowing-left 0)) 

left-tree (->> tree-part 
                 :branches
                   first
                  
)
left-tree-node (->> tree-part 
                 :branches
                   first
                   second
                   :feature 
                 
)
[left-node-site-no left-node-site] (feature-to-site left-tree-node schema base-structure)



;;information about right subtree
  records-flowing-right (filter #(> (get (:values %) root-feature-no) splitting-value) records)

;; troubledsites-records-flowing-right   (for [ x inde-results
;;                                 y records-flowing-right :when (= (:id y) (:id (get (:breakdown x) 0)) )] (:troubled-sites x))

;; trouble-records-flowing-right  (filter #(= % true) (counts troubledsites-records-flowing-right 0)) 

right-tree (->> tree-part 
                 :branches
                   second
                  
)
right-tree-node (->> tree-part 
                 :branches
                   second
                   second
                   :feature 
                 
)
 [right-node-site-no right-node-site] (feature-to-site right-tree-node schema base-structure)
collection1 (cond 
(and (not= left-node-site nil) (not= right-node-site nil))
{:left left-tree :right right-tree}
(and (= right-node-site nil) (not= left-node-site nil) )
{:left left-tree :right nil} 
(and (= left-node-site nil) (not= right-node-site nil) )
{:left nil :right right-tree}
(and (= left-node-site nil) (= right-node-site nil))
{:left nil :right nil}
) 
records-flowing (cond 
(and (not= left-node-site nil) (not= right-node-site nil))
{:left records-flowing-left :right records-flowing-right}
(and (= right-node-site nil) (not= left-node-site nil) )
{:left records-flowing-left :right nil}
(and (= left-node-site nil) (not= right-node-site nil))
{:left nil :right records-flowing-right}
(and (= left-node-site nil) (= right-node-site nil))
{:left nil :right nil}
) 


check (filter #(= % left-node-site) @left-scanned-sites)
]
;;(println "check" right-node-site)
;;(println "left" (count records-flowing-left) "right" (count records-flowing-right))
(let [a (cond
(= part :left)
 @left-scanned-sites
(= part :right)
@right-scanned-sites
)
b (cond
(= part :left)
 sites-paired-left
(= part :right)
sites-paired-right
)

]

;;if left node =! already present site
(if (and (= () (filter #(= % left-node-site) a)) (not= left-node-site nil) (not= left-node-site root-node-site ))
(do
;;(println "leyt" left-node-site @a)
;;(println "inside check left" (count records-flowing-left)) 
(let [troubledsites-records-flowing-left (for [ x inde-results
                                y records-flowing-left :when (= (:id y) (:id (get (:breakdown x) 0)) )] (:troubled-sites x))

 trouble-records-between-sites  (filter #(= % true) (counts-mine troubledsites-records-flowing-left root-node-site-no left-node-site-no)) 
]
;;(println "leyt" (count trouble-records-between-sites))
(if (not= () (filter #(= (key %) [root-node-site left-node-site]) @b))
(do
(let [matched-key  (->>  @b
                  (filter #(= (key %) [root-node-site left-node-site]))
                  (map key)
                  (first)
 )

matched-pair-value (->>  @b
                  (filter #(= (key %) [root-node-site left-node-site]))
                  (map val)
 )
updated-value (+ (count trouble-records-between-sites) (first matched-pair-value))

 ]

(swap! b assoc matched-key updated-value)
)
) 
(swap! b conj {[root-node-site left-node-site] (count trouble-records-between-sites)})   
)
)
)
)
;;if right node =! already present site
(if (and (= () (filter #(= % right-node-site) a)) (not= left-node-site right-node-site) (not= right-node-site nil) (not= right-node-site root-node-site ))
(do
;;(println "inside check right" (count records-flowing-right))
(let [troubledsites-records-flowing-right   (for [ x inde-results
                                y records-flowing-right :when (= (:id y) (:id (get (:breakdown x) 0)) )] (:troubled-sites x))

 trouble-records-between-sites  (filter #(= % true) (counts-mine troubledsites-records-flowing-right root-node-site-no right-node-site-no)) 
]

(if (not= () (filter #(= (key %) [root-node-site right-node-site]) @b))
(do
(let [matched-key  (->>  @b
                  (filter #(= (key %) [root-node-site right-node-site]))
                  (map key)
                  (first)
 )

matched-pair-value (->>  @b
                  (filter #(= (key %) [root-node-site right-node-site]))
                  (map val)
 )
updated-value (+ (count trouble-records-between-sites) (first matched-pair-value))

 ]

(swap! b assoc matched-key updated-value)
)
) 
(swap! b conj {[root-node-site right-node-site] (count trouble-records-between-sites)})   
) 
)
)
)
;;if left node= right node 
(if (and (= () (filter #(= % right-node-site) a)) (= left-node-site right-node-site) (not= left-node-site nil) (not= right-node-site nil) (not= right-node-site root-node-site))
(do
;;(println "both child matched")
(let [
troubledsites-records-flowing-right   (for [ x inde-results
                                y records-flowing-right :when (= (:id y) (:id (get (:breakdown x) 0)) )] (:troubled-sites x))


 trouble-records-between-sites  (filter #(= % true) (counts-mine troubledsites-records-flowing-right root-node-site-no right-node-site-no))
 
matched-key  (->>  @b
                  (filter #(= (key %) [root-node-site left-node-site]))
                  (map key)
                  (first)
 )

matched-pair-value (->>  @b
                  (filter #(= (key %) [root-node-site left-node-site]))
                  (map val)
 )
updated-value (+ (count trouble-records-between-sites) (first matched-pair-value))

 ]
;;(println "leyt" (count trouble-records-between-sites))
(swap! b assoc matched-key updated-value)
)
)
)
;;(println root-node-site)
;;(println left-node-site)
;;(println right-node-site)
;;(println @sites-paired-left)

;;(println @sites-paired-right)
;;(println @left-scanned-sites)

;;(println @right-scanned-sites)

[collection1 records-flowing] 
)
)

)

)
)
)
(defn convert-site-from-name-to-no [site-name inde-class]
;;(println (first site-name))
;;(println (second site-name))
(let [m (->> (get-all-sites inde-class)
           (filter #(= (:label (get % 1)
                        ) (first site-name)))
                 (ffirst)

)
n (->> (get-all-sites inde-class)
           (filter #(= (:label (get % 1)
                        ) (second site-name)))
                 (ffirst)

)

]
;;(println "number1" m)
;;(println "number2" n)
[m n]
)
)
(defn checking [tree-structure base-structure dataset inde-results records inde-class selection-records]
;; (println "time"  (:cpu-nano (:process-time (println "base structure" tree-structure)) 
;;               )  )
;;(.start (Thread. (println "HEllo")))
;;(println  (list '(1 6) '(2 5)))
;;(println "checking" "level" @level "count" (count records))
(def collection (atom {}))
(def collection1 (atom {}))
(def records-flow-l (atom {}))
(def records-flow-r (atom {}))
(def bs (atom ()))
;;when level is 0
(when (= @level 0)
(let [

cross-check  (for [ x inde-results
                     ] (:troubled-sites x))
cross-check1 (count (filter #(= % true) (counts-mine cross-check 2 6)) ) 


tree-part (cond 
             (= @level 0)
             tree-structure
            (= @level 1)
       (second (get tree-structure 1))
            
)
;;set the part (get tree-structure 0)

 ]
;;(println "hf" @highest-tf)

    (if (and (= (:type tree-part) :branch) (> (count records) @highest-tf)) 
    (do
     ;; (println "branch")
    (let [
       schema (get-schema dataset)
      splitting-value (String->Number (subs (ffirst (:branches tree-part))  2))

      ;;information of root
      root (->> tree-part
          :feature
          ) 
    root-full (first (filter #(= (:name %) root) (get-schema dataset))) 
   root-feature-no (first (positions #{root-full} (get-schema dataset))) 
   
   [root-node-site-no root-node-site]  (feature-to-site root schema base-structure)


;;information about left subtree
  records-flowing-left (filter #(<= (get (:values %) root-feature-no) splitting-value) records)

;; troubledsites-records-flowing-left   (for [ x inde-results
;;                                 y records-flowing-left :when (= (:id y) (:id (get (:breakdown x) 0)) )] (:troubled-sites x))

 ;; trouble-records-flowing-left  (filter #(= % true) (counts troubledsites-records-flowing-left 0 )) 

left-tree (->> tree-part 
                 :branches
                   first
                  
)
left-tree-node (->> tree-part 
                 :branches
                   first
                   second
                   :feature 
                 
)
 [left-node-site-no left-node-site]  (feature-to-site left-tree-node schema base-structure)

;; tr-flowing-left (->> troubledsites-records-flowing-left
;;                 (filter #(= % [0 2]))
;;                (count)
;; )
;;information about right subtree
  records-flowing-right (filter #(> (get (:values %) root-feature-no) splitting-value) records)

;; troubledsites-records-flowing-right   (for [ x inde-results
;;                                 y records-flowing-right :when (= (:id y) (:id (get (:breakdown x) 0)) )] (:troubled-sites x))

;; tr-flowing-right (->> troubledsites-records-flowing-right
;;                 (filter #(= % [0 2]))
;;                (count)
;; )
;; trouble-records-flowing-right  (filter #(= % true) (counts troubledsites-records-flowing-right 0 2)) 

right-tree (->> tree-part 
                 :branches
                   second
                  
)
right-tree-node (->> tree-part 
                 :branches
                   second
                   second
                   :feature 
                 
)
 [right-node-site-no right-node-site] (feature-to-site right-tree-node schema base-structure)
collection11 {:left left-tree :right right-tree}

]
;;(println "root" root " " root-full " " root-feature-no)
;;(println "1st" (count records-flowing-right))
;; (spit (str output-dir "/" "cross-check.txt") (with-out-str (pr cross-check)))
;;(spit (str output-dir "/" "cross-check1.txt") (with-out-str (pr cross-check1)))
;;add root node to collection
(swap! left-scanned-sites conj root-node-site)
(swap! right-scanned-sites conj root-node-site)
;;check if left node equals to root node then no calculation
 
(if (not= root-node-site left-node-site)
(do
(let [troubledsites-records-flowing-left   (for [ x inde-results
                                y records-flowing-left :when (= (:id y) (:id (get (:breakdown x) 0)) )] (:troubled-sites x))

 trouble-records-between-sites  (filter #(= % true) (counts-mine troubledsites-records-flowing-left root-node-site-no left-node-site-no)) 
]
(swap! sites-paired-left conj {[root-node-site left-node-site] (count trouble-records-between-sites)})   
)
)
)

;;add right node to collection

 
;;check if left node equals to root node then no calculation
 
(if (and (not= root-node-site right-node-site) (not= left-node-site right-node-site))

(do
(let [troubledsites-records-flowing-right   (for [ x inde-results
                                y records-flowing-right :when (= (:id y) (:id (get (:breakdown x) 0)) )] (:troubled-sites x))

 trouble-records-between-sites  (filter #(= % true) (counts-mine troubledsites-records-flowing-right root-node-site-no right-node-site-no))

 
]


;; (spit (str output-dir "/" "checking.txt") (with-out-str (pr  trouble-records-between-sites1)))

(swap! sites-paired-right conj {[root-node-site right-node-site] (count trouble-records-between-sites)})   
)
)
)
(swap! left-scanned-sites conj left-node-site)
(swap! right-scanned-sites conj right-node-site)

(let [
maxi-r (if (not= @sites-paired-right {}) (val (apply max-key val @sites-paired-right)) 0) 

maxi-l (if (not= @sites-paired-left {}) (val (apply max-key val @sites-paired-left)) 0) 

maxi (max maxi-r maxi-l) 
]
(swap! highest-tf (constantly maxi))

)

(if (and (not= root-node-site right-node-site) (= left-node-site right-node-site))
(do
;;(println "both child matched")
(let [
troubledsites-records-flowing-right   (for [ x inde-results
                                y records-flowing-right :when (= (:id y) (:id (get (:breakdown x) 0)) )] (:troubled-sites x))


 trouble-records-between-sites  (filter #(= % true) (counts-mine troubledsites-records-flowing-right root-node-site-no right-node-site-no))
 
matched-key  (->>  @sites-paired-left
                  (filter #(= (key %) [root-node-site left-node-site]))
                  (map key)
                  (first)
 )

matched-pair-value (->>  @sites-paired-left
                  (filter #(= (key %) [root-node-site left-node-site]))
                  (map val)
 )
updated-value (+ (count trouble-records-between-sites) (first matched-pair-value))

 ]

(swap! sites-paired-left assoc matched-key updated-value)
)
)
)
 ;;(spit (str output-dir "/" "check_prob.txt") (with-out-str (pr troubledsites-records-flowing-right)))
;;(println "left" (count records-flowing-left) "right"  (count records-flowing-right))
(reset! collection1 collection11) 
(reset! collection collection11) 

;;(println @sites-paired-left)

;;(println @sites-paired-right)
;;(println @left-scanned-sites)

;;(println @right-scanned-sites)
;;(println @highest-tf)

(if (= @level 0) (reset! level (+ @level 1)))
(loop [i 1]
(when (< i 3)

;;(println i)

(doseq [x @collection
        ]
;;(println "inside doseq" @level)
(cond 
(= (get x 0) :left)
(do
;;(println "level" @level "left")

(when (= @level 2)

(checking2 (first (get x 1)) (get x 0) base-structure dataset inde-results (second (first @records-flow-l)))
(checking2 (second (get x 1)) (get x 0) base-structure dataset inde-results (second (second @records-flow-l)))

)
(when (= @level 1)
;;(println "left" x)

(let [[l-coll records-l] (checking2 x (get x 0) base-structure dataset inde-results records-flowing-left)
]

(reset! records-flow-l records-l)
(reset! collection1 {:left l-coll})
;;(reset! collection1 {:left l-coll})
) 
)

)

(= (get x 0) :right)
(do
;;(println "level" @level "right")
(when (= @level 2)

(checking2 (first (get x 1)) (get x 0) base-structure dataset inde-results (second (first @records-flow-r)))
(checking2 (second (get x 1)) (get x 0)  base-structure dataset inde-results (second (second @records-flow-r)))

)
(when (= @level 1)
;;(println "left" x)
(let [[r-coll records-r] (checking2 x (get x 0) base-structure dataset inde-results records-flowing-right)
]

(reset! records-flow-r records-r)
 (swap! collection1 conj {:right r-coll})
;;(reset! collection1 {:left l-coll})
) 
)
)
)
)
(reset! collection @collection1)
(reset! level (+ @level 1)) 
(recur (inc i))
)
)
)
)
)
)
)
;;(println "left" @sites-paired-left)
;;(println "right" @sites-paired-right)

;;this loop is for further processing of pairs
;; (loop [x @sites-paired-right
;;       y {}
;;        ]
;; (if (= (empty? x) false)
;; (do
;; (println "inside" (val (first x)))
;; ;;(swap! bs conj (sort (into () (convert-site-from-name-to-no (first x) inde-class))))

;; (recur (rest x) (conj y {(sort (into [] (convert-site-from-name-to-no (key (first x)) inde-class))) (val (first x))}  )
;;        )  
;; )
;; (println "y is" " " y)
;; )

;; )
;;ending

(let [m (filter #(and (not= nil (first %)) (not= nil (second %))) (keys (filter #(>= (val %) selection-records) @sites-paired-left))) 
n  (filter #(and (not= nil (first %)) (not= nil (second %)))  (keys (filter #(>= (val %) selection-records) @sites-paired-right)))
o (concat m n)
 ]
;;(println "counting" m "    " n "     " o)

(loop [x o
      y () ]
(if (= (empty? x) false)
(do
(swap! bs conj (sort (into () (convert-site-from-name-to-no (first x) inde-class))))
(recur (rest x) (conj y (sort (into () (convert-site-from-name-to-no (first x) inde-class)))))  
)
y
)
;;(println "y is" " " y)
)
;;(println m)
)
;;(println "checking" sites-paired-left)
;;(println "checking1" sites-paired-right)
)



(defn run-experiment
  ([base-classifier config]
   (run-experiment base-classifier config nil))
  ([base-classifier
    {:keys [label dataset-description dataset-fn dataset-fn1
            base-site-structure system-config
            p-site-aggregation-rule detrend-confidence?
            confidence-jitter trouble-classifier
            disable-monitors disable-monitor-logging
            grace-period split-confidence selection-records min-records
]}
    progress-atom]
   (let [dataset (dataset-fn)
   dataset1 (dataset-fn1)
start-time1 (get-current-thread-time!)
       central-classifier (hoeffding-tree (get-schema dataset1) grace-period split-confidence)

time-tree (first (map read-string (re-seq #"[\d.]+" (with-out-str (time (hoeffding-tree (get-schema dataset1) grace-period split-confidence)
 ) ))))

;;central-classifier dataset central-site-structure
      tree (make-classifier central-classifier dataset1)
tree-model (describe-model central-classifier)
tree-structure (parse-moa-tree-model-string tree-model) 

stop-time1 (get-current-thread-time!)
independent-classifier
         (build-inde-classifier base-classifier dataset1
                                base-site-structure
                                system-config
                                :p-site-aggregation-rule p-site-aggregation-rule
                                :detrend-confidence? detrend-confidence?
                                :confidence-jitter confidence-jitter
                                :trouble-classifier trouble-classifier
                                :disable-monitors disable-monitors
                                :disable-monitor-logging disable-monitor-logging)

         

         inde-results  (train-classifier independent-classifier dataset1 progress-atom)
;;start-time2 (. System (nanoTime))
         start-time2 (get-current-thread-time!)
         best-pairs (checking tree-structure base-site-structure dataset1 inde-results (:records dataset1) independent-classifier selection-records)


time-parse (first (map read-string (re-seq #"[\d.]+" (with-out-str (time (checking tree-structure base-site-structure dataset1 inde-results (:records dataset1) independent-classifier selection-records)
) ))))




         classifier (if (= :naive system-config)
                      (build-naive-classifier base-classifier dataset
                                              base-site-structure best-pairs
                                              :p-site-aggregation-rule p-site-aggregation-rule
                                              :detrend-confidence? detrend-confidence?
                                              :confidence-jitter confidence-jitter)
                      (build-classifier base-classifier dataset
                                        base-site-structure best-pairs
                                        system-config
                                        :p-site-aggregation-rule p-site-aggregation-rule
                                        :detrend-confidence? detrend-confidence?
                                        :confidence-jitter confidence-jitter
                                        :trouble-classifier trouble-classifier
                                        :disable-monitors disable-monitors
                                        :disable-monitor-logging disable-monitor-logging))
         results (train-classifier classifier dataset progress-atom)
         sites (get-all-sites classifier)
         model-description (describe-model classifier)

stop-time2 (get-current-thread-time!)
ttime1 (- (:cpu-nano stop-time1)
                                         (:cpu-nano start-time1))

ttime2 (- (:cpu-nano stop-time2)
                                         (:cpu-nano start-time2))

ttime (+ ttime1 ttime2)
time-cpu-pairs (:time-cpu-pairs (first results)) 
time-wall-pairs (:time-wall-pairs (first results)) 
total-pairs (:total-pairs (first results)) 
time-func (:time-func (first results))

time-tsite (+ time-tree time-parse (:time-tsite (first (filter #(not= (:time-tsite %) 0) results)) )) 

]
(println "time-tree" time-tree)
(println "time-parse" time-parse)
(println "time-tsite" time-tsite)
     {:label label
      :timestamp (quot (System/currentTimeMillis) 1000)
      :dataset-description dataset-description
      :base-site-structure base-site-structure
      :system-config system-config
      :final-site-structure (->> sites
                                 (map-vals #(select-keys % [:type :label :attributes :source-sites :order])))
      :site-changelog (get-site-changelog classifier)
      :summary (results-summary min-records sites results (get-site-changelog classifier)
                                :pretty? false
                                :time-cal ttime
                                :time-cpu-pairs time-cpu-pairs
                                  :time-wall-pairs time-wall-pairs
                                  :total-pairs total-pairs
                                 :time-func time-func
                                 :time-tsite time-tsite
)
      :model-description (if (map? (:AGGREGATOR model-description))
                           (update-in model-description
                                      [:AGGREGATOR :p-site-aggregation-rule]
                                      dissoc :classifier-generator)
                           model-description)
      :results results
      :time-cal ttime
})))

(defn random-base-site-structure
  ([dataset-fn feature-count max-features-per-p-site]
   (random-base-site-structure dataset-fn feature-count max-features-per-p-site 1))
  ([dataset-fn feature-count max-features-per-p-site seed]
   (let [features (range 0 feature-count)
         class-index feature-count
         p-sites (->> (seeded-shuffle features seed)
                      (partition max-features-per-p-site
                                 max-features-per-p-site
                                 [])
                      (map-indexed
                       (fn [idx site-features]
                         (p-site (keyword (str "p-" (inc idx)))
                                 (vec site-features)))))]
     (apply make-site-structure class-index p-sites))))

(defn- p-site-pairs
  [feature-count]
  (let [p-site-count (/ feature-count 2)
        features (range feature-count)
        feature-pairs (clojure.math.combinatorics/combinations features 2)]
    (->> (clojure.math.combinatorics/combinations feature-pairs p-site-count)
         (filter (fn [pair-combos]
                   (= (count (flatten pair-combos))
                      (count (distinct (flatten pair-combos)))))))))

(defn p-site-permutations
  [feature-count]
  (->> (p-site-pairs feature-count)
       (map
        (fn [p-sites-features]
          (->> p-sites-features
               (map-indexed #(p-site (keyword (str "p" (inc %1)))
                                     (into [] %2))))))))
