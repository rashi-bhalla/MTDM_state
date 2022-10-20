(ns mtdm.classifier.distributed.distributed-test
  (:use midje.sweet)
  (:require
   [mtdm.classifier.distributed.distributed :as distributed]))

(fact "example"
      (conj [1 2] 3) => [1 2 3])
