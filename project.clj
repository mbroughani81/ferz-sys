(defproject io.github.mbroughani81/ferz-sys "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.12.1"]
                 [org.clojure/core.async "1.8.741"]
                 [mvxcvi/puget "1.3.4"]
                 [com.taoensso/timbre "6.6.2"]
                 [jarohen/chime "0.3.3"]]
  :repl-options {:init-ns io.github.mbroughani81.core}
  :main io.github.mbroughani81.core
  :aot [io.github.mbroughani81.core])
