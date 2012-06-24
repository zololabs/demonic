(ns zolodeck.demonic.refs
  (:use zolodeck.demonic.loadable
        zolodeck.demonic.helper
        zolodeck.utils.debug
        zolodeck.utils.clojure
        [zolodeck.utils.maps :only [select-keys-if] :as maps]
        [zolodeck.demonic.schema :as schema]))

(declare process-map)

(def ^:dynamic children)

(defn add-child-txn! [txn]
  (swap! children conj txn)
  nil)

(defn add-children-txns! [txns]
  (swap! children concat txns)
  nil)

(defn single-ref-attrib [attrib old-value new-value]
  (cond
   (nil? new-value) (add-child-txn! (retract-entity-txn old-value))
   :else [attrib (:db/id (process-map new-value))]))

(defn multiple-ref-attrib [attrib old-values new-values]
  (let [deleted (:deleted (diff old-values new-values :db/id))]
    (add-children-txns! (map retract-entity-txn deleted)))
  [attrib (doall (map :db/id (map process-map new-values)))])

(defn process-attrib [old-map [attrib value]]
  (let [old-value (attrib old-map)]
    (cond
     (schema/is-single-ref-attrib? attrib) (single-ref-attrib attrib old-value value)
     (schema/is-multiple-ref-attrib? attrib) (multiple-ref-attrib attrib old-value value)
     :else [attrib value])))

(defn process-map
  ([a-map]
     (let [with-attribs (with-demonic-attributes a-map)
           old-map (-> with-attribs :db/id load-from-db)
           obj (apply hash-map (mapcat #(process-attrib old-map %) with-attribs))]
       (add-child-txn! obj)
       obj)))

(defn process-graph [a-map]
  (binding [children (atom [])]
    (process-map a-map)
    @children))