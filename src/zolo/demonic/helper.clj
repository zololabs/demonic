(ns zolo.demonic.helper
  (:use [datomic.api :only [q db tempid squuid] :as db]
        [zolo.demonic.schema :as schema]
        [zolo.utils.clojure :only [defrunonce random-guid diff]]
        [zolo.utils.maps :only [select-keys-if] :as maps]
        [zolo.utils.debug]
        [zolo.utils.clojure])
  (:require [clojure.tools.logging :as logger]))

(def CONN)
(def ^:dynamic TX-DATA)
(def ^:dynamic DATOMIC-DB)
(def ^:dynamic DATOMIC-TEST false)

(defn setup-schema [schema-txs]
  @(db/transact CONN (vec schema-txs)))

(defn setup-connection [datomic-db-name] 
  (def CONN (db/connect datomic-db-name)))

(defn start-it-up- [datomic-db-name datomic-schema]
  (db/create-database datomic-db-name)
  (setup-connection datomic-db-name)
  (setup-schema datomic-schema))

(defrunonce initialize-datomic [datomic-db-name datomic-schema]
  (start-it-up- datomic-db-name datomic-schema))

(defn next-temp-id []
  (- (System/currentTimeMillis)))

(defn temp-db-id? [eid]
  (map? eid))

(defn load-from-db [eid]
  (if (and eid (not (temp-db-id? eid)))
    (db/entity @DATOMIC-DB eid)))

(defn retract-attribute-txn [entity attrib value-or-values]
  (when (:db/id entity)
    (let [value (cond
                 (schema/is-single-ref-attrib? attrib) (:db/id value-or-values)
                 (and 
                  (schema/is-multiple-ref-attrib? attrib)
                  (sequential? value-or-values)) (map :db/id value-or-values)
                  (and
                   (schema/is-multiple-ref-attrib? attrib)
                   (not (sequential? value-or-values))) (:db/id value-or-values)
                   :else value-or-values)]
      [:db/retract (:db/id entity) attrib value])))

(defn retract-entity-txn [entity]
  [:db.fn/retractEntity (:db/id entity)])

(defn append-ref-txn [entity attrib value-entities]
  [:db/add (:db/id entity) attrib (map :db/id value-entities)])

(defn- datomic-transact [txns]
  (let [tf (db/transact-async CONN txns)]
    @tf))

(defn speculative-transact [db txns]
  (-> db
      (db/with txns)
      :db-after))

(defn run-transaction [tx-data]
  (swap! TX-DATA conj tx-data)
  (swap! DATOMIC-DB speculative-transact tx-data)
  nil)

(defn commit-pending-transactions []
  (when-not DATOMIC-TEST
    (when-not (empty? @TX-DATA)
      (doseq [t @TX-DATA]
        (when-not (empty? t)
          (datomic-transact (doall t)))))))

(defn run-in-demarcation [thunk]
  (binding [TX-DATA (atom [])
            DATOMIC-DB (atom (db/db CONN))]
    (let [res (thunk)]
      (commit-pending-transactions)
      res)))

;; creating new datomic transaction ready maps

(defn is-entity-map? [v]
  (instance? datomic.query.EntityMap v))

(defn entity->map [e]
  (-> (select-keys e (keys e))
      (assoc :db/id (:db/id e))))

(defn- non-db-keys [a-map]
  (remove #(= :db/id %) (keys a-map)))

(defn entity-name [a-map]
  (if a-map
    (-> a-map non-db-keys first .getNamespace)))

(defn guid-key [a-map]
  (if a-map
    (-> a-map entity-name (str "/guid") keyword)))

(defn assoc-demonic-attributes [entity-or-map]
  (when entity-or-map
    (let [gk (guid-key entity-or-map)]
      (-> entity-or-map
          entity->map          
          (assoc gk (or (gk entity-or-map) (db/squuid)))
          (assoc :db/id (or (:db/id entity-or-map) (db/tempid :db.part/user)))))))

(defn schema-attrib-name [attrib-id]
  (-> '[:find ?a :in $ ?e :where [?e :db/ident ?a]]
      (q @DATOMIC-DB attrib-id)
      ffirst))