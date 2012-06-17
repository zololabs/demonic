(ns zolodeck.demonic.loadable
  (:use zolodeck.demonic.helper
        zolodeck.utils.debug
        [slingshot.slingshot :only [throw+ try+]])
  (:require [datomic.api :as db]
            [zolodeck.demonic.schema :as schema]))

(declare get-value seq-entry)

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
  (seq [this] (map seq-entry m))
  
  clojure.lang.IFn
  (invoke [this] this)
  (invoke [this k] (get-value m k))
  (invoke [this k v] (get-value m k v))
  (applyTo [this args] (clojure.lang.AFn/applyToHelper this args))

  Object
  (equals [self o]
    (= self o)))

(defn new-loadable [a-map]
  (if (instance? datomic.query.EntityMap a-map)
    (throw+ {:severity :fatal} "Loadable recieved unexpected object of type datomic.query.EntityMap"))
  (Loadable. a-map))

(defn entity->loadable [e]
  (-> e entity->map new-loadable))

(defn is-loadable? [v]
  (instance? zolodeck.demonic.loadable.Loadable v))

(defn to-loadable-if-needed [v]
  (if (is-loadable? v) v (entity->loadable v)))

(defn seq-entry [[k v :as entry]]
  (cond
   (schema/is-single-ref-attrib? k) (clojure.lang.MapEntry. k (to-loadable-if-needed v))
   (schema/is-multiple-ref-attrib? k) (clojure.lang.MapEntry. k (map to-loadable-if-needed v))
   :else entry))

(defn get-value
  ([m attrib not-found-value]
     (let [v (attrib m)]
       (cond
        (nil? v) not-found-value
        (schema/is-single-ref-attrib? attrib) (entity->loadable v)
        (schema/is-multiple-ref-attrib? attrib) (map entity->loadable v)         
        :otherwise v)))
  ([m attrib]
     (get-value m attrib nil)))

