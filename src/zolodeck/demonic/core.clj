(ns zolodeck.demonic.core
  (:use [datomic.api :only [q db] :as db]
        zolodeck.demonic.helper))

(defmacro in-demarcation [& body]
  `(run-in-demarcation (fn [] ~@body)))

(defn wrap-demarcation [handler]
  (fn [request]
    (if-not DATOMIC-TEST
      (in-demarcation (handler request))
      (handler request))))

(defn init-db [datomic-db-name datomic-schema]
  (initialize-datomic datomic-db-name datomic-schema))

(defn run-query [query & extra-inputs]
  (apply q query @DATOMIC-DB extra-inputs))

(defn load-entity [eid]
  (db/entity @DATOMIC-DB eid))

(defn load-and-transform-with [eid transform]
  (let [e (load-entity eid)]
    (if (:db/id e)
      (transform e))))

(defn insert [a-map]
  (-> {:db/id #db/id[:db.part/user]}
      (merge a-map)
      vector
      run-transaction)
  a-map)

(defn insert-and-transform-with [a-map transform]
  (-> a-map
      insert
      transform))

(defn delete [entity-id]
  (-> [:db.fn/retractEntity entity-id]
      vector
      run-transaction))
