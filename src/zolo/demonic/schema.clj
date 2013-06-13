(ns zolo.demonic.schema
  (:require [zolo.utils.maps :as maps])
  (:use [datomic.api :only [tempid] :as db]
        zolo.utils.debug
        zolo.utils.clojure))

(def SCHEMA-MAP (atom {}))

(def SCHEMA-TYPE (atom {}))

(defn add-to-schema-map [attribute schema]
  (swap! SCHEMA-MAP assoc attribute schema))

(defn add-to-schema-type [attribute enum-type]
  (swap! SCHEMA-TYPE assoc attribute enum-type))

;; declaration

(defn fact-schema [attribute enum-type value-type cardinality fulltext? doc uniqueness index? component? no-history?]
  (let [schema {:db/id (db/tempid :db.part/db)
                :db/ident attribute
                :db/valueType value-type
                :db/cardinality cardinality
                :db/fulltext fulltext? ;; defaults to false
                :db/doc doc
                :db/index (if index? index?) ;;defaults to false
                :db/isComponent (and component? (= value-type :db.type/ref)) ;;defaults to nil
                :db/noHistory no-history? ;; defaults to false                
                :db.install/_attribute :db.part/db}
        schema (if uniqueness
                 (assoc schema :db/unique uniqueness) ;; defaults to nil
                 schema)        
        schema (maps/remove-nil-vals schema)
        ]
    
    (add-to-schema-map attribute schema)
    (add-to-schema-type attribute enum-type)
    schema))

(defnk string-fact-schema [attribute doc :fulltext? false :uniqueness false :index? false :no-history? false]
  (fact-schema attribute false :db.type/string :db.cardinality/one fulltext? doc uniqueness index? false no-history?))

(defnk strings-fact-schema [attribute doc :fulltext? false :uniqueness false :index? false :no-history? false]
  (fact-schema attribute false :db.type/string :db.cardinality/many fulltext? doc uniqueness index? false no-history?))



(defnk long-fact-schema [attribute doc :fulltext? false :uniqueness false :index? false :no-history? false]
  (fact-schema attribute false :db.type/long :db.cardinality/one fulltext? doc uniqueness index? false no-history?))

(defnk longs-fact-schema [attribute doc :fulltext? false :uniqueness false :index? false :no-history? false]
  (fact-schema attribute false :db.type/long :db.cardinality/many fulltext? doc uniqueness index? false no-history?))



(defnk float-fact-schema [attribute doc :fulltext? false :uniqueness false :index? false :no-history? false]
  (fact-schema attribute false :db.type/float :db.cardinality/one fulltext? doc uniqueness index? false no-history?))

(defnk floats-fact-schema [attribute doc :fulltext? false :uniqueness false :index? false :no-history? false]
  (fact-schema attribute false :db.type/float :db.cardinality/many fulltext? doc uniqueness index? false no-history?))



(defnk double-fact-schema [attribute doc :fulltext? false :uniqueness false :index? false :no-history? false]
  (fact-schema attribute false :db.type/double :db.cardinality/one fulltext? doc uniqueness index? false no-history?))

(defnk doubles-fact-schema [attribute doc :fulltext? false :uniqueness false :index? false :no-history? false]
  (fact-schema attribute false :db.type/double :db.cardinality/many fulltext? doc uniqueness index? false no-history?))



(defnk bigdec-fact-schema [attribute doc :fulltext? false :uniqueness false :index? false :no-history? false]
  (fact-schema attribute false :db.type/bigdec :db.cardinality/one fulltext? doc uniqueness index? false no-history?))

(defnk bigdecs-fact-schema [attribute doc :fulltext? false :uniqueness false :index? false :no-history? false]
  (fact-schema attribute false :db.type/bigdec :db.cardinality/many fulltext? doc uniqueness index? false no-history?))



(defnk uri-fact-schema [attribute doc :fulltext? false :uniqueness false :index? false :no-history? false]
  (fact-schema attribute false :db.type/uri :db.cardinality/one fulltext? doc uniqueness index? false no-history?))

(defnk uris-fact-schema [attribute doc :fulltext? false :uniqueness false :index? false :no-history? false]
  (fact-schema attribute false :db.type/uri :db.cardinality/many fulltext? doc uniqueness index? false no-history?))



(defnk boolean-fact-schema [attribute doc :fulltext? false :uniqueness false :index? false :no-history? false]
  (fact-schema attribute false :db.type/boolean :db.cardinality/one fulltext? doc uniqueness index? false no-history?))

(defnk booleans-fact-schema [attribute doc :fulltext? false :uniqueness false :index? false :no-history? false]
  (fact-schema attribute false :db.type/boolean :db.cardinality/many fulltext? doc uniqueness index? false no-history?))



(defnk instant-fact-schema [attribute doc :fulltext? false :uniqueness false :index? false :no-history? false]
  (fact-schema attribute false :db.type/instant :db.cardinality/one fulltext? doc uniqueness index? false no-history?))

(defnk instants-fact-schema [attribute doc :fulltext? false :uniqueness false :index? false :no-history? false]
  (fact-schema attribute false :db.type/instant :db.cardinality/many fulltext? doc uniqueness index? false no-history?))



(defnk uuid-fact-schema [attribute doc :fulltext? false :uniqueness false :index? false :no-history? false]
  (fact-schema attribute false :db.type/uuid :db.cardinality/one fulltext? doc uniqueness index? false no-history?))

(defnk uuids-fact-schema [attribute doc :fulltext? false :uniqueness false :index? false :no-history? false]
  (fact-schema attribute false :db.type/uuid :db.cardinality/many fulltext? doc uniqueness index? false no-history?))



(defnk refs-fact-schema [attribute doc :fulltext? false :uniqueness false :index? false :component? false :no-history? false]
  (fact-schema attribute false :db.type/ref :db.cardinality/many fulltext? doc uniqueness index? component? no-history?))

(defnk ref-fact-schema [attribute doc :fulltext? false :uniqueness false :index? false :component? false :no-history? false]
  (fact-schema attribute false :db.type/ref :db.cardinality/one fulltext? doc uniqueness index? component? no-history?))



(defnk enum-fact-schema [attribute doc :fulltext? false :uniqueness false :index? false :no-history? false]
  (fact-schema attribute true :db.type/ref :db.cardinality/one fulltext? doc uniqueness index? false no-history?))

(defnk enums-fact-schema [attribute doc :fulltext? false :uniqueness false :index? false :no-history? false]
  (fact-schema attribute true :db.type/ref :db.cardinality/many fulltext? doc uniqueness index? false no-history?))



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
