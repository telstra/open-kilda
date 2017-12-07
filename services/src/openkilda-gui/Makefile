# DOCKER TASKS
# Build the container
build-openkilda: ## Build the container
	docker-compose build openkilda_gui

build-nc-openkilda: ## Build the container without caching
	docker-compose build --no-cache openkilda_gui

run: ## Run container on port configured 
	docker-compose up -d 


up: build-openkilda run ## Run container on port configured in `docker-compose.yml`

stop: ## Stop and remove a running container
	docker stop openkilda_gui; docker rm openkilda_gui
