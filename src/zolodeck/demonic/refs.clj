(ns zolodeck.demonic.refs
  "Takes a graph and converts to a series of datomic operations"
  (:use zolodeck.demonic.loadable
        zolodeck.demonic.helper
        zolodeck.utils.debug
        zolodeck.utils.clojure
        [zolodeck.utils.maps :only [select-keys-if] :as maps]
        [zolodeck.demonic.schema :as schema]))

(declare process-map)

(def ^:private ^:dynamic children)

(defn- add-child-txn! [txn]
  (swap! children conj txn)
  nil)

(defn- add-children-txns! [txns]
  (swap! children concat txns)
  nil)

(defn- single-ref-attrib [attrib old-value new-value]
  (cond
   (= old-value new-value) nil
   (nil? new-value) (add-child-txn! (retract-entity-txn old-value))
   :else [attrib (:db/id (process-map new-value))]))

(defn- handle-deleted-multiple-refs-attrib [old-values new-values]
  (let [deleted (:deleted (diff old-values new-values :db/id))]
    (add-children-txns! (map retract-entity-txn deleted))))

(defn- multiple-ref-attrib [attrib old-values new-values]
  (handle-deleted-multiple-refs-attrib old-values new-values)
  (let [db-ids (doall (keep :db/id (map process-map new-values)))]
    (when-not (empty? db-ids)
      [attrib db-ids])))

(defn- process-attrib [old-map [attrib value]]
  (let [old-value (attrib old-map)]
    (cond
     (and (= old-value value) (not= attrib :db/id)) nil
     (schema/is-enum? attrib) [attrib value]
     (schema/is-single-ref-attrib? attrib) (single-ref-attrib attrib old-value value)
     (schema/is-multiple-ref-attrib? attrib) (multiple-ref-attrib attrib old-value value)
     :else [attrib value])))

(defn- process-map [a-map]
  (let [with-attribs (assoc-demonic-attributes a-map)
        old-map (-> with-attribs :db/id load-from-db)
        obj (apply hash-map (mapcat #(process-attrib old-map %) with-attribs))]
    (when-not (empty? obj)
      (add-child-txn! obj))
    obj))

(defn process-graph [a-map]
  (binding [children (atom [])]
    (process-map a-map)
    @children))