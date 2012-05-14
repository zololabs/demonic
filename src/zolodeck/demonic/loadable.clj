(ns zolodeck.demonic.loadable
  (:use zolodeck.demonic.helper
        zolodeck.utils.debug))

(deftype Loadable [m]
  clojure.lang.IPersistentMap
  (assoc [this k v] (Loadable. (assoc m k v)))

  clojure.lang.Counted
  (count [this]
    (count m))
  
  clojure.lang.ILookup
  (valAt [this k]
    (m k))
  (valAt [this k v]
    (m k v))
  
  clojure.lang.Seqable
  (seq [this] (seq m))

  clojure.lang.IFn
  (invoke [this] this)
  (invoke [this k] (k m))
  (invoke [this k v] (k m v))
  (applyTo [this args] (clojure.lang.AFn/applyToHelper this args)))

(defn new-loadable [a-map]
  (Loadable. a-map))