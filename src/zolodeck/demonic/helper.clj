(ns zolodeck.demonic.helper
  (:use [datomic.api :only [q db tempid] :as db]
        [zolodeck.utils.clojure :only [defrunonce]]
        [zolodeck.utils.maps :only [select-keys-if] :as maps]
        [zolodeck.utils.debug]
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

(defn object-with-db-id [a-map]
  (-> {:db/id (db/tempid :db.part/user)}
      (merge a-map)))

;; handling reference attributes

(defn collect-new-objects [refs-map]
  (maps/transform-vals-with refs-map (fn [attribute value]
                                       (if (sequential? value)
                                         (map object-with-db-id value)
                                         (object-with-db-id value)))))

(defn- update-obj-with-db-ids [a-map refs-map new-objects-map]
  (reduce (fn [m k] (if (map? (m k))
                      (assoc m k (:db/id (new-objects-map k)))
                      (assoc m k (map :db/id (new-objects-map k)))))
          a-map (keys refs-map)))

(defn- gather-new-objects [new-objects]
  (reduce (fn [collected obj]
            (if (sequential? obj)
              (concat obj collected)
              (conj collected obj))) () new-objects))

(defn process-ref-attributes [a-map]
  (let [refs-map (maps/select-keys-if a-map schema/is-ref?)
        new-objects-map (collect-new-objects refs-map)]
    (conj (-> new-objects-map vals gather-new-objects reverse)
          (update-obj-with-db-ids a-map refs-map new-objects-map))))