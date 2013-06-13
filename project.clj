(defproject zololabs/demonic "0.1.0-SNAPSHOT"
  :description "Datomic helper for unit-testing, batch commits, and more"

  :dependencies [[org.clojure/clojure "1.5.1"]
                 [com.datomic/datomic-free "0.8.4007"]

                 [org.clojure/tools.logging "0.2.4"]
                 [slingshot "0.10.2"]                 
                 [zololabs/zolo-utils "0.1.0-SNAPSHOT"]
                 [org.clojure/tools.logging "0.2.4"]]

  :plugins [[lein-swank "1.4.4"]
            [lein-pprint "1.1.1"]
            [lein-clojars "0.9.1"]]

  :profiles {:dev
             {:dependencies [[clj-stacktrace "0.2.4"]
                             [swank-clojure "1.3.3"]]}}

  :min-lein-version "1.7.0"

  :warn-on-reflection false

  :test-selectors {:default (fn [t] (not (:integration t)))
                   :integration :integration
                   :all (fn [t] true)}

  :project-init (do (use 'clojure.pprint)
                    (use 'clojure.test)))