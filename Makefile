.PHONY: test verify run docker-up docker-down logs

test:
	./mvnw test

verify:
	./mvnw clean verify

run:
	./mvnw spring-boot:run

docker-up:
	docker compose up --build -d

docker-down:
	docker compose down

logs:
	docker compose logs -f bot
