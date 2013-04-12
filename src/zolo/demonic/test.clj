(ns zolo.demonic.test
  (:require [datomic.api :as db])
  (:use [clojure.test :only [run-tests deftest is are testing]]
        [zolo.demonic.core :only [in-demarcation]]
        [zolo.demonic.helper :only [DATOMIC-TEST start-it-up-]]))

(defmacro with-demonic-demarcation [in-test? & body]
  `(binding [DATOMIC-TEST ~in-test?]
     (in-demarcation ~@body)))

(defmacro demonictest [test-name & body]
  `(deftest ~test-name
     (with-demonic-demarcation true ~@body)))

(defmacro demonic-testing [message & body]
  `(testing ~message
     (with-demonic-demarcation true ~@body)))

(defn re-initialize-db [datomic-db-name datomic-schema]
  (db/delete-database datomic-db-name)
  (start-it-up- datomic-db-name datomic-schema))