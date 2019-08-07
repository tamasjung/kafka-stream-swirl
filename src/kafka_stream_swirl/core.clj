(ns kafka-stream-swirl.core
  (:require [kafka-stream-swirl.utils :as utils]
            [jackdaw.streams :as k])
  (:import (java.util UUID)))

(defn relation->change
  [msg [_ injection-path]]
  (let [change (get-in msg injection-path)]
    [(:id change) change]))

(defn reverse-relation-change
  [relations ch-ent parent-ent]
  (let [parent-type (:type parent-ent)
        path (-> ch-ent :type relations :relations parent-type :path)]
    (update-in parent-ent path utils/deep-merge ch-ent)))

(defn inverse-link
  [inverse-relations [k v]]
  (when-let [[[_ path]] (-> v :type inverse-relations seq)]
    [(get-in v (conj path :id)) k]))

(defn inverse-relations
  [relations]
  (->> relations
       (map (fn [rel]
              (let [[child parent-opts] rel
                    parent (-> parent-opts :relations ffirst)
                    path (-> parent-opts :relations first second :path)]
                [parent {child path}])))
       (into {})))

(defn build-topology
  [builder relations topic-ent-ch-ext topic-ext-int topic-ent-ch topic-ent-st topic-reverse-links]
  (let [inverse-relations (inverse-relations relations)
        ent-table (k/ktable builder topic-ent-st)
        ent-st-stream (k/to-kstream ent-table)
        ent-ch-stream (k/kstream builder topic-ent-ch)
        ent-ch-ext-stream (k/kstream builder topic-ent-ch-ext)
        reverse-links-table (k/ktable builder topic-reverse-links)]

    ;parent changes children
    (-> ent-st-stream
        (k/flat-map (fn [[_ v]]
                      (mapv #(relation->change v %)
                            (-> v :type inverse-relations))))
        (k/filter (fn [[k v]]
                    (and k v)))
        (k/to topic-ent-ch))

    ;child changes parent
    (-> ent-st-stream
        (k/left-join reverse-links-table (fn [ch-ent r-ids]
                                           [ch-ent r-ids]))
        (k/flat-map (fn [[_k [ch-ent r-ids]]]
                      (mapv (fn [parent-id] (vector parent-id ch-ent)) r-ids)))
        (k/left-join ent-table (fn [ch-ent parent-ent]
                                 [ch-ent parent-ent]))
        (k/filter (comp second second))
        (k/map-values (fn [[ch-ent parent-ent]]
                        (reverse-relation-change relations ch-ent parent-ent)))
        (k/to topic-ent-ch))

    ;build reverse-links
    (-> ent-st-stream
        (k/map (partial inverse-link inverse-relations))
        (k/filter second)
        (k/group-by-key)
        (k/aggregate hash-set (fn [acc [_k v]]
                                (conj acc v)))
        (k/to-kstream)
        (k/to topic-reverse-links))

    ;aggregate changes into state
    (-> ent-ch-stream
        k/group-by-key
        (k/aggregate #(do [nil nil])
                     (fn [[_state-before state] [_k v]]
                       [state (utils/deep-merge state v)]))
        (k/to-kstream)
        (k/filter (fn [[_k [state new-state]]]
                    (not= state new-state)))
        (k/map-values second)
        (k/to topic-ent-st))

    ;build ext-to-in, ONLY FOR DEBUGGING!!!!!
    (-> ent-ch-stream
        (k/map (fn [[_k v]]
                 [(v (-> v :type relations :ext-id-key))
                  (:id v)]))
        (k/filter first)
        (k/to topic-ext-int))

    ;resolve external-id
    (-> ent-ch-ext-stream
        (k/group-by-key)
        (k/aggregate #(do)
                     (fn [prev-val [_k new-v]]
                       (let [id (or (:id prev-val)
                                    (str (UUID/randomUUID)))]
                         (assoc new-v :id id))))

        (k/to-kstream)
        (k/select-key (comp :id second))
        (k/to topic-ent-ch))))