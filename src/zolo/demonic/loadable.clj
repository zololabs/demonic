(ns zolo.demonic.loadable
  (:use zolo.demonic.helper
        zolo.utils.debug
        [slingshot.slingshot :only [throw+ try+]])
  (:require [datomic.api :as db]
            [zolo.demonic.schema :as schema]
            [zolo.utils.clojure :as zolo-clj]))

(declare get-value seq-entry is-loadable?)

(def NIL {:db/id nil})

(defn NIL? [e]
  (or (nil? e) (= NIL e)))

(deftype Loadable [m]
  clojure.lang.IPersistentMap
  (assoc [this k v] (Loadable. (assoc m k v)))
  (without [this k] (Loadable. (dissoc m k)))
  
  clojure.lang.Associative
  (entryAt [this k]
    (find m k))

  clojure.lang.IPersistentCollection
  (cons [this o]
        (Loadable. (merge m o)))
  (equiv [self o]
    (and (is-loadable? self)
         (is-loadable? o)
         (= m (.m o))))
  (empty [self]
    (empty? self))

  clojure.lang.Counted
  (count [this]
    (count m))
  
  clojure.lang.ILookup
  (valAt [this k]
    (get-value m k))
  (valAt [this k v]
    (get-value m k v))
  
  clojure.lang.Seqable
  ;(seq [this] (map seq-entry m))
  (seq [this] (seq m))

  clojure.lang.IObj
  (withMeta [this o]
    (with-meta m o))

  clojure.lang.IMeta
  (meta [this]
    (meta m))
  
  clojure.lang.IFn
  (invoke [this] this)
  (invoke [this k] (get-value m k))
  (invoke [this k v] (get-value m k v))
  (applyTo [this args] (clojure.lang.AFn/applyToHelper this args))

  Object
  (equals [self o]
    (and (is-loadable? self)
         (is-loadable? o)
         (= m (.m o))))
  (hashCode [self]
    (.hashCode m)))

(defn is-loadable? [o]
  (instance? Loadable o))

(defn new-loadable [a-map]
  (if (is-entity-map? a-map)
    (throw+ {:severity :fatal} "Loadable recieved unexpected object of type datomic.query.EntityMap"))
  (Loadable. a-map))

(defn entity->loadable [e]
  (if-not (NIL? e)
    (-> e entity->map new-loadable)))

(defn to-loadable-if-needed [v]
  (if (is-entity-map? v) (entity->loadable v) v))

(defn to-loadables [values]
  (when-not (or (nil? values) (zolo-clj/collection? values))
    (throw+ {:severity :fatal :value values} (str "Expected a collection , received :" (class values))))
  (map to-loadable-if-needed values))

(defn seq-entry [[k v :as entry]]
  (cond
   (schema/is-single-ref-attrib? k) (clojure.lang.MapEntry. k (to-loadable-if-needed v))
   (schema/is-multiple-ref-attrib? k) (clojure.lang.MapEntry. k (to-loadables v))
   :else entry))

(defn get-value
  ([m attrib not-found-value]
     (let [v (attrib m)]
       (cond
        (nil? v) not-found-value
        (schema/is-single-ref-attrib? attrib) (to-loadable-if-needed v)
        (schema/is-multiple-ref-attrib? attrib) (map to-loadable-if-needed v)         
        :otherwise v)))
  ([m attrib]
     (get-value m attrib nil)))

