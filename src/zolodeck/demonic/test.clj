(ns zolodeck.demonic.test
  (:use [clojure.test :only [run-tests deftest is are testing]]
        [zolodeck.demonic.core :only [in-demarcation]]
        [zolodeck.demonic.helper :only [DATOMIC-TEST]]))

(defmacro with-demonic-demarcation [in-test? body]
  `(binding [DATOMIC-TEST ~in-test?]
     (in-demarcation ~@body)))

(defmacro zolotest [test-name & body]
  `(deftest ~test-name
     (with-demonic-demarcation true ~body)))

(defmacro zolo-testing [message & body]
  `(testing ~message
     (with-demonic-demarcation true ~body)))

