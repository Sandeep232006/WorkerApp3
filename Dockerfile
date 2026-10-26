FROM eclipse-temurin:21-jdk
WORKDIR /app
COPY . .
RUN javac GigEconomySystem.java WebApp.java
EXPOSE 8080
CMD ["java", "WebApp"]
