(ns zolodeck.demonic.loadable
  (:use zolodeck.demonic.helper
        zolodeck.utils.debug)
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
  ;(seq [this] (seq m))  
  
  clojure.lang.IFn
  (invoke [this] this)
  (invoke [this k] (get-value m k))
  (invoke [this k v] (get-value m k v))
  (applyTo [this args] (clojure.lang.AFn/applyToHelper this args))

  Object
  (equals [self o]
    (= self o)))

(defn new-loadable [a-map]
  (Loadable. a-map))

;; (defn print-friends [m]
;;   (if m
;;     (print-vals "Map Into Friends:" (:user/friends m)))
;;   m)

;; (defn entity->loadable [e]
;;   (-> {:db/id (:db/id e)}
;;       (merge e)
;;       print-friends
;;       new-loadable))

(defn entity->loadable [e]
  ;(print-vals "Friends before:" (:user/friends e))
  (-> (entity->map e)
      new-loadable))

(defn is-loadable? [v]
  (instance? zolodeck.demonic.loadable.Loadable v))

(defn is-not-loadable? [v]
  (not (is-loadable? v)))

(defn to-loadable-if-needed [v]
  (if (is-not-loadable? v) (entity->loadable v) v))

(defn seq-entry [[k v :as entry]]
  (cond
   (schema/is-single-ref-attrib? k) (clojure.lang.MapEntry. k (to-loadable-if-needed v))
   (schema/is-multiple-ref-attrib? k) (clojure.lang.MapEntry. k (map to-loadable-if-needed v))
   :else entry))

;; (defn entity-id->loadable [e-id]
;;   (-> (db/entity @DATOMIC-DB e-id)
;;       entity->loadable))

(defmulti load-ref (fn [attrib _] (schema/cardinality attrib)))

(defmethod load-ref :db.cardinality/one [attrib value]
  (entity->loadable value))

(defmethod load-ref :db.cardinality/many [attrib values]
  (map entity->loadable values))

(defn load-attrib-and-update-loadable [m attrib v]
  (load-ref attrib v))

(defn get-value
  ([m attrib not-found-value]
     (let [v (attrib m)]
       (cond
        (nil? v) not-found-value
        (schema/is-ref? attrib) (load-attrib-and-update-loadable m attrib v)
        :otherwise v)))
  ([m attrib]
     (get-value m attrib nil)))

