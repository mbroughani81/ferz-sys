(defproject io.github.mbroughani81/ferz-sys "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url  "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.12.1"]
                 [org.clojure/core.async "1.8.741"]
                 [mvxcvi/puget "1.3.4"]
                 [com.taoensso/tufte "3.0.0"]
                 [com.taoensso/timbre "6.6.2"]
                 [jarohen/chime "0.3.3"]
                 [org.clojure/test.check "1.1.1"]
                 [virgil "0.5.1"]
                 ;; Source: https://mvnrepository.com/artifact/com.clojure-goes-fast/clj-async-profiler
                 [com.clojure-goes-fast/clj-async-profiler "2.0.0-beta1"]
                 ;; Program Analysis
                 [org.soot-oss/sootup.core "2.0.0-SNAPSHOT"]
                 [org.soot-oss/sootup.java.core "2.0.0-SNAPSHOT"]
                 [org.soot-oss/sootup.interceptors "2.0.0-SNAPSHOT"]
                 [org.soot-oss/sootup.java.bytecode.frontend "2.0.0-SNAPSHOT"]
                 [org.soot-oss/sootup.jimple.frontend "2.0.0-SNAPSHOT"]
                 [org.soot-oss/sootup.apk.frontend "2.0.0-SNAPSHOT"]
                 [org.soot-oss/sootup.callgraph "2.0.0-SNAPSHOT"]
                 [org.soot-oss/sootup.analysis.intraprocedural "2.0.0-SNAPSHOT"]
                 [org.soot-oss/sootup.analysis.interprocedural "2.0.0-SNAPSHOT"]
                 [org.soot-oss/sootup.qilin "2.0.0-SNAPSHOT"]
                 [org.soot-oss/sootup.codepropertygraph "2.0.0-SNAPSHOT"]
                 [org.slf4j/slf4j-simple "2.0.17"]
                 ]
  :source-paths ["src/main/clojure"]
  :java-source-paths ["src/main/java"]
  :repl-options {:init-ns io.github.mbroughani81.core}
  :jvm-opts ["-Djdk.attach.allowAttachSelf"]
  :main io.github.mbroughani81.core
  :aot [io.github.mbroughani81.core]
  )
