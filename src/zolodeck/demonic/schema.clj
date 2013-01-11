(ns zolodeck.demonic.schema
  (:use [datomic.api :only [tempid] :as db]
        zolodeck.utils.debug))

(def SCHEMA-MAP (atom {}))

(def SCHEMA-TYPE (atom {}))

(defn add-to-schema-map [attribute schema]
  (swap! SCHEMA-MAP assoc attribute schema))

(defn add-to-schema-type [attribute enum-type]
  (swap! SCHEMA-TYPE assoc attribute enum-type))

;; declaration

(defn fact-schema [attribute enum-type value-type cardinality fulltext? doc is-component? no-history?]
  (let [schema {:db/id (db/tempid :db.part/db)
                :db/ident attribute
                :db/valueType value-type
                :db/cardinality cardinality
                :db/fulltext fulltext?
                :db/doc doc
                :db.install/_attribute :db.part/db
                :db/isComponent is-component?
                :db/noHistory no-history?}]
    ;(swap! SCHEMA-MAP assoc attribute schema)
    (add-to-schema-map attribute schema)
    (add-to-schema-type attribute enum-type)
    schema))

(defn string-fact-schema [attribute fulltext? doc is-component? no-history?]
  (fact-schema attribute false :db.type/string :db.cardinality/one fulltext? doc is-component? no-history?))

(defn strings-fact-schema [attribute fulltext? doc is-component? no-history?]
  (fact-schema attribute false :db.type/string :db.cardinality/many fulltext? doc is-component? no-history?))

(defn long-fact-schema [attribute fulltext? doc is-component? no-history?]
  (fact-schema attribute false :db.type/long :db.cardinality/one fulltext? doc is-component? no-history?))

(defn boolean-fact-schema [attribute fulltext? doc is-component? no-history?]
  (fact-schema attribute false :db.type/boolean :db.cardinality/one fulltext? doc is-component? no-history?))

(defn instant-fact-schema [attribute fulltext? doc is-component? no-history?]
  (fact-schema attribute false :db.type/instant :db.cardinality/one fulltext? doc is-component? no-history?))

(defn uuid-fact-schema [attribute fulltext? doc is-component? no-history?]
  (fact-schema attribute false :db.type/uuid :db.cardinality/one fulltext? doc is-component? no-history?))

(defn refs-fact-schema [attribute fulltext? doc is-component? no-history?]
  (fact-schema attribute false :db.type/ref :db.cardinality/many fulltext? doc is-component? no-history?))

(defn ref-fact-schema [attribute fulltext? doc is-component? no-history?]
  (fact-schema attribute false :db.type/ref :db.cardinality/one fulltext? doc is-component? no-history?))

(defn enum-fact-schema [attribute fulltext? doc is-component? no-history?]
  (fact-schema attribute true :db.type/ref :db.cardinality/one fulltext? doc is-component? no-history?))

(defn enum-value-schema [value]
  [:db/add (db/tempid :db.part/user) :db/ident value])

;; introspection

(defn is-enum? [attribute]
  (get-in @SCHEMA-TYPE [attribute]))

(defn is-ref? [attribute]
  (-> (get-in @SCHEMA-MAP [attribute :db/valueType])
      (= :db.type/ref)))

(defn cardinality [attribute]
  (get-in @SCHEMA-MAP [attribute :db/cardinality]))

(defn is-cardinality-many? [attribute]
  (= (cardinality attribute) :db.cardinality/many))

(defn is-cardinality-one? [attribute]
  (= (cardinality attribute) :db.cardinality/one))

(defn is-attrib-type? [attribute a-type cardinality]
  (and (= a-type (get-in @SCHEMA-MAP [attribute :db/valueType]))
       (= cardinality (get-in @SCHEMA-MAP [attribute :db/cardinality]))))

(defn is-long? [attribute]
  (is-attrib-type? attribute :db.type/long :db.cardinality/one))

(defn is-string? [attribute]
  (is-attrib-type? attribute :db.type/string :db.cardinality/one))

(defn is-strings? [attribute]
  (is-attrib-type? attribute :db.type/string :db.cardinality/many))

(defn is-multiple-ref-attrib? [k]
  (and (is-ref? k) (is-cardinality-many? k)))

(defn is-single-ref-attrib? [k]
  (and (is-ref? k) (is-cardinality-one? k)))

(defn has-refs? [a-map]
  (->> a-map keys (some is-ref?)))
