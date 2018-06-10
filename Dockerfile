FROM clojure:lein-alpine

COPY . /src

WORKDIR /src

RUN lein deps && lein uberjar

WORKDIR /blog

CMD ["java", "-jar", "/src/target/uberjar/nlogn-0.1.0-SNAPSHOT-standalone.jar", "-c", "config.edn"]
