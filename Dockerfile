# ==== 默认：Java 25 LTS（生产推荐） ====
# Maven 3.9 构建镜像 + Eclipse Temurin JDK 25（OpenJDK 官方下游）
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn package -DskipTests

# 运行阶段仅需 JRE（25-jre 为 JRE 镜像）
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
