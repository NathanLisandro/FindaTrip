# O runtime precisa do Chrome, e e ele que faz a imagem grande.
# Raspando site com JavaScript nao ha como fugir disso.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
COPY scrapers ./scrapers
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:21-jre
# Bibliotecas que o Chrome exige, mais o proprio Chrome do repositorio da Google.
RUN apt-get update && apt-get install -y --no-install-recommends \
      wget gnupg ca-certificates fonts-liberation libnss3 libnspr4 \
      libatk1.0-0 libatk-bridge2.0-0 libcups2 libdrm2 libxkbcommon0 \
      libxcomposite1 libxdamage1 libxfixes3 libxrandr2 libgbm1 \
      libpango-1.0-0 libcairo2 libasound2t64 libatspi2.0-0 \
 && wget -q -O /tmp/chrome.deb https://dl.google.com/linux/direct/google-chrome-stable_current_amd64.deb \
 && apt-get install -y --no-install-recommends /tmp/chrome.deb \
 && rm /tmp/chrome.deb && rm -rf /var/lib/apt/lists/*

# O Playwright usa o Chrome do sistema (setChannel "chrome"); nao baixe os proprios.
ENV PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1

WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
COPY --from=build /app/scrapers ./scrapers
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
