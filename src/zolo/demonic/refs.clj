(ns zolo.demonic.refs
  "Takes a graph and converts to a series of datomic operations"
  (:use zolo.demonic.loadable
        zolo.demonic.helper
        zolo.utils.debug
        zolo.utils.clojure
        [zolo.utils.maps :only [select-keys-if] :as maps]
        [zolo.demonic.schema :as schema]))

(declare process-map)

(def ^:private ^:dynamic children)

(defn- add-child-txn! [txn]
  (swap! children conj txn)
  nil)

(defn- add-children-txns! [txns]
  (swap! children concat txns)
  nil)

(defn- single-ref-attrib [entity attrib old-value new-value]
  (cond
   (nil? new-value) (when (:db/id entity)
                      (add-child-txn! (retract-attribute-txn entity attrib old-value)))
   (nil? old-value) [attrib (:db/id (process-map new-value))]
   (= (:db/id old-value) (:db/id new-value)) (do (process-map new-value) nil)
   :else-new-value-and-attrib-changed [attrib (:db/id (process-map new-value))]))

(defn- handle-deleted-multiple-refs-attrib [entity attrib old-values new-values]
  (let [deleted (:deleted (diff old-values new-values :db/id))]
    (when-not (empty? deleted)
      (add-child-txn! (retract-attribute-txn entity attrib deleted)))))

(defn- multiple-ref-attrib [entity attrib old-values new-values]  
  (handle-deleted-multiple-refs-attrib entity attrib old-values new-values)
  (let [db-ids (doall (keep :db/id (map process-map new-values)))]
    (cond
     (empty? db-ids) nil
     (nil? old-values) [attrib db-ids]     
     (some temp-db-id? db-ids) [attrib db-ids]
     (= (sort (map :db/id old-values)) (sort db-ids)) nil
     :else [attrib db-ids])))

(defn- process-attrib [old-map [attrib value]]
  (let [old-value (attrib old-map)]
    (cond
     (and (= old-value value) (not= attrib :db/id)) nil
     (schema/is-enum? attrib) [attrib value]
     (schema/is-single-ref-attrib? attrib) (single-ref-attrib old-map attrib old-value value)
     (schema/is-multiple-ref-attrib? attrib) (multiple-ref-attrib old-map attrib old-value value)
     :else [attrib value])))

(defn- process-map [a-map]
  (let [with-attribs (assoc-demonic-attributes a-map)
        old-map (-> with-attribs :db/id load-from-db)
        obj (apply hash-map (mapcat #(process-attrib old-map %) with-attribs))]
    (when-not (empty? obj)
      (add-child-txn! obj))
    obj))

(defn actual-changes [child-txn]
  (cond
   (map? child-txn) (if (= '(:db/id) (keys child-txn))
                      nil
                      child-txn)
   :else child-txn))

(defn process-graph [a-map]
  (binding [children (atom [])]
    (process-map a-map)
    (doall (keep actual-changes @children))))
