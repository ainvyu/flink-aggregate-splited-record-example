run_docker_compose_interactive:
	$(eval FLINK_DOCKER_IMAGE_NAME := $(shell echo "flink:1.8.3-scala_2.11"))
	FLINK_DOCKER_IMAGE_NAME=$(FLINK_DOCKER_IMAGE_NAME) docker-compose up --no-deps --build