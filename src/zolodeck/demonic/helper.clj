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

(defn entity->map [e]
  (-> (select-keys e (keys e))
      (assoc :db/id (:db/id e))))

(defn non-db-keys [a-map]
  (remove #(= :db/id %) (keys a-map)))

(defn- guid-key [a-map]
  (-> a-map
      non-db-keys
      first
      .getNamespace
      (str "/guid")
      keyword))

;; (defn merge-guid [a-map]
;;   (if a-map
;;     (let [gk (guid-key a-map)
;;           guid (gk a-map)
;;           db-id (:db/id a-map)]
;;       (print-vals "GK, guid, db-id:" gk guid (:db/id a-map))
;;       (print-vals "merged guid:" (-> (select-keys a-map (keys a-map)) 
;;                                      (assoc gk (or guid (random-guid)))
;;                                      (assoc :db/id (or db-id (db/tempid :db.part/user))))))))

(defn merge-guid [a-map]
  (if a-map
    (merge {(guid-key a-map) (random-guid)} a-map)))

(defn merge-db-id [a-map]
  (->  {:db/id (db/tempid :db.part/user)}
       (merge a-map)))

(defn with-demonic-attributes [a-map]
  (-> a-map
      merge-guid
      merge-db-id))

