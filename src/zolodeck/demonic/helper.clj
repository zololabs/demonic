(ns zolodeck.demonic.helper
  (:use [datomic.api :only [q db tempid squuid resolve-tempid] :as db]
        [zolodeck.demonic.schema :as schema]
        [zolodeck.utils.clojure :only [defrunonce random-guid diff]]
        [zolodeck.utils.maps :only [select-keys-if] :as maps]
        [zolodeck.utils.debug]
        [zolodeck.utils.clojure])
  (:require [clojure.tools.logging :as logger]))

(def CONN)
(def ^:dynamic TX-DATA)
(def ^:dynamic DATOMIC-DB)
(def ^:dynamic TEMP-IDS)
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

;; (defn next-temp-id []
;;   (- (System/currentTimeMillis)))

(defn get-new-temp-id []
  (let [t (db/tempid :db.part/user)
        temp-ids (-> TEMP-IDS
                     deref
                     last
                     (assoc t nil))]
    (reset! TEMP-IDS (conj (apply vector (butlast @TEMP-IDS)) temp-ids))
    t))

(defn pop-tempids []
  (let [t (first @TEMP-IDS)]
    (swap! TEMP-IDS rest)
    t))

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

(defn update-with-realized-temp-ids [realized-tempids txn]
  (print-vals "TXN:" txn)
  (print-vals "Realized:" realized-tempids)  
  (cond
   :else txn))

(defn update-all-with-realized-temp-ids [txns]
  (let [realized (pop-tempids)]
    (map #(update-with-realized-temp-ids realized %) txns)))

(defn- datomic-transact [txns]
  (let [txns (update-all-with-realized-temp-ids txns)
        tf (db/transact-async CONN txns)]
    @tf))

;; (defn speculative-transact [db txns]
;;   (-> db
;;       (db/with txns)
;;       :db-after))

;; (defn update-db-speculatively [tx-data]
    ;(swap! DATOMIC-DB speculative-transact tx-data)
;; )

(defn run-transaction [tx-data]
  (print-vals "Starting run-transaction...")
  (let [{:keys [db-after tempids]} (db/with @DATOMIC-DB tx-data)
        tempids-slice (print-vals "tempids-slice:" (last (print-vals "TEMP-IDS:" @TEMP-IDS)))
        tempids-slice (reduce #(assoc %1 %2 (db/resolve-tempid db-after tempids %2))
                              tempids-slice
                              (keys tempids-slice))]
    (reset! DATOMIC-DB db-after)
    (swap! TX-DATA conj {:tx-data tx-data :temp-ids tempids-slice})
    (swap! TEMP-IDS conj {}))
  ;; (update-db-speculatively tx-data)
  ;; (store-tx-data-for-later tx-data)
  )

(defn commit-pending-transactions []
  (when-not DATOMIC-TEST
    (when-not (empty? @TX-DATA)
      (doseq [{t :tx-data} @TX-DATA]
        (when-not (empty? t)
          (datomic-transact (doall t)))))))

(defn run-in-demarcation [thunk]
  (binding [TX-DATA (atom [])
            DATOMIC-DB (atom (db/db CONN))
            TEMP-IDS (atom [{}{}])]
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
          (assoc :db/id (or (:db/id entity-or-map) (get-new-temp-id)))))))

(defn schema-attrib-name [attrib-id]
  (-> '[:find ?a :in $ ?e :where [?e :db/ident ?a]]
      (q @DATOMIC-DB attrib-id)
      ffirst))