I’ve been writing some code to work with Datomic, and thought some of this might be useful to others. So I’ve put it into a small utility project called demonic. There are a few concepts in demonic:

The first is that of a demarcation. A demarcation is kind of a dynamic binding, within which, all datomic transactions are held until the end. When the scope exits, all the datomic transactions are committed at once. This is useful in a scenario where you’re updating multiple entities in a single request (say), and you want them all to happen or you want them all to roll-back. Here’s how you’d use it:

    (demonic/in-demarcation
      (some-datomic-operation)
      (another-datomic-operation))

The good thing is that this makes it easy to write cleaner tests. There are two macros that help, namely demonictest and demonic-testing, which are respectively like deftest and testing. You could use them like this:

    (demonictest test-demonictest
      (is (nil? (:db/id (find-by-id some-id))))
      (demonic/insert some-map)
      (is (not (nil? (:db/id (find-by-id some-id))))))

or:

    (deftest test-demonic-testing
      (demonic-testing "check not present, insert, then check present" 
        (is (nil? (:db/id (find-by-id some-id))))
        (demonic/insert some-map)
        (is (not (nil? (:db/id (find-by-id some-id)))))))

As you can see, there are several CRUD operations provided by demonic, and in order to get the above benefits (of demarcations and testability), you need to only go through these functions (and not directly call the datomic functions). Here are these basic functions:

* _demonic/insert_ – accepts a map, if it doesn’t contain a :db/id key, an insertion occurs, else an update will occur
* _demonic/load-entity_ – accepts a datomic entity id, and loads the associated entity
* _demonic/delete_ – accepts a datomic entity id, and deletes the associated entity (and all references to it)
* _demonic/run-query_ – accepts a datomic query, and any other data-sources, and executes it against the current snapshot of the db (within the demarcation)

By the way, there’s another helper function for when you’re building web-apps using Compojure:

* _demonic/wrap-demarcation_ – sets up a demonic demarcation for the web-request

So these are a few things I’ve got in there right now. I’m also working on making it easy to create and maintain datomic schemas. I’ll write about that another time, once it is a bit more baked.
