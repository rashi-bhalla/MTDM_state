(ns mtdm.classifier.moa-classifier
  (:require [mtdm.classifier.base
             :refer [process-record describe-model]]
  
            [mtdm.utils :refer [max-fn]]
               [mtdm.trees.base :refer :all]
           [mtdm.trees.graph-tree :refer :all]
            [mtdm.utils.random :refer [seeded-shuffle]]
            [mtdm.utils.timing :refer [get-current-thread-time!]])

  #?(:clj (:import [com.yahoo.labs.samoa.instances
                    Attribute InstanceImpl Instances InstancesHeader]
                   [mtdm.classifier.base Classifier]
                   [java.util ArrayList]
[moa.classifiers.trees.HoeffdingOptionTree$Node]
[moa.classifiers.trees.HoeffdingOptionTree$SplitNode]

[java.lang.reflect.Field]
[java.lang.reflect.Method]
[java.lang.Class]
[moa.AbstractMOAObject]
[moa.AbstractMOAObject$Node]
[moa.classifiers.trees.FIMTDD]
;;[moa.options IntOption]


                   [moa.classifiers.trees HoeffdingTree]
[moa.classifiers.trees.HoeffdingTree$Node]
                   [moa.classifiers.meta AdaptiveRandomForest]
                   [moa.classifiers.bayes NaiveBayes]
                   [moa.classifiers.drift DriftDetectionMethodClassifier])
(:import-static [moa.AbstractMOAObject Node])

))

#?(:clj (do
          (defn- build-instance [record header]
            (let [instance (->> record
                                (:values)
                                ;; Moa represents missing values with
                                ;; NaN:
                                ;; https://github.com/Waikato/moa/blob/master/moa/src/main/java/com/yahoo/labs/samoa/instances/InstanceImpl.java#L464
                                (map #(if (nil? %) Double/NaN %))
                                (double-array)
                                (InstanceImpl. 1.0))]
              (doto instance
                (.setDataset header))))

          (defn- build-attribute [{:keys [name options]}]
            (if (= options :numeric)
              (Attribute. name)
              (Attribute. name (ArrayList. options))))

          (defn- build-header [record-schema]
            (let [attributes (map build-attribute record-schema)
                  instances (Instances. "input-header" (into-array Attribute attributes) 0)]
              (doto (InstancesHeader. instances)
                (.setClassIndex (dec (count record-schema))))))

          (defn- classify-from-votes [schema id votes]
            (if (empty? votes)
              (let [class-options (->> schema
                                       ;; Assumes last attribute is class
                                       last
                                       :options
                                       count
                                       range)]
                ;; If there are no votes, pick a random class with no
                ;; confidence (seeded by id). WARNING: There may be
                ;; correlation between rngs with sequential seeds, but
                ;; probably not of huge importance in this instance.
                {:class (first (seeded-shuffle class-options id))
                 :confidence 0.0
                 :raw-confidence 0.0
                 :raw-votes votes})
              (let [[class confidence] (->> (map-indexed vector votes)
                                            (max-fn second))
                    normal-confidence (/ (double confidence) (reduce + votes))]
                {:class class
                 :confidence (if (Float/isNaN normal-confidence)
                               0.0
                               normal-confidence)
                 :raw-confidence (if (or (Double/isInfinite confidence)
                                         (Double/isNaN confidence))
                                   nil
                                   confidence)
                 :raw-votes (map #(if (or (Double/isInfinite %)
                                          (Double/isNaN %))
                                    nil
                                    %)
                                 votes)})))

          (defn wrap-moa-classifier [schema moa-classifier]
       ;;  (println "inside function")
       ;;   (println str "schema is" schema)

            (let [header (build-header schema)]
              (doto moa-classifier
                (.setModelContext header)
                (.prepareForUse))
              (reify Classifier
                (process-record [this record]
                  (let [instance (build-instance record header)
                        start-time (get-current-thread-time!)
                        votes (.getVotesForInstance moa-classifier instance)
                        _ (.trainOnInstance moa-classifier instance)
                        stop-time (get-current-thread-time!)
                        result (classify-from-votes schema (:id record)
                                                    (vec votes))]
                    (assoc result
                           :id (:id record)
                           :process-time (merge-with - stop-time start-time))))
                     



                (describe-model [this]
 
                  (let [sb (StringBuilder.)
         ;;  ch (. HoeffdingTree/gracePeriodOption this)
;;(.gracePeriodOption this)
;;           ch  (reify  (.gracePeriodOption this))
                    

;;                     ch= this.gracePeriodOption.getValue()
                 
               ;;   sb1 (StringBuilder.)
 ;; f (.getDeclaredField HoeffdingTree "gracePeriodOption") 
  ;;f1 (.getDeclaredField HoeffdingTree "getValue") 
 ;; (.setAccessible f true)  
    
              ]
;;  (println "check" "   " ch)
 ;;(println f1)
 ;; (.setAccessible f true)
;; (let [st1 (.getValue f moa-classifier)]
;; (println (type st1))
;; (println (.toString st1))
;; )




                    (try
                      (do (.getModelDescription moa-classifier sb 2)
                          (.toString sb)
                  
)
;;  (do (.getDescription moa-classifier st 0)
;;                           (.toString st)
                  
;; )


                      (catch NullPointerException e nil))

))))

)

          (defn hoeffding-tree [schema grace-period split-confidence]
            (let [tree (HoeffdingTree.)
;;trying this for tree
 ;; hoeffding-tree-string "ARFHoeffdingTree -e 2000000 -g 50 -c 0.01 -l NB"
  hoeffding-tree-string "ARFHoeffdingTree -e 2000000 -g 50 -c 0.01 -l NB"
                  ;; Optionally disable pre-pruning
                  ;; hoeffding-tree-string (if disable-pre-pruning
                  ;;                         (str hoeffding-tree-string " -p")
                  ;;                         hoeffding-tree-string)
            
]
;; (-> tree .-treeLearnerOption (.setValueViaCLIString hoeffding-tree-string))
              ;; Configure the tree to classify according to
              ;; always use a naive bayes classifier at the leaf.
;;(println "tree is" tree)
              (.setValue (.-gracePeriodOption tree) grace-period)
(.setValue (.-splitConfidenceOption tree) split-confidence)
              (.setChosenLabel (.-leafpredictionOption tree) "NB")
              (wrap-moa-classifier schema tree)

))

          (defn adaptive-random-forest
            [schema &
             {:keys [disable-weighting disable-pre-pruning]}]
            (let [tree (AdaptiveRandomForest.)
                  ;; Set the Tree learner to the default CLI string, but
                  ;; with NaiveBayes leaf prediction ("-l NB")
                  hoeffding-tree-string "ARFHoeffdingTree -e 2000000 -g 50 -c 0.01 -l NB"
                  ;; Optionally disable pre-pruning
                  hoeffding-tree-string (if disable-pre-pruning
                                          (str hoeffding-tree-string " -p")
                                          hoeffding-tree-string)]
              (-> tree .-treeLearnerOption (.setValueViaCLIString hoeffding-tree-string))
              ;; Disable multi-threading (we do our own at the experiment level)
              (-> tree .-numberOfJobsOption (.setValue 0))
              (when disable-weighting
                (-> tree .-disableWeightedVote .set))
              (wrap-moa-classifier schema tree)))

          (defn naive-bayes [schema]
       ;;  (println schema)
            (let [classifier (NaiveBayes.)]
           ;; (println classifier)
              (wrap-moa-classifier schema classifier)))

          (defn drift-detection-classifier [schema classifier-string dd-string]
            (let [classifier (DriftDetectionMethodClassifier.)]
              (-> classifier .-baseLearnerOption (.setValueViaCLIString classifier-string))
              (-> classifier .-driftDetectionMethodOption (.setValueViaCLIString dd-string))
              (wrap-moa-classifier schema classifier)))

          (defn dd-naive-bayes [schema]
            (drift-detection-classifier schema "bayes.NaiveBayes" "ADWINChangeDetector"))))
