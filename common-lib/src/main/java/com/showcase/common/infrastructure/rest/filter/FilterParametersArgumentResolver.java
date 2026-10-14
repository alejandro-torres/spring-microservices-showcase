package com.showcase.common.infrastructure.rest.filter;

import java.util.Arrays;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Rellena los parámetros de tipo {@link FilterParameters} de los controllers con todos los valores
 * de {@code filter} tal cual llegan, sin separarlos por comas.
 */
public class FilterParametersArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return FilterParameters.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        String[] values = webRequest.getParameterValues(FilterParameters.PARAMETER_NAME);
        return values == null ? FilterParameters.none() : new FilterParameters(Arrays.asList(values));
    }
}
