(ns zolodeck.demonic.refs
  (:use zolodeck.demonic.loadable
        zolodeck.demonic.helper
        zolodeck.utils.debug
        zolodeck.utils.clojure
        [zolodeck.utils.maps :only [select-keys-if] :as maps]
        [zolodeck.demonic.schema :as schema]))

(defn only-multi-refs-map [a-map]
  (print-vals "only multi class:" (class a-map)
              (maps/select-keys-if a-map (fn [k _] (schema/is-multiple-ref-attrib? k)))))

(defn only-single-refs-map [a-map]
  (maps/select-keys-if a-map (fn [k _] (schema/is-single-ref-attrib? k))))

(defn annotate-single-children [a-map children-map]
  (print-vals "Annotate Singles: a-map:" a-map)
  (print-vals "Annotate Children: children:" children-map)
  (-> (merge a-map (maps/transform-vals-with children-map (fn [k v] (:db/id v))))
      (maps/transform-vals-with (fn [_ v] (or (:db/id v) v)))))

(defn annotate-multiple-children [a-map children-map]
  (print-vals "[multiple] annotating: a-map:" a-map "children-map:" children-map)
  (merge a-map (maps/transform-vals-with children-map (fn [k v] (map :db/id v)))))

(defn process-single-cardinality-ref [old-refs txns-map [fresh-ref-key fresh-ref-value]]
  (let [old-value (old-refs fresh-ref-key)]
    (cond 
     (= old-value fresh-ref-value) txns-map
     (nil? fresh-ref-value) (assoc txns-map fresh-ref-key (retract-entity-txn old-value))
     :else (assoc txns-map fresh-ref-key (with-demonic-attributes fresh-ref-value)))))

(defn process-single-cardinality-refs [a-map]
  (let [old-refs (print-vals "[single] old-refs:" (-> a-map :db/id load-from-db only-single-refs-map))
        fresh-refs (print-vals "[single] fresh-refs:" (only-single-refs-map a-map))
        children (print-vals "[single] CHILDREN:" (reduce #(process-single-cardinality-ref old-refs %1 %2) {} fresh-refs))
        updated-map (annotate-single-children a-map children)]
    [updated-map (vals children)]))

(defn process-multiple-cardinality-ref [old-refs txns [fresh-ref-key fresh-ref-value]]
  (let [{added :added remaining :remaining deleted :deleted} (print-vals "[multiple] DIFF:" 
                                                                         (diff (old-refs fresh-ref-key) fresh-ref-value :db/id))]
    (print-vals "OLD-refs classes:" (map class (old-refs fresh-ref-key)))
    (print-vals "REMAIN classes:" (map class remaining))
    (assoc txns fresh-ref-key (concat (map with-demonic-attributes added)
                                      (print-vals "REMAINING with attribs:" (map with-demonic-attributes remaining))
                                      (map retract-entity-txn deleted)))))

(defn process-multiple-cardinality-refs [a-map]
  (let [old-refs (print-vals "[multiple] old-refs:" (-> a-map :db/id load-from-db entity->loadable))
        _ (print-vals "[multiple] original friends:" (map class (:user/friends old-refs)))
        old-refs (only-multi-refs-map old-refs)
        _ (print-vals "[multiple] after friends:" (map class (:user/friends old-refs)))
        fresh-refs (print-vals "[multiple] fresh-refs:" (only-multi-refs-map a-map))
        children (reduce #(process-multiple-cardinality-ref old-refs %1 %2) {} fresh-refs)
        updated-map (annotate-multiple-children a-map children)]
    [updated-map (apply concat (vals children))]))

(defn process-ref-attributes [a-map]
  (let [[a-map-updated-for-multiple-refs multiple-refs-txns] (print-vals "multiple-refs-txns:" (process-multiple-cardinality-refs a-map))
        [a-map-updated-for-refs single-refs-txns] (print-vals "single-refs-txns:" (process-single-cardinality-refs a-map-updated-for-multiple-refs))
        all-child-txns (print-vals "all-refs-txns:" (concat multiple-refs-txns single-refs-txns))]
    (print-vals "Processed refs:" (conj all-child-txns a-map-updated-for-refs))))