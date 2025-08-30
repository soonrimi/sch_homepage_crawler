# 멀티스테이지 빌드를 사용하여 이미지 크기 최적화
# Eclipse Temurin JDK 17을 사용한 빌드 환경
FROM eclipse-temurin:17-jdk AS builder

WORKDIR /app
COPY build.gradle.kts settings.gradle.kts gradlew ./
COPY gradle ./gradle
COPY src ./src

# Gradle wrapper에 실행 권한 부여 및 빌드 실행
RUN chmod +x gradlew && ./gradlew build -x test

# 실행용 경량 이미지
FROM eclipse-temurin:17-jre

WORKDIR /app

# 빌드된 JAR 파일 복사
COPY --from=builder /app/build/libs/*.jar app.jar

# 애플리케이션 실행
EXPOSE 7100
ENTRYPOINT ["java", "-jar", "app.jar"]
