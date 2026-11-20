package com.tutorbridge.platform.web;

import com.tutorbridge.platform.api.IdempotencyKey;
import com.tutorbridge.platform.api.WritePrecondition;
import java.util.List;
import java.util.function.Consumer;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.cfg.EnumFeature;
import tools.jackson.databind.cfg.MutableCoercionConfig;
import tools.jackson.databind.type.LogicalType;

/**
 * Shared web behaviour for every controller (ADR-0010). Controllers declare {@link IdempotencyKey},
 * {@link WritePrecondition} or {@link WritePrecondition.Expected} parameters instead of reading headers.
 */
@Configuration(proxyBeanMethods = false)
class WebFoundation implements WebMvcConfigurer {

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new HeaderResolver(IdempotencyKey.class,
                request -> new IdempotencyKey(request.getHeader(IdempotencyKey.HEADER))));
        resolvers.add(new HeaderResolver(WritePrecondition.Expected.class,
                request -> WritePrecondition.Expected.fromIfMatch(request.getHeader(HttpHeaders.IF_MATCH))));
        resolvers.add(new HeaderResolver(WritePrecondition.class,
                request -> WritePrecondition.fromHeaders(
                        request.getHeader(HttpHeaders.IF_MATCH), request.getHeader(HttpHeaders.IF_NONE_MATCH))));
    }

    /**
     * Strict input: a property outside the contract is a 422, not silently dropped, and a number with
     * a fraction sent to an integer field is a 422, not cut to its integer part (#168). A whole number
     * written as {@code 8.0} or {@code 8e0} is still an integer, as JSON Schema says ({@link WholeNumbers}
     * for int and long; other integer types refuse any float token). A scalar of the wrong JSON type is a
     * 422 too, instead of Jackson's coercion (#170): {@code "7"} or {@code ""} is not a number, {@code "true"}
     * or {@code 1} is not a boolean, {@code 7} or {@code true} is not a string, and {@code ""} is not a
     * missing UUID or timestamp.
     */
    @Bean
    JsonMapperBuilderCustomizer strictJson() {
        return builder -> builder
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                        DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES,
                        DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(EnumFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
                .withCoercionConfig(LogicalType.Integer,
                        refuse(CoercionInputShape.String, CoercionInputShape.EmptyString, CoercionInputShape.Boolean))
                .withCoercionConfig(LogicalType.Float,
                        refuse(CoercionInputShape.String, CoercionInputShape.EmptyString, CoercionInputShape.Boolean))
                .withCoercionConfig(LogicalType.Boolean, refuse(CoercionInputShape.String,
                        CoercionInputShape.EmptyString, CoercionInputShape.Integer, CoercionInputShape.Float))
                .withCoercionConfig(LogicalType.Textual,
                        refuse(CoercionInputShape.Integer, CoercionInputShape.Float, CoercionInputShape.Boolean))
                .withCoercionConfig(LogicalType.DateTime, refuse(CoercionInputShape.EmptyString))
                .withCoercionConfig(LogicalType.OtherScalar, refuse(CoercionInputShape.EmptyString))
                .addModule(WholeNumbers.module())
                .addModule(IsoDates.module());
    }

    private static Consumer<MutableCoercionConfig> refuse(CoercionInputShape... shapes) {
        return config -> {
            config.setAcceptBlankAsEmpty(false);
            for (CoercionInputShape shape : shapes) {
                config.setCoercion(shape, CoercionAction.Fail);
            }
        };
    }

    private record HeaderResolver(Class<?> type, java.util.function.Function<NativeWebRequest, Object> read)
            implements HandlerMethodArgumentResolver {

        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.getParameterType() == type;
        }

        @Override
        public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                NativeWebRequest request, WebDataBinderFactory binderFactory) {
            return read.apply(request);
        }
    }
}
