FROM eclipse-temurin:21-jdk AS build
WORKDIR /build
COPY src src
COPY pom.xml mvnw ./
COPY .mvn .mvn
RUN bash ./mvnw -B -Dmaven.test.skip=true package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/target/item-bro.jar item-bro.jar
ENTRYPOINT ["java", "-jar", "/app/item-bro.jar"]
