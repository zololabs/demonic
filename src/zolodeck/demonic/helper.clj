(ns zolodeck.demonic.helper
  (:use [datomic.api :only [q db tempid squuid] :as db]
        [zolodeck.demonic.schema :as schema]
        [zolodeck.utils.clojure :only [defrunonce random-guid diff]]
        [zolodeck.utils.maps :only [select-keys-if] :as maps]
        [zolodeck.utils.debug]
        [zolodeck.utils.clojure])
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
  (let [value (cond
               (schema/is-single-ref-attrib? attrib) (:db/id value-or-values)
               (and 
                (schema/is-multiple-ref-attrib? attrib)
                (sequential? value-or-values)) (map :db/id value-or-values)
                (and
                 (schema/is-multiple-ref-attrib? attrib)
                 (not (sequential? value-or-values))) (:db/id value-or-values)
               :else value-or-values)]
    [:db/retract (:db/id entity) attrib value]))

(defn retract-entity-txn [entity]
  [:db.fn/retractEntity (:db/id entity)])

(defn append-ref-txn [entity attrib value-entities]
  [:db/add (:db/id entity) attrib (map :db/id value-entities)])

(defn- datomic-transact [txns]
  (print-vals "********** COMMIT: txns:" txns)
  (try-catch
   (let [tf (db/transact-async CONN txns)]
     (print-vals "TF-RESULT:"@tf))))

;; (defn speculative-transact [db txns]
;;   (-> db
;;       (db/with txns)
;;       :db-after))

;; (defn run-transaction [tx-data]
;;   (swap! TX-DATA conj tx-data)
;;   (swap! DATOMIC-DB speculative-transact tx-data))

(defn schema-attrib-name [attrib-id]
  (-> '[:find ?a :in $ ?e :where [?e :db/ident ?a]]
      (q @DATOMIC-DB attrib-id)
      ffirst))

(defn tx-data-from-datoms [tx-datoms]
  (map #(vector (if (.added %) :db/add :db/retract)
                (.e %)
                (schema-attrib-name (.a %))
                (.v %)) tx-datoms))

(defn run-transaction [my-tx-data]
  (let [{:keys [tx-data db-after]} (db/with @DATOMIC-DB my-tx-data)
        tx-data (->> tx-data tx-data-from-datoms (remove (fn [[_ _ a _ _]] (= :db/txInstant a)))
                     )]
    (print-vals "MY-TX-DATA:" my-tx-data)
    (print-vals "TX-DATA:" tx-data)
    (swap! TX-DATA conj tx-data)
    (reset! DATOMIC-DB db-after)))

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

(defn- guid-key [a-map]
  (-> a-map non-db-keys first .getNamespace (str "/guid") keyword))

(defn assoc-demonic-attributes [entity-or-map]
  (when entity-or-map
    (let [gk (guid-key entity-or-map)]
      (-> entity-or-map
          entity->map          
          (assoc gk (or (gk entity-or-map) (db/squuid)))
          (assoc :db/id (or (:db/id entity-or-map) (db/tempid :db.part/user)))))))

