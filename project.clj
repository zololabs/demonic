(defproject zolodeck/demonic "0.1.0-SNAPSHOT"
  :description "Datomic helper for testing and batch commits"

  :dependencies [[org.clojure/clojure "1.4.0"]
                 [com.datomic/datomic-free "0.8.3731"]

                 [org.clojure/tools.logging "0.2.4"]
                 [slingshot "0.10.2"]                 
                 [zolodeck/zolo-utils "0.1.0-SNAPSHOT"]
                 [org.clojure/tools.logging "0.2.4"]]

  :plugins [[lein-swank "1.4.4"]
            [lein-pprint "1.1.1"]
            [lein-difftest "1.3.8"]
            [lein-notes "0.0.1"]]

  :hooks [leiningen.hooks.difftest]

  :dev-dependencies [[clj-stacktrace "0.2.4"]
                     [swank-clojure "1.3.3"]]
  
  :min-lein-version "1.7.0"

  :warn-on-reflection false

  :test-selectors {:default (fn [t] (not (:integration t)))
                   :integration :integration
                   :all (fn [t] true)}

  :project-init (do (use 'clojure.pprint)
                    (use 'clojure.test)))