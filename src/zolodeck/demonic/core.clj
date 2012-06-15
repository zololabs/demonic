(ns zolodeck.demonic.core
  (:use [datomic.api :only [q db] :as db]
        zolodeck.demonic.loadable
        zolodeck.demonic.helper
        zolodeck.utils.debug))

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
  (let [e (load-from-db eid)]
    (when (:db/id e)
      (-> e entity->loadable))))

(defn load-and-transform-with [eid transform]
  (-> eid
      load-entity
      transform))

(defn insert [a-map]
  (-> (with-demonic-attributes a-map)
      (process-ref-attributes)
      run-transaction)
  a-map)

(defn insert-and-transform-with [a-map transform]
  (-> a-map
      insert
      transform))

(defn delete [entity-id]
  (-> entity-id
      retract-entity-txn
      vector
      run-transaction))
