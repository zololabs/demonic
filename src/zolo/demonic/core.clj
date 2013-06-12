(ns zolo.demonic.core
  (:use [datomic.api :only [q db tx-report-queue history as-of entity] :as db]
        [zolo.utils.clojure]
        zolo.demonic.loadable
        zolo.demonic.helper        
        zolo.demonic.refs
        zolo.utils.debug))

(defmacro in-demarcation [& body]
  `(run-in-demarcation (fn [] ~@body)))

(defn wrap-demarcation [handler]
  (fn [request]
    (if-not DATOMIC-TEST
      (in-demarcation (handler request))
      (handler request))))

(defrunonce init-db [datomic-db-name datomic-schema]
  (start-it-up- datomic-db-name datomic-schema))

(defn delete-db [datomic-db-name]
  (db/delete-database datomic-db-name))

(defn reset-db [datomic-db-name datomic-schema]
  (delete-db datomic-db-name)
  (start-it-up- datomic-db-name datomic-schema))

(defn run-query [query & extra-inputs]
  (apply q query @DATOMIC-DB extra-inputs))

(defn run-raw-query [query & sources]
  (apply q query sources))

(defn load-entity [eid]
  (let [e (load-from-db eid)]
    (when (:db/id e)
      (-> e entity->loadable))))

(defn insert [a-map]
  (when a-map
    (let [with-attribs (assoc-demonic-attributes a-map)]
      (-> with-attribs process-graph run-transaction)
      (with-attribs (guid-key with-attribs)))))

(defn reload-by-guid [guid-key guid]
  (when guid
    (-> (run-query '[:find ?e :in $ ?gk ?guid :where [?e ?gk ?guid]] guid-key guid)
        ffirst
        load-entity)))

(defn insert-and-reload [a-map]
  (->> a-map
       insert
       (reload-by-guid (guid-key a-map))))

(defn append-multiple [entity attrib value-entities]
  (let [with-attribs (map assoc-demonic-attributes value-entities)
        append-txn (append-ref-txn entity attrib with-attribs)]
    (run-transaction (conj with-attribs append-txn))))

(defn append-multiple-and-reload [entity attrib value-entities]
  (append-multiple entity attrib value-entities)
  (let [gk (guid-key entity)]
    (reload-by-guid gk (entity gk))))

(defn append-single [entity attrib value-entity]
  (append-multiple entity attrib [value-entity]))

(defn append-single-and-reload [entity attrib value-entity]
  (append-multiple-and-reload entity attrib [value-entity]))

(defn retract
  ([entity attrib value]
     (->> (retract-attribute-txn entity attrib value)
          vector
          run-transaction))
  ([entity attrib]
     (retract entity attrib (entity attrib))))

(defn delete [entity]
  (-> entity
      retract-entity-txn
      vector
      run-transaction))

(defn transactions-report-queue []
  (db/tx-report-queue CONN))

(defn schema-attrib-id [attrib-name]
  (-> '[:find ?e :in $ ?a :where [?e :db/ident ?a]]
      (run-query attrib-name)
      ffirst))

(defn versions
  "entity: e for which all versions are needed, attrib-that-changed based on which you want the versions, other-attribs-desired in the result-set, nil if all are required"
  ([entity attrib-that-changed & other-attribs-needed]
     (let [e (:db/id entity)
           required-keys (concat other-attribs-needed [:db/id attrib-that-changed])
           txes (q '[:find ?tx :in $ ?e ?a :where [?e ?a _ ?tx]] (db/history @DATOMIC-DB) e attrib-that-changed)
           entities (mapv #(db/entity (db/as-of @DATOMIC-DB (first %)) e) (sort txes))
           entities (if (empty? other-attribs-needed)
                      entities
                      (mapv #(select-keys % required-keys) entities))]
       (mapv entity->loadable entities))))