(ns zolodeck.demonic.loadable
  (:use zolodeck.demonic.helper
        zolodeck.utils.debug)
  (:require [datomic.api :as db]
            [zolodeck.demonic.schema :as schema]))

(declare get-value)

(deftype Loadable [m]
  clojure.lang.IPersistentMap
  (assoc [this k v] (Loadable. (assoc m k v)))
  
  clojure.lang.Associative
  (entryAt [this k]
    (find m k))

  clojure.lang.IPersistentCollection
  (cons [this o]
        (Loadable. (merge m o)))
  (equiv [self o]
    (= m o))

  clojure.lang.Counted
  (count [this]
    (count m))
  
  clojure.lang.ILookup
  (valAt [this k]
    (get-value m k))
  (valAt [this k v]
    (get-value m k v))
  
  clojure.lang.Seqable
  (seq [this] (seq m))

  clojure.lang.IFn
  (invoke [this] this)
  (invoke [this k] (k m))
  (invoke [this k v] (k m v))
  (applyTo [this args] (clojure.lang.AFn/applyToHelper this args))

  Object
  (equals [self o]
    (= self 0)))

(defn new-loadable [a-map]
  (Loadable. a-map))

(defn entity->loadable [e]
  (-> {:db/id (:db/id e)}
        (into e)
        new-loadable))

(defn entity-id->loadable [e-id]
  (-> (db/entity @DATOMIC-DB e-id)
      entity->loadable))

(defmulti load-ref (fn [attrib _] (schema/cardinality attrib)))

(defmethod load-ref :db.cardinality/one [attrib value]
  (entity-id->loadable (:db/id value)))

(defmethod load-ref :db.cardinality/many [attrib values]
  (map #(entity-id->loadable (:db/id %)) values))

(defn load-attrib-and-update-loadable [m attrib v]
  (let [e (load-ref attrib v)]
    e))

(defn get-value
  ([m attrib not-found-value]
     (let [v (m attrib)]
       (cond
        (nil? v) not-found-value
        (schema/is-ref? attrib) (load-attrib-and-update-loadable m attrib v)
        :otherwise v)))
  ([m attrib]
     (get-value m attrib nil)))

