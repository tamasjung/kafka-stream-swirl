(ns kafka-stream-swirl.core-test
  (:require [clojure.test :refer :all]
            [jackdaw.streams.mock :as mock]
            [jackdaw.serdes]
            [jackdaw.serdes.edn]
            [kafka-stream-swirl.core :refer :all]
            [clojure.pprint :refer [pprint]]))

(defn topic
  [name]
  {:topic-name name
   :key-serde (jackdaw.serdes/string-serde)
   :value-serde (jackdaw.serdes.edn/serde)})

(deftest basic-test
  (let [relations {:company {:relations {:loan-application {:path [:company]}}
                             :ext-id-key :ext-id}
                   :person {:relations {:company {:path [:ceo]}}
                            :ext-id-key :email}}
        
        topic-ent-ch-ext (topic "enc-ch-ext")
        topic-ext-int (topic "ext-int")
        topic-ent-ch (topic "ent-ch")
        topic-ent-st (topic "ent-st")
        topic-reverse-links (topic "reverse-links")
        driver (mock/build-driver #(build-topology %
                                                   relations
                                                   topic-ent-ch-ext
                                                   topic-ext-int
                                                   topic-ent-ch
                                                   topic-ent-st
                                                   topic-reverse-links))
        publish (partial mock/publish driver)]
    (try
      (publish topic-ent-ch "comp" {:id "comp"
                                    :type :company
                                    :aa 1})
      (publish topic-ent-ch "comp" {:id "comp"
                                    ;:type :company
                                    :name "FC"
                                    :bb 44})
      (publish topic-ent-ch "comp2" {:id "comp2"
                                     :type :company
                                     :name "FC2"
                                     :bb 44})
      (publish topic-ent-ch
               "asdf"
               {:id "asdf"
                :type :loan-application
                :rejected false
                :company {:id "comp"
                          :type :company
                          :name "FC"
                          :parent-change-child "22"}})

      (publish topic-ent-ch
               "comp"
               {:id "comp"
                :type :company
                :child-change-parent "yeah"})

      (publish topic-ent-ch-ext
               "comp-ext"
               {:ext-id "comp-ext"
                :name "No-int"})
      (publish topic-ent-ch-ext
               "comp-ext"
               {:ext-id "comp-ext"
                :address "somewhere"})

      (publish topic-ent-ch-ext
               "comp-ext2"
               {:ext-id "comp-ext2"
                :address "hehe"})

      (publish topic-ent-ch-ext
               "per1"
               {:email "blab@bla.com"})
      (catch Exception ex
        (.printStackTrace ex)))

    (Thread/sleep 1000)

    (pprint ["ent-st" (mock/get-keyvals driver topic-ent-st)])
    (pprint ["reverse-links" (mock/get-keyvals driver topic-reverse-links)])
    (pprint ["ent-ch" (mock/get-keyvals driver topic-ent-ch)])))