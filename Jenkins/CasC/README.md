# Jenkins Configuration as Code

This repository shows how to manage Jenkins with help of [Configuration as Code](https://github.com/jenkinsci/configuration-as-code-plugin) plugin based on human-readable declarative configuration files.

This setup provides a Jenkins instance based on Docker with following components:

- General Jenkins configuration (base URL, welcome message, admin user)

- JGit as tool installation for checkout from Git

- Docker cloud to provision dynamic Docker agents

- Prepared jobs configured via [Job DSL](https://github.com/jenkinsci/job-dsl-plugin):

  - Maven docker build

  - Secret handling using encrypted text

- Securely stored credentials

## Prerequisites

- [Docker Desktop for Windows](https://docs.docker.com/docker-for-windows/install/) (switch to Linux containers)

## Usage

In order to deploy this local Jenkins setup simply run: `docker-compose up --build`

Next steps:

- Login to Jenkins at `http://localhost:8080` with admin account (`admin:admin` by default)

- Browse Jenkins and see applied configurations and compare with YAML content.

- Schedule prepared job and see how dynamic agent provision and secret decryption work

- Change configurations, restart setup and check whether they get applied accordingly

To stop all instances run: `docker-compose down`

## Further Information

- [Jenkins Configuration as Code](https://www.jenkins.io/projects/jcasc/)

- [Jenkins and Docker](https://www.jenkins.io/solutions/docker/)

- [Secret Handling](https://github.com/jenkinsci/configuration-as-code-plugin/blob/master/docs/features/secrets.adoc)
