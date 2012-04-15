(ns zolodeck.demonic.schema
  (:use [datomic.api :only [tempid] :as db]))

(defn fact-schema [attribute value-type fulltext? doc]
  {:db/id (db/tempid :db.part/db)
   :db/ident attribute
   :db/valueType value-type
   :db/cardinality :db.cardinality/one
   :db/fulltext fulltext?
   :db/doc doc
   :db.install/_attribute :db.part/db})

(defn string-fact-schema [attribute fulltext? doc]
  (fact-schema attribute :db.type/string fulltext? doc))
