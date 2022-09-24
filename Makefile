SHELL := /bin/bash
GIT_BRANCH := $(shell git rev-parse --abbrev-ref HEAD | sed "s/\//-/g")
GIT_HASH := $(shell git rev-parse --short HEAD)
BUILD_DATE := $(shell date -u +%Y%m%dT%H%M%S)
APP_NAME := "probematic"
DOCKER_REPO := ghcr.io
DOCKER_IMAGE := ${DOCKER_REPO}/sno/${APP_NAME}
DOCKER_TAG := ${GIT_BRANCH}-${GIT_HASH}
DOCKER_TAG_LATEST := ${GIT_BRANCH}-local-latest
DOCKER_BUILD_ARGS := --build-arg GIT_HASH=${GIT_HASH} --build-arg GIT_BRANCH=${GIT_BRANCH} --build-arg BUILD_DATE=${BUILD_DATE}  --build-arg DOCKER_IMAGE_TAG=${DOCKER_IMAGE}:${DOCKER_TAG}
DOCKERFILE ?= docker/Dockerfile
DOCKER ?= docker


cider-repl:
	source .env && clojure -A:dev -M:inspect/reveal-nrepl-cider

repl:
	clojure -A:test -A:dev -M:inspect/reveal-nrepl

serve:  ## Start server
	clojure -M:run-m

clean: ## Clean build
	clojure -T:build clean

.PHONY: ci
ci: ## Run tests and build
	clojure -T:build ci

.PHONY: test
test:
	./bin/kaocha

test-with-cov:
	clojure -T:build tests

db/up:
	docker-compose up -d

db/stop:
	docker-compose stop

db/destroy:
	docker-compose down --volumes

uberjar:
	clojure -T:build uberjar

docker-login:
	echo ${GITHUB_TOKEN} | ${DOCKER} login ghcr.io -u ${GITHUB_USER} --password-stdin

docker-build:
	${DOCKER} build ${DOCKER_BUILD_ARGS} -f ${DOCKERFILE} -t ${DOCKER_IMAGE}:latest-local .
	${DOCKER} tag ${DOCKER_IMAGE}:latest-local ${DOCKER_IMAGE}:${DOCKER_TAG}
	@echo
	@echo Built container image for ${APP_NAME}
	@echo ${DOCKER_IMAGE}:${DOCKER_TAG}
	@echo

docker-build-clean: clean docker-build

docker-publish:
	${DOCKER} push ${DOCKER_IMAGE}:${DOCKER_TAG}
	@echo
	@echo Pushed container image for ${APP_NAME}
	@echo ${DOCKER_IMAGE}:${DOCKER_TAG}
	@echo

deploy-staging-slot:
	az webapp config container set \
		--resource-group ${AZURE_RESOURCE_GROUP} \
		--name ${AZURE_APP_SERVICE_NAME} \
		--slot ${AZURE_APP_SERVICE_SLOT} \
		--docker-custom-image-name ${DOCKER_IMAGE}:${DOCKER_TAG}
	az webapp config container set \
		--resource-group ${AZURE_RESOURCE_GROUP} \
		--name ${AZURE_APP_SERVICE_NAME} \
		--slot ${AZURE_APP_SERVICE_SLOT} \
		--enable-app-service-storage

swap-staging-to-production:
	az webapp config container set \
		--resource-group ${AZURE_RESOURCE_GROUP} \
		--name ${AZURE_APP_SERVICE_NAME} \
		--enable-app-service-storage
	az webapp deployment slot swap \
		--resource-group ${AZURE_RESOURCE_GROUP} \
		--name ${AZURE_APP_SERVICE_NAME} \
		--slot ${AZURE_APP_SERVICE_SLOT} \
		--target-slot production
