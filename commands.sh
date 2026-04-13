lein uberjar

java -XX:AOTMode=record -XX:AOTConfiguration=FerzSys.aotconfig -jar target/ferz-sys-0.1.0-SNAPSHOT-standalone.jar
java -XX:AOTMode=create -XX:AOTConfiguration=FerzSys.aotconfig -XX:AOTCache=FerzSys.aot -jar target/ferz-sys-0.1.0-SNAPSHOT-standalone.jar
java -XX:AOTCache=FerzSys.aot -XX:StartFlightRecording=filename=prod.jfr,dumponexit=true,settings=profile -jar target/ferz-sys-0.1.0-SNAPSHOT-standalone.jar

java -XX:StartFlightRecording=filename=normal.jfr,dumponexit=true,settings=profile -jar target/ferz-sys-0.1.0-SNAPSHOT-standalone.jar




