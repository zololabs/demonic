(ns zolodeck.demonic.schema
  (:use [datomic.api :only [tempid] :as db]))

(def SCHEMA-MAP (atom {}))

(defn fact-schema [attribute value-type cardinality fulltext? doc]
  (let [schema {:db/id (db/tempid :db.part/db)
                :db/ident attribute
                :db/valueType value-type
                :db/cardinality :db.cardinality/one
                :db/fulltext fulltext?
                :db/doc doc
                :db.install/_attribute :db.part/db}]
    (swap! SCHEMA-MAP assoc attribute schema)
    schema))

(defn string-fact-schema [attribute fulltext? doc]
  (fact-schema attribute :db.type/string :db.cardinality/one fulltext? doc))

(defn refs-fact-schema [attribute fulltext? doc]
  (fact-schema attribute :db.type/ref :db.cardinality/many fulltext? doc))