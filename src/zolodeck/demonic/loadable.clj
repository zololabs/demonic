(ns zolodeck.demonic.loadable)

(deftype Loadable [m]
  clojure.lang.IPersistentMap
  (assoc [this k v] (Loadable. (atom (assoc @m k v))))

  clojure.lang.Counted
  (count [this]
    (count @m))
  
  clojure.lang.ILookup
  (valAt [this k]
    (@m k))
  (valAt [this k v]
    (@m k v))
  
  clojure.lang.Seqable
  (seq [this] (seq @m))

  clojure.lang.IFn
  (invoke [this] this)
  (invoke [this k] (k @m))
  (invoke [this k v] (k @m v))
  (applyTo [this args] (clojure.lang.AFn/applyToHelper this args)))

(defn new-loadable [a-map]
  (Loadable. (atom a-map)))