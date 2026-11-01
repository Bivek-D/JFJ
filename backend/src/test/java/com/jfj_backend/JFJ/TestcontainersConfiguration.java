package com.jfj_backend.JFJ;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

// Starts a throwaway PostGIS container for tests; @ServiceConnection points Spring's datasource at it.
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgres() {
		// Same image as docker-compose. "Compatible substitute" tells Testcontainers it's still Postgres.
		return new PostgreSQLContainer(
				DockerImageName.parse("postgis/postgis:16-3.6").asCompatibleSubstituteFor("postgres"));
	}
}
