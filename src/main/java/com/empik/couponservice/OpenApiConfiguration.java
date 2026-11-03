package com.empik.couponservice;

import java.util.Arrays;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.empik.couponservice.error.ErrorCode;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;

@Configuration(proxyBeanMethods = false)
@OpenAPIDefinition(info = @Info(title = "Coupon Service API", version = "v1", description = """
        Creating discount coupons and registering their usage.

        A coupon can be used only in its country, resolved from the client's IP address. Behind a \
        trusted proxy (e.g. Docker or a load balancer) the address is taken from `X-Forwarded-For`.

        Errors are [RFC 9457 Problem Details](https://www.rfc-editor.org/rfc/rfc9457) with a `code` \
        property that distinguishes errors sharing the same HTTP status."""))
class OpenApiConfiguration {

    private static final String PROBLEM_DETAIL_SCHEMA = "ProblemDetail";

    /**
     * Documents every error response as a Problem Detail, so controllers declare only the status and
     * the error codes.
     */
    @Bean
    OpenApiCustomizer problemDetailErrorResponses() {
        return openApi -> {
            openApi.getComponents().addSchemas(PROBLEM_DETAIL_SCHEMA, problemDetailSchema());
            Content problemContent = new Content().addMediaType(
                    org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE,
                    new MediaType().schema(new Schema<>().$ref(PROBLEM_DETAIL_SCHEMA)));
            openApi.getPaths()
                .values()
                .stream()
                .flatMap(path -> path.readOperations().stream())
                .flatMap(operation -> operation.getResponses().entrySet().stream())
                .filter(response -> !response.getKey().startsWith("2"))
                .forEach(response -> response.getValue().setContent(problemContent));
        };
    }

    private static Schema<?> problemDetailSchema() {
        Schema<?> fieldError = new ObjectSchema()
            .addProperty("field", new StringSchema().example("maxUses"))
            .addProperty("message", new StringSchema().example("must be greater than 0"));
        return new ObjectSchema()
            .addProperty("type", new StringSchema().example("about:blank"))
            .addProperty("title", new StringSchema().example("Conflict"))
            .addProperty("status", new IntegerSchema().example(409))
            .addProperty("detail", new StringSchema().example("Coupon SPRING has reached its usage limit"))
            .addProperty("instance", new StringSchema().example("/api/v1/coupons/SPRING/usages"))
            .addProperty("code", new StringSchema()._enum(Arrays.stream(ErrorCode.values()).map(Enum::name).toList())
                .description("Error code, present on business and validation errors"))
            .addProperty("errors", new ArraySchema().items(fieldError)
                .description("Invalid fields, present on VALIDATION_FAILED"));
    }

}
