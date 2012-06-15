(ns zolodeck.demonic.helper
  (:use [datomic.api :only [q db tempid] :as db]
        [zolodeck.utils.clojure :only [defrunonce random-guid]]
        [zolodeck.utils.maps :only [select-keys-if] :as maps]
        [zolodeck.utils.debug]
        [zolodeck.utils.clojure]
        [zolodeck.demonic.schema :as schema]))

(def CONN)
(def ^:dynamic TX-DATA)
(def ^:dynamic DATOMIC-DB)
(def ^:dynamic DATOMIC-TEST false)

(defn setup-schema [schema-txs]
  (->> schema-txs
       (apply vector)
       (db/transact CONN)
       deref))

(defrunonce initialize-datomic [datomic-db-name datomic-schema]
  (db/create-database datomic-db-name)
  (def CONN (db/connect datomic-db-name))
  (setup-schema datomic-schema))

(defn next-temp-id []
  (- (System/currentTimeMillis)))

(defn get-db []
  (db/db CONN))

(defn temp-db-id? [eid]
  (map? eid))

(defn load-from-db [eid]
  (if (and eid (not (temp-db-id? eid)))
    (db/entity @DATOMIC-DB eid)))

(defn retract-entity-txn [entity]
  [:db.fn/retractEntity (:db/id entity)])

(defn run-transaction [tx-data]
  (swap! TX-DATA concat tx-data)
  (swap! DATOMIC-DB db/with tx-data))

(defn commit-pending-transactions []
  (when-not DATOMIC-TEST
    @(db/transact CONN @TX-DATA)))

(defn run-in-demarcation [thunk]
  (binding [TX-DATA (atom [])
            DATOMIC-DB (atom (get-db))]
    (let [res (thunk)]
      (commit-pending-transactions)
      res)))

;; creating new datomic transaction ready maps

(defn- guid-key [a-map]
  (-> a-map
      keys
      first
      .getNamespace
      (str "/guid")
      keyword))

(defn merge-guid [a-map]
  (if a-map
    (merge {(guid-key a-map) (random-guid)} a-map)))

(defn merge-db-id [a-map]
  (-> {:db/id (db/tempid :db.part/user)}
      (merge a-map)))

(defn with-demonic-attributes [a-map]
  (-> a-map
      merge-guid
      merge-db-id))

;; handling reference attributes

;; (defn collect-new-objects [refs-map]
;;   (maps/transform-vals-with refs-map (fn [attribute value]
;;                                        (if (sequential? value)
;;                                          (map with-demonic-attributes value)
;;                                          (with-demonic-attributes value)))))

;; (defn- update-obj-with-db-ids [a-map refs-map new-objects-map]
;;   (reduce (fn [m k] (if (map? (m k))
;;                       (assoc m k (:db/id (new-objects-map k)))
;;                       (assoc m k (map :db/id (new-objects-map k)))))
;;           a-map (keys refs-map)))

;; (defn- gather-new-objects [new-objects]
;;   (reduce (fn [collected obj]
;;             (if (sequential? obj)
;;               (concat obj collected)
;;               (conj collected obj))) () new-objects))

;; (defn process-ref-attributes [a-map]
;;   (let [refs-map (maps/select-keys-if a-map (fn [k v]
;;   (schema/is-ref? k)))
;;         new-objects-map (collect-new-objects refs-map)]
;;     (conj (-> new-objects-map vals gather-new-objects reverse)

;;           (update-obj-with-db-ids a-map refs-map
;;           new-objects-map))))


(defn is-multiple-ref-attrib? [k]
  (and (schema/is-ref? k)
       (schema/is-cardinality-many? k)))

(defn only-multi-refs-map [a-map]
  (maps/select-keys-if a-map (fn [k _] (is-multiple-ref-attrib? k))))

(defn is-single-ref-attrib? [k]
  (and (schema/is-ref? k)
       (schema/is-cardinality-one? k)))

(defn only-single-refs-map [a-map]
  (maps/select-keys-if a-map (fn [k _] (is-single-ref-attrib? k))))

(defn annotate-single-children [a-map children-map]
  (print-vals "Annotate Singles: a-map:" a-map)
  (print-vals "Annotate Children: children:" children-map)
  
  (-> (merge a-map (maps/transform-vals-with children-map (fn [k v] (:db/id v))))
      (maps/transform-vals-with (fn [_ v] (or (:db/id v) v)))))

(defn annotate-multiple-children [a-map children-map]
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
  (let [{added :added updated :updated deleted :deleted} (diff (old-refs fresh-ref-key) fresh-ref-value :db/id)]
    (assoc txns fresh-ref-key (concat (map with-demonic-attributes added)
                                      (map with-demonic-attributes updated)
                                      (map retract-entity-txn deleted)))))

(defn process-multiple-cardinality-refs [a-map]
  (let [old-refs (print-vals "[multiple] old-refs:" (-> a-map :db/id load-from-db only-multi-refs-map))
        fresh-refs (print-vals "[multiple] fresh-refs:" (only-multi-refs-map a-map))
        children (reduce #(process-multiple-cardinality-ref old-refs %1 %2) {} fresh-refs)
        updated-map (annotate-multiple-children a-map children)]
    [updated-map (apply concat (vals children))]))

(defn process-ref-attributes [a-map]
  (let [[a-map-updated-for-multiple-refs multiple-refs-txns] (print-vals "multiple-refs-txns:" (process-multiple-cardinality-refs a-map))
        [a-map-updated-for-refs single-refs-txns] (print-vals "single-refs-txns:" (process-single-cardinality-refs a-map-updated-for-multiple-refs))
        all-child-txns (print-vals "all-refs-txns:" (concat multiple-refs-txns single-refs-txns))]
    (print-vals "Processed refs:" (conj all-child-txns a-map-updated-for-refs))))