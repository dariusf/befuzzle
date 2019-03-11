
# Befuzzle

Fuzz REST APIs using an OpenAPI spec and property-based testing.

## Getting started

The easiest way to try this is to run [a pet store server](https://github.com/spring-projects/spring-petclinic) locally on port 8080, then do `./befuzzle http://petstore.swagger.io/v2/swagger.json`.

To run this on your own server, you need an OpenAPI spec as input. You can write one by hand or generate it with [Swagger](https://swagger.io/open-source-integrations/). For example, one might use [SpringFox](http://springfox.github.io/springfox/) for a Spring Boot application.

## Proxy

The value of `http_proxy` is used.
