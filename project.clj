(defproject kafka-stream-swirl "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [fundingcircle/jackdaw "0.6.6"]]
  :repl-options {:init-ns kafka-stream-swirl.core}
  :profiles {
             :dev
             {:source-paths
              ["dev"]

              :injections [(require 'io.aviso.logging.setup)]
              :dependencies [[io.aviso/logging "0.3.2"]
                             [org.apache.kafka/kafka-streams-test-utils "2.2.0"]
                             [org.apache.kafka/kafka-clients "2.2.0" :classifier "test"]
                             [org.clojure/test.check "0.9.0"]]}})
