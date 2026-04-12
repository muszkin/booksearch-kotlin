package pl.fairydeck.booksearch.api

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.*
import org.yaml.snakeyaml.Yaml

fun Route.openApiRoutes() {
    val openApiJson = loadOpenApiSpecAsJson()

    get("/api/openapi.json") {
        call.respondText(openApiJson, ContentType.Application.Json)
    }

    get("/swagger-ui") {
        call.respondText(buildSwaggerUiHtml(), ContentType.Text.Html)
    }
}

private fun loadOpenApiSpecAsJson(): String {
    val yamlContent = Thread.currentThread().contextClassLoader
        .getResourceAsStream("openapi/api.yaml")
        ?.bufferedReader()
        ?.readText()
        ?: throw IllegalStateException("OpenAPI spec file not found at openapi/api.yaml")

    val yaml = Yaml()
    val parsed = yaml.load<Any>(yamlContent)
    return convertToJsonElement(parsed).toString()
}

private fun convertToJsonElement(value: Any?): JsonElement = when (value) {
    null -> JsonNull
    is Map<*, *> -> buildJsonObject {
        value.forEach { (k, v) -> put(k.toString(), convertToJsonElement(v)) }
    }
    is List<*> -> buildJsonArray {
        value.forEach { add(convertToJsonElement(it)) }
    }
    is String -> JsonPrimitive(value)
    is Boolean -> JsonPrimitive(value)
    is Int -> JsonPrimitive(value)
    is Long -> JsonPrimitive(value)
    is Double -> JsonPrimitive(value)
    is Float -> JsonPrimitive(value)
    else -> JsonPrimitive(value.toString())
}

private fun buildSwaggerUiHtml(): String = """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>BookSearch v2 - API Documentation</title>
    <link rel="stylesheet" href="https://unpkg.com/swagger-ui-dist@5/swagger-ui.css">
</head>
<body>
    <div id="swagger-ui"></div>
    <script src="https://unpkg.com/swagger-ui-dist@5/swagger-ui-bundle.js"></script>
    <script>
        SwaggerUIBundle({
            url: '/api/openapi.json',
            dom_id: '#swagger-ui',
            presets: [SwaggerUIBundle.presets.apis, SwaggerUIBundle.SwaggerUIStandalonePreset],
            layout: 'BaseLayout'
        });
    </script>
</body>
</html>
""".trimIndent()
